package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.EndPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.EndPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.EndPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.EndPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.EndPupRetreatGoal;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #27 - End Wolf.
 *
 * <p>End-native pack wolf built around spatial intelligence rather than raw
 * damage. The species keeps the already-tested Wolfism family/pup/pack
 * foundation and adds four End-specific systems:</p>
 *
 * <ul>
 *   <li><b>Void Awareness</b> - remembers safe ground and refuses unsafe
 *       teleport destinations; if it accidentally starts falling into the End
 *       void it can recover to its most recent safe position.</li>
 *   <li><b>Ender Step</b> - a short tactical combat teleport around a target.</li>
 *   <li><b>Teleport Pursuit</b> - a longer reposition when a valid combat target
 *       has opened a large gap.</li>
 *   <li><b>Pack Repositioning</b> - End Wolves attacking the same target choose
 *       different positions around it instead of stacking on one point.</li>
 * </ul>
 *
 * <p>None of the teleports deal bonus damage. They exist to improve positioning;
 * the wolf's normal melee attack remains the actual attack.</p>
 */
public final class EndWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // End ability tuning
    // ---------------------------------------------------------------------
    public static final int ENDER_STEP_COOLDOWN_TICKS = 20 * 7;
    public static final int PURSUIT_TELEPORT_COOLDOWN_TICKS = 20 * 12;
    public static final int PACK_REPOSITION_COOLDOWN_TICKS = 20 * 10;
    public static final int VOID_RECOVERY_COOLDOWN_TICKS = 20 * 10;

    public static final double ENDER_STEP_MIN_RANGE = 4.5D;
    public static final double ENDER_STEP_MAX_RANGE = 10.0D;
    public static final double PURSUIT_MIN_RANGE = 12.0D;
    public static final double PURSUIT_MAX_RANGE = 28.0D;
    public static final double PACK_REPOSITION_SCAN_RANGE = 24.0D;

    private static final int ABILITY_DECISION_INTERVAL = 5;
    private static final int VOID_SCAN_DEPTH = 12;
    private static final int SAFE_POSITION_UPDATE_INTERVAL = 10;
    private static final int TELEPORT_SEARCH_VERTICAL_RANGE = 4;

    private int enderStepCooldownTicks;
    private int pursuitTeleportCooldownTicks;
    private int packRepositionCooldownTicks;
    private int voidRecoveryCooldownTicks;
    private int teleportLockoutTicks;
    private BlockPos lastSafeGroundPos;

    private static final class BrainHolder {
        private static final Brain.Provider<EndWolf> PROVIDER = Brain.<EndWolf>provider(
                ImmutableList.of(ModSensorTypes.END_PACK.get()),
                wolf -> List.of());
    }

    public EndWolf(EntityType<? extends EndWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.END_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Pups never become miniature combat units.
        this.goalSelector.addGoal(1, new EndPupRetreatGoal(this));
        this.goalSelector.addGoal(5, new EndPupFollowAdultGoal(this));

        // Wild End Wolves remain socially attached to their pack.
        this.goalSelector.addGoal(6, new EndPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new EndPupPlayGoal(this));

        // Shared pack danger sits below Wolfism's universal family emergency.
        this.targetSelector.addGoal(3, new EndPackAssistGoal(this));
    }

    @Override
    protected Brain<EndWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<EndWolf> getBrain() {
        return (Brain<EndWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Never allow a same-pack target to survive into the combat loop.
        LivingEntity current = this.getTarget();
        if (current instanceof EndWolf endWolf && this.isEndPackmate(endWolf)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);

        // Adults may inherit a real threat from another End Wolf's sensor.
        if (this.canParticipateInWolfismCombat() && this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.END_PACK_THREAT.get())
                    .orElse(null);

            if (sharedThreat != null && this.isValidEndCombatTarget(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        // Belt-and-suspenders pup safety. AbstractWolfismWolf already blocks
        // baby combat globally, but this keeps the visible target state clean.
        if (this.isBaby() && this.getTarget() != null) {
            this.setTarget(null);
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.tickEndCooldowns();
        this.tickVoidAwareness(level);

        // Babies, sitting wolves and non-combat states never use tactical
        // teleports. Void Awareness may still remember safe ground for them.
        if (!this.canUseActiveWolfismAbility() || this.teleportLockoutTicks > 0) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null
                || target instanceof Creeper
                || !this.isValidEndCombatTarget(target)) {
            return;
        }

        // Stagger decision ticks by entity id so a large pack does not all run
        // its decision code on the exact same server tick.
        if (Math.floorMod(this.tickCount + this.getId(), ABILITY_DECISION_INTERVAL) != 0) {
            return;
        }

        // Priority: distant pursuit first, then coordinated pack positioning,
        // then ordinary short Ender Step.
        if (this.tryTeleportPursuit(level, target)) {
            return;
        }
        if (this.tryPackReposition(level, target)) {
            return;
        }
        this.tryEnderStep(level, target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof EndWolf other && this.isEndPackmate(other)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isEndPackmate(EndWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidEndCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.canAttack(target)
                || this.isAlliedTo(target)) {
            return false;
        }

        if (target instanceof EndWolf other && this.isEndPackmate(other)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    // ---------------------------------------------------------------------
    // 1) Void Awareness
    // ---------------------------------------------------------------------

    private void tickVoidAwareness(ServerLevel level) {
        // Remember safe ground only when the wolf is actually standing on a
        // stable landing area. This becomes the emergency return point.
        if (this.onGround()
                && this.tickCount % SAFE_POSITION_UPDATE_INTERVAL == 0
                && this.isSafeTeleportPosition(level, this.blockPosition(), true)) {
            this.lastSafeGroundPos = this.blockPosition();
        }

        if (this.voidRecoveryCooldownTicks > 0
                || this.lastSafeGroundPos == null
                || this.onGround()
                || this.fallDistance < 2.5F
                || level.dimension() != Level.END
                || !this.isVoidBelow(level, this.blockPosition(), VOID_SCAN_DEPTH)) {
            return;
        }

        BlockPos returnPos = this.lastSafeGroundPos;
        if (!this.isSafeTeleportPosition(level, returnPos, true)) {
            this.lastSafeGroundPos = null;
            return;
        }

        Vec3 destination = new Vec3(
                returnPos.getX() + 0.5D,
                returnPos.getY(),
                returnPos.getZ() + 0.5D);

        this.performSpatialTeleport(level, destination, 18, false);
        this.fallDistance = 0.0F;
        this.voidRecoveryCooldownTicks = VOID_RECOVERY_COOLDOWN_TICKS;
        this.teleportLockoutTicks = Math.max(this.teleportLockoutTicks, 20);
    }

    private boolean isVoidBelow(ServerLevel level, BlockPos origin, int depth) {
        int minY = Math.max(level.getMinY(), origin.getY() - depth);
        for (int y = origin.getY() - 1; y >= minY; --y) {
            BlockPos check = new BlockPos(origin.getX(), y, origin.getZ());
            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // 2) Ender Step - short tactical teleport
    // ---------------------------------------------------------------------

    private boolean tryEnderStep(ServerLevel level, LivingEntity target) {
        if (this.enderStepCooldownTicks > 0) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < ENDER_STEP_MIN_RANGE * ENDER_STEP_MIN_RANGE
                || distanceSqr > ENDER_STEP_MAX_RANGE * ENDER_STEP_MAX_RANGE) {
            return false;
        }

        // The step is meant to solve positioning. If the target is already
        // close and directly visible, ordinary wolf movement should handle it.
        boolean awkwardHeight = Math.abs(target.getY() - this.getY()) > 1.5D;
        boolean blockedSight = !this.getSensing().hasLineOfSight(target);
        boolean usefulDistance = distanceSqr >= 6.5D * 6.5D;
        if (!awkwardHeight && !blockedSight && !usefulDistance) {
            return false;
        }

        Vec3 safe = this.findSafePositionAroundTarget(level, target, 2.2D, this.getPreferredStepAngle(target));
        if (safe == null) {
            return false;
        }

        this.performSpatialTeleport(level, safe, 14, true);
        this.enderStepCooldownTicks = ENDER_STEP_COOLDOWN_TICKS;
        this.teleportLockoutTicks = 12;
        return true;
    }

    // ---------------------------------------------------------------------
    // 3) Teleport Pursuit - closes a large combat gap
    // ---------------------------------------------------------------------

    private boolean tryTeleportPursuit(ServerLevel level, LivingEntity target) {
        if (this.pursuitTeleportCooldownTicks > 0) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < PURSUIT_MIN_RANGE * PURSUIT_MIN_RANGE
                || distanceSqr > PURSUIT_MAX_RANGE * PURSUIT_MAX_RANGE) {
            return false;
        }

        // Arrive a few blocks from the target rather than directly inside melee
        // range. Normal navigation/melee finishes the interception.
        double approachAngle = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX());
        Vec3 safe = this.findSafePositionAroundTarget(level, target, 3.6D, approachAngle);
        if (safe == null) {
            return false;
        }

        this.performSpatialTeleport(level, safe, 20, true);
        this.pursuitTeleportCooldownTicks = PURSUIT_TELEPORT_COOLDOWN_TICKS;
        this.enderStepCooldownTicks = Math.max(this.enderStepCooldownTicks, 35);
        this.teleportLockoutTicks = 16;
        return true;
    }

    // ---------------------------------------------------------------------
    // 4) Pack Repositioning - coordinated surrounding positions
    // ---------------------------------------------------------------------

    private boolean tryPackReposition(ServerLevel level, LivingEntity target) {
        if (this.packRepositionCooldownTicks > 0) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < 3.5D * 3.5D || distanceSqr > 13.0D * 13.0D) {
            return false;
        }

        List<EndWolf> attackers = this.getPackAttackersOn(level, target);
        if (attackers.size() < 2) {
            return false;
        }

        attackers.sort(Comparator.comparingInt(EndWolf::getId));
        int index = attackers.indexOf(this);
        if (index < 0) {
            return false;
        }

        // Each attacker gets its own angular slot around the target. This makes
        // simultaneous pack teleports form a loose ring instead of an entity pile.
        double angle = (Math.PI * 2.0D * index / attackers.size())
                + (target.getId() % 8) * (Math.PI / 16.0D);
        double radius = 2.6D + (index % 2) * 0.45D;

        Vec3 safe = this.findSafePositionAroundTarget(level, target, radius, angle);
        if (safe == null) {
            return false;
        }

        this.performSpatialTeleport(level, safe, 12, true);
        this.packRepositionCooldownTicks = PACK_REPOSITION_COOLDOWN_TICKS + this.random.nextInt(40);
        this.enderStepCooldownTicks = Math.max(this.enderStepCooldownTicks, 30);
        this.teleportLockoutTicks = 12;
        return true;
    }

    private List<EndWolf> getPackAttackersOn(ServerLevel level, LivingEntity target) {
        List<EndWolf> attackers = new ArrayList<>();
        for (EndWolf candidate : level.getEntitiesOfClass(
                EndWolf.class,
                this.getBoundingBox().inflate(PACK_REPOSITION_SCAN_RANGE),
                wolf -> wolf.isAlive()
                        && !wolf.isBaby()
                        && this.isEndPackmate(wolf)
                        && wolf.getTarget() == target)) {
            attackers.add(candidate);
        }

        // The query predicate excludes this because isEndPackmate(this) is false.
        // Include self explicitly so the angular-slot calculation is stable.
        attackers.add(this);
        return attackers;
    }

    // ---------------------------------------------------------------------
    // Shared spatial helpers
    // ---------------------------------------------------------------------

    private double getPreferredStepAngle(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() > 1.0E-5D) {
            // Behind the target by default.
            return Math.atan2(-flat.z, -flat.x);
        }
        return Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX());
    }

    private Vec3 findSafePositionAroundTarget(
            ServerLevel level,
            LivingEntity target,
            double radius,
            double preferredAngle) {

        // Try the preferred slot first, then fan out around it. Radius also
        // expands slightly on the later attempts to escape blocked terrain.
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
            double currentRadius = radius + ring * 1.25D;
            for (double offset : angleOffsets) {
                double angle = preferredAngle + offset;
                double x = target.getX() + Math.cos(angle) * currentRadius;
                double z = target.getZ() + Math.sin(angle) * currentRadius;

                Vec3 safe = this.findSafeLandingNear(level, x, target.getY(), z);
                if (safe != null && !this.isTeleportDestinationOccupied(level, safe, target)) {
                    return safe;
                }
            }
        }

        return null;
    }

    private Vec3 findSafeLandingNear(ServerLevel level, double x, double referenceY, double z) {
        int startY = (int) Math.floor(referenceY) + TELEPORT_SEARCH_VERTICAL_RANGE;
        int endY = (int) Math.floor(referenceY) - TELEPORT_SEARCH_VERTICAL_RANGE;

        for (int y = startY; y >= endY; --y) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (this.isSafeTeleportPosition(level, pos, true)) {
                return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            }
        }

        return null;
    }

    /**
     * The core of Void Awareness. A legal destination needs empty feet/head,
     * no fluid, a real collision floor, and enough neighboring floor support
     * that the wolf is not being placed on the lip of a one-block void ledge.
     */
    private boolean isSafeTeleportPosition(ServerLevel level, BlockPos pos, boolean requireStableFloor) {
        if (pos.getY() <= level.getMinY() || pos.getY() + 1 >= level.getMaxY()) {
            return false;
        }

        BlockPos head = pos.above();
        BlockPos floor = pos.below();

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(head).isEmpty()
                || !level.getFluidState(floor).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            return false;
        }

        if (!requireStableFloor) {
            return true;
        }

        int supportedNeighbors = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            BlockPos neighborFloor = floor.offset(offset[0], 0, offset[1]);
            if (level.getFluidState(neighborFloor).isEmpty()
                    && !level.getBlockState(neighborFloor)
                    .getCollisionShape(level, neighborFloor)
                    .isEmpty()) {
                ++supportedNeighbors;
            }
        }

        return supportedNeighbors >= 2;
    }

    private boolean isTeleportDestinationOccupied(
            ServerLevel level,
            Vec3 destination,
            LivingEntity intendedTarget) {

        AABB landingBox = new AABB(
                destination.x - 0.45D,
                destination.y,
                destination.z - 0.45D,
                destination.x + 0.45D,
                destination.y + 1.25D,
                destination.z + 0.45D);

        return !level.getEntitiesOfClass(
                        LivingEntity.class,
                        landingBox,
                        entity -> entity != this && entity != intendedTarget && entity.isAlive())
                .isEmpty();
    }

    private void performSpatialTeleport(
            ServerLevel level,
            Vec3 destination,
            int particleCount,
            boolean faceTarget) {

        Vec3 origin = this.position();
        this.getNavigation().stop();
        this.setPos(destination.x, destination.y, destination.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;

        if (faceTarget && this.getTarget() != null) {
            this.getLookControl().setLookAt(this.getTarget(), 90.0F, 90.0F);
        }

        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.85F, 1.10F + this.random.nextFloat() * 0.12F);
        WolfVfx.sendParticles("end_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                origin.x, origin.y + 0.45D, origin.z,
                particleCount, 0.28D, 0.30D, 0.28D, 0.04D);
        WolfVfx.sendParticles("end_wolf", level,
                ParticleTypes.PORTAL,
                destination.x, destination.y + 0.45D, destination.z,
                particleCount + 4, 0.28D, 0.30D, 0.28D, 0.05D);
    }

    private void tickEndCooldowns() {
        if (this.enderStepCooldownTicks > 0) {
            --this.enderStepCooldownTicks;
        }
        if (this.pursuitTeleportCooldownTicks > 0) {
            --this.pursuitTeleportCooldownTicks;
        }
        if (this.packRepositionCooldownTicks > 0) {
            --this.packRepositionCooldownTicks;
        }
        if (this.voidRecoveryCooldownTicks > 0) {
            --this.voidRecoveryCooldownTicks;
        }
        if (this.teleportLockoutTicks > 0) {
            --this.teleportLockoutTicks;
        }
    }

    // Exposed for testing/debugging without adding synced state or new registry
    // entries. These values are server-authoritative and intentionally simple.
    public boolean isEnderStepReady() {
        return this.enderStepCooldownTicks <= 0;
    }

    public boolean isPursuitTeleportReady() {
        return this.pursuitTeleportCooldownTicks <= 0;
    }

    public boolean isPackRepositionReady() {
        return this.packRepositionCooldownTicks <= 0;
    }

    /**
     * End-native natural spawn rule.
     *
     * <p>The biome modifier selects the legal End biomes. This predicate then
     * requires the End dimension, solid End Stone underfoot, no fluid, and a
     * free block at the candidate position. It deliberately has no overworld
     * daylight/grass rule.</p>
     */
    public static boolean checkEndWolfSpawnRules(
            EntityType<EndWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (level.getLevel().dimension() != Level.END) {
            return false;
        }

        return level.getBlockState(pos.below()).is(Blocks.END_STONE)
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).isAir();
    }
}