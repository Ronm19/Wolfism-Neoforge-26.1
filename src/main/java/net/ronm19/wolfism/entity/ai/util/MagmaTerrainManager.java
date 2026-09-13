package net.ronm19.wolfism.entity.ai.util;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.MagmaWolf;

/**
 * Owns Magma Wolf's temporary real terrain.
 *
 * <p>Only blocks this manager itself changed are restored. Pre-existing
 * obsidian/magma is never claimed, and a position is restored only while it is
 * still the expected temporary block.</p>
 */
public final class MagmaTerrainManager {
    private static final int PATH_RADIUS = 2;

    private MagmaTerrainManager() {
    }

    public static void createObsidianPath(ServerLevel level, MagmaWolf wolf) {
        if (!wolf.isAlive() || wolf.isBaby()) return;

        BlockPos center = BlockPos.containing(
                wolf.getX(),
                wolf.getY() - 0.20D,
                wolf.getZ());

        if (!MagmaTerrainState.loadedNeighborhood(level, center)) return;

        boolean nearLava = wolf.isInLava()
                || level.getFluidState(center).is(FluidTags.LAVA)
                || level.getFluidState(center.below()).is(FluidTags.LAVA);

        if (!nearLava) {
            for (int dx = -1; dx <= 1 && !nearLava; ++dx) {
                for (int dz = -1; dz <= 1 && !nearLava; ++dz) {
                    nearLava = level.getFluidState(center.offset(dx, -1, dz)).is(FluidTags.LAVA);
                }
            }
        }

        if (!nearLava) return;

        long expiry = level.getGameTime() + MagmaWolf.OBSIDIAN_PATH_LIFETIME_TICKS;

        for (int dx = -PATH_RADIUS; dx <= PATH_RADIUS; ++dx) {
            for (int dz = -PATH_RADIUS; dz <= PATH_RADIUS; ++dz) {
                if (dx * dx + dz * dz > PATH_RADIUS * PATH_RADIUS) continue;

                BlockPos lava = findSurfaceLava(level, center.offset(dx, 1, dz));
                if (lava == null) continue;

                // Never solidify the block currently intersecting Magma's body.
                if (wolf.getBoundingBox().intersects(new AABB(lava))) continue;

                double lavaTop = lava.getY() + 1.0D;
                if (wolf.getY() < lavaTop - 0.05D) continue;

                placeTemporary(
                        level,
                        wolf,
                        lava,
                        Blocks.OBSIDIAN.defaultBlockState(),
                        expiry);
            }
        }
    }

    /**
     * Proactively builds a temporary crossing in the direction the owner is
     * looking and returns a safe bank destination for Magma to lead toward.
     */
    public static BlockPos createGuidedObsidianCrossing(
            ServerLevel level,
            MagmaWolf wolf,
            LivingEntity owner,
            int maxDistance,
            int lifetimeTicks) {

        if (!wolf.isAlive() || wolf.isBaby() || wolf.level() != level || owner.level() != level) return null;
        Vec3 look = owner.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-4D) return null;
        forward = forward.normalize();

        CrossingCandidate best = null;
        double[] angles = {0.0D, -22.5D, 22.5D, -45.0D, 45.0D};

        for (double degrees : angles) {
            Vec3 direction = rotateHorizontal(forward, Math.toRadians(degrees));
            CrossingCandidate candidate = scanCrossing(
                    level,
                    owner.blockPosition(),
                    direction,
                    maxDistance);

            if (candidate == null) continue;

            if (best == null
                    || candidate.lavaSteps < best.lavaSteps
                    || (candidate.lavaSteps == best.lavaSteps
                        && candidate.totalSteps < best.totalSteps)) {
                best = candidate;
            }
        }

        if (best == null) return null;

        long expiry = level.getGameTime() + lifetimeTicks;
        Set<BlockPos> bridge = new LinkedHashSet<>();
        Vec3 perpendicular = new Vec3(-best.direction.z, 0.0D, best.direction.x);

        for (int step = 1; step < best.totalSteps; ++step) {
            double cx = owner.getX() + best.direction.x * step;
            double cz = owner.getZ() + best.direction.z * step;

            for (int width = -1; width <= 1; ++width) {
                int x = (int) Math.floor(cx + perpendicular.x * width);
                int z = (int) Math.floor(cz + perpendicular.z * width);

                BlockPos lava = findSurfaceLavaNearY(
                        level,
                        x,
                        z,
                        owner.blockPosition().getY());

                if (lava != null) bridge.add(lava);
            }
        }

        int changed = 0;

        for (BlockPos lava : bridge) {
            if (wolf.getBoundingBox().intersects(new AABB(lava))) continue;

            if (placeTemporary(
                    level,
                    wolf,
                    lava,
                    Blocks.OBSIDIAN.defaultBlockState(),
                    expiry)) {
                ++changed;
            }
        }

        return changed >= 2 ? best.destination : null;
    }

    private static CrossingCandidate scanCrossing(
            ServerLevel level,
            BlockPos origin,
            Vec3 direction,
            int maxDistance) {

        boolean foundLava = false;
        int firstLavaStep = -1;
        int lavaSteps = 0;
        int dryStepsAfterLava = 0;

        for (int step = 2; step <= maxDistance; ++step) {
            int x = (int) Math.floor(origin.getX() + 0.5D + direction.x * step);
            int z = (int) Math.floor(origin.getZ() + 0.5D + direction.z * step);

            BlockPos lava = findSurfaceLavaNearY(level, x, z, origin.getY());

            if (lava != null) {
                if (!foundLava) {
                    firstLavaStep = step;

                    // Obsidian Path is an expedition crossing tool, not a
                    // remote terrain painter. The owner should actually be
                    // standing near the lava edge before Magma takes the lead.
                    if (firstLavaStep > 6) return null;
                }

                foundLava = true;
                ++lavaSteps;
                dryStepsAfterLava = 0;
                continue;
            }

            if (!foundLava) continue;

            ++dryStepsAfterLava;

            BlockPos safe = findSafeStandingNearY(
                    level,
                    x,
                    z,
                    origin.getY());

            if (safe != null && lavaSteps >= 3) {
                return new CrossingCandidate(
                        direction,
                        safe.immutable(),
                        step,
                        lavaSteps);
            }

            if (dryStepsAfterLava >= 4) return null;
        }

        return null;
    }

    private static Vec3 rotateHorizontal(Vec3 vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec3(
                vector.x * cos - vector.z * sin,
                0.0D,
                vector.x * sin + vector.z * cos).normalize();
    }

    private static BlockPos findSurfaceLavaNearY(
            ServerLevel level,
            int x,
            int z,
            int referenceY) {

        if (!MagmaTerrainState.loadedNeighborhood(level, new BlockPos(x, referenceY, z))) return null;
        for (int dy = 3; dy >= -7; --dy) {
            BlockPos pos = new BlockPos(x, referenceY + dy, z);

            if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
                continue;
            }

            if (level.getFluidState(pos.above()).is(FluidTags.LAVA)) continue;

            if (!level.getBlockState(pos.above())
                    .getCollisionShape(level, pos.above())
                    .isEmpty()) {
                continue;
            }

            return pos.immutable();
        }

        return null;
    }

    private static BlockPos findSafeStandingNearY(
            ServerLevel level,
            int x,
            int z,
            int referenceY) {

        if (!MagmaTerrainState.loadedNeighborhood(level, new BlockPos(x, referenceY, z))) return null;
        for (int dy = 3; dy >= -5; --dy) {
            BlockPos feet = new BlockPos(x, referenceY + dy, z);
            BlockPos head = feet.above();
            BlockPos ground = feet.below();

            if (!level.getFluidState(feet).isEmpty()
                    || !level.getFluidState(head).isEmpty()) {
                continue;
            }

            if (!level.getBlockState(feet)
                    .getCollisionShape(level, feet)
                    .isEmpty()) {
                continue;
            }

            if (!level.getBlockState(head)
                    .getCollisionShape(level, head)
                    .isEmpty()) {
                continue;
            }

            if (!level.getBlockState(ground)
                    .isFaceSturdy(level, ground, Direction.UP)) {
                continue;
            }

            return feet.immutable();
        }

        return null;
    }

    /**
     * Turns exposed nearby lava blocks into temporary real magma blocks.
     * Solid player terrain is deliberately not edited in V1.
     *
     * @return number of lava positions converted.
     */
    public static int createEruption(
            ServerLevel level,
            MagmaWolf wolf,
            BlockPos center,
            int radius,
            int lifetimeTicks) {

        int changed = 0;
        long expiry = level.getGameTime() + lifetimeTicks;

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (dx * dx + dz * dz > radius * radius) continue;

                BlockPos lava = findSurfaceLava(level, center.offset(dx, 3, dz));
                if (lava == null) continue;

                if (placeTemporary(
                        level,
                        wolf,
                        lava,
                        Blocks.MAGMA_BLOCK.defaultBlockState(),
                        expiry)) {
                    ++changed;
                }
            }
        }

        return changed;
    }

    private static boolean placeTemporary(
            ServerLevel level,
            MagmaWolf wolf,
            BlockPos pos,
            BlockState temporary,
            long expiry) {
        return MagmaTerrainState.get(level).place(level, wolf, pos, temporary, expiry);
    }

    private static BlockPos findSurfaceLava(ServerLevel level, BlockPos start) {
        if (!MagmaTerrainState.loadedNeighborhood(level, start)) return null;
        for (int dy = 0; dy >= -6; --dy) {
            BlockPos pos = start.offset(0, dy, 0);

            if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
                continue;
            }

            if (level.getFluidState(pos.above()).is(FluidTags.LAVA)) continue;
            if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) continue;

            return pos.immutable();
        }
        return null;
    }

    private record CrossingCandidate(
            Vec3 direction,
            BlockPos destination,
            int totalSteps,
            int lavaSteps) {
    }

}
