package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.MagmaChargeGoal;
import net.ronm19.wolfism.entity.ai.sensor.MagmaWolfPackSensor;
import net.ronm19.wolfism.entity.ai.util.MagmaTerrainManager;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Magma Wolf — five-star Nether tank / area-control specialist.
 *
 * <p>Magma does not try to out-DPS Fire, out-artillery Blaze, or out-scale
 * Infernal. His job is to make the battlefield itself miserable: safe lava
 * traversal for the family, temporary magma terrain, a heavyweight charge,
 * Nether intimidation, family heat protection, and a non-griefing molten
 * bombardment ultimate.</p>
 */
public final class MagmaWolf extends AbstractWolfismWolf {
    private static final double BASE_MAX_HEALTH = 72.0D;
    private static final float MAGMA_ARMOR_DAMAGE_MULTIPLIER = 0.68F;

    public static final double PACK_SCAN_RADIUS = 30.0D;
    public static final double TACTICAL_AWARENESS_RADIUS = 30.0D;

    // Obsidian Path ---------------------------------------------------
    public static final int OBSIDIAN_PATH_LIFETIME_TICKS = 20 * 25;
    private static final int OBSIDIAN_ESCORT_REPLAN_INTERVAL = 20;
    private static final int OBSIDIAN_ESCORT_DURATION_TICKS = 20 * 20;
    private static final double OBSIDIAN_ESCORT_START_OWNER_RADIUS = 12.0D;
    private static final double OBSIDIAN_ESCORT_ACTIVE_OWNER_RADIUS = 36.0D;
    private static final int OBSIDIAN_ESCORT_MAX_CROSSING = 28;

    // Magma Eruption --------------------------------------------------
    public static final int ERUPTION_COOLDOWN_TICKS = 20 * 15;
    public static final int ERUPTION_TERRAIN_LIFETIME_TICKS = 20 * 7;
    private static final double ERUPTION_RADIUS = 5.5D;
    private static final float ERUPTION_PHYSICAL_DAMAGE = 4.5F;
    private static final int ERUPTION_BURN_TICKS = 20 * 3;

    // Magma Charge ----------------------------------------------------
    public static final int CHARGE_COOLDOWN_TICKS = 20 * 24;
    private static final int CHARGE_DURATION_TICKS = 18;
    private static final double CHARGE_SPEED = 0.72D;
    private static final double CHARGE_STEER_BLEND = 0.12D;
    private static final double CHARGE_IMPACT_DISTANCE_SQR = 2.7D * 2.7D;
    /** 7.5 radius ~= a 15-block-wide disruption zone. */
    private static final double CHARGE_DISRUPTION_RADIUS = 7.5D;

    // Magma Charm -----------------------------------------------------
    private static final double CHARM_RADIUS = 14.0D;
    private static final double CHARM_COMMITTED_RANGE_SQR = 3.5D * 3.5D;

    // Magma's Protection ---------------------------------------------
    private static final double PROTECTION_RADIUS = 16.0D;
    private static final int PROTECTION_REFRESH_TICKS = 20 * 6;

    // Magma Shower ----------------------------------------------------
    public static final int MAGMA_SHOWER_COOLDOWN_TICKS = 20 * 60;
    private static final int MAGMA_SHOWER_DURATION_TICKS = 20 * 5;
    private static final int MAGMA_SHOWER_SCHEDULE_INTERVAL = 6;
    private static final int MAGMA_SHOWER_MAX_PROJECTILES = 14;
    private static final int MAGMA_PROJECTILE_FALL_TICKS = 12;
    private static final double MAGMA_SHOWER_TARGET_RANGE = 26.0D;
    private static final double MAGMA_PROJECTILE_RADIUS = 2.65D;
    private static final float MAGMA_PROJECTILE_DAMAGE = 9.5F;
    private static final int MAGMA_PROJECTILE_BURN_TICKS = 20 * 5;
    private static final int MAGMA_HAZARD_ZONE_TICKS = 20 * 3;
    private static final int MAGMA_HAZARD_PULSE_INTERVAL = 5;
    private static final float MAGMA_HAZARD_PULSE_DAMAGE = 1.5F;

    // State -----------------------------------------------------------
    private int eruptionCooldownTicks;

    private BlockPos obsidianEscortDestination;
    private int obsidianEscortTicks;

    private int chargeCooldownTicks;
    private int chargeTicks;
    private int chargeTargetId = -1;
    private Vec3 chargeDirection = Vec3.ZERO;

    private int magmaShowerCooldownTicks;
    private int magmaShowerTicks;
    private int magmaShowerProjectilesScheduled;
    private final List<MagmaProjectileMarker> pendingMagmaProjectiles = new ArrayList<>();
    private final List<MagmaHazardZone> activeMagmaHazards = new ArrayList<>();

    private static final class BrainHolder {
        private static final Brain.Provider<MagmaWolf> PROVIDER = Brain.<MagmaWolf>provider(
                ImmutableList.of(
                        ModSensorTypes.MAGMA_PACK.get(),
                        ModSensorTypes.MAGMA_TACTICAL.get()),
                wolf -> List.of());
    }

    public MagmaWolf(EntityType<? extends MagmaWolf> type, Level level) {
        super(type, level);

        // Lava is home terrain, not something this tank should path around.
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.MAGMA_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8.5D)
                .add(Attributes.ARMOR, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 44.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.62D);
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

    // -----------------------------------------------------------------
    // Brain / goals
    // -----------------------------------------------------------------

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // A deliberate, heavy wind-up; Creeper retreat remains priority 0 in the base.
        this.goalSelector.addGoal(2, new MagmaChargeGoal(this));
    }

    @Override
    protected Brain<MagmaWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<MagmaWolf> getBrain() {
        return (Brain<MagmaWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickCooldowns();

        if (this.isBaby()) {
            this.setTarget(null);
            this.endMagmaCharge();
            this.magmaShowerTicks = 0;
            this.pendingMagmaProjectiles.clear();
            this.activeMagmaHazards.clear();
            this.clearObsidianEscort();
            return;
        }

        // Expedition-first traversal: Magma looks for a real crossing and
        // leads the owner toward the safe bank instead of waiting for someone
        // to step into lava first.
        this.tickObsidianEscort(level);

        // Reactive fallback still lays temporary obsidian directly under/around
        // Magma whenever he is already traversing lava.
        MagmaTerrainManager.createObsidianPath(level, this);

        this.tickTacticalBrain(level);
        this.tickMagmasProtection(level);
        this.tickMagmaCharm(level);

        if (this.chargeTicks > 0) {
            this.tickMagmaCharge(level);
        }

        // Ultimate gets first decision priority so Eruption does not fire on
        // the exact same tick and muddy Magma's ability selection.
        this.tryStartMagmaShower(level);
        this.tryStartMagmaEruption(level);
        this.tickMagmaShower(level);
        this.tickPendingMagmaProjectiles(level);
        this.tickMagmaHazards(level);
    }

    private void tickCooldowns() {
        if (this.eruptionCooldownTicks > 0) --this.eruptionCooldownTicks;
        if (this.chargeCooldownTicks > 0) --this.chargeCooldownTicks;
        if (this.magmaShowerCooldownTicks > 0) --this.magmaShowerCooldownTicks;
    }

    private void tickTacticalBrain(ServerLevel level) {
        if (this.tickCount % 5 != 0) return;

        LivingEntity current = this.getTarget();
        if (current != null && this.isMagmaFamilyMember(current)) {
            this.setTarget(null);
            current = null;
        }

        if (current != null && current.isAlive()) return;

        LivingEntity familyThreat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MAGMA_PACK_THREAT.get())
                .orElse(null);

        LivingEntity tacticalThreat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MAGMA_TACTICAL_THREAT.get())
                .orElse(null);

        LivingEntity chosen = familyThreat != null ? familyThreat : tacticalThreat;
        if (chosen == null || !chosen.isAlive() || !this.isValidMagmaThreat(chosen)) return;

        // Awareness is intentionally broader than physical aggression. The
        // ranged/area abilities may still use distant sensor memories.
        if (this.distanceToSqr(chosen) <= 12.0D * 12.0D || isTargetingMagmaFamily(chosen)) {
            this.setTarget(chosen);
        }
    }

    // -----------------------------------------------------------------
    // Obsidian Path — proactive Nether expedition escort
    // -----------------------------------------------------------------

    private void tickObsidianEscort(ServerLevel level) {
        if (!this.isTame()
                || this.isBaby()
                || this.isOrderedToSit()
                || this.chargeTicks > 0
                || this.magmaShowerTicks > 0
                || this.hasFamilyDefenseEmergency()) {
            this.clearObsidianEscort();
            return;
        }

        LivingEntity owner = this.getOwner();
        double ownerRadius = this.obsidianEscortDestination != null
                ? OBSIDIAN_ESCORT_ACTIVE_OWNER_RADIUS
                : OBSIDIAN_ESCORT_START_OWNER_RADIUS;

        if (owner == null
                || !owner.isAlive()
                || owner.level() != level
                || this.distanceToSqr(owner) > ownerRadius * ownerRadius) {
            this.clearObsidianEscort();
            return;
        }

        LivingEntity activeTarget = this.getTarget();
        if (activeTarget != null && activeTarget.isAlive()) {
            this.clearObsidianEscort();
            return;
        }

        if (this.obsidianEscortDestination != null && this.obsidianEscortTicks > 0) {
            --this.obsidianEscortTicks;

            Vec3 destination = Vec3.atBottomCenterOf(this.obsidianEscortDestination);
            if (this.position().distanceToSqr(destination) <= 2.25D) {
                this.clearObsidianEscort();
                return;
            }

            this.getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    1.18D);
            return;
        }

        if (this.tickCount % OBSIDIAN_ESCORT_REPLAN_INTERVAL != 0) return;

        BlockPos destination = MagmaTerrainManager.createGuidedObsidianCrossing(
                level,
                this,
                owner,
                OBSIDIAN_ESCORT_MAX_CROSSING,
                OBSIDIAN_PATH_LIFETIME_TICKS);

        if (destination == null) return;

        this.obsidianEscortDestination = destination;
        this.obsidianEscortTicks = OBSIDIAN_ESCORT_DURATION_TICKS;

        Vec3 target = Vec3.atBottomCenterOf(destination);
        this.getNavigation().moveTo(target.x, target.y, target.z, 1.18D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LAVA,
                this.getX(),
                this.getY() + 0.20D,
                this.getZ(),
                8,
                0.35D, 0.10D, 0.35D,
                0.025D);
    }

    private void clearObsidianEscort() {
        this.obsidianEscortDestination = null;
        this.obsidianEscortTicks = 0;
    }

    // -----------------------------------------------------------------
    // Fire / lava immunity + traversal
    // -----------------------------------------------------------------

    /**
     * Magma Armor: five-star tank durability without Creator-style immunity.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) return false;

        float adjusted = amount;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            adjusted *= MAGMA_ARMOR_DAMAGE_MULTIPLIER;
        }

        return super.hurtServer(level, source, adjusted);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return true;
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA) || super.canStandOnFluid(fluid);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new MagmaPathNavigation(this, level);
    }

    private static final class MagmaPathNavigation extends GroundPathNavigation {
        private MagmaPathNavigation(MagmaWolf wolf, Level level) {
            super(wolf, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new WalkNodeEvaluator();
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }

        @Override
        protected boolean hasValidPathType(PathType pathType) {
            return pathType == PathType.LAVA
                    || pathType == PathType.FIRE
                    || pathType == PathType.FIRE_IN_NEIGHBOR
                    || super.hasValidPathType(pathType);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            return this.level.getFluidState(pos).is(FluidTags.LAVA)
                    || super.isStableDestination(pos);
        }
    }

    // -----------------------------------------------------------------
    // Magma Eruption — 15s area-control cycle
    // -----------------------------------------------------------------

    private void tryStartMagmaEruption(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.eruptionCooldownTicks > 0
                || this.chargeTicks > 0
                || this.magmaShowerTicks > 0) {
            return;
        }

        LivingEntity threat = this.getTarget();
        if (threat == null || !threat.isAlive()) {
            threat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.MAGMA_TACTICAL_THREAT.get())
                    .orElse(null);
        }

        if (threat == null
                || !threat.isAlive()
                || !this.isValidMagmaThreat(threat)
                || this.distanceToSqr(threat) > 14.0D * 14.0D) {
            return;
        }

        int hostiles = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MAGMA_HOSTILE_COUNT.get())
                .orElse(0);

        boolean lavaPresent = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MAGMA_LAVA_SURFACE.get())
                .isPresent();

        if (!lavaPresent && hostiles < 2 && threat.getMaxHealth() < 35.0F) return;

        this.eruptionCooldownTicks = ERUPTION_COOLDOWN_TICKS;
        BlockPos center = threat.blockPosition();

        MagmaTerrainManager.createEruption(
                level,
                this,
                center,
                (int) Math.ceil(ERUPTION_RADIUS),
                ERUPTION_TERRAIN_LIFETIME_TICKS);

        DamageSource impact = this.damageSources().mobAttack(this);
        AABB area = new AABB(center).inflate(ERUPTION_RADIUS, 2.5D, ERUPTION_RADIUS);

        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidMagmaHazardVictim)) {

            if (!victim.hurtServer(level, impact, ERUPTION_PHYSICAL_DAMAGE)) continue;

            victim.setRemainingFireTicks(Math.max(
                    victim.getRemainingFireTicks(),
                    ERUPTION_BURN_TICKS));

            double dx = center.getX() + 0.5D - victim.getX();
            double dz = center.getZ() + 0.5D - victim.getZ();
            victim.knockback(0.65D, dx, dz);
        }

        // Family receives the protection half of the area-control identity.
        if (this.isTame()) {
            for (LivingEntity family : level.getEntitiesOfClass(
                    LivingEntity.class,
                    this.getBoundingBox().inflate(ERUPTION_RADIUS + 2.0D),
                    this::isMagmaFamilyMember)) {

                family.addEffect(new MobEffectInstance(
                        MobEffects.FIRE_RESISTANCE,
                        ERUPTION_TERRAIN_LIFETIME_TICKS + 20,
                        0,
                        true,
                        false));

                family.addEffect(new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        ERUPTION_TERRAIN_LIFETIME_TICKS,
                        0,
                        true,
                        false));
            }
        }

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LAVA,
                center.getX() + 0.5D,
                center.getY() + 0.45D,
                center.getZ() + 0.5D,
                28,
                ERUPTION_RADIUS * 0.45D,
                0.35D,
                ERUPTION_RADIUS * 0.45D,
                0.04D);
    }

    // -----------------------------------------------------------------
    // Magma Charge — heavyweight physical breakthrough
    // -----------------------------------------------------------------

    public boolean isMagmaCharging() {
        return this.chargeTicks > 0;
    }

    public boolean canStartMagmaChargeAgainst(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.canUseActiveWolfismAbility()
                || this.chargeTicks > 0
                || this.chargeCooldownTicks > 0
                || this.magmaShowerTicks > 0
                || !this.onGround()
                || !this.isValidMagmaThreat(target)
                || !this.getSensing().hasLineOfSight(target)) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= MagmaChargeGoal.MIN_START_DISTANCE_SQR
                && distance <= MagmaChargeGoal.MAX_START_DISTANCE_SQR;
    }

    public boolean beginMagmaCharge(LivingEntity target) {
        if (!this.canStartMagmaChargeAgainst(target)) return false;

        Vec3 direction = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (direction.lengthSqr() < 1.0E-5D) return false;

        this.chargeDirection = direction.normalize();
        this.chargeTicks = CHARGE_DURATION_TICKS;
        this.chargeTargetId = target.getId();
        this.chargeCooldownTicks = CHARGE_COOLDOWN_TICKS;
        this.getNavigation().stop();
        this.setSprinting(true);
        return true;
    }

    public void endMagmaCharge() {
        this.chargeTicks = 0;
        this.chargeTargetId = -1;
        this.chargeDirection = Vec3.ZERO;
        this.setSprinting(false);
    }

    private void tickMagmaCharge(ServerLevel level) {
        LivingEntity target = this.chargeTargetId < 0
                ? null
                : level.getEntity(this.chargeTargetId) instanceof LivingEntity living
                        ? living
                        : null;

        if (target == null || !target.isAlive() || !this.isValidMagmaThreat(target)) {
            this.impactMagmaCharge(level, null);
            return;
        }

        Vec3 desired = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (desired.lengthSqr() > 1.0E-5D) {
            desired = desired.normalize();
            Vec3 steered = this.chargeDirection
                    .scale(1.0D - CHARGE_STEER_BLEND)
                    .add(desired.scale(CHARGE_STEER_BLEND));
            if (steered.lengthSqr() > 1.0E-5D) {
                this.chargeDirection = steered.normalize();
            }
        }

        this.getLookControl().setLookAt(target, 45.0F, 30.0F);

        if (this.distanceToSqr(target) <= CHARGE_IMPACT_DISTANCE_SQR
                || this.horizontalCollision
                || this.chargeTicks <= 1) {
            this.impactMagmaCharge(level, target);
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(
                this.chargeDirection.x * CHARGE_SPEED,
                motion.y,
                this.chargeDirection.z * CHARGE_SPEED);
        this.hurtMarked = true;
        --this.chargeTicks;

        if (this.tickCount % 2 == 0) {
            WolfVfx.sendParticles("magma_wolf", level,
                    ParticleTypes.LAVA,
                    this.getX() - this.chargeDirection.x * 0.45D,
                    this.getY() + 0.22D,
                    this.getZ() - this.chargeDirection.z * 0.45D,
                    2,
                    0.22D, 0.10D, 0.22D,
                    0.02D);
        }
    }

    private void impactMagmaCharge(ServerLevel level, LivingEntity primary) {
        Vec3 impact = primary != null ? primary.position() : this.position();
        AABB area = new AABB(
                impact.x - CHARGE_DISRUPTION_RADIUS,
                impact.y - 2.0D,
                impact.z - CHARGE_DISRUPTION_RADIUS,
                impact.x + CHARGE_DISRUPTION_RADIUS,
                impact.y + 4.0D,
                impact.z + CHARGE_DISRUPTION_RADIUS);

        DamageSource physical = this.damageSources().mobAttack(this);
        double attack = this.getAttributeValue(Attributes.ATTACK_DAMAGE);

        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidMagmaHazardVictim)) {

            double horizontal = Math.sqrt(
                    (victim.getX() - impact.x) * (victim.getX() - impact.x)
                    + (victim.getZ() - impact.z) * (victim.getZ() - impact.z));

            if (horizontal > CHARGE_DISRUPTION_RADIUS) continue;

            boolean primaryHit = victim == primary;
            float damage = primaryHit
                    ? (float) (attack * 1.65D)
                    : 5.5F;

            if (!victim.hurtServer(level, physical, damage)) continue;

            victim.setRemainingFireTicks(Math.max(
                    victim.getRemainingFireTicks(),
                    primaryHit ? 20 * 7 : 20 * 4));

            double dx = impact.x - victim.getX();
            double dz = impact.z - victim.getZ();
            double falloff = 1.0D - Math.min(1.0D, horizontal / CHARGE_DISRUPTION_RADIUS);
            double knockback = (primaryHit ? 2.8D : 1.4D) + falloff * 0.9D;
            victim.knockback(knockback, dx, dz);
        }

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.EXPLOSION,
                impact.x,
                impact.y + 0.35D,
                impact.z,
                2,
                0.40D, 0.22D, 0.40D,
                0.0D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LAVA,
                impact.x,
                impact.y + 0.25D,
                impact.z,
                24,
                1.7D, 0.35D, 1.7D,
                0.055D);

        this.endMagmaCharge();
    }

    // -----------------------------------------------------------------
    // Magma Charm — Nether intimidation, not mind control
    // -----------------------------------------------------------------

    private void tickMagmaCharm(ServerLevel level) {
        if (level.dimension() != Level.NETHER || this.tickCount % 5 != 0) return;

        for (Mob mob : level.getEntitiesOfClass(
                Mob.class,
                this.getBoundingBox().inflate(CHARM_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && isMagmaCharmEligible(candidate))) {

            LivingEntity target = mob.getTarget();
            boolean targetingFamily = target != null && this.isMagmaFamilyMember(target);

            LivingEntity lastHurt = mob.getLastHurtByMob();
            boolean familyProvoked = lastHurt != null && this.isMagmaFamilyMember(lastHurt);
            boolean committed = targetingFamily
                    && mob.distanceToSqr(target) <= CHARM_COMMITTED_RANGE_SQR;

            if (familyProvoked || committed) continue;
            if (!targetingFamily) continue;

            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);

            Vec3 away = mob.position().subtract(this.position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);

            if (horizontal.lengthSqr() > 1.0E-5D) {
                horizontal = horizontal.normalize();

                double retreatX = mob.getX() + horizontal.x * 7.0D;
                double retreatZ = mob.getZ() + horizontal.z * 7.0D;

                mob.getNavigation().moveTo(
                        retreatX,
                        mob.getY(),
                        retreatZ,
                        1.12D);

                mob.push(horizontal.x * 0.28D, 0.035D, horizontal.z * 0.28D);
                mob.hurtMarked = true;
            }
        }
    }

    private static boolean isMagmaCharmEligible(Mob mob) {
        EntityType<?> type = mob.getType();

        return type == EntityType.GHAST
                || type == EntityType.PIGLIN_BRUTE
                || type == EntityType.PIGLIN
                || type == EntityType.HOGLIN
                || type == EntityType.ZOGLIN
                || type == EntityType.BLAZE
                || type == EntityType.WITHER_SKELETON
                || type == EntityType.ZOMBIFIED_PIGLIN
                || type == EntityType.MAGMA_CUBE;
    }

    // -----------------------------------------------------------------
    // Magma's Protection — refreshed family Fire Resistance
    // -----------------------------------------------------------------

    private void tickMagmasProtection(ServerLevel level) {
        if (!this.isTame()
                || !this.isWolfismWorkTick(10)
                || !this.isExtremeHeat(level)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.level() == level
                && this.distanceToSqr(owner) <= PROTECTION_RADIUS * PROTECTION_RADIUS) {
            this.applyMagmaProtection(owner);
        }

        for (Wolf wolf : level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(PROTECTION_RADIUS),
                candidate -> candidate.isAlive()
                        && candidate.isTame()
                        && this.isWolfismFamily(candidate))) {

            this.applyMagmaProtection(wolf);
        }
    }

    private void applyMagmaProtection(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE,
                PROTECTION_REFRESH_TICKS,
                0,
                true,
                false));
    }

    private boolean isExtremeHeat(ServerLevel level) {
        if (level.dimension() == Level.NETHER || this.isInLava() || this.isOnFire()) return true;

        BlockPos center = this.blockPosition();
        for (int dx = -4; dx <= 4; ++dx) {
            for (int dz = -4; dz <= 4; ++dz) {
                for (int dy = -2; dy <= 1; ++dy) {
                    if (level.getFluidState(center.offset(dx, dy, dz)).is(FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Magma Shower — 60s non-griefing molten bombardment ultimate
    // -----------------------------------------------------------------

    private void tryStartMagmaShower(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.magmaShowerCooldownTicks > 0
                || this.magmaShowerTicks > 0
                || this.chargeTicks > 0) {
            return;
        }

        LivingEntity anchor = this.getTarget();
        if (anchor == null || !anchor.isAlive()) {
            anchor = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.MAGMA_TACTICAL_THREAT.get())
                    .orElse(null);
        }

        if (anchor == null
                || !anchor.isAlive()
                || !this.isValidMagmaThreat(anchor)
                || this.distanceToSqr(anchor)
                        > MAGMA_SHOWER_TARGET_RANGE * MAGMA_SHOWER_TARGET_RANGE) {
            return;
        }

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MAGMA_HOSTILE_COUNT.get())
                .orElse(0);

        boolean majorThreat = anchor.getMaxHealth() >= 50.0F;
        if (hostileCount < 3 && !majorThreat && !this.hasFamilyDefenseEmergency()) return;

        this.magmaShowerTicks = MAGMA_SHOWER_DURATION_TICKS;
        this.magmaShowerCooldownTicks = MAGMA_SHOWER_COOLDOWN_TICKS;
        this.magmaShowerProjectilesScheduled = 0;

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LAVA,
                this.getX(),
                this.getY() + 0.75D,
                this.getZ(),
                34,
                1.15D, 0.65D, 1.15D,
                0.065D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.FLAME,
                this.getX(),
                this.getY() + 1.10D,
                this.getZ(),
                42,
                1.25D, 0.85D, 1.25D,
                0.055D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LARGE_SMOKE,
                this.getX(),
                this.getY() + 1.20D,
                this.getZ(),
                14,
                0.85D, 0.55D, 0.85D,
                0.025D);
    }

    private void tickMagmaShower(ServerLevel level) {
        if (this.magmaShowerTicks <= 0) return;

        --this.magmaShowerTicks;

        if (this.magmaShowerProjectilesScheduled < MAGMA_SHOWER_MAX_PROJECTILES
                && this.magmaShowerTicks % MAGMA_SHOWER_SCHEDULE_INTERVAL == 0) {
            LivingEntity target = chooseMagmaShowerTarget(level);
            if (target != null) {
                Vec3 impact = target.position().add(
                        (this.random.nextDouble() - 0.5D) * 3.5D,
                        0.0D,
                        (this.random.nextDouble() - 0.5D) * 3.5D);

                Vec3 start = impact.add(
                        (this.random.nextDouble() - 0.5D) * 8.0D,
                        12.0D + this.random.nextDouble() * 4.0D,
                        (this.random.nextDouble() - 0.5D) * 8.0D);

                this.pendingMagmaProjectiles.add(
                        new MagmaProjectileMarker(
                                start,
                                impact,
                                MAGMA_PROJECTILE_FALL_TICKS));
                ++this.magmaShowerProjectilesScheduled;
            }
        }
    }

    private LivingEntity chooseMagmaShowerTarget(ServerLevel level) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(MAGMA_SHOWER_TARGET_RANGE),
                this::isValidMagmaHazardVictim);

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(this::scoreMagmaThreat).reversed());
        int topPool = Math.min(4, candidates.size());
        return candidates.get(this.random.nextInt(topPool));
    }

    private void tickPendingMagmaProjectiles(ServerLevel level) {
        for (int i = this.pendingMagmaProjectiles.size() - 1; i >= 0; --i) {
            MagmaProjectileMarker marker = this.pendingMagmaProjectiles.get(i);
            --marker.ticks;

            double progress = 1.0D
                    - Math.max(0, marker.ticks) / (double) MAGMA_PROJECTILE_FALL_TICKS;

            Vec3 position = marker.start.lerp(marker.impact, progress);

            WolfVfx.sendParticles("magma_wolf", level,
                    ParticleTypes.FLAME,
                    position.x,
                    position.y,
                    position.z,
                    5,
                    0.18D, 0.18D, 0.18D,
                    0.018D);

            WolfVfx.sendParticles("magma_wolf", level,
                    ParticleTypes.LAVA,
                    position.x,
                    position.y,
                    position.z,
                    3,
                    0.14D, 0.14D, 0.14D,
                    0.018D);

            WolfVfx.sendParticles("magma_wolf", level,
                    ParticleTypes.LARGE_SMOKE,
                    position.x,
                    position.y + 0.15D,
                    position.z,
                    2,
                    0.18D, 0.18D, 0.18D,
                    0.012D);

            if (marker.ticks <= 0) {
                this.impactMagmaProjectile(level, marker.impact);
                this.pendingMagmaProjectiles.remove(i);
            }
        }
    }

    private void impactMagmaProjectile(ServerLevel level, Vec3 impact) {
        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.EXPLOSION,
                impact.x,
                impact.y + 0.3D,
                impact.z,
                1,
                0.30D, 0.18D, 0.30D,
                0.0D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LAVA,
                impact.x,
                impact.y + 0.25D,
                impact.z,
                18,
                0.85D, 0.32D, 0.85D,
                0.055D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.FLAME,
                impact.x,
                impact.y + 0.35D,
                impact.z,
                24,
                1.05D, 0.42D, 1.05D,
                0.065D);

        WolfVfx.sendParticles("magma_wolf", level,
                ParticleTypes.LARGE_SMOKE,
                impact.x,
                impact.y + 0.45D,
                impact.z,
                8,
                0.70D, 0.38D, 0.70D,
                0.035D);

        AABB area = new AABB(
                impact.x - MAGMA_PROJECTILE_RADIUS,
                impact.y - 1.2D,
                impact.z - MAGMA_PROJECTILE_RADIUS,
                impact.x + MAGMA_PROJECTILE_RADIUS,
                impact.y + 3.0D,
                impact.z + MAGMA_PROJECTILE_RADIUS);

        // Physical explosion-style impact: fire immunity does not invalidate the hit.
        DamageSource physicalImpact = this.damageSources().source(DamageTypes.EXPLOSION, this);

        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidMagmaHazardVictim)) {

            if (!victim.hurtServer(level, physicalImpact, MAGMA_PROJECTILE_DAMAGE)) continue;

            victim.setRemainingFireTicks(Math.max(
                    victim.getRemainingFireTicks(),
                    MAGMA_PROJECTILE_BURN_TICKS));

            double dx = impact.x - victim.getX();
            double dz = impact.z - victim.getZ();
            victim.knockback(1.1D, dx, dz);
        }

        this.activeMagmaHazards.add(
                new MagmaHazardZone(impact, MAGMA_HAZARD_ZONE_TICKS));
    }

    private void tickMagmaHazards(ServerLevel level) {
        for (int i = this.activeMagmaHazards.size() - 1; i >= 0; --i) {
            MagmaHazardZone zone = this.activeMagmaHazards.get(i);
            --zone.ticks;

            if (zone.ticks % MAGMA_HAZARD_PULSE_INTERVAL == 0) {
                WolfVfx.sendParticles("magma_wolf", level,
                        ParticleTypes.FLAME,
                        zone.center.x,
                        zone.center.y + 0.18D,
                        zone.center.z,
                        8,
                        1.25D, 0.12D, 1.25D,
                        0.025D);

                WolfVfx.sendParticles("magma_wolf", level,
                        ParticleTypes.LAVA,
                        zone.center.x,
                        zone.center.y + 0.12D,
                        zone.center.z,
                        4,
                        1.0D, 0.08D, 1.0D,
                        0.012D);

                DamageSource physical = this.damageSources().mobAttack(this);
                AABB area = new AABB(
                        zone.center.x - MAGMA_PROJECTILE_RADIUS,
                        zone.center.y - 0.75D,
                        zone.center.z - MAGMA_PROJECTILE_RADIUS,
                        zone.center.x + MAGMA_PROJECTILE_RADIUS,
                        zone.center.y + 2.25D,
                        zone.center.z + MAGMA_PROJECTILE_RADIUS);

                for (LivingEntity victim : level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        this::isValidMagmaHazardVictim)) {

                    if (victim.hurtServer(level, physical, MAGMA_HAZARD_PULSE_DAMAGE)) {
                        victim.setRemainingFireTicks(Math.max(
                                victim.getRemainingFireTicks(),
                                20 * 2));
                    }
                }
            }

            if (zone.ticks <= 0) {
                this.activeMagmaHazards.remove(i);
            }
        }
    }

    // -----------------------------------------------------------------
    // Pack / family intelligence
    // -----------------------------------------------------------------

    public boolean isMagmaWolfPackmate(MagmaWolf other) {
        if (other == null
                || other == this
                || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) return true;

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isMagmaFamilyMember(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            return this.isWolfismFamily(entity);
        }

        return entity instanceof MagmaWolf magma && this.isMagmaWolfPackmate(magma);
    }

    public boolean isValidMagmaThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || target instanceof Creeper
                || this.isAlliedTo(target)
                || this.isMagmaFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public boolean isValidMagmaHazardVictim(LivingEntity target) {
        if (!this.isValidMagmaThreat(target)) return false;
        return target == this.getTarget()
                || target instanceof Enemy
                || isTargetingMagmaFamily(target);
    }

    public double scoreMagmaThreat(LivingEntity target) {
        if (target == null || !target.isAlive()) return Double.NEGATIVE_INFINITY;

        double score = target.getMaxHealth() * 0.55D;
        score -= Math.sqrt(this.distanceToSqr(target)) * 1.2D;

        if (isTargetingMagmaFamily(target)) score += 120.0D;
        if (target == this.getFamilyDefenseTarget()) score += 180.0D;
        if (!target.onGround()) score += 12.0D;

        return score;
    }

    public boolean isTargetingMagmaFamily(LivingEntity target) {
        if (!(target instanceof Mob mob)) return false;
        LivingEntity mobTarget = mob.getTarget();
        return mobTarget != null && this.isMagmaFamilyMember(mobTarget);
    }

    public void alertMagmaPack(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidMagmaThreat(threat)) {
            return;
        }

        for (MagmaWolf mate : level.getEntitiesOfClass(
                MagmaWolf.class,
                this.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate == this || this.isMagmaWolfPackmate(candidate))) {

            mate.getBrain().setMemory(ModMemoryModuleTypes.MAGMA_PACK_THREAT.get(), threat);

            if (!mate.isBaby()
                    && mate.distanceToSqr(threat) <= 12.0D * 12.0D
                    && mate.isValidMagmaThreat(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive() && !(attacker instanceof Creeper)) {
            this.alertMagmaPack(attacker);
        }
    }

    // -----------------------------------------------------------------
    // Neutral natural spawning — Nether volcanic habitat
    // -----------------------------------------------------------------

    /**
     * Wild Magma Wolves are neutral volcanic predators. The biome modifier
     * limits them to Basalt Deltas + Nether Wastes; this placement method
     * handles physical safety and local wild-Magma separation.
     */
    public static boolean checkMagmaWolfSpawnRules(
            EntityType<MagmaWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        // Spawn eggs, commands and other explicit creation should never be
        // blocked by natural-spawn restrictions.
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.NETHER) {
            return false;
        }

        // Spawn on a real solid Nether floor, not inside lava/fire or a wall.
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos)
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

        // Solitary heavyweight territory. Tamed Magmas never suppress wild
        // spawning, but two wild adults should not stack in the same encounter.
        boolean anotherWildMagma = !serverLevel.getEntitiesOfClass(
                        MagmaWolf.class,
                        new AABB(pos).inflate(48.0D, 24.0D, 48.0D),
                        wolf -> wolf.isAlive() && !wolf.isTame())
                .isEmpty();

        return !anotherWildMagma;
    }

    // -----------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("MagmaEruptionCooldown", this.eruptionCooldownTicks);
        output.putInt("MagmaChargeCooldown", this.chargeCooldownTicks);
        output.putInt("MagmaShowerCooldown", this.magmaShowerCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.eruptionCooldownTicks = Math.max(0, input.getIntOr("MagmaEruptionCooldown", 0));
        this.chargeCooldownTicks = Math.max(0, input.getIntOr("MagmaChargeCooldown", 0));
        this.magmaShowerCooldownTicks = Math.max(0, input.getIntOr("MagmaShowerCooldown", 0));

        // Moving/airborne states deliberately restart cleanly after reload.
        this.endMagmaCharge();
        this.magmaShowerTicks = 0;
        this.magmaShowerProjectilesScheduled = 0;
        this.pendingMagmaProjectiles.clear();
        this.activeMagmaHazards.clear();
        this.clearObsidianEscort();
    }

    private static final class MagmaProjectileMarker {
        private final Vec3 start;
        private final Vec3 impact;
        private int ticks;

        private MagmaProjectileMarker(Vec3 start, Vec3 impact, int ticks) {
            this.start = start;
            this.impact = impact;
            this.ticks = ticks;
        }
    }

    private static final class MagmaHazardZone {
        private final Vec3 center;
        private int ticks;

        private MagmaHazardZone(Vec3 center, int ticks) {
            this.center = center;
            this.ticks = ticks;
        }
    }
}
