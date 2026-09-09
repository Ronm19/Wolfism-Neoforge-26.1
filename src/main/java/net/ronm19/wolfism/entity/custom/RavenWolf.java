package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ronm19.wolfism.entity.ai.control.RavenFlightSteering;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

import org.jspecify.annotations.Nullable;

/**
 * Raven's short powered strokes, coasting approaches and deliberate landings
 * share one flight controller. Ground goals only run after flight releases MOVE.
 */
public final class RavenWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Synced state
    // =====================================================================

    private static final EntityDataAccessor<Boolean> DATA_RAVEN_FLYING =
            SynchedEntityData.defineId(
                    RavenWolf.class,
                    EntityDataSerializers.BOOLEAN);

    // =====================================================================
    // Attributes
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 36.0D;
    private static final double BASE_ATTACK_DAMAGE = 5.5D;
    private static final double BASE_MOVEMENT_SPEED = 0.34D;
    private static final double BASE_ARMOR = 3.0D;

    // =====================================================================
    // AI
    // =====================================================================

    private static final int DECISION_INTERVAL = 10;

    private static final double COMBAT_FLIGHT_START_DISTANCE_SQR =
            7.0D * 7.0D;

    // =====================================================================
    // Flight
    // =====================================================================

    private static final double OWNER_FLIGHT_START_DISTANCE_SQR = 10.0D * 10.0D;
    private static final double OWNER_FLIGHT_STOP_DISTANCE_SQR = 3.5D * 3.5D;

    private static final double SCOUT_ORBIT_MIN_RADIUS = 5.0D;
    private static final double SCOUT_ORBIT_MAX_RADIUS = 8.0D;
    private static final double SCOUT_ORBIT_HEIGHT = 5.0D;

    private static final double OBSERVATION_DISTANCE = 9.0D;
    private static final double OBSERVATION_HEIGHT = 5.5D;

    private Vec3 moveTargetPoint = Vec3.ZERO;
    private boolean hasFlightMoveTarget;
    private boolean landingRequested;
    private boolean flightLanding;
    private boolean ownerFlight;
    private boolean processedSitCommand;
    private int rageCycleIndex = -1;
    private @Nullable Vec3 rageRetreatPoint;
    private @Nullable Vec3 landingPoint;
    private int groundedFlightCooldown;
    private int flightRepathTicks;
    private int flightStallTicks;
    private Vec3 flightProgressPosition = Vec3.ZERO;
    private @Nullable Vec3 lastPathDestination;
    private int ownerStallTicks;
    private double ownerPreviousDistance = Double.MAX_VALUE;
    private int lastOwnerTeleportTick = -400;

    private final MoveControl groundMoveControl;
    private final PathNavigation groundNavigation;
    private final RavenMoveControl flyingMoveControl;
    private final FlyingPathNavigation flyingNavigation;

    private static final EntityDataAccessor<Boolean> DATA_RAVEN_LANDING =
            SynchedEntityData.defineId(RavenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RAVEN_EYE_ACTIVE =
            SynchedEntityData.defineId(RavenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RAVEN_RAGE_ACTIVE =
            SynchedEntityData.defineId(RavenWolf.class, EntityDataSerializers.BOOLEAN);

    private int scoutOrbitIndex;

    // =====================================================================
    // Eye of Raven
    // =====================================================================

    public static final int EYE_OF_RAVEN_DURATION_TICKS =
            20 * 10;

    public static final int EYE_OF_RAVEN_COOLDOWN_TICKS =
            20 * 28;

    private static final double BASE_CONTAINER_SCAN_RADIUS = 16.0D;
    private static final double EYE_CONTAINER_SCAN_RADIUS = 28.0D;

    private static final int BASE_CONTAINER_SCAN_INTERVAL = 40;
    private static final int EYE_CONTAINER_SCAN_INTERVAL = 20;

    private int eyeOfRavenTicks;
    private int eyeOfRavenCooldownTicks;

    // =====================================================================
    // Theft
    // =====================================================================

    private static final double THEFT_INTERACT_DISTANCE_SQR =
            2.4D * 2.4D;

    private static final long CHECKED_CONTAINER_MEMORY_TICKS =
            20L * 60L * 5L;

    private static final int MAX_CHECKED_CONTAINER_MEMORIES = 48;

    private final Map<Long, Long> checkedContainers =
            new HashMap<>();

    private @Nullable BlockPos theftTargetPos;

    private ItemStack carriedLoot =
            ItemStack.EMPTY;

    // =====================================================================
    // Revenge
    // =====================================================================

    private static final int REVENGE_MEMORY_TICKS =
            20 * 60;

    private @Nullable UUID revengeOffenderUuid;
    private @Nullable EntityType<?> revengeMobType;

    private int revengeTicks;

    // =====================================================================
    // Tactical intelligence
    // =====================================================================

    private static final float RETREAT_HEALTH_FRACTION =
            0.32F;

    private static final double NORMAL_MAX_FIGHT_HEALTH_RATIO =
            2.60D;

    private static final double RAGE_MAX_FIGHT_HEALTH_RATIO =
            4.25D;

    private static final double REPORT_OWNER_DISTANCE_SQR =
            6.0D * 6.0D;

    // =====================================================================
    // Raven's Rage
    // =====================================================================

    public static final int RAVENS_RAGE_DURATION_TICKS =
            20 * 12;

    public static final int RAVENS_RAGE_COOLDOWN_TICKS =
            20 * 68;

    private static final double RAGE_COORDINATION_RADIUS =
            18.0D;

    private static final int RAGE_HARASS_HIT_COOLDOWN =
            16;

    private static final int RAGE_HARASS_CYCLE_TICKS =
            38;

    private static final int RAGE_PRESSURE_EFFECT_TICKS =
            30;

    private int ravensRageTicks;
    private int ravensRageCooldownTicks;

    private int rageHarassHitCooldownTicks;

    private int abilityLockoutTicks;
    private long theftApproachStartedAt = -1L;
    // =====================================================================
    // Owner sit/stand authority
    // =====================================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean wasSitting = this.isOrderedToSit();
        InteractionResult result = super.mobInteract(player, hand);
        if (!this.level().isClientSide() && this.isTame() && this.isOwnedBy(player)
                && wasSitting != this.isOrderedToSit()) {
            if (this.isOrderedToSit()) {
                cancelRavenActivityForSit();
                this.setInSittingPose(this.onGround());
            } else {
                this.setInSittingPose(false);
                if (flightLanding) requestNormalLanding();
            }
        }
        return result;
    }

    private boolean isRavenSitCommandActive() {
        return this.isOrderedToSit();
    }

    @Override
    public boolean beginFamilyDefense(LivingEntity attacker, LivingEntity protectedFamily) {
        return !isRavenSitCommandActive() && !this.isInSittingPose()
                && super.beginFamilyDefense(attacker, protectedFamily);
    }

    private void cancelRavenActivityForSit() {
        this.setTarget(null);
        if (this.hasFamilyDefenseEmergency()) this.clearFamilyDefense();
        this.groundNavigation.stop();
        this.flyingNavigation.stop();
        this.setSprinting(false);
        this.setJumping(false);
        clearTheftTarget();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
        eyeOfRavenTicks = 0;
        ravensRageTicks = 0;
        ownerFlight = false;
    }

    private void hardStopGroundedSit() {
        if (!isRavenSitCommandActive() || !this.onGround()) return;
        if (isRavenFlying()) finishFlight();
        this.groundNavigation.stop();
        this.groundMoveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setZza(0.0F);
        this.setJumping(false);
        this.setSprinting(false);
        this.setNoGravity(false);
        this.setInSittingPose(true);
        this.setXRot(0.0F);
        // Retain downward contact velocity: zeroing Y makes onGround alternate
        // false/true and used to restart airborne sitting every other tick.
        this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, -0.08D), 0.0D);
    }

    @Override
    public boolean shouldTryTeleportToOwner() {
        // Vanilla follow and panic must never teleport in the middle of a flight.
        return false;
    }

    @Override
    public void tryToTeleportToOwner() {
        // Only RavenFollowOwnerGoal's delayed, grounded recovery may teleport.
    }

    // =====================================================================
    // Brain
    // =====================================================================

    private static final class BrainHolder {

        private static final Brain.Provider<RavenWolf> PROVIDER =
                Brain.<RavenWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.RAVEN_TACTICAL.get()),
                        wolf -> List.of());
    }

    // =====================================================================
    // Constructor
    // =====================================================================

    public RavenWolf(
            EntityType<? extends RavenWolf> type,
            Level level) {

        super(type, level);

        /*
         * Preserve the normal Wolf controls created by Wolf, then build a
         * dedicated Raven flight stack. We only swap to it while Raven
         * is genuinely airborne.
         */
        this.groundMoveControl =
                this.moveControl;

        this.groundNavigation =
                this.navigation;

        this.flyingMoveControl =
                new RavenMoveControl(this);

        this.flyingNavigation =
                this.createRavenFlyingNavigation(level);

        this.lookControl =
                new RavenLookControl(this);
    }

    private FlyingPathNavigation createRavenFlyingNavigation(
            Level level) {

        FlyingPathNavigation navigation =
                new FlyingPathNavigation(
                        this,
                        level);

        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);

        return navigation;
    }

    // =====================================================================
    // Goals
    // =====================================================================

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> goal instanceof FollowOwnerGoal
                || goal instanceof SitWhenOrderedToGoal);
        this.goalSelector.addGoal(-2, new RavenSitGoal());
        this.goalSelector.addGoal(-1, new RavenFlightGoal());
        this.goalSelector.addGoal(6, new RavenFollowOwnerGoal());
    }

    @Override
    protected BodyRotationControl createBodyControl() {

        return new RavenBodyRotationControl(this);
    }

    // =====================================================================
    // Synced data
    // =====================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RAVEN_FLYING, false);
        builder.define(DATA_RAVEN_LANDING, false);
        builder.define(DATA_RAVEN_EYE_ACTIVE, false);
        builder.define(DATA_RAVEN_RAGE_ACTIVE, false);
    }

    // =====================================================================
    // Wolfism identity
    // =====================================================================

    @Override
    protected EntityType<? extends AbstractWolfismWolf>
    wolfismEntityType() {

        return ModEntities.RAVEN_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {

        return Wolf.createAttributes()
                .add(
                        Attributes.MAX_HEALTH,
                        BASE_MAX_HEALTH)
                .add(
                        Attributes.ATTACK_DAMAGE,
                        BASE_ATTACK_DAMAGE)
                .add(
                        Attributes.MOVEMENT_SPEED,
                        BASE_MOVEMENT_SPEED)
                .add(
                        Attributes.ARMOR,
                        BASE_ARMOR)
                .add(
                        Attributes.FOLLOW_RANGE,
                        48.0D)
                .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        0.08D)
                .add(
                        Attributes.STEP_HEIGHT,
                        1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {

        AttributeInstance maxHealth =
                this.getAttribute(
                        Attributes.MAX_HEALTH);

        if (maxHealth == null) {
            return;
        }

        float oldHealth =
                this.getHealth();

        maxHealth.setBaseValue(
                BASE_MAX_HEALTH);

        if (this.isTame()) {

            this.setHealth(
                    this.getMaxHealth());

        } else {

            this.setHealth(
                    Math.min(
                            oldHealth,
                            this.getMaxHealth()));
        }
    }

    // =====================================================================
    // Combat legality
    // =====================================================================

    @Override
    public boolean canAttack(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target == this
                || this.isBaby()
                || isRavenFamilyMember(target)) {

            return false;
        }

        /*
         * Sitting does NOT belong here.
         *
         * The sit system clears target/movement separately.
         * This keeps stale sitting state from poisoning combat legality.
         */
        return super.canAttack(target);
    }

    /**
     * Broad legal combat validation.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(isRavenSitCommandActive() ? null : target);
    }

    public boolean isValidRavenCombatTarget(
            LivingEntity target) {

        return target != null
                && target.isAlive()
                && target != this
                && !isRavenFamilyMember(target)
                && !this.isAlliedTo(target)
                && this.canAttack(target);
    }

    // =====================================================================
    // Brain
    // =====================================================================

    @Override
    protected Brain<RavenWolf> makeBrain(
            Brain.Packed packedBrain) {

        return BrainHolder.PROVIDER.makeBrain(
                this,
                packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<RavenWolf> getBrain() {

        return (Brain<RavenWolf>)
                super.getBrain();
    }

    @Override
    protected void customServerAiStep(
            ServerLevel level) {

        this.getBrain().tick(
                level,
                this);

        super.customServerAiStep(level);
    }

    // =====================================================================
    // Main tick
    // =====================================================================

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            if (isRavenSitCommandActive()) {
                if (!processedSitCommand) cancelRavenActivityForSit();
                processedSitCommand = true;
                hardStopGroundedSit();
            } else {
                processedSitCommand = false;
                if (this.isInSittingPose()) this.setInSittingPose(false);
            }
        }
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;
        tickRuntimeTimers();
        if (groundedFlightCooldown > 0) --groundedFlightCooldown;
        pruneCheckedContainers(level.getGameTime());
        try {
            tickRavenBehavior(level);
        } finally {
            this.entityData.set(DATA_RAVEN_LANDING, flightLanding && isRavenFlying());
            this.entityData.set(DATA_RAVEN_EYE_ACTIVE, eyeOfRavenTicks > 0);
            this.entityData.set(DATA_RAVEN_RAGE_ACTIVE, ravensRageTicks > 0);
        }
    }

    private void tickRavenBehavior(ServerLevel level) {
        if (isRavenSitCommandActive()) {
            hardStopGroundedSit();
            return;
        }
        if (this.isPassenger() || this.isNoAi()) {
            if (isRavenFlying()) finishFlight();
            return;
        }
        if (landingRequested || flightLanding) {
            tickNormalLanding();
            if (isRavenFlying()) return;
        }
        if (this.isBaby()) {
            clearAdultRavenStates();
            tickOwnerFlightFollow();
            return;
        }
        if (this.tickCount % DECISION_INTERVAL == Math.floorMod(this.getId(), DECISION_INTERVAL)) {
            tickCombatAbilityTriggers(level);
        }
        if (eyeOfRavenTicks > 0) tickEyeOfRaven(level);
        if (ravensRageTicks > 0) {
            tickRavensRage(level);
            return;
        }
        if (tickExistingCombatTarget()) return;
        if (!carriedLoot.isEmpty() && !this.hasFamilyDefenseEmergency()) {
            tickReturnLootToOwner(level);
            return;
        }
        if (theftTargetPos != null && !this.hasFamilyDefenseEmergency()
                && tickTheftApproach(level)) return;
        if (this.getBrain().getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get()).isPresent()) {
            tickReportObservedThreat(level);
            return;
        }
        int scanInterval = isEyeOfRavenActive() ? EYE_CONTAINER_SCAN_INTERVAL : BASE_CONTAINER_SCAN_INTERVAL;
        if (this.isTame() && this.getTarget() == null
                && this.tickCount % scanInterval == Math.floorMod(this.getId(), scanInterval)) {
            tickContainerRecon(level);
            if (theftTargetPos != null) return;
        }
        if (this.tickCount % DECISION_INTERVAL == Math.floorMod(this.getId(), DECISION_INTERVAL)) {
            tickRavenDecisionBrain(level);
        }
        if (this.getTarget() != null || isRavensRageActive()
                || this.getBrain().getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get()).isPresent()) return;
        if (isEyeOfRavenActive() && theftTargetPos == null && carriedLoot.isEmpty() && this.isTame()) {
            ownerFlight = false;
            tickScoutOrbit(level);
        } else {
            tickOwnerFlightFollow();
        }
        if (isRavenFlying() && !flightLanding && !hasActiveAerialPurpose()) requestNormalLanding();
    }

    // =====================================================================
    // Existing combat
    // =====================================================================

    private boolean tickExistingCombatTarget() {

        LivingEntity current =
                this.getTarget();

        if (current == null) {
            return false;
        }

        /*
         * Shared Wolfism Creeper AI remains authoritative.
         */
        if (current instanceof Creeper) {
            return true;
        }

        if (!isValidRavenCombatTarget(
                current)) {

            this.setTarget(null);

            return false;
        }

        if (theftTargetPos != null) {
            clearTheftTarget();
        }

        double distanceSqr =
                this.distanceToSqr(
                        current);

        /*
         * Far away:
         *
         * KEEP the target while flying.
         */
        if (distanceSqr
                > COMBAT_FLIGHT_START_DISTANCE_SQR) {

            requestFlightTo(
                    combatApproachPoint(
                            current));

            return true;
        }

        /*
         * Close enough:
         *
         * land while KEEPING target.
         */
        if (this.entityData.get(
                DATA_RAVEN_FLYING)) {

            requestNormalLanding();

            return true;
        }

        /*
         * Grounded:
         * let vanilla Wolf melee own combat.
         */
        return true;
    }

    private Vec3 combatApproachPoint(
            LivingEntity target) {

        return target.position()
                .add(
                        0.0D,
                        target.getBbHeight()
                                * 0.65D
                                + 1.0D,
                        0.0D);
    }

    private void commitToCombatTarget(LivingEntity target) {
        if (!isValidRavenCombatTarget(target) || this.isOrderedToSit()) {
            return;
        }
        // Retained targets may be outside the acquisition radius. Do not
        // re-acquire them or fly toward a target rejected by the shared base.
        if (this.getTarget() != target) {
            this.setTarget(target);
        }
        if (this.getTarget() != target) {
            return;
        }
        clearTheftTarget();
        if (this.distanceToSqr(target) > COMBAT_FLIGHT_START_DISTANCE_SQR) {
            requestFlightTo(combatApproachPoint(target));
        } else if (this.isRavenFlying()) {
            requestNormalLanding();
        }
    }

    // =====================================================================
    // Timers
    // =====================================================================

    private void tickRuntimeTimers() {

        if (eyeOfRavenCooldownTicks > 0) {
            --eyeOfRavenCooldownTicks;
        }

        if (eyeOfRavenTicks > 0) {
            --eyeOfRavenTicks;
        }

        if (ravensRageCooldownTicks > 0) {
            --ravensRageCooldownTicks;
        }

        if (ravensRageTicks > 0) {

            --ravensRageTicks;

            if (ravensRageTicks == 0
                    && isRavenFlying()) {

                requestNormalLanding();
            }
        }

        if (rageHarassHitCooldownTicks > 0) {
            --rageHarassHitCooldownTicks;
        }

        if (abilityLockoutTicks > 0) {
            --abilityLockoutTicks;
        }

        if (revengeTicks > 0) {

            --revengeTicks;

            if (revengeTicks <= 0) {
                clearRevengeMemory();
            }
        }
    }

    // =====================================================================
    // Baby cleanup
    // =====================================================================

    private void clearAdultRavenStates() {
        this.setTarget(null);
        eyeOfRavenTicks = 0;
        ravensRageTicks = 0;
        clearTheftTarget();
        // Pups may follow and land with their wings, but never use adult abilities.
    }

    // =====================================================================
    // Raven flight: one destination, bounded steering and a real landing phase
    // =====================================================================

    public boolean isRavenFlying() {
        return this.entityData.get(DATA_RAVEN_FLYING);
    }

    public boolean isRavenLanding() {
        return this.entityData.get(DATA_RAVEN_LANDING);
    }

    public float getRavenFlightPhase(float partialTick) {
        return (Math.floorMod(this.level().getGameTime() + this.getId() * 3L, 36L)
                + partialTick) % 36.0F;
    }

    private void enterRavenFlight() {
        if (isRavenFlying()) return;
        this.groundNavigation.stop();
        this.setInSittingPose(false);
        this.setJumping(false);
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setZza(0.0F);
        this.navigation = this.flyingNavigation;
        this.moveControl = this.flyingMoveControl;
        this.flyingMoveControl.reset();
        this.entityData.set(DATA_RAVEN_FLYING, true);
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        this.flightProgressPosition = this.position();
        this.flightStallTicks = 0;
    }

    private void leaveRavenFlight() {
        this.flyingNavigation.stop();
        this.flyingMoveControl.reset();
        clearFlightMoveTarget();
        this.navigation = this.groundNavigation;
        this.moveControl = this.groundMoveControl;
        this.groundNavigation.stop();
        this.groundMoveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setZza(0.0F);
        this.setJumping(false);
        flightLanding = false;
        landingRequested = false;
        ownerFlight = false;
        landingPoint = null;
        lastPathDestination = null;
        this.setNoGravity(false);
        this.entityData.set(DATA_RAVEN_FLYING, false);
        this.entityData.set(DATA_RAVEN_LANDING, false);
        this.setXRot(0.0F);
    }

    private void setRavenFlying(boolean flying) {
        if (flying) enterRavenFlight();
        else leaveRavenFlight();
    }

    private void setFlightMoveTarget(Vec3 target) {
        this.moveTargetPoint = target;
        this.hasFlightMoveTarget = true;
    }

    private void clearFlightMoveTarget() {
        this.hasFlightMoveTarget = false;
        this.moveTargetPoint = this.position();
    }

    private boolean touchingFlightTarget() {
        return !hasFlightMoveTarget || moveTargetPoint.distanceToSqr(this.position()) < 1.0D;
    }

    private void requestFlightTo(Vec3 destination) {
        if (isRavenSitCommandActive() || this.isPassenger() || this.isNoAi()
                || !Double.isFinite(destination.x) || !Double.isFinite(destination.y)
                || !Double.isFinite(destination.z)) return;
        if (!isRavenFlying() && groundedFlightCooldown > 0) {
            this.groundNavigation.moveTo(destination.x, destination.y, destination.z, 1.15D);
            return;
        }
        landingRequested = false;
        flightLanding = false;
        landingPoint = null;
        enterRavenFlight();
        setFlightMoveTarget(destination);
        this.setNoGravity(true);
    }

    private void requestNormalLanding() {
        if (!isRavenFlying()) return;
        if (!flightLanding) {
            landingPoint = null;
            this.flyingNavigation.stop();
            this.flyingMoveControl.reset();
        }
        ownerFlight = false;
        landingRequested = true;
        flightLanding = true;
        this.entityData.set(DATA_RAVEN_LANDING, true);
        if (this.onGround()) finishFlight();
        else selectNormalLandingPoint();
    }

    private void tickNormalLanding() {
        if (this.onGround()) {
            finishFlight();
            if (isRavenSitCommandActive()) hardStopGroundedSit();
            return;
        }
        enterRavenFlight();
        landingRequested = true;
        flightLanding = true;
        this.entityData.set(DATA_RAVEN_LANDING, true);
        this.setInSittingPose(false);
        this.setNoGravity(true);
        selectNormalLandingPoint();
    }

    private void selectNormalLandingPoint() {
        if (landingPoint == null || this.tickCount % 20 == 0
                && !isClearFlightPosition(landingPoint.add(0.0D, 0.10D, 0.0D))) {
            landingPoint = findLandingPoint();
            this.flyingNavigation.stop();
            this.flyingMoveControl.reset();
        }
        setFlightMoveTarget(landingPoint);
    }

    private Vec3 findLandingPoint() {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos origin = this.blockPosition();
        // Search actual collision surfaces below Raven. A heightmap selects the
        // roof above an indoor wolf and cannot describe slabs or cave floors.
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dz = -3; dz <= 3; ++dz) {
                BlockPos column = origin.offset(dx, 0, dz);
                if (!this.level().hasChunkAt(column)) continue;
                for (int y = origin.getY(); y >= Math.max(this.level().getMinY(), origin.getY() - 64); --y) {
                    BlockPos support = new BlockPos(column.getX(), y, column.getZ());
                    BlockState state = this.level().getBlockState(support);
                    VoxelShape shape = state.getCollisionShape(this.level(), support);
                    if (shape.isEmpty() || !state.getFluidState().isEmpty()) continue;
                    double top = y + shape.max(Direction.Axis.Y);
                    if (top > this.getY() + 0.1D) continue;
                    double x = dx == 0 ? this.getX() : column.getX() + 0.5D;
                    double z = dz == 0 ? this.getZ() : column.getZ() + 0.5D;
                    Vec3 feet = new Vec3(x, top + 0.05D, z);
                    if (!isClearFlightPosition(feet)) break;
                    double score = feet.distanceToSqr(this.position()) + (dx * dx + dz * dz) * 3.0D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = feet.add(0.0D, -0.09D, 0.0D);
                    }
                    break;
                }
            }
        }
        // At great height descend a bounded stage, then search again.
        return best != null ? best : this.position().add(0.0D, -8.0D, 0.0D);
    }

    private boolean isClearFlightPosition(Vec3 feet) {
        if (!this.level().hasChunkAt(BlockPos.containing(feet))) return false;
        var box = this.getBoundingBox().move(feet.subtract(this.position()));
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    private void finishFlight() {
        boolean wasFlying = isRavenFlying();
        leaveRavenFlight();
        if (wasFlying) groundedFlightCooldown = 40;
        if (this.onGround()) {
            this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, -0.08D), 0.0D);
        }
    }

    private boolean hasActiveAerialPurpose() {
        return ownerFlight || this.getTarget() != null || isRavensRageActive()
                || isEyeOfRavenActive() || theftTargetPos != null
                || !carriedLoot.isEmpty() && validFollowOwner() != null
                || this.getBrain().getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get()).isPresent();
    }

    private @Nullable LivingEntity validFollowOwner() {
        LivingEntity owner = this.getOwner();
        return owner != null && owner.isAlive() && !owner.isSpectator()
                && owner.level() == this.level() ? owner : null;
    }

    private boolean canFollowOwner() {
        return this.isTame() && !isRavenSitCommandActive() && !this.isPassenger()
                && !this.isLeashed() && this.getTarget() == null && theftTargetPos == null
                && carriedLoot.isEmpty() && !isRavensRageActive() && !isEyeOfRavenActive()
                && !this.hasFamilyDefenseEmergency()
                && this.getBrain().getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get()).isEmpty();
    }

    private void tickOwnerFlightFollow() {
        if (!ownerFlight) return;
        LivingEntity owner = validFollowOwner();
        if (!canFollowOwner() || owner == null) {
            ownerFlight = false;
            if (!hasActiveAerialPurpose()) requestNormalLanding();
            return;
        }
        Vec3 offset = owner.position().subtract(this.position());
        if (offset.horizontalDistanceSqr() <= OWNER_FLIGHT_STOP_DISTANCE_SQR
                && Math.abs(offset.y) < 3.5D && owner.onGround()) {
            requestNormalLanding();
            return;
        }
        Vec3 lead = owner.getDeltaMovement().scale(5.0D);
        requestFlightTo(owner.position().add(lead.x, 2.0D, lead.z));
    }

    private void updateFlightNavigation() {
        if (!hasFlightMoveTarget) return;
        if (flightLanding && touchingFlightTarget()) {
            // The target lies just below the supporting surface so collision
            // produces onGround; never hover a few pixels above a sit landing.
            this.flyingNavigation.stop();
            this.flyingMoveControl.reset();
        } else if (--flightRepathTicks <= 0) {
            flightRepathTicks = 10;
            if (lastPathDestination == null || lastPathDestination.distanceToSqr(moveTargetPoint) > 2.25D
                    || this.flyingNavigation.isDone()) {
                lastPathDestination = moveTargetPoint;
                this.flyingNavigation.moveTo(moveTargetPoint.x, moveTargetPoint.y, moveTargetPoint.z, 1.0D);
            }
        }
        if (this.tickCount % 20 == 0) {
            if (this.position().distanceToSqr(flightProgressPosition) < 0.16D && !touchingFlightTarget()) {
                flightStallTicks += 20;
            } else flightStallTicks = 0;
            flightProgressPosition = this.position();
            if (flightStallTicks >= 100 && !flightLanding) requestNormalLanding();
            if (flightLanding && (touchingFlightTarget() || flightStallTicks >= 60)) {
                landingPoint = null;
                selectNormalLandingPoint();
                flightStallTicks = 0;
            }
        }
    }

    @Override
    public void travel(Vec3 input) {
        if ((isRavenSitCommandActive() || this.isInSittingPose()) && this.onGround()) {
            this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, -0.08D), 0.0D);
            super.travel(Vec3.ZERO);
        } else if (isRavenFlying()) {
            // The motor owns velocity; ground-goal input must not add a second
            // acceleration. Vanilla's flight travel performs collision once.
            this.travelFlying(Vec3.ZERO, 0.0F);
            this.fallDistance = 0.0F;
        } else super.travel(input);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // Raven's wings retain the species' existing fall protection.
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    // =====================================================================
    // Scout orbit
    // =====================================================================

    private void tickScoutOrbit(
            ServerLevel level) {

        LivingEntity owner =
                this.getOwner();

        if (owner == null
                || !owner.isAlive()
                || this.isOrderedToSit()
                || this.getTarget() != null) {

            return;
        }

        if (this.tickCount % 30
                != Math.floorMod(
                this.getId(),
                30)
                && moveTargetPoint.distanceToSqr(
                this.position())
                > 4.0D) {

            return;
        }

        ++scoutOrbitIndex;

        double angle =
                scoutOrbitIndex
                        * 1.37D
                        + this.getId()
                        * 0.31D;

        double radius =
                SCOUT_ORBIT_MIN_RADIUS
                        + this.random.nextDouble()
                        * (SCOUT_ORBIT_MAX_RADIUS
                        - SCOUT_ORBIT_MIN_RADIUS);

        Vec3 destination =
                new Vec3(
                        owner.getX()
                                + Math.cos(angle)
                                * radius,

                        owner.getY()
                                + SCOUT_ORBIT_HEIGHT
                                + this.random.nextDouble()
                                * 2.0D,

                        owner.getZ()
                                + Math.sin(angle)
                                * radius);

        requestFlightTo(
                destination);

        if (this.tickCount % 40 == 0) {

            level.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    this.getX(),
                    this.getY()
                            + this.getBbHeight()
                            * 0.65D,
                    this.getZ(),
                    3,
                    0.35D,
                    0.20D,
                    0.35D,
                    0.015D);
        }
    }

    // =====================================================================
    // Eye of Raven
    // =====================================================================

    public boolean isEyeOfRavenActive() {
        return this.level().isClientSide()
                ? this.entityData.get(DATA_RAVEN_EYE_ACTIVE)
                : eyeOfRavenTicks > 0;
    }

    private boolean tryStartEyeOfRaven(
            ServerLevel level,
            boolean meaningfulDiscovery) {

        if (!meaningfulDiscovery
                || eyeOfRavenCooldownTicks > 0
                || eyeOfRavenTicks > 0
                || abilityLockoutTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        eyeOfRavenTicks =
                EYE_OF_RAVEN_DURATION_TICKS;

        eyeOfRavenCooldownTicks =
                EYE_OF_RAVEN_COOLDOWN_TICKS;

        abilityLockoutTicks =
                8;

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY()
                        + this.getBbHeight()
                        * 0.72D,
                this.getZ(),
                24,
                0.55D,
                0.35D,
                0.55D,
                0.045D);

        return true;
    }

    private void tickEyeOfRaven(
            ServerLevel level) {

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.SPEED,
                        25,
                        0,
                        true,
                        true));

        if (eyeOfRavenTicks % 20 == 0) {

            level.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    this.getX(),
                    this.getY()
                            + this.getBbHeight()
                            * 0.72D,
                    this.getZ(),
                    4,
                    0.25D,
                    0.18D,
                    0.25D,
                    0.015D);
        }
    }

    // =====================================================================
    // Container reconnaissance
    // =====================================================================

    private void tickContainerRecon(ServerLevel level) {
        if (!this.isTame() || this.isOrderedToSit() || this.isInSittingPose()
                || this.getTarget() != null || !carriedLoot.isEmpty()
                || this.hasFamilyDefenseEmergency()) {
            return;
        }
        // A theft must have somebody available to receive it.
        LivingEntity owner = this.getOwner();
        if (!(owner instanceof Player) || !owner.isAlive()) {
            return;
        }
        double radius = isEyeOfRavenActive()
                ? EYE_CONTAINER_SCAN_RADIUS : BASE_CONTAINER_SCAN_RADIUS;
        BlockPos candidate = findBestGeneratedLootContainer(level, radius);
        if (candidate == null) {
            return;
        }
        theftTargetPos = candidate.immutable();
        theftApproachStartedAt = level.getGameTime();
        this.getBrain().setMemory(ModMemoryModuleTypes.RAVEN_LOOT_POS.get(), theftTargetPos);
        tryStartEyeOfRaven(level, true);
        requestFlightTo(Vec3.atCenterOf(candidate).add(0.0D, 1.25D, 0.0D));
    }

    private @Nullable BlockPos findBestGeneratedLootContainer(
            ServerLevel level,
            double radius) {

        int centerChunkX =
                this.blockPosition()
                        .getX() >> 4;

        int centerChunkZ =
                this.blockPosition()
                        .getZ() >> 4;

        int chunkRadius =
                Math.max(
                        1,
                        ((int) Math.ceil(radius))
                                / 16
                                + 1);

        double radiusSqr =
                radius * radius;

        BlockPos bestPos =
                null;

        double bestScore =
                Double.MAX_VALUE;

        for (int chunkX =
             centerChunkX - chunkRadius;
             chunkX <= centerChunkX + chunkRadius;
             ++chunkX) {

            for (int chunkZ =
                 centerChunkZ - chunkRadius;
                 chunkZ <= centerChunkZ + chunkRadius;
                 ++chunkZ) {

                LevelChunk chunk =
                        level.getChunkSource()
                                .getChunkNow(
                                        chunkX,
                                        chunkZ);

                if (chunk == null) {
                    continue;
                }

                for (Map.Entry<BlockPos, BlockEntity> entry
                        : chunk.getBlockEntities()
                        .entrySet()) {

                    BlockPos pos =
                            entry.getKey();

                    Vec3 center =
                            Vec3.atCenterOf(
                                    pos);

                    if (this.position()
                            .distanceToSqr(center)
                            > radiusSqr) {

                        continue;
                    }

                    if (Math.abs(
                            pos.getY()
                                    - this.getY())
                            > 14.0D) {

                        continue;
                    }

                    if (isContainerRemembered(
                            pos,
                            level.getGameTime())) {

                        continue;
                    }

                    if (!(entry.getValue()
                            instanceof RandomizableContainer generated)) {

                        continue;
                    }

                    /*
                     * Generated-world container only.
                     */
                    if (generated.getLootTable()
                            == null) {

                        continue;
                    }

                    double score =
                            this.position()
                                    .distanceToSqr(
                                            center);

                    if (score < bestScore) {

                        bestScore =
                                score;

                        bestPos =
                                pos;
                    }
                }
            }
        }

        return bestPos;
    }

    // =====================================================================
    // Theft approach
    // =====================================================================

    private boolean tickTheftApproach(ServerLevel level) {
        if (theftTargetPos == null) {
            return false;
        }
        if (this.getTarget() != null || this.hasFamilyDefenseEmergency()
                || this.isOrderedToSit()) {
            clearTheftTarget();
            return false;
        }
        long now = level.getGameTime();
        if (theftApproachStartedAt < 0L) {
            theftApproachStartedAt = now;
        }
        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()
                || !level.hasChunkAt(theftTargetPos)
                || now - theftApproachStartedAt >= 20L * 20L) {
            rememberCheckedContainer(theftTargetPos, now);
            clearTheftTarget();
            requestNormalLanding();
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(theftTargetPos);
        if (!(blockEntity instanceof RandomizableContainer generated)
                || generated.getLootTable() == null) {
            rememberCheckedContainer(theftTargetPos, now);
            clearTheftTarget();
            return false;
        }
        Vec3 center = Vec3.atCenterOf(theftTargetPos);
        if (this.position().distanceToSqr(center) > THEFT_INTERACT_DISTANCE_SQR
                || !hasRavenContainerLineOfSight(level, theftTargetPos)) {
            requestFlightTo(center.add(0.0D, 1.25D, 0.0D));
            return true;
        }
        stealBestGeneratedLoot(level, generated, theftTargetPos);
        return true;
    }

    private void clearTheftTarget() {
        theftTargetPos = null;
        theftApproachStartedAt = -1L;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_LOOT_POS.get());
    }

    // =====================================================================
    // Theft
    // =====================================================================

    private void stealBestGeneratedLoot(
            ServerLevel level,
            RandomizableContainer generated,
            BlockPos pos) {

        Player ownerPlayer =
                this.getOwner()
                        instanceof Player player
                        ? player
                        : null;

        generated.unpackLootTable(
                ownerPlayer);

        Container container =
                generated;

        int bestSlot =
                -1;

        double bestScore =
                0.0D;

        for (int slot = 0;
             slot < container.getContainerSize();
             ++slot) {

            ItemStack stack =
                    container.getItem(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            double score =
                    scoreLootForOwner(
                            stack);

            if (score > bestScore) {

                bestScore =
                        score;

                bestSlot =
                        slot;
            }
        }

        rememberCheckedContainer(
                pos,
                level.getGameTime());

        clearTheftTarget();

        if (bestSlot < 0
                || bestScore <= 0.0D) {

            level.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    3,
                    0.15D,
                    0.10D,
                    0.15D,
                    0.01D);

            requestNormalLanding();

            return;
        }

        ItemStack source =
                container.getItem(
                        bestSlot);

        int amount =
                chooseTheftAmount(
                        source);

        ItemStack stolen =
                container.removeItem(
                        bestSlot,
                        amount);

        container.setChanged();

        if (stolen.isEmpty()) {

            requestNormalLanding();

            return;
        }

        carriedLoot =
                stolen;

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                8,
                0.30D,
                0.22D,
                0.30D,
                0.03D);
    }

    // =====================================================================
    // Loot valuation
    // =====================================================================

    private double scoreLootForOwner(
            ItemStack stack) {

        if (stack.isEmpty()) {
            return 0.0D;
        }

        double score =
                Math.min(
                        12.0D,
                        stack.getCount()
                                * 0.35D);

        Rarity rarity =
                stack.getRarity();

        if (rarity == Rarity.UNCOMMON) {

            score += 9.0D;

        } else if (rarity == Rarity.RARE) {

            score += 22.0D;

        } else if (rarity == Rarity.EPIC) {

            score += 42.0D;

        } else {

            score += 2.0D;
        }

        if (stack.isEnchanted()) {
            score += 24.0D;
        }

        if (stack.is(
                Items.NETHERITE_INGOT)) {

            score += 100.0D;

        } else if (stack.is(
                Items.NETHERITE_SCRAP)) {

            score += 72.0D;

        } else if (stack.is(
                Items.ANCIENT_DEBRIS)) {

            score += 65.0D;

        } else if (stack.is(
                Items.DIAMOND)) {

            score += 58.0D;

        } else if (stack.is(
                Items.ENCHANTED_GOLDEN_APPLE)) {

            score += 55.0D;

        } else if (stack.is(
                Items.EMERALD)) {

            score += 35.0D;

        } else if (stack.is(
                Items.GOLDEN_APPLE)) {

            score += 32.0D;

        } else if (stack.is(
                Items.NAME_TAG)
                || stack.is(
                Items.SADDLE)) {

            score += 24.0D;

        } else if (stack.is(
                Items.ENDER_PEARL)
                || stack.is(
                Items.BLAZE_ROD)) {

            score += 20.0D;

        } else if (stack.is(
                Items.GOLD_INGOT)
                || stack.is(
                Items.IRON_INGOT)) {

            score += 14.0D;

        } else if (stack.is(
                Items.BREAD)
                || stack.is(
                Items.COOKED_BEEF)
                || stack.is(
                Items.COOKED_PORKCHOP)) {

            score += 8.0D;

        } else if (stack.is(
                Items.ARROW)) {

            score += 6.0D;
        }

        return score;
    }

    private int chooseTheftAmount(
            ItemStack stack) {

        if (stack.isEmpty()) {
            return 0;
        }

        int cap;

        if (stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.NETHERITE_SCRAP)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.IRON_INGOT)) {

            cap = 8;

        } else if (stack.is(Items.BREAD)
                || stack.is(Items.COOKED_BEEF)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.ARROW)) {

            cap = 6;

        } else if (stack.getRarity()
                == Rarity.COMMON) {

            cap = 3;

        } else {

            cap = 2;
        }

        return Math.max(
                1,
                Math.min(
                        cap,
                        stack.getCount()));
    }

    // =====================================================================
    // Loot delivery
    // =====================================================================

    private void tickReturnLootToOwner(ServerLevel level) {
        if (carriedLoot.isEmpty()) {
            return;
        }
        LivingEntity owner = this.getOwner();
        if (!(owner instanceof Player player) || !player.isAlive()) {
            // Keep the saved item and settle until its recipient returns.
            requestNormalLanding();
            return;
        }
        if (this.distanceToSqr(player) > 2.75D * 2.75D
                || !this.hasLineOfSight(player)) {
            requestFlightTo(player.position().add(0.0D, 1.7D, 0.0D));
            return;
        }
        ItemStack delivery = carriedLoot.copy();
        player.addItem(delivery);
        if (!delivery.isEmpty()) {
            player.drop(delivery, false);
        }
        carriedLoot = ItemStack.EMPTY;
        abilityLockoutTicks = 8;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + player.getBbHeight() * 0.55D, player.getZ(),
                10, 0.35D, 0.35D, 0.35D, 0.035D);
        requestNormalLanding();
    }

    // =====================================================================
    // Container memory
    // =====================================================================

    private void rememberCheckedContainer(
            BlockPos pos,
            long now) {

        if (checkedContainers.size()
                >= MAX_CHECKED_CONTAINER_MEMORIES) {

            Long oldestKey =
                    null;

            long oldestExpiry =
                    Long.MAX_VALUE;

            for (Map.Entry<Long, Long> entry
                    : checkedContainers.entrySet()) {

                if (entry.getValue()
                        < oldestExpiry) {

                    oldestExpiry =
                            entry.getValue();

                    oldestKey =
                            entry.getKey();
                }
            }

            if (oldestKey != null) {

                checkedContainers.remove(
                        oldestKey);
            }
        }

        checkedContainers.put(
                pos.asLong(),
                now
                        + CHECKED_CONTAINER_MEMORY_TICKS);
    }

    private boolean isContainerRemembered(
            BlockPos pos,
            long now) {

        Long expiry =
                checkedContainers.get(
                        pos.asLong());

        if (expiry == null) {
            return false;
        }

        if (expiry <= now) {

            checkedContainers.remove(
                    pos.asLong());

            return false;
        }

        return true;
    }

    private void pruneCheckedContainers(
            long now) {

        if (this.tickCount % 100 != 0
                || checkedContainers.isEmpty()) {

            return;
        }

        checkedContainers
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= now);
    }

    // =====================================================================
    // Raven's Revenge
    // =====================================================================

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float amount) {

        boolean wasOrderedToSit = this.isOrderedToSit();
        boolean damaged =
                super.hurtServer(
                        level,
                        source,
                        amount);
        if (wasOrderedToSit) this.setOrderedToSit(true);

        if (!damaged
                || this.isBaby()
                || this.isOrderedToSit()) {

            return damaged;
        }

        Entity sourceEntity =
                source.getEntity();

        if (sourceEntity
                instanceof LivingEntity attacker
                && attacker != this
                && isValidRavenCombatTarget(
                attacker)) {

            rememberOffender(
                    attacker);

            this.getBrain()
                    .setMemory(
                            ModMemoryModuleTypes
                                    .RAVEN_PRIORITY_THREAT
                                    .get(),
                            attacker);

            if (shouldEngageThreat(
                    attacker)) {

                commitToCombatTarget(
                        attacker);

            } else {

                this.setTarget(
                        null);

                this.getBrain()
                        .setMemory(
                                ModMemoryModuleTypes
                                        .RAVEN_OBSERVED_THREAT
                                        .get(),
                                attacker);

                beginObservationRetreat(
                        attacker);
            }
        }

        return damaged;
    }

    private void rememberOffender(
            LivingEntity attacker) {

        revengeOffenderUuid =
                attacker.getUUID();

        revengeMobType =
                attacker.getType();

        revengeTicks =
                REVENGE_MEMORY_TICKS;
    }

    private void clearRevengeMemory() {

        revengeOffenderUuid =
                null;

        revengeMobType =
                null;

        revengeTicks =
                0;
    }

    public boolean isExactRavenOffender(
            LivingEntity target) {

        return revengeTicks > 0
                && revengeOffenderUuid != null
                && revengeOffenderUuid.equals(
                target.getUUID());
    }

    public boolean isRememberedRavenType(
            LivingEntity target) {

        return revengeTicks > 0
                && revengeMobType != null
                && target.getType()
                == revengeMobType;
    }

    // =====================================================================
    // Tactical intelligence
    // =====================================================================

    /** Evaluate active abilities even while ordinary melee retains its target. */
    private void tickCombatAbilityTriggers(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()) {
            return;
        }
        LivingEntity current = this.getTarget();
        if (current == null || current instanceof Creeper || !isValidRavenCombatTarget(current)) {
            return;
        }
        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_HOSTILE_COUNT.get()).orElse(0);
        int nearbyRavens = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_NEARBY_RAVEN_COUNT.get()).orElse(1);
        if (!isRavensRageActive() && ravensRageCooldownTicks <= 0 && abilityLockoutTicks <= 0
                && shouldStartRavensRage(current, hostileCount, nearbyRavens)) {
            startRavensRage(level, current);
            if (isRavensRageActive()) {
                return;
            }
        }
        if (!isEyeOfRavenActive() && eyeOfRavenCooldownTicks <= 0 && hostileCount >= 2) {
            tryStartEyeOfRaven(level, true);
        }
    }
    private boolean hasRavenContainerLineOfSight(ServerLevel level, BlockPos pos) {
        BlockHitResult hit = level.clip(new ClipContext(this.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private void tickRavenDecisionBrain(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()) {
            return;
        }
        LivingEntity existing = this.getTarget();
        if (existing != null) {
            if (existing instanceof Creeper) {
                return;
            }
            if (isValidRavenCombatTarget(existing)) {
                tickCombatAbilityTriggers(level);
                if (!isRavensRageActive()) {
                    commitToCombatTarget(existing);
                }
                return;
            }
            this.setTarget(null);
        }
        LivingEntity priority = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_PRIORITY_THREAT.get()).orElse(null);
        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_HOSTILE_COUNT.get()).orElse(0);
        int nearbyRavens = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_NEARBY_RAVEN_COUNT.get()).orElse(1);
        LivingEntity familyThreat = this.hasFamilyDefenseEmergency()
                ? this.getFamilyDefenseTarget() : null;
        if (familyThreat != null && isValidRavenCombatTarget(familyThreat)) {
            priority = familyThreat;
        }
        if (!isRavensRageActive() && ravensRageCooldownTicks <= 0
                && abilityLockoutTicks <= 0
                && shouldStartRavensRage(priority, hostileCount, nearbyRavens)
                && this.isWithinWolfismPhysicalAggroAcquireRange(priority)) {
            startRavensRage(level, priority);
            if (isRavensRageActive()) {
                return;
            }
        }
        if (familyThreat != null && isValidRavenCombatTarget(familyThreat)
                && this.isWithinWolfismPhysicalAggroAcquireRange(familyThreat)) {
            commitToCombatTarget(familyThreat);
            return;
        }
        if (!isEyeOfRavenActive() && eyeOfRavenCooldownTicks <= 0
                && priority != null && hostileCount >= 2) {
            tryStartEyeOfRaven(level, true);
        }
        // A pending observation must get a chance to return and report
        // even while the original threat remains in the sensor radius.
        if (this.getBrain().getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get())
                .isPresent()) {
            tickReportObservedThreat(level);
            return;
        }
        if (priority == null || !isValidRavenThreat(priority)) {
            return;
        }
        if (shouldEngageThreat(priority)
                && this.isWithinWolfismPhysicalAggroAcquireRange(priority)) {
            commitToCombatTarget(priority);
            return;
        }
        LivingEntity reported = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_REPORTED_THREAT.get()).orElse(null);
        if (reported == priority) {
            return;
        }
        this.setTarget(null);
        this.getBrain().setMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get(), priority);
        beginObservationRetreat(priority);
    }

    // =====================================================================
    // Autonomous threat validation
    // =====================================================================

    public boolean isValidRavenThreat(
            LivingEntity target) {

        if (!isValidRavenCombatTarget(
                target)) {

            return false;
        }

        if (target instanceof Creeper) {
            return false;
        }

        if (target
                == this.getFamilyDefenseTarget()) {

            return true;
        }

        if (target instanceof Mob mob) {

            LivingEntity victim =
                    mob.getTarget();

            if (victim != null
                    && isRavenFamilyMember(
                    victim)) {

                return true;
            }
        }

        if (!(target instanceof Enemy)
                && !(target instanceof WitherBoss)
                && !(target instanceof EnderDragon)) {

            return false;
        }

        LivingEntity owner =
                this.getOwner();

        return !this.isTame()
                || owner == null
                || this.wantsToAttack(
                target,
                owner);
    }

    // =====================================================================
    // Threat score
    // =====================================================================

    public double ravenThreatScore(
            LivingEntity target) {

        if (!isValidRavenThreat(target)) {
            return Double.MAX_VALUE;
        }

        double score =
                Math.sqrt(
                        this.distanceToSqr(
                                target));

        if (target
                == this.getFamilyDefenseTarget()) {

            score -=
                    1000.0D;
        }

        if (isExactRavenOffender(
                target)) {

            score -=
                    800.0D;

        } else if (isRememberedRavenType(
                target)) {

            score -=
                    220.0D;
        }

        if (target instanceof Mob mob
                && mob.getTarget() != null
                && isRavenFamilyMember(
                mob.getTarget())) {

            score -=
                    500.0D;
        }

        score -=
                Math.min(
                        30.0D,
                        target.getMaxHealth()
                                * 0.08D);

        return score;
    }

    // =====================================================================
    // Fight-or-flight
    // =====================================================================

    private boolean shouldEngageThreat(
            LivingEntity target) {

        if (!isValidRavenCombatTarget(
                target)) {

            return false;
        }

        if (target
                == this.getFamilyDefenseTarget()) {

            return true;
        }

        if (this.getHealth()
                <= this.getMaxHealth()
                * RETREAT_HEALTH_FRACTION
                && !isRavensRageActive()) {

            return false;
        }

        if (target instanceof EnderDragon
                || target instanceof WitherBoss) {

            return isRavensRageActive()
                    || this.hasFamilyDefenseEmergency();
        }

        double healthRatio =
                target.getMaxHealth()
                        / Math.max(
                        1.0D,
                        this.getMaxHealth());

        double permitted =
                isRavensRageActive()
                        ? RAGE_MAX_FIGHT_HEALTH_RATIO
                        : NORMAL_MAX_FIGHT_HEALTH_RATIO;

        if (isExactRavenOffender(
                target)) {

            permitted +=
                    0.75D;
        }

        return healthRatio
                <= permitted;
    }

    // =====================================================================
    // Observation
    // =====================================================================

    private void beginObservationRetreat(
            LivingEntity threat) {

        LivingEntity owner =
                this.getOwner();

        Vec3 away =
                this.position()
                        .subtract(
                                threat.position());

        if (away.lengthSqr()
                < 0.001D
                && owner != null) {

            away =
                    owner.position()
                            .subtract(
                                    threat.position());
        }

        if (away.lengthSqr()
                < 0.001D) {

            away =
                    new Vec3(
                            1.0D,
                            0.0D,
                            0.0D);
        }

        away =
                new Vec3(
                        away.x,
                        0.0D,
                        away.z)
                        .normalize();

        Vec3 observationPoint =
                threat.position()
                        .add(
                                away.x
                                        * OBSERVATION_DISTANCE,

                                threat.getBbHeight()
                                        + OBSERVATION_HEIGHT,

                                away.z
                                        * OBSERVATION_DISTANCE);

        requestFlightTo(
                observationPoint);
    }

    // =====================================================================
    // Reporting
    // =====================================================================

    private void tickReportObservedThreat(ServerLevel level) {
        LivingEntity observed = this.getBrain()
                .getMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get()).orElse(null);
        LivingEntity owner = this.getOwner();
        if (observed == null) {
            return;
        }
        if (!observed.isAlive() || observed.isRemoved()
                || observed.level() != this.level() || !isValidRavenThreat(observed)) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
            requestNormalLanding();
            return;
        }
        if (owner == null || !owner.isAlive()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
            this.getBrain().setMemoryWithExpiry(
                    ModMemoryModuleTypes.RAVEN_REPORTED_THREAT.get(), observed, 20L * 4L);
            requestNormalLanding();
            return;
        }
        if (this.getTarget() == observed) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
            return;
        }
        if (this.distanceToSqr(owner) > REPORT_OWNER_DISTANCE_SQR) {
            requestFlightTo(owner.position().add(0.0D, 2.0D, 0.0D));
            return;
        }
        observed.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 4, 0, true, false), this);
        this.getBrain().setMemoryWithExpiry(
                ModMemoryModuleTypes.RAVEN_REPORTED_THREAT.get(), observed, 20L * 4L);
        shareReportWithRavens(level, observed);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                this.getX(), this.getY() + this.getBbHeight() * 0.70D, this.getZ(),
                6, 0.25D, 0.20D, 0.25D, 0.02D);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
        if (!isEyeOfRavenActive()) {
            requestNormalLanding();
        }
    }

    private void shareReportWithRavens(ServerLevel level, LivingEntity threat) {
        for (RavenWolf raven : level.getEntitiesOfClass(RavenWolf.class,
                this.getBoundingBox().inflate(18.0D),
                other -> other.isAlive() && (other == this || this.isRavenPackmate(other)))) {
            raven.getBrain().setMemoryWithExpiry(
                    ModMemoryModuleTypes.RAVEN_REPORTED_THREAT.get(), threat, 20L * 4L);
        }
    }

    // =====================================================================
    // Raven's Rage
    // =====================================================================

    public boolean isRavensRageActive() {
        return this.level().isClientSide()
                ? this.entityData.get(DATA_RAVEN_RAGE_ACTIVE)
                : ravensRageTicks > 0;
    }

    private boolean shouldStartRavensRage(
            @Nullable LivingEntity priority,
            int hostileCount,
            int nearbyRavens) {

        if (priority == null
                || !isValidRavenThreat(
                priority)) {

            return false;
        }

        if (this.hasFamilyDefenseEmergency()) {
            return true;
        }

        if (hostileCount >= 5) {
            return true;
        }

        if (revengeTicks > 0
                && hostileCount >= 2) {

            return true;
        }

        return nearbyRavens >= 2
                && hostileCount >= 3;
    }

    private void startRavensRage(ServerLevel level, @Nullable LivingEntity priority) {
        if (!this.canUseActiveWolfismAbility() || priority == null
                || priority instanceof Creeper || !isValidRavenCombatTarget(priority)) {
            return;
        }
        if (this.getTarget() != priority) {
            this.setTarget(priority);
        }
        if (this.getTarget() != priority) {
            return;
        }
        ravensRageTicks = RAVENS_RAGE_DURATION_TICKS;
        ravensRageCooldownTicks = RAVENS_RAGE_COOLDOWN_TICKS;
        rageHarassHitCooldownTicks = 0;
        rageRetreatPoint = null;
        rageCycleIndex = -1;
        abilityLockoutTicks = 10;
        clearTheftTarget();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.RAVEN_OBSERVED_THREAT.get());
        requestFlightTo(combatApproachPoint(priority));
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + this.getBbHeight() * 0.65D, this.getZ(),
                28, 0.70D, 0.45D, 0.70D, 0.04D);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                this.getX(), this.getY() + this.getBbHeight() * 0.68D, this.getZ(),
                20, 0.55D, 0.35D, 0.55D, 0.04D);
    }

    private void tickRavensRage(ServerLevel level) {
        if (this.isOrderedToSit()) {
            return;
        }
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 0, true, true));
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 25, 0, true, true));
        LivingEntity target = this.getTarget();
        if (target instanceof Creeper) {
            return;
        }
        if (target != null && !isValidRavenCombatTarget(target)) {
            this.setTarget(null);
            target = null;
        }
        if (target == null) {
            LivingEntity priority = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.RAVEN_PRIORITY_THREAT.get()).orElse(null);
            if (priority != null && isValidRavenThreat(priority)) {
                this.setTarget(priority);
                target = this.getTarget();
            }
        }
        if (target == null || !isValidRavenCombatTarget(target)) {
            // Ending an engagement must not leave the last dive waypoint live.
            requestNormalLanding();
            return;
        }
        tickRageHarassment(level, target);
        if (countCoordinatedRavens(level, target) >= 2 && this.tickCount % 20 == 0) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, RAGE_PRESSURE_EFFECT_TICKS, 0, true, true), this);
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, RAGE_PRESSURE_EFFECT_TICKS, 0, true, true), this);
        }
    }

    private void tickRageHarassment(ServerLevel level, LivingEntity target) {
        int clock = this.tickCount + this.getId() * 3;
        int cycle = Math.floorMod(clock, RAGE_HARASS_CYCLE_TICKS);
        int cycleIndex = Math.floorDiv(clock, RAGE_HARASS_CYCLE_TICKS);
        if (rageRetreatPoint == null || cycleIndex != rageCycleIndex) {
            rageCycleIndex = cycleIndex;
            double angle = cycleIndex * 1.37D + this.getId() * 0.71D;
            double radius = 4.0D + (this.getId() & 1) * 1.2D;
            // Hold a real flank position for the coast phase. A continuously rotating
            // destination used to outrun Raven and prevent a completed approach.
            rageRetreatPoint = target.position().add(Math.cos(angle) * radius,
                    target.getBbHeight() + 2.8D, Math.sin(angle) * radius);
        }
        if (cycle < 14 && rageHarassHitCooldownTicks <= 0) {
            requestFlightTo(target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D));
        } else {
            requestFlightTo(rageRetreatPoint);
        }
        if (cycle < 14 && rageHarassHitCooldownTicks <= 0 && this.hasLineOfSight(target)
                && this.distanceToSqr(target) <= 1.85D * 1.85D
                && this.doHurtTarget(level, target)) {
            rageHarassHitCooldownTicks = RAGE_HARASS_HIT_COOLDOWN;
            requestFlightTo(rageRetreatPoint);
            level.sendParticles(ParticleTypes.CRIT, target.getX(),
                    target.getY() + target.getBbHeight() * 0.50D, target.getZ(),
                    8, 0.25D, 0.20D, 0.25D, 0.04D);
        }
    }

    private int countCoordinatedRavens(
            ServerLevel level,
            LivingEntity target) {

        return level.getEntitiesOfClass(
                        RavenWolf.class,

                        target.getBoundingBox()
                                .inflate(
                                        RAGE_COORDINATION_RADIUS),

                        raven ->
                                raven.isAlive()
                                        && raven.isRavensRageActive()
                                        && (raven == this
                                        || this.isRavenPackmate(
                                        raven))
                                        && raven.isFocusedOnRageTarget(
                                        target))
                .size();
    }

    private boolean isFocusedOnRageTarget(
            LivingEntity target) {

        if (this.getTarget()
                == target) {

            return true;
        }

        LivingEntity priority =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .RAVEN_PRIORITY_THREAT
                                        .get())
                        .orElse(null);

        return priority
                == target;
    }

    // =====================================================================
    // Raven family
    // =====================================================================

    public boolean isRavenPackmate(
            RavenWolf other) {

        if (other == null
                || other == this
                || other.isTame()
                != this.isTame()) {

            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(
                this.getOwnerReference(),
                other.getOwnerReference());
    }

    public boolean isRavenFamilyMember(
            LivingEntity entity) {

        if (entity == null
                || !entity.isAlive()) {

            return false;
        }

        if (entity == this) {
            return true;
        }

        if (this.isTame()) {

            if (entity
                    == this.getOwner()) {

                return true;
            }

            return entity instanceof Wolf wolf
                    && wolf.isTame();
        }

        return entity instanceof RavenWolf raven
                && (raven == this
                || this.isRavenPackmate(
                raven));
    }

    // =====================================================================
    // Public state
    // =====================================================================

    public ItemStack getRavenCarriedLoot() {

        return carriedLoot;
    }

    public boolean hasRavenLootTarget() {

        return theftTargetPos != null;
    }

    public int getRavenRevengeTicks() {

        return revengeTicks;
    }

    // =====================================================================
// Natural spawning
// =====================================================================

    /**
     * Raven Wolf is a rare solitary highland scout.
     *
     * The biome modifier determines the allowed habitats.
     * This predicate validates the exact physical spawn point and prevents
     * persistent wild Ravens from accumulating too densely.
     */
    public static boolean checkRavenWolfSpawnRules(
            EntityType<RavenWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Spawn eggs, commands, breeding, etc. should not be blocked by
         * Raven's natural habitat restrictions.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {

            return true;
        }

        /*
         * Highland identity.
         */
        if (pos.getY() < 70) {
            return false;
        }

        /*
         * Raven must have dry, empty body space.
         */
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()) {

            return false;
        }

        BlockState ground =
                level.getBlockState(pos.below());

        /*
         * Windswept/peak biomes frequently expose stone, gravel and snow,
         * so relying only on vanilla wolf-ground tags would make Raven
         * unnecessarily difficult to spawn.
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
         * Raven is meant to feel like an uncommon solitary encounter.
         */
        if (level instanceof ServerLevel serverLevel) {

            int nearbyWild =
                    serverLevel.getEntitiesOfClass(
                                    RavenWolf.class,
                                    new AABB(pos).inflate(
                                            48.0D,
                                            24.0D,
                                            48.0D),
                                    raven ->
                                            raven.isAlive()
                                                    && !raven.isTame())
                            .size();

            if (nearbyWild >= 1) {
                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output) {

        super.addAdditionalSaveData(
                output);

        output.putBoolean(
                "RavenOwnerSitCommand",
                isRavenSitCommandActive());

        output.putInt(
                "RavenEyeCooldown",
                eyeOfRavenCooldownTicks);

        output.putInt(
                "RavenRageCooldown",
                ravensRageCooldownTicks);

        output.store(
                "RavenCarriedLoot",
                ItemStack.OPTIONAL_CODEC,
                carriedLoot);
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {

        super.readAdditionalSaveData(
                input);


        eyeOfRavenCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "RavenEyeCooldown",
                                0));

        ravensRageCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "RavenRageCooldown",
                                0));

        carriedLoot =
                input.read(
                                "RavenCarriedLoot",
                                ItemStack.OPTIONAL_CODEC)
                        .orElse(
                                ItemStack.EMPTY);

        eyeOfRavenTicks = 0;

        ravensRageTicks = 0;
        rageHarassHitCooldownTicks = 0;

        revengeOffenderUuid = null;
        revengeMobType = null;
        revengeTicks = 0;

        theftTargetPos = null;

        checkedContainers.clear();

        landingRequested = false;
        flightLanding = false;
        hasFlightMoveTarget = false;

        moveTargetPoint =
                this.position();

        /*
         * Runtime movement state never survives reload. Always rebuild from
         * the normal Wolf controller/navigation first.
         */
        this.navigation =
                this.groundNavigation;

        this.moveControl =
                this.groundMoveControl;

        this.entityData.set(
                DATA_RAVEN_FLYING,
                false);

        this.setNoGravity(
                false);

        ownerFlight = false;
        landingPoint = null;
        lastPathDestination = null;
        groundedFlightCooldown = 0;
        processedSitCommand = false;
        theftApproachStartedAt = -1L;
        rageRetreatPoint = null;
        rageCycleIndex = -1;
        this.entityData.set(DATA_RAVEN_LANDING, false);
        this.entityData.set(DATA_RAVEN_EYE_ACTIVE, false);
        this.entityData.set(DATA_RAVEN_RAGE_ACTIVE, false);
        this.setInSittingPose(this.isOrderedToSit() && this.onGround());
    }

    // =====================================================================
    // Exclusive movement ownership: sit, air, then ordinary ground follow
    // =====================================================================

    private final class RavenSitGoal extends Goal {
        private RavenSitGoal() { this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() { return isRavenSitCommandActive(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { cancelRavenActivityForSit(); }
        @Override public void tick() {
            if (RavenWolf.this.onGround()) hardStopGroundedSit();
            else {
                tickNormalLanding();
                updateFlightNavigation();
            }
        }
        @Override public void stop() { RavenWolf.this.setInSittingPose(false); }
    }

    private final class RavenFlightGoal extends Goal {
        private RavenFlightGoal() { this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() { return isRavenFlying() && !isRavenSitCommandActive(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() {
            RavenWolf.this.groundNavigation.stop();
            RavenWolf.this.setJumping(false);
        }
        @Override public void tick() { updateFlightNavigation(); }
    }

    private final class RavenFollowOwnerGoal extends Goal {
        private int repathTicks;
        private RavenFollowOwnerGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            LivingEntity owner = validFollowOwner();
            return canFollowOwner() && !isRavenFlying() && owner != null
                    && RavenWolf.this.distanceToSqr(owner) > 4.0D * 4.0D;
        }
        @Override public boolean canContinueToUse() {
            LivingEntity owner = validFollowOwner();
            return canFollowOwner() && !isRavenFlying() && owner != null
                    && RavenWolf.this.distanceToSqr(owner) > 2.5D * 2.5D;
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { repathTicks = 0; }
        @Override public void stop() { RavenWolf.this.groundNavigation.stop(); }
        @Override public void tick() {
            LivingEntity owner = validFollowOwner();
            if (owner == null || --repathTicks > 0) return;
            repathTicks = 10;
            double distance = RavenWolf.this.distanceToSqr(owner);
            if (distance >= ownerPreviousDistance - 0.25D) ownerStallTicks += 10;
            else ownerStallTicks = 0;
            ownerPreviousDistance = distance;
            if (distance >= 48.0D * 48.0D && ownerStallTicks >= 160
                    && RavenWolf.this.onGround() && owner.onGround()
                    && RavenWolf.this.tickCount - lastOwnerTeleportTick >= 400) {
                lastOwnerTeleportTick = RavenWolf.this.tickCount;
                RavenWolf.super.tryToTeleportToOwner();
                ownerStallTicks = 0;
                finishFlight();
                groundedFlightCooldown = 100;
                return;
            }
            if (groundedFlightCooldown <= 0 && (distance >= OWNER_FLIGHT_START_DISTANCE_SQR
                    || Math.abs(owner.getY() - RavenWolf.this.getY()) > 3.0D
                    || ownerStallTicks >= 40 && RavenWolf.this.groundNavigation.isDone())) {
                ownerFlight = true;
                requestFlightTo(owner.position().add(0.0D, 2.0D, 0.0D));
            } else {
                RavenWolf.this.groundNavigation.moveTo(owner, 1.15D);
            }
        }
    }

    // =====================================================================
    // LookControl
    // =====================================================================

    private static final class RavenLookControl
            extends LookControl {

        private final RavenWolf raven;

        private RavenLookControl(
                RavenWolf raven) {

            super(raven);

            this.raven =
                    raven;
        }

        @Override
        public void tick() {

            if (this.raven.entityData.get(
                    DATA_RAVEN_FLYING)) {

                return;
            }

            super.tick();
        }
    }

    // =====================================================================
    // BodyRotationControl
    // =====================================================================

    private static final class RavenBodyRotationControl
            extends BodyRotationControl {

        private final RavenWolf raven;

        private RavenBodyRotationControl(
                RavenWolf raven) {

            super(raven);

            this.raven =
                    raven;
        }

        @Override
        public void clientTick() {

            if (this.raven.entityData.get(
                    DATA_RAVEN_FLYING)) {

                this.raven.yHeadRot =
                        this.raven.yBodyRot;

                this.raven.yBodyRot =
                        this.raven.getYRot();

                return;
            }

            super.clientTick();
        }
    }

    // =====================================================================
    // Raven motor: powered strokes, coasting, arrival braking and local clearance
    // =====================================================================

    private static final class RavenMoveControl extends MoveControl {
        private final RavenWolf raven;
        private @Nullable Vec3 navigationWaypoint;

        private RavenMoveControl(RavenWolf raven) {
            super(raven);
            this.raven = raven;
        }

        private void reset() {
            this.operation = Operation.WAIT;
            this.navigationWaypoint = null;
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speedModifier) {
            super.setWantedPosition(x, y, z, speedModifier);
            // Navigation owns a waypoint, never the ability's final destination.
            this.navigationWaypoint = new Vec3(x, y, z);
        }

        @Override
        public void tick() {
            if (!raven.isRavenFlying() || !raven.hasFlightMoveTarget) return;
            Vec3 target = !raven.flyingNavigation.isDone() && navigationWaypoint != null
                    ? navigationWaypoint : raven.moveTargetPoint;
            if (raven.flightLanding && raven.touchingFlightTarget()) target = raven.moveTargetPoint;
            Vec3 error = target.subtract(raven.position());
            Vec3 old = raven.getDeltaMovement();
            double topSpeed = raven.flightLanding ? 0.26D : raven.isRavensRageActive() ? 0.65D
                    : raven.ownerFlight ? 0.58D : raven.isEyeOfRavenActive() ? 0.50D : 0.46D;
            var next = RavenFlightSteering.step(error.x, error.y, error.z,
                    old.x, old.y, old.z, topSpeed, raven.flightLanding,
                    raven.getRavenFlightPhase(0.0F) < 14.0F);
            Vec3 velocity = new Vec3(next.x(), next.y(), next.z());
            // Keep a clear body-sized corridor. Navigation handles longer
            // detours; this guard brakes before walls instead of bouncing 180°.
            if (!raven.flightLanding && !raven.isClearFlightPosition(raven.position().add(velocity.scale(2.0D)))) {
                Vec3 climb = new Vec3(velocity.x * 0.25D, Math.max(0.16D, velocity.y), velocity.z * 0.25D);
                velocity = raven.isClearFlightPosition(raven.position().add(climb.scale(2.0D)))
                        ? climb : new Vec3(0.0D, Math.min(velocity.y, 0.0D), 0.0D);
                raven.flightRepathTicks = 0;
            }
            raven.setDeltaMovement(velocity);
            raven.setXxa(0.0F);
            raven.setYya(0.0F);
            raven.setZza(0.0F);
            raven.setNoGravity(true);
            if (velocity.horizontalDistanceSqr() > 0.0004D) {
                float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * 180.0D / Math.PI) - 90.0F;
                raven.setYRot(Mth.approachDegrees(raven.getYRot(), yaw, 10.0F));
                raven.yBodyRot = raven.getYRot();
                raven.yHeadRot = raven.getYRot();
            }
            float pitch = (float) (-Mth.atan2(velocity.y, Math.max(0.08D, velocity.horizontalDistance())) * 180.0D / Math.PI);
            raven.setXRot(Mth.approachDegrees(raven.getXRot(), Mth.clamp(pitch, -25.0F, 25.0F), 4.0F));
        }
    }
}
