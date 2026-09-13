package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Ash Wolf (#51) — Ash / Smoke / Evasion / Debuff / Perception Control.
 *
 * <p>Her rule is simple: "You cannot hit what you cannot properly track." Ash
 * does not compete with Infernal for brute force or Blaze for fire artillery.
 * She degrades hostile perception, breaks ranged pressure, repositions through
 * smoke, and turns already-burning enemies into cinder-burst opportunities.</p>
 *
 * <p>Owner sit/stay remains absolute. Pups never use adult abilities. All smoke
 * and cinder fields are hostile-only and never intentionally impair family.</p>
 */
public final class AshWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Synced client state
    // =====================================================================

    private static final EntityDataAccessor<Boolean> DATA_ASH_VISUAL_ACTIVE =
            SynchedEntityData.defineId(
                    AshWolf.class,
                    EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_ASH_STEP_ACTIVE =
            SynchedEntityData.defineId(
                    AshWolf.class,
                    EntityDataSerializers.BOOLEAN);

    // =====================================================================
    // Baseline
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 40.0D;
    private static final double BASE_ATTACK_DAMAGE = 6.5D;
    private static final double BASE_MOVEMENT_SPEED = 0.35D;
    private static final double BASE_ARMOR = 4.0D;
    private static final double BASE_FOLLOW_RANGE = 44.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 0.08D;

    public static final double ASH_AWARENESS_RADIUS = 28.0D;
    public static final double ASH_FAMILY_RADIUS = 24.0D;

    private static final int DECISION_INTERVAL = 8;

    // =====================================================================
    // Cinder Bite / Ashen
    // =====================================================================

    public static final int CINDER_BITE_COOLDOWN_TICKS = 20 * 5;
    private static final double CINDER_BITE_BONUS_DAMAGE = 1.50D;
    private static final double BURNING_CINDER_BONUS_DAMAGE = 2.50D;
    private static final int ASHEN_DURATION_TICKS = 20 * 8;

    // =====================================================================
    // Ash Cloud
    // =====================================================================

    public static final int ASH_CLOUD_DURATION_TICKS = 20 * 5;
    public static final int ASH_CLOUD_COOLDOWN_TICKS = 20 * 14;
    private static final double ASH_CLOUD_RADIUS = 5.5D;

    // =====================================================================
    // Ash Step
    // =====================================================================

    public static final int ASH_STEP_DURATION_TICKS = 8;
    public static final int ASH_STEP_COOLDOWN_TICKS = 20 * 9;

    // =====================================================================
    // Smoke Veil
    // =====================================================================

    public static final int SMOKE_VEIL_DURATION_TICKS = 20 * 8;
    public static final int SMOKE_VEIL_COOLDOWN_TICKS = 20 * 20;
    private static final double SMOKE_VEIL_RADIUS = 10.0D;
    private static final double SMOKE_VEIL_SCAN_RADIUS = 14.0D;

    // =====================================================================
    // Cinderstorm
    // =====================================================================

    public static final int CINDERSTORM_DURATION_TICKS = 20 * 9;
    public static final int CINDERSTORM_COOLDOWN_TICKS = 20 * 40;
    private static final double CINDERSTORM_RADIUS = 8.0D;

    /**
     * Visual-only Cinderstorm identity: a rotating low ember ring around the
     * fixed storm center. This intentionally avoids the broad gray ASH/SMOKE
     * language used by Ashes to Ashes.
     */
    private void sendCinderstormRing(ServerLevel level, boolean activation) {
        if (cinderstormCenter == null) return;

        int points = activation ? 24 : 16;
        double radius = activation
                ? CINDERSTORM_RADIUS * 0.72D
                : CINDERSTORM_RADIUS * 0.64D;
        double rotation = this.tickCount * 0.12D;

        for (int i = 0; i < points; ++i) {
            double angle = (Math.PI * 2.0D * i) / points + rotation;
            double x = cinderstormCenter.x + Math.cos(angle) * radius;
            double z = cinderstormCenter.z + Math.sin(angle) * radius;
            double y = cinderstormCenter.y + 0.20D
                    + ((i & 1) == 0 ? 0.10D : 0.35D);

            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.FLAME,
                    x, y, z,
                    1,
                    0.02D, 0.06D, 0.02D,
                    0.004D);

            if (activation && i % 3 == 0) {
                WolfVfx.sendParticles("ash_wolf", level,
                        ParticleTypes.LAVA,
                        x, y + 0.05D, z,
                        1,
                        0.01D, 0.02D, 0.01D,
                        0.0D);
            }
        }
    }

    // =====================================================================
    // Ashes to Ashes — ultimate
    // =====================================================================

    public static final int ASHES_TO_ASHES_DURATION_TICKS = 20 * 14;
    public static final int ASHES_TO_ASHES_COOLDOWN_TICKS = 20 * 60;
    private static final double ASHES_TO_ASHES_RADIUS = 14.0D;

    // =====================================================================
    // Runtime state
    // =====================================================================

    private int cinderBiteCooldownTicks;
    private int ashCloudCooldownTicks;
    private int ashStepCooldownTicks;
    private int smokeVeilCooldownTicks;
    private int cinderstormCooldownTicks;
    private int ashesToAshesCooldownTicks;

    private int ashCloudTicks;
    private Vec3 ashCloudCenter;

    private int ashStepTicks;
    private Vec3 ashStepDirection = Vec3.ZERO;

    private int smokeVeilTicks;

    private int cinderstormTicks;
    private Vec3 cinderstormCenter;

    private int ashesToAshesTicks;

    /** Per-Ash transient Ashen memory; intentionally rebuilt after reload. */
    private final Map<Integer, Integer> ashenTargets = new HashMap<>();

    // =====================================================================
    // Brain
    // =====================================================================

    private static final class BrainHolder {
        private static final Brain.Provider<AshWolf> PROVIDER =
                Brain.<AshWolf>provider(
                        ImmutableList.of(ModSensorTypes.ASH_AWARENESS.get()),
                        wolf -> List.of());
    }

    public AshWolf(EntityType<? extends AshWolf> type, Level level) {
        super(type, level);

        /* Ash is comfortable around heat, but she does NOT deliberately path into lava. */
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ASH_VISUAL_ACTIVE, false);
        builder.define(DATA_ASH_STEP_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.ASH_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ARMOR, BASE_ARMOR)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float oldHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(oldHealth, this.getMaxHealth()));
        }
    }

    // =====================================================================
    // Brain plumbing
    // =====================================================================

    @Override
    protected Brain<AshWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<AshWolf> getBrain() {
        return (Brain<AshWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    // =====================================================================
    // Fire / smoke identity
    // =====================================================================

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        }

        float adjusted = damage;

        /*
         * Ash survives ranged pressure through evasion, not armor stacking.
         * Smoke-step and the ultimate therefore mitigate projectiles only.
         */
        if (source.getDirectEntity() instanceof Projectile) {
            if (ashStepTicks > 0) {
                adjusted *= 0.55F;
            } else if (ashesToAshesTicks > 0) {
                adjusted *= 0.75F;
            }
        }

        return super.hurtServer(level, source, adjusted);
    }

    // =====================================================================
    // Main server tick
    // =====================================================================

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) return;

        tickCooldowns();
        tickAshenMemory(level);

        if (this.tickCount % 15 == 0) {
            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.ASH,
                    this.getX(), this.getY() + 0.55D, this.getZ(),
                    this.isBaby() ? 1 : 3,
                    0.28D, 0.25D, 0.28D,
                    0.01D);
        }

        if (this.isBaby()) {
            cancelAllAshActivity();
            this.setTarget(null);
            return;
        }

        /*
         * OWNER SIT/STAY IS ABSOLUTE.
         *
         * Ash Step and Ashes to Ashes can inject real horizontal velocity. Merely
         * cancelling their timers is not enough because that already-issued motion
         * can survive for several ticks and make Ash visibly slide/walk while the
         * renderer shows the sitting pose.
         *
         * Keep the vanilla synchronized sitting pose authoritative, stop navigation,
         * cancel every active Ash state, and explicitly kill residual horizontal
         * ability motion. Vertical motion is preserved so knockback/falls remain
         * physically sane.
         */
        if (this.isOrderedToSit() || this.isInSittingPose()) {
            enforceAshSitCommand();
            return;
        }

        tickActiveAbilities(level);

        if (!this.canUseActiveWolfismAbility()) {
            syncAshVisualState();
            return;
        }

        if (this.tickCount % DECISION_INTERVAL == 0) {
            tickAshDecisionBrain(level);
        }

        syncAshVisualState();
    }

    private void tickCooldowns() {
        if (cinderBiteCooldownTicks > 0) --cinderBiteCooldownTicks;
        if (ashCloudCooldownTicks > 0) --ashCloudCooldownTicks;
        if (ashStepCooldownTicks > 0) --ashStepCooldownTicks;
        if (smokeVeilCooldownTicks > 0) --smokeVeilCooldownTicks;
        if (cinderstormCooldownTicks > 0) --cinderstormCooldownTicks;
        if (ashesToAshesCooldownTicks > 0) --ashesToAshesCooldownTicks;
    }

    private void tickActiveAbilities(ServerLevel level) {
        if (ashStepTicks > 0) {
            tickAshStep(level);
        }

        if (ashCloudTicks > 0) {
            --ashCloudTicks;
            if (this.tickCount % 5 == 0 && ashCloudCenter != null) {
                pulseAshCloud(level, ashCloudCenter, ASH_CLOUD_RADIUS, false);
            }
            if (ashCloudTicks == 0) ashCloudCenter = null;
        }

        if (smokeVeilTicks > 0) {
            --smokeVeilTicks;
            if (this.tickCount % 5 == 0) {
                pulseSmokeVeil(level);
            }
        }

        if (cinderstormTicks > 0) {
            --cinderstormTicks;
            if (this.tickCount % 5 == 0 && cinderstormCenter != null) {
                pulseCinderstorm(level);
            }
            if (cinderstormTicks == 0) cinderstormCenter = null;
        }

        if (ashesToAshesTicks > 0) {
            --ashesToAshesTicks;

            if (this.tickCount % 5 == 0) {
                pulseAshesToAshes(level);
            }

            this.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    12,
                    1,
                    true,
                    false), this);

            if (ashesToAshesTicks == 0) {
                ashBurst(level, this, 18, ParticleTypes.POOF);
            }
        }
    }

    // =====================================================================
    // Contextual decision AI
    // =====================================================================

    private void tickAshDecisionBrain(ServerLevel level) {
        LivingEntity priority = getPriorityThreat();
        LivingEntity ranged = getRangedThreat();
        LivingEntity burning = getBurningThreat();
        LivingEntity vulnerable = getVulnerableFamily();

        int hostileCount = getHostileCount();
        int clusterSize = getClusterSize();
        int focusingAsh = getFocusingCount();

        if (priority == null) return;

        if (ashesToAshesCooldownTicks <= 0
                && shouldStartAshesToAshes(
                hostileCount,
                clusterSize,
                ranged,
                vulnerable)) {
            startAshesToAshes(level);
            return;
        }

        if (ashStepCooldownTicks <= 0
                && shouldUseAshStep(focusingAsh, priority)
                && startAshStep(level, priority)) {
            return;
        }

        if (smokeVeilCooldownTicks <= 0
                && ranged != null
                && (vulnerable != null || isThreateningAshFamily(ranged))
                && startSmokeVeil(level)) {
            return;
        }

        if (cinderstormCooldownTicks <= 0
                && (clusterSize >= 4
                || (burning != null && hostileCount >= 3))
                && startCinderstorm(level, priority)) {
            return;
        }

        if (ashCloudCooldownTicks <= 0
                && (clusterSize >= 2 || ranged != null)) {
            startAshCloud(level, ranged != null ? ranged : priority);
        }
    }

    private boolean shouldUseAshStep(int focusingAsh, LivingEntity priority) {
        if (priority == null) return false;
        if (focusingAsh >= 2) return true;

        return this.getHealth() <= this.getMaxHealth() * 0.35F
                && this.distanceToSqr(priority) <= 7.0D * 7.0D;
    }

    private boolean shouldStartAshesToAshes(
            int hostileCount,
            int clusterSize,
            LivingEntity ranged,
            LivingEntity vulnerable) {

        if (hostileCount >= 5) return true;
        if (clusterSize >= 4 && ranged != null) return true;

        return this.hasFamilyDefenseEmergency()
                && vulnerable != null
                && hostileCount >= 3;
    }

    // =====================================================================
    // Cinder Bite
    // =====================================================================

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        LivingEntity living = target instanceof LivingEntity entity
                ? entity
                : null;

        boolean cinderReady = living != null
                && !this.isBaby()
                && cinderBiteCooldownTicks <= 0
                && isValidAshThreat(living);

        boolean burningBeforeHit = cinderReady && living.isOnFire();

        /* Same-hit bonus avoids Minecraft's immediate hurt-invulnerability window. */
        AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double oldBase = attackDamage == null
                ? 0.0D
                : attackDamage.getBaseValue();

        if (cinderReady && attackDamage != null) {
            double bonus = CINDER_BITE_BONUS_DAMAGE
                    + (burningBeforeHit ? BURNING_CINDER_BONUS_DAMAGE : 0.0D);
            attackDamage.setBaseValue(oldBase + bonus);
        }

        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (cinderReady && attackDamage != null) {
                attackDamage.setBaseValue(oldBase);
            }
        }

        if (!hit || !cinderReady || living == null) return hit;

        cinderBiteCooldownTicks = CINDER_BITE_COOLDOWN_TICKS;
        applyAshen(living, ASHEN_DURATION_TICKS);

        living.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                60,
                0,
                true,
                true), this);

        living.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                80,
                0,
                true,
                true), this);

        ashBurst(level, living, 8, ParticleTypes.ASH);

        if (burningBeforeHit) {
            living.knockback(
                    0.30D,
                    this.getX() - living.getX(),
                    this.getZ() - living.getZ());

            ashBurst(level, living, 9, ParticleTypes.FLAME);
            ashBurst(level, living, 7, ParticleTypes.SMOKE);
        }

        return true;
    }

    // =====================================================================
    // Ash Cloud
    // =====================================================================

    private boolean startAshCloud(ServerLevel level, LivingEntity target) {
        if (ashCloudCooldownTicks > 0
                || target == null
                || !isValidAshThreat(target)) {
            return false;
        }

        ashCloudCenter = target.position();
        ashCloudTicks = ASH_CLOUD_DURATION_TICKS;
        ashCloudCooldownTicks = ASH_CLOUD_COOLDOWN_TICKS;

        pulseAshCloud(level, ashCloudCenter, ASH_CLOUD_RADIUS, true);
        return true;
    }

    private void pulseAshCloud(
            ServerLevel level,
            Vec3 center,
            double radius,
            boolean activation) {

        if (activation || this.isWolfismWorkTick(10)) {
            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.ASH,
                    center.x, center.y + 0.55D, center.z,
                    activation ? 32 : 13,
                    radius * 0.55D, 0.75D, radius * 0.55D,
                    0.015D);

            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.SMOKE,
                    center.x, center.y + 0.45D, center.z,
                    activation ? 20 : 8,
                    radius * 0.45D, 0.55D, radius * 0.45D,
                    0.02D);
        }

        for (LivingEntity enemy : getThreatsAround(level, center, radius)) {
            applyPerceptionDebuffs(enemy, 30, false);

            if (enemy instanceof Mob mob
                    && mob.getTarget() != null
                    && isAshFamilyMember(mob.getTarget())
                    && this.isWolfismWorkTick(10)
                    && this.random.nextFloat() < 0.50F) {
                disruptMobAction(mob, true);
            }
        }
    }

    // =====================================================================
    // Ash Step — physical evasive burst, explicitly NOT teleportation
    // =====================================================================

    private boolean startAshStep(ServerLevel level, LivingEntity threat) {
        if (ashStepCooldownTicks > 0
                || !this.canUseActiveWolfismAbility()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || threat == null
                || !threat.isAlive()) {
            return false;
        }

        Vec3 away = this.position().subtract(threat.position());
        away = new Vec3(away.x, 0.0D, away.z);

        if (away.lengthSqr() < 1.0E-4D) {
            away = this.getLookAngle().scale(-1.0D);
            away = new Vec3(away.x, 0.0D, away.z);
        }

        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                .scale(this.random.nextBoolean() ? 1.0D : -1.0D);

        ashStepDirection = side.scale(0.82D)
                .add(away.scale(0.38D))
                .normalize();

        ashStepTicks = ASH_STEP_DURATION_TICKS;
        ashStepCooldownTicks = ASH_STEP_COOLDOWN_TICKS;

        this.getNavigation().stop();
        this.setSprinting(true);
        this.setDeltaMovement(
                ashStepDirection.x * 0.95D,
                Math.max(this.getDeltaMovement().y, 0.08D),
                ashStepDirection.z * 0.95D);

        ashBurst(level, this, 20, ParticleTypes.SMOKE);
        ashBurst(level, this, 10, ParticleTypes.ASH);
        syncAshVisualState();
        return true;
    }

    private void tickAshStep(ServerLevel level) {
        --ashStepTicks;

        Vec3 motion = this.getDeltaMovement();
        double horizontal = motion.horizontalDistanceSqr();

        if (horizontal < 0.20D * 0.20D
                && ashStepDirection.lengthSqr() > 0.5D) {
            this.setDeltaMovement(
                    ashStepDirection.x * 0.58D,
                    motion.y,
                    ashStepDirection.z * 0.58D);
        }

        if (this.tickCount % 2 == 0) {
            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.35D, this.getZ(),
                    3,
                    0.16D, 0.10D, 0.16D,
                    0.015D);
        }

        if (ashStepTicks <= 0) {
            ashStepTicks = 0;
            ashStepDirection = Vec3.ZERO;
            this.setSprinting(false);
        }
    }

    // =====================================================================
    // Smoke Veil — family support through hostile detection disruption
    // =====================================================================

    private boolean startSmokeVeil(ServerLevel level) {
        if (smokeVeilCooldownTicks > 0 || !this.isTame()) return false;

        smokeVeilTicks = SMOKE_VEIL_DURATION_TICKS;
        smokeVeilCooldownTicks = SMOKE_VEIL_COOLDOWN_TICKS;

        WolfVfx.sendParticles("ash_wolf", level,
                ParticleTypes.ASH,
                this.getX(), this.getY() + 0.45D, this.getZ(),
                34,
                SMOKE_VEIL_RADIUS * 0.55D,
                0.85D,
                SMOKE_VEIL_RADIUS * 0.55D,
                0.02D);

        pulseSmokeVeil(level);
        return true;
    }

    private void pulseSmokeVeil(ServerLevel level) {
        if (!this.isTame()) return;

        if (this.isWolfismWorkTick(10)) {
            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.45D, this.getZ(),
                    10,
                    SMOKE_VEIL_RADIUS * 0.50D,
                    0.60D,
                    SMOKE_VEIL_RADIUS * 0.50D,
                    0.018D);
        }

        for (LivingEntity enemy : getNearbyAshThreats(level, SMOKE_VEIL_SCAN_RADIUS)) {
            if (!(enemy instanceof Mob mob)) continue;

            LivingEntity victim = mob.getTarget();
            if (victim == null
                    || !isAshFamilyMember(victim)
                    || victim.distanceToSqr(this) > SMOKE_VEIL_RADIUS * SMOKE_VEIL_RADIUS) {
                continue;
            }

            mob.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    25,
                    0,
                    true,
                    true), this);

            if (isRangedAshThreat(enemy)) {
                enemy.stopUsingItem();
                if (enemy instanceof Pillager pillager) {
                    pillager.setChargingCrossbow(false);
                }
            }

            if (this.isWolfismWorkTick(10)
                    && this.random.nextFloat() < 0.65F) {
                disruptMobAction(mob, true);
            }
        }
    }

    // =====================================================================
    // Cinderstorm
    // =====================================================================

    private boolean startCinderstorm(ServerLevel level, LivingEntity target) {
        if (cinderstormCooldownTicks > 0
                || target == null
                || !isValidAshThreat(target)) {
            return false;
        }

        cinderstormCenter = target.position();
        cinderstormTicks = CINDERSTORM_DURATION_TICKS;
        cinderstormCooldownTicks = CINDERSTORM_COOLDOWN_TICKS;

        /*
         * Cinderstorm must read as a fiery ember cyclone, not as a smaller
         * Ashes-to-Ashes cloud. Keep Ashes to Ashes gray/obscuring; make this
         * fixed-area control field visibly orange, hot, and ring-shaped.
         */
        WolfVfx.sendParticles("ash_wolf", level,
                ParticleTypes.LAVA,
                cinderstormCenter.x,
                cinderstormCenter.y + 0.35D,
                cinderstormCenter.z,
                22,
                CINDERSTORM_RADIUS * 0.42D,
                0.30D,
                CINDERSTORM_RADIUS * 0.42D,
                0.0D);

        WolfVfx.sendParticles("ash_wolf", level,
                ParticleTypes.FLAME,
                cinderstormCenter.x,
                cinderstormCenter.y + 0.60D,
                cinderstormCenter.z,
                34,
                CINDERSTORM_RADIUS * 0.55D,
                0.75D,
                CINDERSTORM_RADIUS * 0.55D,
                0.028D);

        sendCinderstormRing(level, true);
        pulseCinderstorm(level);
        return true;
    }

    private void pulseCinderstorm(ServerLevel level) {
        if (cinderstormCenter == null) return;

        if (this.isWolfismWorkTick(10)) {
            sendCinderstormRing(level, false);

            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.FLAME,
                    cinderstormCenter.x,
                    cinderstormCenter.y + 0.55D,
                    cinderstormCenter.z,
                    12,
                    CINDERSTORM_RADIUS * 0.38D,
                    0.55D,
                    CINDERSTORM_RADIUS * 0.38D,
                    0.018D);
        }

        for (LivingEntity enemy : getThreatsAround(
                level,
                cinderstormCenter,
                CINDERSTORM_RADIUS)) {

            applyPerceptionDebuffs(enemy, 30, true);

            boolean primed = enemy.isOnFire() || isAshen(enemy);
            if (primed && this.isWolfismWorkTick(20)) {
                enemy.hurtServer(
                        level,
                        this.damageSources().mobAttack(this),
                        enemy.isOnFire() ? 2.75F : 1.75F);

                ashBurst(level, enemy, 7, ParticleTypes.FLAME);
                ashBurst(level, enemy, 5, ParticleTypes.SMOKE);
            }
        }
    }

    // =====================================================================
    // Ashes to Ashes — ultimate
    // =====================================================================

    private boolean startAshesToAshes(ServerLevel level) {
        if (ashesToAshesCooldownTicks > 0
                || ashesToAshesTicks > 0
                || !this.canUseActiveWolfismAbility()) {
            return false;
        }

        ashesToAshesTicks = ASHES_TO_ASHES_DURATION_TICKS;
        ashesToAshesCooldownTicks = ASHES_TO_ASHES_COOLDOWN_TICKS;

        /* Ultimate subsumes the smaller smoke fields for a clean state. */
        ashCloudTicks = 0;
        ashCloudCenter = null;
        smokeVeilTicks = 0;
        cinderstormTicks = 0;
        cinderstormCenter = null;

        WolfVfx.sendParticles("ash_wolf", level,
                ParticleTypes.ASH,
                this.getX(), this.getY() + 0.70D, this.getZ(),
                70,
                7.0D, 1.40D, 7.0D,
                0.035D);

        WolfVfx.sendParticles("ash_wolf", level,
                ParticleTypes.SMOKE,
                this.getX(), this.getY() + 0.55D, this.getZ(),
                44,
                6.0D, 1.00D, 6.0D,
                0.025D);

        pulseAshesToAshes(level);
        syncAshVisualState();
        return true;
    }

    private void pulseAshesToAshes(ServerLevel level) {
        if (this.isWolfismWorkTick(10)) {
            WolfVfx.sendParticles("ash_wolf", level,
                    ParticleTypes.ASH,
                    this.getX(), this.getY() + 0.65D, this.getZ(),
                    24,
                    6.5D, 1.20D, 6.5D,
                    0.025D);
        }

        for (LivingEntity enemy : getNearbyAshThreats(
                level,
                ASHES_TO_ASHES_RADIUS)) {

            applyPerceptionDebuffs(enemy, 35, true);
            applyAshen(enemy, Math.max(getAshenTicks(enemy), 80));

            if (enemy instanceof Mob mob
                    && mob.getTarget() != null
                    && isAshFamilyMember(mob.getTarget())
                    && this.isWolfismWorkTick(10)) {

                if (isRangedAshThreat(enemy)) {
                    disruptMobAction(mob, true);
                } else if (this.random.nextFloat() < 0.60F) {
                    disruptMobAction(mob, true);
                }
            }

            if (enemy.isOnFire() && this.isWolfismWorkTick(20)) {
                enemy.hurtServer(
                        level,
                        this.damageSources().mobAttack(this),
                        2.50F);
                ashBurst(level, enemy, 8, ParticleTypes.FLAME);
            }
        }

        /* If multiple mobs hard-focus Ash, she becomes much harder to pin down. */
        if (getFocusingCount() >= 2
                && ashStepTicks <= 0
                && this.isWolfismWorkTick(20)) {
            LivingEntity priority = getPriorityThreat();
            if (priority != null) {
                Vec3 away = this.position().subtract(priority.position());
                away = new Vec3(away.x, 0.0D, away.z);
                if (away.lengthSqr() > 1.0E-4D) {
                    Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                            .normalize()
                            .scale(this.random.nextBoolean() ? 0.34D : -0.34D);
                    this.setDeltaMovement(
                            this.getDeltaMovement().add(side));
                }
            }
        }
    }

    // =====================================================================
    // Perception-control helpers
    // =====================================================================

    private void applyPerceptionDebuffs(
            LivingEntity enemy,
            int duration,
            boolean strong) {

        enemy.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                duration,
                0,
                true,
                true), this);

        enemy.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                duration,
                strong ? 1 : 0,
                true,
                true), this);

        enemy.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                duration,
                strong ? 1 : 0,
                true,
                true), this);

        if (isRangedAshThreat(enemy)) {
            enemy.stopUsingItem();
            if (enemy instanceof Pillager pillager) {
                pillager.setChargingCrossbow(false);
            }
        }
    }

    private void disruptMobAction(Mob mob, boolean clearTarget) {
        mob.stopUsingItem();
        mob.getNavigation().stop();

        if (mob instanceof Pillager pillager) {
            pillager.setChargingCrossbow(false);
        }

        if (clearTarget) {
            mob.setTarget(null);
        }
    }

    // =====================================================================
    // Ashen memory
    // =====================================================================

    private void applyAshen(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) return;
        ashenTargets.merge(target.getId(), ticks, Math::max);
    }

    private boolean isAshen(LivingEntity target) {
        return target != null
                && ashenTargets.getOrDefault(target.getId(), 0) > 0;
    }

    private int getAshenTicks(LivingEntity target) {
        return target == null
                ? 0
                : ashenTargets.getOrDefault(target.getId(), 0);
    }

    private void tickAshenMemory(ServerLevel level) {
        if (ashenTargets.isEmpty()) return;

        Iterator<Map.Entry<Integer, Integer>> it =
                ashenTargets.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int ticks = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());

            if (ticks <= 1
                    || !(entity instanceof LivingEntity living)
                    || !living.isAlive()) {
                it.remove();
            } else {
                entry.setValue(ticks - 1);
            }
        }
    }

    // =====================================================================
    // Awareness helpers — public for AshWolfAwarenessSensor
    // =====================================================================

    public boolean isValidAshThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || isAshFamilyMember(target)
                || this.isAlliedTo(target)
                || (!(target instanceof Enemy)
                && !isBossAshTarget(target))) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public boolean isRangedAshThreat(LivingEntity target) {
        if (!isValidAshThreat(target)) return false;

        return target instanceof RangedAttackMob
                || target.isUsingItem()
                || (target instanceof Pillager pillager
                && pillager.isChargingCrossbow());
    }

    public boolean isBurningAshThreat(LivingEntity target) {
        return isValidAshThreat(target) && target.isOnFire();
    }

    public boolean isAshFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            return this.isWolfismFamily(entity);
        }

        return entity instanceof AshWolf ash && !ash.isTame();
    }

    public boolean isVulnerableAshFamilyMember(LivingEntity member) {
        if (!isAshFamilyMember(member) || member == this) return false;
        return member.getHealth() <= member.getMaxHealth() * 0.45F;
    }

    public boolean isThreateningAshFamily(LivingEntity target) {
        if (!(target instanceof Mob mob)) return false;
        LivingEntity victim = mob.getTarget();
        return victim != null && isAshFamilyMember(victim);
    }

    public double ashThreatScore(LivingEntity target) {
        if (!isValidAshThreat(target)) return Double.MAX_VALUE;

        double score = Math.sqrt(this.distanceToSqr(target));

        if (target == this.getFamilyDefenseTarget()) score -= 500.0D;
        if (isThreateningAshFamily(target)) score -= 100.0D;
        if (isRangedAshThreat(target)) score -= 40.0D;
        if (target.isOnFire()) score -= 18.0D;
        if (isBossAshTarget(target)) score -= 14.0D;

        return score;
    }

    private boolean isBossAshTarget(LivingEntity target) {
        return target instanceof WitherBoss
                || target instanceof EnderDragon;
    }

    public List<AbstractWolfismWolf> getNearbyAshFamily(
            ServerLevel level,
            double radius) {

        if (!this.isTame()) return List.of();

        LivingEntity owner = this.getOwner();
        if (owner == null) return List.of();

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        AbstractWolfismWolf.class,
                        this.getBoundingBox().inflate(radius),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && wolf.isOwnedBy(owner))
                .stream()
                .filter(wolf -> this.distanceToSqr(wolf) <= radiusSqr)
                .toList();
    }

    public List<LivingEntity> getNearbyAshThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isValidAshThreat)
                .stream()
                .filter(enemy -> this.distanceToSqr(enemy) <= radiusSqr)
                .toList();
    }

    private List<LivingEntity> getThreatsAround(
            ServerLevel level,
            Vec3 center,
            double radius) {

        AABB box = new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius);

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        this::isValidAshThreat)
                .stream()
                .filter(enemy -> enemy.position().distanceToSqr(center) <= radiusSqr)
                .toList();
    }

    // =====================================================================
    // Brain memory getters
    // =====================================================================

    private LivingEntity getPriorityThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_PRIORITY_THREAT.get())
                .orElse(null);
    }

    private LivingEntity getRangedThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_RANGED_THREAT.get())
                .orElse(null);
    }

    private LivingEntity getBurningThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_BURNING_THREAT.get())
                .orElse(null);
    }

    private LivingEntity getVulnerableFamily() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_VULNERABLE_FAMILY.get())
                .orElse(null);
    }

    private int getHostileCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_HOSTILE_COUNT.get())
                .orElse(0);
    }

    private int getClusterSize() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_CLUSTER_SIZE.get())
                .orElse(0);
    }

    private int getFocusingCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.ASH_FOCUSING_COUNT.get())
                .orElse(0);
    }

    // =====================================================================
    // Visual state / utility
    // =====================================================================

    public boolean isAshCombatVisualActive() {
        return this.entityData.get(DATA_ASH_VISUAL_ACTIVE);
    }

    public boolean isAshStepActive() {
        return this.entityData.get(DATA_ASH_STEP_ACTIVE);
    }

    public boolean isAshesToAshesActive() {
        return ashesToAshesTicks > 0;
    }

    private void syncAshVisualState() {
        boolean active = ashCloudTicks > 0
                || smokeVeilTicks > 0
                || cinderstormTicks > 0
                || ashesToAshesTicks > 0
                || ashStepTicks > 0;

        this.entityData.set(DATA_ASH_VISUAL_ACTIVE, active);
        this.entityData.set(DATA_ASH_STEP_ACTIVE, ashStepTicks > 0);
    }

    private void ashBurst(
            ServerLevel level,
            LivingEntity center,
            int count,
            ParticleOptions particle) {

        WolfVfx.sendParticles("ash_wolf", level,
                particle,
                center.getX(),
                center.getY() + center.getBbHeight() * 0.55D,
                center.getZ(),
                count,
                0.34D, 0.25D, 0.34D,
                0.025D);
    }

    /**
     * Hard movement lock for the owner's sit/stay order.
     *
     * <p>This is intentionally separate from {@link #cancelAllAshActivity()}:
     * cancelling ability state should not globally zero motion during ordinary
     * gameplay, while sitting specifically must erase Ash Step / ultimate drift.</p>
     */
    private void enforceAshSitCommand() {
        cancelAllAshActivity();

        this.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);

        Vec3 motion = this.getDeltaMovement();
        if (Math.abs(motion.x) > 1.0E-4D
                || Math.abs(motion.z) > 1.0E-4D) {
            this.setDeltaMovement(
                    0.0D,
                    motion.y,
                    0.0D);
            this.hurtMarked = true;
        }
    }

    private void cancelAllAshActivity() {
        ashCloudTicks = 0;
        ashCloudCenter = null;

        ashStepTicks = 0;
        ashStepDirection = Vec3.ZERO;

        smokeVeilTicks = 0;

        cinderstormTicks = 0;
        cinderstormCenter = null;

        ashesToAshesTicks = 0;

        this.setSprinting(false);
        syncAshVisualState();
    }


    // =====================================================================
    // Natural spawning — Basalt Deltas
    // =====================================================================

    /**
     * Ash Wolf's natural habitat is the Basalt Deltas.
     *
     * <p>The biome modifier performs the biome lock. This predicate handles
     * physical safety, Nether-only validation, and local wild-Ash separation.
     * Spawn eggs, commands, breeding, and other explicit creation are never
     * blocked by these natural-spawn restrictions.</p>
     */
    public static boolean checkAshWolfSpawnRules(
            EntityType<AshWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        ServerLevel serverLevel = level.getLevel();

        if (serverLevel.dimension() != Level.NETHER) {
            return false;
        }

        /*
         * Ash survives heat, but her identity is not lava swimming/walking.
         * Natural spawns therefore require dry, unobstructed body space.
         */
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(pos.above())
                .getCollisionShape(level, pos.above())
                .isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();

        if (!level.getBlockState(floor)
                .isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        /*
         * Wild Ash Wolves are rare solitary encounters.
         * Tamed Ash Wolves never suppress spawning.
         * Use the supplied region's entity view during chunk generation.
         */
        boolean anotherWildAsh =
                !level.getEntitiesOfClass(
                                AshWolf.class,
                                new AABB(pos).inflate(48.0D, 24.0D, 48.0D),
                                wolf -> wolf.isAlive() && !wolf.isTame())
                        .isEmpty();

        return !anotherWildAsh;
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt("AshCinderBiteCooldown", cinderBiteCooldownTicks);
        output.putInt("AshCloudCooldown", ashCloudCooldownTicks);
        output.putInt("AshStepCooldown", ashStepCooldownTicks);
        output.putInt("AshSmokeVeilCooldown", smokeVeilCooldownTicks);
        output.putInt("AshCinderstormCooldown", cinderstormCooldownTicks);
        output.putInt("AshesToAshesCooldown", ashesToAshesCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        cinderBiteCooldownTicks = Math.max(
                0,
                input.getIntOr("AshCinderBiteCooldown", 0));
        ashCloudCooldownTicks = Math.max(
                0,
                input.getIntOr("AshCloudCooldown", 0));
        ashStepCooldownTicks = Math.max(
                0,
                input.getIntOr("AshStepCooldown", 0));
        smokeVeilCooldownTicks = Math.max(
                0,
                input.getIntOr("AshSmokeVeilCooldown", 0));
        cinderstormCooldownTicks = Math.max(
                0,
                input.getIntOr("AshCinderstormCooldown", 0));
        ashesToAshesCooldownTicks = Math.max(
                0,
                input.getIntOr("AshesToAshesCooldown", 0));

        ashenTargets.clear();
        cancelAllAshActivity();
    }
}
