package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.EarthChargeGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthDirtBlastGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthDirtShellGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthEarthquakeGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.EarthShockwaveGoal;
import net.ronm19.wolfism.entity.ai.sensor.EarthWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBlockTags;

/**
 * Earth Wolf: grounded tank, terrain controller and family frontliner.
 *
 * <p>Earth Wolf is deliberately physical. Charge uses his body, Shockwave and
 * Earthquake displace enemies through the ground, while Dirt Shell and Dirt
 * Blast manipulate real nearby dirt-class terrain. Temporary terrain movement
 * is tracked and restored so ordinary combat does not permanently scar a world.</p>
 */
public final class EarthWolf extends AbstractWolfismWolf {
    public static final int CHARGE_COOLDOWN_TICKS = 20 * 18;
    public static final int DIRT_SHELL_COOLDOWN_TICKS = 20 * 30;
    public static final int SHOCKWAVE_COOLDOWN_TICKS = 20 * 17;
    public static final int DIRT_BLAST_COOLDOWN_TICKS = 20 * 16;
    public static final int EARTHQUAKE_COOLDOWN_TICKS = 20 * 90;

    public static final float CHARGE_DAMAGE = 8.0F;
    public static final float SHOCKWAVE_DAMAGE = 6.0F;
    public static final float DIRT_BLAST_DAMAGE = 7.0F;
    public static final float EARTHQUAKE_WAVE_DAMAGE = 2.5F;

    public static final double SHOCKWAVE_RADIUS = 5.5D;
    public static final double EARTHQUAKE_RADIUS = 30.0D;

    private static final int CHARGE_DURATION_TICKS = 14;
    private static final double CHARGE_SPEED = 0.95D;
    private static final double CHARGE_IMPACT_RADIUS = 1.65D;
    private static final int DIRT_SHELL_DURATION_TICKS = 20 * 6;
    private static final int EARTHQUAKE_DURATION_TICKS = 72;
    private static final int EARTHQUAKE_WAVE_INTERVAL = 12;

    private final Set<Integer> chargeHitIds = new HashSet<>();
    private final List<TerrainMove> terrainMoves = new ArrayList<>();

    private int chargeTicks;
    private Vec3 chargeDirection = Vec3.ZERO;
    private int dirtShellTicks;
    private int earthquakeTicks;
    private int earthquakeWave;
    private DirtBlastMotion dirtBlast;

    private record TerrainMove(BlockPos source, BlockPos destination, BlockState state, long restoreGameTime) {
    }

    private static final class DirtBlastMotion {
        private final BlockPos source;
        private final BlockState state;
        private final LivingEntity target;
        private Vec3 position;
        private Vec3 velocity = Vec3.ZERO;
        private int ticks;

        private DirtBlastMotion(BlockPos source, BlockState state, LivingEntity target) {
            this.source = source;
            this.state = state;
            this.target = target;
            this.position = Vec3.atCenterOf(source).add(0.0D, 0.35D, 0.0D);
        }
    }

    private static final class BrainHolder {
        private static final Brain.Provider<EarthWolf> PROVIDER = Brain.<EarthWolf>provider(
                ImmutableList.of(ModSensorTypes.EARTH_PACK.get(), ModSensorTypes.EARTH_TERRAIN.get()),
                wolf -> List.of());
    }

    public EarthWolf(EntityType<? extends EarthWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.EARTH_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new EarthPupRetreatGoal(this));
        this.goalSelector.addGoal(2, new EarthEarthquakeGoal(this));
        this.goalSelector.addGoal(3, new EarthDirtShellGoal(this));
        // Terrain offense must outrank vanilla wolf melee movement or the melee
        // goal can continuously own MOVE and starve Dirt Blast before it starts.
        this.goalSelector.addGoal(4, new EarthDirtBlastGoal(this));
        this.goalSelector.addGoal(4, new EarthShockwaveGoal(this));
        this.goalSelector.addGoal(5, new EarthChargeGoal(this));
        this.goalSelector.addGoal(7, new EarthPupFollowAdultGoal(this));
        this.goalSelector.addGoal(8, new EarthPackCohesionGoal(this));
        this.goalSelector.addGoal(9, new EarthPupPlayGoal(this));

        this.targetSelector.addGoal(3, new EarthPackAssistGoal(this));
    }

    @Override
    protected Brain<EarthWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<EarthWolf> getBrain() {
        return (Brain<EarthWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickCooldown(ModMemoryModuleTypes.EARTH_CHARGE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.EARTH_DIRT_SHELL_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.EARTH_SHOCKWAVE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.EARTH_DIRT_BLAST_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.EARTH_EARTHQUAKE_COOLDOWN.get());

        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelCharge();
            this.cancelDirtBlast(true);
            this.earthquakeTicks = 0;
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel level) {
            this.tickTemporaryTerrain(level);

            if (this.chargeTicks > 0) {
                this.tickCharge(level);
            }
            if (this.dirtBlast != null) {
                this.tickDirtBlast(level);
            }
            if (this.earthquakeTicks > 0) {
                this.tickEarthquake(level);
            }
            if (this.dirtShellTicks > 0) {
                --this.dirtShellTicks;
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            // TerrainMove state is intentionally transient. Restore anything this
            // wolf still owns before death, dimension transfer, despawn or chunk
            // unload so temporary combat terrain cannot be stranded in the world.
            this.cancelCharge();
            this.cancelDirtBlast(true);
            this.restoreAllOwnedTerrain(level);
        }
        super.remove(reason);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    /** Earth Wolf prefers solid natural ground and is at his best on it. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        BlockState below = level.getBlockState(pos.below());
        if (isManipulableEarthState(below) || below.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return 4.0F + super.getWalkTargetValue(pos, level);
        }
        return super.getWalkTargetValue(pos, level);
    }

    // ---------------------------------------------------------------------
    // Charge
    // ---------------------------------------------------------------------

    public boolean canStartChargeAgainst(LivingEntity target) {
        if (!this.canUseGroundAbility()
                || target == null
                || target instanceof Creeper
                || !this.isValidEarthCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.EARTH_CHARGE_COOLDOWN.get())
                || this.isAbilityTargetReservedByPack(target, ModMemoryModuleTypes.EARTH_CHARGE_TARGET.get())
                || this.chargeTicks > 0
                || this.dirtBlast != null
                || this.earthquakeTicks > 0) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= EarthChargeGoal.MIN_START_DISTANCE_SQR
                && distance <= EarthChargeGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target);
    }

    public boolean beginCharge(LivingEntity target) {
        if (!this.canStartChargeAgainst(target)) {
            return false;
        }

        Vec3 flat = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-5D) {
            return false;
        }

        this.chargeDirection = flat.normalize();
        this.chargeTicks = CHARGE_DURATION_TICKS;
        this.chargeHitIds.clear();
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_CHARGE_TARGET.get(), target);
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_CHARGE_COOLDOWN.get(), CHARGE_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setSprinting(true);
        return true;
    }

    private void tickCharge(ServerLevel level) {
        LivingEntity target = this.getBrain().getMemory(ModMemoryModuleTypes.EARTH_CHARGE_TARGET.get()).orElse(null);
        if (target == null || !target.isAlive() || !this.canUseGroundAbility()) {
            this.cancelCharge();
            return;
        }

        Vec3 desired = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (desired.lengthSqr() > 1.0E-5D) {
            Vec3 steered = this.chargeDirection.scale(0.78D).add(desired.normalize().scale(0.22D));
            if (steered.lengthSqr() > 1.0E-5D) {
                this.chargeDirection = steered.normalize();
            }
        }

        this.faceDirection(this.chargeDirection);
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(this.chargeDirection.x * CHARGE_SPEED, current.y, this.chargeDirection.z * CHARGE_SPEED);
        this.hurtMarked = true;

        List<LivingEntity> impacts = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(CHARGE_IMPACT_RADIUS),
                candidate -> candidate != this
                        && !this.chargeHitIds.contains(candidate.getId())
                        && this.isValidEarthCombatTarget(candidate)
                        && !(candidate instanceof Creeper));

        for (LivingEntity impact : impacts) {
            this.chargeHitIds.add(impact.getId());
            if (impact.hurtServer(level, this.damageSources().mobAttack(this), CHARGE_DAMAGE)) {
                Vec3 push = impact.position().subtract(this.position());
                if (push.lengthSqr() < 1.0E-5D) {
                    push = this.chargeDirection;
                } else {
                    push = push.normalize();
                }
                impact.push(push.x * 2.15D, 0.35D, push.z * 2.15D);
            }
            this.sendGroundBurst(level, impact.blockPosition(), 14, 0.65D);
        }

        if (this.horizontalCollision || --this.chargeTicks <= 0) {
            this.cancelCharge();
        }
    }

    public void cancelCharge() {
        this.chargeTicks = 0;
        this.chargeDirection = Vec3.ZERO;
        this.chargeHitIds.clear();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.EARTH_CHARGE_TARGET.get());
        this.setSprinting(false);
    }

    public boolean isCharging() {
        return this.chargeTicks > 0;
    }

    // ---------------------------------------------------------------------
    // Dirt Shell
    // ---------------------------------------------------------------------

    public boolean canStartDirtShell() {
        if (!this.canUseGroundAbility()
                || this.dirtShellTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.EARTH_DIRT_SHELL_COOLDOWN.get())
                || !this.canManipulateTerrain()) {
            return false;
        }

        LivingEntity anchor = this.selectDirtShellAnchor();
        return anchor != null && this.hasManipulableEarthNear(anchor.blockPosition(), 5, 3);
    }

    public boolean beginDirtShell() {
        if (!this.canStartDirtShell() || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        LivingEntity anchor = this.selectDirtShellAnchor();
        if (anchor == null) {
            return false;
        }

        int terrainMoveStart = this.terrainMoves.size();
        int moved = this.buildDirtShell(level, anchor.blockPosition());
        Wolfism.LOGGER.info(
                "[EarthTerrain] DirtShell wolf={} anchor={} terrainScore={} moved={}",
                this.getId(),
                anchor.blockPosition(),
                this.getBrain().getMemory(ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get()).orElse(-1),
                moved);
        if (moved < 4) {
            this.restoreTerrainMovesFrom(level, terrainMoveStart);
            return false;
        }

        this.dirtShellTicks = DIRT_SHELL_DURATION_TICKS;
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_DIRT_SHELL_COOLDOWN.get(), DIRT_SHELL_COOLDOWN_TICKS);
        this.sendGroundBurst(level, anchor.blockPosition(), 24, 1.25D);
        return true;
    }

    private LivingEntity selectDirtShellAnchor() {
        LivingEntity protectedFamily = this.getProtectedFamilyMember();
        if (protectedFamily != null
                && protectedFamily.isAlive()
                && this.distanceToSqr(protectedFamily) <= 10.0D * 10.0D) {
            return protectedFamily;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.distanceToSqr(owner) <= 10.0D * 10.0D) {
            List<LivingEntity> threats = this.findCombatThreatsAround(owner, 6.0D);
            if (!threats.isEmpty() && owner.getHealth() <= owner.getMaxHealth() * 0.75F) {
                return owner;
            }
        }

        if (this.getTarget() != null && this.distanceToSqr(this.getTarget()) <= 7.0D * 7.0D) {
            return this;
        }
        return null;
    }

    private int buildDirtShell(ServerLevel level, BlockPos anchor) {
        int[][] destinations = {
                {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2},
                {2, 0, 1}, {2, 0, -1}, {-2, 0, 1}, {-2, 0, -1},
                {1, 0, 2}, {-1, 0, 2}, {1, 0, -2}, {-1, 0, -2},
                {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2}
        };

        Set<BlockPos> reservedSources = new HashSet<>();
        int moved = 0;
        for (int[] offset : destinations) {
            BlockPos destination = anchor.offset(offset[0], offset[1], offset[2]);
            if (!level.getBlockState(destination).isAir()) {
                continue;
            }

            if (this.tryMoveTerrainToDestination(
                    level,
                    anchor,
                    destination,
                    reservedSources,
                    3,
                    7,
                    DIRT_SHELL_DURATION_TICKS,
                    8)) {
                ++moved;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------------
    // Shockwave
    // ---------------------------------------------------------------------

    public boolean canStartShockwave() {
        if (!this.canUseGroundAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.EARTH_SHOCKWAVE_COOLDOWN.get())
                || this.earthquakeTicks > 0) {
            return false;
        }
        return !this.findCombatThreatsAround(this, SHOCKWAVE_RADIUS).isEmpty();
    }

    public boolean performShockwave() {
        if (!this.canStartShockwave() || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        List<LivingEntity> threats = this.findCombatThreatsAround(this, SHOCKWAVE_RADIUS);
        if (threats.isEmpty()) {
            return false;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_SHOCKWAVE_COOLDOWN.get(), SHOCKWAVE_COOLDOWN_TICKS);
        for (LivingEntity threat : threats) {
            boolean hurt = threat.hurtServer(level, this.damageSources().mobAttack(this), SHOCKWAVE_DAMAGE);
            if (hurt && threat.isAlive()) {
                threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 24, 3));
            }
            Vec3 away = threat.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-5D) {
                away = new Vec3(0.0D, 0.0D, 1.0D);
            } else {
                away = away.normalize();
            }
            threat.push(away.x * 1.45D, 0.42D, away.z * 1.45D);
        }

        this.sendShockwaveRings(level, SHOCKWAVE_RADIUS);

        // Shockwave is an EARTH ability, not just a particle ring. Heave real
        // nearby dirt/grass into short-lived columns so ordinary close combat
        // visibly manipulates the terrain even when Dirt Blast/Earthquake are
        // not the selected ability. The same ownership/restoration system used
        // by Dirt Shell and Earthquake prevents permanent terrain scarring.
        if (this.canManipulateTerrain()) {
            int moved = this.raiseQuakeSpikes(level, SHOCKWAVE_RADIUS, 6, 80);
            Wolfism.LOGGER.info(
                    "[EarthTerrain] Shockwave wolf={} pos={} terrainScore={} requested=6 moved={}",
                    this.getId(),
                    this.blockPosition(),
                    this.getBrain().getMemory(ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get()).orElse(-1),
                    moved);
        } else {
            Wolfism.LOGGER.info("[EarthTerrain] Shockwave wolf={} terrain manipulation blocked", this.getId());
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Dirt Blast -- a real moving block state, not a projectile entity
    // ---------------------------------------------------------------------

    public boolean canStartDirtBlastAgainst(LivingEntity target) {
        if (!this.canUseGroundAbility()
                || !this.canManipulateTerrain()
                || target == null
                || target instanceof Creeper
                || !this.isValidEarthCombatTarget(target)
                || this.dirtBlast != null
                || this.isCharging()
                || this.earthquakeTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.EARTH_DIRT_BLAST_COOLDOWN.get())
                || this.isAbilityTargetReservedByPack(target, ModMemoryModuleTypes.EARTH_DIRT_BLAST_TARGET.get())) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= EarthDirtBlastGoal.MIN_START_DISTANCE_SQR
                && distance <= EarthDirtBlastGoal.MAX_START_DISTANCE_SQR
                && this.hasManipulableEarthNear(this.blockPosition(), 4, 3);
    }

    public boolean beginDirtBlast(LivingEntity target) {
        if (!this.canStartDirtBlastAgainst(target) || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        Set<BlockPos> rejectedSources = new HashSet<>();
        BlockPos source = null;
        BlockState state = null;
        for (int attempt = 0; attempt < 10; ++attempt) {
            BlockPos candidate = this.findPreferredTerrainSource(
                    level, this.blockPosition(), rejectedSources, 1, 6);
            if (candidate == null) {
                break;
            }

            BlockState candidateState = level.getBlockState(candidate);
            if (this.extractTerrainBlock(level, candidate, candidateState)) {
                source = candidate.immutable();
                state = candidateState;
                break;
            }
            rejectedSources.add(candidate.immutable());
        }

        if (source == null || state == null) {
            Wolfism.LOGGER.info(
                    "[EarthTerrain] DirtBlast wolf={} failed after {} candidate(s); terrainScore={}",
                    this.getId(),
                    rejectedSources.size(),
                    this.getBrain().getMemory(ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get()).orElse(-1));
            return false;
        }

        Wolfism.LOGGER.info(
                "[EarthTerrain] DirtBlast wolf={} extracted source={} state={} target={}",
                this.getId(), source, state, target.getId());
        this.dirtBlast = new DirtBlastMotion(source, state, target);
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_DIRT_BLAST_TARGET.get(), target);
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_DIRT_BLAST_COOLDOWN.get(), DIRT_BLAST_COOLDOWN_TICKS);
        this.getNavigation().stop();
        return true;
    }

    private void tickDirtBlast(ServerLevel level) {
        DirtBlastMotion motion = this.dirtBlast;
        if (motion == null) {
            return;
        }

        LivingEntity target = motion.target;
        if (!target.isAlive() || !this.isValidEarthCombatTarget(target) || ++motion.ticks > 46) {
            this.cancelDirtBlast(true);
            return;
        }

        /*
         * Earth-bending presentation:
         *   1) pull the real terrain block out of the ground;
         *   2) smoothly lift it beside/in front of Earth Wolf;
         *   3) hold it for a beat while he aims;
         *   4) accelerate the earth mass through world-space toward the target.
         *
         * The travelling mass is deliberately NOT placed as a solid block every
         * tick. Doing that made the old version jump between BlockPos cells and
         * could physically occupy the caster's body. The terrain is still real:
         * the source block is removed at cast time and restored after the cast,
         * while a dense block-particle mass represents it continuously in flight.
         */
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        Vec3 flatToTarget = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());
        if (flatToTarget.lengthSqr() < 1.0E-5D) {
            flatToTarget = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flatToTarget = flatToTarget.normalize();
        }
        Vec3 side = new Vec3(-flatToTarget.z, 0.0D, flatToTarget.x);
        Vec3 holdPoint = this.position()
                .add(flatToTarget.scale(1.35D))
                .add(side.scale(0.70D))
                .add(0.0D, 1.35D, 0.0D);

        if (motion.ticks <= 7) {
            // Smooth ease-out lift from the extracted ground block to the caster.
            double t = motion.ticks / 7.0D;
            double eased = 1.0D - Math.pow(1.0D - t, 3.0D);
            Vec3 sourceCenter = Vec3.atCenterOf(motion.source).add(0.0D, 0.35D, 0.0D);
            motion.position = sourceCenter.lerp(holdPoint, eased);
            this.sendDirtBlastMass(level, motion.position, motion.state, 12, 0.18D);
            return;
        }

        if (motion.ticks <= 11) {
            // Short telekinetic hold. The tiny bob makes the chunk feel controlled
            // rather than frozen in mid-air.
            double bob = Math.sin((motion.ticks - 7) * 0.9D) * 0.07D;
            motion.position = holdPoint.add(0.0D, bob, 0.0D);
            this.sendDirtBlastMass(level, motion.position, motion.state, 15, 0.20D);
            this.getLookControl().setLookAt(target, 45.0F, 45.0F);
            return;
        }

        // Lead moving targets slightly, then smoothly steer the earth mass toward
        // that predicted point. This gives the throw momentum without making it a
        // perfect homing missile.
        Vec3 predictedTarget = targetCenter.add(target.getDeltaMovement().scale(2.5D));
        Vec3 desired = predictedTarget.subtract(motion.position);
        if (desired.lengthSqr() < 1.0E-5D) {
            this.impactDirtBlast(level, target);
            this.cancelDirtBlast(true);
            return;
        }

        double desiredSpeed = Math.min(1.25D, 0.72D + desired.length() * 0.035D);
        Vec3 desiredVelocity = desired.normalize().scale(desiredSpeed);
        if (motion.velocity.lengthSqr() < 1.0E-5D) {
            motion.velocity = desiredVelocity.scale(0.72D);
        } else {
            motion.velocity = motion.velocity.scale(0.70D).add(desiredVelocity.scale(0.30D));
        }

        Vec3 next = motion.position.add(motion.velocity);

        // Stop on real terrain/walls instead of phasing the earth mass through
        // structures. The source is safely restored because the travelling visual
        // never became a solid world block.
        BlockPos nextBlock = BlockPos.containing(next);
        if (!level.getBlockState(nextBlock).isAir()
                && !level.getBlockState(nextBlock).getCollisionShape(level, nextBlock).isEmpty()) {
            this.sendGroundBurst(level, nextBlock, 12, 0.45D);
            this.cancelDirtBlast(true);
            return;
        }

        motion.position = next;
        this.sendDirtBlastMass(level, motion.position, motion.state, 14, 0.17D);
        this.sendDirtBlastTrail(level, motion.position, motion.velocity, motion.state);

        // Only an actual hostile target can receive impact damage. The caster,
        // owner, and Wolfism family can never be hit because isValidEarthCombatTarget
        // rejects them and the travelling earth mass itself has no solid collision.
        if (targetCenter.distanceToSqr(motion.position) <= 1.15D * 1.15D) {
            this.impactDirtBlast(level, target);
            this.cancelDirtBlast(true);
        }
    }

    private void sendDirtBlastMass(
            ServerLevel level,
            Vec3 position,
            BlockState state,
            int particleCount,
            double spread) {
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        level.sendParticles(
                particle,
                position.x,
                position.y,
                position.z,
                particleCount,
                spread,
                spread,
                spread,
                0.012D);
    }

    private void sendDirtBlastTrail(ServerLevel level, Vec3 position, Vec3 velocity, BlockState state) {
        Vec3 back = velocity.lengthSqr() < 1.0E-5D ? Vec3.ZERO : velocity.normalize().scale(-0.38D);
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 1; i <= 3; ++i) {
            Vec3 trail = position.add(back.scale(i));
            level.sendParticles(
                    particle,
                    trail.x,
                    trail.y,
                    trail.z,
                    3,
                    0.08D,
                    0.08D,
                    0.08D,
                    0.01D);
        }
    }

    private void impactDirtBlast(ServerLevel level, LivingEntity target) {
        if (target == null || !target.isAlive() || !this.isValidEarthCombatTarget(target)) {
            return;
        }
        if (target.hurtServer(level, this.damageSources().mobAttack(this), DIRT_BLAST_DAMAGE)) {
            Vec3 away = target.position().subtract(this.position());
            if (away.lengthSqr() > 1.0E-5D) {
                away = away.normalize();
                target.push(away.x * 0.8D, 0.30D, away.z * 0.8D);
            }
        }
        this.sendGroundBurst(level, target.blockPosition(), 18, 0.75D);
    }

    public void cancelDirtBlast(boolean restoreSource) {
        DirtBlastMotion motion = this.dirtBlast;
        this.dirtBlast = null;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.EARTH_DIRT_BLAST_TARGET.get());

        if (motion == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }

        // Dirt Blast no longer places solid blocks along its flight path. The
        // extracted source is therefore the only world block we need to restore.
        // If something else filled the source while the cast was active, never
        // overwrite that change.
        if (restoreSource && level.getBlockState(motion.source).isAir()) {
            this.placeTerrainBlock(level, motion.source, motion.state);
        }
    }

    public boolean isDirtBlastActive() {
        return this.dirtBlast != null;
    }

    // ---------------------------------------------------------------------
    // Earthquake ultimate
    // ---------------------------------------------------------------------

    public boolean canStartEarthquake() {
        if (!this.canUseGroundAbility()
                || this.earthquakeTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.EARTH_EARTHQUAKE_COOLDOWN.get())
                || this.hasNearbyEarthquakeFromPack()) {
            return false;
        }

        List<LivingEntity> threats = this.findCombatThreatsAround(this, EARTHQUAKE_RADIUS);
        if (threats.size() >= 3) {
            return true;
        }

        // A single heavyweight enemy (Ravager-tier health and above) is also
        // worthy of the ultimate. This makes the Earthquake observable in real
        // boss-like fights instead of requiring three separate mobs every time.
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null
                && this.isValidEarthCombatTarget(currentTarget)
                && currentTarget.getMaxHealth() >= 60.0F
                && this.distanceToSqr(currentTarget) <= 18.0D * 18.0D) {
            return true;
        }

        LivingEntity protectedFamily = this.getProtectedFamilyMember();
        return protectedFamily != null
                && protectedFamily.isAlive()
                && this.findCombatThreatsAround(protectedFamily, 8.0D).size() >= 2;
    }

    public boolean beginEarthquake() {
        if (!this.canStartEarthquake() || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        this.cancelCharge();
        this.cancelDirtBlast(true);
        this.earthquakeTicks = EARTHQUAKE_DURATION_TICKS;
        this.earthquakeWave = 0;
        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_EARTHQUAKE_COOLDOWN.get(), EARTHQUAKE_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.15D, 1.0D, 0.15D));
        this.sendGroundBurst(level, this.blockPosition(), 30, 1.5D);
        return true;
    }

    private void tickEarthquake(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.35D, 1.0D, 0.35D));

        if (this.earthquakeTicks % EARTHQUAKE_WAVE_INTERVAL == 0) {
            ++this.earthquakeWave;
            double waveRadius = Math.min(EARTHQUAKE_RADIUS, 5.0D + this.earthquakeWave * 5.0D);
            this.performEarthquakeWave(level, waveRadius, this.earthquakeTicks <= EARTHQUAKE_WAVE_INTERVAL);
        }

        if (--this.earthquakeTicks <= 0) {
            this.earthquakeTicks = 0;
        }
    }

    private void performEarthquakeWave(ServerLevel level, double waveRadius, boolean finalWave) {
        float damage = finalWave ? EARTHQUAKE_WAVE_DAMAGE * 1.6F : EARTHQUAKE_WAVE_DAMAGE;
        List<LivingEntity> threats = this.findCombatThreatsAround(this, waveRadius);
        for (LivingEntity threat : threats) {
            threat.hurtServer(level, this.damageSources().mobAttack(this), damage);
            threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 28, finalWave ? 4 : 2));

            Vec3 away = threat.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-5D) {
                double angle = this.random.nextDouble() * Math.PI * 2.0D;
                away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            } else {
                away = away.normalize();
            }
            double horizontal = finalWave ? 1.15D : 0.65D;
            threat.push(away.x * horizontal, finalWave ? 0.58D : 0.30D, away.z * horizontal);
        }

        this.sendShockwaveRings(level, waveRadius);
        if (this.canManipulateTerrain()) {
            int requested = finalWave ? 8 : 4;
            int moved = this.raiseQuakeSpikes(level, waveRadius, requested, 100);
            Wolfism.LOGGER.info(
                    "[EarthTerrain] Earthquake wolf={} radius={} requested={} moved={}",
                    this.getId(), waveRadius, requested, moved);
        }
    }

    public boolean isEarthquakeActive() {
        return this.earthquakeTicks > 0;
    }

    private boolean hasNearbyEarthquakeFromPack() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return !level.getEntitiesOfClass(
                EarthWolf.class,
                this.getBoundingBox().inflate(24.0D),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isEarthPackmate(candidate)
                        && candidate.isEarthquakeActive())
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // Terrain ownership/restoration
    // ---------------------------------------------------------------------

    public boolean hasStrongTerrainConnection() {
        return this.getBrain().getMemory(ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get()).orElse(0) >= 4;
    }

    public static boolean isManipulableEarthState(BlockState state) {
        // Do not rely exclusively on a datapack tag for the core Earth Wolf fantasy.
        // The explicit vanilla surface set guarantees that ordinary grass/dirt terrain
        // works even if a generated tag is missing, stale, or not loaded as expected.
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD)
                || state.is(ModBlockTags.EARTH_MANIPULABLE)
                || state.is(BlockTags.DIRT);
    }

    private boolean canManipulateTerrain() {
        return this.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    private boolean hasManipulableEarthNear(BlockPos center, int horizontalRadius, int verticalRadius) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        for (int dx = -horizontalRadius; dx <= horizontalRadius; ++dx) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; ++dz) {
                for (int dy = verticalRadius; dy >= -verticalRadius; --dy) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isManipulableEarthState(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockPos findPreferredTerrainSource(
            ServerLevel level,
            BlockPos center,
            Set<BlockPos> excluded,
            int minRadius,
            int maxRadius) {
        BlockPos remembered = this.getBrain()
                .getMemory(ModMemoryModuleTypes.EARTH_NEAREST_TERRAIN.get())
                .orElse(null);
        if (remembered != null && !excluded.contains(remembered)) {
            int dx = Math.abs(remembered.getX() - center.getX());
            int dz = Math.abs(remembered.getZ() - center.getZ());
            int horizontalRadius = Math.max(dx, dz);
            if (horizontalRadius >= minRadius
                    && horizontalRadius <= maxRadius
                    && isManipulableEarthState(level.getBlockState(remembered))
                    && level.getBlockState(remembered.above()).isAir()) {
                return remembered.immutable();
            }
        }
        return this.findTerrainSource(level, center, excluded, minRadius, maxRadius);
    }

    private boolean isOwnedTemporaryTerrain(BlockPos pos) {
        for (TerrainMove move : this.terrainMoves) {
            if (move.destination().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findTerrainSource(
            ServerLevel level,
            BlockPos center,
            Set<BlockPos> excluded,
            int minRadius,
            int maxRadius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = minRadius; radius <= maxRadius; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 5; dy >= -10; --dy) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (excluded.contains(pos) || this.isOwnedTemporaryTerrain(pos)) {
                            continue;
                        }
                        if (isManipulableEarthState(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
                            candidates.add(pos.immutable());
                            break;
                        }
                    }
                }
            }
            if (!candidates.isEmpty()) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(this.random.nextInt(candidates.size()));
    }

    private boolean moveTerrainTemporarily(ServerLevel level, BlockPos source, BlockPos destination, int durationTicks) {
        BlockState state = level.getBlockState(source);
        if (!isManipulableEarthState(state) || !level.getBlockState(destination).isAir()) {
            return false;
        }

        if (!this.extractTerrainBlock(level, source, state)) {
            return false;
        }
        if (!this.placeTerrainBlock(level, destination, state)) {
            // The source was successfully extracted but the destination rejected
            // placement. Roll back immediately rather than silently losing terrain.
            if (level.getBlockState(source).isAir()) {
                this.placeTerrainBlock(level, source, state);
            }
            return false;
        }

        this.terrainMoves.add(new TerrainMove(
                source.immutable(),
                destination.immutable(),
                state,
                level.getGameTime() + durationTicks));
        return true;
    }

    /**
     * Server-authoritative terrain extraction with post-condition verification.
     * This mirrors the reliability hardening used by Dire Wolf demolition: a
     * block operation is only accepted after the world actually reflects it.
     */
    private boolean extractTerrainBlock(ServerLevel level, BlockPos source, BlockState expected) {
        if (!isManipulableEarthState(expected) || !level.getBlockState(source).equals(expected)) {
            return false;
        }

        if (level.removeBlock(source, false) && level.getBlockState(source).isAir()) {
            return true;
        }
        if (level.destroyBlock(source, false, this) && level.getBlockState(source).isAir()) {
            return true;
        }

        level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
        return level.getBlockState(source).isAir();
    }

    /** Places terrain and verifies that the requested state really exists there. */
    private boolean placeTerrainBlock(ServerLevel level, BlockPos destination, BlockState state) {
        if (!level.getBlockState(destination).isAir()) {
            return false;
        }
        level.setBlockAndUpdate(destination, state);
        return level.getBlockState(destination).equals(state);
    }

    /** Removes only a temporary block still owned by this Earth Wolf. */
    private boolean clearOwnedTerrainBlock(ServerLevel level, BlockPos pos, BlockState expected) {
        if (!level.getBlockState(pos).equals(expected)) {
            return false;
        }
        if (level.removeBlock(pos, false) && level.getBlockState(pos).isAir()) {
            return true;
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return level.getBlockState(pos).isAir();
    }

    private void restoreTerrainMovesFrom(ServerLevel level, int startIndex) {
        for (int i = this.terrainMoves.size() - 1; i >= startIndex; --i) {
            TerrainMove move = this.terrainMoves.remove(i);
            if (level.getBlockState(move.destination()).equals(move.state())) {
                this.clearOwnedTerrainBlock(level, move.destination(), move.state());
            }
            if (level.getBlockState(move.source()).isAir()) {
                this.placeTerrainBlock(level, move.source(), move.state());
            }
        }
    }

    private void restoreAllOwnedTerrain(ServerLevel level) {
        for (int i = this.terrainMoves.size() - 1; i >= 0; --i) {
            TerrainMove move = this.terrainMoves.remove(i);
            if (!level.getBlockState(move.destination()).equals(move.state())) {
                continue;
            }

            this.clearOwnedTerrainBlock(level, move.destination(), move.state());
            if (level.getBlockState(move.source()).isAir()) {
                this.placeTerrainBlock(level, move.source(), move.state());
            }
        }
    }

    private void tickTemporaryTerrain(ServerLevel level) {
        if (this.terrainMoves.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        for (int i = this.terrainMoves.size() - 1; i >= 0; --i) {
            TerrainMove move = this.terrainMoves.get(i);
            if (now < move.restoreGameTime()) {
                continue;
            }

            BlockState destinationState = level.getBlockState(move.destination());
            boolean destinationStillOurs = destinationState.equals(move.state());
            if (destinationStillOurs) {
                boolean cleared = this.clearOwnedTerrainBlock(level, move.destination(), move.state());

                // Restore only when the temporary block is still ours and was
                // actually removed. If a player mined/replaced it, restoring the
                // source would duplicate material. Never overwrite source changes.
                if (cleared && level.getBlockState(move.source()).isAir()) {
                    this.placeTerrainBlock(level, move.source(), move.state());
                }
            }
            this.terrainMoves.remove(i);
        }
    }

    private int raiseQuakeSpikes(ServerLevel level, double radius, int count, int durationTicks) {
        int moved = 0;
        Set<BlockPos> reservedSources = new HashSet<>();
        Set<BlockPos> reservedDestinations = new HashSet<>();

        // A requested spike used to get only one random chance. One blocked probe or
        // unusable source therefore made the visible terrain count depend heavily on
        // luck. Spread the first probes evenly around the wave, then retry with
        // progressively jittered positions until the requested count is filled or a
        // generous safety budget is exhausted.
        int maxAttempts = Math.max(24, count * 10);
        double baseAngle = this.random.nextDouble() * Math.PI * 2.0D;
        for (int attempt = 0; attempt < maxAttempts && moved < count; ++attempt) {
            int slot = attempt % Math.max(1, count);
            int retryBand = attempt / Math.max(1, count);
            double angle = baseAngle
                    + (Math.PI * 2.0D * slot / Math.max(1, count))
                    + (retryBand * 0.17D * ((slot & 1) == 0 ? 1.0D : -1.0D));
            double distanceFactor = 0.55D + 0.10D * (retryBand % 4);
            double distance = Math.max(2.5D, radius * Math.min(0.90D, distanceFactor));

            BlockPos probe = BlockPos.containing(
                    this.getX() + Math.cos(angle) * distance,
                    this.getY(),
                    this.getZ() + Math.sin(angle) * distance);
            BlockPos spikeBase = this.findNearbySurfaceEarth(level, probe, 2);
            if (spikeBase == null) {
                continue;
            }

            BlockPos destination = spikeBase.above();
            if (reservedDestinations.contains(destination)
                    || !level.getBlockState(destination).isAir()) {
                continue;
            }

            if (this.tryMoveSurfaceTerrainToDestination(
                    level,
                    spikeBase,
                    destination,
                    reservedSources,
                    2,
                    7,
                    durationTicks,
                    10)) {
                reservedDestinations.add(destination.immutable());
                ++moved;
            }
        }
        return moved;
    }

    private boolean tryMoveTerrainToDestination(
            ServerLevel level,
            BlockPos sourceCenter,
            BlockPos destination,
            Set<BlockPos> reservedSources,
            int minRadius,
            int maxRadius,
            int durationTicks,
            int maxAttempts) {
        if (!level.getBlockState(destination).isAir()) {
            return false;
        }

        Set<BlockPos> rejected = new HashSet<>(reservedSources);
        for (int attempt = 0; attempt < maxAttempts; ++attempt) {
            BlockPos source = this.findTerrainSource(
                    level, sourceCenter, rejected, minRadius, maxRadius);
            if (source == null) {
                return false;
            }
            if (this.moveTerrainTemporarily(level, source, destination, durationTicks)) {
                reservedSources.add(source.immutable());
                return true;
            }
            rejected.add(source.immutable());
        }
        return false;
    }

    private boolean tryMoveSurfaceTerrainToDestination(
            ServerLevel level,
            BlockPos sourceCenter,
            BlockPos destination,
            Set<BlockPos> reservedSources,
            int minRadius,
            int maxRadius,
            int durationTicks,
            int maxAttempts) {
        if (!level.getBlockState(destination).isAir()) {
            return false;
        }

        Set<BlockPos> rejected = new HashSet<>(reservedSources);
        for (int attempt = 0; attempt < maxAttempts; ++attempt) {
            BlockPos source = this.findSurfaceTerrainSource(
                    level, sourceCenter, rejected, minRadius, maxRadius);
            if (source == null) {
                return false;
            }
            if (this.moveTerrainTemporarily(level, source, destination, durationTicks)) {
                reservedSources.add(source.immutable());
                return true;
            }
            rejected.add(source.immutable());
        }
        return false;
    }

    private BlockPos findNearbySurfaceEarth(ServerLevel level, BlockPos probe, int horizontalRadius) {
        BlockPos direct = this.findSurfaceEarth(level, probe);
        if (direct != null) {
            return direct;
        }

        for (int radius = 1; radius <= horizontalRadius; ++radius) {
            List<BlockPos> candidates = new ArrayList<>();
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos surface = this.findSurfaceEarth(level, probe.offset(dx, 0, dz));
                    if (surface != null && level.getBlockState(surface.above()).isAir()) {
                        candidates.add(surface.immutable());
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(this.random.nextInt(candidates.size()));
            }
        }
        return null;
    }

    private BlockPos findSurfaceTerrainSource(
            ServerLevel level,
            BlockPos center,
            Set<BlockPos> excluded,
            int minRadius,
            int maxRadius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = minRadius; radius <= maxRadius; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos probe = center.offset(dx, 0, dz);
                    BlockPos surface = this.findSurfaceEarth(level, probe);
                    if (surface != null && !excluded.contains(surface)) {
                        candidates.add(surface);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                break;
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(this.random.nextInt(candidates.size())).immutable();
    }

    private BlockPos findSurfaceEarth(ServerLevel level, BlockPos probe) {
        for (int dy = 8; dy >= -16; --dy) {
            BlockPos pos = probe.offset(0, dy, 0);
            if (!this.isOwnedTemporaryTerrain(pos)
                    && isManipulableEarthState(level.getBlockState(pos))
                    && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Combat/pack helpers
    // ---------------------------------------------------------------------

    private boolean canUseGroundAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.onGround()
                && !this.isInWater();
    }

    public boolean isEarthPackmate(EarthWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidEarthCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof EarthWolf earthWolf && this.isEarthPackmate(earthWolf)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private List<LivingEntity> findCombatThreatsAround(LivingEntity anchor, double radius) {
        if (!(this.level() instanceof ServerLevel level) || anchor == null) {
            return List.of();
        }
        LivingEntity currentTarget = this.getTarget();
        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        return level.getEntitiesOfClass(
                LivingEntity.class,
                anchor.getBoundingBox().inflate(radius),
                candidate -> candidate != this
                        && !(candidate instanceof Creeper)
                        && this.isValidEarthCombatTarget(candidate)
                        && (candidate == currentTarget
                                || candidate == familyTarget
                                || (currentTarget != null && candidate instanceof Enemy)));
    }

    private boolean isAbilityTargetReservedByPack(
            LivingEntity target,
            MemoryModuleType<LivingEntity> reservationMemory) {
        if (!(this.level() instanceof ServerLevel level) || target == null) {
            return false;
        }

        return !level.getEntitiesOfClass(
                EarthWolf.class,
                this.getBoundingBox().inflate(22.0D),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isEarthPackmate(candidate)
                        && candidate.getBrain()
                                .getMemory(reservationMemory)
                                .filter(reserved -> reserved == target)
                                .isPresent())
                .isEmpty();
    }

    private boolean isCoolingDown(MemoryModuleType<Integer> memory) {
        return this.getBrain().getMemory(memory).orElse(0) > 0;
    }

    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int value = this.getBrain().getMemory(memory).orElse(0);
        if (value > 1) {
            this.getBrain().setMemory(memory, value - 1);
        } else if (value == 1) {
            this.getBrain().eraseMemory(memory);
        }
    }

    private void faceDirection(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-5D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    private void sendGroundBurst(ServerLevel level, BlockPos center, int count, double spread) {
        BlockState state = level.getBlockState(center.below());
        if (state.isAir()) {
            state = Blocks.DIRT.defaultBlockState();
        }
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                center.getX() + 0.5D,
                center.getY() + 0.15D,
                center.getZ() + 0.5D,
                count,
                spread,
                0.15D,
                spread,
                0.09D);
    }

    private void sendShockwaveRings(ServerLevel level, double radius) {
        BlockState state = level.getBlockState(this.blockPosition().below());
        if (state.isAir()) {
            state = Blocks.DIRT.defaultBlockState();
        }

        int points = Math.max(18, (int) (radius * 2.0D));
        double[] rings = radius > 12.0D
                ? new double[] {Math.min(5.0D, radius), Math.min(radius * 0.55D, radius), radius}
                : new double[] {radius * 0.45D, radius};

        for (double ring : rings) {
            for (int i = 0; i < points; ++i) {
                double angle = Math.PI * 2.0D * i / points;
                double x = this.getX() + Math.cos(angle) * ring;
                double z = this.getZ() + Math.sin(angle) * ring;
                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x,
                        this.getY() + 0.15D,
                        z,
                        1,
                        0.04D,
                        0.05D,
                        0.04D,
                        0.02D);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.cancelCharge();
        this.cancelDirtBlast(true);
    }

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || this.isAlliedTo(threat)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && !this.wantsToAttack(threat, owner)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.EARTH_PACK_THREAT.get(), threat);
        double radius = pupEmergency ? 34.0D : EarthWolfPackSensor.PACK_SCAN_RADIUS;
        List<EarthWolf> packmates = level.getEntitiesOfClass(
                EarthWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isEarthPackmate(candidate));

        for (EarthWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.EARTH_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidEarthCombatTarget(threat)
                    && packmate.getTarget() == null) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkEarthWolfSpawnRules(
            EntityType<EarthWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());
        boolean earthGround = ground.is(BlockTags.DIRT)
                || ground.is(BlockTags.BASE_STONE_OVERWORLD)
                || ground.is(Blocks.GRAVEL);
        return earthGround && level.getFluidState(pos).isEmpty();
    }
}
