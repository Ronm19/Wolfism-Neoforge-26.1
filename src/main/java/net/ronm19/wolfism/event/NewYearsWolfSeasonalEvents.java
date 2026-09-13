package net.ronm19.wolfism.event;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Common New Year's encounters during the short real-world window. Bells are
 * preferred celebration anchors, but exploring a valid biome is still enough.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class NewYearsWolfSeasonalEvents {
    private static final int CHECK_INTERVAL = 20 * 8;
    private static final int LOCAL_WILD_CAP = 4;
    private static final double LOCAL_RADIUS = 64.0D;
    private static final String NEXT_ENCOUNTER = "WolfismNewYearsNextEncounter";
    private static final int AREA_COOLDOWN = 20 * 30;

    private NewYearsWolfSeasonalEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()
                || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        ServerLevel level = player.level();
        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !NewYearsWolf.isNewYearsSeasonOpen(level)
                || !level.getBiome(player.blockPosition())
                .is(ModBiomeTags.NEW_YEARS_WOLF_SPAWNS)) {
            return;
        }

        long now = level.getGameTime();
        if (player.getPersistentData().getLongOr(NEXT_ENCOUNTER, 0L) > now) {
            return;
        }

        int existing = countWild(
                level,
                player.getBoundingBox().inflate(LOCAL_RADIUS, 32.0D, LOCAL_RADIUS));
        if (existing >= LOCAL_WILD_CAP) {
            return;
        }

        Optional<BlockPos> bell = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.MEETING),
                player.blockPosition(),
                48,
                PoiManager.Occupancy.ANY);
        BlockPos anchor = bell.orElse(player.blockPosition());
        boolean hasBellAnchor = bell.isPresent();

        int wanted = Math.min(
                LOCAL_WILD_CAP - existing,
                level.getRandom().nextFloat() < 0.50F ? 2 : 1);
        int created = 0;

        for (int attempt = 0; attempt < 40 && created < wanted; ++attempt) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double minimum = hasBellAnchor ? 10.0D : 26.0D;
            double spread = hasBellAnchor ? 18.0D : 20.0D;
            double distance = minimum + level.getRandom().nextDouble() * spread;
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);

            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null || !hasLoadedCollisionNeighborhood(level, x, z)) {
                continue;
            }

            int y = chunk.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x & 15,
                    z & 15) + 1;
            BlockPos feet = new BlockPos(x, y, z);

            if (!level.getWorldBorder().isWithinBounds(feet)
                    || !level.getBiome(feet).is(ModBiomeTags.NEW_YEARS_WOLF_SPAWNS)
                    || !NewYearsWolf.isSafeNewYearsSurface(level, feet)
                    || level.getNearestPlayer(
                            x + 0.5D,
                            y,
                            z + 0.5D,
                            hasBellAnchor ? 10.0D : 24.0D,
                            false) != null) {
                continue;
            }

            AABB box = new AABB(
                    x + 0.2D, y, z + 0.2D,
                    x + 0.8D, y + 0.85D, z + 0.8D);
            if (!level.noCollision(box)
                    || countWild(
                            level,
                            box.inflate(LOCAL_RADIUS, 32.0D, LOCAL_RADIUS))
                    >= LOCAL_WILD_CAP) {
                continue;
            }

            NewYearsWolf wolf = ModEntities.NEW_YEARS_WOLF.get().spawn(
                    level,
                    feet,
                    EntitySpawnReason.EVENT);
            if (wolf != null && !wolf.isRemoved()) {
                ++created;
            }
        }

        if (created > 0) {
            player.getPersistentData().putLong(
                    NEXT_ENCOUNTER,
                    now + AREA_COOLDOWN);
        }
    }

    private static boolean hasLoadedCollisionNeighborhood(
            ServerLevel level,
            int x,
            int z) {
        for (int chunkX = (x - 2) >> 4; chunkX <= (x + 2) >> 4; ++chunkX) {
            for (int chunkZ = (z - 2) >> 4; chunkZ <= (z + 2) >> 4; ++chunkZ) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countWild(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(
                NewYearsWolf.class,
                area,
                wolf -> wolf.isAlive() && !wolf.isTame())
                .size();
    }
}
