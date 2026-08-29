package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.BeeWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Bee Wolf #20: female pollination / honey utility support wolf.
 *
 * <p>Confirmed named kit: Bee Friend, Honey Scent, Pollination, Haste Aura.
 * This implementation keeps those four identities mechanically and visually
 * distinct while preserving Wolfism family safety and pack coordination.</p>
 */
public final class BeeWolf extends AbstractWolfismWolf implements FlyingAnimal {
    public static final double BEE_FRIEND_RADIUS = 8.0D;
    public static final double HONEY_SCENT_RANGE = 20.0D;
    public static final int HONEY_SCENT_VERTICAL_RANGE = 8;
    public static final double POLLINATION_RADIUS = 6.0D;
    public static final int POLLINATION_VERTICAL_RANGE = 2;
    public static final int POLLINATION_MAX_TARGETS = 4;
    private static final double POLLINATION_FLIGHT_SPEED = 0.65D;
    private static final double POLLINATION_HOVER_SPEED = 0.32D;
    private static final double POLLINATION_HOVER_HEIGHT = 1.45D;
    private static final double POLLINATION_ARRIVAL_DISTANCE = 0.85D;
    private static final int POLLINATION_HOVER_TICKS = 32;
    private static final double POLLINATION_OWNER_START_RANGE = 16.0D;
    public static final double HASTE_AURA_RADIUS = 8.0D;

    /** Owner catch-up rule: walk at <=10 blocks, fly at >10 blocks. */
    public static final double OWNER_FLIGHT_DISTANCE = 10.0D;
    private static final double OWNER_FLIGHT_DISTANCE_SQR = OWNER_FLIGHT_DISTANCE * OWNER_FLIGHT_DISTANCE;
    private static final double OWNER_FLIGHT_SPEED = 1.10D;
    private static final double OWNER_LANDING_SPEED = 0.45D;
    private static final double OWNER_GROUND_FOLLOW_SPEED = 1.05D;
    private static final double OWNER_GROUND_FOLLOW_START = 3.0D;
    private static final double OWNER_GROUND_FOLLOW_STOP = 2.0D;
    private static final double OWNER_FLIGHT_CRUISE_HEIGHT = 2.0D;
    private static final double OWNER_LANDING_OFFSET = 2.0D;

    /** Sitting is absolute unless Wolfism's family-defense protocol wakes her. */
    private static final int DEFENSE_OVERRIDE_TICKS = 20 * 30;
    private static final double DEFENSE_FLIGHT_DISTANCE = 6.0D;
    private static final double DEFENSE_FLIGHT_DISTANCE_SQR =
            DEFENSE_FLIGHT_DISTANCE * DEFENSE_FLIGHT_DISTANCE;
    private static final double DEFENSE_LANDING_DISTANCE = 3.0D;
    private static final double DEFENSE_LANDING_DISTANCE_SQR =
            DEFENSE_LANDING_DISTANCE * DEFENSE_LANDING_DISTANCE;
    private static final double DEFENSE_FLIGHT_SPEED = 1.45D;

    private static final EntityDataAccessor<Boolean> DATA_CATCH_UP_FLYING =
            SynchedEntityData.defineId(BeeWolf.class, EntityDataSerializers.BOOLEAN);

    public static final int POLLINATION_COOLDOWN_TICKS = 20 * 10;
    public static final int HASTE_AURA_COOLDOWN_TICKS = 20 * 12;

    private static final int HONEY_SCAN_INTERVAL = 80;
    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int HASTE_DURATION_TICKS = 20 * 8;

    private int abilityLockoutTicks;
    private int defenseOverrideTicks;
    private boolean resumeOrderedSitAfterDefense;

    // Pollination is a short aerial job, not an instant bonemeal burst.
    // Bee Wolf flies above up to four distinct immature crops, hovers long
    // enough to visibly pollinate each one, grows it, then lands.
    private BlockPos activePollinationTarget;
    private final List<BlockPos> pollinatedThisSequence = new ArrayList<>();
    private int pollinationHoverTicks;
    private int pollinationTargetsRemaining;
    private boolean pollinationLanding;

    // Keep the vanilla wolf ground controls intact and only swap to Bee-style
    // flight controls while the owner is farther than 10 blocks.
    private final MoveControl groundMoveControl;
    private final PathNavigation groundNavigation;
    private final FlyingMoveControl flyingMoveControl;
    private final FlyingPathNavigation flyingNavigation;

    private static final class BrainHolder {
        private static final Brain.Provider<BeeWolf> PROVIDER = Brain.<BeeWolf>provider(
                ImmutableList.of(ModSensorTypes.BEE_PACK.get()),
                wolf -> List.of());
    }

    public BeeWolf(EntityType<? extends BeeWolf> type, Level level) {
        super(type, level);
        this.groundMoveControl = this.moveControl;
        this.groundNavigation = this.navigation;
        this.flyingMoveControl = new FlyingMoveControl(this, 20, true);
        this.flyingNavigation = this.createBeeFlyingNavigation(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CATCH_UP_FLYING, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Defensive flight wins first. Owner catch-up is secondary. Both own
        // MOVE + LOOK so vanilla/normal wolf movement cannot fight the Bee
        // flight controller during an aerial phase.
        this.goalSelector.addGoal(0, new DefensiveFlightGoal());
        // Pollination is an intentional Bee-style aerial job and therefore
        // outranks ordinary owner following, but never family defense.
        this.goalSelector.addGoal(1, new PollinationFlightGoal());
        this.goalSelector.addGoal(2, new CatchUpToOwnerFlightGoal());
        // Explicitly own the <=10 block companion-follow band. This avoids the
        // flight controller/nav swap leaving Bee Wolf visually "awake" but
        // stationary until combat happens.
        this.goalSelector.addGoal(3, new GroundFollowOwnerGoal());
    }

    private FlyingPathNavigation createBeeFlyingNavigation(Level level) {
        FlyingPathNavigation flying = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }
        };
        flying.setCanOpenDoors(false);
        flying.setCanFloat(false);
        flying.setRequiredPathLength(48.0F);
        return flying;
    }

    public boolean isCatchUpFlying() {
        return this.entityData.get(DATA_CATCH_UP_FLYING);
    }

    private void enterCatchUpFlight() {
        if (this.isCatchUpFlying()) {
            return;
        }
        this.groundNavigation.stop();
        this.navigation = this.flyingNavigation;
        this.moveControl = this.flyingMoveControl;
        this.entityData.set(DATA_CATCH_UP_FLYING, true);

        // Match vanilla Bee behavior: let FlyingMoveControl generate the
        // takeoff velocity. Do NOT inject a vertical jump impulse here; that
        // was the source of the repeated bunny-hop look around mode changes.
        this.setNoGravity(true);
    }

    private void leaveCatchUpFlight() {
        if (!this.isCatchUpFlying()) {
            return;
        }
        this.flyingNavigation.stop();
        this.flyingNavigation.resetMaxVisitedNodesMultiplier();
        this.navigation = this.groundNavigation;
        this.moveControl = this.groundMoveControl;
        this.setNoGravity(false);
        this.entityData.set(DATA_CATCH_UP_FLYING, false);
    }

    @Override
    public boolean isFlying() {
        return this.isCatchUpFlying() && !this.onGround();
    }

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % Bee.TICKS_PER_FLAP == 0;
    }

    /**
     * During catch-up flight, use the Bee's air preference verbatim. Ground
     * mode keeps the normal wolf path value so she still behaves like a wolf
     * inside the owner's ten-block radius.
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (this.isCatchUpFlying()) {
            return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
        }
        return super.getWalkTargetValue(pos, level);
    }

    /** Bee-style flight safety: a winged Bee Wolf does not take fall damage. */
    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    private double horizontalDistanceToSqr(LivingEntity target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return dx * dx + dz * dz;
    }

    private Vec3 ownerCruiseTarget(LivingEntity owner) {
        // Altitude remains owner-relative, but we only ask the flight control
        // to correct a small amount of Y each tick. This makes takeoff/climb
        // smooth instead of snapping toward ownerY+2 and looking like jumps.
        double desiredY = owner.getY() + OWNER_FLIGHT_CRUISE_HEIGHT;
        double yStep = Mth.clamp(desiredY - this.getY(), -0.45D, 0.45D);
        return new Vec3(owner.getX(), this.getY() + yStep, owner.getZ());
    }

    private Vec3 ownerLandingTarget(LivingEntity owner) {
        double dx = this.getX() - owner.getX();
        double dz = this.getZ() - owner.getZ();
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLength < 1.0E-4D) {
            // Deterministic fallback: land two blocks behind the owner's look
            // direction instead of trying to occupy the owner's feet.
            Vec3 look = owner.getLookAngle();
            dx = -look.x;
            dz = -look.z;
            horizontalLength = Math.sqrt(dx * dx + dz * dz);
        }
        if (horizontalLength < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            horizontalLength = 1.0D;
        }
        dx /= horizontalLength;
        dz /= horizontalLength;
        double yStep = Mth.clamp(owner.getY() - this.getY(), -0.35D, 0.0D);
        return new Vec3(
                owner.getX() + dx * OWNER_LANDING_OFFSET,
                this.getY() + yStep,
                owner.getZ() + dz * OWNER_LANDING_OFFSET);
    }

    private void steerBeeFlight(Vec3 target, double speed) {
        // BeePollinateGoal steers hovering flight with FlyingMoveControl's
        // wanted position. We use the same Bee mechanism, but with a stable
        // target rather than AirRandomPos.
        this.flyingMoveControl.setWantedPosition(target.x(), target.y(), target.z(), speed);
    }

    /**
     * Bee-style crop pollination flight. The wolf flies to a stable hover point
     * above each immature crop, visibly pollinates it for a short moment, grows
     * it once, then proceeds to the next distinct crop. Defense can preempt this
     * at any time; sitting cancels it immediately.
     */
    private final class PollinationFlightGoal extends Goal {
        private PollinationFlightGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return BeeWolf.this.hasActivePollinationSequence()
                    && !BeeWolf.this.isBaby()
                    && !BeeWolf.this.isPassenger()
                    && !BeeWolf.this.isOrderedToSit()
                    && !BeeWolf.this.isInSittingPose()
                    && BeeWolf.this.getTarget() == null
                    && !BeeWolf.this.isDefensiveProtocolActive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse()
                    && (BeeWolf.this.activePollinationTarget != null
                        || (BeeWolf.this.pollinationLanding && !BeeWolf.this.onGround()));
        }

        @Override
        public void start() {
            BeeWolf.this.enterCatchUpFlight();
        }

        @Override
        public void stop() {
            BeeWolf.this.leaveCatchUpFlight();
            BeeWolf.this.clearPollinationSequence();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerLevel level = (ServerLevel) BeeWolf.this.level();

            if (BeeWolf.this.pollinationLanding) {
                LivingEntity owner = BeeWolf.this.getOwner();
                if (owner == null || !owner.isAlive()) {
                    BeeWolf.this.pollinationLanding = false;
                    return;
                }

                BeeWolf.this.setNoGravity(true);
                BeeWolf.this.getLookControl().setLookAt(owner, 25.0F, 25.0F);
                BeeWolf.this.steerBeeFlight(
                        BeeWolf.this.ownerLandingTarget(owner),
                        OWNER_LANDING_SPEED);
                if (BeeWolf.this.onGround()) {
                    BeeWolf.this.pollinationLanding = false;
                }
                return;
            }

            BlockPos crop = BeeWolf.this.activePollinationTarget;
            if (crop == null || !BeeWolf.this.isGrowableCrop(level, crop)) {
                BeeWolf.this.advancePollinationTarget(level);
                return;
            }

            Vec3 hover = new Vec3(
                    crop.getX() + 0.5D,
                    crop.getY() + POLLINATION_HOVER_HEIGHT,
                    crop.getZ() + 0.5D);

            BeeWolf.this.setNoGravity(true);
            BeeWolf.this.getLookControl().setLookAt(
                    crop.getX() + 0.5D,
                    crop.getY() + 0.45D,
                    crop.getZ() + 0.5D);

            double distanceSqr = BeeWolf.this.position().distanceToSqr(hover);
            if (distanceSqr > POLLINATION_ARRIVAL_DISTANCE * POLLINATION_ARRIVAL_DISTANCE) {
                BeeWolf.this.pollinationHoverTicks = 0;
                BeeWolf.this.steerBeeFlight(hover, POLLINATION_FLIGHT_SPEED);
                return;
            }

            // Hold a small, stable hover like a Bee pollinating a flower rather
            // than repeatedly jumping above the crop.
            BeeWolf.this.steerBeeFlight(hover, POLLINATION_HOVER_SPEED);
            ++BeeWolf.this.pollinationHoverTicks;

            if (BeeWolf.this.pollinationHoverTicks % 4 == 0) {
                level.sendParticles(
                        ParticleTypes.FALLING_NECTAR,
                        BeeWolf.this.getX(), BeeWolf.this.getY(0.35D), BeeWolf.this.getZ(),
                        3, 0.16D, 0.10D, 0.16D, 0.01D);
                level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        crop.getX() + 0.5D, crop.getY() + 0.70D, crop.getZ() + 0.5D,
                        2, 0.18D, 0.18D, 0.18D, 0.01D);
            }

            if (BeeWolf.this.pollinationHoverTicks >= POLLINATION_HOVER_TICKS) {
                BeeWolf.this.performPollinationGrowth(level, crop);
                BeeWolf.this.pollinatedThisSequence.add(crop.immutable());
                --BeeWolf.this.pollinationTargetsRemaining;
                BeeWolf.this.pollinationHoverTicks = 0;
                BeeWolf.this.advancePollinationTarget(level);
            }
        }
    }

    private final class CatchUpToOwnerFlightGoal extends Goal {
        private LivingEntity owner;

        private CatchUpToOwnerFlightGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!BeeWolf.this.isTame()
                    || BeeWolf.this.isOrderedToSit()
                    || BeeWolf.this.isInSittingPose()
                    || BeeWolf.this.isPassenger()
                    || BeeWolf.this.getTarget() != null
                    || BeeWolf.this.isDefensiveProtocolActive()) {
                return false;
            }

            LivingEntity candidate = BeeWolf.this.getOwner();
            if (candidate == null || !candidate.isAlive()) {
                return false;
            }

            this.owner = candidate;
            // The 10-block rule is horizontal. Being high above the owner must
            // never count as a reason to keep climbing.
            return BeeWolf.this.horizontalDistanceToSqr(candidate) > OWNER_FLIGHT_DISTANCE_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.owner == null
                    || !this.owner.isAlive()
                    || !BeeWolf.this.isTame()
                    || BeeWolf.this.isOrderedToSit()
                    || BeeWolf.this.isInSittingPose()
                    || BeeWolf.this.isPassenger()
                    || BeeWolf.this.getTarget() != null
                    || BeeWolf.this.isDefensiveProtocolActive()) {
                return false;
            }

            // Once we cross back into ten blocks, remain in this goal only long
            // enough to perform a controlled Bee-style descent.
            return BeeWolf.this.horizontalDistanceToSqr(this.owner) > OWNER_FLIGHT_DISTANCE_SQR
                    || (BeeWolf.this.isCatchUpFlying() && !BeeWolf.this.onGround());
        }

        @Override
        public void start() {
            BeeWolf.this.enterCatchUpFlight();
        }

        @Override
        public void stop() {
            BeeWolf.this.leaveCatchUpFlight();
            this.owner = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }

            BeeWolf.this.setNoGravity(true);
            BeeWolf.this.getLookControl().setLookAt(this.owner, 30.0F, 30.0F);

            double horizontalDistanceSqr = BeeWolf.this.horizontalDistanceToSqr(this.owner);
            if (horizontalDistanceSqr > OWNER_FLIGHT_DISTANCE_SQR) {
                // Direct Bee flight toward a Y target clamped to owner altitude.
                // No random air nodes: this is the fix for the space-program bug.
                BeeWolf.this.steerBeeFlight(
                        BeeWolf.this.ownerCruiseTarget(this.owner),
                        OWNER_FLIGHT_SPEED);
            } else {
                // Inside ten blocks: descend beside the owner, then hand back to
                // the normal wolf controller as soon as the paws touch ground.
                BeeWolf.this.steerBeeFlight(
                        BeeWolf.this.ownerLandingTarget(this.owner),
                        OWNER_LANDING_SPEED);
            }
        }
    }

    /**
     * Normal companion movement inside the ten-block flight threshold. This is
     * deliberately ground-only and stops before the wolf reaches the owner's
     * feet, matching normal wolf companionship rather than constant hovering.
     */
    private final class GroundFollowOwnerGoal extends Goal {
        private LivingEntity owner;
        private int repathTicks;

        private GroundFollowOwnerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!BeeWolf.this.isTame()
                    || BeeWolf.this.isCatchUpFlying()
                    || BeeWolf.this.isOrderedToSit()
                    || BeeWolf.this.isInSittingPose()
                    || BeeWolf.this.isPassenger()
                    || BeeWolf.this.getTarget() != null
                    || BeeWolf.this.isDefensiveProtocolActive()) {
                return false;
            }
            LivingEntity candidate = BeeWolf.this.getOwner();
            if (candidate == null || !candidate.isAlive()) {
                return false;
            }
            double horizontal = BeeWolf.this.horizontalDistanceToSqr(candidate);
            if (horizontal > OWNER_FLIGHT_DISTANCE_SQR
                    || horizontal <= OWNER_GROUND_FOLLOW_START * OWNER_GROUND_FOLLOW_START) {
                return false;
            }
            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.owner == null
                    || !this.owner.isAlive()
                    || BeeWolf.this.isCatchUpFlying()
                    || BeeWolf.this.isOrderedToSit()
                    || BeeWolf.this.isInSittingPose()
                    || BeeWolf.this.isPassenger()
                    || BeeWolf.this.getTarget() != null
                    || BeeWolf.this.isDefensiveProtocolActive()) {
                return false;
            }
            double horizontal = BeeWolf.this.horizontalDistanceToSqr(this.owner);
            return horizontal <= OWNER_FLIGHT_DISTANCE_SQR
                    && horizontal > OWNER_GROUND_FOLLOW_STOP * OWNER_GROUND_FOLLOW_STOP;
        }

        @Override
        public void start() {
            this.repathTicks = 0;
            BeeWolf.this.leaveCatchUpFlight();
        }

        @Override
        public void stop() {
            BeeWolf.this.groundNavigation.stop();
            this.owner = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            BeeWolf.this.getLookControl().setLookAt(this.owner, 20.0F, 20.0F);
            if (--this.repathTicks <= 0) {
                this.repathTicks = 8;
                BeeWolf.this.groundNavigation.moveTo(this.owner, OWNER_GROUND_FOLLOW_SPEED);
            }
        }
    }

    /**
     * Sitting Bee Wolves are statues unless Wolfism's family-defense protocol
     * has explicitly been triggered. If a protected family member is attacked,
     * a previously sitting Bee Wolf may temporarily wake, fly to a distant
     * threat, fight normally, then return to her ordered sitting state.
     */
    private final class DefensiveFlightGoal extends Goal {
        private LivingEntity threat;

        private DefensiveFlightGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!BeeWolf.this.isDefensiveProtocolActive() || BeeWolf.this.isPassenger()) {
                return false;
            }
            LivingEntity candidate = BeeWolf.this.getTarget();
            if (candidate == null || !BeeWolf.this.isValidBeeCombatTarget(candidate)) {
                return false;
            }
            this.threat = candidate;
            return BeeWolf.this.distanceToSqr(candidate) > DEFENSE_FLIGHT_DISTANCE_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            return this.threat != null
                    && this.threat.isAlive()
                    && BeeWolf.this.isDefensiveProtocolActive()
                    && BeeWolf.this.isValidBeeCombatTarget(this.threat)
                    && (BeeWolf.this.distanceToSqr(this.threat) > DEFENSE_LANDING_DISTANCE_SQR
                        || (BeeWolf.this.isCatchUpFlying() && !BeeWolf.this.onGround()));
        }

        @Override
        public void start() {
            BeeWolf.this.enterCatchUpFlight();
        }

        @Override
        public void stop() {
            BeeWolf.this.leaveCatchUpFlight();
            this.threat = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.threat == null) {
                return;
            }

            BeeWolf.this.setNoGravity(true);
            BeeWolf.this.getLookControl().setLookAt(this.threat, 35.0F, 35.0F);

            double distanceSqr = BeeWolf.this.distanceToSqr(this.threat);
            double desiredY = distanceSqr > DEFENSE_FLIGHT_DISTANCE_SQR
                    ? this.threat.getY() + Math.min(1.5D, this.threat.getBbHeight() * 0.75D)
                    : this.threat.getY();
            double yStep = Mth.clamp(desiredY - BeeWolf.this.getY(), -0.45D, 0.45D);

            BeeWolf.this.steerBeeFlight(
                    new Vec3(this.threat.getX(), BeeWolf.this.getY() + yStep, this.threat.getZ()),
                    distanceSqr > DEFENSE_FLIGHT_DISTANCE_SQR
                            ? DEFENSE_FLIGHT_SPEED
                            : OWNER_LANDING_SPEED);
        }
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.BEE_WOLF.get();
    }

    @Override
    protected Brain<BeeWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<BeeWolf> getBrain() {
        return (Brain<BeeWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof Bee
                || (current instanceof BeeWolf beeWolf && this.isBeePackmate(beeWolf))) {
            // Bee Friend is literal: Bee Wolf never retaliates against vanilla
            // bees. Any bee anger is handled by the diplomacy passive below.
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.BEE_POLLINATION_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.BEE_HASTE_AURA_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.BEE_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidBeeCombatTarget(sharedThreat)) {
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

        this.tickDefenseOverride();

        // Keep the logical sit command and the synced visual sitting pose in
        // lockstep. The prior flight patch could leave one true and the other
        // false, which produced a standing statue that would only animate once
        // combat forced movement.
        if (!this.isDefensiveProtocolActive()) {
            if (this.isOrderedToSit()) {
                if (this.isCatchUpFlying()) {
                    this.leaveCatchUpFlight();
                }
                this.groundNavigation.stop();
                this.setInSittingPose(true);
                // A sitting Bee Wolf must not silently inherit an ordinary
                // combat target. Family defense explicitly releases the sit
                // command before combat, so any target left here is stale.
                if (this.getTarget() != null) {
                    this.setTarget(null);
                }
                // Kill horizontal drift without cancelling normal vertical
                // gravity/falling if the wolf was commanded to sit mid-air.
                Vec3 motion = this.getDeltaMovement();
                if (Math.abs(motion.x) > 1.0E-4D || Math.abs(motion.z) > 1.0E-4D) {
                    this.setDeltaMovement(0.0D, motion.y, 0.0D);
                    this.hurtMarked = true;
                }
            } else if (this.isInSittingPose()) {
                // Right-click stand must release the render pose immediately;
                // do not wait for a vanilla goal to repair stale state.
                this.setInSittingPose(false);
            }
        }

        if (this.isCatchUpFlying()
                && (this.isPassenger()
                || (!this.isDefensiveProtocolActive()
                    && (!this.isTame()
                        || this.isOrderedToSit()
                        || this.isInSittingPose()
                        || this.getOwner() == null
                        || !this.getOwner().isAlive()
                        || this.getTarget() != null)))) {
            this.leaveCatchUpFlight();
        }

        if (this.isBaby()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get());
            return;
        }

        // Bee Friend is a true species passive and does not consume the active slot.
        // Run it frequently enough to calm a bee before normal wolf retaliation
        // can turn the interaction into a kill.
        if (this.tickCount % 5 == 0) {
            this.tickBeeFriend(level);
        }

        if (this.isTame()
                && this.tickCount % HONEY_SCAN_INTERVAL == Math.floorMod(this.getId(), HONEY_SCAN_INTERVAL)) {
            this.updateHoneyScent(level);
        }

        if (this.tickCount % 20 == 0) {
            this.tickHoneyScentVisual(level);
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            if (!this.tryPollination(level)) {
                this.tryHasteAura(level);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 1. Bee Friend (passive bee diplomacy)
    // ---------------------------------------------------------------------

    private void tickBeeFriend(ServerLevel level) {
        for (Bee bee : level.getEntitiesOfClass(
                Bee.class,
                this.getBoundingBox().inflate(BEE_FRIEND_RADIUS),
                candidate -> candidate.isAlive())) {
            LivingEntity beeTarget = bee.getTarget();
            boolean protectingFamily = beeTarget != null && this.isBeeFamilyMember(beeTarget);

            // A Bee Wolf's presence calms bees that are actively threatening its
            // family. We intentionally do not hijack neutral bees into combat,
            // because vanilla bees pay for a sting with their life.
            if (protectingFamily) {
                bee.setTarget(null);
                bee.stopBeingAngry();
                level.sendParticles(
                        ParticleTypes.HEART,
                        bee.getX(), bee.getY(0.65D), bee.getZ(),
                        3, 0.18D, 0.18D, 0.18D, 0.02D);
                level.sendParticles(
                        ParticleTypes.FALLING_NECTAR,
                        bee.getX(), bee.getY(0.55D), bee.getZ(),
                        4, 0.20D, 0.16D, 0.20D, 0.01D);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 2. Honey Scent (owner-facing hive/honey tracker)
    // ---------------------------------------------------------------------

    private void updateHoneyScent(ServerLevel level) {
        ServerPlayer owner = this.getBeeOwner();
        if (owner == null || owner.distanceToSqr(this) > 24.0D * 24.0D) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get());
            return;
        }

        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int horizontal = (int) HONEY_SCENT_RANGE;

        for (int dx = -horizontal; dx <= horizontal; ++dx) {
            for (int dy = -HONEY_SCENT_VERTICAL_RANGE; dy <= HONEY_SCENT_VERTICAL_RANGE; ++dy) {
                for (int dz = -horizontal; dz <= horizontal; ++dz) {
                    if ((double) (dx * dx + dz * dz) > HONEY_SCENT_RANGE * HONEY_SCENT_RANGE) {
                        continue;
                    }

                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isHoneyResource(level.getBlockState(candidate))) {
                        continue;
                    }

                    double distance = candidate.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }

        if (best == null) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get());
        } else {
            this.getBrain().setMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get(), best);
        }
    }

    private void tickHoneyScentVisual(ServerLevel level) {
        BlockPos target = this.getBrain()
                .getMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get())
                .orElse(null);
        ServerPlayer owner = this.getBeeOwner();
        if (target == null || owner == null) {
            return;
        }

        if (owner.distanceToSqr(this) > 24.0D * 24.0D
                || target.distSqr(this.blockPosition()) > HONEY_SCENT_RANGE * HONEY_SCENT_RANGE
                || !isHoneyResource(level.getBlockState(target))) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get());
            return;
        }

        this.sendDirectionalTrace(
                level,
                this.position().add(0.0D, 0.65D, 0.0D),
                target,
                3.2D,
                9,
                ParticleTypes.FALLING_HONEY);

        if (owner.distanceToSqr(this) <= 16.0D * 16.0D) {
            this.sendDirectionalTrace(
                    level,
                    owner.position().add(0.0D, 1.05D, 0.0D),
                    target,
                    2.4D,
                    7,
                    ParticleTypes.FALLING_NECTAR);

            Block block = level.getBlockState(target).getBlock();
            int horizontalDistance = (int) Math.round(Math.sqrt(
                    owner.blockPosition().distSqr(new BlockPos(
                            target.getX(), owner.blockPosition().getY(), target.getZ()))));
            int vertical = target.getY() - owner.blockPosition().getY();
            String verticalText = vertical == 0 ? "LEVEL" : vertical > 0 ? "UP " + vertical : "DOWN " + (-vertical);
            owner.sendOverlayMessage(
                    Component.literal("Honey Scent ◆ " + block.getName().getString()
                            + " ◆ " + horizontalDistance + " blocks ◆ " + verticalText));
        }
    }

    private static boolean isHoneyResource(BlockState state) {
        return state.is(BlockTags.BEEHIVES)
                || state.is(Blocks.HONEY_BLOCK)
                || state.is(Blocks.HONEYCOMB_BLOCK);
    }

    // ---------------------------------------------------------------------
    // 3. Pollination (bounded crop growth burst)
    // ---------------------------------------------------------------------

    private boolean tryPollination(ServerLevel level) {
        if (!this.canUseActiveBeeAbility()
                || !this.isTame()
                || this.hasActivePollinationSequence()
                || this.getTarget() != null
                || this.isDefensiveProtocolActive()
                || this.isCoolingDown(ModMemoryModuleTypes.BEE_POLLINATION_COOLDOWN.get())) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.distanceToSqr(this) > POLLINATION_OWNER_START_RANGE * POLLINATION_OWNER_START_RANGE) {
            return false;
        }

        List<BlockPos> growable = this.findGrowableCropPositions(level);
        if (growable.isEmpty()) {
            return false;
        }

        growable.sort(Comparator.comparingDouble(pos -> pos.distSqr(this.blockPosition())));
        this.pollinatedThisSequence.clear();
        this.pollinationTargetsRemaining = Math.min(POLLINATION_MAX_TARGETS, growable.size());
        this.pollinationHoverTicks = 0;
        this.pollinationLanding = false;
        this.activePollinationTarget = growable.get(0).immutable();

        // Reserve the pack ability immediately so several Bee Wolves do not all
        // launch at the same crop patch on the same decision tick.
        this.setPackCooldown(
                ModMemoryModuleTypes.BEE_POLLINATION_COOLDOWN.get(),
                POLLINATION_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(POLLINATION_HOVER_TICKS * POLLINATION_MAX_TARGETS + 40);
        return true;
    }

    private boolean hasActivePollinationSequence() {
        return this.activePollinationTarget != null || this.pollinationLanding;
    }

    private void clearPollinationSequence() {
        this.activePollinationTarget = null;
        this.pollinationLanding = false;
        this.pollinationHoverTicks = 0;
        this.pollinationTargetsRemaining = 0;
        this.pollinatedThisSequence.clear();
    }

    private void advancePollinationTarget(ServerLevel level) {
        if (this.pollinationTargetsRemaining <= 0) {
            this.finishPollinationSequence(level);
            return;
        }

        List<BlockPos> candidates = this.findGrowableCropPositions(level);
        candidates.removeIf(this.pollinatedThisSequence::contains);
        if (candidates.isEmpty()) {
            this.finishPollinationSequence(level);
            return;
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(this.blockPosition())));
        this.activePollinationTarget = candidates.get(0).immutable();
        this.pollinationHoverTicks = 0;
    }

    private void finishPollinationSequence(ServerLevel level) {
        this.activePollinationTarget = null;
        this.pollinationHoverTicks = 0;
        this.pollinationTargetsRemaining = 0;
        this.pollinationLanding = true;
        this.sendRing(level, this.position(), 2.2D, 26, ParticleTypes.FALLING_NECTAR);
    }

    private boolean isGrowableCrop(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.BEE_GROWABLES)
                && state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(level, pos, state);
    }

    private boolean performPollinationGrowth(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEE_GROWABLES)
                || !(state.getBlock() instanceof BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(level, pos, state)
                || !bonemealable.isBonemealSuccess(level, this.random, pos, state)) {
            return false;
        }

        bonemealable.performBonemeal(level, this.random, pos, state);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5D, pos.getY() + 0.65D, pos.getZ() + 0.5D,
                10, 0.24D, 0.30D, 0.24D, 0.04D);
        level.sendParticles(
                ParticleTypes.FALLING_NECTAR,
                pos.getX() + 0.5D, pos.getY() + 0.90D, pos.getZ() + 0.5D,
                8, 0.20D, 0.25D, 0.20D, 0.02D);
        return true;
    }

    private List<BlockPos> findGrowableCropPositions(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        int horizontal = (int) POLLINATION_RADIUS;
        List<BlockPos> result = new ArrayList<>();

        for (int dx = -horizontal; dx <= horizontal; ++dx) {
            for (int dy = -POLLINATION_VERTICAL_RANGE; dy <= POLLINATION_VERTICAL_RANGE; ++dy) {
                for (int dz = -horizontal; dz <= horizontal; ++dz) {
                    if ((double) (dx * dx + dz * dz) > POLLINATION_RADIUS * POLLINATION_RADIUS) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (this.isGrowableCrop(level, pos)) {
                        result.add(pos.immutable());
                    }
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // 4. Haste Aura (temporary family utility pulse)
    // ---------------------------------------------------------------------

    private boolean tryHasteAura(ServerLevel level) {
        if (!this.canUseActiveBeeAbility()
                || !this.isTame()
                || this.isCoolingDown(ModMemoryModuleTypes.BEE_HASTE_AURA_COOLDOWN.get())) {
            return false;
        }

        List<LivingEntity> family = this.findFamily(HASTE_AURA_RADIUS).stream()
                .filter(candidate -> candidate != this)
                .toList();
        if (family.isEmpty()) {
            return false;
        }

        boolean useful = family.stream().anyMatch(candidate -> {
            MobEffectInstance current = candidate.getEffect(MobEffects.HASTE);
            return current == null || current.getDuration() < 20 * 3;
        });
        if (!useful) {
            return false;
        }

        this.setPackCooldown(ModMemoryModuleTypes.BEE_HASTE_AURA_COOLDOWN.get(), HASTE_AURA_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(24);

        this.sendRing(level, this.position(), 2.0D, 24, ParticleTypes.WAX_ON);
        this.sendRing(level, this.position(), 4.5D, 36, ParticleTypes.FALLING_HONEY);
        this.sendRing(level, this.position(), HASTE_AURA_RADIUS, 48, ParticleTypes.FALLING_NECTAR);

        for (LivingEntity ally : family) {
            ally.addEffect(new MobEffectInstance(MobEffects.HASTE, HASTE_DURATION_TICKS, 0, true, true), this);
            level.sendParticles(
                    ParticleTypes.WAX_ON,
                    ally.getX(), ally.getY(0.55D), ally.getZ(),
                    8, 0.24D, 0.28D, 0.24D, 0.03D);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby() || target instanceof Bee) {
            return false;
        }
        if (target instanceof BeeWolf beeWolf && this.isBeePackmate(beeWolf)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isBeePackmate(BeeWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isBeeFamilyMember(LivingEntity entity) {
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
            // Wolfism family rule: all tamed wolves recognize each other as family.
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof BeeWolf beeWolf && this.isBeePackmate(beeWolf);
    }

    public boolean isValidBeeCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof BeeWolf beeWolf && this.isBeePackmate(beeWolf)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private List<LivingEntity> findFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isBeeFamilyMember);
    }

    private ServerPlayer getBeeOwner() {
        return this.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    private boolean canUseActiveBeeAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.hasActivePollinationSequence()
                && this.abilityLockoutTicks <= 0;
    }

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

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }
        for (BeeWolf mate : level.getEntitiesOfClass(
                BeeWolf.class,
                this.getBoundingBox().inflate(BeeWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isBeePackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void applyPackAbilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, ticks);
            return;
        }
        for (BeeWolf mate : level.getEntitiesOfClass(
                BeeWolf.class,
                this.getBoundingBox().inflate(BeeWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isBeePackmate(wolf)))) {
            mate.abilityLockoutTicks = Math.max(mate.abilityLockoutTicks, ticks);
        }
    }

    private boolean isDefensiveProtocolActive() {
        LivingEntity threat = this.getTarget();
        return this.defenseOverrideTicks > 0
                && threat != null
                && threat.isAlive()
                && this.isValidBeeCombatTarget(threat);
    }

    private void beginDefenseOverride() {
        this.defenseOverrideTicks = DEFENSE_OVERRIDE_TICKS;
        if (this.isOrderedToSit()) {
            this.resumeOrderedSitAfterDefense = true;
            // Preserve the player's command logically, but temporarily release
            // the vanilla sitting goal so the defensive protocol may move.
            this.setOrderedToSit(false);
            this.setInSittingPose(false);
        }
    }

    private void tickDefenseOverride() {
        if (this.defenseOverrideTicks <= 0) {
            return;
        }

        LivingEntity threat = this.getTarget();
        if (threat != null && threat.isAlive() && this.isValidBeeCombatTarget(threat)) {
            // Defense remains authorized for as long as the real threat exists.
            // Refresh the guard instead of timing out mid-fight.
            this.defenseOverrideTicks = DEFENSE_OVERRIDE_TICKS;
            return;
        }

        this.defenseOverrideTicks = 0;
        if (this.resumeOrderedSitAfterDefense) {
            this.resumeOrderedSitAfterDefense = false;
            if (this.isCatchUpFlying()) {
                this.leaveCatchUpFlight();
            }
            this.navigation.stop();
            this.setOrderedToSit(true);
            this.setInSittingPose(true);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidBeeCombatTarget(threat)) {
            return;
        }
        for (BeeWolf mate : level.getEntitiesOfClass(
                BeeWolf.class,
                this.getBoundingBox().inflate(BeeWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isBeePackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.BEE_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidBeeCombatTarget(threat)) {
                mate.beginDefenseOverride();
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (attacker instanceof Bee bee) {
            // Bee Friend resolves bee aggression diplomatically. Do not wake a
            // sitting Bee Wolf or begin a lethal retaliation against a bee.
            bee.setTarget(null);
            bee.stopBeingAngry();
            return;
        }
        if (attacker != null && attacker.isAlive()) {
            this.beginDefenseOverride();
            this.alertPackToThreat(attacker);
        }
    }

    private void sendDirectionalTrace(
            ServerLevel level,
            Vec3 source,
            BlockPos target,
            double length,
            int points,
            ParticleOptions particle) {
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 delta = targetCenter.subtract(source);
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 direction = delta.normalize();
        for (int i = 1; i <= points; ++i) {
            double step = length * i / points;
            Vec3 point = source.add(direction.scale(step));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
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
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.25D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.02D, 0.03D, 0.02D, 0.0D);
        }
    }

    public static boolean checkBeeWolfSpawnRules(
            EntityType<BeeWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());
        return ground.is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }
}
