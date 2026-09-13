package net.ronm19.wolfism.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.ronm19.wolfism.Config;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/** Bounded natural encounters in existing habitats, independent of vanilla livestock occupancy. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WolfHabitatSpawner {
    private static final int CHECK_INTERVAL = 100;
    private static final int MAX_COLUMNS = 16;
    private static final int MIN_RADIUS = 32;
    private static final int MAX_RADIUS = 64;
    private static final int LOCAL_RADIUS = 96;
    private static final int LOCAL_VERTICAL_RADIUS = 64;
    private static final int TERRAIN_MARGIN = 10;
    private static final int MAX_VERTICAL_SCAN = 48;
    private static final List<MobCategory> CATEGORIES = List.of(
            MobCategory.CREATURE, MobCategory.AMBIENT, MobCategory.MONSTER);
    private static final Set<String> SEPARATE_ENCOUNTERS = Set.of(
            "creator_wolf", "halloween_wolf", "christmas_wolf", "saint_patricks_wolf",
            "new_years_wolf", "valentines_wolf", "easter_wolf", "firework_wolf");
    private static final Map<ServerLevel, Integer> NEXT_PLAYER = new WeakHashMap<>();

    private WolfHabitatSpawner() {}

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.getGameTime() % CHECK_INTERVAL != 0
                || !enabled(level)) return;
        List<ServerPlayer> eligible = level.players().stream()
                .filter(player -> !player.isSpectator() && player.isAlive()).toList();
        if (eligible.isEmpty()) return;
        int index = Math.floorMod(NEXT_PLAYER.getOrDefault(level, 0), eligible.size());
        NEXT_PLAYER.put(level, (index + 1) % eligible.size());
        trySpawnEncounter(eligible.get(index));
    }

    /** Runs one bounded encounter attempt; returns whether a natural wolf was actually added. */
    public static boolean trySpawnEncounter(ServerPlayer player) {
        ServerLevel level = player.level();
        if (player.isSpectator() || !player.isAlive() || !enabled(level)) return false;
        int cap = Config.HABITAT_WILD_CAP.get();
        if (wildCount(level, player.blockPosition(), null) >= cap) return false;
        LevelChunk playerChunk = level.getChunkSource().getChunkNow(
                player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (playerChunk == null) return false;
        int playerSurface = playerChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX() & 15, player.getBlockZ() & 15) + 1;
        boolean underground = level.dimensionType().hasCeiling() || player.getBlockY() + 8 < playerSurface;
        RandomSource random = level.getRandom();
        Set<Long> columns = new HashSet<>();
        MobSpawnSettings.SpawnerData data = null;
        MobCategory selectedCategory = null;
        for (int attempt = 0; attempt < MAX_COLUMNS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!columns.add(columnKey(x, z))) continue;
            BlockPos feet = findFeet(level, x, z, player.getBlockY(), underground, random);
            if (feet == null || !withinEncounterRadius(player, feet)
                    || !safeDistanceAndBorder(level, feet)) continue;

            if (data == null) {
                List<SpawnTable> tables = eligibleTables(level, feet);
                if (tables.isEmpty()) continue;
                SpawnTable table = tables.get(random.nextInt(tables.size()));
                // One weighted draw per encounter, from the FULL category table.
                // A vanilla/non-wolf win ends this encounter; terrain retries
                // never turn a legendary weight of one into repeated lottery draws.
                var selected = table.entries().getRandom(random);
                if (selected.isEmpty() || !isHabitatWolf(selected.get().type())) return false;
                data = selected.get();
                selectedCategory = table.category();
            } else if (!tableContains(level, feet, selectedCategory, data)) {
                continue;
            }
            int room = remainingRoom(level, player, feet, data, cap);
            int minimum = Math.max(1, data.minCount());
            int maximum = Math.min(data.maxCount(), room);
            if (maximum < minimum) continue;
            int wanted = minimum + random.nextInt(maximum - minimum + 1);
            SpawnResult first = spawnMember(level, player, feet, data, cap, null);
            if (first == null) continue;

            int created = 1;
            SpawnGroupData groupData = first.groupData();
            // One actual group per interval. Group members keep that species;
            // each new column still requires its own current biome/event table.
            for (int memberAttempt = attempt + 1; memberAttempt < MAX_COLUMNS && created < wanted; memberAttempt++) {
                int memberX = feet.getX() + random.nextInt(9) - 4;
                int memberZ = feet.getZ() + random.nextInt(9) - 4;
                if (!columns.add(columnKey(memberX, memberZ))) continue;
                BlockPos memberFeet = findFeet(level, memberX, memberZ, feet.getY(), underground, random);
                if (memberFeet == null || !withinEncounterRadius(player, memberFeet)
                        || !safeDistanceAndBorder(level, memberFeet)
                        || !tableContains(level, memberFeet, selectedCategory, data)) continue;
                SpawnResult next = spawnMember(level, player, memberFeet, data, cap, groupData);
                if (next != null) {
                    groupData = next.groupData();
                    created++;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean enabled(ServerLevel level) {
        return Config.HABITAT_REPLENISHMENT.get()
                && level.getGameRules().get(GameRules.SPAWN_MOBS)
                && level.getServer().tickRateManager().runsNormally();
    }

    private static List<SpawnTable> eligibleTables(ServerLevel level, BlockPos feet) {
        var tables = new ArrayList<SpawnTable>();
        if (!loadedStructureReferences(level, feet)) return tables;
        for (MobCategory category : CATEGORIES) {
            if (!category.isFriendly() && !level.isSpawningMonsters()) continue;
            WeightedList<MobSpawnSettings.SpawnerData> entries = spawnTable(level, feet, category);
            if (entries.unwrap().stream().anyMatch(entry -> entry.weight() > 0 && isHabitatWolf(entry.value().type()))) {
                tables.add(new SpawnTable(category, entries));
            }
        }
        return tables;
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> spawnTable(
            ServerLevel level, BlockPos feet, MobCategory category) {
        var original = level.getChunkSource().getGenerator().getMobsAt(
                level.getBiome(feet), level.structureManager(), category, feet);
        return EventHooks.getPotentialSpawns(level, category, feet, original);
    }

    private static boolean tableContains(ServerLevel level, BlockPos feet, MobCategory category, MobSpawnSettings.SpawnerData data) {
        return (category.isFriendly() || level.isSpawningMonsters())
                && loadedStructureReferences(level, feet)
                && spawnTable(level, feet, category).unwrap().stream()
                        .anyMatch(entry -> entry.weight() > 0 && entry.value().equals(data));
    }

    private static boolean isHabitatWolf(EntityType<?> type) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id.getNamespace().equals(Wolfism.MOD_ID)
                && (id.getPath().endsWith("_wolf") || id.getPath().equals("wolf_king"))
                && !SEPARATE_ENCOUNTERS.contains(id.getPath());
    }

    private static BlockPos findFeet(ServerLevel level, int x, int z, int nearY,
                                     boolean underground, RandomSource random) {
        if (!loadedNeighborhood(level, x, z)) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return null;
        int minY = level.getMinY() + 1;
        int maxY = level.getMaxY() - 2;
        if (level.dimensionType().hasCeiling()) {
            // The Nether's build height extends above its playable ceiling.
            maxY = Math.min(maxY, level.getMinY() + level.dimensionType().logicalHeight() - 2);
        }
        if (!underground) {
            int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
            if (surface < minY || surface > maxY) return null;
            BlockPos water = new BlockPos(x, surface - 1, z);
            if (chunk.getFluidState(water).is(FluidTags.WATER)) {
                int depth = Math.min(3, water.getY() - minY);
                BlockPos submerged = water.below(random.nextInt(depth + 1));
                return chunk.getFluidState(submerged).is(FluidTags.WATER) ? submerged : water;
            }
            return new BlockPos(x, surface, z);
        }
        int top = Math.clamp(nearY + 12 + random.nextInt(13), minY, maxY);
        int bottom = Math.max(minY, top - MAX_VERTICAL_SCAN + 1);
        for (int y = top; y >= bottom; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (chunk.getFluidState(feet).is(FluidTags.WATER)) return feet;
            if (!chunk.getFluidState(feet).isEmpty()) continue;
            var body = chunk.getBlockState(feet);
            var floor = chunk.getBlockState(feet.below());
            if (body.getCollisionShape(level, feet).isEmpty()
                    && chunk.getFluidState(feet.above()).isEmpty()
                    && chunk.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && floor.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return feet;
        }
        return null;
    }

    private static boolean loadedNeighborhood(ServerLevel level, int x, int z) {
        for (int chunkX = (x - TERRAIN_MARGIN) >> 4; chunkX <= (x + TERRAIN_MARGIN) >> 4; chunkX++) {
            for (int chunkZ = (z - TERRAIN_MARGIN) >> 4; chunkZ <= (z + TERRAIN_MARGIN) >> 4; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null
                        || !level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(chunkX, chunkZ))) return false;
            }
        }
        return true;
    }

    private static boolean loadedStructureReferences(ServerLevel level, BlockPos feet) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4);
        if (chunk == null) return false;
        // getMobsAt may consult a structure start stored in a different chunk.
        // Refuse missing starts before that vanilla lookup can request a load.
        for (var references : chunk.getAllReferences().values()) {
            for (long reference : references) {
                if (level.getChunkSource().getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference)) == null) return false;
            }
        }
        return true;
    }

    private static boolean withinEncounterRadius(ServerPlayer player, BlockPos feet) {
        double dx = feet.getX() + .5 - player.getX();
        double dz = feet.getZ() + .5 - player.getZ();
        double distanceSquared = dx * dx + dz * dz;
        return distanceSquared >= MIN_RADIUS * MIN_RADIUS && distanceSquared <= MAX_RADIUS * MAX_RADIUS;
    }

    private static boolean safeDistanceAndBorder(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || !level.canSpawnEntitiesInChunk(ChunkPos.containing(feet))
                || !level.anyPlayerCloseEnoughForSpawning(feet)) return false;
        Vec3 position = new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
        // Match natural spawning's active-player and shared-spawn protection.
        for (ServerPlayer nearby : level.players()) {
            if (!nearby.isSpectator() && nearby.distanceToSqr(position) <= 24.0 * 24.0) return false;
        }
        var respawn = level.getRespawnData();
        return respawn.dimension() != level.dimension() || !respawn.pos().closerToCenterThan(position, 24.0);
    }

    private static int wildCount(ServerLevel level, BlockPos center, EntityType<?> type) {
        return level.getEntitiesOfClass(AbstractWolfismWolf.class,
                new AABB(center).inflate(LOCAL_RADIUS, LOCAL_VERTICAL_RADIUS, LOCAL_RADIUS),
                wolf -> wolf.isAlive() && !wolf.isTame() && (type == null || wolf.getType() == type)).size();
    }

    private static int remainingRoom(ServerLevel level, ServerPlayer player, BlockPos feet,
                                     MobSpawnSettings.SpawnerData data, int cap) {
        int speciesCap = data.maxCount() <= 1 ? 1 : (int) Math.min(cap, 2L * data.maxCount());
        return Math.min(Math.min(cap - wildCount(level, player.blockPosition(), null), cap - wildCount(level, feet, null)),
                Math.min(speciesCap - wildCount(level, player.blockPosition(), data.type()),
                        speciesCap - wildCount(level, feet, data.type())));
    }

    private static SpawnResult spawnMember(ServerLevel level, ServerPlayer player, BlockPos feet,
                                           MobSpawnSettings.SpawnerData data, int cap, SpawnGroupData groupData) {
        if (remainingRoom(level, player, feet, data, cap) <= 0
                || !SpawnPlacements.isSpawnPositionOk(data.type(), level, feet)
                || !SpawnPlacements.checkSpawnRules(data.type(), level, EntitySpawnReason.NATURAL, feet, level.getRandom())) return null;
        AABB body = data.type().getSpawnAABB(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
        if (!level.getWorldBorder().isWithinBounds(body) || !level.noCollision(body)) return null;
        var entity = data.type().create(level, EntitySpawnReason.NATURAL);
        if (!(entity instanceof AbstractWolfismWolf wolf)) return null;
        wolf.snapTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!EventHooks.checkSpawnPosition(wolf, level, EntitySpawnReason.NATURAL)) return null;
        SpawnGroupData updated = EventHooks.finalizeMobSpawn(wolf, level,
                level.getCurrentDifficultyAt(feet), EntitySpawnReason.NATURAL, groupData);
        // FinalizeSpawn/EntityJoin cancellation remains authoritative in the
        // normal add path; do not set persistence or force an entity into a chunk.
        // A finalization listener may reposition the entity. Never let that
        // bypass the loaded-chunk, distance, collision or local-population bounds.
        BlockPos finalizedFeet = wolf.blockPosition();
        if (!loadedNeighborhood(level, finalizedFeet.getX(), finalizedFeet.getZ())
                || !withinEncounterRadius(player, finalizedFeet)
                || !safeDistanceAndBorder(level, finalizedFeet)
                || remainingRoom(level, player, finalizedFeet, data, cap) <= 0
                || !level.getWorldBorder().isWithinBounds(wolf.getBoundingBox())
                || !level.noCollision(wolf)) return null;
        level.tryAddFreshEntityWithPassengers(wolf);
        return wolf.isAddedToLevel() ? new SpawnResult(updated) : null;
    }

    private static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private record SpawnTable(MobCategory category, WeightedList<MobSpawnSettings.SpawnerData> entries) {}
    private record SpawnResult(SpawnGroupData groupData) {}
}
