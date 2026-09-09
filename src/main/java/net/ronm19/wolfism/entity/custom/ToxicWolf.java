package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Toxic Wolf
 *
 * <p>Five-star Toxic / Radiation / Ranged / Area-Denial specialist.</p>
 *
 * <p>Core identity:</p>
 * <ul>
 *     <li>Radiation is separate from vanilla Poison.</li>
 *     <li>Exposure escalates while enemies remain contaminated.</li>
 *     <li>Reactive Eye gives long-range awareness without creating marathon chase AI.</li>
 *     <li>Radiation Area and Radioactive Rain deny hostile space without terrain griefing.</li>
 *     <li>Family is never intentionally harmed or selected by Toxic's hazards.</li>
 * </ul>
 */
public final class ToxicWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Baseline
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 50.0D;

    // =====================================================================
    // Exposure
    // =====================================================================


    private static final float MAX_EXPOSURE = 100.0F;
    private static final float EXPOSURE_DECAY_PER_SECOND = 4.0F;
    private static final int EXPOSURE_DECAY_GRACE_TICKS = 20 * 3;

    private static final float EXPOSURE_TIER_1 = 15.0F;
    private static final float EXPOSURE_TIER_2 = 35.0F;
    private static final float EXPOSURE_TIER_3 = 60.0F;
    private static final float EXPOSURE_TIER_4 = 85.0F;

    // =====================================================================
    // Radioactive Projectile
    // =====================================================================

    public static final int RADIOACTIVE_PROJECTILE_COOLDOWN_TICKS = 20 * 6;
    public static final double REACTIVE_EYE_BASE_RANGE = 20.0D;
    public static final double REACTIVE_EYE_MAX_RANGE = 40.0D;

    private static final double PROJECTILE_SPEED = 1.25D;
    private static final int PROJECTILE_MAX_LIFE = 38;
    private static final double PROJECTILE_HIT_DISTANCE_SQR = 1.30D * 1.30D;

    private static final float PROJECTILE_IMPACT_DAMAGE = 7.5F;
    private static final float PROJECTILE_RADIATION_DAMAGE = 2.0F;
    private static final float PROJECTILE_EXPOSURE = 24.0F;

    // =====================================================================
    // Radiation Area
    // =====================================================================

    public static final int RADIATION_AREA_COOLDOWN_TICKS = 20 * 28;
    private static final int RADIATION_AREA_DURATION_TICKS = 20 * 12;
    private static final double RADIATION_AREA_RADIUS = 9.5D;
    private static final double RADIATION_AREA_SURGE_RADIUS = 11.0D;
    private static final int RADIATION_AREA_PULSE_INTERVAL = 10;
    private static final float RADIATION_AREA_EXPOSURE_PER_PULSE = 5.0F;

    // =====================================================================
    // Radioactive Surge
    // =====================================================================

    public static final int RADIOACTIVE_SURGE_COOLDOWN_TICKS = 20 * 35;
    private static final int RADIOACTIVE_SURGE_DURATION_TICKS = 20 * 11;
    private static final float SURGE_EXPOSURE_MULTIPLIER = 1.45F;
    private static final float SURGE_PROJECTILE_DAMAGE_MULTIPLIER = 1.25F;
    private static final float SURGE_RADIATION_DAMAGE_MULTIPLIER = 1.35F;

    // =====================================================================
    // Radioactive Rain
    // =====================================================================

    public static final int RADIOACTIVE_RAIN_COOLDOWN_TICKS = 20 * 70;
    private static final int RADIOACTIVE_RAIN_DURATION_TICKS = 20 * 12;
    private static final double RADIOACTIVE_RAIN_TARGET_SCAN = 32.0D;
    private static final double RADIOACTIVE_RAIN_CLOUD_RADIUS = 3.75D;
    private static final double RADIOACTIVE_RAIN_SURGE_RADIUS = 4.50D;
    private static final int RADIOACTIVE_RAIN_MAX_CLOUDS = 4;
    private static final int RADIOACTIVE_RAIN_PULSE_INTERVAL = 10;
    private static final float RADIOACTIVE_RAIN_EXPOSURE_PER_PULSE = 4.0F;
    private static final float RADIOACTIVE_RAIN_RADIATION_DAMAGE = 0.80F;
    private static final double RADIOACTIVE_RAIN_TRACK_SPEED = 0.55D;

    // =====================================================================
    // Brain
    // =====================================================================

    private static final int NORMAL_DECISION_INTERVAL = 8;
    private static final int SURGE_DECISION_INTERVAL = 4;

    // =====================================================================
    // Runtime state
    // =====================================================================

    private int radioactiveProjectileCooldownTicks;

    private int radiationAreaCooldownTicks;
    private int radiationAreaTicks;
    private Vec3 radiationAreaCenter;

    private int radioactiveSurgeCooldownTicks;
    private int radioactiveSurgeTicks;

    private int radioactiveRainCooldownTicks;
    private int radioactiveRainTicks;

    private final List<RadioactiveProjectileMarker> radioactiveProjectiles =
            new ArrayList<>();

    private final List<RadioactiveCloudMarker> radioactiveRainClouds =
            new ArrayList<>();

    private final Map<Integer, ExposureRecord> exposureLedger =
            new HashMap<>();

    // =====================================================================
    // Brain provider
    // =====================================================================

    private static final class BrainHolder {
        private static final Brain.Provider<ToxicWolf> PROVIDER =
                Brain.<ToxicWolf>provider(
                        ImmutableList.of(ModSensorTypes.TOXIC_TACTICAL.get()),
                        wolf -> List.of());
    }

    public ToxicWolf(EntityType<? extends ToxicWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.TOXIC_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.18D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    // =====================================================================
    // Brain
    // =====================================================================

    @Override
    protected Brain<ToxicWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ToxicWolf> getBrain() {
        return (Brain<ToxicWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    // =====================================================================
    // Combat validation
    // =====================================================================

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isToxicFamilyMember(target)
                && super.canAttack(target);
    }

    /**
     * Reactive Eye is awareness and ranged response, not permission to sprint
     * across the biome. We deliberately do not inflate physical aggro range.
     */
    public boolean canUseReactiveEyeAgainst(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !isValidToxicThreat(target)
                || !this.hasLineOfSight(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr > REACTIVE_EYE_MAX_RANGE * REACTIVE_EYE_MAX_RANGE) {
            return false;
        }

        // Base suppression range can engage normally up to ~20 blocks.
        if (distanceSqr <= REACTIVE_EYE_BASE_RANGE * REACTIVE_EYE_BASE_RANGE) {
            return true;
        }

        // Full 40-block Reactive Eye range is reserved for meaningful threats.
        if (target == this.getFamilyDefenseTarget()) {
            return true;
        }

        if (getExposure(target) > 0.0F) {
            return true;
        }

        if (target instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            if (mobTarget != null && isToxicFamilyMember(mobTarget)) {
                return true;
            }
        }

        return false;
    }

    // =====================================================================
    // Main tick
    // =====================================================================

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) return;

        // Toxic Immunity: vanilla Poison never sticks to Toxic Wolf.
        if (this.hasEffect(MobEffects.POISON)) {
            this.removeEffect(MobEffects.POISON);
        }

        tickCooldowns();
        tickExposureLedger(level);
        tickRadioactiveProjectiles(level);
        tickRadiationArea(level);
        tickRadioactiveSurge(level);
        tickRadioactiveRain(level);

        if (this.isBaby()) {
            cancelAdultToxicStates();
            this.setTarget(null);
            return;
        }

        int interval = radioactiveSurgeTicks > 0
                ? SURGE_DECISION_INTERVAL
                : NORMAL_DECISION_INTERVAL;

        if (this.tickCount % interval == 0) {
            tickToxicDecisionBrain(level);
        }
    }

    private void tickCooldowns() {
        if (radioactiveProjectileCooldownTicks > 0) {
            --radioactiveProjectileCooldownTicks;
        }
        if (radiationAreaCooldownTicks > 0) {
            --radiationAreaCooldownTicks;
        }
        if (radioactiveSurgeCooldownTicks > 0) {
            --radioactiveSurgeCooldownTicks;
        }
        if (radioactiveRainCooldownTicks > 0) {
            --radioactiveRainCooldownTicks;
        }
    }

    // =====================================================================
    // Exposure system
    // =====================================================================

    public float getExposure(LivingEntity entity) {
        if (entity == null) return 0.0F;
        ExposureRecord record = exposureLedger.get(entity.getId());
        return record == null ? 0.0F : record.exposure;
    }

    private void addExposure(LivingEntity target, float rawAmount) {
        /*
         * Exposure is contamination state, not an AI targeting decision.
         * Once a hostile is contaminated it must not lose radiation simply
         * because its current target/aggro state changes for a moment.
         */
        if (!canCarryToxicExposure(target) || rawAmount <= 0.0F) return;

        float amount = adjustExposureGainForTarget(target, rawAmount);
        if (radioactiveSurgeTicks > 0) {
            amount *= SURGE_EXPOSURE_MULTIPLIER;
        }

        ExposureRecord record = exposureLedger.computeIfAbsent(
                target.getId(),
                id -> new ExposureRecord(id));

        record.exposure = Math.min(MAX_EXPOSURE, record.exposure + amount);
        record.lastTouchedTick = this.tickCount;
    }

    private float adjustExposureGainForTarget(
            LivingEntity target,
            float rawAmount) {

        if (isBossToxicTarget(target)) {
            return rawAmount * 0.50F;
        }

        if (isEliteToxicTarget(target)) {
            return rawAmount * 0.85F;
        }

        return rawAmount;
    }

    private void tickExposureLedger(ServerLevel level) {
        if (exposureLedger.isEmpty()) return;

        /*
         * Consequences refresh twice per second so the escalation is readable.
         * Actual radiation damage and decay are still gated to once per second.
         */
        if (this.tickCount % 10 != 0) return;

        List<Integer> remove = new ArrayList<>();

        for (ExposureRecord record : exposureLedger.values()) {
            LivingEntity target = getLivingEntityById(level, record.entityId);

            if (target == null
                    || !target.isAlive()
                    || !canCarryToxicExposure(target)) {

                remove.add(record.entityId);
                continue;
            }

            if (this.tickCount % 20 == 0
                    && this.tickCount - record.lastTouchedTick
                    > EXPOSURE_DECAY_GRACE_TICKS) {

                record.exposure = Math.max(
                        0.0F,
                        record.exposure - EXPOSURE_DECAY_PER_SECOND);
            }

            if (record.exposure <= 0.0F) {
                remove.add(record.entityId);
                continue;
            }

            applyExposureConsequences(level, target, record.exposure);
        }

        for (int id : remove) {
            exposureLedger.remove(id);
        }
    }

    private void applyExposureConsequences(
            ServerLevel level,
            LivingEntity target,
            float exposure) {

        if (exposure < EXPOSURE_TIER_1) return;

        int weaknessAmplifier = exposure >= EXPOSURE_TIER_3 ? 1 : 0;

        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                30,
                weaknessAmplifier,
                true,
                false));

        if (exposure >= EXPOSURE_TIER_2) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.NAUSEA,
                    35,
                    0,
                    true,
                    false));

            // Poison is supplementary. Poison immunity never blocks radiation.
            target.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    35,
                    0,
                    true,
                    false));

            int slownessAmplifier = exposure >= EXPOSURE_TIER_4
                    ? 2
                    : exposure >= EXPOSURE_TIER_3
                    ? 1
                    : 0;

            target.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS,
                    30,
                    slownessAmplifier,
                    true,
                    false));
        }

        /*
         * High Exposure causes visible combat hesitation on mobs.
         * We stop navigation briefly rather than deleting their target, so
         * the effect reads as radiation sickness/disorientation without
         * permanently breaking their AI.
         */
        if (target instanceof Mob mob
                && exposure >= EXPOSURE_TIER_3
                && this.tickCount % 20 == 0) {

            float hesitationChance = exposure >= EXPOSURE_TIER_4
                    ? 0.60F
                    : 0.30F;

            if (this.getRandom().nextFloat() < hesitationChance) {
                mob.getNavigation().stop();

                Vec3 movement = mob.getDeltaMovement();
                mob.setDeltaMovement(
                        movement.x * 0.45D,
                        movement.y,
                        movement.z * 0.45D);
            }
        }

        /*
         * Damage is once per second. The escalating debuffs/hesitation refresh
         * twice per second, so Exposure remains obvious even when Minecraft's
         * normal hurt-invulnerability window rejects an occasional damage hit.
         */
        if (this.tickCount % 20 == 0) {
            float radiationDamage;

            if (exposure >= EXPOSURE_TIER_4) {
                radiationDamage = 5.0F;
            } else if (exposure >= EXPOSURE_TIER_3) {
                radiationDamage = 3.0F;
            } else if (exposure >= EXPOSURE_TIER_2) {
                radiationDamage = 1.5F;
            } else {
                radiationDamage = 0.5F;
            }

            if (isBossToxicTarget(target)) {
                radiationDamage *= 0.55F;
            } else if (isEliteToxicTarget(target)) {
                radiationDamage *= 0.85F;
            }

            target.hurtServer(
                    level,
                    this.damageSources().generic(),
                    radiationDamage);
        }

        spawnExposureParticles(level, target, exposure);
    }

    private void spawnExposureParticles(
            ServerLevel level,
            LivingEntity target,
            float exposure) {

        int count = exposure >= EXPOSURE_TIER_4
                ? 14
                : exposure >= EXPOSURE_TIER_3
                ? 10
                : exposure >= EXPOSURE_TIER_2
                ? 7
                : 4;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                count,
                0.35D, 0.35D, 0.35D,
                0.01D);

        if (exposure >= EXPOSURE_TIER_3) {
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.45D,
                    target.getZ(),
                    exposure >= EXPOSURE_TIER_4 ? 6 : 3,
                    0.30D, 0.28D, 0.30D,
                    0.008D);
        }
    }

    // =====================================================================
    // Radioactive Projectile
    // =====================================================================

    private boolean tryRadioactiveProjectile(
            ServerLevel level,
            LivingEntity target) {

        if (radioactiveProjectileCooldownTicks > 0
                || this.isBaby()
                || target == null
                || !canUseReactiveEyeAgainst(target)) {
            return false;
        }

        Vec3 start = this.getEyePosition().add(0.0D, -0.12D, 0.0D);

        radioactiveProjectiles.add(
                new RadioactiveProjectileMarker(
                        start,
                        target.getId(),
                        PROJECTILE_MAX_LIFE));

        radioactiveProjectileCooldownTicks =
                RADIOACTIVE_PROJECTILE_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                start.x, start.y, start.z,
                12,
                0.18D, 0.18D, 0.18D,
                0.02D);

        // Long-range Reactive Eye feedback.
        if (this.distanceToSqr(target)
                > REACTIVE_EYE_BASE_RANGE * REACTIVE_EYE_BASE_RANGE) {

            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    this.getX(),
                    this.getEyeY(),
                    this.getZ(),
                    8,
                    0.16D, 0.10D, 0.16D,
                    0.005D);
        }

        return true;
    }

    private void tickRadioactiveProjectiles(ServerLevel level) {
        if (radioactiveProjectiles.isEmpty()) return;

        for (int i = radioactiveProjectiles.size() - 1; i >= 0; --i) {
            RadioactiveProjectileMarker projectile =
                    radioactiveProjectiles.get(i);

            --projectile.life;

            LivingEntity target =
                    getLivingEntityById(level, projectile.targetId);

            if (target == null
                    || !target.isAlive()
                    || !isValidToxicThreat(target)) {

                radioactiveProjectiles.remove(i);
                continue;
            }

            Vec3 aim = target.getEyePosition();
            Vec3 delta = aim.subtract(projectile.position);

            if (delta.lengthSqr() <= PROJECTILE_HIT_DISTANCE_SQR) {
                hitWithRadioactiveProjectile(level, target);
                radioactiveProjectiles.remove(i);
                continue;
            }

            Vec3 step = delta.normalize().scale(PROJECTILE_SPEED);
            projectile.position = projectile.position.add(step);

            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    projectile.position.x,
                    projectile.position.y,
                    projectile.position.z,
                    3,
                    0.10D, 0.10D, 0.10D,
                    0.0D);

            if (projectile.life % 3 == 0) {
                level.sendParticles(
                        ParticleTypes.SMOKE,
                        projectile.position.x,
                        projectile.position.y,
                        projectile.position.z,
                        1,
                        0.08D, 0.08D, 0.08D,
                        0.0D);
            }

            if (projectile.life <= 0) {
                radioactiveProjectiles.remove(i);
            }
        }
    }

    private void hitWithRadioactiveProjectile(
            ServerLevel level,
            LivingEntity target) {

        float impactDamage = PROJECTILE_IMPACT_DAMAGE;
        float radiationDamage = PROJECTILE_RADIATION_DAMAGE;

        if (radioactiveSurgeTicks > 0) {
            impactDamage *= SURGE_PROJECTILE_DAMAGE_MULTIPLIER;
            radiationDamage *= SURGE_RADIATION_DAMAGE_MULTIPLIER;
        }

        DamageSource impactSource = this.damageSources().mobAttack(this);

        target.hurtServer(level, impactSource, impactDamage);
        target.hurtServer(
                level,
                this.damageSources().generic(),
                radiationDamage);

        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                20 * 5,
                0,
                true,
                true));

        target.addEffect(new MobEffectInstance(
                MobEffects.POISON,
                20 * 4,
                0,
                true,
                true));

        addExposure(target, PROJECTILE_EXPOSURE);

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                18,
                0.42D, 0.38D, 0.42D,
                0.025D);

        level.sendParticles(
                ParticleTypes.SMOKE,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.45D,
                target.getZ(),
                7,
                0.34D, 0.28D, 0.34D,
                0.01D);
    }

    // =====================================================================
    // Radiation Area
    // =====================================================================

    private boolean startRadiationArea(
            ServerLevel level,
            Vec3 proposedCenter) {

        if (radiationAreaCooldownTicks > 0
                || radiationAreaTicks > 0
                || radioactiveRainTicks > 0
                || proposedCenter == null
                || this.isBaby()) {
            return false;
        }

        Vec3 safeCenter = findFamilySafeZoneCenter(
                level,
                proposedCenter,
                5.0D);

        if (safeCenter == null) return false;

        radiationAreaCenter = safeCenter;
        radiationAreaTicks = RADIATION_AREA_DURATION_TICKS;
        radiationAreaCooldownTicks = RADIATION_AREA_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                safeCenter.x,
                safeCenter.y + 0.35D,
                safeCenter.z,
                34,
                2.0D, 0.45D, 2.0D,
                0.03D);

        spawnRadiationAreaRing(level, true);
        return true;
    }

    private void tickRadiationArea(ServerLevel level) {
        if (radiationAreaTicks <= 0 || radiationAreaCenter == null) return;

        --radiationAreaTicks;

        // If family pushes into the center of the denied zone, Toxic attempts
        // to shift the field deeper onto hostile territory instead of forcing
        // family to fight inside her visual hazard.
        if (radiationAreaTicks % 20 == 0
                && isFamilyTooCloseToZoneCenter(
                level,
                radiationAreaCenter,
                3.5D)) {

            LivingEntity replacementTarget =
                    findBestLocalClusterTarget(level, 14.0D);

            Vec3 replacement = replacementTarget == null
                    ? null
                    : findFamilySafeZoneCenter(
                    level,
                    replacementTarget.position(),
                    5.0D);

            if (replacement != null) {
                radiationAreaCenter = replacement;
            } else {
                radiationAreaTicks = 0;
                radiationAreaCenter = null;
                return;
            }
        }

        double radius = radioactiveSurgeTicks > 0
                ? RADIATION_AREA_SURGE_RADIUS
                : RADIATION_AREA_RADIUS;

        if (radiationAreaTicks % RADIATION_AREA_PULSE_INTERVAL == 0) {
            for (LivingEntity enemy : getThreatsAround(
                    level,
                    radiationAreaCenter,
                    radius)) {

                addExposure(
                        enemy,
                        RADIATION_AREA_EXPOSURE_PER_PULSE);
            }
        }

        if (radiationAreaTicks % 10 == 0) {
            spawnRadiationAreaRing(level, false);
        }

        if (radiationAreaTicks % 5 == 0) {
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    radiationAreaCenter.x,
                    radiationAreaCenter.y + 0.25D,
                    radiationAreaCenter.z,
                    7,
                    radius * 0.32D,
                    0.25D,
                    radius * 0.32D,
                    0.01D);
        }

        if (radiationAreaTicks <= 0) {
            radiationAreaCenter = null;
        }
    }

    private void spawnRadiationAreaRing(
            ServerLevel level,
            boolean bright) {

        if (radiationAreaCenter == null) return;

        double radius = radioactiveSurgeTicks > 0
                ? RADIATION_AREA_SURGE_RADIUS
                : RADIATION_AREA_RADIUS;

        int points = bright ? 34 : 22;

        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;

            double x = radiationAreaCenter.x + Math.cos(angle) * radius;
            double z = radiationAreaCenter.z + Math.sin(angle) * radius;

            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    x,
                    radiationAreaCenter.y + 0.12D,
                    z,
                    1,
                    0.02D, 0.04D, 0.02D,
                    0.0D);
        }
    }

    // =====================================================================
    // Radioactive Surge
    // =====================================================================

    private boolean startRadioactiveSurge(ServerLevel level) {
        if (radioactiveSurgeCooldownTicks > 0
                || radioactiveSurgeTicks > 0
                || this.isBaby()) {
            return false;
        }

        radioactiveSurgeTicks = RADIOACTIVE_SURGE_DURATION_TICKS;
        radioactiveSurgeCooldownTicks = RADIOACTIVE_SURGE_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                42,
                0.75D, 0.55D, 0.75D,
                0.035D);

        return true;
    }

    private void tickRadioactiveSurge(ServerLevel level) {
        if (radioactiveSurgeTicks <= 0) return;

        --radioactiveSurgeTicks;

        this.addEffect(new MobEffectInstance(
                MobEffects.SPEED,
                12,
                0,
                true,
                false));

        if (radioactiveSurgeTicks % 4 == 0) {
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    4,
                    0.34D, 0.30D, 0.34D,
                    0.005D);
        }
    }

    // =====================================================================
    // Radioactive Rain
    // =====================================================================

    private boolean startRadioactiveRain(ServerLevel level) {
        if (radioactiveRainCooldownTicks > 0
                || radioactiveRainTicks > 0
                || this.isBaby()) {
            return false;
        }

        List<LivingEntity> targets =
                findRadioactiveRainTargets(level);

        if (targets.size() < 3) return false;

        radiationAreaTicks = 0;
        radiationAreaCenter = null;

        radioactiveRainClouds.clear();

        for (LivingEntity target : targets) {
            Vec3 center = findFamilySafeCloudCenter(
                    level,
                    target.position(),
                    4.5D);

            if (center == null) continue;

            radioactiveRainClouds.add(
                    new RadioactiveCloudMarker(
                            center,
                            target.getId()));
        }

        if (radioactiveRainClouds.size() < 2) {
            radioactiveRainClouds.clear();
            return false;
        }

        radioactiveRainTicks = RADIOACTIVE_RAIN_DURATION_TICKS;
        radioactiveRainCooldownTicks = RADIOACTIVE_RAIN_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(),
                this.getY() + 0.60D,
                this.getZ(),
                56,
                1.4D, 0.7D, 1.4D,
                0.04D);

        return true;
    }

    private void tickRadioactiveRain(ServerLevel level) {
        if (radioactiveRainTicks <= 0) return;

        --radioactiveRainTicks;

        if (radioactiveRainClouds.isEmpty()) {
            radioactiveRainTicks = 0;
            return;
        }

        // Re-target missing/dead cloud assignments every second.
        if (radioactiveRainTicks % 20 == 0) {
            retargetRadioactiveRainClouds(level);
        }

        double cloudRadius = radioactiveSurgeTicks > 0
                ? RADIOACTIVE_RAIN_SURGE_RADIUS
                : RADIOACTIVE_RAIN_CLOUD_RADIUS;

        Set<Integer> exposureAppliedThisPulse = new HashSet<>();

        for (RadioactiveCloudMarker cloud : radioactiveRainClouds) {
            trackRadioactiveRainCloud(level, cloud);
            spawnRadioactiveRainCloudParticles(level, cloud, cloudRadius);

            if (radioactiveRainTicks % RADIOACTIVE_RAIN_PULSE_INTERVAL != 0) {
                continue;
            }

            for (LivingEntity enemy : getThreatsAround(
                    level,
                    cloud.center,
                    cloudRadius)) {

                if (!exposureAppliedThisPulse.add(enemy.getId())) {
                    continue;
                }

                addExposure(
                        enemy,
                        RADIOACTIVE_RAIN_EXPOSURE_PER_PULSE);

                float rainDamage = RADIOACTIVE_RAIN_RADIATION_DAMAGE;
                if (radioactiveSurgeTicks > 0) {
                    rainDamage *= SURGE_RADIATION_DAMAGE_MULTIPLIER;
                }

                if (isBossToxicTarget(enemy)) {
                    rainDamage *= 0.60F;
                }

                enemy.hurtServer(
                        level,
                        this.damageSources().generic(),
                        rainDamage);

                enemy.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        30,
                        0,
                        true,
                        false));

                enemy.addEffect(new MobEffectInstance(
                        MobEffects.POISON,
                        30,
                        0,
                        true,
                        false));
            }
        }

        if (radioactiveRainTicks <= 0) {
            radioactiveRainClouds.clear();
        }
    }

    private void trackRadioactiveRainCloud(
            ServerLevel level,
            RadioactiveCloudMarker cloud) {

        LivingEntity target = getLivingEntityById(level, cloud.targetId);
        if (target == null || !target.isAlive() || !isValidToxicThreat(target)) {
            return;
        }

        Vec3 desired = findFamilySafeCloudCenter(
                level,
                target.position(),
                4.5D);

        if (desired == null) return;

        Vec3 delta = desired.subtract(cloud.center);
        double distance = delta.length();

        if (distance <= RADIOACTIVE_RAIN_TRACK_SPEED) {
            cloud.center = desired;
            return;
        }

        cloud.center = cloud.center.add(
                delta.normalize().scale(RADIOACTIVE_RAIN_TRACK_SPEED));
    }

    private void retargetRadioactiveRainClouds(ServerLevel level) {
        List<LivingEntity> candidates = findRadioactiveRainTargets(level);
        if (candidates.isEmpty()) return;

        Set<Integer> alreadyAssigned = new HashSet<>();
        for (RadioactiveCloudMarker cloud : radioactiveRainClouds) {
            LivingEntity current = getLivingEntityById(level, cloud.targetId);
            if (current != null
                    && current.isAlive()
                    && isValidToxicThreat(current)) {

                alreadyAssigned.add(current.getId());
            } else {
                cloud.targetId = -1;
            }
        }

        for (RadioactiveCloudMarker cloud : radioactiveRainClouds) {
            if (cloud.targetId >= 0) continue;

            LivingEntity replacement = candidates.stream()
                    .filter(candidate -> !alreadyAssigned.contains(candidate.getId()))
                    .findFirst()
                    .orElse(null);

            if (replacement != null) {
                cloud.targetId = replacement.getId();
                alreadyAssigned.add(replacement.getId());
            }
        }
    }

    private List<LivingEntity> findRadioactiveRainTargets(ServerLevel level) {
        List<LivingEntity> threats = getNearbyToxicThreats(
                level,
                RADIOACTIVE_RAIN_TARGET_SCAN)
                .stream()
                .filter(this::canUseReactiveEyeAgainst)
                .toList();

        return threats.stream()
                .sorted(
                        Comparator
                                .<LivingEntity>comparingInt(
                                        target -> -countNeighbors(
                                                threats,
                                                target,
                                                7.0D))
                                .thenComparingDouble(this::toxicRangedPriorityScore))
                .limit(RADIOACTIVE_RAIN_MAX_CLOUDS)
                .toList();
    }

    private void spawnRadioactiveRainCloudParticles(
            ServerLevel level,
            RadioactiveCloudMarker cloud,
            double radius) {

        if (this.tickCount % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    cloud.center.x,
                    cloud.center.y + 2.8D,
                    cloud.center.z,
                    5,
                    radius * 0.35D,
                    0.20D,
                    radius * 0.35D,
                    0.01D);
        }

        if (this.tickCount % 4 == 0) {
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    cloud.center.x,
                    cloud.center.y + 1.8D,
                    cloud.center.z,
                    6,
                    radius * 0.45D,
                    1.1D,
                    radius * 0.45D,
                    0.01D);
        }
    }

    // =====================================================================
    // Brain decision policy
    // =====================================================================

    private void tickToxicDecisionBrain(ServerLevel level) {
        if (this.isOrderedToSit() || this.isInSittingPose()) return;

        int observedHostiles = this.getBrain()
                .getMemory(ModMemoryModuleTypes.TOXIC_HOSTILE_COUNT.get())
                .orElse(0);

        LivingEntity cluster = this.getBrain()
                .getMemory(ModMemoryModuleTypes.TOXIC_CLUSTER_TARGET.get())
                .orElse(null);

        LivingEntity ranged = this.getBrain()
                .getMemory(ModMemoryModuleTypes.TOXIC_RANGED_TARGET.get())
                .orElse(null);

        // 1. Large force -> ultimate battlefield contamination.
        if (observedHostiles >= 5
                && radioactiveRainCooldownTicks <= 0
                && radioactiveRainTicks <= 0
                && countThreatsAround(
                level,
                this.position(),
                16.0D) >= 5) {

            if (startRadioactiveRain(level)) {
                return;
            }
        }

        // 2. Sustained pressure -> Surge first so subsequent abilities are stronger.
        if (radioactiveSurgeCooldownTicks <= 0
                && radioactiveSurgeTicks <= 0
                && radioactiveRainTicks <= 0
                && (observedHostiles >= 3
                || countMeaningfullyExposedTargets() >= 2
                || (observedHostiles >= 2
                && this.getHealth() <= this.getMaxHealth() * 0.65F))) {

            if (startRadioactiveSurge(level)) {
                return;
            }
        }

        // 3. Cluster / chokepoint -> Radiation Area.
        if (observedHostiles >= 3
                && cluster != null
                && radiationAreaCooldownTicks <= 0
                && radiationAreaTicks <= 0
                && radioactiveRainTicks <= 0) {

            int enemiesNearCluster = countThreatsAround(
                    level,
                    cluster.position(),
                    RADIATION_AREA_RADIUS);

            if (enemiesNearCluster >= 3
                    && startRadiationArea(level, cluster.position())) {
                return;
            }
        }

        // 4. Reactive Eye ranged suppression.
        if (ranged != null
                && radioactiveProjectileCooldownTicks <= 0
                && tryRadioactiveProjectile(level, ranged)) {
            return;
        }

        // 5. Current engaged target fallback, without expanding physical pursuit.
        LivingEntity current = this.getTarget();
        if (current != null
                && radioactiveProjectileCooldownTicks <= 0
                && canUseReactiveEyeAgainst(current)) {

            tryRadioactiveProjectile(level, current);
        }
    }

    // =====================================================================
    // Reactive Eye scoring
    // =====================================================================

    public double toxicRangedPriorityScore(LivingEntity target) {
        double distance = Math.sqrt(this.distanceToSqr(target));
        float exposure = getExposure(target);

        // A little existing Exposure makes a target attractive because Toxic can
        // capitalize on it, but highly exposed enemies receive a saturation
        // penalty so she spreads contamination rather than wasting stacks.
        double exposureWeight = exposure < 70.0F
                ? -exposure * 0.08D
                : 14.0D + (exposure - 70.0F) * 0.20D;

        double familyThreatBonus = isThreateningToxicFamily(target)
                ? -18.0D
                : 0.0D;

        double eliteBonus = isEliteToxicTarget(target)
                ? -3.0D
                : 0.0D;

        return distance + exposureWeight + familyThreatBonus + eliteBonus;
    }

    private int countMeaningfullyExposedTargets() {
        int count = 0;

        for (ExposureRecord record : exposureLedger.values()) {
            if (record.exposure >= EXPOSURE_TIER_1) {
                ++count;
            }
        }

        return count;
    }

    // =====================================================================
    // Zone family-safety helpers
    // =====================================================================

    private Vec3 findFamilySafeZoneCenter(
            ServerLevel level,
            Vec3 proposed,
            double minimumFamilyDistance) {

        if (!isFamilyTooCloseToZoneCenter(
                level,
                proposed,
                minimumFamilyDistance)) {
            return proposed;
        }

        List<LivingEntity> nearbyThreats = getThreatsAround(
                level,
                proposed,
                10.0D);

        return nearbyThreats.stream()
                .map(LivingEntity::position)
                .filter(candidate -> !isFamilyTooCloseToZoneCenter(
                        level,
                        candidate,
                        minimumFamilyDistance))
                .max(Comparator.comparingDouble(
                        candidate -> minimumFamilyDistanceSqr(level, candidate)))
                .orElse(null);
    }

    private Vec3 findFamilySafeCloudCenter(
            ServerLevel level,
            Vec3 proposed,
            double minimumFamilyDistance) {

        if (!isFamilyTooCloseToZoneCenter(
                level,
                proposed,
                minimumFamilyDistance)) {
            return proposed;
        }

        LivingEntity closestFamily = getToxicFamilyAroundPosition(
                level,
                proposed,
                10.0D)
                .stream()
                .filter(member -> member != this)
                .min(Comparator.comparingDouble(
                        member -> member.position().distanceToSqr(proposed)))
                .orElse(null);

        if (closestFamily == null) return proposed;

        Vec3 away = proposed.subtract(closestFamily.position());
        if (away.lengthSqr() < 1.0E-5D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 shifted = proposed.add(
                away.normalize().scale(minimumFamilyDistance));

        if (!isFamilyTooCloseToZoneCenter(
                level,
                shifted,
                minimumFamilyDistance * 0.85D)) {
            return shifted;
        }

        return null;
    }

    private boolean isFamilyTooCloseToZoneCenter(
            ServerLevel level,
            Vec3 center,
            double minimumDistance) {

        double minimumSqr = minimumDistance * minimumDistance;

        for (LivingEntity family : getToxicFamilyAroundPosition(
                level,
                center,
                Math.max(12.0D, minimumDistance + 2.0D))) {

            if (family == this) continue;

            if (family.position().distanceToSqr(center) < minimumSqr) {
                return true;
            }
        }

        return false;
    }

    private double minimumFamilyDistanceSqr(
            ServerLevel level,
            Vec3 point) {

        double best = Double.MAX_VALUE;

        for (LivingEntity family : getToxicFamilyAroundPosition(
                level,
                point,
                16.0D)) {
            if (family == this) continue;
            best = Math.min(best, family.position().distanceToSqr(point));
        }

        return best;
    }

    // =====================================================================
    // Threat / cluster helpers
    // =====================================================================

    private LivingEntity findBestLocalClusterTarget(
            ServerLevel level,
            double radius) {

        List<LivingEntity> threats = getNearbyToxicThreats(level, radius);

        return threats.stream()
                .max(Comparator.comparingInt(
                        candidate -> countNeighbors(
                                threats,
                                candidate,
                                7.0D)))
                .orElse(null);
    }

    private int countThreatsAround(
            ServerLevel level,
            Vec3 center,
            double radius) {

        return getThreatsAround(level, center, radius).size();
    }

    private List<LivingEntity> getThreatsAround(
            ServerLevel level,
            Vec3 center,
            double radius) {

        double radiusSqr = radius * radius;

        AABB area = new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius);

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        this::isValidToxicThreat)
                .stream()
                .filter(enemy -> enemy.position().distanceToSqr(center) <= radiusSqr)
                .toList();
    }

    private int countNeighbors(
            List<LivingEntity> threats,
            LivingEntity center,
            double radius) {

        double radiusSqr = radius * radius;
        int count = 0;

        for (LivingEntity other : threats) {
            if (other.position().distanceToSqr(center.position()) <= radiusSqr) {
                ++count;
            }
        }

        return count;
    }

    private boolean isThreateningToxicFamily(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;

        LivingEntity target = mob.getTarget();
        return target != null && isToxicFamilyMember(target);
    }

    private boolean isEliteToxicTarget(LivingEntity target) {
        return !isBossToxicTarget(target)
                && (target.getMaxHealth() >= 40.0F
                || target.getArmorValue() >= 10);
    }

    private boolean isBossToxicTarget(LivingEntity target) {
        return target instanceof WitherBoss
                || target instanceof EnderDragon;
    }

    // =====================================================================
    // Family / threat helpers
    // =====================================================================

    public boolean isToxicFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            LivingEntity owner = this.getOwner();

            if (entity == owner) return true;

            if (entity instanceof Wolf wolf
                    && wolf.isTame()
                    && owner != null) {

                return wolf.isOwnedBy(owner);
            }

            return false;
        }

        return entity instanceof ToxicWolf toxic
                && !toxic.isTame();
    }

    private boolean canCarryToxicExposure(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || this.isToxicFamilyMember(target)
                || this.isAlliedTo(target)) {
            return false;
        }

        return target instanceof Enemy
                || isBossToxicTarget(target);
    }

    public boolean isValidToxicThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || this.isToxicFamilyMember(target)
                || this.isAlliedTo(target)
                || (!(target instanceof Enemy)
                && !isBossToxicTarget(target))) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public List<LivingEntity> getNearbyToxicFamily(
            ServerLevel level,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isToxicFamilyMember)
                .stream()
                .filter(member -> this.distanceToSqr(member) <= radiusSqr)
                .toList();
    }


    private List<LivingEntity> getToxicFamilyAroundPosition(
            ServerLevel level,
            Vec3 center,
            double radius) {

        double radiusSqr = radius * radius;

        AABB area = new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius);

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        this::isToxicFamilyMember)
                .stream()
                .filter(member -> member.position().distanceToSqr(center) <= radiusSqr)
                .toList();
    }

    public List<LivingEntity> getNearbyToxicThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isValidToxicThreat)
                .stream()
                .filter(enemy -> this.distanceToSqr(enemy) <= radiusSqr)
                .toList();
    }

    private LivingEntity getLivingEntityById(ServerLevel level, int id) {
        if (id < 0) return null;

        if (level.getEntity(id) instanceof LivingEntity living) {
            return living;
        }

        return null;
    }

    public boolean isToxicCombatVisualActive() {
        return radiationAreaTicks > 0
                || radioactiveSurgeTicks > 0
                || radioactiveRainTicks > 0
                || !radioactiveProjectiles.isEmpty();
    }

    // =====================================================================
    // Natural spawning
    // =====================================================================

    public static boolean checkToxicWolfSpawnRules(
            EntityType<ToxicWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        // Never natural-spawn in fluids.
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        // Feet/head space must be physically clear.
        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(pos.above())
                .getCollisionShape(level, pos.above())
                .isEmpty()) {
            return false;
        }

        // Solid footing.
        BlockPos floor = pos.below();
        if (!level.getBlockState(floor)
                .isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        // Rare/solitary five-star specialist. Tamed Toxic Wolves do NOT
        // suppress future wild spawns.
        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildToxic =
                    !serverLevel.getEntitiesOfClass(
                                    ToxicWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf -> wolf.isAlive() && !wolf.isTame())
                            .isEmpty();

            if (anotherWildToxic) {
                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt(
                "ToxicProjectileCooldown",
                radioactiveProjectileCooldownTicks);

        output.putInt(
                "ToxicRadiationAreaCooldown",
                radiationAreaCooldownTicks);

        output.putInt(
                "ToxicSurgeCooldown",
                radioactiveSurgeCooldownTicks);

        output.putInt(
                "ToxicRainCooldown",
                radioactiveRainCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        radioactiveProjectileCooldownTicks = Math.max(
                0,
                input.getIntOr("ToxicProjectileCooldown", 0));

        radiationAreaCooldownTicks = Math.max(
                0,
                input.getIntOr("ToxicRadiationAreaCooldown", 0));

        radioactiveSurgeCooldownTicks = Math.max(
                0,
                input.getIntOr("ToxicSurgeCooldown", 0));

        radioactiveRainCooldownTicks = Math.max(
                0,
                input.getIntOr("ToxicRainCooldown", 0));

        // Active combat states deliberately reset on reload.
        radiationAreaTicks = 0;
        radiationAreaCenter = null;

        radioactiveSurgeTicks = 0;

        radioactiveRainTicks = 0;
        radioactiveRainClouds.clear();

        radioactiveProjectiles.clear();
        exposureLedger.clear();
    }

    private void cancelAdultToxicStates() {
        radiationAreaTicks = 0;
        radiationAreaCenter = null;

        radioactiveSurgeTicks = 0;

        radioactiveRainTicks = 0;
        radioactiveRainClouds.clear();

        radioactiveProjectiles.clear();
        exposureLedger.clear();
    }

    // =====================================================================
    // Runtime records
    // =====================================================================

    private static final class ExposureRecord {
        private final int entityId;
        private float exposure;
        private int lastTouchedTick;

        private ExposureRecord(int entityId) {
            this.entityId = entityId;
        }
    }

    private static final class RadioactiveProjectileMarker {
        private Vec3 position;
        private final int targetId;
        private int life;

        private RadioactiveProjectileMarker(
                Vec3 position,
                int targetId,
                int life) {

            this.position = position;
            this.targetId = targetId;
            this.life = life;
        }
    }

    private static final class RadioactiveCloudMarker {
        private Vec3 center;
        private int targetId;

        private RadioactiveCloudMarker(Vec3 center, int targetId) {
            this.center = center;
            this.targetId = targetId;
        }
    }
}
