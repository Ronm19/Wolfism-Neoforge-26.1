package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Angel Wolf: Wolfism's divine healer / protector / anti-undead aerial support.
 *
 * <p>Her combat identity is deliberately support-first. Angel does not receive a
 * generic undead-hunting target goal; she stays with her family, evaluates family
 * health, flies to injured members when terrain or distance demands it, and only
 * uses Banishment against a legitimate combat target. This keeps her from behaving
 * like "nearest zombie detected -> charge" while still making her terrifying to
 * undead that threaten the pack.</p>
 */
public final class AngelWolf extends AbstractWolfismWolf implements FlyingAnimal {
    private static final TagKey<EntityType<?>> UNDEAD_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("minecraft", "undead"));

    /**
     * Angel uses the same sensor + memory Brain architecture as the established
     * Wolfism wolves. Custom Angel decision-making must come from these memories,
     * not from extra Goal/TargetGoal classes. Vanilla Wolf baseline goals remain
     * inherited from AbstractWolfismWolf.
     */
    private static final class BrainHolder {
        private static final Brain.Provider<AngelWolf> PROVIDER = Brain.<AngelWolf>provider(
                ImmutableList.of(
                        ModSensorTypes.ANGEL_FAMILY.get(),
                        ModSensorTypes.ANGEL_THREAT.get()),
                wolf -> List.of());
    }

    // ---------------------------------------------------------------------
    // Balance / support constants
    // ---------------------------------------------------------------------

    public static final double BLESSING_RADIUS = 10.0D;
    public static final double SUPPORT_SEARCH_RADIUS = 24.0D;
    public static final double CROP_BLESSING_RADIUS = 5.0D;

    /*
     * Wider senses than a normal wolf, without turning Angel into a roaming
     * "attack every hostile in sight" mob.
     */
    public static final double COMBAT_AWARENESS_RADIUS = 30.0D;
    public static final double FAMILY_THREAT_RADIUS = 14.0D;
    public static final double PERSONAL_THREAT_RADIUS = 11.0D;
    public static final double BANISHMENT_AWARENESS_RADIUS = 22.0D;
    public static final double BANISHMENT_AOE_RADIUS = 7.0D;

    public static final int BASE_BLESSING_COOLDOWN_TICKS = 20 * 20;
    public static final int MAX_BLESSING_COOLDOWN_TICKS = 20 * 30;
    public static final int BANISHMENT_COOLDOWN_TICKS = 20 * 25;
    public static final int CROP_BLESSING_COOLDOWN_TICKS = 20 * 15;

    private static final int SUPPORT_DECISION_INTERVAL = 10;
    private static final int BANISHMENT_DECISION_INTERVAL = 5;
    private static final int CROP_DECISION_INTERVAL = 20;
    private static final int MAX_CROPS_PER_BLESSING = 18;

    private static final float INJURED_RATIO = 0.78F;
    private static final float CRITICAL_RATIO = 0.45F;

    private static final double SUPPORT_FLIGHT_START_DISTANCE = 4.5D;
    private static final double OWNER_FLIGHT_START_DISTANCE = 10.0D;
    private static final double OWNER_GROUND_FOLLOW_START_DISTANCE = 4.0D;
    private static final double OWNER_OBSTACLE_FLIGHT_MIN_DISTANCE = 3.0D;

    /*
     * Unlike vanilla FollowOwnerGoal, Angel should actually use her wings across
     * ordinary companion distances. Teleporting is reserved for a genuine
     * catch-up emergency.
     */
    private static final double OWNER_TELEPORT_DISTANCE = 48.0D;
    private static final double OWNER_TELEPORT_DISTANCE_SQR =
            OWNER_TELEPORT_DISTANCE * OWNER_TELEPORT_DISTANCE;
    private static final double OWNER_LANDING_HORIZONTAL_DISTANCE = 2.35D;
    private static final double OWNER_COMPANION_HOVER_HEIGHT = 1.35D;
    private static final double SUPPORT_HOVER_HEIGHT = 1.20D;
    private static final double OBSTACLE_PROBE_DISTANCE = 0.85D;

    /*
     * Phantom-derived flight motor tuning.
     *
     * Keep the real XYZ destination intact and temporarily bias vertical
     * steering upward while colliding with fences/walls. Angel is deliberately
     * softer/slower than Phantom because she is graceful support.
     */
    private static final int OBSTACLE_CLIMB_MEMORY_TICKS = 8;
    private static final double OBSTACLE_CLIMB_MIN_Y = 2.25D;
    private static final float ANGEL_OWNER_FLIGHT_SPEED = 1.45F;
    private static final float ANGEL_SUPPORT_FLIGHT_SPEED = 1.70F;
    private static final float ANGEL_LANDING_FLIGHT_SPEED = 1.10F;

    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(AngelWolf.class, EntityDataSerializers.BOOLEAN);

    private int blessingCooldownTicks;
    private int banishmentCooldownTicks;
    private int cropBlessingCooldownTicks;

    // Client-side presentation blend for reliable wing animation syncing.
    private float flightAnimationBlend;
    private float flightAnimationBlendOld;

    /*
     * Brain decides WHERE Angel goes. This Phantom-derived motor owns HOW she
     * physically reaches that XYZ point.
     */
    private Vec3 flightMoveTarget = Vec3.ZERO;
    private boolean hasFlightMoveTarget;
    private boolean flightLanding;
    private float requestedFlightSpeed = ANGEL_OWNER_FLIGHT_SPEED;

    private final MoveControl groundMoveControl;
    private final PathNavigation groundNavigation;
    private final AngelFlightMoveControl flyingMoveControl;
    private final FlyingPathNavigation flyingNavigation;

    public AngelWolf(EntityType<? extends AngelWolf> type, Level level) {
        super(type, level);
        this.groundMoveControl = this.moveControl;
        this.groundNavigation = this.navigation;
        this.flyingMoveControl = new AngelFlightMoveControl(this);
        this.flyingNavigation = this.createAngelFlyingNavigation(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FLYING, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.ANGEL_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        /*
         * Vanilla Wolf installs FollowOwnerGoal, whose built-in teleport fallback
         * can fire while Angel is still well within a distance she can fly.
         *
         * Angel owns following through tickBrainFlight() instead, so remove only
         * that inherited goal. All other vanilla/Wolfism goals stay untouched.
         */
        this.goalSelector.removeAllGoals(goal -> goal instanceof FollowOwnerGoal);
    }

    @Override
    protected Brain<AngelWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<AngelWolf> getBrain() {
        return (Brain<AngelWolf>) super.getBrain();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FLYING_SPEED, 0.70D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    // ---------------------------------------------------------------------
    // AI priorities / Graceful Flight
    // ---------------------------------------------------------------------

    /*
     * No Angel-specific Goal/TargetGoal registrations here. The custom support,
     * threat awareness and aerial decisions are Brain-memory driven below.
     */

    private FlyingPathNavigation createAngelFlyingNavigation(Level level) {
        FlyingPathNavigation flying = new FlyingPathNavigation(this, level);
        flying.setCanOpenDoors(false);
        flying.setCanFloat(false);
        return flying;
    }

    public boolean isAngelFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    private void enterAngelFlight() {
        if (!this.isAngelFlying()) {
            this.groundNavigation.stop();
            this.navigation = this.flyingNavigation;
            this.moveControl = this.flyingMoveControl;
            this.entityData.set(DATA_FLYING, true);
        }

        this.flightLanding = false;
        this.setNoGravity(true);

        if (!this.hasFlightMoveTarget) {
            this.setFlightMoveTarget(this.position(), ANGEL_OWNER_FLIGHT_SPEED);
        }
    }

    private void leaveAngelFlight() {
        if (!this.isAngelFlying()) {
            return;
        }

        this.flyingNavigation.stop();
        this.clearFlightMoveTarget();
        this.navigation = this.groundNavigation;
        this.moveControl = this.groundMoveControl;
        this.flightLanding = false;
        this.setNoGravity(false);
        this.entityData.set(DATA_FLYING, false);
        this.setXRot(Mth.approachDegrees(this.getXRot(), 0.0F, 10.0F));
    }

    private void setFlightMoveTarget(Vec3 target, float speed) {
        this.flightMoveTarget = target;
        this.hasFlightMoveTarget = true;
        this.requestedFlightSpeed = speed;
    }

    private void clearFlightMoveTarget() {
        this.hasFlightMoveTarget = false;
        this.flightMoveTarget = this.position();
        this.requestedFlightSpeed = ANGEL_OWNER_FLIGHT_SPEED;
    }

    @Override
    public boolean isFlying() {
        return this.isAngelFlying() && !this.onGround();
    }

    public boolean shouldAnimateFlight() {
        if (this.isInSittingPose() || (this.isOrderedToSit() && this.onGround())) {
            return false;
        }

        return this.isAngelFlying() || this.isNoGravity() || this.isFlying();
    }

    public float getFlightAnimationBlend(float partialTick) {
        return Mth.lerp(partialTick, this.flightAnimationBlendOld, this.flightAnimationBlend);
    }

    private void tickFlightAnimationBlend() {
        this.flightAnimationBlendOld = this.flightAnimationBlend;
        float target = this.shouldAnimateFlight() ? 1.0F : 0.0F;
        float step = target > this.flightAnimationBlend ? 0.34F : 0.22F;

        if (this.flightAnimationBlend < target) {
            this.flightAnimationBlend = Math.min(target, this.flightAnimationBlend + step);
        } else if (this.flightAnimationBlend > target) {
            this.flightAnimationBlend = Math.max(target, this.flightAnimationBlend - step);
        }
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (this.isAngelFlying()) {
            return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
        }
        return super.getWalkTargetValue(pos, level);
    }

    @Override
    protected void checkFallDamage(double yMotion, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isAngelFlying() && !this.flightLanding && !this.isOrderedToSit()) {
            this.travelFlying(input, 0.2F);
            return;
        }
        super.travel(input);
    }

    private double horizontalDistanceToSqr(LivingEntity target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Fast takeoff probe. Once airborne, AngelFlightMoveControl handles the
     * repeated fence/wall climb instead of replacing the real destination.
     */
    private boolean hasImmediateFlightObstacle(LivingEntity target) {
        if (this.horizontalCollision) {
            return true;
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return false;
        }

        double stepX = dx / length * OBSTACLE_PROBE_DISTANCE;
        double stepZ = dz / length * OBSTACLE_PROBE_DISTANCE;
        AABB probe = this.getBoundingBox().move(stepX, 0.0D, stepZ);
        return !this.level().noCollision(this, probe);
    }

    private boolean tryEmergencyOwnerTeleport(ServerLevel level, LivingEntity owner) {
        if (owner.level() != level
                || this.distanceToSqr(owner) <= OWNER_TELEPORT_DISTANCE_SQR) {
            return false;
        }

        BlockPos ownerPos = owner.blockPosition();

        // Randomized first so multiple wolves do not stack on one exact block.
        for (int attempt = 0; attempt < 20; ++attempt) {
            int dx = this.random.nextIntBetweenInclusive(-5, 5);
            int dz = this.random.nextIntBetweenInclusive(-5, 5);
            int dy = this.random.nextIntBetweenInclusive(-1, 2);

            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }

            BlockPos feet = ownerPos.offset(dx, dy, dz);
            if (this.isSafeOwnerTeleportPosition(level, feet)) {
                this.finishEmergencyOwnerTeleport(feet);
                return true;
            }
        }

        // Deterministic fallback for cramped terrain.
        for (int radius = 2; radius <= 5; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -1; dy <= 2; ++dy) {
                        BlockPos feet = ownerPos.offset(dx, dy, dz);
                        if (this.isSafeOwnerTeleportPosition(level, feet)) {
                            this.finishEmergencyOwnerTeleport(feet);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isSafeOwnerTeleportPosition(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos groundPos = feet.below();

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState groundState = level.getBlockState(groundPos);

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }

        return groundState.isFaceSturdy(level, groundPos, Direction.UP);
    }

    private void finishEmergencyOwnerTeleport(BlockPos feet) {
        this.getNavigation().stop();
        this.clearFlightMoveTarget();
        this.setNoGravity(false);
        this.entityData.set(DATA_FLYING, false);
        this.flightLanding = false;

        this.teleportTo(
                feet.getX() + 0.5D,
                feet.getY(),
                feet.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = true;
    }

    private boolean shouldUseOwnerFlight(LivingEntity owner) {
        double distance = this.distanceTo(owner);
        double vertical = Math.abs(this.getY() - owner.getY());

        if (distance > OWNER_FLIGHT_START_DISTANCE || vertical > 1.75D) {
            return true;
        }

        return distance > OWNER_OBSTACLE_FLIGHT_MIN_DISTANCE
                && this.hasImmediateFlightObstacle(owner);
    }

    /**
     * Direct adaptation of the supplied PhantomWolfMoveControl.
     *
     * Brain chooses the destination; this motor handles smooth XYZ steering,
     * nearly-vertical targets, and remembered obstacle climbing.
     */
    private static final class AngelFlightMoveControl extends MoveControl {
        private final AngelWolf wolf;
        private float speed = 0.1F;
        private int obstacleClimbTicks;

        private AngelFlightMoveControl(AngelWolf wolf) {
            super(wolf);
            this.wolf = wolf;
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speedModifier) {
            super.setWantedPosition(x, y, z, speedModifier);
            this.wolf.setFlightMoveTarget(new Vec3(x, y, z), (float) speedModifier);
        }

        @Override
        public void tick() {
            if (!this.wolf.isAngelFlying() || !this.wolf.hasFlightMoveTarget) {
                return;
            }

            if (this.wolf.horizontalCollision
                    && !this.wolf.flightLanding
                    && !this.wolf.isOrderedToSit()) {
                this.obstacleClimbTicks = OBSTACLE_CLIMB_MEMORY_TICKS;
                this.speed = Math.max(this.speed, 0.72F);
            }

            double tdx = this.wolf.flightMoveTarget.x - this.wolf.getX();
            double tdy = this.wolf.flightMoveTarget.y - this.wolf.getY();
            double tdz = this.wolf.flightMoveTarget.z - this.wolf.getZ();

            if (this.obstacleClimbTicks > 0) {
                --this.obstacleClimbTicks;
                tdy = Math.max(tdy, OBSTACLE_CLIMB_MIN_Y);
            }

            double horizontal = Math.sqrt(tdx * tdx + tdz * tdz);

            if (horizontal <= 1.0E-5D) {
                if (Math.abs(tdy) > 0.05D) {
                    Vec3 movement = this.wolf.getDeltaMovement();
                    double wantedY = Mth.clamp(tdy * 0.08D, -0.24D, 0.24D);
                    this.wolf.setDeltaMovement(
                            movement.add(new Vec3(0.0D, wantedY, 0.0D)
                                    .subtract(movement).scale(0.22D)));
                    this.wolf.setXRot(tdy > 0.0D ? -55.0F : 55.0F);
                }
                return;
            }

            double horizontalScale = Mth.clamp(
                    1.0D - Math.abs(tdy * 0.62D) / Math.max(horizontal, 1.0E-5D),
                    0.28D,
                    1.0D);
            tdx *= horizontalScale;
            tdz *= horizontalScale;

            horizontal = Math.sqrt(tdx * tdx + tdz * tdz);
            double distance3d = Math.sqrt(tdx * tdx + tdz * tdz + tdy * tdy);
            if (distance3d <= 1.0E-5D) {
                return;
            }

            float previousYaw = this.wolf.getYRot();
            float angle = (float) Mth.atan2(tdz, tdx);
            float current = Mth.wrapDegrees(this.wolf.getYRot() + 90.0F);
            float wanted = Mth.wrapDegrees(angle * (180.0F / (float) Math.PI));

            // Graceful support gets a softer turn than Phantom's combat flight.
            this.wolf.setYRot(Mth.approachDegrees(current, wanted, 6.0F) - 90.0F);
            this.wolf.yBodyRot = this.wolf.getYRot();

            float topSpeed = Mth.clamp(
                    this.wolf.requestedFlightSpeed,
                    ANGEL_LANDING_FLIGHT_SPEED,
                    ANGEL_SUPPORT_FLIGHT_SPEED);

            float headingDifference =
                    Mth.degreesDifferenceAbs(previousYaw, this.wolf.getYRot());

            if (headingDifference < 5.0F) {
                this.speed = Mth.approach(this.speed, topSpeed, 0.035F);
            } else {
                this.speed = Mth.approach(this.speed, 0.32F, 0.028F);
            }

            float wantedPitch = (float) (-(Mth.atan2(-tdy, horizontal)
                    * 180.0F / (float) Math.PI));
            wantedPitch = Mth.clamp(wantedPitch, -52.0F, 52.0F);
            this.wolf.setXRot(wantedPitch);

            float moveAngle = this.wolf.getYRot() + 90.0F;
            double wantedX = this.speed
                    * Mth.cos(moveAngle * (float) (Math.PI / 180.0D))
                    * Math.abs(tdx / distance3d);
            double wantedZ = this.speed
                    * Mth.sin(moveAngle * (float) (Math.PI / 180.0D))
                    * Math.abs(tdz / distance3d);
            double wantedY = this.speed
                    * Mth.sin(wantedPitch * (float) (Math.PI / 180.0D))
                    * Math.abs(tdy / distance3d);

            Vec3 movement = this.wolf.getDeltaMovement();
            this.wolf.setDeltaMovement(
                    movement.add(new Vec3(wantedX, wantedY, wantedZ)
                            .subtract(movement).scale(0.20D)));
        }
    }

    // ---------------------------------------------------------------------
    // Brain-driven movement / targeting
    // ---------------------------------------------------------------------

    private void tickBrainCombatTarget() {
        if (!this.canParticipateInWolfismCombat() || this.isBaby()) {
            this.setTarget(null);
            return;
        }

        // Shared family-defense AI from AbstractWolfismWolf remains authoritative.
        if (this.hasFamilyDefenseEmergency()) {
            LivingEntity defense = this.getFamilyDefenseTarget();
            if (this.isValidAngelCombatTarget(defense)) {
                this.setTarget(defense);
            }
            return;
        }

        LivingEntity sensed = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_FAMILY_THREAT.get())
                .orElse(null);

        if (this.isValidAngelCombatTarget(sensed)) {
            this.setTarget(sensed);
        } else {
            LivingEntity current = this.getTarget();
            if (current != null && !this.isValidAngelCombatTarget(current)) {
                this.setTarget(null);
            }
        }
    }

    /**
     * Movement intent comes from Brain memories. The Phantom-derived motor owns
     * the actual 3-D movement, so the Brain and physical flight have distinct jobs.
     */
    private void tickBrainFlight() {
        if (this.isOrderedToSit() || this.isInSittingPose()) {
            if (this.isAngelFlying()) {
                if (this.onGround()) {
                    this.leaveAngelFlight();
                } else {
                    this.flightLanding = true;
                    this.setNoGravity(false);
                    this.setInSittingPose(false);
                    this.setFlightMoveTarget(
                            new Vec3(this.getX(), this.getY() - 1.0D, this.getZ()),
                            ANGEL_LANDING_FLIGHT_SPEED);
                }
            }
            return;
        }

        if (this.isBaby() || !this.isTame() || this.isPassenger()) {
            if (this.isAngelFlying()) {
                this.flightLanding = true;
                this.setNoGravity(false);
                if (this.onGround()) {
                    this.leaveAngelFlight();
                }
            }
            return;
        }

        LivingEntity critical = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_CRITICAL_FAMILY.get())
                .orElse(null);
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_INJURED_FAMILY.get())
                .orElse(null);
        LivingEntity supportTarget = critical != null ? critical : injured;

        if (supportTarget != null
                && supportTarget != this
                && supportTarget.isAlive()
                && this.isAngelFamilyMember(supportTarget)) {
            double distance = this.distanceTo(supportTarget);
            double vertical = Math.abs(this.getY() - supportTarget.getY());

            if (distance >= SUPPORT_FLIGHT_START_DISTANCE || vertical >= 1.75D) {
                this.enterAngelFlight();
                this.flightLanding = false;
                this.setNoGravity(true);
                this.getLookControl().setLookAt(supportTarget, 35.0F, 35.0F);
                this.setFlightMoveTarget(
                        new Vec3(
                                supportTarget.getX(),
                                supportTarget.getY() + SUPPORT_HOVER_HEIGHT,
                                supportTarget.getZ()),
                        ANGEL_SUPPORT_FLIGHT_SPEED);
                return;
            }
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            /*
             * Emergency teleport is now a LAST-resort distance fallback.
             * Within 48 blocks Angel must solve the trip through ground movement
             * or her Phantom-derived flight motor.
             */
            if (this.level() instanceof ServerLevel serverLevel
                    && this.tryEmergencyOwnerTeleport(serverLevel, owner)) {
                return;
            }

            if (this.shouldUseOwnerFlight(owner)) {
                this.enterAngelFlight();
                this.getLookControl().setLookAt(owner, 30.0F, 30.0F);

                double horizontalDistanceSqr = this.horizontalDistanceToSqr(owner);
                boolean blocked = this.hasImmediateFlightObstacle(owner);

                if (horizontalDistanceSqr
                        <= OWNER_LANDING_HORIZONTAL_DISTANCE
                        * OWNER_LANDING_HORIZONTAL_DISTANCE
                        && !blocked) {
                    this.flightLanding = true;

                    if (this.getY() <= owner.getY() + 0.95D) {
                        this.setNoGravity(false);
                    } else {
                        this.setNoGravity(true);
                    }

                    this.setFlightMoveTarget(
                            new Vec3(owner.getX(), owner.getY() + 0.20D, owner.getZ()),
                            ANGEL_LANDING_FLIGHT_SPEED);
                } else {
                    this.flightLanding = false;
                    this.setNoGravity(true);
                    this.setFlightMoveTarget(
                            new Vec3(
                                    owner.getX(),
                                    owner.getY() + OWNER_COMPANION_HOVER_HEIGHT,
                                    owner.getZ()),
                            ANGEL_OWNER_FLIGHT_SPEED);
                }
                return;
            }

            /*
             * Vanilla FollowOwnerGoal was removed to stop premature teleports.
             * Preserve ordinary close-range companion following explicitly.
             */
            if (!this.isAngelFlying()
                    && this.distanceTo(owner) > OWNER_GROUND_FOLLOW_START_DISTANCE) {
                this.groundNavigation.moveTo(owner, 1.10D);
                this.getLookControl().setLookAt(owner, 25.0F, 25.0F);
                return;
            }
        }

        if (this.isAngelFlying()) {
            if (this.onGround()) {
                this.leaveAngelFlight();
                return;
            }

            this.flightLanding = true;

            if (owner != null && owner.isAlive()) {
                if (this.getY() <= owner.getY() + 0.95D) {
                    this.setNoGravity(false);
                } else {
                    this.setNoGravity(true);
                }

                this.setFlightMoveTarget(
                        new Vec3(owner.getX(), owner.getY() + 0.20D, owner.getZ()),
                        ANGEL_LANDING_FLIGHT_SPEED);
            } else {
                this.setNoGravity(false);
                this.setFlightMoveTarget(
                        new Vec3(this.getX(), this.getY() - 1.0D, this.getZ()),
                        ANGEL_LANDING_FLIGHT_SPEED);
            }
        }
    }

    /**
     * Phantom-style sitting stability: descend standing first, become a seated
     * statue only after the paws actually reach the ground.
     */
    private void enforceSittingStability() {
        boolean orderedSit = this.isOrderedToSit();

        if (!orderedSit && !this.isInSittingPose()) {
            return;
        }

        if (!this.onGround()) {
            if (orderedSit) {
                this.setInSittingPose(false);
            }
            return;
        }

        if (this.isAngelFlying()) {
            this.leaveAngelFlight();
        }

        this.clearFlightMoveTarget();
        this.getNavigation().stop();
        this.setNoGravity(false);

        if (orderedSit) {
            this.setInSittingPose(true);
        }

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        this.setXRot(Mth.approachDegrees(this.getXRot(), 0.0F, 8.0F));
        this.hurtMarked = true;
    }

    public boolean isValidAngelCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || this.isAlliedTo(target)
                || this.isProtectedUndeadWolf(target)
                || !this.canAttack(target)) {
            return false;
        }

        if (!(target instanceof Enemy) && target != this.getFamilyDefenseTarget()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public boolean isRelevantAngelThreat(LivingEntity candidate) {
        if (!this.isValidAngelCombatTarget(candidate)) {
            return false;
        }

        if (candidate instanceof Mob mob
                && mob.getTarget() != null
                && this.isAngelFamilyMember(mob.getTarget())) {
            return true;
        }

        if (this.distanceToSqr(candidate)
                <= PERSONAL_THREAT_RADIUS * PERSONAL_THREAT_RADIUS) {
            return true;
        }

        LivingEntity owner = this.getOwner();
        return owner != null
                && owner.distanceToSqr(candidate)
                <= FAMILY_THREAT_RADIUS * FAMILY_THREAT_RADIUS;
    }

    public double angelThreatScore(LivingEntity candidate) {
        double score = this.distanceToSqr(candidate);

        if (candidate instanceof Mob mob
                && mob.getTarget() != null
                && this.isAngelFamilyMember(mob.getTarget())) {
            score -= 600.0D;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null) {
            score = Math.min(score, owner.distanceToSqr(candidate) - 120.0D);
        }

        if (this.isUndeadTarget(candidate)) {
            score -= 80.0D;
        }

        return score;
    }

    // ---------------------------------------------------------------------
    // Server tick: support brain / active kit
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
        } else {
            this.tickBrainCombatTarget();
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        // Client presentation remains independent from server Brain timing.
        this.tickFlightAnimationBlend();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.enforceSittingStability();

        if (this.blessingCooldownTicks > 0) {
            --this.blessingCooldownTicks;
        }
        if (this.banishmentCooldownTicks > 0) {
            --this.banishmentCooldownTicks;
        }
        if (this.cropBlessingCooldownTicks > 0) {
            --this.cropBlessingCooldownTicks;
        }

        if (this.isBaby()) {
            return;
        }

        LivingEntity current = this.getTarget();
        if (current != null && this.isProtectedUndeadWolf(current)) {
            this.setTarget(null);
        }

        // Flight choices are now driven by ANGEL_* Brain memories.
        this.tickBrainFlight();

        if (this.tickCount % SUPPORT_DECISION_INTERVAL == 0) {
            this.tickSupportDecision(level);
        }

        if (this.tickCount % BANISHMENT_DECISION_INTERVAL == 0) {
            this.tickBanishment(level);
        }

        if (this.tickCount % CROP_DECISION_INTERVAL == 0) {
            this.tickCropBlessing(level);
        }
    }

    // ---------------------------------------------------------------------
    // Angel's Blessing
    // ---------------------------------------------------------------------

    private void tickSupportDecision(ServerLevel level) {
        if (!this.isTame() || this.blessingCooldownTicks > 0) {
            return;
        }

        List<LivingEntity> family = this.findAngelFamily(BLESSING_RADIUS);
        if (family.isEmpty()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        int injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_INJURED_COUNT.get())
                .orElse(0);
        boolean critical = this.getBrain()
                .hasMemoryValue(ModMemoryModuleTypes.ANGEL_CRITICAL_FAMILY.get());
        boolean ownerInDanger = owner != null && this.healthRatio(owner) < 0.70F;
        boolean combatPressure = this.getBrain()
                .hasMemoryValue(ModMemoryModuleTypes.ANGEL_FAMILY_THREAT.get())
                || this.hasFamilyDefenseEmergency();

        if (!critical
                && !ownerInDanger
                && injured < 2
                && !(combatPressure && injured > 0)) {
            return;
        }

        this.performAngelsBlessing(level, family);
    }

    private void performAngelsBlessing(ServerLevel level, List<LivingEntity> family) {
        for (LivingEntity member : family) {
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 1), this);
            member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 8, 0), this);
            member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 5, 0), this);

            WolfVfx.sendParticles("angel_wolf", level,
                    ParticleTypes.END_ROD,
                    member.getX(),
                    member.getY() + member.getBbHeight() * 0.60D,
                    member.getZ(),
                    8,
                    0.28D, 0.35D, 0.28D,
                    0.02D);
        }

        WolfVfx.sendParticles("angel_wolf", level,
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY(0.70D), this.getZ(),
                18, 0.70D, 0.45D, 0.70D, 0.04D);

        // Scale from 20 -> 30 seconds as the supported family grows. Three
        // family members pay no surcharge; each additional member adds 0.6s.
        int extraFamily = Math.max(0, family.size() - 3);
        int scaled = BASE_BLESSING_COOLDOWN_TICKS + extraFamily * 12;
        this.blessingCooldownTicks = Math.min(MAX_BLESSING_COOLDOWN_TICKS, scaled);
    }

    // ---------------------------------------------------------------------
    // Banishment
    // ---------------------------------------------------------------------

    private void tickBanishment(ServerLevel level) {
        if (this.banishmentCooldownTicks > 0) {
            return;
        }

        LivingEntity anchor = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_UNDEAD_THREAT.get())
                .orElse(null);
        if (!this.isValidBanishmentTarget(anchor)) {
            return;
        }

        /*
         * Banishment is crowd control: the sensed undead becomes the centre of
         * a divine suppression zone. Every valid undead caught in that zone is
         * affected by the same cast, rather than deleting only one target.
         */
        List<LivingEntity> caught = level.getEntitiesOfClass(
                        LivingEntity.class,
                        anchor.getBoundingBox().inflate(BANISHMENT_AOE_RADIUS),
                        candidate -> this.isValidBanishmentTarget(candidate)
                                && candidate.distanceToSqr(anchor)
                                <= BANISHMENT_AOE_RADIUS * BANISHMENT_AOE_RADIUS)
                .stream()
                .limit(16)
                .toList();

        if (caught.isEmpty()) {
            return;
        }

        for (LivingEntity target : caught) {
            boolean elite = this.isEliteUndead(target);
            if (elite) {
                target.hurtServer(level, this.damageSources().mobAttack(this), 18.0F);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 6, 1), this);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 4, 1), this);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 5, 0, true, false), this);
            } else {
                target.hurtServer(level, this.damageSources().mobAttack(this), 1000.0F);
            }

            WolfVfx.sendParticles("angel_wolf", level,
                    ParticleTypes.END_ROD,
                    target.getX(), target.getY(0.60D), target.getZ(),
                    elite ? 34 : 24,
                    0.50D, 0.65D, 0.50D,
                    0.06D);
        }

        // One clear central flash makes the crowd-control cast readable.
        WolfVfx.sendParticles("angel_wolf", level,
                ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE6A3),
                anchor.getX(), anchor.getY(0.65D), anchor.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        this.banishmentCooldownTicks = BANISHMENT_COOLDOWN_TICKS;
    }

    public boolean isUndeadTarget(LivingEntity target) {
        return target != null && target.is(UNDEAD_ENTITY_TYPES);
    }

    public boolean isValidBanishmentTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && this.isUndeadTarget(target)
                && !this.isProtectedUndeadWolf(target)
                && this.isValidAngelCombatTarget(target);
    }

    /**
     * Tamed undead wolves are always protected. Neutral untamed undead wolves
     * are also ignored unless they have actually committed to attacking Angel's
     * family; proximity alone is not grounds for an exorcism.
     */
    public boolean isProtectedUndeadWolf(LivingEntity target) {
        if (!(target instanceof Wolf wolf) || !this.isUndeadTarget(target)) {
            return false;
        }

        if (wolf.isTame()) {
            return true;
        }

        LivingEntity wolfTarget = wolf.getTarget();
        return wolfTarget == null || !this.isAngelFamilyMember(wolfTarget);
    }

    private boolean isEliteUndead(LivingEntity target) {
        String id = target.getType().toString().toLowerCase();
        return target.getMaxHealth() >= 55.0F
                || id.contains("wither")
                || id.contains("boss")
                || id.contains("lich")
                || id.contains("warden");
    }

    // ---------------------------------------------------------------------
    // Blessed Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(level, entity);
        if (!hurt || this.isBaby() || !(entity instanceof LivingEntity target)) {
            return hurt;
        }

        boolean undead = target.is(UNDEAD_ENTITY_TYPES);
        if (undead && !this.isProtectedUndeadWolf(target)) {
            target.hurtServer(level, this.damageSources().mobAttack(this), 2.5F);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 18, 4), this);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 2, 0), this);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 1), this);
        }

        WolfVfx.sendParticles("angel_wolf", level,
                ParticleTypes.END_ROD,
                target.getX(), target.getY(0.55D), target.getZ(),
                undead ? 10 : 5,
                0.22D, 0.24D, 0.22D,
                0.03D);
        return true;
    }

    // ---------------------------------------------------------------------
    // Crop Blessing
    // ---------------------------------------------------------------------

    private void tickCropBlessing(ServerLevel level) {
        if (!this.isTame()
                || this.cropBlessingCooldownTicks > 0
                || this.getTarget() != null
                || this.hasFamilyDefenseEmergency()) {
            return;
        }

        // Healing takes priority over farming. If the family is meaningfully
        // injured, Angel stays a medic instead of wandering into utility work.
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ANGEL_INJURED_FAMILY.get())
                .orElse(null);
        if (injured != null && this.healthRatio(injured) < INJURED_RATIO) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || owner.distanceToSqr(this) > 20.0D * 20.0D) {
            return;
        }

        BlockPos origin = this.blockPosition();
        int radius = (int) CROP_BLESSING_RADIUS;
        int grown = 0;

        outer:
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if ((double) (dx * dx + dz * dz) > CROP_BLESSING_RADIUS * CROP_BLESSING_RADIUS) {
                        continue;
                    }

                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.BEE_GROWABLES)
                            || !(state.getBlock() instanceof BonemealableBlock bonemealable)
                            || !bonemealable.isValidBonemealTarget(level, pos, state)) {
                        continue;
                    }

                    bonemealable.performBonemeal(level, this.random, pos, state);
                    ++grown;

                    WolfVfx.sendParticles("angel_wolf", level,
                            ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5D,
                            pos.getY() + 0.75D,
                            pos.getZ() + 0.5D,
                            5,
                            0.18D, 0.24D, 0.18D,
                            0.03D);

                    if (grown >= MAX_CROPS_PER_BLESSING) {
                        break outer;
                    }
                }
            }
        }

        if (grown > 0) {
            WolfVfx.sendParticles("angel_wolf", level,
                    ParticleTypes.END_ROD,
                    this.getX(), this.getY(0.65D), this.getZ(),
                    16, 0.80D, 0.35D, 0.80D, 0.025D);
            this.cropBlessingCooldownTicks = CROP_BLESSING_COOLDOWN_TICKS;
        }
    }

    // ---------------------------------------------------------------------
    // Graceful projectile resistance / family safety
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            // Tuned down from the first pass so ranged attacks are still a
            // meaningful threat and the behavior is easier to read in testing.
            if (this.isAngelFlying() && this.random.nextFloat() < 0.20F) {
                WolfVfx.sendParticles("angel_wolf", level,
                        ParticleTypes.CLOUD,
                        this.getX(), this.getY(0.60D), this.getZ(),
                        8, 0.28D, 0.20D, 0.28D, 0.03D);
                return false;
            }

            amount *= this.isAngelFlying() ? 0.65F : 0.80F;
        }

        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isProtectedUndeadWolf(target)) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("AngelBlessingCooldown", this.blessingCooldownTicks);
        output.putInt("AngelBanishmentCooldown", this.banishmentCooldownTicks);
        output.putInt("AngelCropBlessingCooldown", this.cropBlessingCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.blessingCooldownTicks = Mth.clamp(input.getIntOr("AngelBlessingCooldown", 0), 0, MAX_BLESSING_COOLDOWN_TICKS);
        this.banishmentCooldownTicks = Mth.clamp(input.getIntOr("AngelBanishmentCooldown", 0), 0, BANISHMENT_COOLDOWN_TICKS);
        this.cropBlessingCooldownTicks = Mth.clamp(input.getIntOr("AngelCropBlessingCooldown", 0), 0, CROP_BLESSING_COOLDOWN_TICKS);
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    /**
     * Legendary Angel Wolf natural spawn gate.
     *
     * <p>Biome rarity is controlled by ModBiomeModifierProvider. This predicate
     * handles the exact-block/environment rules: high terrain, daytime, open sky,
     * bright enough to spawn, and legitimate wolf ground.</p>
     */
    public static boolean checkAngelWolfSpawnRules(
            EntityType<AngelWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {

        if (pos.getY() < 90) {
            return false;
        }

        if (!level.getLevel().isBrightOutside()
                || !level.canSeeSky(pos.above())
                || !isBrightEnoughToSpawn(level, pos)) {
            return false;
        }

        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Family-health helpers
    // ---------------------------------------------------------------------

    public boolean isAngelFamilyMember(LivingEntity entity) {
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
        if (entity == owner) {
            return true;
        }

        return entity instanceof Wolf wolf
                && wolf.isTame()
                && Objects.equals(this.getOwnerReference(), wolf.getOwnerReference());
    }

    private List<LivingEntity> findAngelFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isAngelFamilyMember);
    }

    public float healthRatio(LivingEntity entity) {
        return entity.getMaxHealth() <= 0.0F
                ? 1.0F
                : entity.getHealth() / entity.getMaxHealth();
    }
}
