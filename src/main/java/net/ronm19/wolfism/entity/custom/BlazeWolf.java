package net.ronm19.wolfism.entity.custom;

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
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.BlazeWolfArtilleryPositionGoal;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Blaze Wolf (#53) — Fire / Ranged / Artillery / Offensive Pack Support.
 *
 * <p>Blaze is Wolfism's dedicated ranged-fire specialist. She deliberately
 * maintains firing distance, leads moving targets, spreads or focuses barrages
 * according to battlefield pressure, repositions when rushed, and escalates
 * through Anger of Fire -> Blazing Fury -> Inferno Barrage instead of behaving
 * like a melee wolf that happens to own a projectile.</p>
 */
public final class BlazeWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Synced presentation state
    // ---------------------------------------------------------------------

    private static final EntityDataAccessor<Boolean> DATA_BLAZE_COMBAT_ACTIVE =
            SynchedEntityData.defineId(BlazeWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ANGER_ACTIVE =
            SynchedEntityData.defineId(BlazeWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FURY_ACTIVE =
            SynchedEntityData.defineId(BlazeWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_INFERNO_ACTIVE =
            SynchedEntityData.defineId(BlazeWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FIRE_SHIELD_ACTIVE =
            SynchedEntityData.defineId(BlazeWolf.class, EntityDataSerializers.BOOLEAN);

    // ---------------------------------------------------------------------
    // Five-star artillery baseline
    // ---------------------------------------------------------------------

    private static final double BASE_MAX_HEALTH = 44.0D;
    private static final double BASE_ATTACK_DAMAGE = 7.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.34D;
    private static final double BASE_ARMOR = 4.0D;
    private static final double BASE_FOLLOW_RANGE = 56.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 0.10D;

    public static final double BLAZE_AWARENESS_RADIUS = 34.0D;
    public static final double BLAZE_ARTILLERY_MAX_RANGE = 30.0D;
    public static final double BLAZE_PREFERRED_MIN_RANGE = 10.0D;
    public static final double BLAZE_PREFERRED_MAX_RANGE = 18.0D;
    public static final double BLAZE_EMERGENCY_RANGE = 5.5D;
    public static final double BLAZE_PRESENCE_RADIUS = 10.0D;

    // ---------------------------------------------------------------------
    // Blaze Barrage
    // ---------------------------------------------------------------------

    private static final int NORMAL_BARRAGE_COOLDOWN = 46;
    private static final int ANGER_BARRAGE_COOLDOWN = 36;
    private static final int FURY_BARRAGE_COOLDOWN = 28;

    private static final int NORMAL_BARRAGE_SHOTS = 3;
    private static final int ANGER_BARRAGE_SHOTS = 4;
    private static final int FURY_BARRAGE_SHOTS = 5;

    private static final int NORMAL_SHOT_DELAY = 8;
    private static final int ANGER_SHOT_DELAY = 7;
    private static final int FURY_SHOT_DELAY = 6;
    private static final int INFERNO_SHOT_DELAY = 6;

    // Accurate but not magical. Sudden turns/teleports/cover can still beat it.
    private static final double BASE_MAX_LEAD_TICKS = 10.0D;
    private static final double ANGER_MAX_LEAD_TICKS = 12.0D;
    private static final double FURY_MAX_LEAD_TICKS = 14.0D;
    private static final double INFERNO_MAX_LEAD_TICKS = 16.0D;
    private static final double MAX_TRACKED_TARGET_SPEED = 1.55D;
    private static final double ESTIMATED_FIREBALL_SPEED = 0.86D;

    // ---------------------------------------------------------------------
    // Escalation states
    // ---------------------------------------------------------------------

    private static final int ANGER_DURATION = 20 * 10;
    private static final int ANGER_COOLDOWN = 20 * 18;

    private static final int FURY_DURATION = 20 * 10;
    private static final int FURY_COOLDOWN = 20 * 38;

    public static final int INFERNO_DURATION = 20 * 12;
    private static final int INFERNO_COOLDOWN = 20 * 70;

    // ---------------------------------------------------------------------
    // Fire Shield / Blaze Charge / Presence
    // ---------------------------------------------------------------------

    private static final int FIRE_SHIELD_DURATION = 20 * 6;
    private static final int FIRE_SHIELD_COOLDOWN = 20 * 20;

    private static final int BLAZE_CHARGE_DURATION = 7;
    private static final int BLAZE_CHARGE_COOLDOWN = 20 * 9;
    private static final double BLAZE_CHARGE_SPEED = 0.78D;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int barrageCooldownTicks;
    private int barrageShotsRemaining;
    private int barrageShotDelayTicks;
    private int barrageTargetCursor;

    private int combatHeatTicks;
    private int angerTicks;
    private int angerCooldownTicks;
    private int furyTicks;
    private int furyCooldownTicks;
    private int infernoTicks;
    private int infernoCooldownTicks;
    private int fireShieldTicks;
    private int fireShieldCooldownTicks;
    private int blazeChargeTicks;
    private int blazeChargeCooldownTicks;

    private Vec3 blazeChargeDirection = Vec3.ZERO;

    // ---------------------------------------------------------------------
    // Brain AI
    // ---------------------------------------------------------------------

    private static final class BrainHolder {
        private static final Brain.Provider<BlazeWolf> PROVIDER =
                Brain.<BlazeWolf>provider(
                        List.of(ModSensorTypes.BLAZE_AWARENESS.get()),
                        wolf -> List.of());
    }

    public BlazeWolf(EntityType<? extends BlazeWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.BLAZE_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new BlazeWolfArtilleryPositionGoal(this));
    }

    @Override
    protected Brain<BlazeWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<BlazeWolf> getBrain() {
        return (Brain<BlazeWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLAZE_COMBAT_ACTIVE, false);
        builder.define(DATA_ANGER_ACTIVE, false);
        builder.define(DATA_FURY_ACTIVE, false);
        builder.define(DATA_INFERNO_ACTIVE, false);
        builder.define(DATA_FIRE_SHIELD_ACTIVE, false);
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
    // Fire identity / Fire Shield
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // Strong innate fire/lava/fireball immunity.
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        }

        float adjusted = damage;
        if (fireShieldTicks > 0 && source.getDirectEntity() instanceof Projectile) {
            // Fire Shield remains useful against non-fire ranged pressure too,
            // while true fire projectiles are already nullified above.
            adjusted *= 0.60F;
        }

        return super.hurtServer(level, source, adjusted);
    }

    // ---------------------------------------------------------------------
    // Tick / decision loop
    // ---------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;

        tickCooldowns();
        tickActiveStates(level);

        if (this.isBaby()) {
            cancelBlazeCombat();
            this.setTarget(null);
            syncBlazeVisualState(false);
            return;
        }

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            cancelBlazeCombat();
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            syncBlazeVisualState(false);
            return;
        }

        if (!this.canUseActiveWolfismAbility()) {
            syncBlazeVisualState(false);
            return;
        }

        if (this.tickCount % 5 == 0) {
            tickBlazeDecisionBrain(level);
        }

        maintainLongRangeAccess();

        if (blazeChargeTicks > 0) {
            tickBlazeCharge(level);
        }

        if (infernoTicks > 0) {
            tickInfernoBarrage(level);
        } else {
            tickNormalBarrage(level);
        }

        if (this.tickCount % 10 == 0) {
            pulseBlazesPresence(level);
        }

        if (this.tickCount % 18 == 0) {
            level.sendParticles(
                    infernoTicks > 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.50D, this.getZ(),
                    infernoTicks > 0 ? 4 : 2,
                    0.30D, 0.28D, 0.30D,
                    0.012D);
        }

        syncBlazeVisualState(hasBlazeCombatTarget());
    }

    private void tickCooldowns() {
        if (barrageCooldownTicks > 0) --barrageCooldownTicks;
        if (barrageShotDelayTicks > 0) --barrageShotDelayTicks;
        if (angerCooldownTicks > 0) --angerCooldownTicks;
        if (furyCooldownTicks > 0) --furyCooldownTicks;
        if (infernoCooldownTicks > 0) --infernoCooldownTicks;
        if (fireShieldCooldownTicks > 0) --fireShieldCooldownTicks;
        if (blazeChargeCooldownTicks > 0) --blazeChargeCooldownTicks;
    }

    private void tickActiveStates(ServerLevel level) {
        if (angerTicks > 0) {
            --angerTicks;
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 0, true, false), this);
        }

        if (furyTicks > 0) {
            --furyTicks;
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 1, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 12, 0, true, false), this);
        }

        if (infernoTicks > 0) {
            --infernoTicks;
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 1, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 12, 0, true, false), this);
            if (this.tickCount % 4 == 0) {
                sendBlazeOrbit(level, 7, 1.30D);
            }
        }

        if (fireShieldTicks > 0) {
            --fireShieldTicks;
            if (this.tickCount % 5 == 0) {
                interceptHostileFireballs(level);
                level.sendParticles(
                        ParticleTypes.FLAME,
                        this.getX(), this.getY() + 0.55D, this.getZ(),
                        10, 0.70D, 0.55D, 0.70D, 0.015D);
            }
        }
    }

    private void tickBlazeDecisionBrain(ServerLevel level) {
        Brain<BlazeWolf> brain = this.getBrain();

        LivingEntity frontline = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_FRONTLINE_LOCKED_TARGET.get())
                .orElse(null);
        LivingEntity ranged = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_RANGED_TARGET.get())
                .orElse(null);
        LivingEntity priority = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_PRIORITY_THREAT.get())
                .orElse(null);
        LivingEntity close = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_CLOSE_THREAT.get())
                .orElse(null);

        int hostileCount = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_HOSTILE_COUNT.get())
                .orElse(0);
        int clusterSize = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_CLUSTER_SIZE.get())
                .orElse(0);
        int incomingFireballs = brain
                .getMemory(ModMemoryModuleTypes.BLAZE_INCOMING_FIREBALL_COUNT.get())
                .orElse(0);

        LivingEntity chosen = firstValidBlazeTarget(frontline, ranged, priority);
        if (chosen != null) {
            this.setTarget(chosen);
            combatHeatTicks = Math.min(20 * 20, combatHeatTicks + 5);
        } else {
            combatHeatTicks = Math.max(0, combatHeatTicks - 10);
            if (this.getTarget() != null && !isValidBlazeCombatTarget(this.getTarget())) {
                this.setTarget(null);
            }
        }

        if (incomingFireballs > 0) {
            tryStartFireShield(level);
        }

        if (close != null && blazeChargeCooldownTicks <= 0 && blazeChargeTicks <= 0) {
            startBlazeCharge(level, close);
        }

        if (chosen == null || !canUseBlazeFireballAgainst(chosen)) return;

        if (infernoTicks <= 0
                && infernoCooldownTicks <= 0
                && combatHeatTicks >= 80
                && (hostileCount >= 5
                    || clusterSize >= 4
                    || chosen.getMaxHealth() >= 100.0F)) {
            startInfernoBarrage(level);
            return;
        }

        if (furyTicks <= 0
                && furyCooldownTicks <= 0
                && infernoTicks <= 0
                && (hostileCount >= 3
                    || this.getHealth() <= this.getMaxHealth() * 0.62F
                    || (angerTicks > 0 && combatHeatTicks >= 100))) {
            startBlazingFury(level);
            return;
        }

        if (angerTicks <= 0
                && angerCooldownTicks <= 0
                && furyTicks <= 0
                && infernoTicks <= 0
                && combatHeatTicks >= 40) {
            startAngerOfFire(level);
        }

        if (barrageShotsRemaining <= 0 && barrageCooldownTicks <= 0 && infernoTicks <= 0) {
            startBlazeBarrage();
        }
    }

    private void maintainLongRangeAccess() {
        if (blazeChargeTicks > 0) return;

        LivingEntity target = getBlazeArtilleryTarget();
        if (target == null || !canUseBlazeFireballAgainst(target)) return;

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr > BLAZE_PREFERRED_MAX_RANGE * BLAZE_PREFERRED_MAX_RANGE) {
            this.getNavigation().moveTo(target, furyTicks > 0 || infernoTicks > 0 ? 1.20D : 1.10D);
        } else if (!this.getSensing().hasLineOfSight(target) && this.tickCount % 10 == 0) {
            // Move toward the target enough to hunt for a new firing lane rather
            // than standing behind a wall firing nothing forever.
            this.getNavigation().moveTo(target, 1.08D);
        }
    }

    private LivingEntity firstValidBlazeTarget(LivingEntity... candidates) {
        for (LivingEntity candidate : candidates) {
            if (candidate != null && isValidBlazeCombatTarget(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasBlazeCombatTarget() {
        return this.getTarget() != null
                || this.getBrain().getMemory(ModMemoryModuleTypes.BLAZE_PRIORITY_THREAT.get()).isPresent();
    }

    // ---------------------------------------------------------------------
    // Blaze Barrage / Inferno Barrage
    // ---------------------------------------------------------------------

    private void startBlazeBarrage() {
        if (furyTicks > 0) {
            barrageShotsRemaining = FURY_BARRAGE_SHOTS;
            barrageCooldownTicks = FURY_BARRAGE_COOLDOWN;
        } else if (angerTicks > 0) {
            barrageShotsRemaining = ANGER_BARRAGE_SHOTS;
            barrageCooldownTicks = ANGER_BARRAGE_COOLDOWN;
        } else {
            barrageShotsRemaining = NORMAL_BARRAGE_SHOTS;
            barrageCooldownTicks = NORMAL_BARRAGE_COOLDOWN;
        }
        barrageShotDelayTicks = 0;
    }

    private void tickNormalBarrage(ServerLevel level) {
        if (barrageShotsRemaining <= 0 || barrageShotDelayTicks > 0) return;

        List<LivingEntity> targets = getCurrentRangedBlazeTargets(level);
        if (targets.isEmpty()) {
            barrageShotsRemaining = 0;
            return;
        }

        LivingEntity target = selectBarrageTarget(targets, false);
        if (target != null && fireBlazeFireball(level, target)) {
            --barrageShotsRemaining;
            barrageShotDelayTicks = furyTicks > 0
                    ? FURY_SHOT_DELAY
                    : angerTicks > 0 ? ANGER_SHOT_DELAY : NORMAL_SHOT_DELAY;
        } else {
            // Do not hammer a blocked firing lane every tick.
            barrageShotDelayTicks = 4;
        }
    }

    private void tickInfernoBarrage(ServerLevel level) {
        if (infernoTicks <= 0 || barrageShotDelayTicks > 0) return;

        List<LivingEntity> targets = getCurrentRangedBlazeTargets(level);
        if (targets.isEmpty()) return;

        LivingEntity target = selectBarrageTarget(targets, true);
        if (target != null && fireBlazeFireball(level, target)) {
            barrageShotDelayTicks = INFERNO_SHOT_DELAY;
        } else {
            barrageShotDelayTicks = 3;
        }
    }

    private LivingEntity selectBarrageTarget(List<LivingEntity> targets, boolean inferno) {
        if (targets.isEmpty()) return null;

        // One enemy = focused barrage. Multiple enemies = controlled spread.
        if (targets.size() == 1) return targets.get(0);

        int spreadCap = inferno ? Math.min(5, targets.size()) : Math.min(3, targets.size());
        int index = Math.floorMod(barrageTargetCursor++, spreadCap);
        return targets.get(index);
    }

    private List<LivingEntity> getCurrentRangedBlazeTargets(ServerLevel level) {
        List<LivingEntity> targets = new ArrayList<>(getNearbyBlazeThreats(level, BLAZE_ARTILLERY_MAX_RANGE));
        targets.removeIf(target -> !canUseBlazeFireballAgainst(target));

        LivingEntity current = this.getTarget();
        targets.sort(Comparator
                .comparingInt((LivingEntity target) -> target == current ? 0 : isFrontlineLockedTarget(target) ? 1 : 2)
                .thenComparingDouble(this::blazeThreatScore));
        return targets;
    }

    private boolean isFrontlineLockedTarget(LivingEntity target) {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLAZE_FRONTLINE_LOCKED_TARGET.get())
                .filter(entity -> entity == target)
                .isPresent();
    }

    private boolean fireBlazeFireball(ServerLevel level, LivingEntity target) {
        if (!canUseBlazeFireballAgainst(target)
                || !this.getSensing().hasLineOfSight(target)
                || !hasFamilySafeFiringLane(level, target)) {
            return false;
        }

        Vec3 muzzle = this.getEyePosition().add(this.getViewVector(1.0F).scale(0.55D));
        Vec3 aimPoint = getPredictiveAimPoint(target, muzzle);
        Vec3 direction = aimPoint.subtract(muzzle);
        if (direction.lengthSqr() < 1.0E-5D) return false;

        SmallFireball fireball = new SmallFireball(level, this, direction);
        fireball.setPos(muzzle.x, muzzle.y - 0.06D, muzzle.z);
        fireball.accelerationPower = infernoTicks > 0 ? 0.135D : furyTicks > 0 ? 0.125D : angerTicks > 0 ? 0.115D : 0.105D;
        level.addFreshEntity(fireball);

        this.getLookControl().setLookAt(target, 35.0F, 35.0F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.90F, 1.02F + this.random.nextFloat() * 0.13F);
        level.sendParticles(
                ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z,
                infernoTicks > 0 ? 9 : 5,
                0.16D, 0.16D, 0.16D,
                0.018D);
        return true;
    }

    private Vec3 getPredictiveAimPoint(LivingEntity target, Vec3 muzzle) {
        Vec3 targetVelocity = target.getDeltaMovement();
        double speed = targetVelocity.length();
        if (speed > MAX_TRACKED_TARGET_SPEED && speed > 1.0E-5D) {
            targetVelocity = targetVelocity.scale(MAX_TRACKED_TARGET_SPEED / speed);
        }

        double maxLead = infernoTicks > 0
                ? INFERNO_MAX_LEAD_TICKS
                : furyTicks > 0
                    ? FURY_MAX_LEAD_TICKS
                    : angerTicks > 0 ? ANGER_MAX_LEAD_TICKS : BASE_MAX_LEAD_TICKS;

        Vec3 centerMass = new Vec3(
                target.getX(),
                target.getY() + target.getBbHeight() * 0.52D,
                target.getZ());

        double firstTravel = muzzle.distanceTo(centerMass) / ESTIMATED_FIREBALL_SPEED;
        double firstLead = Mth.clamp(firstTravel, 0.0D, maxLead);
        Vec3 firstPrediction = centerMass.add(targetVelocity.scale(firstLead));

        // Second pass corrects travel time after the first lead moved the intercept point.
        double secondTravel = muzzle.distanceTo(firstPrediction) / ESTIMATED_FIREBALL_SPEED;
        double secondLead = Mth.clamp(secondTravel, 0.0D, maxLead);

        return centerMass.add(targetVelocity.scale(secondLead));
    }

    private boolean hasFamilySafeFiringLane(ServerLevel level, LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 end = new Vec3(target.getX(), target.getY(0.55D), target.getZ());

        for (LivingEntity family : getNearbyBlazeFamily(level, BLAZE_ARTILLERY_MAX_RANGE)) {
            if (family == this) continue;

            if (distanceToSegmentSqr(family.getEyePosition(), start, end) <= 1.55D * 1.55D) {
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
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    // ---------------------------------------------------------------------
    // Anger of Fire / Blazing Fury / Inferno Barrage
    // ---------------------------------------------------------------------

    private void startAngerOfFire(ServerLevel level) {
        angerTicks = ANGER_DURATION;
        angerCooldownTicks = ANGER_COOLDOWN;
        barrageCooldownTicks = Math.min(barrageCooldownTicks, 8);

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.55D, this.getZ(),
                24, 0.65D, 0.50D, 0.65D, 0.035D);
        this.playSound(SoundEvents.BLAZE_AMBIENT, 0.85F, 1.10F);
    }

    private void startBlazingFury(ServerLevel level) {
        furyTicks = FURY_DURATION;
        furyCooldownTicks = FURY_COOLDOWN;
        angerTicks = 0;
        barrageCooldownTicks = Math.min(barrageCooldownTicks, 5);

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.58D, this.getZ(),
                38, 0.90D, 0.62D, 0.90D, 0.045D);
        level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.58D, this.getZ(),
                12, 0.60D, 0.45D, 0.60D, 0.025D);
        this.playSound(SoundEvents.BLAZE_BURN, 1.0F, 0.88F);
    }

    private void startInfernoBarrage(ServerLevel level) {
        infernoTicks = INFERNO_DURATION;
        infernoCooldownTicks = INFERNO_COOLDOWN;
        angerTicks = 0;
        furyTicks = 0;
        barrageShotsRemaining = 0;
        barrageCooldownTicks = 0;
        barrageShotDelayTicks = 0;

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.62D, this.getZ(),
                60, 1.35D, 0.85D, 1.35D, 0.055D);
        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 0.62D, this.getZ(),
                24, 0.90D, 0.65D, 0.90D, 0.030D);
        this.playSound(SoundEvents.BLAZE_BURN, 1.15F, 0.72F);
    }

    private void sendBlazeOrbit(ServerLevel level, int count, double radius) {
        double rotation = this.tickCount * 0.30D;
        for (int i = 0; i < count; ++i) {
            double angle = rotation + (Math.PI * 2.0D * i) / count;
            level.sendParticles(
                    (i & 1) == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + Math.cos(angle) * radius,
                    this.getY() + 0.50D + Math.sin(angle * 2.0D) * 0.18D,
                    this.getZ() + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.002D);
        }
    }

    // ---------------------------------------------------------------------
    // Fire Shield
    // ---------------------------------------------------------------------

    private void tryStartFireShield(ServerLevel level) {
        if (fireShieldTicks > 0 || fireShieldCooldownTicks > 0) return;

        fireShieldTicks = FIRE_SHIELD_DURATION;
        fireShieldCooldownTicks = FIRE_SHIELD_COOLDOWN;

        interceptHostileFireballs(level);

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.55D, this.getZ(),
                30, 0.95D, 0.65D, 0.95D, 0.035D);
        this.playSound(SoundEvents.BLAZE_BURN, 0.85F, 1.25F);
    }

    private void interceptHostileFireballs(ServerLevel level) {
        // Only actual fireballs are erased. Wither skulls and unrelated magical
        // projectiles are not silently turned into Fire Shield targets.
        for (Fireball projectile : level.getEntitiesOfClass(
                Fireball.class,
                this.getBoundingBox().inflate(4.5D),
                projectile -> projectile.isAlive() && projectile.getOwner() != this)) {
            if (projectile.getOwner() == null || !isBlazeFamilyMember(projectile.getOwner())) {
                level.sendParticles(
                        ParticleTypes.FLAME,
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.18D, 0.18D, 0.18D, 0.02D);
                projectile.discard();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Blaze Charge — ranged repositioning, not a melee rush
    // ---------------------------------------------------------------------

    private void startBlazeCharge(ServerLevel level, LivingEntity threat) {
        Vec3 away = new Vec3(this.getX() - threat.getX(), 0.0D, this.getZ() - threat.getZ());
        if (away.lengthSqr() < 1.0E-5D) {
            Vec3 look = this.getViewVector(1.0F);
            away = new Vec3(-look.x, 0.0D, -look.z);
        }
        away = away.normalize();

        double sideSign = (this.getId() & 1) == 0 ? 1.0D : -1.0D;
        Vec3 tangent = new Vec3(-away.z, 0.0D, away.x).scale(sideSign * 0.45D);
        Vec3 direction = away.add(tangent);
        if (direction.lengthSqr() < 1.0E-5D) return;

        blazeChargeDirection = direction.normalize();
        blazeChargeTicks = BLAZE_CHARGE_DURATION;
        blazeChargeCooldownTicks = BLAZE_CHARGE_COOLDOWN;
        this.getNavigation().stop();

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.40D, this.getZ(),
                18, 0.45D, 0.35D, 0.45D, 0.035D);
    }

    private void tickBlazeCharge(ServerLevel level) {
        if (blazeChargeTicks <= 0) return;
        --blazeChargeTicks;

        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(
                blazeChargeDirection.x * BLAZE_CHARGE_SPEED,
                Math.max(movement.y, 0.08D),
                blazeChargeDirection.z * BLAZE_CHARGE_SPEED);
        this.hurtMarked = true;
        this.setSprinting(true);

        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX() - blazeChargeDirection.x * 0.40D,
                this.getY() + 0.35D,
                this.getZ() - blazeChargeDirection.z * 0.40D,
                4, 0.18D, 0.18D, 0.18D, 0.015D);

        if (blazeChargeTicks <= 0) {
            this.setSprinting(false);
            blazeChargeDirection = Vec3.ZERO;
        }
    }

    // ---------------------------------------------------------------------
    // Blaze's Presence — offensive pack support
    // ---------------------------------------------------------------------

    private void pulseBlazesPresence(ServerLevel level) {
        if (!hasBlazeCombatTarget()) return;

        for (LivingEntity family : getNearbyBlazeFamily(level, BLAZE_PRESENCE_RADIUS)) {
            if (family == this || !family.isAlive()) continue;

            family.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH,
                    28,
                    0,
                    true,
                    false), this);

            // Very short Speed I refresh = combat momentum rather than a giant
            // permanent universal steroid.
            family.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    28,
                    0,
                    true,
                    false), this);
        }

        if (this.tickCount % 20 == 0) {
            level.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.25D, this.getZ(),
                    8, BLAZE_PRESENCE_RADIUS * 0.35D, 0.25D, BLAZE_PRESENCE_RADIUS * 0.35D, 0.005D);
        }
    }

    // ---------------------------------------------------------------------
    // Tactical helpers used by Blaze Awareness / artillery goal
    // ---------------------------------------------------------------------

    @Override
    protected boolean canRetainDistantWolfismTargetForRangedAbility(LivingEntity target) {
        return canUseBlazeFireballAgainst(target)
                && this.distanceToSqr(target) <= BLAZE_ARTILLERY_MAX_RANGE * BLAZE_ARTILLERY_MAX_RANGE;
    }

    public LivingEntity getBlazeArtilleryTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && isValidBlazeCombatTarget(target)) return target;

        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.BLAZE_RANGED_TARGET.get())
                .filter(this::isValidBlazeCombatTarget)
                .orElse(null);
    }

    public List<LivingEntity> getNearbyBlazeThreats(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isValidBlazeCombatTarget);
    }

    public boolean isValidBlazeCombatTarget(LivingEntity entity) {
        if (entity == null
                || entity == this
                || !entity.isAlive()
                || isBlazeFamilyMember(entity)
                || this.isAlliedTo(entity)) {
            return false;
        }

        if (entity == this.getTarget() || entity == this.getFamilyDefenseTarget()) return true;
        if (entity instanceof Mob mob && isBlazeFamilyMember(mob.getTarget())) return true;

        // Wild Blaze Wolves are not indiscriminate turret mobs. Tamed ones actively
        // pressure ordinary hostile mobs for their owner/family.
        return this.isTame() && entity instanceof Enemy;
    }

    public boolean canUseBlazeFireballAgainst(LivingEntity entity) {
        return isValidBlazeCombatTarget(entity)
                && !entity.fireImmune()
                && this.distanceToSqr(entity) <= BLAZE_ARTILLERY_MAX_RANGE * BLAZE_ARTILLERY_MAX_RANGE;
    }

    public double blazeThreatScore(LivingEntity entity) {
        int priority = 3;
        if (entity == this.getTarget()) priority = 0;
        else if (entity instanceof Mob mob && isBlazeFamilyMember(mob.getTarget())) priority = 1;
        else if (!entity.fireImmune()) priority = 2;

        return priority * 100000.0D + this.distanceToSqr(entity);
    }

    public boolean isBlazeFamilyMember(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (living == this || living == this.getOwner()) return true;
        if (this.isTame() && living instanceof Wolf wolf && wolf.isTame()) return true;
        return this.isTame() && this.isAlliedTo(living);
    }

    public List<LivingEntity> getNearbyBlazeFamily(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                entity -> isBlazeFamilyMember(entity));
    }

    public boolean isFamilyFrontlinerEngaging(LivingEntity hostile, ServerLevel level) {
        if (hostile == null) return false;

        for (LivingEntity family : getNearbyBlazeFamily(level, BLAZE_AWARENESS_RADIUS)) {
            if (family == this || !family.isAlive()) continue;

            if (family instanceof Mob mob
                    && mob.getTarget() == hostile
                    && family.distanceToSqr(hostile) <= 5.0D * 5.0D) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Natural spawning — rare solitary Nether Wastes artillery wolf
    // ---------------------------------------------------------------------

    public static boolean checkBlazeWolfSpawnRules(
            EntityType<BlazeWolf> type,
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

        // Rare, solitary natural sightings. Tamed Blaze Wolves do not suppress spawns.
        return serverLevel.getEntitiesOfClass(
                        BlazeWolf.class,
                        new AABB(pos).inflate(56.0D, 28.0D, 56.0D),
                        wolf -> wolf.isAlive() && !wolf.isTame())
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BlazeBarrageCooldown", barrageCooldownTicks);
        output.putInt("BlazeAngerCooldown", angerCooldownTicks);
        output.putInt("BlazeFuryCooldown", furyCooldownTicks);
        output.putInt("BlazeInfernoCooldown", infernoCooldownTicks);
        output.putInt("BlazeFireShieldCooldown", fireShieldCooldownTicks);
        output.putInt("BlazeChargeCooldown", blazeChargeCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        barrageCooldownTicks = Math.max(0, input.getIntOr("BlazeBarrageCooldown", 0));
        angerCooldownTicks = Math.max(0, input.getIntOr("BlazeAngerCooldown", 0));
        furyCooldownTicks = Math.max(0, input.getIntOr("BlazeFuryCooldown", 0));
        infernoCooldownTicks = Math.max(0, input.getIntOr("BlazeInfernoCooldown", 0));
        fireShieldCooldownTicks = Math.max(0, input.getIntOr("BlazeFireShieldCooldown", 0));
        blazeChargeCooldownTicks = Math.max(0, input.getIntOr("BlazeChargeCooldown", 0));
    }

    // ---------------------------------------------------------------------
    // Client helpers
    // ---------------------------------------------------------------------

    private void syncBlazeVisualState(boolean combat) {
        this.entityData.set(DATA_BLAZE_COMBAT_ACTIVE, combat || infernoTicks > 0);
        this.entityData.set(DATA_ANGER_ACTIVE, angerTicks > 0);
        this.entityData.set(DATA_FURY_ACTIVE, furyTicks > 0);
        this.entityData.set(DATA_INFERNO_ACTIVE, infernoTicks > 0);
        this.entityData.set(DATA_FIRE_SHIELD_ACTIVE, fireShieldTicks > 0);
    }

    public boolean isBlazeCombatVisualActive() {
        return this.entityData.get(DATA_BLAZE_COMBAT_ACTIVE);
    }

    public boolean isAngerOfFireActive() {
        return this.entityData.get(DATA_ANGER_ACTIVE);
    }

    public boolean isBlazingFuryActive() {
        return this.entityData.get(DATA_FURY_ACTIVE);
    }

    public boolean isInfernoBarrageActive() {
        return this.entityData.get(DATA_INFERNO_ACTIVE);
    }

    public boolean isFireShieldActive() {
        return this.entityData.get(DATA_FIRE_SHIELD_ACTIVE);
    }

    private void cancelBlazeCombat() {
        barrageShotsRemaining = 0;
        barrageShotDelayTicks = 0;
        angerTicks = 0;
        furyTicks = 0;
        infernoTicks = 0;
        fireShieldTicks = 0;
        blazeChargeTicks = 0;
        blazeChargeDirection = Vec3.ZERO;
        combatHeatTicks = 0;
        this.setSprinting(false);
    }
}
