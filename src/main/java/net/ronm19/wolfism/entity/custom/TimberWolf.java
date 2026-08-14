package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.ronm19.wolfism.entity.base.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.TimberHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.TimberPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.TimberPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.TimberPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.TimberPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.TimberPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.TimberWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Timber Wolf: Wolfism's reference natural wolf and intelligent pack hunter.
 */
public final class TimberWolf extends AbstractWolfismWolf {
    public static final float PREY_DAMAGE_MULTIPLIER = 1.25F;
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;

    private static final class BrainHolder {
        private static final Brain.Provider<TimberWolf> PROVIDER = Brain.<TimberWolf>provider(
                ImmutableList.of(ModSensorTypes.TIMBER_PACK.get(), ModSensorTypes.TIMBER_PREY.get()),
                wolf -> List.of());
    }

    public TimberWolf(EntityType<? extends TimberWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.TIMBER_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Pup safety beats every ordinary movement decision.
        this.goalSelector.addGoal(1, new TimberPupRetreatGoal(this));
        this.goalSelector.addGoal(5, new TimberPupFollowAdultGoal(this));

        // Adult social movement sits above random wandering but below emergency movement.
        this.goalSelector.addGoal(6, new TimberPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new TimberPupPlayGoal(this));

        // Family danger outranks prey; hunting is deliberately lower priority.
        this.targetSelector.addGoal(3, new TimberPackAssistGoal(this));
        this.targetSelector.addGoal(6, new TimberHuntGoal(this));
    }

    @Override
    protected Brain<TimberWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<TimberWolf> getBrain() {
        return (Brain<TimberWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
        this.getBrain().tick(level, this);

        // Timber pups are family members, not tiny combat units.
        if (this.isBaby() && this.getTarget() != null) {
            this.setTarget(null);
        }

        super.customServerAiStep(level);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isTimberPackmate(TimberWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidTimberCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public static boolean isPreferredPrey(LivingEntity target) {
        EntityType<?> type = target.getType();
        return type == EntityType.RABBIT
                || type == EntityType.CHICKEN
                || type == EntityType.SHEEP
                || type == EntityType.PIG
                || type == EntityType.COW;
    }

    public boolean hasEnoughHuntersFor(LivingEntity prey) {
        int requiredAdults = prey.getType() == EntityType.COW ? 3 : 1;
        int adults = this.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_ADULT_PACK_SIZE.get())
                .orElse(this.isBaby() ? 0 : 1);
        return adults >= requiredAdults;
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean hasForestCoverAdvantage() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_FOREST_COVER.get())
                .orElse(false);
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get());
        }
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get());

        List<TimberWolf> packmates = serverLevel.getEntitiesOfClass(
                TimberWolf.class,
                this.getBoundingBox().inflate(TimberWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isTimberPackmate(candidate));
        for (TimberWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get());
        }
    }

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || threat == null
                || !threat.isAlive()
                || this.isAlliedTo(threat)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && !this.wantsToAttack(threat, owner)) {
            return;
        }

        double radius = pupEmergency ? 32.0D : TimberWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_PACK_THREAT.get(), threat);

        List<TimberWolf> packmates = serverLevel.getEntitiesOfClass(
                TimberWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isTimberPackmate(candidate));

        for (TimberWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidTimberCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkTimberWolfSpawnRules(
            EntityType<TimberWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}