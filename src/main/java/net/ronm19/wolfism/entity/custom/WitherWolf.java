package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wither Wolf (#52) — Wither / Undead / Ranged / AoE / dedicated Wither hunter.
 *
 * <p>Against ordinary hostile groups he is controlled artillery: he selects a
 * few meaningful targets, fires vanilla Wither skulls, applies close-range
 * withering pressure, and occasionally seeds family-safe Wither Roses on the
 * hostile side of the fight.</p>
 *
 * <p>Against the vanilla Wither Boss his priorities change completely. The boss
 * becomes a persistent quarry. Wither Wolf tracks it over a much larger combat
 * envelope, recognizes the spawn/invulnerability and armored melee phases,
 * refuses to waste projectiles into the armored phase, closes for melee when
 * necessary, and uses Wither Shield to keep the family functional under skull
 * pressure.</p>
 */
public final class WitherWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Synced presentation state
    // ---------------------------------------------------------------------

    private static final EntityDataAccessor<Boolean> DATA_WITHER_COMBAT_ACTIVE =
            SynchedEntityData.defineId(WitherWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WITHER_HEART_ACTIVE =
            SynchedEntityData.defineId(WitherWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WITHER_RAGE_ACTIVE =
            SynchedEntityData.defineId(WitherWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WITHER_SHIELD_ACTIVE =
            SynchedEntityData.defineId(WitherWolf.class, EntityDataSerializers.BOOLEAN);

    // ---------------------------------------------------------------------
    // Five-star baseline
    // ---------------------------------------------------------------------

    private static final double BASE_MAX_HEALTH = 48.0D;
    private static final double BASE_ATTACK_DAMAGE = 8.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.335D;
    private static final double BASE_ARMOR = 6.0D;
    private static final double BASE_FOLLOW_RANGE = 64.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 0.16D;

    // ---------------------------------------------------------------------
    // Wither Awareness / Relentless Hunt
    // ---------------------------------------------------------------------

    public static final double WITHER_AWARENESS_RADIUS = 72.0D;
    public static final double WITHER_HUNT_RELEASE_RADIUS = 96.0D;
    private static final double RANGED_HUNT_MAX_RANGE = 34.0D;
    private static final double RANGED_HUNT_COMFORT_MIN = 9.0D;
    private static final double RANGED_HUNT_COMFORT_MAX = 18.0D;
    private static final double MELEE_HUNT_DISTANCE = 4.25D;

    // ---------------------------------------------------------------------
    // Withering Projectile
    // ---------------------------------------------------------------------

    public static final double ARTILLERY_SCAN_RADIUS = 28.0D;
    private static final int PROJECTILE_COOLDOWN_TICKS = 44;
    private static final int HEART_PROJECTILE_COOLDOWN_TICKS = 24;
    private static final int RAGE_PROJECTILE_COOLDOWN_TICKS = 12;
    private static final int MAX_NORMAL_TARGET_STREAMS = 3;
    private static final int MAX_RAGE_TARGET_STREAMS = 2;

    // Accurate Shooter / Wither Marksman. Vanilla skulls stay honest projectiles,
    // but Wither Wolf leads moving targets instead of aiming at where they were.
    // The prediction cap prevents absurd shots against teleportation or sudden
    // direction changes while still making fast/flying targets hard to shake.
    private static final double ACCURATE_SHOOTER_ESTIMATED_SKULL_SPEED = 0.92D;
    private static final double ACCURATE_SHOOTER_MAX_TRACKED_TARGET_SPEED = 1.65D;
    private static final double ACCURATE_SHOOTER_MAX_LEAD_TICKS = 14.0D;
    private static final double HEART_MAX_LEAD_TICKS = 16.0D;
    private static final double RAGE_MAX_LEAD_TICKS = 18.0D;

    // Modest anti-Wither pressure: offsets part of the boss's natural regen
    // without turning the hunter identity into a giant damage multiplier.
    private static final float ANTI_WITHER_PRESSURE_DAMAGE = 1.25F;

    // ---------------------------------------------------------------------
    // Wither Rose placement
    // ---------------------------------------------------------------------

    private static final float WITHER_ROSE_CHANCE = 0.40F;
    private static final int WITHER_ROSE_INTERNAL_COOLDOWN_TICKS = 30;
    private static final double WITHER_ROSE_FAMILY_SAFE_RADIUS = 5.0D;

    // ---------------------------------------------------------------------
    // Withering Area / Wither Shield
    // ---------------------------------------------------------------------

    private static final double WITHERING_AREA_RADIUS = 4.5D;
    private static final int WITHER_SHIELD_DURATION_TICKS = 20 * 8;
    private static final int WITHER_SHIELD_COOLDOWN_TICKS = 20 * 24;
    public static final double WITHER_SHIELD_RADIUS = 9.0D;

    /** Brain memory phase values written by WitherWolfAwarenessSensor. */
    public static final int WITHER_PHASE_NONE = 0;
    public static final int WITHER_PHASE_SPAWNING = 1;
    public static final int WITHER_PHASE_RANGED = 2;
    public static final int WITHER_PHASE_ARMORED = 3;

    // ---------------------------------------------------------------------
    // Wither Heart / Wither Rage
    // ---------------------------------------------------------------------

    public static final int WITHER_HEART_DURATION_TICKS = 20 * 10;
    public static final int WITHER_HEART_COOLDOWN_TICKS = 20 * 34;

    public static final int WITHER_RAGE_DURATION_TICKS = 20 * 12;
    public static final int WITHER_RAGE_COOLDOWN_TICKS = 20 * 70;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private WitherBoss witherQuarry;

    private int projectileCooldownTicks;
    private int roseInternalCooldownTicks;
    private int witherShieldTicks;
    private int witherShieldCooldownTicks;
    private int witherHeartTicks;
    private int witherHeartCooldownTicks;
    private int witherRageTicks;
    private int witherRageCooldownTicks;

    // ---------------------------------------------------------------------
    // Brain AI
    // ---------------------------------------------------------------------

    private static final class BrainHolder {
        private static final Brain.Provider<WitherWolf> PROVIDER =
                Brain.<WitherWolf>provider(
                        List.of(ModSensorTypes.WITHER_AWARENESS.get()),
                        wolf -> List.of());
    }

    public WitherWolf(EntityType<? extends WitherWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected Brain<WitherWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<WitherWolf> getBrain() {
        return (Brain<WitherWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WITHER_COMBAT_ACTIVE, false);
        builder.define(DATA_WITHER_HEART_ACTIVE, false);
        builder.define(DATA_WITHER_RAGE_ACTIVE, false);
        builder.define(DATA_WITHER_SHIELD_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.WITHER_WOLF.get();
    }

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

    // ---------------------------------------------------------------------
    // Wither immunity / hunter survivability
    // ---------------------------------------------------------------------

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (effectInstance.getEffect().equals(MobEffects.WITHER)) {
            return false;
        }
        return super.canBeAffected(effectInstance);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float adjusted = damage;

        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();

        if (attacker instanceof WitherBoss || direct instanceof WitherSkull) {
            // Hunter specialization is survival knowledge, not total boss immunity.
            adjusted *= 0.72F;
        }

        return super.hurtServer(level, source, adjusted);
    }

    // ---------------------------------------------------------------------
    // Main server tick
    // ---------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) return;

        tickCooldowns();

        // Complete passive immunity to the ordinary Wither status / Wither Rose
        // effect. Repeated rose application is erased immediately.
        if (this.hasEffect(MobEffects.WITHER)) {
            this.removeEffect(MobEffects.WITHER);
        }

        if (this.isBaby()) {
            cancelActiveWitherStates();
            this.setTarget(null);
            syncWitherVisualState(false);
            return;
        }

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            cancelActiveWitherStates();
            this.getNavigation().stop();
            syncWitherVisualState(false);
            return;
        }

        tickWitherShield(level);
        tickWitherHeart(level);
        tickWitherRage(level);

        tickWitherDecisionBrain(level);

        if (this.isWolfismWorkTick(20)) {
            pulseWitheringArea(level);
        }

        syncWitherVisualState(witherQuarry != null || this.getTarget() != null);
    }

    private void tickCooldowns() {
        if (projectileCooldownTicks > 0) --projectileCooldownTicks;
        if (roseInternalCooldownTicks > 0) --roseInternalCooldownTicks;
        if (witherShieldCooldownTicks > 0) --witherShieldCooldownTicks;
        if (witherHeartCooldownTicks > 0) --witherHeartCooldownTicks;
        if (witherRageCooldownTicks > 0) --witherRageCooldownTicks;
    }

    // ---------------------------------------------------------------------
    // Dedicated Wither Killer logic
    // ---------------------------------------------------------------------

    private void tickWitherDecisionBrain(ServerLevel level) {
        Brain<WitherWolf> brain = this.getBrain();

        LivingEntity rememberedQuarry = brain
                .getMemory(ModMemoryModuleTypes.WITHER_QUARRY.get())
                .orElse(null);

        WitherBoss quarry = rememberedQuarry instanceof WitherBoss boss
                && isValidWitherQuarry(boss)
                ? boss
                : null;

        this.witherQuarry = quarry;

        int phase = brain
                .getMemory(ModMemoryModuleTypes.WITHER_BOSS_PHASE.get())
                .orElse(WITHER_PHASE_NONE);

        LivingEntity priorityThreat = brain
                .getMemory(ModMemoryModuleTypes.WITHER_PRIORITY_THREAT.get())
                .orElse(null);

        int hostileCount = brain
                .getMemory(ModMemoryModuleTypes.WITHER_HOSTILE_COUNT.get())
                .orElse(0);

        LivingEntity vulnerableFamily = brain
                .getMemory(ModMemoryModuleTypes.WITHER_VULNERABLE_FAMILY.get())
                .orElse(null);

        int incomingSkulls = brain
                .getMemory(ModMemoryModuleTypes.WITHER_INCOMING_SKULL_COUNT.get())
                .orElse(0);

        boolean familyUnderWitherPressure = vulnerableFamily != null || incomingSkulls > 0;

        if (quarry != null) {
            tickDedicatedWitherHunt(level, quarry, phase, familyUnderWitherPressure);
        } else {
            tickGeneralArtillery(level, priorityThreat, hostileCount, familyUnderWitherPressure);
        }
    }

    public boolean isValidWitherQuarry(WitherBoss boss) {
        return boss != null
                && boss.isAlive()
                && boss.level() == this.level()
                && !this.isAlliedTo(boss)
                && this.distanceToSqr(boss)
                <= WITHER_HUNT_RELEASE_RADIUS * WITHER_HUNT_RELEASE_RADIUS;
    }

    private void tickDedicatedWitherHunt(
            ServerLevel level,
            WitherBoss quarry,
            int rememberedPhase,
            boolean familyUnderWitherPressure) {
        if (!this.canUseActiveWolfismAbility()) return;

        boolean spawnPhase = rememberedPhase == WITHER_PHASE_SPAWNING
                || quarry.getInvulnerableTicks() > 0;
        boolean armoredPhase = rememberedPhase == WITHER_PHASE_ARMORED
                || (!spawnPhase && quarry.isPowered());
        double distance = this.distanceTo(quarry);

        this.getLookControl().setLookAt(quarry, 35.0F, 35.0F);

        if (spawnPhase) {
            // Do not stand beside the spawn explosion and do not waste skulls.
            maintainHuntRange(quarry, 13.0D, 18.0D, 1.08D);
            tryStartWitherShield(level, true);
            return;
        }

        if (armoredPhase) {
            // Vanilla Wither becomes projectile-immune below half health.
            // The hunter recognizes this and deliberately changes jobs.
            projectileCooldownTicks = Math.max(projectileCooldownTicks, 8);
            if (distance > MELEE_HUNT_DISTANCE) {
                this.getNavigation().moveTo(quarry, witherRageTicks > 0 ? 1.38D : 1.24D);
                this.setSprinting(true);
            } else {
                this.setTarget(quarry);
            }
        } else {
            if (distance > RANGED_HUNT_COMFORT_MAX) {
                this.getNavigation().moveTo(quarry, witherRageTicks > 0 ? 1.30D : 1.15D);
            } else if (distance < RANGED_HUNT_COMFORT_MIN) {
                maintainHuntRange(quarry, RANGED_HUNT_COMFORT_MIN, RANGED_HUNT_COMFORT_MAX, 1.18D);
            } else if (this.tickCount % 30 == 0) {
                circleHuntTarget(quarry);
            }

            if (distance <= RANGED_HUNT_MAX_RANGE
                    && this.getSensing().hasLineOfSight(quarry)) {
                tryFireWitheringVolley(level, List.of(quarry), true);

                if (this.isWolfismWorkTick(20)) {
                    quarry.hurtServer(
                            level,
                            this.damageSources().mobAttack(this),
                            ANTI_WITHER_PRESSURE_DAMAGE);
                    WolfVfx.sendParticles("wither_wolf", level,
                            ParticleTypes.SOUL,
                            quarry.getX(), quarry.getY(0.62D), quarry.getZ(),
                            5, 0.32D, 0.32D, 0.32D, 0.015D);
                }
            }
        }

        if (witherRageCooldownTicks <= 0
                && witherRageTicks <= 0
                && distance <= 34.0D
                && (quarry.getHealth() <= quarry.getMaxHealth() * 0.75F
                || this.getHealth() <= this.getMaxHealth() * 0.60F)) {
            startWitherRage(level);
        } else if (witherHeartCooldownTicks <= 0
                && witherHeartTicks <= 0
                && witherRageTicks <= 0
                && distance <= 30.0D) {
            startWitherHeart(level);
        }

        tryStartWitherShield(level, familyUnderWitherPressure);
    }

    private void maintainHuntRange(
            LivingEntity target,
            double minimum,
            double preferred,
            double speed) {

        Vec3 flatAway = this.position().subtract(target.position());
        flatAway = new Vec3(flatAway.x, 0.0D, flatAway.z);
        if (flatAway.lengthSqr() < 1.0E-5D) {
            flatAway = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            flatAway = flatAway.normalize();
        }

        double distance = this.distanceTo(target);
        if (distance < minimum) {
            Vec3 destination = this.position().add(flatAway.scale(6.0D));
            this.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        } else if (distance > preferred) {
            this.getNavigation().moveTo(target, speed);
        } else {
            this.getNavigation().stop();
        }
    }

    private void circleHuntTarget(LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-5D) return;

        away = away.normalize();
        double side = (this.getId() & 1) == 0 ? 1.0D : -1.0D;
        Vec3 tangent = new Vec3(-away.z, 0.0D, away.x).scale(side * 4.0D);
        Vec3 destination = target.position().add(away.scale(13.0D)).add(tangent);
        this.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.95D);
    }

    // ---------------------------------------------------------------------
    // General artillery behavior
    // ---------------------------------------------------------------------

    private void tickGeneralArtillery(
            ServerLevel level,
            LivingEntity rememberedPriority,
            int rememberedHostileCount,
            boolean familyUnderWitherPressure) {
        if (!this.canUseActiveWolfismAbility()) return;

        List<LivingEntity> threats = getArtilleryThreats(level);
        if (threats.isEmpty()) return;

        if (rememberedPriority != null
                && rememberedPriority.isAlive()
                && threats.remove(rememberedPriority)) {
            threats.add(0, rememberedPriority);
        }

        int hostileCount = Math.max(rememberedHostileCount, threats.size());

        if (witherRageCooldownTicks <= 0
                && witherRageTicks <= 0
                && hostileCount >= 5) {
            startWitherRage(level);
        } else if (witherHeartCooldownTicks <= 0
                && witherHeartTicks <= 0
                && witherRageTicks <= 0
                && hostileCount >= 3) {
            startWitherHeart(level);
        }

        int streamCap = witherRageTicks > 0
                ? MAX_RAGE_TARGET_STREAMS
                : MAX_NORMAL_TARGET_STREAMS;

        List<LivingEntity> targets = threats.subList(0, Math.min(streamCap, threats.size()));
        tryFireWitheringVolley(level, targets, false);

        tryStartWitherShield(level, familyUnderWitherPressure);
    }

    private List<LivingEntity> getArtilleryThreats(ServerLevel level) {
        List<LivingEntity> result = new ArrayList<>();

        for (LivingEntity entity : getNearbyWitherThreats(level, ARTILLERY_SCAN_RADIUS)) {

            if (entity instanceof WitherBoss) continue;
            result.add(entity);
        }

        result.sort(Comparator
                .comparingInt(this::threatPriority)
                .thenComparingDouble(this::distanceToSqr));
        return result;
    }

    private int threatPriority(LivingEntity entity) {
        if (entity == this.getTarget()) return 0;
        if (entity instanceof Mob mob && isWitherFamilyMember(mob.getTarget())) return 1;
        return 2;
    }

    public double witherThreatScore(LivingEntity entity) {
        return threatPriority(entity) * 100000.0D + this.distanceToSqr(entity);
    }

    public List<LivingEntity> getNearbyWitherThreats(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isValidWitherThreat);
    }

    public boolean isValidWitherThreat(LivingEntity entity) {
        if (entity == null
                || entity == this
                || !entity.isAlive()
                || isWitherFamilyMember(entity)
                || this.isAlliedTo(entity)) {
            return false;
        }

        if (entity instanceof WitherBoss) return true;
        if (entity == this.getTarget() || entity == this.getFamilyDefenseTarget()) return true;

        if (entity instanceof Mob mob && isWitherFamilyMember(mob.getTarget())) {
            return true;
        }

        // Once tamed, Wither Wolf behaves as an active anti-hostile artillery
        // companion. Wild wolves reserve their supernatural aggression for the
        // Wither Boss itself.
        return this.isTame() && entity instanceof Enemy;
    }

    // ---------------------------------------------------------------------
    // Withering Projectile
    // ---------------------------------------------------------------------

    private boolean tryFireWitheringVolley(
            ServerLevel level,
            List<? extends LivingEntity> requestedTargets,
            boolean dedicatedHunt) {

        if (projectileCooldownTicks > 0
                || requestedTargets.isEmpty()
                || !this.canUseActiveWolfismAbility()) {
            return false;
        }

        int fired = 0;
        LivingEntity roseAnchor = null;

        for (LivingEntity target : requestedTargets) {
            if (target == null
                    || !target.isAlive()
                    || this.distanceToSqr(target) > RANGED_HUNT_MAX_RANGE * RANGED_HUNT_MAX_RANGE
                    || !this.getSensing().hasLineOfSight(target)
                    || !hasFamilySafeFiringLane(level, target)) {
                continue;
            }

            fireVanillaWitherSkull(level, target);
            ++fired;
            if (roseAnchor == null) roseAnchor = target;
        }

        if (fired <= 0) return false;

        projectileCooldownTicks = witherRageTicks > 0
                ? RAGE_PROJECTILE_COOLDOWN_TICKS
                : (witherHeartTicks > 0
                ? HEART_PROJECTILE_COOLDOWN_TICKS
                : PROJECTILE_COOLDOWN_TICKS);

        if (roseAnchor != null) {
            tryPlaceWitherRose(level, roseAnchor);
        }

        this.playSound(
                SoundEvents.WITHER_SHOOT,
                dedicatedHunt ? 0.85F : 0.72F,
                1.16F + this.random.nextFloat() * 0.12F);
        return true;
    }

    private void fireVanillaWitherSkull(ServerLevel level, LivingEntity target) {
        Vec3 muzzle = this.getEyePosition().add(this.getLookAngle().scale(0.34D));
        Vec3 aimPoint = calculateAccurateShooterAimPoint(target, muzzle);

        Vec3 direction = aimPoint.subtract(muzzle);
        if (direction.lengthSqr() < 1.0E-5D) return;
        direction = direction.normalize();

        WitherSkull skull = new WitherSkull(level, this, direction);
        skull.setPos(muzzle.x, muzzle.y, muzzle.z);
        level.addFreshEntity(skull);

        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SMOKE,
                muzzle.x, muzzle.y, muzzle.z,
                witherRageTicks > 0 ? 9 : 5,
                0.12D, 0.10D, 0.12D,
                0.018D);
        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SOUL,
                muzzle.x, muzzle.y, muzzle.z,
                witherHeartTicks > 0 || witherRageTicks > 0 ? 6 : 2,
                0.10D, 0.10D, 0.10D,
                0.010D);
    }

    /**
     * Accurate Shooter: predicts an interception point from the target's current
     * velocity and the skull's estimated travel time. This is deliberately not
     * homing: once the skull is fired, a hard turn, teleport, wall, or sudden
     * acceleration can still make it miss.
     */
    private Vec3 calculateAccurateShooterAimPoint(LivingEntity target, Vec3 muzzle) {
        // Center-mass is more reliable than eye-only aiming against tiny mobs,
        // diving Phantoms, and targets whose eye height sits near a hitbox edge.
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.58D, 0.0D);
        Vec3 targetVelocity = target.getDeltaMovement();

        double targetSpeed = targetVelocity.length();
        if (targetSpeed > ACCURATE_SHOOTER_MAX_TRACKED_TARGET_SPEED) {
            targetVelocity = targetVelocity
                    .normalize()
                    .scale(ACCURATE_SHOOTER_MAX_TRACKED_TARGET_SPEED);
        }

        // No need to invent lead for effectively stationary targets.
        if (targetVelocity.lengthSqr() < 0.0004D) {
            return targetPoint;
        }

        double maxLeadTicks = witherRageTicks > 0
                ? RAGE_MAX_LEAD_TICKS
                : (witherHeartTicks > 0 ? HEART_MAX_LEAD_TICKS : ACCURATE_SHOOTER_MAX_LEAD_TICKS);

        // Two prediction passes make the lead react to the extra distance created
        // by a moving target without requiring a fragile projectile-physics hook.
        double leadTicks = Mth.clamp(
                muzzle.distanceTo(targetPoint) / ACCURATE_SHOOTER_ESTIMATED_SKULL_SPEED,
                0.0D,
                maxLeadTicks);

        double predictionStrength = 0.88D;
        if (!target.onGround()) predictionStrength += 0.08D; // flyers/divers need stronger lead
        if (target instanceof WitherBoss) predictionStrength += 0.06D;
        if (witherHeartTicks > 0) predictionStrength += 0.04D;
        if (witherRageTicks > 0) predictionStrength += 0.08D;
        predictionStrength = Mth.clamp(predictionStrength, 0.82D, 1.08D);

        Vec3 predicted = targetPoint.add(targetVelocity.scale(leadTicks * predictionStrength));
        double refinedLeadTicks = Mth.clamp(
                muzzle.distanceTo(predicted) / ACCURATE_SHOOTER_ESTIMATED_SKULL_SPEED,
                0.0D,
                maxLeadTicks);

        predicted = targetPoint.add(targetVelocity.scale(refinedLeadTicks * predictionStrength));

        // Fast vertical movement (especially Wither climbs and Phantom dives) is
        // intentionally respected, but bounded by the same lead-time cap above.
        return predicted;
    }

    private boolean hasFamilySafeFiringLane(ServerLevel level, LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 end = target.getEyePosition();
        AABB corridor = new AABB(start, end).inflate(2.0D);

        for (LivingEntity family : level.getEntitiesOfClass(
                LivingEntity.class,
                corridor,
                this::isWitherFamilyMember)) {
            if (family == this) continue;

            // A family member around the impact point is an automatic no-fire.
            if (family.distanceToSqr(target)
                    <= WITHER_ROSE_FAMILY_SAFE_RADIUS * WITHER_ROSE_FAMILY_SAFE_RADIUS) {
                return false;
            }

            if (distanceToSegmentSqr(family.getEyePosition(), start, end) <= 1.75D * 1.75D) {
                return false;
            }
        }
        return true;
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-8D) return point.distanceToSqr(start);

        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Mth.clamp(t, 0.0D, 1.0D);
        Vec3 closest = start.add(segment.scale(t));
        return point.distanceToSqr(closest);
    }

    // ---------------------------------------------------------------------
    // Wither Rose placement — family-safe hostile-side terrain denial
    // ---------------------------------------------------------------------

    private void tryPlaceWitherRose(ServerLevel level, LivingEntity hostile) {
        if (!EventHooks.canEntityGrief(level, this)
                || roseInternalCooldownTicks > 0
                || this.random.nextFloat() >= WITHER_ROSE_CHANCE) {
            return;
        }

        BlockPos center = hostile.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(center);

        for (int radius = 1; radius <= 3; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    candidates.add(center.offset(dx, 0, dz));
                    candidates.add(center.offset(dx, 1, dz));
                    candidates.add(center.offset(dx, -1, dz));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));

        for (BlockPos pos : candidates) {
            if (!isSafeRosePosition(level, pos)) continue;

            level.setBlock(pos, Blocks.WITHER_ROSE.defaultBlockState(), 3);
            roseInternalCooldownTicks = WITHER_ROSE_INTERNAL_COOLDOWN_TICKS;
            WolfVfx.sendParticles("wither_wolf", level,
                    ParticleTypes.SOUL,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.55D,
                    pos.getZ() + 0.5D,
                    9,
                    0.25D, 0.22D, 0.25D,
                    0.018D);
            return;
        }
    }

    private boolean isSafeRosePosition(ServerLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return false;
        if (!Blocks.WITHER_ROSE.defaultBlockState().canSurvive(level, pos)) return false;

        AABB safetyBox = new AABB(pos).inflate(WITHER_ROSE_FAMILY_SAFE_RADIUS);
        return level.getEntitiesOfClass(
                LivingEntity.class,
                safetyBox,
                this::isWitherFamilyMember).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Withering Area / close-range bite
    // ---------------------------------------------------------------------

    private void pulseWitheringArea(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()) return;

        boolean touched = false;
        for (LivingEntity enemy : level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(WITHERING_AREA_RADIUS),
                this::isValidWitherThreat)) {

            if (enemy instanceof WitherBoss) {
                if (this.distanceToSqr(enemy) <= 3.5D * 3.5D) {
                    enemy.hurtServer(level, this.damageSources().mobAttack(this), 1.0F);
                    touched = true;
                }
                continue;
            }

            enemy.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 4, 1), this);
            enemy.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0), this);
            touched = true;
        }

        if (touched) {
            WolfVfx.sendParticles("wither_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.40D, this.getZ(),
                    12,
                    WITHERING_AREA_RADIUS * 0.55D,
                    0.38D,
                    WITHERING_AREA_RADIUS * 0.55D,
                    0.018D);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (!hurt || !(target instanceof LivingEntity living)) return hurt;

        if (living instanceof WitherBoss) {
            living.hurtServer(level, this.damageSources().mobAttack(this), 1.75F);
        } else if (isValidWitherThreat(living)) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 6, 1), this);
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0), this);
        }

        tryPlaceWitherRose(level, living);

        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SOUL,
                living.getX(), living.getY(0.55D), living.getZ(),
                7, 0.24D, 0.24D, 0.24D, 0.015D);
        return true;
    }

    // ---------------------------------------------------------------------
    // Wither Shield
    // ---------------------------------------------------------------------

    private boolean tryStartWitherShield(ServerLevel level, boolean forceFromBrain) {
        if (witherShieldCooldownTicks > 0
                || witherShieldTicks > 0
                || !this.canUseActiveWolfismAbility()) {
            return false;
        }

        boolean familyWithered = false;
        for (LivingEntity family : getNearbyWitherFamily(level, WITHER_SHIELD_RADIUS)) {
            if (family.hasEffect(MobEffects.WITHER)) {
                familyWithered = true;
                break;
            }
        }

        boolean incomingSkull = hasIncomingWitherSkull(level);
        if (!forceFromBrain && !familyWithered && !incomingSkull) {
            return false;
        }

        witherShieldTicks = WITHER_SHIELD_DURATION_TICKS;
        witherShieldCooldownTicks = WITHER_SHIELD_COOLDOWN_TICKS;
        pulseWitherShield(level, true);
        return true;
    }

    private void tickWitherShield(ServerLevel level) {
        if (witherShieldTicks <= 0) return;
        --witherShieldTicks;

        if (this.tickCount % 5 == 0) {
            pulseWitherShield(level, false);
        }
    }

    private void pulseWitherShield(ServerLevel level, boolean activation) {
        for (LivingEntity family : getNearbyWitherFamily(level, WITHER_SHIELD_RADIUS)) {
            family.removeEffect(MobEffects.WITHER);
            family.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE,
                    14,
                    activation ? 1 : 0,
                    true,
                    false), this);
        }

        // Emergency interception: only skulls genuinely fired by the vanilla
        // Wither Boss, only when they enter the protected family bubble.
        for (WitherSkull skull : level.getEntitiesOfClass(
                WitherSkull.class,
                this.getBoundingBox().inflate(WITHER_SHIELD_RADIUS + 3.0D),
                projectile -> projectile.isAlive()
                        && projectile.getOwner() instanceof WitherBoss)) {

            boolean threatensFamily = false;
            for (LivingEntity family : getNearbyWitherFamily(level, WITHER_SHIELD_RADIUS)) {
                if (family.distanceToSqr(skull) <= 3.0D * 3.0D) {
                    threatensFamily = true;
                    break;
                }
            }

            if (threatensFamily) {
                WolfVfx.sendParticles("wither_wolf", level,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        skull.getX(), skull.getY(), skull.getZ(),
                        12, 0.22D, 0.22D, 0.22D, 0.025D);
                skull.discard();
            }
        }

        if (activation || this.isWolfismWorkTick(10)) {
            WolfVfx.sendParticles("wither_wolf", level,
                    ParticleTypes.SOUL,
                    this.getX(), this.getY() + 0.55D, this.getZ(),
                    activation ? 34 : 12,
                    WITHER_SHIELD_RADIUS * 0.42D,
                    0.70D,
                    WITHER_SHIELD_RADIUS * 0.42D,
                    0.020D);
        }
    }

    private boolean hasIncomingWitherSkull(ServerLevel level) {
        for (WitherSkull skull : level.getEntitiesOfClass(
                WitherSkull.class,
                this.getBoundingBox().inflate(WITHER_SHIELD_RADIUS + 4.0D),
                projectile -> projectile.isAlive()
                        && projectile.getOwner() instanceof WitherBoss)) {
            for (LivingEntity family : getNearbyWitherFamily(level, WITHER_SHIELD_RADIUS)) {
                if (family.distanceToSqr(skull) <= 7.0D * 7.0D) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Wither Heart
    // ---------------------------------------------------------------------

    private void startWitherHeart(ServerLevel level) {
        witherHeartTicks = WITHER_HEART_DURATION_TICKS;
        witherHeartCooldownTicks = WITHER_HEART_COOLDOWN_TICKS;

        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 0.60D, this.getZ(),
                28, 0.75D, 0.55D, 0.75D, 0.025D);
        this.playSound(this.getWolfismGrowlSound(), 0.72F, 1.18F);
    }

    private void tickWitherHeart(ServerLevel level) {
        if (witherHeartTicks <= 0) return;
        --witherHeartTicks;

        this.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 12, 0, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 12, 0, true, false), this);

        if (this.tickCount % 5 == 0) {
            sendOrbitingWitherSpores(level, 6, 1.35D);
        }
    }

    // ---------------------------------------------------------------------
    // Wither Rage — ultimate
    // ---------------------------------------------------------------------

    private void startWitherRage(ServerLevel level) {
        witherRageTicks = WITHER_RAGE_DURATION_TICKS;
        witherRageCooldownTicks = WITHER_RAGE_COOLDOWN_TICKS;
        witherHeartTicks = 0;
        projectileCooldownTicks = Math.min(projectileCooldownTicks, 4);

        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SMOKE,
                this.getX(), this.getY() + 0.58D, this.getZ(),
                54, 1.35D, 0.85D, 1.35D, 0.045D);
        WolfVfx.sendParticles("wither_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 0.62D, this.getZ(),
                32, 1.00D, 0.65D, 1.00D, 0.035D);
        this.playSound(this.getWolfismGrowlSound(), 1.0F, 0.72F);
    }

    private void tickWitherRage(ServerLevel level) {
        if (witherRageTicks <= 0) return;
        --witherRageTicks;

        this.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 12, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 12, 1, true, false), this);

        if (this.tickCount % 4 == 0) {
            sendOrbitingWitherSpores(level, 8, 1.60D);
        }
    }

    private void sendOrbitingWitherSpores(ServerLevel level, int count, double radius) {
        double rotation = this.tickCount * 0.24D;
        for (int i = 0; i < count; ++i) {
            double angle = rotation + (Math.PI * 2.0D * i) / count;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + 0.45D + 0.22D * Math.sin(angle * 2.0D);

            WolfVfx.sendParticles("wither_wolf", level,
                    (i & 1) == 0 ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
                    x, y, z,
                    1, 0.015D, 0.015D, 0.015D, 0.005D);
        }
    }

    // ---------------------------------------------------------------------
    // Family helpers
    // ---------------------------------------------------------------------

    public boolean isWitherFamilyMember(LivingEntity entity) {
        if (entity == null) return false;
        if (entity == this) return true;
        if (entity == this.getOwner()) return true;
        if (this.isTame() && entity instanceof Wolf wolf && wolf.isTame()) return true;
        return this.isTame() && this.isAlliedTo(entity);
    }

    public boolean isVulnerableWitherFamilyMember(LivingEntity entity) {
        if (!isWitherFamilyMember(entity) || !entity.isAlive()) return false;

        if (entity.hasEffect(MobEffects.WITHER)) return true;

        float healthRatio = entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
        return healthRatio <= 0.45F;
    }

    public List<LivingEntity> getNearbyWitherFamily(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isWitherFamilyMember);
    }

    // ---------------------------------------------------------------------
    // Client presentation helpers
    // ---------------------------------------------------------------------

    private void syncWitherVisualState(boolean combat) {
        this.entityData.set(DATA_WITHER_COMBAT_ACTIVE, combat || witherRageTicks > 0);
        this.entityData.set(DATA_WITHER_HEART_ACTIVE, witherHeartTicks > 0);
        this.entityData.set(DATA_WITHER_RAGE_ACTIVE, witherRageTicks > 0);
        this.entityData.set(DATA_WITHER_SHIELD_ACTIVE, witherShieldTicks > 0);
    }

    public boolean isWitherCombatVisualActive() {
        return this.entityData.get(DATA_WITHER_COMBAT_ACTIVE);
    }

    public boolean isWitherHeartActive() {
        return this.entityData.get(DATA_WITHER_HEART_ACTIVE);
    }

    public boolean isWitherRageActive() {
        return this.entityData.get(DATA_WITHER_RAGE_ACTIVE);
    }

    public boolean isWitherShieldActive() {
        return this.entityData.get(DATA_WITHER_SHIELD_ACTIVE);
    }

    private void cancelActiveWitherStates() {
        witherHeartTicks = 0;
        witherRageTicks = 0;
        witherShieldTicks = 0;
        this.setSprinting(false);
    }

    // ---------------------------------------------------------------------
    // Natural spawning — Soul Sand Valley
    // ---------------------------------------------------------------------

    public static boolean checkWitherWolfSpawnRules(
            EntityType<WitherWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.NETHER) return false;

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        // Respect WorldGenRegion's entity view when spawning during chunk generation.
        boolean anotherWildWitherWolf = !level.getEntitiesOfClass(
                        WitherWolf.class,
                        new AABB(pos).inflate(64.0D, 32.0D, 64.0D),
                        wolf -> wolf.isAlive() && !wolf.isTame())
                .isEmpty();

        return !anotherWildWitherWolf;
    }

    // ---------------------------------------------------------------------
    // Persistence — cooldowns only; quarry is intentionally reacquired
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("WitherProjectileCooldown", projectileCooldownTicks);
        output.putInt("WitherRoseCooldown", roseInternalCooldownTicks);
        output.putInt("WitherShieldCooldown", witherShieldCooldownTicks);
        output.putInt("WitherHeartCooldown", witherHeartCooldownTicks);
        output.putInt("WitherRageCooldown", witherRageCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        projectileCooldownTicks = Math.max(0, input.getIntOr("WitherProjectileCooldown", 0));
        roseInternalCooldownTicks = Math.max(0, input.getIntOr("WitherRoseCooldown", 0));
        witherShieldCooldownTicks = Math.max(0, input.getIntOr("WitherShieldCooldown", 0));
        witherHeartCooldownTicks = Math.max(0, input.getIntOr("WitherHeartCooldown", 0));
        witherRageCooldownTicks = Math.max(0, input.getIntOr("WitherRageCooldown", 0));

        witherShieldTicks = 0;
        witherHeartTicks = 0;
        witherRageTicks = 0;
        witherQuarry = null;
    }
}
