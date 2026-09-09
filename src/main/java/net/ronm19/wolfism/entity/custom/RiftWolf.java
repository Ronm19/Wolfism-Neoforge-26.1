package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;

/**
 * Rift Wolf #35 — "The Spatial Wanderer".
 *
 * <p>Identity: tactical rescue / displacement / spatial control. Rift is not an
 * End Wolf clone: End uses teleportation to improve its own combat positioning,
 * while Rift rewrites positions for the whole battlefield.</p>
 *
 * <p>Core kit:</p>
 * <ul>
 *     <li><b>Rift Step</b> — short tactical self-reposition through paired rifts.</li>
 *     <li><b>Emergency Rescue</b> — extracts critically endangered family to a safe landing.</li>
 *     <li><b>Banish / Displacement</b> — ejects a dangerous enemy away from protected family.</li>
 *     <li><b>Rift Passage</b> — temporary two-way walk-through gateway for owner/family.</li>
 *     <li><b>Rift Field</b> — static spatial-control zone that randomly shifts enemies.</li>
 * </ul>
 *
 * <p>No Rift ability deals bonus teleport damage and no ability places blocks.</p>
 */
public final class RiftWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Baseline balance
    // ---------------------------------------------------------------------

    private static final double RIFT_MAX_HEALTH = 32.0D;
    private static final double RIFT_ATTACK_DAMAGE = 5.5D;
    private static final double RIFT_MOVE_SPEED = 0.31D;

    // Rift Step
    public static final int RIFT_STEP_COOLDOWN_TICKS = 20 * 8;
    private static final double RIFT_STEP_MIN_RANGE = 5.5D;
    private static final double RIFT_STEP_MAX_RANGE = 14.0D;

    // Emergency Rescue
    public static final int EMERGENCY_RESCUE_COOLDOWN_TICKS = 20 * 22;
    public static final double EMERGENCY_RESCUE_RADIUS = 22.0D;
    private static final double EMERGENCY_RESCUE_VERTICAL_RANGE = 32.0D;
    private static final float EMERGENCY_HEALTH_RATIO = 0.40F;
    private static final float EMERGENCY_FALL_DISTANCE = 4.0F;
    private static final double RESCUE_MIN_HAZARD_ESCAPE_DISTANCE = 5.0D;
    private static final int RESCUE_HAZARD_CLEARANCE_RADIUS = 3;

    // Banish / Displacement
    public static final int BANISH_COOLDOWN_TICKS = 20 * 18;
    public static final double BANISH_TRIGGER_RADIUS = 7.5D;
    private static final double BANISH_MIN_DISTANCE = 8.0D;
    private static final double BANISH_MAX_DISTANCE = 12.0D;

    // Rift Passage
    public static final int RIFT_PASSAGE_DURATION_TICKS = 20 * 7;
    public static final int RIFT_PASSAGE_COOLDOWN_TICKS = 20 * 40;
    private static final double RIFT_PASSAGE_MIN_RANGE = 14.0D;
    private static final double RIFT_PASSAGE_MAX_RANGE = 40.0D;

    private static boolean isInsideRiftPassageRange(double distanceSqr) {
        return distanceSqr
                >= RIFT_PASSAGE_MIN_RANGE
                * RIFT_PASSAGE_MIN_RANGE
                && distanceSqr
                <= RIFT_PASSAGE_MAX_RANGE
                * RIFT_PASSAGE_MAX_RANGE;
    }

    // Rift Return / owner catch-up fallback
    public static final int OWNER_RIFT_RETURN_COOLDOWN_TICKS = 20 * 5;
    private static final double OWNER_RIFT_RETURN_HARD_DISTANCE = 40.0D;
    private static final double OWNER_RIFT_RETURN_STUCK_MIN_DISTANCE = 6.0D;
    private static final int OWNER_RIFT_RETURN_STUCK_TICKS = 20 * 3;

    // Rift Field
    public static final double RIFT_FIELD_RADIUS = 8.0D;
    public static final int RIFT_FIELD_DURATION_TICKS = 20 * 10;
    public static final int RIFT_FIELD_COOLDOWN_TICKS = 20 * 45;
    private static final int RIFT_FIELD_PULSE_INTERVAL = 20;
    private static final int RIFT_FIELD_MAX_SHIFTS_PER_PULSE = 2;

    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int SAFE_VERTICAL_SEARCH = 4;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int riftStepCooldownTicks;
    private int emergencyRescueCooldownTicks;
    private int banishCooldownTicks;
    private int riftPassageCooldownTicks;
    private int riftFieldCooldownTicks;
    private int riftFieldTicks;
    private int activeRiftPassageTicks;
    private int ownerRiftReturnCooldownTicks;
    private int abilityLockoutTicks;

    private Vec3 riftFieldCenter;

    /**
     * First pass intentionally uses built-in perception sensors. That keeps Rift
     * focused on proving the actual spatial mechanics before adding unnecessary
     * registry plumbing.
     */

    private static final class BrainHolder {
        private static final Brain.Provider<RiftWolf> PROVIDER = Brain.<RiftWolf>provider(
                ImmutableList.of(
                        SensorType.HURT_BY,
                        SensorType.NEAREST_LIVING_ENTITIES),
                wolf -> List.of());
    }

    public RiftWolf(EntityType<? extends RiftWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(
                goal -> goal instanceof FollowOwnerGoal);

        this.goalSelector.addGoal(
                6,
                new RiftFollowOwnerGoal(this));
    }

    // ---------------------------------------------------------------------
    // Foundation
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, RIFT_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, RIFT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, RIFT_MOVE_SPEED)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.08D);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.RIFT_WOLF.get();
    }

    @Override
    protected Brain<RiftWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<RiftWolf> getBrain() {
        return (Brain<RiftWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(RIFT_MAX_HEALTH);
        }

        if (this.isTame()) {
            this.setHealth((float) RIFT_MAX_HEALTH);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isRiftFamilyMember(target)
                && super.canAttack(target);
    }

    /**
     * Rift is a spatial traveler; ordinary falling should never be what kills
     * her. Environmental hazards such as lava still matter normally.
     */
    @Override
    public boolean causeFallDamage(
            double fallDistance,
            float damageMultiplier,
            DamageSource damageSource) {

        this.fallDistance = 0.0F;
        return false;
    }

    /**
     * Personal owner catch-up fallback.
     *
     * <p>Passage owns normal medium-range spatial travel. This direct return is
     * reserved for two cases:</p>
     *
     * <ul>
     *     <li>owner is beyond Passage's ~40 block operating range;</li>
     *     <li>Rift has made no meaningful navigation progress for several
     *         seconds and Passage is unavailable for that situation.</li>
     * </ul>
     */
    private boolean tryOwnerRiftReturn(
            ServerLevel level,
            LivingEntity owner,
            boolean stuckFallback) {

        if (this.ownerRiftReturnCooldownTicks > 0
                || this.activeRiftPassageTicks > 0
                || this.isBaby()
                || !this.isTame()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.getTarget() != null
                || this.riftFieldTicks > 0
                || owner == null
                || !owner.isAlive()
                || owner.level() != level) {
            return false;
        }

        double distanceSqr =
                this.distanceToSqr(owner);

        /*
         * HARD SEPARATION:
         * Rift Passage owns its entire 14-40 block operating band.
         * Recall is never allowed to fire inside that band — even when Passage
         * itself is cooling down. This prevents Recall from stealing Passage's
         * identity or collapsing the two mechanics into one auto-teleport.
         */
        if (isInsideRiftPassageRange(distanceSqr)) {
            return false;
        }

        boolean beyondPassage =
                distanceSqr
                        > OWNER_RIFT_RETURN_HARD_DISTANCE
                        * OWNER_RIFT_RETURN_HARD_DISTANCE;

        if (!beyondPassage && !stuckFallback) {
            return false;
        }

        if (stuckFallback
                && distanceSqr
                < OWNER_RIFT_RETURN_STUCK_MIN_DISTANCE
                * OWNER_RIFT_RETURN_STUCK_MIN_DISTANCE) {
            return false;
        }

        Vec3 ownerLook =
                owner.getLookAngle();

        Vec3 flatLook =
                new Vec3(
                        ownerLook.x,
                        0.0D,
                        ownerLook.z);

        double preferredAngle =
                flatLook.lengthSqr() > 1.0E-5D
                        ? Math.atan2(
                        -flatLook.z,
                        -flatLook.x)
                        : Math.atan2(
                        this.getZ() - owner.getZ(),
                        this.getX() - owner.getX());

        Vec3 safe =
                this.findSafePositionAround(
                        level,
                        owner,
                        this,
                        2.8D,
                        preferredAngle,
                        owner);

        if (safe == null) {
            return false;
        }

        this.performRiftTransfer(
                level,
                this,
                safe,
                RiftPortalEntity.Mode.RECALL,
                owner);

        this.ownerRiftReturnCooldownTicks =
                OWNER_RIFT_RETURN_COOLDOWN_TICKS;

        this.abilityLockoutTicks =
                Math.max(
                        this.abilityLockoutTicks,
                        14);

        return true;
    }

    // ---------------------------------------------------------------------
    // Server loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickRiftTimers();

        if (this.riftFieldTicks > 0) {
            this.tickRiftField(level);
        }

        if (this.isBaby()) {
            this.clearAdultRiftState();
            return;
        }

        this.applyBrainRetaliationMemory();

        if (this.abilityLockoutTicks > 0
                || Math.floorMod(this.tickCount + this.getId(), ABILITY_DECISION_INTERVAL) != 0) {
            return;
        }

        /*
         * Emergency Rescue is allowed even while sitting. It is an emergency
         * protection reflex and does not physically move Rift herself.
         */
        if (this.tryEmergencyRescue(level)) {
            return;
        }

        if (this.isOrderedToSit()) {
            return;
        }

        /*
         * Priority:
         * 1) battlefield-wide crowd displacement
         * 2) urgent close-range banish
         * 3) non-combat owner/family utility passage
         * 4) self tactical step
         */
        if (this.riftFieldTicks <= 0 && this.tryStartRiftField(level)) {
            return;
        }

        if (this.tryBanishThreat(level)) {
            return;
        }

        /*
         * Owner-distance Passage is NOT decided here anymore.
         * RiftFollowOwnerGoal owns Passage/Recall routing so there is one
         * authoritative subsystem for owner travel.
         */
        this.tryRiftStep(level);
    }

    private void tickRiftTimers() {
        if (this.riftStepCooldownTicks > 0) {
            --this.riftStepCooldownTicks;
        }
        if (this.emergencyRescueCooldownTicks > 0) {
            --this.emergencyRescueCooldownTicks;
        }
        if (this.banishCooldownTicks > 0) {
            --this.banishCooldownTicks;
        }
        if (this.riftPassageCooldownTicks > 0) {
            --this.riftPassageCooldownTicks;
        }
        if (this.riftFieldCooldownTicks > 0) {
            --this.riftFieldCooldownTicks;
        }
        if (this.activeRiftPassageTicks > 0) {
            --this.activeRiftPassageTicks;
        }
        if (this.ownerRiftReturnCooldownTicks > 0) {
            --this.ownerRiftReturnCooldownTicks;
        }
        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }
    }

    private void clearAdultRiftState() {
        this.setTarget(null);
        this.riftFieldTicks = 0;
        this.riftFieldCenter = null;
        this.activeRiftPassageTicks = 0;
    }

    private void applyBrainRetaliationMemory() {
        LivingEntity attacker = this.getBrain()
                .getMemory(MemoryModuleType.HURT_BY_ENTITY)
                .orElse(null);

        if (this.isRelevantRiftThreat(attacker)
                && (this.getTarget() == null || !this.getTarget().isAlive())) {
            this.setTarget(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Family / threat rules
    // ---------------------------------------------------------------------

    /**
     * Broad Wolfism family safety: once tamed, Rift protects her owner and all
     * tamed wolves. This matches the family behavior already used by Grave.
     */
    public boolean isRiftFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && entity == owner) {
            return true;
        }

        return entity instanceof Wolf wolf && wolf.isTame();
    }

    private boolean isTargetingRiftFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        return target != null && this.isRiftFamilyMember(target);
    }

    private boolean isRelevantRiftThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || this.isRiftFamilyMember(candidate)
                || !this.canAttack(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || this.isTargetingRiftFamily(candidate);
    }

    private List<LivingEntity> findRiftThreats(
            ServerLevel level,
            Vec3 center,
            double radius) {

        AABB area = new AABB(
                center.x - radius,
                center.y - 4.0D,
                center.z - radius,
                center.x + radius,
                center.y + 5.0D,
                center.z + radius);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> this.isRelevantRiftThreat(candidate)
                        && candidate.position().distanceToSqr(center)
                        <= radius * radius);
    }

    // ---------------------------------------------------------------------
    // 1) Rift Step
    // ---------------------------------------------------------------------

    private boolean tryRiftStep(ServerLevel level) {
        if (this.riftStepCooldownTicks > 0) {
            return false;
        }

        LivingEntity target = this.getTarget();
        if (!this.isRelevantRiftThreat(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < RIFT_STEP_MIN_RANGE * RIFT_STEP_MIN_RANGE
                || distanceSqr > RIFT_STEP_MAX_RANGE * RIFT_STEP_MAX_RANGE) {
            return false;
        }

        boolean blockedSight = !this.getSensing().hasLineOfSight(target);
        boolean usefulGap = distanceSqr >= 8.0D * 8.0D;
        boolean awkwardHeight = Math.abs(target.getY() - this.getY()) > 1.5D;

        if (!blockedSight && !usefulGap && !awkwardHeight) {
            return false;
        }

        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        double preferredAngle = flat.lengthSqr() > 1.0E-5D
                ? Math.atan2(-flat.z, -flat.x)
                : Math.atan2(
                this.getZ() - target.getZ(),
                this.getX() - target.getX());

        Vec3 safe = this.findSafePositionAround(
                level,
                target,
                this,
                2.4D,
                preferredAngle,
                target);

        if (safe == null) {
            return false;
        }

        this.performRiftTransfer(
                level,
                this,
                safe,
                RiftPortalEntity.Mode.STEP,
                target);

        this.riftStepCooldownTicks = RIFT_STEP_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 12;
        return true;
    }

    // ---------------------------------------------------------------------
    // 2) Emergency Rescue
    // ---------------------------------------------------------------------

    private boolean tryEmergencyRescue(ServerLevel level) {
        if (this.emergencyRescueCooldownTicks > 0
                || !this.isTame()) {
            return false;
        }

        LivingEntity family = this.findEmergencyFamily(level);
        if (family == null) {
            return false;
        }

        LivingEntity nearestThreat = this.findNearestThreatTo(
                level,
                family,
                10.0D);

        /*
         * V2 does NOT merely find an empty block near Rift. Rescue destinations
         * are intentionally several blocks away from the emergency, require a
         * broader floor, and reject lava/fire/magma/cactus/powder-snow danger
         * around the landing itself.
         */
        Vec3 safe = this.findSafeEmergencyRescuePosition(
                level,
                family,
                nearestThreat);

        if (safe == null) {
            return false;
        }

        this.performRiftTransfer(
                level,
                family,
                safe,
                RiftPortalEntity.Mode.RESCUE,
                this);

        family.fallDistance = 0.0F;

        this.emergencyRescueCooldownTicks =
                EMERGENCY_RESCUE_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 12;

        return true;
    }

    private LivingEntity findEmergencyFamily(ServerLevel level) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(
                                EMERGENCY_RESCUE_RADIUS,
                                EMERGENCY_RESCUE_VERTICAL_RANGE,
                                EMERGENCY_RESCUE_RADIUS),
                        member -> member != this
                                && this.isRiftFamilyMember(member)
                                && this.isEmergencyFamilyState(member))
                .stream()
                .min(Comparator.comparingDouble(
                        this::emergencyPriorityScore))
                .orElse(null);
    }

    private boolean isEmergencyFamilyState(LivingEntity member) {
        if (member.getMaxHealth() <= 0.0F) {
            return false;
        }

        float healthRatio =
                member.getHealth() / member.getMaxHealth();

        boolean criticalHealth =
                healthRatio <= EMERGENCY_HEALTH_RATIO;

        boolean dangerousFall =
                !member.onGround()
                        && member.fallDistance
                        >= EMERGENCY_FALL_DISTANCE;

        boolean lavaEmergency =
                member.isInLava();

        if (lavaEmergency || dangerousFall) {
            return true;
        }

        if (!criticalHealth) {
            return false;
        }

        /*
         * Health rescue is still contextual so Rift does not teleport every
         * injured family member during peaceful downtime. V2 expands the threat
         * scan, making real combat rescue much less timing-sensitive.
         */
        return this.level() instanceof ServerLevel level
                && this.findNearestThreatTo(
                level,
                member,
                10.0D) != null;
    }

    private double emergencyPriorityScore(LivingEntity member) {
        double healthRatio = member.getHealth()
                / Math.max(1.0F, member.getMaxHealth());

        double fallUrgency = Math.min(
                1.0D,
                member.fallDistance / 12.0D);

        double lavaUrgency =
                member.isInLava()
                        ? 0.60D
                        : 0.0D;

        /*
         * If owner and another family wolf are simultaneously in danger, the
         * bonded owner's emergency wins close ties. A catastrophically worse
         * family member can still outrank them naturally.
         */
        double ownerPriority =
                member == this.getOwner()
                        ? 0.20D
                        : 0.0D;

        return healthRatio
                - fallUrgency * 0.45D
                - lavaUrgency
                - ownerPriority;
    }

    private LivingEntity findNearestThreatTo(
            ServerLevel level,
            LivingEntity family,
            double radius) {

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        family.getBoundingBox().inflate(radius),
                        this::isRelevantRiftThreat)
                .stream()
                .min(Comparator.comparingDouble(
                        family::distanceToSqr))
                .orElse(null);
    }

    /**
     * Dedicated emergency landing search.
     *
     * <p>The important difference from ordinary Rift Step is distance from the
     * danger. A fall/lava rescue should look like an extraction, not a one-block
     * sidestep onto the lip of the same hazard.</p>
     */
    private Vec3 findSafeEmergencyRescuePosition(
            ServerLevel level,
            LivingEntity family,
            LivingEntity nearestThreat) {

        double preferredAngle;

        if (nearestThreat != null) {
            preferredAngle = Math.atan2(
                    this.getZ() - nearestThreat.getZ(),
                    this.getX() - nearestThreat.getX());
        } else {
            Vec3 awayFromEmergency =
                    this.position()
                            .subtract(family.position())
                            .multiply(1.0D, 0.0D, 1.0D);

            if (awayFromEmergency.lengthSqr() < 1.0E-5D) {
                awayFromEmergency = this.getLookAngle()
                        .multiply(-1.0D, 0.0D, -1.0D);
            }

            preferredAngle = Math.atan2(
                    awayFromEmergency.z,
                    awayFromEmergency.x);
        }

        double[] radii = {
                5.0D,
                6.5D,
                8.0D
        };

        double[] angleOffsets = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        boolean severeEnvironment =
                family.isInLava()
                        || (!family.onGround()
                        && family.fallDistance
                        >= EMERGENCY_FALL_DISTANCE);

        /*
         * V3: falls and lava are WORLD-geometry emergencies, so solve them with
         * a surface search first. This finds actual terrain/shore using the
         * heightmap instead of assuming Rift's current Y is close to the ground.
         */
        if (severeEnvironment) {
            Vec3 surfaceSafe =
                    this.findSafeEnvironmentalRescueSurface(
                            level,
                            family,
                            preferredAngle);

            if (surfaceSafe != null) {
                return surfaceSafe;
            }
        }

        for (double radius : radii) {
            for (double offset : angleOffsets) {
                double angle =
                        preferredAngle + offset;

                double x = this.getX()
                        + Math.cos(angle) * radius;

                double z = this.getZ()
                        + Math.sin(angle) * radius;

                /*
                 * Reference Rift's ground height rather than the falling
                 * entity's current Y. A falling owner may be dozens of blocks
                 * above the actual safe floor.
                 */
                Vec3 safe = this.findSafeLandingNear(
                        level,
                        family,
                        x,
                        this.getY(),
                        z,
                        this);

                if (safe == null) {
                    continue;
                }

                BlockPos safeFeet =
                        BlockPos.containing(safe);

                if (this.hasNearbyRescueHazard(
                        level,
                        safeFeet,
                        RESCUE_HAZARD_CLEARANCE_RADIUS)) {
                    continue;
                }

                if (!this.hasBroadRescueFloor(
                        level,
                        safeFeet)) {
                    continue;
                }

                if (severeEnvironment) {
                    double dx =
                            safe.x - family.getX();

                    double dz =
                            safe.z - family.getZ();

                    if (dx * dx + dz * dz
                            < RESCUE_MIN_HAZARD_ESCAPE_DISTANCE
                            * RESCUE_MIN_HAZARD_ESCAPE_DISTANCE) {
                        continue;
                    }
                }

                return safe;
            }
        }

        return null;
    }

    /**
     * Environmental rescue fallback for FALL / LAVA only.
     *
     * <p>Searches actual surface terrain around both Rift and the endangered
     * family member. This means a player falling from a tower can be sent to
     * real ground, and somebody in lava can be sent to a real shore several
     * blocks away rather than another point in the same hazard.</p>
     */
    private Vec3 findSafeEnvironmentalRescueSurface(
            ServerLevel level,
            LivingEntity family,
            double preferredAngle) {

        Vec3[] centers = {
                this.position(),
                family.position()
        };

        double[] radii = {
                5.0D,
                7.0D,
                9.0D,
                12.0D,
                15.0D,
                18.0D
        };

        double[] angleOffsets = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        for (Vec3 center : centers) {
            for (double radius : radii) {
                for (double offset : angleOffsets) {
                    double angle =
                            preferredAngle + offset;

                    int x = Mth.floor(
                            center.x
                                    + Math.cos(angle)
                                    * radius);

                    int z = Mth.floor(
                            center.z
                                    + Math.sin(angle)
                                    * radius);

                    /*
                     * getHeight returns the feet-level immediately above the
                     * top motion-blocking surface for this X/Z column.
                     */
                    int y = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            x,
                            z);

                    BlockPos feet =
                            new BlockPos(x, y, z);

                    if (!this.isSafeRiftPosition(
                            level,
                            family,
                            feet,
                            this)) {
                        continue;
                    }

                    if (this.hasNearbyRescueHazard(
                            level,
                            feet,
                            RESCUE_HAZARD_CLEARANCE_RADIUS)) {
                        continue;
                    }

                    if (!this.hasBroadRescueFloor(
                            level,
                            feet)) {
                        continue;
                    }

                    Vec3 safe =
                            new Vec3(
                                    feet.getX() + 0.5D,
                                    feet.getY(),
                                    feet.getZ() + 0.5D);

                    double dx =
                            safe.x - family.getX();

                    double dz =
                            safe.z - family.getZ();

                    if (dx * dx + dz * dz
                            < RESCUE_MIN_HAZARD_ESCAPE_DISTANCE
                            * RESCUE_MIN_HAZARD_ESCAPE_DISTANCE) {
                        continue;
                    }

                    return safe;
                }
            }
        }

        return null;
    }

    private boolean hasNearbyRescueHazard(
            ServerLevel level,
            BlockPos feet,
            int radius) {

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    BlockPos check =
                            feet.offset(dx, dy, dz);

                    if (level.getFluidState(check)
                            .is(FluidTags.LAVA)) {
                        return true;
                    }

                    var state =
                            level.getBlockState(check);

                    if (state.is(Blocks.FIRE)
                            || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.CACTUS)
                            || state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.POWDER_SNOW)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Rescue gets a stricter floor than ordinary combat teleporting. At least
     * five cells of the 3x3 floor under/around the destination must be solid and
     * dry, preventing "safe" rescue onto a tiny cliff lip or lone pillar.
     */
    private boolean hasBroadRescueFloor(
            ServerLevel level,
            BlockPos feet) {

        BlockPos floor = feet.below();
        int safeFloorCells = 0;

        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                BlockPos check =
                        floor.offset(dx, 0, dz);

                if (level.getFluidState(check).isEmpty()
                        && !level.getBlockState(check)
                        .getCollisionShape(level, check)
                        .isEmpty()) {
                    ++safeFloorCells;
                }
            }
        }

        return safeFloorCells >= 5;
    }

    // ---------------------------------------------------------------------
    // 3) Banish / Displacement
    // ---------------------------------------------------------------------

    private boolean tryBanishThreat(ServerLevel level) {
        if (this.banishCooldownTicks > 0) {
            return false;
        }

        LivingEntity threat = this.findBanishThreat(level);
        if (threat == null) {
            return false;
        }

        LivingEntity protectedMember =
                this.getThreatenedFamilyMember(threat);

        Vec3 protectedCenter = protectedMember != null
                ? protectedMember.position()
                : this.position();

        Vec3 away = threat.position()
                .subtract(protectedCenter)
                .multiply(1.0D, 0.0D, 1.0D);

        if (away.lengthSqr() < 1.0E-5D) {
            away = this.getLookAngle()
                    .multiply(-1.0D, 0.0D, -1.0D);
        }

        if (away.lengthSqr() < 1.0E-5D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        away = away.normalize();

        Vec3 destination = this.findSafeBanishLanding(
                level,
                threat,
                protectedCenter,
                away);

        if (destination == null) {
            return false;
        }

        this.performRiftTransfer(
                level,
                threat,
                destination,
                RiftPortalEntity.Mode.BANISH,
                protectedMember != null
                        ? protectedMember
                        : this);

        this.banishCooldownTicks = BANISH_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 14;
        return true;
    }

    private LivingEntity findBanishThreat(ServerLevel level) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(
                                BANISH_TRIGGER_RADIUS),
                        candidate -> this.isRelevantRiftThreat(candidate)
                                && (this.isTargetingRiftFamily(candidate)
                                || candidate == this.getTarget()))
                .stream()
                .min(Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    private LivingEntity getThreatenedFamilyMember(
            LivingEntity threat) {

        if (!(threat instanceof Mob mob)) {
            return null;
        }

        LivingEntity target = mob.getTarget();
        return target != null
                && this.isRiftFamilyMember(target)
                ? target
                : null;
    }

    private Vec3 findSafeBanishLanding(
            ServerLevel level,
            LivingEntity threat,
            Vec3 protectedCenter,
            Vec3 away) {

        double baseAngle =
                Math.atan2(away.z, away.x);

        double[] angleOffsets = {
                0.0D,
                Math.PI / 8.0D,
                -Math.PI / 8.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D
        };

        for (double offset : angleOffsets) {
            double distance = Mth.lerp(
                    this.random.nextDouble(),
                    BANISH_MIN_DISTANCE,
                    BANISH_MAX_DISTANCE);

            double angle = baseAngle + offset;
            double x = protectedCenter.x
                    + Math.cos(angle) * distance;
            double z = protectedCenter.z
                    + Math.sin(angle) * distance;

            Vec3 safe = this.findSafeLandingNear(
                    level,
                    threat,
                    x,
                    threat.getY(),
                    z,
                    null);

            if (safe != null) {
                return safe;
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------
    // 4) Rift Passage
    // ---------------------------------------------------------------------

    /**
     * Opens a real temporary two-way portal pair. The RiftPortalEntity handles
     * walk-through detection and only transports Rift family members.
     *
     * <p>This first pass auto-opens Passage as a regroup tool when the owner is
     * meaningfully separated from Rift. A future Phantom-Staff-style command
     * system can call this method explicitly without rewriting the portal core.</p>
     */
    public boolean tryOpenPassageToOwner(ServerLevel level) {
        if (this.riftPassageCooldownTicks > 0
                || this.activeRiftPassageTicks > 0
                || !this.isTame()
                || this.getTarget() != null
                || this.riftFieldTicks > 0) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.level() != level) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(owner);
        if (!isInsideRiftPassageRange(distanceSqr)) {
            return false;
        }

        boolean blockedSight =
                !this.getSensing().hasLineOfSight(owner);

        /*
         * V2 deliberately makes Passage deterministic enough to test:
         * once the owner is >=18 blocks away, separation itself is sufficient.
         * Between 14-18 blocks, blocked sight is required.
         */
        boolean genuinelySeparated =
                distanceSqr >= 18.0D * 18.0D;

        if (!blockedSight && !genuinelySeparated) {
            return false;
        }

        double towardOwner = Math.atan2(
                owner.getZ() - this.getZ(),
                owner.getX() - this.getX());

        Vec3 entrance = this.findSafePositionAround(
                level,
                this,
                this,
                2.2D,
                towardOwner,
                this);

        Vec3 exit = this.findSafePassageExitAround(
                level,
                owner,
                towardOwner + Math.PI);

        if (entrance == null || exit == null) {
            return false;
        }

        /*
         * Freeze ordinary following while the doorway exists. Rift should stand
         * by her own entrance instead of running toward the owner and confusing
         * the test/use of the portal.
         */
        this.getNavigation().stop();

        RiftPortalEntity.spawnPair(
                level,
                this,
                entrance,
                exit,
                RiftPortalEntity.Mode.PASSAGE,
                RIFT_PASSAGE_DURATION_TICKS);

        this.activeRiftPassageTicks =
                RIFT_PASSAGE_DURATION_TICKS;

        this.riftPassageCooldownTicks =
                RIFT_PASSAGE_COOLDOWN_TICKS;

        this.abilityLockoutTicks = 20;

        this.playSound(
                net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL,
                0.90F,
                1.18F);

        return true;
    }

    public boolean hasActiveRiftPassage() {
        return this.activeRiftPassageTicks > 0;
    }

    /**
     * Passage EXIT placement is intentionally stricter than ordinary combat /
     * cinematic rifts.
     *
     * <p>The exit must not merely have empty feet/head blocks; the owner's full
     * moved bounding box, plus a small clearance margin, must fit without
     * touching fences, walls, gates or neighboring block collision shapes.</p>
     */
    private Vec3 findSafePassageExitAround(
            ServerLevel level,
            LivingEntity owner,
            double preferredAngle) {

        double[] radii = {
                2.2D,
                3.2D,
                4.2D,
                5.2D
        };

        double[] angleOffsets = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        for (double radius : radii) {
            for (double offset : angleOffsets) {
                double angle =
                        preferredAngle + offset;

                double x =
                        owner.getX()
                                + Math.cos(angle) * radius;

                double z =
                        owner.getZ()
                                + Math.sin(angle) * radius;

                Vec3 safe =
                        this.findSafeLandingNear(
                                level,
                                owner,
                                x,
                                owner.getY(),
                                z,
                                owner);

                if (safe == null) {
                    continue;
                }

                if (this.hasPassageExitClearance(
                        level,
                        owner,
                        safe)) {
                    return safe;
                }
            }
        }

        return null;
    }

    /**
     * Uses the actual entity bounding box at the destination, not just block
     * occupancy. This is what prevents exits from clipping the player into
     * fence posts, walls and other partial collision shapes.
     */
    private boolean hasPassageExitClearance(
            ServerLevel level,
            LivingEntity owner,
            Vec3 destination) {

        double dx =
                destination.x - owner.getX();
        double dy =
                destination.y - owner.getY();
        double dz =
                destination.z - owner.getZ();

        AABB movedBox =
                owner.getBoundingBox()
                        .move(dx, dy, dz);

        /*
         * Small horizontal breathing room around the actual player box.
         * Enough to reject a fence/wall shoulder clip without requiring a huge
         * empty plaza around every portal.
         */
        AABB clearanceBox =
                movedBox.inflate(
                        0.18D,
                        0.05D,
                        0.18D);

        if (!level.noCollision(
                owner,
                clearanceBox)) {
            return false;
        }

        /*
         * Also keep the exit's immediate cardinal neighbors clear at body
         * height. Partial-height geometry (especially fences/walls) can be
         * technically outside the destination block while still intersecting
         * the player as they emerge.
         */
        BlockPos feet =
                BlockPos.containing(destination);

        BlockPos[] neighbors = {
                feet.north(),
                feet.south(),
                feet.east(),
                feet.west()
        };

        for (BlockPos neighbor : neighbors) {
            if (!level.getBlockState(neighbor)
                    .getCollisionShape(level, neighbor)
                    .isEmpty()) {

                AABB blockBox =
                        new AABB(neighbor);

                if (clearanceBox.intersects(blockBox)) {
                    return false;
                }
            }

            BlockPos upper =
                    neighbor.above();

            if (!level.getBlockState(upper)
                    .getCollisionShape(level, upper)
                    .isEmpty()) {

                AABB blockBox =
                        new AABB(upper);

                if (clearanceBox.intersects(blockBox)) {
                    return false;
                }
            }
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // 5) Rift Field
    // ---------------------------------------------------------------------

    private boolean tryStartRiftField(ServerLevel level) {
        if (this.riftFieldCooldownTicks > 0
                || this.riftFieldTicks > 0) {
            return false;
        }

        List<LivingEntity> threats =
                this.findRiftThreats(
                        level,
                        this.position(),
                        RIFT_FIELD_RADIUS);

        boolean familyEmergency = threats.size() >= 2
                && threats.stream()
                .anyMatch(this::isTargetingRiftFamily);

        if (threats.size() < 3 && !familyEmergency) {
            return false;
        }

        this.riftFieldCenter = this.position();
        this.riftFieldTicks =
                RIFT_FIELD_DURATION_TICKS;
        this.riftFieldCooldownTicks =
                RIFT_FIELD_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 24;

        this.sendRiftFieldRing(level, true);

        this.playSound(
                net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                0.65F,
                1.35F);

        return true;
    }

    private void tickRiftField(ServerLevel level) {
        if (this.riftFieldCenter == null) {
            this.finishRiftField();
            return;
        }

        --this.riftFieldTicks;

        if (this.tickCount % 5 == 0) {
            this.sendRiftFieldRing(level, false);
        }

        if (this.tickCount
                % RIFT_FIELD_PULSE_INTERVAL == 0) {
            this.pulseRiftField(level);
        }

        if (this.riftFieldTicks <= 0) {
            this.finishRiftField();
        }
    }

    private void pulseRiftField(ServerLevel level) {
        List<LivingEntity> threats =
                this.findRiftThreats(
                        level,
                        this.riftFieldCenter,
                        RIFT_FIELD_RADIUS);

        int shifted = 0;

        for (LivingEntity threat : threats) {
            if (shifted >= RIFT_FIELD_MAX_SHIFTS_PER_PULSE) {
                break;
            }

            /*
             * Each pulse intentionally has uncertainty. The field feels unstable
             * rather than becoming a deterministic teleport conveyor belt.
             */
            if (threat != this.getTarget()
                    && this.random.nextFloat() > 0.60F) {
                continue;
            }

            double angle =
                    this.random.nextDouble()
                            * Math.PI
                            * 2.0D;

            double radius =
                    2.5D
                            + this.random.nextDouble()
                            * 2.5D;

            Vec3 safe = this.findSafePositionAround(
                    level,
                    threat,
                    threat,
                    radius,
                    angle,
                    null);

            if (safe == null
                    || safe.distanceToSqr(
                    this.riftFieldCenter)
                    > (RIFT_FIELD_RADIUS + 1.5D)
                    * (RIFT_FIELD_RADIUS + 1.5D)) {
                continue;
            }

            this.performRiftTransfer(
                    level,
                    threat,
                    safe,
                    RiftPortalEntity.Mode.FIELD,
                    null);

            ++shifted;
        }
    }

    private void sendRiftFieldRing(
            ServerLevel level,
            boolean opening) {

        int points = opening ? 52 : 24;

        for (int i = 0; i < points; ++i) {
            double angle =
                    Math.PI * 2.0D * i / points;

            level.sendParticles(
                    opening
                            ? ParticleTypes.PORTAL
                            : ParticleTypes.REVERSE_PORTAL,
                    this.riftFieldCenter.x
                            + Math.cos(angle)
                            * RIFT_FIELD_RADIUS,
                    this.riftFieldCenter.y + 0.12D,
                    this.riftFieldCenter.z
                            + Math.sin(angle)
                            * RIFT_FIELD_RADIUS,
                    1,
                    0.02D,
                    0.02D,
                    0.02D,
                    0.0D);
        }
    }

    private void finishRiftField() {
        this.riftFieldTicks = 0;
        this.riftFieldCenter = null;
    }

    // ---------------------------------------------------------------------
    // Shared rift transfer / safe landing
    // ---------------------------------------------------------------------

    private void performRiftTransfer(
            ServerLevel level,
            LivingEntity transferred,
            Vec3 destination,
            RiftPortalEntity.Mode mode,
            LivingEntity lookAnchor) {

        Vec3 origin = transferred.position();

        /*
         * Open both visual/transport anchors FIRST, but only after destination
         * validation has already succeeded. Nobody ever enters a rift with no
         * legal exit.
         */
        RiftPortalEntity.spawnPair(
                level,
                this,
                origin,
                destination,
                mode,
                14);

        if (transferred instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        /*
         * IMPORTANT: a ServerPlayer must use the player teleport path so the
         * server sends the authoritative position update to the client.
         * Plain Entity#setPos is sufficient for mobs but can be corrected /
         * ignored visually for a networked player.
         */
        if (transferred instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(
                    destination.x,
                    destination.y,
                    destination.z);
        } else {
            transferred.setPos(
                    destination.x,
                    destination.y,
                    destination.z);
        }

        transferred.setDeltaMovement(Vec3.ZERO);
        transferred.fallDistance = 0.0F;
        transferred.hurtMarked = true;

        if (transferred == this
                && lookAnchor != null) {
            this.getLookControl().setLookAt(
                    lookAnchor,
                    90.0F,
                    90.0F);
        }

        transferred.playSound(
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                0.78F,
                1.18F + this.random.nextFloat() * 0.10F);
    }

    private Vec3 findSafePositionAround(
            ServerLevel level,
            LivingEntity anchor,
            LivingEntity movingEntity,
            double radius,
            double preferredAngle,
            LivingEntity allowedOverlap) {

        double[] angleOffsets = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        for (int ring = 0; ring < 2; ++ring) {
            double currentRadius =
                    radius + ring * 1.15D;

            for (double offset : angleOffsets) {
                double angle =
                        preferredAngle + offset;

                double x = anchor.getX()
                        + Math.cos(angle)
                        * currentRadius;

                double z = anchor.getZ()
                        + Math.sin(angle)
                        * currentRadius;

                Vec3 safe = this.findSafeLandingNear(
                        level,
                        movingEntity,
                        x,
                        anchor.getY(),
                        z,
                        allowedOverlap);

                if (safe != null) {
                    return safe;
                }
            }
        }

        return null;
    }

    private Vec3 findSafeLandingNear(
            ServerLevel level,
            LivingEntity movingEntity,
            double x,
            double referenceY,
            double z,
            LivingEntity allowedOverlap) {

        int startY =
                (int) Math.floor(referenceY)
                        + SAFE_VERTICAL_SEARCH;

        int endY =
                (int) Math.floor(referenceY)
                        - SAFE_VERTICAL_SEARCH;

        for (int y = startY; y >= endY; --y) {
            BlockPos pos =
                    BlockPos.containing(x, y, z);

            if (this.isSafeRiftPosition(
                    level,
                    movingEntity,
                    pos,
                    allowedOverlap)) {

                return new Vec3(
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D);
            }
        }

        return null;
    }

    private boolean isSafeRiftPosition(
            ServerLevel level,
            LivingEntity movingEntity,
            BlockPos pos,
            LivingEntity allowedOverlap) {

        if (pos.getY() <= level.getMinY()
                || pos.getY() + 1 >= level.getMaxY()) {
            return false;
        }

        BlockPos floor = pos.below();

        if (!level.getFluidState(floor).isEmpty()
                || level.getBlockState(floor)
                .getCollisionShape(level, floor)
                .isEmpty()) {
            return false;
        }

        int clearBlocks = Math.max(
                2,
                Mth.ceil(movingEntity.getBbHeight()));

        for (int i = 0; i < clearBlocks; ++i) {
            BlockPos check = pos.above(i);

            if (!level.getFluidState(check).isEmpty()
                    || !level.getBlockState(check)
                    .getCollisionShape(level, check)
                    .isEmpty()) {
                return false;
            }
        }

        /*
         * Require neighboring support so Rescue/Step never picks a one-block lip
         * beside a ravine or void.
         */
        int supportedNeighbors = 0;
        int[][] offsets = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] offset : offsets) {
            BlockPos neighbor =
                    floor.offset(
                            offset[0],
                            0,
                            offset[1]);

            if (level.getFluidState(neighbor).isEmpty()
                    && !level.getBlockState(neighbor)
                    .getCollisionShape(level, neighbor)
                    .isEmpty()) {
                ++supportedNeighbors;
            }
        }

        if (supportedNeighbors < 2) {
            return false;
        }

        double halfWidth = Math.max(
                0.30D,
                movingEntity.getBbWidth() * 0.50D);

        AABB landingBox = new AABB(
                pos.getX() + 0.5D - halfWidth,
                pos.getY(),
                pos.getZ() + 0.5D - halfWidth,
                pos.getX() + 0.5D + halfWidth,
                pos.getY()
                        + movingEntity.getBbHeight(),
                pos.getZ() + 0.5D + halfWidth);

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        landingBox,
                        entity -> entity != movingEntity
                                && entity != allowedOverlap
                                && entity.isAlive())
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // Rift-specific owner following
    // ---------------------------------------------------------------------

    /**
     * Ordinary owner following without vanilla's teleport fallback.
     *
     * <p>This keeps Rift useful as a companion while guaranteeing that Rift
     * Passage — not FollowOwnerGoal — owns medium-range spatial catch-up.</p>
     */
    private static final class RiftFollowOwnerGoal extends Goal {

        private final RiftWolf wolf;
        private LivingEntity owner;
        private int repathTicks;
        private int progressSampleTicks;
        private int stuckTicks;
        private double lastSampleDistance = Double.MAX_VALUE;

        private RiftFollowOwnerGoal(RiftWolf wolf) {
            this.wolf = wolf;
            this.setFlags(EnumSet.of(
                    Goal.Flag.MOVE,
                    Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.wolf.isTame()
                    || this.wolf.isOrderedToSit()
                    || this.wolf.isInSittingPose()
                    || this.wolf.hasActiveRiftPassage()
                    || this.wolf.getTarget() != null
                    || this.wolf.isInLove()) {
                return false;
            }

            LivingEntity owner =
                    this.wolf.getOwner();

            if (owner == null
                    || !owner.isAlive()
                    || owner.level()
                    != this.wolf.level()) {
                return false;
            }

            if (this.wolf.distanceToSqr(owner)
                    <= 5.0D * 5.0D) {
                return false;
            }

            this.owner = owner;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null
                    && this.owner.isAlive()
                    && this.owner.level()
                    == this.wolf.level()
                    && !this.wolf.isOrderedToSit()
                    && !this.wolf.isInSittingPose()
                    && !this.wolf.hasActiveRiftPassage()
                    && this.wolf.getTarget() == null
                    && this.wolf.distanceToSqr(this.owner)
                    > 3.0D * 3.0D;
        }

        @Override
        public void start() {
            this.repathTicks = 0;
            this.progressSampleTicks = 0;
            this.stuckTicks = 0;
            this.lastSampleDistance =
                    Math.sqrt(
                            this.wolf.distanceToSqr(
                                    this.owner));
        }

        @Override
        public void stop() {
            this.owner = null;
            this.repathTicks = 0;
            this.progressSampleTicks = 0;
            this.stuckTicks = 0;
            this.lastSampleDistance = Double.MAX_VALUE;
            this.wolf.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }

            this.wolf.getLookControl().setLookAt(
                    this.owner,
                    25.0F,
                    25.0F);

            if (this.wolf.level()
                    instanceof ServerLevel level) {

                double ownerDistanceSqr =
                        this.wolf.distanceToSqr(
                                this.owner);

                /*
                 * PASSAGE FIRST.
                 *
                 * Medium-range owner separation is exclusively Rift Passage
                 * territory. Try the real walk-through gateway before ordinary
                 * navigation or any Recall fallback can act.
                 */
                if (RiftWolf.isInsideRiftPassageRange(
                        ownerDistanceSqr)
                        && this.wolf.tryOpenPassageToOwner(level)) {

                    this.wolf.getNavigation().stop();
                    this.stuckTicks = 0;
                    this.progressSampleTicks = 0;
                    this.lastSampleDistance =
                            Math.sqrt(
                                    this.wolf.distanceToSqr(
                                            this.owner));
                    return;
                }

                /*
                 * RECALL SECOND.
                 * Beyond Passage's hard range, Rift may cut directly back.
                 */
                if (this.wolf.tryOwnerRiftReturn(
                        level,
                        this.owner,
                        false)) {
                    this.stuckTicks = 0;
                    this.lastSampleDistance =
                            Math.sqrt(
                                    this.wolf.distanceToSqr(
                                            this.owner));
                    return;
                }

                if (++this.progressSampleTicks >= 20) {
                    this.progressSampleTicks = 0;

                    double currentDistance =
                            Math.sqrt(
                                    this.wolf.distanceToSqr(
                                            this.owner));

                    /*
                     * Roughly 0.75 blocks of progress per second is enough to
                     * prove navigation is actually working. Fences, sealed rooms,
                     * inaccessible ledges and bad pathing naturally accumulate
                     * the 3-second stuck timer.
                     */
                    if (currentDistance
                            <= this.lastSampleDistance - 0.75D) {
                        this.stuckTicks = 0;
                    } else {
                        this.stuckTicks += 20;
                    }

                    this.lastSampleDistance =
                            currentDistance;

                    if (this.stuckTicks
                            >= OWNER_RIFT_RETURN_STUCK_TICKS) {

                        /*
                         * tryOwnerRiftReturn() itself hard-rejects the Passage
                         * band, so a stuck Rift 14-40 blocks away waits for /
                         * retries Passage instead of silently replacing it.
                         */
                        if (this.wolf.tryOwnerRiftReturn(
                                level,
                                this.owner,
                                true)) {
                            this.stuckTicks = 0;
                            this.lastSampleDistance =
                                    Math.sqrt(
                                            this.wolf.distanceToSqr(
                                                    this.owner));
                            return;
                        }
                    }
                }
            }

            if (--this.repathTicks <= 0) {
                this.repathTicks = 10;

                this.wolf.getNavigation().moveTo(
                        this.owner,
                        1.10D);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    /**
     * Rift Wolf is a rare solitary highland / fractured-terrain encounter.
     *
     * <p>The biome modifier controls WHERE Rift can spawn. This predicate keeps
     * her on exposed highland terrain, accepts the stone/gravel/snow surfaces
     * that actually make up mountain biomes, and prevents persistent wild Rift
     * Wolves from accumulating in one local area.</p>
     *
     * <p>There is deliberately NO day/night restriction. Rift is spatial, not
     * nocturnal.</p>
     */
    public static boolean checkRiftWolfSpawnRules(
            EntityType<RiftWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Do not make spawn eggs, /summon or other explicit/admin spawning
         * awkward. Natural habitat restrictions matter only for world spawning.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        // Highland identity without making Windswept Hills impossibly strict.
        if (pos.getY() < 70) {
            return false;
        }

        // No spawning submerged or with occupied feet/head.
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }

        var ground =
                level.getBlockState(pos.below());

        /*
         * Peaks are often exposed stone/gravel rather than vanilla wolf ground.
         * Keep the habitat practical instead of accidentally sterilizing the
         * exact mountain biomes Rift is supposed to inhabit.
         */
        boolean validGround =
                ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                        || ground.is(BlockTags.BASE_STONE_OVERWORLD)
                        || ground.is(Blocks.GRAVEL)
                        || ground.is(Blocks.SNOW)
                        || ground.is(Blocks.SNOW_BLOCK);

        if (!validGround) {
            return false;
        }

        /*
         * Rift is a solitary supernatural encounter. She is also a persistent
         * animal once spawned, so allow only one untamed wild Rift in the wider
         * local area.
         */
        if (level instanceof ServerLevel serverLevel) {
            int nearbyWild =
                    serverLevel.getEntitiesOfClass(
                                    RiftWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf -> wolf.isAlive()
                                            && !wolf.isTame())
                            .size();

            if (nearbyWild >= 1) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt(
                "RiftStepCooldown",
                this.riftStepCooldownTicks);

        output.putInt(
                "RiftRescueCooldown",
                this.emergencyRescueCooldownTicks);

        output.putInt(
                "RiftBanishCooldown",
                this.banishCooldownTicks);

        output.putInt(
                "RiftPassageCooldown",
                this.riftPassageCooldownTicks);

        output.putInt(
                "RiftOwnerReturnCooldown",
                this.ownerRiftReturnCooldownTicks);

        output.putInt(
                "RiftFieldCooldown",
                this.riftFieldCooldownTicks);

        output.putInt(
                "RiftFieldTicks",
                this.riftFieldTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        AttributeInstance maxHealth =
                this.getAttribute(Attributes.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.setBaseValue(RIFT_MAX_HEALTH);
        }

        super.readAdditionalSaveData(input);

        if (maxHealth != null) {
            maxHealth.setBaseValue(RIFT_MAX_HEALTH);
        }

        this.riftStepCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftStepCooldown",
                        0));

        this.emergencyRescueCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftRescueCooldown",
                        0));

        this.banishCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftBanishCooldown",
                        0));

        this.riftPassageCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftPassageCooldown",
                        0));

        this.ownerRiftReturnCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftOwnerReturnCooldown",
                        0));

        this.riftFieldCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "RiftFieldCooldown",
                        0));

        this.riftFieldTicks = Mth.clamp(
                input.getIntOr(
                        "RiftFieldTicks",
                        0),
                0,
                RIFT_FIELD_DURATION_TICKS);

        /*
         * Like Graveyard, the old world coordinate is not serialized. If the
         * world reloads during Rift Field, continue the remaining duration from
         * Rift's current position instead of reviving a stale cross-chunk point.
         */
        this.riftFieldCenter =
                this.riftFieldTicks > 0
                        && !this.isBaby()
                        ? this.position()
                        : null;

        if (this.isBaby()) {
            this.clearAdultRiftState();
        }
    }

    // ---------------------------------------------------------------------
    // Test/debug accessors
    // ---------------------------------------------------------------------

    public boolean isRiftStepReady() {
        return this.riftStepCooldownTicks <= 0;
    }

    public boolean isEmergencyRescueReady() {
        return this.emergencyRescueCooldownTicks <= 0;
    }

    public boolean isBanishReady() {
        return this.banishCooldownTicks <= 0;
    }

    public boolean isRiftPassageReady() {
        return this.riftPassageCooldownTicks <= 0;
    }

    public boolean isOwnerRiftReturnReady() {
        return this.ownerRiftReturnCooldownTicks <= 0;
    }

    public boolean isRiftFieldActive() {
        return this.riftFieldTicks > 0;
    }
}
