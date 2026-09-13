package net.ronm19.wolfism.compat;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.core.WaystonePermissionManager;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Config;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;

/** Loaded only when Waystones is present; travel stays inside its own transaction. */
public final class WaystonesCompatibility {
    // The shared ordinary portal-follow behavior arms within 24 blocks.
    private static final double PORTAL_FOLLOW_RESET_RADIUS = 24.0D;
    private static final Map<WaystoneTeleportContext, Trip> TRIPS = new WeakHashMap<>();
    private static final int PREPARATION_TIMEOUT = 100, TICKET_LIFETIME = 120, TICKET_RADIUS = 3;
    private static final Map<WaystoneTeleportContext, Readiness> READINESS = new IdentityHashMap<>();
    private static boolean registered;
    private WaystonesCompatibility() {}

    public static void register() {
        if (registered) return;
        registered = true;
        WaystoneTeleportEvent.Before.EVENT.register(WaystonesCompatibility::prepare);
        WaystoneTeleportEvent.Prepare.EVENT.register(WaystonesCompatibility::prepareDestination);
        WaystoneTeleportEntityEvent.Pre.EVENT.register(WaystonesCompatibility::placeCompanion);
        WaystoneTeleportEntityEvent.Post.EVENT.register(WaystonesCompatibility::observeArrival);
        WaystoneTeleportEvent.Complete.EVENT.register(WaystonesCompatibility::complete);
        NeoForge.EVENT_BUS.addListener(WaystonesCompatibility::tickPreparations);
        NeoForge.EVENT_BUS.addListener(WaystonesCompatibility::stopPreparations);
        Wolfism.LOGGER.info("Waystones companion travel enabled");
    }

    private static void prepare(WaystoneTeleportEvent.Before event) {
        var context = event.getContext();
        if (event.isCanceled() || !(context.getEntity() instanceof ServerPlayer owner) || !owner.isAlive()) return;
        ServerLevel source = owner.level();
        var trip = new Trip(source, owner.getUUID());
        rememberPortalFollowers(trip, owner);
        // Even a disabled/empty automatic group needs to suppress the separate
        // ordinary portal follow after the owner's successful Waystones transfer.
        TRIPS.put(context, trip);
        if (!Config.WAYSTONES_COMPANIONS.get()) return;

        double range = Config.WAYSTONES_COMPANION_RANGE.get();
        var nearby = source.getEntitiesOfClass(AbstractWolfismWolf.class,
                owner.getBoundingBox().inflate(range),
                wolf -> wolf.isAlive() && wolf.isTame() && wolf.isOwnedBy(owner)
                        && wolf.distanceToSqr(owner) <= range * range);
        // Waystones can already enroll pets when its generic pet option is enabled.
        // Apply Wolfism's sit/recovery rules to those entries and avoid duplicates.
        context.getAdditionalEntities().removeIf(entity -> entity instanceof AbstractWolfismWolf wolf
                && wolf.isOwnedBy(owner) && (!canTravel(wolf, owner) || wolf.distanceToSqr(owner) > range * range));
        for (var wolf : nearby) {
            if (!canTravel(wolf, owner)) continue;
            if (!context.getAdditionalEntities().contains(wolf)) context.addAdditionalEntity(wolf);
            trip.companions.add(wolf.getUUID());
            trip.portalFollowers.add(wolf.getUUID());
        }
    }

    private static void prepareDestination(WaystoneTeleportEvent.Prepare event) {
        var context = event.getContext();
        var trip = TRIPS.get(context);
        if (trip == null || trip.companions.isEmpty()) return;
        var center = ChunkPos.containing(context.getTargetWaystone().getPos());
        // The arrival search can cross a chunk edge. Declare only its surrounding
        // nine chunks to Waystones; Minecraft supplies the normal loading halo.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) event.addChunkPosition(new ChunkPos(center.x() + x, center.z() + z));
        event.addPreparationTask(previous -> {
            if (previous.right().isPresent()) return CompletableFuture.completedFuture(previous);
            var target = trip.source.getServer().getLevel(context.getTargetWaystone().getDimension());
            if (target == null) return CompletableFuture.completedFuture(Either.right(new WaystoneTeleportError.InvalidDimension(context.getTargetWaystone().getDimension())));
            var pending = new Readiness(trip, target, center);
            READINESS.put(context, pending);
            try {
                target.getChunkSource().addTicketWithRadius(pending.ticketType, center, TICKET_RADIUS);
            } catch (RuntimeException error) {
                failPreparation(context, pending, "Could not prepare companion destination: " + error.getClass().getSimpleName());
            }
            return pending.future;
        });
    }

    private static void tickPreparations(ServerTickEvent.Post event) {
        // Completing a future immediately resumes Waystones and can call Complete,
        // which removes this entry. Never iterate the live collection here.
        for (var entry : new ArrayList<>(READINESS.entrySet())) {
            var context = entry.getKey(); var pending = entry.getValue();
            if (pending.target.getServer() != event.getServer()) continue;
            long elapsed = event.getServer().getTickCount() - pending.started;
            if (pending.future.isDone()) {
                // Another integration may append its own preparation task. Never
                // retain our ticket indefinitely while waiting for that work.
                if (elapsed >= TICKET_LIFETIME) releasePreparation(context);
                continue;
            }
            if (!(context.getEntity() instanceof ServerPlayer owner) || !owner.isAlive() || owner.isRemoved() || owner.level() != pending.trip.source) {
                failPreparation(context, pending, "The companion owner is no longer available at the source");
                continue;
            }
            if (elapsed >= PREPARATION_TIMEOUT) {
                failPreparation(context, pending, "Companion destination entities did not become ready in time");
                continue;
            }
            boolean ready = true;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var chunk = new ChunkPos(pending.center.x() + x, pending.center.z() + z);
                if (!pending.target.hasChunk(chunk.x(), chunk.z()) || !pending.target.areEntitiesActuallyLoadedAndTicking(chunk)) ready = false;
            }
            if (ready) pending.future.complete(Either.left(null));
        }
    }

    private static void failPreparation(WaystoneTeleportContext context, Readiness pending, String detail) {
        releasePreparation(context);
        // An exceptional preparation future crashes upstream Waystones. Expected
        // bounded failure must be its normal error result, before travel/charging.
        pending.future.complete(Either.right(new WaystoneTeleportError.DestinationChunkLoadFailed(
                pending.target.dimension(), pending.center, detail)));
    }

    private static void releasePreparation(WaystoneTeleportContext context) {
        var pending = READINESS.remove(context);
        if (pending != null) pending.target.getChunkSource().removeTicketWithRadius(pending.ticketType, pending.center, TICKET_RADIUS);
    }

    private static void stopPreparations(ServerStoppingEvent event) {
        for (var entry : new ArrayList<>(READINESS.entrySet())) {
            if (entry.getValue().target.getServer() == event.getServer()) failPreparation(entry.getKey(), entry.getValue(), "The server is stopping");
        }
    }

    public static int activePreparationTickets() { return READINESS.size(); }

    private static final class Readiness {
        final Trip trip;
        final ServerLevel target;
        final ChunkPos center;
        final long started;
        // TicketStorage compares types by identity, so concurrent trips at the
        // same destination own separate tickets. Nonpersistent types never enter
        // the registry codec; expiry is a final bound, not the usual cleanup path.
        final TicketType ticketType = new TicketType(TICKET_LIFETIME, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                | TicketType.FLAG_KEEP_DIMENSION_ACTIVE | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED);
        final CompletableFuture<Either<Void, WaystoneTeleportError>> future = new CompletableFuture<>();
        Readiness(Trip trip, ServerLevel target, ChunkPos center) {
            this.trip = trip; this.target = target; this.center = center; this.started = target.getServer().getTickCount();
        }
    }

    private static void rememberPortalFollowers(Trip trip, ServerPlayer owner) {
        if (owner.level() != trip.source) return;
        for (var wolf : trip.source.getEntitiesOfClass(AbstractWolfismWolf.class,
                owner.getBoundingBox().inflate(PORTAL_FOLLOW_RESET_RADIUS),
                wolf -> wolf.isTame() && wolf.isOwnedBy(owner)
                        && wolf.distanceToSqr(owner) <= PORTAL_FOLLOW_RESET_RADIUS * PORTAL_FOLLOW_RESET_RADIUS)) {
            trip.portalFollowers.add(wolf.getUUID());
        }
    }

    private static boolean canTravel(AbstractWolfismWolf wolf, ServerPlayer owner) {
        return wolf.isAlive() && !wolf.isRemoved() && wolf.isTame() && wolf.isOwnedBy(owner)
                && !wolf.isOrderedToSit() && !wolf.isNoAi() && !wolf.isPassenger()
                && !wolf.isVehicle() && !wolf.isLeashed()
                && !WaystonePermissionManager.isEntityDeniedTeleports(wolf)
                && !(wolf instanceof CreatorWolf creator && creator.isRecoveryActive())
                && !(wolf instanceof AbstractWolfismHolidayWolf holiday && holiday.isHolidayRecovering());
    }

    private static void placeCompanion(WaystoneTeleportEntityEvent.Pre event) {
        var trip = TRIPS.get(event.getContext());
        if (trip == null) return;
        if (event.getEntity() instanceof ServerPlayer owner && trip.owner.equals(owner.getUUID())) {
            // Chunk preparation can span ticks. Include wolves that approached the
            // owner while it was pending, without enrolling them in this trip.
            rememberPortalFollowers(trip, owner);
            return;
        }
        if (!trip.companions.contains(event.getEntity().getUUID())
                || !(event.getEntity() instanceof AbstractWolfismWolf wolf)) return;
        wolf.clearOwnerPortalFollowGrace();
        if (event.isCanceled()) return;
        if (!trip.ownerArrived || !(event.getContext().getEntity() instanceof ServerPlayer owner)
                || !trip.owner.equals(owner.getUUID()) || !canTravel(wolf, owner)
                || wolf.level() != trip.source || owner.level() != event.getTargetLevel()) {
            event.setCanceled(true);
            return;
        }
        BlockPos landing = wolf.findSafeCompanionArrival(event.getTargetLevel(),
                BlockPos.containing(event.getTargetPosition()));
        if (landing == null || !event.getTargetLevel().areEntitiesActuallyLoadedAndTicking(ChunkPos.containing(landing))) {
            event.setCanceled(true);
            return;
        }
        event.setTargetPosition(new Vec3(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5));
    }

    private static void observeArrival(WaystoneTeleportEntityEvent.Post event) {
        var trip = TRIPS.get(event.getContext());
        if (trip == null) return;
        if (event.getEntity().getUUID().equals(trip.owner)) {
            trip.ownerArrived = event.getTeleportResult().isSuccessful();
            if (trip.ownerArrived) {
                for (UUID id : trip.portalFollowers) {
                    if (trip.source.getEntity(id) instanceof AbstractWolfismWolf wolf) {
                        wolf.clearOwnerPortalFollowGrace();
                    }
                }
            }
        } else if (trip.companions.contains(event.getEntity().getUUID())
                && event.getTeleportResult().isSuccessful()
                && event.getTeleportedEntity() instanceof AbstractWolfismWolf wolf) {
            wolf.setTarget(null);
            wolf.getNavigation().stop();
            wolf.setInSittingPose(false);
            wolf.clearOwnerPortalFollowGrace();
            trip.arrived.add(wolf.getUUID());
        }
    }

    private static void complete(WaystoneTeleportEvent.Complete event) {
        releasePreparation(event.getContext());
        var trip = TRIPS.remove(event.getContext());
        if (trip == null || !trip.ownerArrived || trip.companions.isEmpty()
                || !(event.getContext().getEntity() instanceof ServerPlayer owner)) return;
        int moved = trip.arrived.size();
        int stayed = trip.companions.size() - moved;
        owner.sendOverlayMessage(stayed == 0
                ? Component.translatable("message.wolfism.waystones_arrived", moved)
                : Component.translatable("message.wolfism.waystones_partial", moved, stayed));
    }

    private static final class Trip {
        final ServerLevel source;
        final UUID owner;
        final Set<UUID> portalFollowers = new HashSet<>();
        final Set<UUID> companions = new HashSet<>();
        final Set<UUID> arrived = new HashSet<>();
        boolean ownerArrived;
        Trip(ServerLevel source, UUID owner) {
            this.source = source;
            this.owner = owner;
        }
    }
}
