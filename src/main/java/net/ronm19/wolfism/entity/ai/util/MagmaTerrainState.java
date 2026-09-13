package net.ronm19.wolfism.entity.ai.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.MagmaWolf;

/** Per-dimension ownership of lava temporarily solidified by Magma Wolves. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class MagmaTerrainState extends SavedData {
    private static final int VISITS_PER_TICK = 64;
    private static final int RETRY_TICKS = 40;

    private record Entry(BlockPos pos, BlockState original, BlockState replacement,
                         String actor, long expires, Optional<BlockState> playerEdit) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Entry::original),
                BlockState.CODEC.fieldOf("replacement").forGetter(Entry::replacement),
                Codec.STRING.fieldOf("actor").forGetter(Entry::actor),
                Codec.LONG.fieldOf("expires").forGetter(Entry::expires),
                BlockState.CODEC.optionalFieldOf("pending_break").forGetter(Entry::playerEdit)
        ).apply(i, Entry::new));

        private Entry retry(long next) {
            return new Entry(pos, original, replacement, actor, next, playerEdit);
        }
    }

    public static final Codec<MagmaTerrainState> CODEC = Entry.CODEC.listOf()
            .optionalFieldOf("blocks", List.of())
            .xmap(MagmaTerrainState::new, state -> List.copyOf(state.entries.values())).codec();
    public static final SavedDataType<MagmaTerrainState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "magma_temporary_terrain"),
            () -> new MagmaTerrainState(List.of()), CODEC);

    private final Map<BlockPos, Entry> entries = new HashMap<>();
    // One queue node per journal position. Removing an entry leaves its node for
    // the bounded sweep; reusing that position reuses the existing queue node.
    private final ArrayDeque<BlockPos> sweep = new ArrayDeque<>();
    private final java.util.Set<BlockPos> queued = new java.util.HashSet<>();

    private MagmaTerrainState(List<Entry> saved) {
        for (Entry entry : saved) {
            // Only this journal proves ownership. Never infer it from existing
            // obsidian or attempt to migrate the former, unsaved static map.
            if (!entry.original().getFluidState().is(FluidTags.LAVA)) continue;
            try { UUID.fromString(entry.actor()); } catch (IllegalArgumentException invalid) { continue; }
            if (entries.putIfAbsent(entry.pos(), entry) == null) enqueue(entry.pos());
        }
    }

    public static MagmaTerrainState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Checks full chunks without requesting tickets or synchronous loads. */
    public static boolean loadedNeighborhood(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; ++dx) for (int dz = -1; dz <= 1; ++dz) {
            if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
        }
        return true;
    }

    private void enqueue(BlockPos pos) {
        if (queued.add(pos)) sweep.addLast(pos);
    }

    private void put(Entry entry) {
        entries.put(entry.pos(), entry);
        enqueue(entry.pos());
        setDirty();
    }

    private void forget(BlockPos pos) {
        if (entries.remove(pos) != null) setDirty();
    }

    boolean place(ServerLevel level, MagmaWolf wolf, BlockPos pos, BlockState replacement, long expires) {
        if (wolf.level() != level || !wolf.isAlive() || wolf.isBaby()
                || !loadedNeighborhood(level, pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !EventHooks.canEntityGrief(level, wolf)) return false;
        Entry previous = resolvePlayerEdit(level, entries.get(pos));
        BlockState current = level.getBlockState(pos);
        if (previous != null && !current.equals(previous.replacement())) {
            // Mining or replacement ends the debt; restoring lava here would
            // duplicate material after the player harvested the temporary block.
            forget(pos);
            previous = null;
        }
        if (previous != null) {
            if (!previous.replacement().equals(replacement)) return false;
            boolean allowed = permitted(level, pos, replacement, wolf);
            if (entries.get(pos) != previous || !allowed) return false;
            if (!loadedNeighborhood(level, pos)) return false;
            if (!level.getBlockState(pos).equals(previous.replacement())) {
                forget(pos);
                return false;
            }
            put(previous.retry(Math.max(previous.expires(), expires)));
            return true;
        }
        if (!current.getFluidState().is(FluidTags.LAVA) || !permitted(level, pos, replacement, wolf)) return false;
        // Claim callbacks may edit terrain. Do not overwrite their replacement.
        if (!loadedNeighborhood(level, pos) || !level.getBlockState(pos).equals(current)) return false;
        Entry entry = new Entry(pos.immutable(), current, replacement, wolf.getUUID().toString(), expires, Optional.empty());
        put(entry);
        boolean changed = level.setBlockAndUpdate(pos, replacement);
        if (!level.getBlockState(pos).equals(replacement)) {
            forget(pos);
            return false;
        }
        return changed;
    }

    private Entry resolvePlayerEdit(ServerLevel level, Entry entry) {
        if (entry == null || entry.playerEdit().isEmpty() || !loadedNeighborhood(level, entry.pos())) return entry;
        if (!entry.playerEdit().get().equals(level.getBlockState(entry.pos()))) {
            forget(entry.pos());
            return null;
        }
        Entry unchanged = new Entry(entry.pos(), entry.original(), entry.replacement(),
                entry.actor(), entry.expires(), Optional.empty());
        put(unchanged);
        return unchanged;
    }

    private void notePlayerBreak(BlockPos pos, BlockState before) {
        Entry entry = entries.get(pos);
        if (entry != null) put(new Entry(entry.pos(), entry.original(), entry.replacement(),
                entry.actor(), entry.expires(), Optional.of(before)));
    }

    private void visit(ServerLevel level, BlockPos pos, long now) {
        Entry entry = entries.get(pos);
        if (entry == null || !loadedNeighborhood(level, pos)) return;
        entry = resolvePlayerEdit(level, entry);
        if (entry == null) return;
        if (!level.getBlockState(pos).equals(entry.replacement())) {
            forget(pos);
            return;
        }
        if (entry.expires() > now) return;
        AABB standingSpace = new AABB(pos.getX(), pos.getY() + 0.80D, pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.25D, pos.getZ() + 1.0D);
        if (!level.getEntitiesOfClass(LivingEntity.class, standingSpace, LivingEntity::isAlive).isEmpty()) {
            put(entry.retry(now + RETRY_TICKS));
            return;
        }
        Entity actor = level.getEntity(UUID.fromString(entry.actor()));
        // This is cleanup of an already-authorized change, not new griefing.
        // Global mobGriefing may now be off and the wolf may no longer exist.
        // Claims still receive the exact proposed restoration; a denial retains
        // the durable debt until permission becomes available again.
        boolean allowed = permitted(level, pos, entry.original(), actor);
        // A callback may complete a same-state player replacement or renew this
        // position. Never resurrect or restore an entry whose ownership changed.
        if (entries.get(pos) != entry) return;
        if (!allowed) {
            put(entry.retry(now + RETRY_TICKS));
            return;
        }
        if (!loadedNeighborhood(level, pos)) return;
        if (!level.getBlockState(pos).equals(entry.replacement())) {
            forget(pos);
            return;
        }
        level.setBlockAndUpdate(pos, entry.original());
        if (level.getBlockState(pos).equals(entry.replacement())) put(entry.retry(now + RETRY_TICKS));
        else forget(pos);
    }

    private static boolean permitted(ServerLevel level, BlockPos pos, BlockState proposed, Entity actor) {
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        var against = level.getBlockState(pos.relative(Direction.DOWN));
        // For non-player actors NeoForge's stock event uses the snapshot state
        // as the placed block. Preserve that snapshot as the original and expose
        // the proposed block explicitly, before any mutation or neighbor updates.
        var event = new BlockEvent.EntityPlaceEvent(snapshot, against, actor) {
            @Override public BlockState getState() { return proposed; }
            @Override public BlockState getPlacedBlock() { return proposed; }
        };
        return !NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MagmaTerrainState state = level.getDataStorage().get(TYPE);
        if (state != null) state.notePlayerBreak(event.getPos(), event.getState());
    }

    /** Called only after the final accepted player placement hook has returned. */
    public static void playerPlaced(ServerLevel level, BlockPos pos) {
        MagmaTerrainState state = level.getDataStorage().get(TYPE);
        if (state != null) state.forget(pos);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // get loads a saved journal even when there are no Magma Wolves at all.
        MagmaTerrainState state = level.getDataStorage().get(TYPE);
        if (state == null) return;
        int visits = Math.min(VISITS_PER_TICK, state.sweep.size());
        long now = level.getGameTime();
        for (int i = 0; i < visits; ++i) {
            BlockPos pos = state.sweep.removeFirst();
            state.queued.remove(pos);
            state.visit(level, pos, now);
            if (state.entries.containsKey(pos)) state.enqueue(pos);
        }
    }
}
