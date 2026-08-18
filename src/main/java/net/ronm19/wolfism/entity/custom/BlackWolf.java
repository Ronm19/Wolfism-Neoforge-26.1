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
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.BlackAmbushHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.BlackPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.BlackPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.BlackPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.BlackPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.BlackPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.BlackWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Black Wolf: Wolfism's natural nocturnal hunter and ambush specialist.
 *
 * <p>Black Wolves are intentionally non-magical. Their strength comes from
 * nighttime awareness, patient stalking, coordinated flanking, a powerful
 * opening ambush strike and tighter pack communication after dark.</p>
 */
public final class BlackWolf extends AbstractWolfismWolf {
    public static final float NIGHT_DAMAGE_MULTIPLIER = 1.20F;
    public static final float AMBUSH_DAMAGE_MULTIPLIER = 1.25F;
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;
    public static final int AMBUSH_COOLDOWN_TICKS = 20 * 20;

    private static final class BrainHolder {
        private static final Brain.Provider<BlackWolf> PROVIDER = Brain.<BlackWolf>provider(
                ImmutableList.of(ModSensorTypes.BLACK_PACK.get(), ModSensorTypes.BLACK_NIGHT_PREY.get()),
                wolf -> List.of());
    }

    public BlackWolf(EntityType<? extends BlackWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.BLACK_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Pups always interpret danger as retreat, never as combat.
        this.goalSelector.addGoal(1, new BlackPupRetreatGoal(this));

        // Black Wolves stalk before committing to a fight, so the hunt owns
        // movement while the target is still unaware/out of reach.
        this.goalSelector.addGoal(4, new BlackAmbushHuntGoal(this));
        this.goalSelector.addGoal(5, new BlackPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new BlackPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new BlackPupPlayGoal(this));

        // Family danger immediately interrupts prey stalking.
        this.targetSelector.addGoal(3, new BlackPackAssistGoal(this));
    }

    @Override
    protected Brain<BlackWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<BlackWolf> getBrain() {
        return (Brain<BlackWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
        this.tickAmbushCooldown();
        this.getBrain().tick(level, this);

        if (!this.isNightActive()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
        } else {
            // Tamed Black Wolves never auto-hunt livestock, but if their owner
            // legitimately gives them a combat target at night they can still
            // prepare the same natural opening ambush as a wild Black Wolf.
            LivingEntity target = this.getTarget();
            if (!this.isBaby()
                    && target != null
                    && target.isAlive()
                    && this.distanceToSqr(target) > 7.0D * 7.0D
                    && !this.isAmbushCoolingDown()
                    && this.isValidBlackCombatTarget(target)) {
                this.primeAmbush(target);
            }
        }

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

    public boolean isBlackPackmate(BlackWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidBlackCombatTarget(LivingEntity target) {
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
                || type == EntityType.PIG;
    }

    public boolean isNightActive() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_NIGHT_ACTIVE.get())
                .orElse(this.level().isDarkOutside());
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean isAmbushCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get())
                .orElse(0) > 0;
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get());
        }
    }

    private void tickAmbushCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get());
        }

        LivingEntity ambushTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get())
                .orElse(null);
        if (ambushTarget != null && (!ambushTarget.isAlive() || !this.isNightActive())) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
        }
    }

    public void primeAmbush(LivingEntity target) {
        if (!this.isBaby()
                && target != null
                && target.isAlive()
                && this.isNightActive()
                && !this.isAmbushCoolingDown()
                && this.isValidBlackCombatTarget(target)) {
            this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get(), target);
        }
    }

    /** Returns true exactly once for a prepared night ambush against this target. */
    public boolean consumeAmbushAgainst(LivingEntity target) {
        if (target == null || !this.isNightActive() || this.isAmbushCoolingDown()) {
            return false;
        }

        LivingEntity remembered = this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get())
                .orElse(null);
        if (remembered != target) {
            return false;
        }

        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
        this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get(), AMBUSH_COOLDOWN_TICKS);
        return true;
    }

    public void cancelAmbush() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());

        List<BlackWolf> packmates = serverLevel.getEntitiesOfClass(
                BlackWolf.class,
                this.getBoundingBox().inflate(BlackWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isBlackPackmate(candidate));
        for (BlackWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
        }
    }


    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.cancelAmbush();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
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

        this.cancelAmbush();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());

        double radius = pupEmergency ? 34.0D : BlackWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.BLACK_PACK_THREAT.get(), threat);

        List<BlackWolf> packmates = serverLevel.getEntitiesOfClass(
                BlackWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isBlackPackmate(candidate));

        for (BlackWolf packmate : packmates) {
            packmate.cancelAmbush();
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
            packmate.getBrain().setMemory(ModMemoryModuleTypes.BLACK_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidBlackCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkBlackWolfSpawnRules(
            EntityType<BlackWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
