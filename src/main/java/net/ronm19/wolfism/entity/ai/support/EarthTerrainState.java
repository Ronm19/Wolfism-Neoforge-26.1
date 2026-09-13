package net.ronm19.wolfism.entity.ai.support;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.ronm19.wolfism.Wolfism;

/**
 * Coordinate ownership and restoration journal for temporary Earth terrain.
 * Records contain no entity/world references. A pending unloaded-chunk repair
 * survives both the wolf's removal and a server restart. Edits wait for a loaded
 * neighborhood so their immediate neighbor updates do not load boundary chunks.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class EarthTerrainState extends SavedData {
    private record PlayerEdit(BlockState before, Optional<BlockState> placed) {
        private static final Codec<PlayerEdit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockState.CODEC.fieldOf("before").forGetter(PlayerEdit::before),
                BlockState.CODEC.optionalFieldOf("placed").forGetter(PlayerEdit::placed)
        ).apply(instance, PlayerEdit::new));

        private boolean applied(BlockState current) {
            return !current.equals(before) && placed.map(current::equals).orElse(true);
        }
    }

    private record Move(String owner, BlockPos source, Optional<BlockPos> destination,
                        BlockState state, long expires, Optional<PlayerEdit> playerEdit) {
        private static final Codec<Move> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("owner").forGetter(Move::owner),
                BlockPos.CODEC.fieldOf("source").forGetter(Move::source),
                BlockPos.CODEC.optionalFieldOf("destination").forGetter(Move::destination),
                BlockState.CODEC.fieldOf("state").forGetter(Move::state),
                Codec.LONG.fieldOf("expires").forGetter(Move::expires),
                PlayerEdit.CODEC.optionalFieldOf("player_edit").forGetter(Move::playerEdit)
        ).apply(instance, Move::new));
    }

    public static final Codec<EarthTerrainState> CODEC = Move.CODEC.listOf()
            .optionalFieldOf("moves", List.of())
            .xmap(EarthTerrainState::new, state -> List.copyOf(state.moves.values())).codec();
    public static final SavedDataType<EarthTerrainState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "earth_temporary_terrain"),
            () -> new EarthTerrainState(List.of()), CODEC);

    private final Map<BlockPos, Move> moves = new HashMap<>();
    private final Map<BlockPos, Move> reservations = new HashMap<>();
    private final Set<BlockPos> pendingPlayerEdits = new HashSet<>();

    private EarthTerrainState(List<Move> saved) {
        for (Move move : saved) {
            if (reserved(move.source()) || move.destination().filter(this::reserved).isPresent()) continue;
            moves.put(move.source(), move);
            reservations.put(move.source(), move);
            move.destination().ifPresent(pos -> reservations.put(pos, move));
            if (move.playerEdit().isPresent()) pendingPlayerEdits.add(move.source());
        }
    }

    public static EarthTerrainState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean reserved(BlockPos pos) {
        return reservations.containsKey(pos);
    }

    public boolean reserve(UUID owner, BlockPos source, BlockPos destination,
                           BlockState state, long expires) {
        if (reserved(source) || destination != null && reserved(destination)) return false;
        Move move = new Move(owner.toString(), source.immutable(),
                Optional.ofNullable(destination).map(BlockPos::immutable), state, expires, Optional.empty());
        moves.put(move.source(), move);
        reservations.put(move.source(), move);
        move.destination().ifPresent(pos -> reservations.put(pos, move));
        setDirty();
        return true;
    }

    /** Undo a reservation whose extraction never succeeded; no world edits. */
    public void abandon(UUID owner, BlockPos source) {
        Move move = owned(owner, source);
        if (move != null) forget(move);
    }

    /** Extraction succeeded but destination placement did not; only repay the source. */
    public void rollbackUnplaced(ServerLevel level, UUID owner, BlockPos source) {
        Move move = owned(owner, source);
        if (move == null) return;
        move.destination().ifPresent(pos -> reservations.remove(pos, move));
        Move pending = new Move(move.owner(), move.source(), Optional.empty(), move.state(), 0, Optional.empty());
        moves.put(pending.source(), pending);
        reservations.put(pending.source(), pending);
        pendingPlayerEdits.remove(pending.source());
        setDirty();
        restore(level, owner, source);
    }

    public void restore(ServerLevel level, UUID owner, BlockPos source) {
        Move move = owned(owner, source);
        if (move == null) return;
        if (!tryRestore(level, move)) {
            // Release is deferred until both neighborhoods are available. Persist the
            // immediate deadline so a removed wolf is never needed for cleanup.
            Move current = owned(owner, source);
            if (current != null) replace(current, new Move(current.owner(), current.source(),
                    current.destination(), current.state(), 0, current.playerEdit()));
        }
    }

    private Move owned(UUID owner, BlockPos source) {
        Move move = moves.get(source);
        return move != null && move.owner().equals(owner.toString()) ? move : null;
    }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /**
     * Flags-3 block changes notify neighbors. Require the surrounding full chunks
     * before editing, not just the edited chunk. This covers immediate boundary
     * updates; it is not a guarantee about arbitrary redstone/mod propagation.
     */
    public static boolean canUpdateTerrain(ServerLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) return false;
            }
        }
        return true;
    }

    private boolean tryRestore(ServerLevel level, Move original) {
        Move move = resolvePlayerEdit(level, original);
        if (move == null) return true;
        if (!canUpdateTerrain(level, move.source())
                || move.destination().filter(pos -> !canUpdateTerrain(level, pos)).isPresent()) return false;
        if (move.destination().isPresent()) {
            BlockPos destination = move.destination().get();
            if (!stillOwned(move.state(), level.getBlockState(destination))) {
                // A player mined/replaced the moved block. Restoring its source
                // would duplicate material; preserve the player's edit.
                forget(move);
                return true;
            }
            level.removeBlock(destination, false);
            if (!level.getBlockState(destination).isAir()) return false;
        }
        if (level.getBlockState(move.source()).isAir()) {
            level.setBlockAndUpdate(move.source(), move.state());
            if (!level.getBlockState(move.source()).equals(move.state())) {
                // Destination has been consumed, so retain a source-only debt.
                move.destination().ifPresent(pos -> reservations.remove(pos, move));
                Move pending = new Move(move.owner(), move.source(), Optional.empty(), move.state(), 0, Optional.empty());
                moves.put(pending.source(), pending);
                reservations.put(pending.source(), pending);
                setDirty();
                return true;
            }
        }
        // Never overwrite a player block placed in the original hole.
        forget(move);
        return true;
    }

    private void forget(Move move) {
        moves.remove(move.source(), move);
        reservations.remove(move.source(), move);
        move.destination().ifPresent(pos -> reservations.remove(pos, move));
        pendingPlayerEdits.remove(move.source());
        setDirty();
    }

    private static boolean stillOwned(BlockState original, BlockState current) {
        // Covered Dirt Shell grass/mycelium naturally decays. Keep the original
        // source state, but consume its decayed dirt at the temporary destination.
        return current.equals(original) || current.is(Blocks.DIRT)
                && (original.is(Blocks.GRASS_BLOCK) || original.is(Blocks.MYCELIUM));
    }

    private void replace(Move previous, Move updated) {
        if (!moves.replace(previous.source(), previous, updated)) return;
        reservations.put(updated.source(), updated);
        updated.destination().ifPresent(pos -> reservations.put(pos, updated));
        if (updated.playerEdit().isPresent()) pendingPlayerEdits.add(updated.source());
        else pendingPlayerEdits.remove(updated.source());
        setDirty();
    }

    private void notePlayerEdit(BlockPos pos, BlockState before, Optional<BlockState> placed) {
        Move move = reservations.get(pos);
        if (move == null || move.destination().filter(pos::equals).isEmpty()) return;
        replace(move, new Move(move.owner(), move.source(), move.destination(), move.state(),
                move.expires(), Optional.of(new PlayerEdit(before, placed))));
    }

    private Move resolvePlayerEdit(ServerLevel level, Move move) {
        if (move.playerEdit().isEmpty() || move.destination().isEmpty()
                || !loaded(level, move.destination().get())) return move;
        if (move.playerEdit().get().applied(level.getBlockState(move.destination().get()))) {
            // A real player action ended ownership, including mining grass and
            // putting dirt back. That must not be mistaken for natural decay.
            forget(move);
            return null;
        }
        Move unchanged = new Move(move.owner(), move.source(), move.destination(), move.state(),
                move.expires(), Optional.empty());
        replace(move, unchanged);
        return unchanged;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        EarthTerrainState state = level.getDataStorage().get(TYPE);
        if (state != null) state.notePlayerEdit(event.getPos(), event.getState(), Optional.empty());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) return;
        EarthTerrainState state = level.getDataStorage().get(TYPE);
        if (state == null) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) {
                state.notePlayerEdit(snapshot.getPos(), snapshot.getState(), Optional.of(snapshot.getCurrentState()));
            }
        } else {
            state.notePlayerEdit(event.getPos(), event.getBlockSnapshot().getState(), Optional.of(event.getPlacedBlock()));
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        EarthTerrainState state = level.getDataStorage().get(TYPE);
        if (state == null || state.moves.isEmpty()) return;
        // Break/place events are cancellable and may precede their actual edit.
        // Verify the outcome once the action has completed; canceled attempts do
        // not surrender ownership. Pending verification also survives saving.
        for (BlockPos source : new ArrayList<>(state.pendingPlayerEdits)) {
            Move move = state.moves.get(source);
            if (move != null) state.resolvePlayerEdit(level, move);
        }
        if (level.getGameTime() % 10 != 0) return;
        long now = level.getGameTime();
        for (Move move : new ArrayList<>(state.moves.values())) {
            if (move.expires() <= now) state.tryRestore(level, move);
        }
    }
}
