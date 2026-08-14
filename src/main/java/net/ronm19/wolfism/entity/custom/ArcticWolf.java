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
import net.minecraft.world.level.pathfinder.PathType;
import net.ronm19.wolfism.entity.base.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.ArcticHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.ArcticPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.ArcticPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.ArcticPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.ArcticPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.ArcticPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.ArcticWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Arctic Wolf: Wolfism's natural cold-biome survival and tracking specialist.
 *
 * <p>Arctic Wolves are intentionally non-magical. Their advantage comes from
 * cold adaptation, snow-country tracking, environmental awareness and pack
 * coordination rather than Frost-style supernatural slowing or ice attacks.</p>
 */
public final class ArcticWolf extends AbstractWolfismWolf {
    public static final float PREY_DAMAGE_MULTIPLIER = 1.20F;
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;
    public static final int TRACK_MEMORY_TICKS = 20 * 8;

    private static final class BrainHolder {
        private static final Brain.Provider<ArcticWolf> PROVIDER = Brain.<ArcticWolf>provider(
                ImmutableList.of(ModSensorTypes.ARCTIC_PACK.get(), ModSensorTypes.ARCTIC_PREY.get()),
                wolf -> List.of());
    }

    public ArcticWolf(EntityType<? extends ArcticWolf> type, Level level) {
        super(type, level);

        // Powder snow is terrain, not a navigation hazard, for an Arctic Wolf.
        this.setPathfindingMalus(PathType.POWDER_SNOW, 0.0F);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.ARCTIC_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Babies read danger as a retreat order, never as an attack order.
        this.goalSelector.addGoal(1, new ArcticPupRetreatGoal(this));
        this.goalSelector.addGoal(5, new ArcticPupFollowAdultGoal(this));

        // Wild adults stay socially coherent without moving as one rigid blob.
        this.goalSelector.addGoal(6, new ArcticPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new ArcticPupPlayGoal(this));

        // Family defense outranks hunting.
        this.targetSelector.addGoal(3, new ArcticPackAssistGoal(this));
        this.targetSelector.addGoal(6, new ArcticHuntGoal(this));
    }

    @Override
    protected Brain<ArcticWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ArcticWolf> getBrain() {
        return (Brain<ArcticWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
        this.tickTrackingMemory();

        // Natural cold adaptation: Arctic Wolves never accumulate freeze time.
        if (this.getTicksFrozen() > 0) {
            this.setTicksFrozen(0);
        }

        this.getBrain().tick(level, this);

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

    public boolean isArcticPackmate(ArcticWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidArcticCombatTarget(LivingEntity target) {
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
                || type == EntityType.FOX
                || type == EntityType.GOAT;
    }

    public boolean hasEnoughHuntersFor(LivingEntity prey) {
        int requiredAdults = prey.getType() == EntityType.GOAT ? 2 : 1;
        int adults = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ARCTIC_ADULT_PACK_SIZE.get())
                .orElse(this.isBaby() ? 0 : 1);
        return adults >= requiredAdults;
    }

    public boolean isColdEnvironment() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ARCTIC_COLD_ENVIRONMENT.get())
                .orElse(false);
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get());
        }
    }

    private void tickTrackingMemory() {
        int trackingTicks = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get())
                .orElse(0);
        if (trackingTicks > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get(), trackingTicks - 1);
        } else if (trackingTicks == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get());
            this.getBrain().eraseMemory(ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get());
        }
    }

    public void refreshPreyTrack(LivingEntity prey) {
        if (prey != null && prey.isAlive()) {
            this.getBrain().setMemory(ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get(), prey);
            this.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get(), TRACK_MEMORY_TICKS);
        }
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.ARCTIC_HUNT_TARGET.get());

        List<ArcticWolf> packmates = serverLevel.getEntitiesOfClass(
                ArcticWolf.class,
                this.getBoundingBox().inflate(ArcticWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isArcticPackmate(candidate));
        for (ArcticWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.ARCTIC_HUNT_TARGET.get());
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

        double radius = pupEmergency ? 32.0D : ArcticWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_PACK_THREAT.get(), threat);

        List<ArcticWolf> packmates = serverLevel.getEntitiesOfClass(
                ArcticWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isArcticPackmate(candidate));

        for (ArcticWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.ARCTIC_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidArcticCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkArcticWolfSpawnRules(
            EntityType<ArcticWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
