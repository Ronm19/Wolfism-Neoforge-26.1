package net.ronm19.wolfism.event;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;

/**
 * Shared loaded-chunk encounter helper for the final Holiday Wolves.
 *
 * <p>The real-world date is the rarity, so an eligible player should have a
 * reliable opportunity to meet each wolf during its season. This helper never
 * asks for an unavailable chunk, never runs from WorldGenRegion, and caps wild
 * encounters locally so a seasonal biome cannot become a dog printer.</p>
 */
public final class HolidayWolfSeasonalSpawner {
    private static final int LOCAL_WILD_CAP = 4;
    private static final double LOCAL_RADIUS = 64.0D;
    private static final int AREA_COOLDOWN = 20 * 30;

    private HolidayWolfSeasonalSpawner() {
    }

    public static <T extends AbstractWolfismHolidayWolf> void trySpawnEncounter(
            ServerPlayer player,
            String cooldownKey,
            Predicate<ServerLevel> seasonOpen,
            TagKey<Biome> biomeTag,
            EntityType<T> entityType,
            BiPredicate<ServerLevel, BlockPos> safeSurface,
            float secondWolfChance) {
        if (player.isSpectator()) {
            return;
        }

        ServerLevel level = player.level();
        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !seasonOpen.test(level)
                || !level.getBiome(player.blockPosition()).is(biomeTag)) {
            return;
        }

        long now = level.getGameTime();
        if (player.getPersistentData().getLongOr(cooldownKey, 0L) > now) {
            return;
        }

        AABB playerArea = player.getBoundingBox().inflate(
                LOCAL_RADIUS,
                32.0D,
                LOCAL_RADIUS);
        int existing = countWild(level, playerArea, entityType);
        if (existing >= LOCAL_WILD_CAP) {
            return;
        }

        int wanted = 1;
        if (existing + wanted < LOCAL_WILD_CAP
                && level.getRandom().nextFloat() < secondWolfChance) {
            ++wanted;
        }
        wanted = Math.min(wanted, LOCAL_WILD_CAP - existing);

        int created = 0;
        for (int attempt = 0; attempt < 40 && created < wanted; ++attempt) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 26.0D + level.getRandom().nextDouble() * 20.0D;
            int x = player.blockPosition().getX()
                    + (int) Math.round(Math.cos(angle) * distance);
            int z = player.blockPosition().getZ()
                    + (int) Math.round(Math.sin(angle) * distance);

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
                    || !level.getBiome(feet).is(biomeTag)
                    || !safeSurface.test(level, feet)
                    || level.getNearestPlayer(
                            x + 0.5D,
                            y,
                            z + 0.5D,
                            24.0D,
                            false) != null) {
                continue;
            }

            AABB spawnBox = new AABB(
                    x + 0.2D,
                    y,
                    z + 0.2D,
                    x + 0.8D,
                    y + 0.85D,
                    z + 0.8D);
            if (!level.noCollision(spawnBox)
                    || countWild(
                            level,
                            spawnBox.inflate(LOCAL_RADIUS, 32.0D, LOCAL_RADIUS),
                            entityType) >= LOCAL_WILD_CAP) {
                continue;
            }

            T wolf = entityType.spawn(level, feet, EntitySpawnReason.EVENT);
            if (wolf != null && !wolf.isRemoved()) {
                ++created;
            }
        }

        if (created > 0) {
            player.getPersistentData().putLong(
                    cooldownKey,
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

    private static int countWild(
            ServerLevel level,
            AABB area,
            EntityType<?> type) {
        return level.getEntitiesOfClass(
                        AbstractWolfismHolidayWolf.class,
                        area,
                        wolf -> wolf.isAlive()
                                && !wolf.isTame()
                                && wolf.getType() == type)
                .size();
    }
}
