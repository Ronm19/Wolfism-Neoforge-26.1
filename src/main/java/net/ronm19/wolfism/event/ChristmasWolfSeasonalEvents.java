package net.ronm19.wolfism.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ChristmasWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Makes the holiday date, not the passive-creature cap, the rarity.
 * Supplements biome spawning with bounded, loaded-chunk-only winter encounters.
 * No pumpkin scans and no block reads from WorldGenRegion.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ChristmasWolfSeasonalEvents {
    private static final int CHECK_INTERVAL = 20 * 8;
    private static final int LOCAL_WILD_CAP = 4;
    private static final double LOCAL_RADIUS = 64.0D;
    private static final String NEXT_ENCOUNTER = "WolfismChristmasNextEncounter";
    private static final int AREA_COOLDOWN = 20 * 30;

    private ChristmasWolfSeasonalEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || player.tickCount % CHECK_INTERVAL != 0) return;
        ServerLevel level = player.level();
        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !ChristmasWolf.isChristmasSeasonOpen(level)
                || !level.getBiome(player.blockPosition()).is(ModBiomeTags.CHRISTMAS_WOLF_SPAWNS)) {
            return;
        }
        long now = level.getGameTime();
        if (player.getPersistentData().getLongOr(NEXT_ENCOUNTER, 0L) > now) return;
        int count = countWild(level, player.getBoundingBox().inflate(LOCAL_RADIUS, 32.0D, LOCAL_RADIUS));
        if (count >= LOCAL_WILD_CAP) return;

        int wanted = Math.min(LOCAL_WILD_CAP - count,
                level.getRandom().nextBoolean() ? 2 : 1);
        int created = 0;
        for (int attempt = 0; attempt < 32 && created < wanted; ++attempt) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 26.0D + level.getRandom().nextDouble() * 20.0D;
            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);

            // getChunkNow returns null; it never forces a neighboring chunk to load.
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null || !hasLoadedCollisionNeighborhood(level, x, z)) continue;
            // ChunkAccess#getHeight is the top occupied Y, not Level#getHeight's first-air Y.
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(feet)
                    || !level.getBiome(feet).is(ModBiomeTags.CHRISTMAS_WOLF_SPAWNS)
                    || !ChristmasWolf.isSafeChristmasSurface(level, feet)
                    || level.getNearestPlayer(x + 0.5D, y, z + 0.5D, 24.0D, false) != null) {
                continue;
            }
            AABB box = new AABB(x + 0.2D, y, z + 0.2D, x + 0.8D, y + 0.85D, z + 0.8D);
            if (!level.noCollision(box)
                    || countWild(level, box.inflate(LOCAL_RADIUS, 32.0D, LOCAL_RADIUS)) >= LOCAL_WILD_CAP) {
                continue;
            }
            // Use EntityType.spawn rather than raw addFreshEntity: finalizes the
            // living entity and passes through NeoForge's mob-spawn lifecycle.
            ChristmasWolf wolf = ModEntities.CHRISTMAS_WOLF.get().spawn(
                    level, feet, EntitySpawnReason.EVENT);
            if (wolf != null && !wolf.isRemoved()) ++created;
        }
        if (created > 0) {
            player.getPersistentData().putLong(NEXT_ENCOUNTER, now + AREA_COOLDOWN);
        }
    }

    private static boolean hasLoadedCollisionNeighborhood(ServerLevel level, int x, int z) {
        // Collision queries have a small padding around the bounding box. At a
        // chunk edge, verify those neighbors too, before asking for any block data.
        for (int chunkX = (x - 2) >> 4; chunkX <= (x + 2) >> 4; ++chunkX) {
            for (int chunkZ = (z - 2) >> 4; chunkZ <= (z + 2) >> 4; ++chunkZ) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
            }
        }
        return true;
    }

    private static int countWild(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(
                ChristmasWolf.class, area, wolf -> wolf.isAlive() && !wolf.isTame()).size();
    }
}
