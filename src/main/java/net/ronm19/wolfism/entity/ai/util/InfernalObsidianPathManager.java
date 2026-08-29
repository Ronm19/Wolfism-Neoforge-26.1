package net.ronm19.wolfism.entity.ai.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.entity.custom.InfernalWolf;

/**
 * Creates temporary real obsidian footing over surface lava.
 *
 * <p>The replacement is intentionally conservative: only exposed lava is
 * replaced, pre-existing obsidian is never claimed, and a block is restored
 * only if it is still obsidian when its timer expires.</p>
 */
public final class InfernalObsidianPathManager {
    private static final int RADIUS = 2;

    private static final Map<ServerLevel, Map<BlockPos, Entry>> ACTIVE_PATHS =
            new WeakHashMap<>();

    private static final Map<ServerLevel, Long> LAST_TICK =
            new WeakHashMap<>();

    private InfernalObsidianPathManager() {
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();

        if (LAST_TICK.getOrDefault(level, Long.MIN_VALUE) == gameTime) {
            return;
        }

        LAST_TICK.put(level, gameTime);

        Map<BlockPos, Entry> paths = ACTIVE_PATHS.get(level);
        if (paths == null || paths.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<BlockPos, Entry>> iterator =
                paths.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Entry> mapEntry = iterator.next();
            Entry entry = mapEntry.getValue();

            if (entry.expiresAt > gameTime) {
                continue;
            }

            BlockPos pos = mapEntry.getKey();

            if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                level.setBlockAndUpdate(pos, entry.originalState);
            }

            iterator.remove();
        }

        if (paths.isEmpty()) {
            ACTIVE_PATHS.remove(level);
        }
    }

    public static void createPath(
            ServerLevel level,
            InfernalWolf wolf) {

        if (!wolf.isAlive()) {
            return;
        }

        BlockPos center = BlockPos.containing(
                wolf.getX(),
                wolf.getY() - 0.20D,
                wolf.getZ());

        boolean nearLava =
                wolf.isInLava()
                || level.getFluidState(center).is(FluidTags.LAVA)
                || level.getFluidState(center.below()).is(FluidTags.LAVA);

        if (!nearLava) {
            // Shoreline support: a wolf standing at the edge may create the
            // first pieces before stepping onto the lava.
            for (int dx = -1; dx <= 1 && !nearLava; ++dx) {
                for (int dz = -1; dz <= 1 && !nearLava; ++dz) {
                    nearLava = level.getFluidState(
                            center.offset(dx, -1, dz))
                            .is(FluidTags.LAVA);
                }
            }
        }

        if (!nearLava) {
            return;
        }

        Map<BlockPos, Entry> paths =
                ACTIVE_PATHS.computeIfAbsent(
                        level,
                        ignored -> new HashMap<>());

        long expiry =
                level.getGameTime()
                        + InfernalWolf.OBSIDIAN_PATH_LIFETIME_TICKS;

        for (int dx = -RADIUS; dx <= RADIUS; ++dx) {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }

                BlockPos lavaPos = findSurfaceLava(
                        level,
                        center.offset(dx, 1, dz));

                if (lavaPos == null) {
                    continue;
                }

                /*
                 * Never solidify a lava block that currently intersects the
                 * wolf's body. This is the important lava-fall/submerged fix:
                 * replacing that block with obsidian is what caused the wolf
                 * to become entombed/suffocate inside the path.
                 */
                AABB candidateBlock = new AABB(lavaPos);
                if (wolf.getBoundingBox().intersects(candidateBlock)) {
                    continue;
                }

                /*
                 * The top of the lava block must already be at/below the
                 * wolf's feet. If Infernal is swimming inside a lava column,
                 * wait until he reaches the surface before creating footing.
                 */
                double lavaTop = lavaPos.getY() + 1.0D;
                if (wolf.getY() < lavaTop - 0.05D) {
                    continue;
                }

                Entry existing = paths.get(lavaPos);

                if (existing != null) {
                    paths.put(
                            lavaPos,
                            new Entry(
                                    existing.originalState,
                                    Math.max(existing.expiresAt, expiry)));
                    continue;
                }

                BlockState original = level.getBlockState(lavaPos);

                if (!original.getFluidState().is(FluidTags.LAVA)) {
                    continue;
                }

                if (level.setBlockAndUpdate(
                        lavaPos,
                        Blocks.OBSIDIAN.defaultBlockState())) {

                    paths.put(
                            new BlockPos(
                                    lavaPos.getX(),
                                    lavaPos.getY(),
                                    lavaPos.getZ()),
                            new Entry(original, expiry));
                }
            }
        }
    }


    /**
     * Used by Infernal's landing protection. Only obsidian created and still
     * tracked by this manager counts as an Infernal path block.
     */
    public static boolean isTemporaryInfernalPath(
            ServerLevel level,
            BlockPos pos) {

        Map<BlockPos, Entry> paths = ACTIVE_PATHS.get(level);
        return paths != null
                && paths.containsKey(pos)
                && level.getBlockState(pos).is(Blocks.OBSIDIAN);
    }

    private static BlockPos findSurfaceLava(
            ServerLevel level,
            BlockPos start) {

        for (int dy = 0; dy >= -3; --dy) {
            BlockPos pos = start.offset(0, dy, 0);

            if (!level.getFluidState(pos).is(FluidTags.LAVA)
                    || !level.getFluidState(pos).isSource()) {
                continue;
            }

            BlockPos above = pos.above();

            if (level.getFluidState(above).is(FluidTags.LAVA)) {
                continue;
            }

            if (!level.getBlockState(above)
                    .getCollisionShape(level, above)
                    .isEmpty()) {
                continue;
            }

            return new BlockPos(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ());
        }

        return null;
    }

    private record Entry(
            BlockState originalState,
            long expiresAt) {
    }
}
