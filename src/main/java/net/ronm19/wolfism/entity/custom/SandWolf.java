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
import net.minecraft.world.level.block.Blocks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.SandBuriedInterestGoal;
import net.ronm19.wolfism.entity.ai.goal.SandHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.SandPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.SandPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.SandPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.SandPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.SandPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.SandWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Sand Wolf: Wolfism's natural desert awareness and exploration specialist.
 *
 * <p>Sand Wolves are non-magical desert animals. Their advantage is practical:
 * long-range awareness in open desert, safe movement around cactus, sensing
 * suspicious/buried objects beneath sand, and loose pack coordination.</p>
 */
public final class SandWolf extends AbstractWolfismWolf {
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;

    private static final class BrainHolder {
        private static final Brain.Provider<SandWolf> PROVIDER = Brain.<SandWolf>provider(
                ImmutableList.of(ModSensorTypes.SAND_PACK.get(), ModSensorTypes.SAND_DESERT.get()),
                wolf -> List.of());
    }

    public SandWolf(EntityType<? extends SandWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SAND_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new SandPupRetreatGoal(this));

        // A tamed adult can quietly lead its owner toward suspicious sand or a
        // container genuinely buried beneath sand. Combat and Creeper safety
        // remain higher priority than this exploration behavior.
        this.goalSelector.addGoal(4, new SandBuriedInterestGoal(this));
        this.goalSelector.addGoal(5, new SandPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new SandPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new SandPupPlayGoal(this));

        this.targetSelector.addGoal(3, new SandPackAssistGoal(this));
        this.targetSelector.addGoal(6, new SandHuntGoal(this));
    }

    @Override
    protected Brain<SandWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SandWolf> getBrain() {
        return (Brain<SandWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
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

    public boolean isSandPackmate(SandWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidSandCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public static boolean isPreferredPrey(LivingEntity target) {
        EntityType<?> type = target.getType();
        return type == EntityType.RABBIT || type == EntityType.CHICKEN;
    }

    public boolean isDesertEnvironment() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_DESERT_ENVIRONMENT.get())
                .orElseGet(() -> this.level().getBiome(this.blockPosition())
                        .is(ModBiomeTags.SAND_WOLF_DESERT_ENVIRONMENT));
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get());
        }
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.SAND_HUNT_TARGET.get());

        List<SandWolf> packmates = serverLevel.getEntitiesOfClass(
                SandWolf.class,
                this.getBoundingBox().inflate(SandWolfPackSensor.MAX_PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isSandPackmate(candidate));
        for (SandWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.SAND_HUNT_TARGET.get());
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

        double radius = pupEmergency ? 36.0D : SandWolfPackSensor.MAX_PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.SAND_PACK_THREAT.get(), threat);

        List<SandWolf> packmates = serverLevel.getEntitiesOfClass(
                SandWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isSandPackmate(candidate));

        for (SandWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.SAND_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidSandCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkSandWolfSpawnRules(
            EntityType<SandWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        var ground = level.getBlockState(pos.below());
        boolean validGround = ground.is(Blocks.SAND)
                || ground.is(Blocks.RED_SAND)
                || ground.is(BlockTags.WOLVES_SPAWNABLE_ON);
        return validGround && isBrightEnoughToSpawn(level, pos);
    }
}
