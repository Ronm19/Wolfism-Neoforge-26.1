package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.FrostIceSpikeGoal;
import net.ronm19.wolfism.entity.ai.goal.FrostPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.FrostPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.FrostPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.FrostPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.FrostPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.FrostWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Frost Wolf: supernatural cold-control specialist.
 *
 * <p>Frost deliberately does not copy Arctic Wolf's natural tracking niche.
 * Its combat identity is battlefield control: bites build vanilla freezing,
 * Ice Spike applies ranged chill/control, and packs avoid wasting multiple
 * spikes on the same target at once.</p>
 */
public final class FrostWolf extends AbstractWolfismWolf {
    public static final int FROST_BITE_FREEZE_TICKS = 35;
    public static final int ICE_SPIKE_FREEZE_TICKS = 75;
    public static final int CHILLED_ICE_SPIKE_FREEZE_TICKS = 105;
    public static final float ICE_SPIKE_DAMAGE = 4.0F;

    public static final int ICE_SPIKE_COOLDOWN_TICKS = 20 * 20;
    public static final int COLD_ICE_SPIKE_COOLDOWN_TICKS = 20 * 18;

    private static final double ICE_TRACTION_DAMPING = 0.72D;

    private boolean iceSpikeImpactActive;

    private static final class BrainHolder {
        private static final Brain.Provider<FrostWolf> PROVIDER = Brain.<FrostWolf>provider(
                ImmutableList.of(ModSensorTypes.FROST_PACK.get(), ModSensorTypes.FROST_COMBAT.get()),
                wolf -> List.of());
    }

    public FrostWolf(EntityType<? extends FrostWolf> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 0.0F);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.FROST_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new FrostPupRetreatGoal(this));
        this.goalSelector.addGoal(2, new FrostIceSpikeGoal(this));
        this.goalSelector.addGoal(5, new FrostPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new FrostPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new FrostPupPlayGoal(this));

        this.targetSelector.addGoal(3, new FrostPackAssistGoal(this));
    }

    @Override
    protected Brain<FrostWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<FrostWolf> getBrain() {
        return (Brain<FrostWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickIceSpikeCooldown();

        if (this.getTicksFrozen() > 0) {
            this.setTicksFrozen(0);
        }

        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            this.clearIceSpikeReservation();
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        this.applyIceTraction();

        if (!this.isBaby()
                && this.level() instanceof ServerLevel level
                && this.getRandom().nextInt(110) == 0) {
            WolfVfx.sendParticles("frost_wolf", level,
                    ParticleTypes.SNOWFLAKE,
                    this.getX() + (this.getRandom().nextDouble() - 0.5D) * 0.40D,
                    this.getY() + 0.25D + this.getRandom().nextDouble() * 0.40D,
                    this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 0.40D,
                    1,
                    0.01D,
                    0.02D,
                    0.01D,
                    0.0D);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    public boolean isIceSpikeImpactActive() {
        return this.iceSpikeImpactActive;
    }

    public boolean isIceSpikeCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean canStartIceSpikeAgainst(LivingEntity target) {
        if (target == null
                || target instanceof Creeper
                || !target.isAlive()
                || this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isIceSpikeCoolingDown()
                || !this.isValidFrostCombatTarget(target)) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= FrostIceSpikeGoal.MIN_START_DISTANCE_SQR
                && distance <= FrostIceSpikeGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target)
                && !this.isIceSpikeReservedByPack(target);
    }

    public void reserveIceSpikeTarget(LivingEntity target) {
        if (target != null && target.isAlive()) {
            this.getBrain().setMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_TARGET.get(), target);
        }
    }

    public void clearIceSpikeReservation() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_TARGET.get());
    }

    public boolean performIceSpike(LivingEntity target) {
        if (!this.canStartIceSpikeAgainst(target) || !(this.level() instanceof ServerLevel level)) {
            this.clearIceSpikeReservation();
            return false;
        }

        int existingFreeze = target.getTicksFrozen();
        int freezeAmount = existingFreeze >= FROST_BITE_FREEZE_TICKS
                ? CHILLED_ICE_SPIKE_FREEZE_TICKS
                : ICE_SPIKE_FREEZE_TICKS;

        this.iceSpikeImpactActive = true;
        boolean hurt;
        try {
            hurt = target.hurtServer(level, this.damageSources().mobAttack(this), ICE_SPIKE_DAMAGE);
        } finally {
            this.iceSpikeImpactActive = false;
        }

        if (hurt && target.isAlive()) {
            this.applyFreezeControl(target, freezeAmount, true);
            WolfVfx.sendParticles("frost_wolf", level,
                    ParticleTypes.SNOWFLAKE,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.45D,
                    target.getZ(),
                    24,
                    0.38D,
                    0.50D,
                    0.38D,
                    0.035D);
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get(),
                this.isColdEnvironment() ? COLD_ICE_SPIKE_COOLDOWN_TICKS : ICE_SPIKE_COOLDOWN_TICKS);
        this.clearIceSpikeReservation();
        return hurt;
    }

    public void applyFrostBite(LivingEntity target) {
        if (target == null || !target.isAlive() || target == this || this.isAlliedTo(target)) {
            return;
        }
        this.applyFreezeControl(target, FROST_BITE_FREEZE_TICKS, false);
    }

    private void applyFreezeControl(LivingEntity target, int freezeTicks, boolean iceSpike) {
        if (!target.canFreeze()) {
            return;
        }

        int required = Math.max(1, target.getTicksRequiredToFreeze());
        int current = Math.max(0, target.getTicksFrozen());
        int next = Math.min(required, current + Math.max(0, freezeTicks));
        target.setTicksFrozen(next);

        boolean fullyFrozen = next >= required;
        int duration = fullyFrozen ? 50 : (iceSpike ? 70 : 50);
        int amplifier = fullyFrozen ? 3 : (iceSpike ? 1 : 0);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier));
    }

    private boolean isIceSpikeReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        List<FrostWolf> packmates = level.getEntitiesOfClass(
                FrostWolf.class,
                this.getBoundingBox().inflate(FrostWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isFrostPackmate(candidate));

        for (FrostWolf packmate : packmates) {
            LivingEntity reserved = packmate.getBrain()
                    .getMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_TARGET.get())
                    .orElse(null);
            if (reserved == target) {
                return true;
            }
        }
        return false;
    }

    private void tickIceSpikeCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get());
        }
    }

    private void applyIceTraction() {
        if (!this.onGround()) {
            return;
        }

        BlockState below = this.level().getBlockState(this.blockPosition().below());
        if (!isIceSurface(below)) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(
                motion.x * ICE_TRACTION_DAMPING,
                motion.y,
                motion.z * ICE_TRACTION_DAMPING);
    }

    private static boolean isIceSurface(BlockState state) {
        return state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE);
    }

    public boolean isFrostPackmate(FrostWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidFrostCombatTarget(LivingEntity target) {
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
                || type == EntityType.SHEEP;
    }

    public boolean isColdEnvironment() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.FROST_COLD_ENVIRONMENT.get())
                .orElse(false);
    }


    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.clearIceSpikeReservation();
    }

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || this.isAlliedTo(threat)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && !this.wantsToAttack(threat, owner)) {
            return;
        }

        this.clearIceSpikeReservation();

        double radius = pupEmergency ? 34.0D : FrostWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.FROST_PACK_THREAT.get(), threat);

        List<FrostWolf> packmates = level.getEntitiesOfClass(
                FrostWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isFrostPackmate(candidate));

        for (FrostWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.FROST_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidFrostCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkFrostWolfSpawnRules(
            EntityType<FrostWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
