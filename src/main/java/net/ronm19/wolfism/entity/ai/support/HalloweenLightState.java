package net.ronm19.wolfism.entity.ai.support;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.ronm19.wolfism.Wolfism;

/** Saved, shared ownership of Halloween lights; never adopts an unrecorded Light block. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class HalloweenLightState extends SavedData {
    public static final int LIGHT_LEVEL = 10;
    private static final int LEASE_TICKS = 40;
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL);

    private record Lease(String owner, long expires) {
        private static final Codec<Lease> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("owner").forGetter(Lease::owner),
                Codec.LONG.fieldOf("expires").forGetter(Lease::expires)
        ).apply(instance, Lease::new));
    }

    private record Claim(BlockPos pos, BlockState original, List<Lease> leases) {
        private static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Claim::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Claim::original),
                Lease.CODEC.listOf().fieldOf("leases").forGetter(Claim::leases)
        ).apply(instance, Claim::new));
    }

    public static final Codec<HalloweenLightState> CODEC = Claim.CODEC.listOf()
            .optionalFieldOf("lights", List.of())
            .xmap(HalloweenLightState::new, state -> List.copyOf(state.claims.values())).codec();
    public static final SavedDataType<HalloweenLightState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "halloween_personal_lights"),
            () -> new HalloweenLightState(List.of()), CODEC);

    private final Map<BlockPos, Claim> claims = new LinkedHashMap<>();
    private final Map<String, BlockPos> positions = new HashMap<>();

    private HalloweenLightState(List<Claim> saved) {
        for (Claim claim : saved) {
            if (!claim.original().isAir() || claims.containsKey(claim.pos())) continue;
            var leases = new ArrayList<Lease>();
            for (Lease lease : claim.leases()) {
                if (positions.putIfAbsent(lease.owner(), claim.pos()) == null) leases.add(lease);
            }
            claims.put(claim.pos(), new Claim(claim.pos(), claim.original(), List.copyOf(leases)));
        }
    }

    public static HalloweenLightState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasClaim(BlockPos pos) { return claims.containsKey(pos); }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** Flags-3 changes need loaded neighbors; arbitrary mod propagation is outside this bound. */
    public static boolean canUpdateLight(ServerLevel level, BlockPos pos) {
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; ++dx) for (int dz = -1; dz <= 1; ++dz)
            if (level.getChunkSource().getChunkNow(x + dx, z + dz) == null) return false;
        return pos.getY() >= level.getMinY() && pos.getY() < level.getMaxY();
    }

    /** Renew one wolf's lease, sharing an existing journaled source when wolves overlap. */
    public void follow(ServerLevel level, UUID ownerId, BlockPos feet) {
        String owner = ownerId.toString();
        BlockPos previous = positions.get(owner);
        BlockPos desired = null;
        for (BlockPos candidate : List.of(feet, feet.above())) {
            if (!canUpdateLight(level, candidate)) continue;
            BlockState current = level.getBlockState(candidate);
            if (claims.containsKey(candidate) && !current.equals(LIGHT)) forget(candidate);
            if (current.isAir() || claims.containsKey(candidate) && current.equals(LIGHT)) {
                desired = candidate.immutable();
                break;
            }
        }
        if (previous != null && !previous.equals(desired)) release(level, ownerId);
        if (desired == null) return;
        Claim claim = claims.get(desired);
        if (claim == null) {
            BlockState original = level.getBlockState(desired);
            if (!original.isAir()) return;
            claim = new Claim(desired, original, List.of());
            claims.put(desired, claim);
            setDirty(); // Record ownership before editing the world.
            level.setBlock(desired, LIGHT, 3);
            // A neighbor callback can complete a real player replacement.
            // Never recreate ownership relinquished during that world update.
            if (claims.get(desired) != claim) return;
            if (!level.getBlockState(desired).equals(LIGHT)) {
                forget(desired);
                return;
            }
        }
        var leases = new ArrayList<Lease>();
        for (Lease lease : claim.leases()) if (!lease.owner().equals(owner)) leases.add(lease);
        leases.add(new Lease(owner, level.getGameTime() + LEASE_TICKS));
        claims.put(desired, new Claim(desired, claim.original(), List.copyOf(leases)));
        positions.put(owner, desired);
        setDirty();
    }

    public void release(ServerLevel level, UUID ownerId) {
        String owner = ownerId.toString();
        BlockPos pos = positions.remove(owner);
        if (pos == null) return;
        Claim claim = claims.get(pos);
        if (claim == null) return;
        var leases = claim.leases().stream().filter(lease -> !lease.owner().equals(owner)).toList();
        Claim remaining = new Claim(pos, claim.original(), leases);
        claims.put(pos, remaining);
        setDirty();
        if (leases.isEmpty()) restore(level, remaining);
    }

    public static void releaseOwner(ServerLevel level, UUID owner) {
        HalloweenLightState state = level.getDataStorage().get(TYPE);
        if (state != null) state.release(level, owner);
    }

    /** Called after all placement listeners accepted a real player placement, even if states match. */
    public static void playerPlaced(ServerLevel level, BlockPos pos) {
        HalloweenLightState state = level.getDataStorage().get(TYPE);
        if (state != null) state.forget(pos);
    }

    private void forget(BlockPos pos) {
        Claim removed = claims.remove(pos);
        if (removed == null) return;
        for (Lease lease : removed.leases()) positions.remove(lease.owner(), pos);
        setDirty();
    }

    private void restore(ServerLevel level, Claim claim) {
        if (!loaded(level, claim.pos())) return;
        if (!level.getBlockState(claim.pos()).equals(LIGHT)) {
            forget(claim.pos()); // Preserve changed or removed blocks.
            return;
        }
        if (!canUpdateLight(level, claim.pos())) return;
        level.setBlock(claim.pos(), claim.original(), 3);
        if (claims.get(claim.pos()) != claim) return;
        if (level.getBlockState(claim.pos()).equals(claim.original())) forget(claim.pos());
    }

    /** Cleanup runs without any surviving/loaded Halloween wolf and retries unloaded terrain. */
    public void expire(ServerLevel level) {
        long now = level.getGameTime();
        // Rotate at most 64 positions per pass without copying the whole journal.
        // Loaded and unloaded claims get equal turns as exploration grows it.
        int budget = Math.min(64, claims.size());
        for (int index = 0; index < budget && !claims.isEmpty(); ++index) {
            Claim claim = claims.values().iterator().next();
            claims.remove(claim.pos());
            claims.put(claim.pos(), claim);
            if (loaded(level, claim.pos()) && !level.getBlockState(claim.pos()).equals(LIGHT)) {
                forget(claim.pos());
                continue;
            }
            var remaining = claim.leases().stream().filter(lease -> lease.expires() > now).toList();
            if (remaining.size() != claim.leases().size()) {
                for (Lease lease : claim.leases()) if (lease.expires() <= now) positions.remove(lease.owner(), claim.pos());
                claim = new Claim(claim.pos(), claim.original(), remaining);
                claims.put(claim.pos(), claim);
                setDirty();
            }
            if (remaining.isEmpty()) restore(level, claim);
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 10 != 0) return;
        HalloweenLightState state = level.getDataStorage().get(TYPE);
        if (state != null && !state.claims.isEmpty()) state.expire(level);
    }

    private record Probe(BlockPos pos, int cost) {}

    /**
     * Determine whether an external emitter supplies block light above eight.
     * Reverse vanilla light propagation: entering each block costs at least one,
     * so a level-15 emitter cannot matter beyond six steps. Shapes and opacity
     * follow the vanilla engine, and only already-loaded blocks are examined.
     * Journaled level-10 sources remain transparent but are not emitters here.
     */
    public boolean hasBrightExternalBlockLight(ServerLevel level, BlockPos origin) {
        var queue = new PriorityQueue<Probe>(Comparator.comparingInt(Probe::cost));
        var costs = new HashMap<BlockPos, Integer>();
        var states = new HashMap<BlockPos, BlockState>();
        queue.add(new Probe(origin.immutable(), 0));
        costs.put(origin.immutable(), 0);
        while (!queue.isEmpty()) {
            Probe probe = queue.remove();
            if (probe.cost() != costs.get(probe.pos())) continue;
            BlockState current = loadedState(level, probe.pos(), states);
            if (current == null) continue;
            boolean own = claims.containsKey(probe.pos()) && current.equals(LIGHT);
            if (!own && current.getLightEmission(level, probe.pos()) - probe.cost() > 8) return true;
            int nextCost = probe.cost() + Math.max(1, current.getLightDampening());
            if (nextCost > 6) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = probe.pos().relative(direction);
                if (costs.getOrDefault(neighbor, 7) <= nextCost) continue;
                BlockState source = loadedState(level, neighbor, states);
                if (source == null || Shapes.faceShapeOccludes(
                        LightEngine.getOcclusionShape(source, direction.getOpposite()),
                        LightEngine.getOcclusionShape(current, direction))) continue;
                costs.put(neighbor, nextCost);
                queue.add(new Probe(neighbor, nextCost));
            }
        }
        return false;
    }

    private static BlockState loadedState(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> cache) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return null;
        if (cache.containsKey(pos)) return cache.get(pos);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockState state = chunk.getBlockState(pos);
        cache.put(pos.immutable(), state);
        return state;
    }
}
