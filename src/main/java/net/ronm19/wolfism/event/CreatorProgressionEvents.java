package net.ronm19.wolfism.event;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.creator.CreatorDeveloperAccess;
import net.ronm19.wolfism.creator.CreatorProgress;
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModStructureTags;

/**
 * Creator's player-facing acquisition lifecycle.
 *
 * <p>FIRST ENCOUNTER: earn Timber + Primordial + Wolf King + Salva, return to
 * the player's bed/base, and Creator appears there in his stationary,
 * invulnerable waiting state.</p>
 *
 * <p>AFTER "VANISH": the progression key remains earned, but Creator no longer
 * returns politely to the base. The player must find him again at a meaningful
 * vanilla structure.</p>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class CreatorProgressionEvents {
    private static final int PROGRESSION_CHECK_INTERVAL = 20 * 5;
    private static final double FIRST_ARRIVAL_HOME_RADIUS = 112.0D;
    private static final double VANISH_COMMAND_RADIUS = 12.0D;
    private static final double STRUCTURE_ACTIVATION_RADIUS = 176.0D;
    private static final int STRUCTURE_SEARCH_RADIUS_CHUNKS = 32;

    /**
     * A newly investigated eligible structure has a hidden chance to be the
     * Creator encounter for this hunt. The roll is deterministic for that
     * structure during the current hunt generation, so waiting beside one
     * structure can never reroll it.
     *
     * 40% keeps the hunt meaningful without making reacquisition miserable.
     */
    private static final int STRUCTURE_ENCOUNTER_CHANCE_PERCENT = 40;

    private CreatorProgressionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CreatorProgress.scanNearbyOwnedPrerequisites(player);

        if (CreatorDeveloperAccess.isDeveloper(player)
                && !CreatorProgress.get(player, CreatorProgress.DEV_GRANTED)) {
            grantDeveloperCreator(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer replacement)) {
            return;
        }

        // Persistent entity data is not guaranteed to copy through a death
        // clone, so Creator progression explicitly survives it.
        if (event.isWasDeath()) {
            CreatorProgress.copyToClone(original, replacement);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % PROGRESSION_CHECK_INTERVAL != 0) {
            return;
        }

        CreatorProgress.scanNearbyOwnedPrerequisites(player);

        /*
         * Developer access only guarantees the FIRST Creator.
         *
         * Once that Creator has been deliberately vanished, developers fall
         * through into the exact same post-vanish structure hunt as everybody
         * else. DEV_GRANTED deliberately remains true so login/tick can never
         * hand them another free replacement.
         */
        if (CreatorDeveloperAccess.isDeveloper(player)
                && !CreatorProgress.get(player, CreatorProgress.DEV_GRANTED)) {
            grantDeveloperCreator(player);
            return;
        }

        if (CreatorProgress.get(player, CreatorProgress.CREATOR_TAMED)
                || CreatorProgress.get(player, CreatorProgress.ENCOUNTER_ACTIVE)) {
            return;
        }

        if (CreatorProgress.get(player, CreatorProgress.POST_VANISH_HUNT)) {
            trySpawnPostVanishCreator(player);
            return;
        }

        if (CreatorProgress.hasAllPrerequisites(player)
                && !CreatorProgress.get(player, CreatorProgress.FIRST_ARRIVAL_SPAWNED)) {
            trySpawnFirstArrival(player);
        }
    }

    /**
     * Spoken command rather than a slash command. It only consumes chat when an
     * owned Creator is actually close enough and the player is looking toward
     * him, avoiding accidental vanish triggers in normal conversation.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String raw = event.getRawText().trim();
        if (!raw.equalsIgnoreCase("vanish")
                && !raw.equalsIgnoreCase("creator vanish")
                && !raw.equalsIgnoreCase("creator, vanish")) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();

        CreatorWolf creator = level.getEntitiesOfClass(
                        CreatorWolf.class,
                        player.getBoundingBox().inflate(VANISH_COMMAND_RADIUS),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && wolf.isOwnedBy(player))
                .stream()
                .filter(wolf -> isLookingAt(player, wolf))
                .min(Comparator.comparingDouble(wolf -> wolf.distanceToSqr(player)))
                .orElse(null);

        if (creator == null) return;

        event.setCanceled(true);
        CreatorProgress.onCreatorVanished(player);
        creator.vanishByOwner(player);

        player.sendSystemMessage(
                Component.translatable("message.wolfism.creator_vanished"));
    }

    private static boolean isLookingAt(ServerPlayer player, CreatorWolf creator) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 toCreator = creator.getEyePosition().subtract(player.getEyePosition());
        if (toCreator.lengthSqr() < 0.001D) return true;
        return look.dot(toCreator.normalize()) >= 0.42D;
    }

    private static void grantDeveloperCreator(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos spawnPos = findAdaptiveCreatorArrival(
                level,
                player.blockPosition(),
                player.blockPosition(),
                false,
                level.getRandom());

        CreatorWolf creator = spawnCreator(level, spawnPos);
        if (creator == null) return;

        creator.prepareDeveloperGift(player);
        creator.tame(player);
        CreatorProgress.markDeveloperGranted(player);

        player.sendSystemMessage(
                Component.translatable("message.wolfism.creator_dev_granted"));
    }

    private static void trySpawnFirstArrival(ServerPlayer player) {
        HomeAnchor home = resolveHome(player);
        if (home == null) return;

        ServerLevel playerLevel = (ServerLevel) player.level();
        if (home.level() != playerLevel) return;

        if (player.distanceToSqr(Vec3.atCenterOf(home.pos()))
                > FIRST_ARRIVAL_HOME_RADIUS * FIRST_ARRIVAL_HOME_RADIUS) {
            return;
        }

        BlockPos spawnPos = findAdaptiveCreatorArrival(
                home.level(),
                home.pos(),
                player.blockPosition(),
                home.hasRespawnPoint(),
                home.level().getRandom());

        CreatorWolf creator = spawnCreator(home.level(), spawnPos);
        if (creator == null) return;

        creator.prepareUnlockEncounter(player, false);
        CreatorProgress.markFirstArrival(player);
        creator.playCreatorArrivalHowl();

        player.sendSystemMessage(
                Component.translatable("message.wolfism.creator_arrived"));
    }

    private static void trySpawnPostVanishCreator(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos structure = level.findNearestMapStructure(
                ModStructureTags.CREATOR_REACQUISITION_STRUCTURES,
                player.blockPosition(),
                STRUCTURE_SEARCH_RADIUS_CHUNKS,
                false);

        if (structure == null) return;

        if (player.distanceToSqr(Vec3.atCenterOf(structure))
                > STRUCTURE_ACTIVATION_RADIUS * STRUCTURE_ACTIVATION_RADIUS) {
            return;
        }

        /*
         * Do NOT guarantee Creator at the first structure the player visits.
         * Every eligible structure has one stable hidden result for this hunt.
         * If this structure misses, camping here cannot reroll it; move on.
         */
        if (!isChosenCreatorHuntStructure(player, level, structure)) {
            return;
        }

        BlockPos spawnPos = findSafeNear(
                level,
                structure,
                4,
                11,
                level.getRandom());

        CreatorWolf creator = spawnCreator(level, spawnPos);
        if (creator == null) return;

        creator.prepareUnlockEncounter(player, true);
        CreatorProgress.markReacquisitionEncounter(player);
        creator.playCreatorArrivalHowl();
    }

    private static boolean isChosenCreatorHuntStructure(
            ServerPlayer player,
            ServerLevel level,
            BlockPos structure) {

        int generation = Math.max(1, CreatorProgress.getHuntGeneration(player));

        long value = structure.asLong();
        value ^= player.getUUID().getMostSignificantBits();
        value ^= Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23);
        // ResourceKey uses object identity: hashing it rerolls this hunt after
        // a server restart. The dimension identifier is stable across loads.
        value ^= ((long) level.dimension().identifier().toString().hashCode()) * 0x9E3779B97F4A7C15L;
        value ^= ((long) generation) * 0xD1B54A32D192ED03L;

        long mixed = mixCreatorHuntHash(value);
        int roll = (int) Math.floorMod(mixed, 100L);

        return roll < STRUCTURE_ENCOUNTER_CHANCE_PERCENT;
    }

    /**
     * SplitMix64-style avalanche. No mutable RandomSource is involved, which
     * is what makes one structure's result stable for the entire hunt.
     */
    private static long mixCreatorHuntHash(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static CreatorWolf spawnCreator(ServerLevel level, BlockPos pos) {
        // Search helpers may exhaust all candidates. Never turn their fallback
        // into a wolf embedded in a bed, outside the border, or over a hazard.
        // The progression flags are set only after a successful spawn, so the
        // next progression check can retry once a safe space is available.
        if (!isSafeStandingSpot(level, pos)) return null;
        CreatorWolf creator = ModEntities.CREATOR_WOLF.get()
                .create(level, EntitySpawnReason.EVENT);
        if (creator == null) return null;

        creator.snapTo(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D);
        if (!level.getWorldBorder().isWithinBounds(creator.getBoundingBox())
                || !level.noCollision(creator)
                || !level.isUnobstructed(creator)) return null;
        creator.setPersistenceRequired();

        if (!level.addFreshEntity(creator)) return null;
        return creator;
    }

    /**
     * A bed/respawn anchor is the best vanilla signal for "my base". If a
     * player has never set one, Creator uses the player's current location when
     * the final prerequisite is completed rather than inventing a magic base.
     */
    private static HomeAnchor resolveHome(ServerPlayer player) {
        ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn != null) {
            var data = respawn.respawnData();
            ServerLevel level = player.level().getServer().getLevel(data.dimension());
            if (level != null) {
                return new HomeAnchor(level, data.pos(), true);
            }
        }

        return new HomeAnchor(
                (ServerLevel) player.level(),
                player.blockPosition(),
                false);
    }

    /**
     * Chooses Creator's FIRST-arrival position according to the kind of base
     * the player actually built instead of assuming every home is an open
     * surface camp.
     *
     * OPEN_AIR:
     *   Creator appears farther out, like the current savanna test.
     *
     * ELEVATED:
     *   Sky islands/towers use local platform-aware placement and NEVER
     *   project Creator down to the terrain heightmap below.
     *
     * SHELTERED:
     *   Search close to the bed/current position and strongly prefer the same
     *   floor level so a house does not place Creator on its roof.
     *
     * UNDERGROUND:
     *   Never use surface heightmap placement. Search the local cave/base volume.
     *
     * CRAMPED:
     *   Use the closest safe nearby position available instead of forcing a
     *   dramatic distance that the room cannot support.
     */
    private static BlockPos findAdaptiveCreatorArrival(
            ServerLevel level,
            BlockPos anchor,
            BlockPos playerPos,
            boolean hasRespawnPoint,
            RandomSource random) {

        BaseEnvironment environment = classifyBaseEnvironment(level, anchor);

        return switch (environment) {
            case OPEN_AIR -> findSurfaceArrival(
                    level,
                    anchor,
                    hasRespawnPoint ? 7 : 5,
                    hasRespawnPoint ? 13 : 9,
                    random);

            case ELEVATED -> findLocalArrival(
                    level,
                    anchor,
                    hasRespawnPoint ? 3 : 2,
                    hasRespawnPoint ? 8 : 6,
                    4,
                    false,
                    random);

            case SHELTERED -> findLocalArrival(
                    level,
                    anchor,
                    2,
                    hasRespawnPoint ? 6 : 5,
                    2,
                    true,
                    random);

            case UNDERGROUND -> findLocalArrival(
                    level,
                    anchor,
                    3,
                    hasRespawnPoint ? 8 : 6,
                    4,
                    false,
                    random);

            case CRAMPED -> {
                BlockPos close = findLocalArrival(
                        level,
                        anchor,
                        1,
                        4,
                        2,
                        false,
                        random);

                /*
                 * If the bed is jammed into a tiny room, the player's current
                 * position is a better fallback than teleporting Creator to
                 * the roof or far outside the base.
                 */
                if (close.equals(anchor) && !playerPos.equals(anchor)) {
                    yield findLocalArrival(
                            level,
                            playerPos,
                            1,
                            5,
                            2,
                            false,
                            random);
                }

                yield close;
            }
        };
    }

    private static BaseEnvironment classifyBaseEnvironment(
            ServerLevel level,
            BlockPos anchor) {

        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                anchor.getX(),
                anchor.getZ());

        boolean seesSky = level.canSeeSky(anchor.above());
        boolean deepBelowSurface = anchor.getY() + 5 < surfaceY;
        boolean farAboveTerrain = anchor.getY() > surfaceY + 8;

        /*
         * A floating island, airship, tall tower platform, or other elevated
         * base must NEVER use the world's terrain heightmap for arrival. Doing
         * so could place Creator far below the actual home.
         */
        if (farAboveTerrain) {
            return BaseEnvironment.ELEVATED;
        }

        if (!seesSky && deepBelowSurface) {
            return BaseEnvironment.UNDERGROUND;
        }

        int usableNearbySpots = countNearbySafeStandingSpots(
                level,
                anchor,
                4,
                2,
                18);

        if (usableNearbySpots < 6) {
            return BaseEnvironment.CRAMPED;
        }

        if (!seesSky) {
            return BaseEnvironment.SHELTERED;
        }

        return BaseEnvironment.OPEN_AIR;
    }

    private static int countNearbySafeStandingSpots(
            ServerLevel level,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            int stopAfter) {

        int count = 0;

        for (int dx = -horizontalRadius; dx <= horizontalRadius; ++dx) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; ++dz) {
                if (dx == 0 && dz == 0) continue;

                for (int dy = -verticalRadius; dy <= verticalRadius; ++dy) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!isSafeStandingSpot(level, candidate)) continue;

                    ++count;
                    if (count >= stopAfter) return count;
                }
            }
        }

        return count;
    }

    /**
     * Surface-style placement for open bases. Prefers roughly the same terrain
     * elevation as the home so Creator does not appear on a distant hilltop or
     * at the bottom of a ravine merely because it is technically "nearby".
     */
    private static BlockPos findSurfaceArrival(
            ServerLevel level,
            BlockPos center,
            int minRadius,
            int maxRadius,
            RandomSource random) {

        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int attempt = 0; attempt < 48; ++attempt) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = minRadius + random.nextInt(
                    Math.max(1, maxRadius - minRadius + 1));

            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x,
                    z);

            BlockPos candidate = new BlockPos(x, y, z);
            if (!isSafeStandingSpot(level, candidate)) continue;

            int heightDifference = Math.abs(candidate.getY() - center.getY());

            // Strongly prefer a visually obvious spot on roughly the same grade.
            double score = heightDifference * 4.0D
                    + Math.abs(radius - ((minRadius + maxRadius) * 0.5D));

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) return best;

        return findSafeNear(level, center, minRadius, maxRadius, random);
    }

    /**
     * Room/cave-aware placement. Unlike findSafeNear(), this does not project
     * candidates onto the world's surface heightmap.
     */
    private static BlockPos findLocalArrival(
            ServerLevel level,
            BlockPos center,
            int minRadius,
            int maxRadius,
            int verticalRadius,
            boolean preferSheltered,
            RandomSource random) {

        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int attempt = 0; attempt < 72; ++attempt) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = minRadius + random.nextInt(
                    Math.max(1, maxRadius - minRadius + 1));

            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

            /*
             * Try the home floor first, then nearby floors. This prevents a
             * two-story house from casually choosing the roof before checking
             * the room the player actually lives in.
             */
            for (int step = 0; step <= verticalRadius; ++step) {
                int[] offsets = step == 0
                        ? new int[]{0}
                        : new int[]{step, -step};

                for (int dy : offsets) {
                    BlockPos candidate = new BlockPos(x, center.getY() + dy, z);
                    if (!isSafeStandingSpot(level, candidate)) continue;

                    boolean sheltered = !level.canSeeSky(candidate.above());

                    double score =
                            Math.abs(dy) * 5.0D
                            + Math.abs(radius - ((minRadius + maxRadius) * 0.5D));

                    if (preferSheltered && !sheltered) {
                        score += 14.0D;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        if (best != null) return best;

        /*
         * Last-resort local scan. Still refuses to jump to the terrain surface
         * for indoor/underground homes.
         */
        for (int radius = 1; radius <= Math.max(2, maxRadius); ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    for (int dy = -verticalRadius; dy <= verticalRadius; ++dy) {
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isSafeStandingSpot(level, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return center;
    }

    private static BlockPos findSafeNear(
            ServerLevel level,
            BlockPos center,
            int minRadius,
            int maxRadius,
            RandomSource random) {

        for (int attempt = 0; attempt < 32; ++attempt) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeStandingSpot(level, candidate)) return candidate;
        }

        int fallbackY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.getX(),
                center.getZ());
        return new BlockPos(center.getX(), fallbackY, center.getZ());
    }

    private static boolean isSafeStandingSpot(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isInWorldBounds(pos.above())
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.hasChunkAt(pos)) return false;
        var floor = level.getBlockState(pos.below());
        if (floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || floor.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above())
                        .isEmpty()
                && !level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below())
                        .isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private enum BaseEnvironment {
        OPEN_AIR,
        ELEVATED,
        SHELTERED,
        UNDERGROUND,
        CRAMPED
    }

    private record HomeAnchor(
            ServerLevel level,
            BlockPos pos,
            boolean hasRespawnPoint) {
    }
}
