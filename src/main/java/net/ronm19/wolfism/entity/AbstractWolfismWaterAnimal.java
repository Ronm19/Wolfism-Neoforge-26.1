package net.ronm19.wolfism.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared amphibious foundation for Wolfism's water-capable wolves.
 *
 * <p>This class intentionally keeps {@link AbstractWolfismWolf} as its parent so
 * aquatic wolves retain vanilla Wolf taming, sitting, armor, breeding, owner
 * logic and Wolfism family combat.</p>
 *
 * <p>The water locomotion itself is deliberately vanilla-driven. Instead of a
 * custom "smooth" controller, this class uses the same proven architecture as
 * vanilla amphibious/aquatic mobs:</p>
 *
 * <ul>
 *     <li>a normal {@link GroundPathNavigation} for land,</li>
 *     <li>a {@link WaterBoundPathNavigation} for real 3D water paths,</li>
 *     <li>a Drowned-style {@link MoveControl} while submerged,</li>
 *     <li>Drowned/AbstractFish-style water travel and drag,</li>
 *     <li>WaterAnimal-style water pathfinding and fluid resistance.</li>
 * </ul>
 *
 * <p>One deliberate wolf-specific exception is important: this class never puts
 * the entity into Minecraft's generic SWIMMING pose. Vanilla aquatic/humanoid
 * models are authored for that pose; the vanilla Wolf model is not. The wolf
 * still swims physically in full XYZ space while keeping its normal quadruped
 * body pose.</p>
 */
public abstract class AbstractWolfismWaterAnimal extends AbstractWolfismWolf {
    /** Global underwater locomotion boost for all Wolfism aquatic wolves. */
    private static final double AQUATIC_SPEED_MULTIPLIER = 1.45D;

    private static final double OWNER_FOLLOW_START_DISTANCE_SQR = 12.25D; // 3.5 blocks
    private static final double OWNER_FOLLOW_STOP_DISTANCE_SQR = 6.25D;   // 2.5 blocks
    private static final double OWNER_FOLLOW_SPEED = 1.00D;
    private static final double OWNER_CATCHUP_SPEED = 1.25D;
    private static final int OWNER_REPATH_INTERVAL_TICKS = 10;

    private final GroundPathNavigation groundNavigation;
    private final WaterBoundPathNavigation waterNavigation;

    /**
     * Short-lived intent marker used only to decide which vanilla navigation
     * should currently own the entity. It does not implement movement physics.
     */
    private int aquaticMoveRequestExpiryTick = Integer.MIN_VALUE;
    private int ownerRepathCooldown;

    protected AbstractWolfismWaterAnimal(
            EntityType<? extends AbstractWolfismWaterAnimal> type,
            Level level) {
        super(type, level);

        /*
         * Mob constructed the navigation returned by createNavigation() during
         * super construction. Keep that exact GroundPathNavigation as our land
         * navigator, then create the vanilla water navigator beside it.
         */
        this.groundNavigation = (GroundPathNavigation) this.getNavigation();
        this.groundNavigation.setCanFloat(true);

        /*
         * IMPORTANT: do NOT call setCanFloat(true) on WaterBoundPathNavigation.
         * For water navigation that flag enables breaching / leaving the water
         * surface. Fish and Dolphin leave it disabled, and so do we.
         */
        this.waterNavigation = new WaterBoundPathNavigation(this, level);

        this.setPathfindingMalus(PathType.WATER, 0.0F);

        /*
         * This is a direct adaptation of vanilla Drowned's amphibious
         * MoveControl. No custom pitch rotation, approach-factor acceleration,
         * or hand-written smoothing layer is used.
         */
        this.moveControl = new VanillaAquaticMoveControl(this);
    }

    // ---------------------------------------------------------------------
    // Goal cleanup inherited from vanilla Wolf
    // ---------------------------------------------------------------------

    /**
     * Vanilla Wolf installs FloatGoal. That goal is correct for a land animal
     * that merely needs to keep its head above water, but it is wrong for a
     * true aquatic Wolfism species: while the mob is in water it repeatedly
     * asks JumpControl to jump, which produces the constant surface-hopping
     * behavior seen during testing.
     *
     * <p>Remove only FloatGoal. Ordinary land jumping remains available through
     * vanilla MoveControl/JumpControl, so the wolf can still step and jump over
     * real obstacles when it is on land.</p>
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> goal instanceof FloatGoal);
    }

    // ---------------------------------------------------------------------
    // Vanilla land/water navigation split
    // ---------------------------------------------------------------------

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AquaticGroundPathNavigation(this, level);
    }

    /** Keeps a shore route's water waypoints at the height used to advance it. */
    private static final class AquaticGroundPathNavigation extends GroundPathNavigation {
        private static final double SHORE_FLOOR_CLEARANCE = 1.0D / 16.0D;

        private AquaticGroundPathNavigation(AbstractWolfismWaterAnimal wolf, Level level) {
            super(wolf, level);
        }

        @Override
        protected double getGroundY(Vec3 target) {
            if (this.mob.isInWater()) {
                BlockPos support = BlockPos.containing(target).below();
                var supportState = this.level.getBlockState(support);
                var supportShape = supportState.getCollisionShape(this.level, support);
                if (supportState.getFluidState().is(FluidTags.WATER)
                        && supportShape.isEmpty()) {
                    // Water provides no ground collision floor. The ordinary floor
                    // adjustment aims one block below the path node; an aquatic
                    // controller can settle there and never enter its <1 Y tolerance.
                    // Preserve the node height, as WaterBoundPathNavigation does.
                    return target.y;
                }
                if (!supportShape.isEmpty()) {
                    // Aim just above a real ledge while swimming toward shore.
                    // Converging on its exact top can leave the paws fractionally
                    // below it, where clipped movement falls under Entity's tiny-
                    // movement cutoff. Keep partial-block collision heights intact.
                    return super.getGroundY(target) + SHORE_FLOOR_CLEARANCE;
                }
            }
            return super.getGroundY(target);
        }
    }

    private void useWaterNavigation() {
        if (this.navigation != this.waterNavigation) {
            this.navigation.stop();
            this.navigation = this.waterNavigation;
        }
    }

    private void useGroundNavigation() {
        if (this.navigation != this.groundNavigation) {
            this.navigation.stop();
            this.navigation = this.groundNavigation;
        }
    }

    /**
     * Aquatic goals call this instead of manipulating MoveControl directly.
     * The destination is handed to vanilla path navigation, which in turn feeds
     * ordinary MOVE_TO waypoints into the vanilla-derived aquatic MoveControl.
     */
    public final void moveToWaterAware(
            double x,
            double y,
            double z,
            double speedModifier) {
        if (this.isInWater()) {
            this.aquaticMoveRequestExpiryTick = this.tickCount + 20;
            this.useWaterNavigation();
            this.waterNavigation.moveTo(x, y, z, speedModifier);
            return;
        }

        this.aquaticMoveRequestExpiryTick = Integer.MIN_VALUE;
        this.useGroundNavigation();
        this.groundNavigation.moveTo(x, y, z, speedModifier);
    }

    public final void moveToWaterAware(
            LivingEntity target,
            double speedModifier) {
        if (target == null) {
            return;
        }

        if (this.isInWater() && target.isInWater()) {
            this.aquaticMoveRequestExpiryTick = this.tickCount + 20;
            this.useWaterNavigation();
            this.waterNavigation.moveTo(target, speedModifier);
            return;
        }

        /*
         * If the owner/target has reached shore, let vanilla ground navigation
         * solve the water-to-land transition rather than forcing a water path to
         * a dry destination.
         */
        this.aquaticMoveRequestExpiryTick = Integer.MIN_VALUE;
        this.useGroundNavigation();
        this.groundNavigation.moveTo(target, speedModifier);
    }

    public final void stopWaterAwareMovement() {
        this.aquaticMoveRequestExpiryTick = Integer.MIN_VALUE;
        this.waterNavigation.stop();
        this.groundNavigation.stop();
        this.setSpeed(0.0F);
    }

    protected boolean usesSharedUnderwaterOwnerFollowing() {
        return true;
    }

    /** Idle aquatic wandering yields to the existing owner-follow distance. */
    public final boolean shouldPrioritizeAquaticOwnerFollowing() {
        LivingEntity owner = this.getOwner();
        return this.isTame()
                && owner != null
                && owner.isAlive()
                && owner.level() == this.level()
                && this.distanceToSqr(owner) >= OWNER_FOLLOW_START_DISTANCE_SQR;
    }

    /**
     * Species such as Drowned Wolf can expose additional vanilla-style aquatic
     * intent (for example "searching for land") without implementing movement
     * themselves.
     */
    protected boolean hasSpeciesWaterMovementIntent() {
        return false;
    }

    protected final boolean hasRecentAquaticMoveRequest() {
        return this.tickCount <= this.aquaticMoveRequestExpiryTick;
    }

    // ---------------------------------------------------------------------
    // Shared underwater owner following
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        if (this.usesSharedUnderwaterOwnerFollowing()) {
            this.tickSharedUnderwaterOwnerFollowing();
        }
    }

    private void tickSharedUnderwaterOwnerFollowing() {
        if (!this.isInWater()
                || !this.isTame()
                || this.isPassenger()) {
            this.ownerRepathCooldown = 0;
            return;
        }

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            this.stopWaterAwareMovement();
            this.ownerRepathCooldown = 0;
            return;
        }

        LivingEntity combatTarget = this.getTarget();
        if (combatTarget != null && combatTarget.isAlive()) {
            return;
        }

        if (this.isBaby() && this.hasFamilyDefenseEmergency()) {
            return;
        }

        /* Species rescue/combat/patrol goals keep priority over owner follow. */
        if (this.hasRecentAquaticMoveRequest()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            return;
        }

        double distanceSqr = this.distanceToSqr(owner);
        if (distanceSqr <= OWNER_FOLLOW_STOP_DISTANCE_SQR) {
            this.stopWaterAwareMovement();
            this.ownerRepathCooldown = 0;
            return;
        }

        if (distanceSqr < OWNER_FOLLOW_START_DISTANCE_SQR) {
            return;
        }

        if (--this.ownerRepathCooldown > 0 && this.getNavigation().isInProgress()) {
            return;
        }
        this.ownerRepathCooldown = OWNER_REPATH_INTERVAL_TICKS;

        double speed = distanceSqr > 100.0D
                ? OWNER_CATCHUP_SPEED
                : OWNER_FOLLOW_SPEED;

        this.getLookControl().setLookAt(
                owner,
                this.getMaxHeadYRot() + 15.0F,
                this.getMaxHeadXRot() + 15.0F);

        if (this.isInWater() && owner.isInWater()) {
            this.useWaterNavigation();
            this.waterNavigation.moveTo(owner, speed);
        } else {
            this.useGroundNavigation();
            this.groundNavigation.moveTo(owner, speed);
        }
    }

    // ---------------------------------------------------------------------
    // Water physiology — adapted from vanilla WaterAnimal
    // ---------------------------------------------------------------------

    @Override
    @Deprecated
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected int increaseAirSupply(int currentSupply) {
        return this.getMaxAirSupply();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isInWater()) {
            this.setAirSupply(this.getMaxAirSupply());
        } else {
            this.aquaticMoveRequestExpiryTick = Integer.MIN_VALUE;
            this.ownerRepathCooldown = 0;
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Vanilla WaterAnimal behavior. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return 10.0F + level.getPathfindingCostFromLightLevels(pos);
        }

        return super.getWalkTargetValue(pos, level);
    }

    // ---------------------------------------------------------------------
    // Vanilla-style swimming state — navigation only, NOT wolf pose
    // ---------------------------------------------------------------------

    /** A shore route still needs 3D water travel until the wolf reaches land. */
    private boolean hasActiveGroundWaterPath() {
        return this.isInWater()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.navigation == this.groundNavigation
                && this.groundNavigation.isInProgress();
    }

    protected boolean wantsToSwim() {
        if (!this.isInWater()) {
            return false;
        }

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            return false;
        }

        if (this.hasSpeciesWaterMovementIntent()
                || this.hasRecentAquaticMoveRequest()) {
            return true;
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && target.isInWater()) {
            return true;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame()
                && owner != null
                && owner.isAlive()
                && owner.level() == this.level()
                && owner.isInWater()
                && this.distanceToSqr(owner) > OWNER_FOLLOW_STOP_DISTANCE_SQR) {
            return true;
        }

        return this.navigation == this.waterNavigation
                && this.waterNavigation.isInProgress();
    }

    /**
     * Switches between water and ground navigation without ever forcing the
     * generic SWIMMING pose onto the vanilla Wolf model.
     *
     * <p>While an aquatic intent is active, WaterBoundPathNavigation remains in
     * control even at the surface. This prevents GroundPathNavigation from
     * taking over at the waterline and dragging the wolf into surface-floating
     * behavior. A live ground route is the exception: it owns a shore
     * transition, while the existing aquatic controller still follows its XYZ
     * waypoints until the wolf leaves the water. Replacing that navigator would
     * discard the dry destination; disabling water travel would strand the wolf
     * below the surface assumed by the ground pathfinder.</p>
     */
    @Override
    public void updateSwimming() {
        if (!this.level().isClientSide()) {
            if (this.isEffectiveAi()
                    && this.isInWater()
                    && this.wantsToSwim()) {
                this.useWaterNavigation();
            } else {
                this.useGroundNavigation();
            }

            if (this.isSwimming()) {
                this.setSwimming(false);
            }
        }
    }

    @Override
    public boolean isVisuallySwimming() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Vanilla Drowned / AbstractFish-style water travel
    // ---------------------------------------------------------------------

    @Override
    protected void travelInWater(
            Vec3 input,
            double baseGravity,
            boolean isFalling,
            double oldY) {
        if (this.isEffectiveAi()
                && this.isInWater()
                && (this.wantsToSwim() || this.hasActiveGroundWaterPath())) {
            this.moveRelative(0.01F, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.90D));
        } else {
            super.travelInWater(input, baseGravity, isFalling, oldY);
        }
    }

    // ---------------------------------------------------------------------
    // Vanilla Drowned-style amphibious MoveControl
    // ---------------------------------------------------------------------

    /**
     * Direct adaptation of vanilla Drowned's MoveControl.
     *
     * <p>There is deliberately no entity X-rotation here. Vanilla Drowned's
     * controller steers yaw and velocity; it does not need our previous custom
     * pitch system. This keeps a wolf-shaped model horizontal while Y movement
     * comes entirely from the destination vector.</p>
     */
    private static final class VanillaAquaticMoveControl extends MoveControl {
        private final AbstractWolfismWaterAnimal wolf;

        private VanillaAquaticMoveControl(AbstractWolfismWaterAnimal wolf) {
            super(wolf);
            this.wolf = wolf;
        }

        @Override
        public void tick() {
            if (this.wolf.isInWater()
                    && (this.wolf.wantsToSwim() || this.wolf.hasActiveGroundWaterPath())) {
                LivingEntity target = this.wolf.getTarget();

                /* Vanilla Drowned adds a tiny upward bias when pursuing upward. */
                if (target != null && target.getY() > this.wolf.getY()) {
                    this.wolf.setDeltaMovement(
                            this.wolf.getDeltaMovement().add(0.0D, 0.002D, 0.0D));
                }

                if (this.operation != MoveControl.Operation.MOVE_TO
                        || this.wolf.getNavigation().isDone()) {
                    this.wolf.setSpeed(0.0F);
                    return;
                }

                double xd = this.wantedX - this.wolf.getX();
                double yd = this.wantedY - this.wolf.getY();
                double zd = this.wantedZ - this.wolf.getZ();
                double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);

                if (distance < 1.0E-7D) {
                    this.wolf.setSpeed(0.0F);
                    return;
                }

                double normalizedY = yd / distance;

                float wantedYaw = (float) (
                        Mth.atan2(zd, xd)
                                * 180.0F
                                / (float) Math.PI) - 90.0F;

                this.wolf.setYRot(
                        this.rotlerp(
                                this.wolf.getYRot(),
                                wantedYaw,
                                90.0F));
                this.wolf.yBodyRot = this.wolf.getYRot();

                float targetSpeed = (float) (
                        this.speedModifier
                                * this.wolf.getAttributeValue(
                                Attributes.MOVEMENT_SPEED)
                                * AQUATIC_SPEED_MULTIPLIER);

                float smoothedSpeed = Mth.lerp(
                        0.125F,
                        this.wolf.getSpeed(),
                        targetSpeed);

                this.wolf.setSpeed(smoothedSpeed);

                this.wolf.setDeltaMovement(
                        this.wolf.getDeltaMovement().add(
                                (double) smoothedSpeed * xd / distance * 0.005D,
                                (double) smoothedSpeed * normalizedY * 0.10D,
                                (double) smoothedSpeed * zd / distance * 0.005D));
                return;
            }

            /* Vanilla Drowned falls naturally when it is not actively swimming. */
            if (!this.wolf.onGround()) {
                this.wolf.setDeltaMovement(
                        this.wolf.getDeltaMovement().add(0.0D, -0.008D, 0.0D));
            }

            super.tick();
        }
    }
}
