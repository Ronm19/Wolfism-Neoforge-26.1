package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.wolfism.entity.custom.AstralWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Celestial Awareness + Night Navigation sensor.
 *
 * <p>At night this sensor gives Astral one shared threat picture for the pack,
 * tracks whether a nearby family member is already in danger, identifies a
 * safer nearby position when the owner is near obvious terrain hazards, and
 * remembers the owner's same-dimension respawn/home position.</p>
 */
public final class AstralWolfNightSensor
        extends Sensor<AstralWolf> {

    /*
     * Safe Area search:
     *
     * - owner hazard detection remains local (3 blocks);
     * - Astral searches farther away for a REAL safe destination;
     * - the center 3x3 must be completely safe;
     * - most of the surrounding 5x5 must also be usable;
     * - dangerous blocks/fluids need a clear buffer around the destination.
     *
     * This intentionally prefers ONE obvious safe patch over lots of tiny
     * "technically safe" blocks beside the danger.
     */
    private static final int SAFE_SEARCH_RADIUS = 12;
    private static final int SAFE_SEARCH_MIN_RADIUS = 4;
    private static final int HAZARD_SCAN_RADIUS = 3;

    private static final int SAFE_CORE_RADIUS = 1;       // 3x3
    private static final int SAFE_AREA_RADIUS = 2;       // 5x5
    private static final int SAFE_AREA_MIN_USABLE = 21;  // 21/25 ~= 84%
    private static final int SAFE_HAZARD_BUFFER = 4;

    public AstralWolfNightSensor() {
        super(10);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            AstralWolf wolf) {

        Brain<AstralWolf> brain =
                wolf.getBrain();

        if (level.isBrightOutside()) {
            brain.eraseMemory(
                    ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get());
            brain.eraseMemory(
                    ModMemoryModuleTypes.ASTRAL_FAMILY_IN_DANGER.get());
            brain.eraseMemory(
                    ModMemoryModuleTypes.ASTRAL_SAFE_ROUTE_POS.get());
            brain.eraseMemory(
                    ModMemoryModuleTypes.ASTRAL_HOME_POS.get());
            brain.setMemory(
                    ModMemoryModuleTypes.ASTRAL_HOSTILE_COUNT.get(),
                    0);
            return;
        }

        List<LivingEntity> threats =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(
                                AstralWolf.CELESTIAL_AWARENESS_RANGE),
                        candidate ->
                                wolf.isPotentialAstralThreat(
                                        candidate));

        int hostileCount =
                (int)threats.stream()
                        .filter(
                                target ->
                                        wolf.distanceToSqr(target)
                                                <= 24.0D * 24.0D)
                        .count();

        LivingEntity familyInDanger =
                findFamilyInDanger(
                        level,
                        wolf,
                        threats);

        LivingEntity sharedThreat =
                threats.stream()
                        .filter(
                                target ->
                                        shouldSenseThreat(
                                                wolf,
                                                target))
                        .max(
                                Comparator.comparingDouble(
                                        target ->
                                                threatScore(
                                                        wolf,
                                                        target,
                                                        familyInDanger)))
                        .orElse(null);

        brain.setMemory(
                ModMemoryModuleTypes.ASTRAL_HOSTILE_COUNT.get(),
                hostileCount);

        setOrErase(
                brain,
                ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get(),
                sharedThreat);

        setOrErase(
                brain,
                ModMemoryModuleTypes.ASTRAL_FAMILY_IN_DANGER.get(),
                familyInDanger);

        LivingEntity owner =
                wolf.getOwner();

        BlockPos safePos = null;
        BlockPos homePos = null;

        if (owner != null
                && owner.isAlive()) {

            if (isNearTerrainHazard(
                    level,
                    owner.blockPosition())) {

                safePos =
                        findSafeGround(
                                level,
                                owner.blockPosition());
            }

            if (owner instanceof ServerPlayer player) {
                ServerPlayer.RespawnConfig config =
                        player.getRespawnConfig();

                if (config != null
                        && config.respawnData()
                        .dimension()
                        .equals(
                                level.dimension())) {

                    homePos =
                            config.respawnData()
                                    .pos()
                                    .immutable();
                }
            }
        }

        setOrErase(
                brain,
                ModMemoryModuleTypes.ASTRAL_SAFE_ROUTE_POS.get(),
                safePos);

        setOrErase(
                brain,
                ModMemoryModuleTypes.ASTRAL_HOME_POS.get(),
                homePos);
    }

    private static LivingEntity findFamilyInDanger(
            ServerLevel level,
            AstralWolf astral,
            List<LivingEntity> threats) {

        if (!astral.isTame()) {
            return null;
        }

        LivingEntity owner =
                astral.getOwner();

        if (owner != null
                && owner.isAlive()
                && isFamilyTargetedByAny(
                        astral,
                        owner,
                        threats)) {
            return owner;
        }

        return level.getEntitiesOfClass(
                Wolf.class,
                astral.getBoundingBox().inflate(
                        AstralWolf.NIGHT_SUPPORT_RADIUS),
                wolf -> wolf.isAlive()
                        && wolf.isTame()
                        && isFamilyTargetedByAny(
                                astral,
                                wolf,
                                threats))
                .stream()
                .min(
                        Comparator.comparingDouble(
                                astral::distanceToSqr))
                .orElse(null);
    }

    private static boolean isFamilyTargetedByAny(
            AstralWolf astral,
            LivingEntity family,
            List<LivingEntity> threats) {

        for (LivingEntity threat : threats) {
            if (threat instanceof Mob mob
                    && mob.getTarget() == family) {
                return true;
            }
        }

        return family == astral.getFamilyDefenseTarget();
    }

    private static boolean shouldSenseThreat(
            AstralWolf astral,
            LivingEntity target) {

        if (astral.isThreatCommittedToAstralFamily(target)) {
            return true;
        }

        double distanceSqr =
                astral.distanceToSqr(target);

        return distanceSqr <= 10.0D * 10.0D
                || astral.getSensing()
                .hasLineOfSight(target);
    }

    private static double threatScore(
            AstralWolf astral,
            LivingEntity target,
            LivingEntity familyInDanger) {

        double score =
                Math.min(
                        100.0D,
                        target.getMaxHealth());

        if (target == astral.getFamilyDefenseTarget()) {
            score += 180.0D;
        }

        if (astral.isThreatCommittedToAstralFamily(target)) {
            score += 150.0D;
        }

        if (familyInDanger != null
                && target instanceof Mob mob
                && mob.getTarget() == familyInDanger) {
            score += 90.0D;
        }

        score +=
                Math.max(
                        0.0D,
                        50.0D
                                - Math.sqrt(
                                astral.distanceToSqr(target))
                                * 2.0D);

        return score;
    }

    private static boolean isNearTerrainHazard(
            ServerLevel level,
            BlockPos center) {

        if (level.getFluidState(center)
                .is(FluidTags.LAVA)) {
            return true;
        }

        /*
         * Simple edge test: if there is no support one and two blocks below
         * the owner's feet, Astral treats the immediate area as a fall hazard.
         */
        if (!level.getBlockState(center.below())
                .isFaceSturdy(
                        level,
                        center.below(),
                        Direction.UP)
                && !level.getBlockState(center.below(2))
                .isFaceSturdy(
                        level,
                        center.below(2),
                        Direction.UP)) {
            return true;
        }

        for (int dx = -HAZARD_SCAN_RADIUS;
                dx <= HAZARD_SCAN_RADIUS;
                ++dx) {
            for (int dz = -HAZARD_SCAN_RADIUS;
                    dz <= HAZARD_SCAN_RADIUS;
                    ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    BlockPos pos =
                            center.offset(
                                    dx,
                                    dy,
                                    dz);

                    if (level.getFluidState(pos)
                            .is(FluidTags.LAVA)) {
                        return true;
                    }

                    BlockState state =
                            level.getBlockState(pos);

                    if (state.is(Blocks.FIRE)
                            || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.CACTUS)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.POWDER_SNOW)
                            || state.is(Blocks.SWEET_BERRY_BUSH)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static BlockPos findSafeGround(
            ServerLevel level,
            BlockPos center) {

        BlockPos best = null;
        double bestScore =
                Double.NEGATIVE_INFINITY;

        /*
         * Do NOT return the first technically-safe block.
         *
         * Search the full nearby region and choose one clearly usable area:
         * - at least several blocks away from the owner's hazard;
         * - solid/open 3x3 core;
         * - mostly usable 5x5 neighborhood;
         * - no lava/cactus/fire/magma/powder-snow/etc. inside the safety buffer.
         */
        for (int radius = SAFE_SEARCH_MIN_RADIUS;
                radius <= SAFE_SEARCH_RADIUS;
                ++radius) {

            for (int dx = -radius;
                    dx <= radius;
                    ++dx) {
                for (int dz = -radius;
                        dz <= radius;
                        ++dz) {

                    if (Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -2;
                            dy <= 2;
                            ++dy) {

                        BlockPos feet =
                                center.offset(
                                        dx,
                                        dy,
                                        dz);

                        if (!isClearlySafeArea(
                                level,
                                feet)) {
                            continue;
                        }

                        /*
                         * Prefer a high-quality area with maximum hazard
                         * clearance, while gently preferring a destination that
                         * is not unnecessarily far from the owner.
                         */
                        double clearance =
                                hazardClearanceScore(
                                        level,
                                        feet);

                        double ownerDistance =
                                Math.sqrt(
                                        feet.distSqr(center));

                        double score =
                                clearance * 100.0D
                                        - ownerDistance * 2.0D;

                        if (score > bestScore) {
                            bestScore = score;
                            best = feet.immutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    /**
     * A candidate is a SAFE AREA, not just a safe single block.
     */
    private static boolean isClearlySafeArea(
            ServerLevel level,
            BlockPos center) {

        // The central 3x3 must be fully usable.
        for (int dx = -SAFE_CORE_RADIUS;
                dx <= SAFE_CORE_RADIUS;
                ++dx) {
            for (int dz = -SAFE_CORE_RADIUS;
                    dz <= SAFE_CORE_RADIUS;
                    ++dz) {

                if (!isSafeStandingPosition(
                        level,
                        center.offset(
                                dx,
                                0,
                                dz))) {
                    return false;
                }
            }
        }

        // The wider 5x5 must be overwhelmingly usable.
        int usable = 0;

        for (int dx = -SAFE_AREA_RADIUS;
                dx <= SAFE_AREA_RADIUS;
                ++dx) {
            for (int dz = -SAFE_AREA_RADIUS;
                    dz <= SAFE_AREA_RADIUS;
                    ++dz) {

                if (isSafeStandingPosition(
                        level,
                        center.offset(
                                dx,
                                0,
                                dz))) {
                    ++usable;
                }
            }
        }

        if (usable < SAFE_AREA_MIN_USABLE) {
            return false;
        }

        return hasClearHazardBuffer(
                level,
                center);
    }

    /**
     * Reject a destination if obvious danger is still close to the chosen
     * safe zone. This is the main difference from the old implementation.
     */
    private static boolean hasClearHazardBuffer(
            ServerLevel level,
            BlockPos center) {

        for (int dx = -SAFE_HAZARD_BUFFER;
                dx <= SAFE_HAZARD_BUFFER;
                ++dx) {
            for (int dz = -SAFE_HAZARD_BUFFER;
                    dz <= SAFE_HAZARD_BUFFER;
                    ++dz) {
                for (int dy = -2;
                        dy <= 2;
                        ++dy) {

                    BlockPos pos =
                            center.offset(
                                    dx,
                                    dy,
                                    dz);

                    if (isExplicitHazard(
                            level,
                            pos)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Higher score = danger is farther from the safe-area center.
     */
    private static double hazardClearanceScore(
            ServerLevel level,
            BlockPos center) {

        final int scanRadius = 7;
        double nearestHazardSqr =
                (scanRadius + 1.0D)
                        * (scanRadius + 1.0D);

        for (int dx = -scanRadius;
                dx <= scanRadius;
                ++dx) {
            for (int dz = -scanRadius;
                    dz <= scanRadius;
                    ++dz) {
                for (int dy = -2;
                        dy <= 2;
                        ++dy) {

                    BlockPos pos =
                            center.offset(
                                    dx,
                                    dy,
                                    dz);

                    if (!isExplicitHazard(
                            level,
                            pos)) {
                        continue;
                    }

                    double distanceSqr =
                            dx * dx
                                    + dz * dz
                                    + dy * dy;

                    nearestHazardSqr =
                            Math.min(
                                    nearestHazardSqr,
                                    distanceSqr);
                }
            }
        }

        return Math.sqrt(
                nearestHazardSqr);
    }

    private static boolean isExplicitHazard(
            ServerLevel level,
            BlockPos pos) {

        if (level.getFluidState(pos)
                .is(FluidTags.LAVA)) {
            return true;
        }

        BlockState state =
                level.getBlockState(pos);

        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean isSafeStandingPosition(
            ServerLevel level,
            BlockPos feet) {

        BlockPos groundPos =
                feet.below();

        BlockState ground =
                level.getBlockState(
                        groundPos);

        if (!ground.isFaceSturdy(
                level,
                groundPos,
                Direction.UP)) {
            return false;
        }

        if (!level.getFluidState(feet)
                .isEmpty()
                || !level.getFluidState(feet.above())
                .isEmpty()) {
            return false;
        }

        if (!level.getBlockState(feet)
                .getCollisionShape(
                        level,
                        feet)
                .isEmpty()
                || !level.getBlockState(feet.above())
                .getCollisionShape(
                        level,
                        feet.above())
                .isEmpty()) {
            return false;
        }

        if (ground.is(Blocks.CACTUS)
                || ground.is(Blocks.MAGMA_BLOCK)
                || ground.is(Blocks.POWDER_SNOW)
                || ground.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }

        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                if (level.getFluidState(
                        feet.offset(
                                dx,
                                -1,
                                dz))
                        .is(FluidTags.LAVA)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static <T> void setOrErase(
            Brain<AstralWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {
            brain.setMemory(
                    memory,
                    value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get(),
                ModMemoryModuleTypes.ASTRAL_FAMILY_IN_DANGER.get(),
                ModMemoryModuleTypes.ASTRAL_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.ASTRAL_SAFE_ROUTE_POS.get(),
                ModMemoryModuleTypes.ASTRAL_HOME_POS.get());
    }
}
