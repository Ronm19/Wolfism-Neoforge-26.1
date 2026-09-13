package net.ronm19.wolfism.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.HalloweenWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Common nighttime encounters and daytime pumpkin encounters for Halloween Wolf.
 *
 * <p>Nighttime supplements ordinary biome spawning with capped encounters so
 * an already-full passive mob cap cannot hide the entire holiday. During bright daytime,
 * pumpkins become explicit encounter anchors in already-loaded terrain around
 * players. This is deliberately player-area logic instead of a spawn-placement
 * block scan, so it cannot request unavailable chunks during world generation.</p>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class HalloweenWolfSeasonalEvents {
    private static final int ANCHOR_CHECK_INTERVAL = 20 * 8;
    private static final String NEXT_ENCOUNTER = "WolfismHalloweenNextEncounter";
    private static final int AREA_COOLDOWN = 20 * 30;

    /** How far an exploring player can "activate" a pumpkin patch. */
    private static final int PUMPKIN_SEARCH_RADIUS = 48;

    /** Keep the event common, but never turn a patch into an unlimited dog printer. */
    private static final double LOCAL_WILD_RADIUS = 56.0D;
    private static final int LOCAL_WILD_CAP = 4;

    private static final int MIN_SPAWN_RADIUS_FROM_PUMPKIN = 5;
    private static final int MAX_SPAWN_RADIUS_FROM_PUMPKIN = 15;
    private static final double MIN_SPAWN_DISTANCE_FROM_PLAYER_SQR = 12.0D * 12.0D;

    private HalloweenWolfSeasonalEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()
                || player.tickCount % ANCHOR_CHECK_INTERVAL != 0) {
            return;
        }

        ServerLevel level = player.level();

        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !HalloweenWolf.isHalloweenSeasonOpen(level)) {
            return;
        }

        if (!level.isBrightOutside()) {
            HolidayWolfSeasonalSpawner.trySpawnEncounter(
                    player, NEXT_ENCOUNTER, HalloweenWolf::isHalloweenSeasonOpen,
                    ModBiomeTags.HALLOWEEN_WOLF_SPAWNS, ModEntities.HALLOWEEN_WOLF.get(),
                    HalloweenWolfSeasonalEvents::isSafeWolfSpawn, 0.40F);
            return;
        }

        long now = level.getGameTime();
        if (player.getPersistentData().getLongOr(NEXT_ENCOUNTER, 0L) > now) {
            return;
        }

        int existingWild = level.getEntitiesOfClass(
                        HalloweenWolf.class,
                        player.getBoundingBox().inflate(LOCAL_WILD_RADIUS, 24.0D, LOCAL_WILD_RADIUS),
                        wolf -> wolf.isAlive() && !wolf.isTame())
                .size();

        if (existingWild >= LOCAL_WILD_CAP) {
            return;
        }

        BlockPos pumpkin = findPumpkinAnchor(level, player);
        if (pumpkin == null) {
            return;
        }

        RandomSource random = level.getRandom();

        // One encounter is guaranteed when a valid active patch is found and the
        // local cap has room. A second wolf is common enough to make the event feel
        // alive without filling every pumpkin patch with a giant pack.
        int desired = 1;
        if (existingWild + desired < LOCAL_WILD_CAP && random.nextFloat() < 0.40F) {
            ++desired;
        }

        desired = Math.min(desired, LOCAL_WILD_CAP - existingWild);

        int created = 0;
        for (int i = 0; i < desired; ++i) {
            BlockPos spawnPos = findSafeSpawnNearPumpkin(level, player, pumpkin, random);
            if (spawnPos == null) {
                break;
            }

            AABB spawnBox = new AABB(
                    spawnPos.getX() + 0.2D, spawnPos.getY(), spawnPos.getZ() + 0.2D,
                    spawnPos.getX() + 0.8D, spawnPos.getY() + 0.85D, spawnPos.getZ() + 0.8D);
            if (!level.noCollision(spawnBox)
                    || level.getEntitiesOfClass(HalloweenWolf.class,
                            spawnBox.inflate(LOCAL_WILD_RADIUS, 24.0D, LOCAL_WILD_RADIUS),
                            wolf -> wolf.isAlive() && !wolf.isTame()).size() >= LOCAL_WILD_CAP) {
                continue;
            }

            HalloweenWolf wolf = ModEntities.HALLOWEEN_WOLF.get()
                    .spawn(level, spawnPos, EntitySpawnReason.EVENT);
            if (wolf != null && !wolf.isRemoved()) ++created;
        }
        if (created > 0) {
            player.getPersistentData().putLong(NEXT_ENCOUNTER, now + AREA_COOLDOWN);
        }
    }

    /**
     * Finds the nearest visible/surface pumpkin-family block in already-loaded
     * terrain. MOTION_BLOCKING_NO_LEAVES lets this work naturally in forests
     * without leaf canopies hiding the ground surface from the search.
     */
    private static BlockPos findPumpkinAnchor(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int minX = center.getX() - PUMPKIN_SEARCH_RADIUS;
        int maxX = center.getX() + PUMPKIN_SEARCH_RADIUS;
        int minZ = center.getZ() - PUMPKIN_SEARCH_RADIUS;
        int maxZ = center.getZ() + PUMPKIN_SEARCH_RADIUS;

        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; ++x) {
            int chunkX = x >> 4;

            for (int z = minZ; z <= maxZ; ++z) {
                int chunkZ = z >> 4;

                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                int surfaceY = chunk.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x & 15,
                        z & 15) + 1;

                // SurfaceY is normally the first free block above the surface.
                // Check a few blocks down so small terrain irregularities do not
                // hide a pumpkin from the anchor scan.
                for (int depth = 1; depth <= 4; ++depth) {
                    scan.set(x, surfaceY - depth, z);
                    BlockState state = level.getBlockState(scan);

                    if (!isPumpkin(state)
                            || !level.getBiome(scan).is(ModBiomeTags.HALLOWEEN_WOLF_SPAWNS)) {
                        continue;
                    }

                    double distance = scan.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = scan.immutable();
                    }
                    break;
                }
            }
        }

        return best;
    }

    private static BlockPos findSafeSpawnNearPumpkin(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pumpkin,
            RandomSource random) {

        for (int attempt = 0; attempt < 32; ++attempt) {
            int dx = random.nextInt(MAX_SPAWN_RADIUS_FROM_PUMPKIN * 2 + 1)
                    - MAX_SPAWN_RADIUS_FROM_PUMPKIN;
            int dz = random.nextInt(MAX_SPAWN_RADIUS_FROM_PUMPKIN * 2 + 1)
                    - MAX_SPAWN_RADIUS_FROM_PUMPKIN;

            int horizontalSqr = dx * dx + dz * dz;
            if (horizontalSqr < MIN_SPAWN_RADIUS_FROM_PUMPKIN * MIN_SPAWN_RADIUS_FROM_PUMPKIN
                    || horizontalSqr > MAX_SPAWN_RADIUS_FROM_PUMPKIN * MAX_SPAWN_RADIUS_FROM_PUMPKIN) {
                continue;
            }

            int x = pumpkin.getX() + dx;
            int z = pumpkin.getZ() + dz;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null || !hasLoadedCollisionNeighborhood(level, x, z)) {
                continue;
            }

            int y = chunk.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x & 15,
                    z & 15) + 1;

            BlockPos feet = new BlockPos(x, y, z);

            if (!level.getWorldBorder().isWithinBounds(feet)
                    || feet.distSqr(player.blockPosition()) < MIN_SPAWN_DISTANCE_FROM_PLAYER_SQR
                    || !level.getBiome(feet).is(ModBiomeTags.HALLOWEEN_WOLF_SPAWNS)
                    || !isSafeWolfSpawn(level, feet)) {
                continue;
            }

            return feet;
        }

        return null;
    }

    private static boolean hasLoadedCollisionNeighborhood(ServerLevel level, int x, int z) {
        for (int chunkX = (x - 2) >> 4; chunkX <= (x + 2) >> 4; ++chunkX) {
            for (int chunkZ = (z - 2) >> 4; chunkZ <= (z + 2) >> 4; ++chunkZ) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
            }
        }
        return true;
    }

    private static boolean isSafeWolfSpawn(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos floor = feet.below();

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }

        return level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP);
    }

    private static boolean isPumpkin(BlockState state) {
        return state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN);
    }
}
