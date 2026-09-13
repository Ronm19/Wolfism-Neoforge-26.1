package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.SkeletonWolfRangedCombatGoal;
import net.ronm19.wolfism.entity.ai.sensor.SkeletonWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Skeleton Wolf #22: bone marksman / ranged skirmisher.
 *
 * <p>Kit: Bone Shot, Splinter Bite, Bone Rattle, Skeletal Volley and Marrow Guard.
 * Unlike Zombie Wolf, Skeleton Wolf survives through range control and tactical
 * repositioning rather than regeneration or resurrection.</p>
 */
public final class SkeletonWolf extends AbstractWolfismWolf {
    public static final double BONE_SHOT_MAX_RANGE = 20.0D;

    public static final int BONE_SHOT_COOLDOWN_TICKS = 20 * 3;
    public static final int BONE_RATTLE_COOLDOWN_TICKS = 20 * 12;
    public static final int SKELETAL_VOLLEY_COOLDOWN_TICKS = 20 * 25;
    public static final int MARROW_GUARD_COOLDOWN_TICKS = 20 * 30;

    private static final float BONE_SHOT_DAMAGE = 4.0F;
    private static final float VOLLEY_SHARD_DAMAGE = 2.75F;
    private static final float SPLINTER_BITE_CHANCE = 0.45F;
    private static final int SPLINTER_SLOWNESS_TICKS = 20 * 6;
    private static final int SPLINTER_WEAKNESS_TICKS = 20 * 4;
    private static final double BONE_RATTLE_RANGE = 5.0D;
    private static final int BONE_RATTLE_DURATION = 20 * 4;
    private static final float MARROW_GUARD_HEALTH_RATIO = 0.30F;
    private static final int MARROW_GUARD_DURATION = 20 * 6;
    private static final int ABILITY_DECISION_INTERVAL = 10;

    private int abilityLockoutTicks;
    private int volleyShotsRemaining;
    private int volleyShotDelayTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<SkeletonWolf> PROVIDER = Brain.<SkeletonWolf>provider(
                ImmutableList.of(ModSensorTypes.SKELETON_PACK.get()),
                wolf -> List.of());
    }

    public SkeletonWolf(EntityType<? extends SkeletonWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new SkeletonWolfRangedCombatGoal(this));
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SKELETON_WOLF.get();
    }

    @Override
    protected Brain<SkeletonWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SkeletonWolf> getBrain() {
        return (Brain<SkeletonWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof SkeletonWolf skeleton && this.isSkeletonPackmate(skeleton)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.SKELETON_BONE_SHOT_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SKELETON_BONE_RATTLE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SKELETON_VOLLEY_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SKELETON_MARROW_GUARD_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.canParticipateInWolfismCombat() && this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.SKELETON_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidSkeletonCombatTarget(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }
        if (this.volleyShotDelayTicks > 0) {
            --this.volleyShotDelayTicks;
        }

        if (!this.canParticipateInWolfismCombat()) {
            this.cancelVolley();
            return;
        }

        if (this.volleyShotsRemaining > 0) {
            this.tickVolley(level);
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0) {
            this.tryMarrowGuard(level);
            this.tryBoneRattle(level);
            this.trySkeletalVolley(level);
        }

        if (this.tickCount % 4 == 0 && this.volleyShotsRemaining <= 0) {
            this.tryBoneShot(level);
        }
    }

    // ---------------------------------------------------------------------
    // 1. Bone Shot - physical ranged projectile
    // ---------------------------------------------------------------------

    private boolean tryBoneShot(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.abilityLockoutTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.SKELETON_BONE_SHOT_COOLDOWN.get())) {
            return false;
        }

        LivingEntity target = this.getPrimarySkeletonTarget();
        if (!this.isValidRangedTarget(target, 5.5D, BONE_SHOT_MAX_RANGE)) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.SKELETON_BONE_SHOT_COOLDOWN.get(),
                BONE_SHOT_COOLDOWN_TICKS);
        this.fireBoneShard(level, target, BONE_SHOT_DAMAGE, 1.65F, 1.1F);
        this.playSound(SoundEvents.SKELETON_SHOOT, 0.8F, 1.15F + this.random.nextFloat() * 0.15F);
        return true;
    }

    // ---------------------------------------------------------------------
    // 2. Splinter Bite - close-range fallback
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(entity);
        if (hurt
                && this.canParticipateInWolfismCombat()
                && entity instanceof LivingEntity target
                && this.random.nextFloat() < SPLINTER_BITE_CHANCE) {
            // One splinter consequence per proc keeps the bite less predictable:
            // either the shard hampers movement or it weakens the victim's attacks.
            if (this.random.nextBoolean()) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        SPLINTER_SLOWNESS_TICKS,
                        0),
                        this);
            } else {
                target.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        SPLINTER_WEAKNESS_TICKS,
                        0),
                        this);
            }
            WolfVfx.sendParticles("skeleton_wolf", level,
                    ParticleTypes.CRIT,
                    target.getX(), target.getY(0.55D), target.getZ(),
                    14, 0.24D, 0.28D, 0.24D, 0.12D);
            WolfVfx.sendParticles("skeleton_wolf", level,
                    ParticleTypes.POOF,
                    target.getX(), target.getY(0.45D), target.getZ(),
                    5, 0.18D, 0.18D, 0.18D, 0.02D);
        }
        return hurt;
    }

    // ---------------------------------------------------------------------
    // 3. Bone Rattle - close-range escape / reset
    // ---------------------------------------------------------------------

    private boolean tryBoneRattle(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.abilityLockoutTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.SKELETON_BONE_RATTLE_COOLDOWN.get())) {
            return false;
        }

        LivingEntity target = this.getPrimarySkeletonTarget();
        if (target == null
                || this.distanceToSqr(target) > BONE_RATTLE_RANGE * BONE_RATTLE_RANGE) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.SKELETON_BONE_RATTLE_COOLDOWN.get(),
                BONE_RATTLE_COOLDOWN_TICKS);
        this.abilityLockoutTicks = 10;

        this.addEffect(new MobEffectInstance(MobEffects.SPEED, BONE_RATTLE_DURATION, 1, true, true), this);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BONE_RATTLE_DURATION, 0, true, true), this);

        Vec3 away = new Vec3(
                this.getX() - target.getX(),
                0.0D,
                this.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-5D) {
            Vec3 look = this.getViewVector(1.0F);
            away = new Vec3(-look.x, 0.0D, -look.z);
        }
        if (away.lengthSqr() > 1.0E-5D) {
            away = away.normalize();
            this.setDeltaMovement(this.getDeltaMovement().add(away.x * 0.72D, 0.12D, away.z * 0.72D));
            this.hurtMarked = true;
        }
        this.getNavigation().stop();

        WolfVfx.sendParticles("skeleton_wolf", level,
                ParticleTypes.POOF,
                this.getX(), this.getY(0.55D), this.getZ(),
                26, 0.45D, 0.38D, 0.45D, 0.10D);
        this.sendRing(level, this.position(), 1.35D, 22, ParticleTypes.CRIT);
        this.playSound(this.getWolfismGrowlSound(), 1.0F, 0.75F);
        return true;
    }

    // ---------------------------------------------------------------------
    // 4. Skeletal Volley - three quick physical shards
    // ---------------------------------------------------------------------

    private boolean trySkeletalVolley(ServerLevel level) {
        if (this.volleyShotsRemaining > 0
                || !this.canUseActiveWolfismAbility()
                || this.abilityLockoutTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.SKELETON_VOLLEY_COOLDOWN.get())) {
            return false;
        }

        LivingEntity target = this.getPrimarySkeletonTarget();
        if (!this.isValidRangedTarget(target, 6.0D, BONE_SHOT_MAX_RANGE)) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.SKELETON_VOLLEY_COOLDOWN.get(),
                SKELETAL_VOLLEY_COOLDOWN_TICKS);
        this.volleyShotsRemaining = 3;
        this.volleyShotDelayTicks = 0;
        this.abilityLockoutTicks = 16;

        WolfVfx.sendParticles("skeleton_wolf", level,
                ParticleTypes.CLOUD,
                this.getX(), this.getY(0.62D), this.getZ(),
                9, 0.22D, 0.20D, 0.22D, 0.03D);
        return true;
    }

    private void tickVolley(ServerLevel level) {
        if (this.volleyShotsRemaining <= 0 || this.volleyShotDelayTicks > 0) {
            return;
        }

        LivingEntity target = this.getPrimarySkeletonTarget();
        if (!this.isValidRangedTarget(target, 3.0D, BONE_SHOT_MAX_RANGE + 2.0D)) {
            this.cancelVolley();
            return;
        }

        float inaccuracy = this.volleyShotsRemaining == 2 ? 1.8F : 1.2F;
        this.fireBoneShard(level, target, VOLLEY_SHARD_DAMAGE, 1.75F, inaccuracy);
        this.playSound(SoundEvents.SKELETON_SHOOT, 0.75F, 1.28F + this.random.nextFloat() * 0.12F);

        --this.volleyShotsRemaining;
        this.volleyShotDelayTicks = 5;
        if (this.volleyShotsRemaining <= 0) {
            this.sendRing(level, this.position(), 1.15D, 18, ParticleTypes.CLOUD);
        }
    }

    private void cancelVolley() {
        this.volleyShotsRemaining = 0;
        this.volleyShotDelayTicks = 0;
    }

    // ---------------------------------------------------------------------
    // 5. Marrow Guard - low-health damage resistance
    // ---------------------------------------------------------------------

    private boolean tryMarrowGuard(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.SKELETON_MARROW_GUARD_COOLDOWN.get())
                || this.getHealth() / this.getMaxHealth() > MARROW_GUARD_HEALTH_RATIO
                || this.getPrimarySkeletonTarget() == null) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.SKELETON_MARROW_GUARD_COOLDOWN.get(),
                MARROW_GUARD_COOLDOWN_TICKS);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MARROW_GUARD_DURATION, 1, true, true), this);

        WolfVfx.sendParticles("skeleton_wolf", level,
                ParticleTypes.CLOUD,
                this.getX(), this.getY(0.55D), this.getZ(),
                22, 0.38D, 0.42D, 0.38D, 0.035D);
        this.sendRing(level, this.position(), 1.65D, 28, ParticleTypes.POOF);
        this.playSound(this.getWolfismGrowlSound(), 0.85F, 0.70F);
        return true;
    }

    // ---------------------------------------------------------------------
    // Projectile / targeting helpers
    // ---------------------------------------------------------------------

    private void fireBoneShard(
            ServerLevel level,
            LivingEntity target,
            float damage,
            float velocity,
            float inaccuracy) {
        BoneShardProjectile shard = new BoneShardProjectile(level, this)
                .setShardDamage(damage);
        shard.setPos(this.getX(), this.getY(0.70D), this.getZ());

        double dx = target.getX() - shard.getX();
        double dz = target.getZ() - shard.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY(0.55D) - shard.getY() + horizontal * 0.045D;

        shard.shoot(dx, dy, dz, velocity, inaccuracy);
        level.addFreshEntity(shard);

        WolfVfx.sendParticles("skeleton_wolf", level,
                ParticleTypes.CRIT,
                this.getX(), this.getY(0.70D), this.getZ(),
                4, 0.08D, 0.08D, 0.08D, 0.03D);
    }

    private boolean isValidRangedTarget(LivingEntity target, double minRange, double maxRange) {
        if (target == null || !this.isValidSkeletonCombatTarget(target)) {
            return false;
        }
        double distanceSqr = this.distanceToSqr(target);
        return distanceSqr >= minRange * minRange
                && distanceSqr <= maxRange * maxRange
                && this.getSensing().hasLineOfSight(target);
    }

    private LivingEntity getPrimarySkeletonTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && this.isValidSkeletonCombatTarget(target)) {
            return target;
        }

        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && this.isValidSkeletonCombatTarget(familyTarget)) {
            return familyTarget;
        }

        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SKELETON_PACK_THREAT.get())
                .orElse(null);
        return shared != null && this.isValidSkeletonCombatTarget(shared) ? shared : null;
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    public boolean isSkeletonPackmate(SkeletonWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isSkeletonFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity == this) {
            return true;
        }
        if (this.isTame()) {
            if (entity == this.getOwner()) {
                return true;
            }
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof SkeletonWolf skeleton && this.isSkeletonPackmate(skeleton);
    }

    public boolean isValidSkeletonCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.canParticipateInWolfismCombat()
                || !this.canAttack(target)
                || this.isAlliedTo(target)
                || this.isSkeletonFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidSkeletonCombatTarget(threat)) {
            return;
        }

        for (SkeletonWolf mate : level.getEntitiesOfClass(
                SkeletonWolf.class,
                this.getBoundingBox().inflate(SkeletonWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isSkeletonPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.SKELETON_PACK_THREAT.get(), threat);
            if (mate.canParticipateInWolfismCombat() && mate.isValidSkeletonCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.cancelVolley();
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Brain cooldown helpers / visuals / spawn
    // ---------------------------------------------------------------------

    private boolean isCoolingDown(MemoryModuleType<Integer> memory) {
        return this.getBrain().getMemory(memory).orElse(0) > 0;
    }

    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int cooldown = this.getBrain().getMemory(memory).orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(memory, cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(memory);
        }
    }

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("skeleton_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.18D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public static boolean checkSkeletonWolfSpawnRules(
            EntityType<SkeletonWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        // Same proven night-surface philosophy as the finalized Zombie Wolf:
        // placement handles physical ground validity; the species rule simply
        // asks whether the outside world is dark.
        return level.getLevel().isDarkOutside();
    }
}
