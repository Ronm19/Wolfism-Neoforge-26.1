package net.ronm19.wolfism.vfx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.ronm19.wolfism.Wolfism;

/** Server-safe emission adapter. Existing ability cadence and sampled geometry remain authoritative. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WolfVfx {
    public static final int VIEW_RANGE = 48;
    private static final int MAX_PACKETS_PER_PLAYER_TICK = 24;
    private static final int MAX_SOURCES = 128;
    private static final int MAX_GROUPS_PER_SOURCE = 8;
    private static final Map<ServerLevel, Map<Long, List<Batch>>> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Entity> TICK_SOURCE = new ThreadLocal<>();
    private static long admittedPackets;
    private static long emergencyEvents;

    private WolfVfx() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(WolfVfxPayload.TYPE, WolfVfxPayload.STREAM_CODEC);
    }

    public static void beginEntity(Entity entity) { TICK_SOURCE.set(entity); }
    public static void endEntity() { TICK_SOURCE.remove(); }
    public static long admittedPackets() { return admittedPackets; }
    public static long emergencyEvents() { return emergencyEvents; }

    /** Confirmation only, called after a real rescue succeeds and spends its resource. */
    public static void emergency(Entity rescuer, net.minecraft.world.entity.LivingEntity beneficiary) {
        if (!(rescuer.level() instanceof ServerLevel level) || beneficiary.level() != level) return;
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(rescuer.getType()).getPath();
        var profile = WolfVfxProfile.of(species);
        var position = beneficiary.position().add(0, beneficiary.getBbHeight() + .04, 0);
        float radius = Math.clamp(beneficiary.getBbWidth() * .5f + .25f, .55f, 1.8f);
        enqueue(level, new WolfVfxPayload(level.dimension().identifier(), position.x, position.y, position.z,
                profile.style(), 8, radius, .42f, radius, 0, species, rescuer.getId(),
                WolfVfxPhase.EMERGENCY, List.of()));
        emergencyEvents++;
    }

    public static <T extends ParticleOptions> int sendParticles(ServerLevel level, T particle,
            double x, double y, double z, int count, double sx, double sy, double sz, double speed) {
        var source = TICK_SOURCE.get();
        String species = source == null ? "generic" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).getPath();
        return sendParticles(species, level, particle, x, y, z, count, sx, sy, sz, speed);
    }

    public static <T extends ParticleOptions> int sendParticles(String species, ServerLevel level, T particle,
            double x, double y, double z, int count, double sx, double sy, double sz, double speed) {
        var style = WolfVfxStyle.fromParticle(particle);
        // Hearts, status icons, dust colors, block/item fragments and directed velocity
        // retain their exact data. Sampled count-one paths now use one batched Photon shape.
        if (style == null || count <= 0) return level.sendParticles(particle, x, y, z, count, sx, sy, sz, speed);
        var source = TICK_SOURCE.get();
        int sourceId = source != null && source.level() == level ? source.getId() : 0;
        var phase = WolfVfxPhase.fromEmission(count, sx, sy, sz);
        enqueue(level, new WolfVfxPayload(level.dimension().identifier(), x, y, z, style, count,
                (float) sx, (float) sy, (float) sz, (float) speed, species, sourceId, phase,
                count == 1 ? List.of(Vec3.ZERO) : List.of()));
        return (int) level.players().stream().filter(p -> canReceive(p)
                && p.distanceToSqr(x, y, z) <= VIEW_RANGE * VIEW_RANGE).count();
    }

    /** Used by actual damage/projectile hooks, with the real source entity for pack fairness. */
    public static void present(Entity source, Vec3 position, WolfVfxPhase phase, List<Vec3> relativePoints) {
        if (!(source.level() instanceof ServerLevel level)) return;
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).getPath();
        var profile = WolfVfxProfile.of(species);
        int count = switch (phase) { case PASSIVE -> 2; case CAST -> 10; case PATH -> 12;
            case IMPACT -> 12; case AREA -> 20; case ULTIMATE -> 28; case EMERGENCY -> 8; };
        enqueue(level, new WolfVfxPayload(level.dimension().identifier(), position.x, position.y, position.z,
                profile.style(), count, .42f, .32f, .42f, .035f, species, source.getId(), phase, relativePoints));
    }

    public static void enqueue(ServerLevel level, WolfVfxPayload payload) {
        // Never allocate for a remote, unseen pack.
        if (level.players().stream().noneMatch(p -> canReceive(p) && p.distanceToSqr(payload.x(), payload.y(), payload.z())
                <= VIEW_RANGE * VIEW_RANGE)) return;
        var sources = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        long key = payload.sourceKey();
        if (!sources.containsKey(key) && sources.size() >= MAX_SOURCES) return;
        var groups = sources.computeIfAbsent(key, ignored -> new ArrayList<>());
        for (var batch : groups) {
            if (batch.accepts(payload)) { batch.merge(payload); return; }
        }
        if (groups.size() < MAX_GROUPS_PER_SOURCE) groups.add(new Batch(payload));
        else {
            // Keep high-information attacks over another tiny passive ornament.
            int lowest = 0;
            for (int i = 1; i < groups.size(); i++) {
                if (groups.get(i).first.phase().priority() < groups.get(lowest).first.phase().priority()) lowest = i;
            }
            if (payload.phase().priority() > groups.get(lowest).first.phase().priority()) groups.set(lowest, new Batch(payload));
        }
    }

    @SubscribeEvent
    public static void flush(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        endEntity();
        var sources = PENDING.remove(level);
        if (sources == null) return;
        var candidates = sources.values().stream().flatMap(List::stream).map(Batch::payload).toList();
        for (var player : level.players()) {
            if (!canReceive(player)) continue;
            var nearby = candidates.stream().filter(p -> player.distanceToSqr(p.x(), p.y(), p.z())
                    <= VIEW_RANGE * VIEW_RANGE).toList();
            for (var payload : WolfVfxBudget.select(nearby, MAX_PACKETS_PER_PLAYER_TICK, level.getGameTime())) {
                PacketDistributor.sendToPlayer(player, payload);
                admittedPackets++;
            }
        }
    }

    private static boolean canReceive(ServerPlayer player) {
        // Fake players and connections still negotiating have no client payload channel.
        return player.connection != null && player.connection.hasChannel(WolfVfxPayload.TYPE);
    }

    private static final class Batch {
        private final WolfVfxPayload first;
        private final List<Vec3> samples = new ArrayList<>();
        private int seen;
        private int count;
        private float sx, sy, sz;

        private Batch(WolfVfxPayload first) {
            this.first = first;
            merge(first);
        }

        private boolean accepts(WolfVfxPayload other) {
            if (first.style() != other.style() || first.phase() != other.phase()
                    || !first.species().equals(other.species())) return false;
            // Separate rescued recipients keep their own confirmation even when a
            // single support wolf saves more than one family member in the same tick.
            double range = first.phase() == WolfVfxPhase.EMERGENCY ? .05 : first.phase() == WolfVfxPhase.PATH ? 24 : 1.4;
            return new Vec3(first.x(), first.y(), first.z()).distanceToSqr(new Vec3(other.x(), other.y(), other.z())) <= range * range;
        }

        private void merge(WolfVfxPayload other) {
            count = Math.min(32, count + other.count());
            sx = Math.max(sx, other.spreadX()); sy = Math.max(sy, other.spreadY()); sz = Math.max(sz, other.spreadZ());
            for (var point : other.points()) {
                var relative = point.add(other.x() - first.x(), other.y() - first.y(), other.z() - first.z());
                if (Math.abs(relative.x) > 32 || Math.abs(relative.y) > 32 || Math.abs(relative.z) > 32) continue;
                seen++;
                if (samples.size() < WolfVfxPayload.MAX_POINTS) samples.add(relative);
                else {
                    // Endpoint-preserving reservoir: a long beam never becomes only its first metre.
                    int slot = Math.floorMod(seen * 1103515245L, seen - 2);
                    if (slot < WolfVfxPayload.MAX_POINTS - 2) samples.set(slot + 1, samples.getLast());
                    samples.set(samples.size() - 1, relative);
                }
            }
        }

        private WolfVfxPayload payload() {
            return new WolfVfxPayload(first.dimension(), first.x(), first.y(), first.z(), first.style(), count,
                    sx, sy, sz, first.speed(), first.species(), first.sourceId(), first.phase(), samples);
        }
    }
}
