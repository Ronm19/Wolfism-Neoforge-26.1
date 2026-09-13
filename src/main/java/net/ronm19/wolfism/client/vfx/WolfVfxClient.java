package net.ronm19.wolfism.client.vfx;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.mojang.blaze3d.platform.DestFactor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.vfx.WolfVfx;
import net.ronm19.wolfism.vfx.WolfVfxBudget;
import net.ronm19.wolfism.vfx.WolfVfxPayload;
import net.ronm19.wolfism.vfx.WolfVfxPhase;
import net.ronm19.wolfism.vfx.WolfVfxProfile;
import net.ronm19.wolfism.vfx.WolfVfxProfile.Motif;
import net.ronm19.wolfism.vfx.WolfVfxStyle;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Layered Photon presentation with explicit silhouettes and bounded visibility. Client-only. */
@EventBusSubscriber(modid = Wolfism.MOD_ID, value = Dist.CLIENT)
public final class WolfVfxClient {
    static final int MAX_ACTIVE_EFFECTS = 80;
    static final int MAX_ACTIVE_PARTICLES = 2400;
    private static final int DECORATION_SLOTS = 24;
    static final int EMERGENCY_SLOTS = 4;
    static final int EMERGENCY_PARTICLES = 8;
    static final int EMERGENCY_MAX_WAIT = 6;
    private static final java.util.Map<WolfVfxPayload, Long> EMERGENCY_ARRIVAL = new java.util.IdentityHashMap<>();
    private static long clientTick;
    private static final List<LiveEffect> ACTIVE = new ArrayList<>();
    private static final List<WolfVfxPayload> PENDING = new ArrayList<>();
    private static ClientLevel previousLevel;
    private static int effectsThisTick;

    private record LiveEffect(FXRuntime runtime, int cost, long source, boolean signature, int priority, long born, boolean emergency) {}

    private WolfVfxClient() {}
    private static int activeEmergencyEffects() { return (int) ACTIVE.stream().filter(LiveEffect::emergency).count(); }
    private static int reservedParticles() { return ACTIVE.stream().mapToInt(LiveEffect::cost).sum(); }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(WolfVfxPayload.TYPE, (payload, context) -> queue(payload));
    }

    static void queue(WolfVfxPayload payload) {
        if (payload.phase() == WolfVfxPhase.EMERGENCY) EMERGENCY_ARRIVAL.put(payload, clientTick);
        if (PENDING.size() < 256) PENDING.add(payload);
        else {
            // A late impact must not be discarded merely because quiet detail filled
            // the bounded inbox first. Never grow the inbox beyond its existing cap.
            int replace = -1;
            if (payload.signature()) for (int i = 0; i < PENDING.size(); i++) {
                if (!PENDING.get(i).signature() || payload.phase() == WolfVfxPhase.EMERGENCY
                        && PENDING.get(i).phase() != WolfVfxPhase.EMERGENCY) { replace = i; break; }
            }
            if (replace >= 0) { reject(PENDING.set(replace, payload)); }
            else reject(payload);
        }
    }

    private static void reject(WolfVfxPayload payload) {
        EMERGENCY_ARRIVAL.remove(payload);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        clientTick++;
        var level = Minecraft.getInstance().level;
        if (level != previousLevel) {
            for (var effect : ACTIVE) effect.runtime().destroy(true);
            ACTIVE.clear();
            PENDING.removeIf(p -> level == null || !p.dimension().equals(level.dimension().identifier()));
            EMERGENCY_ARRIVAL.keySet().removeIf(p -> !PENDING.contains(p));
            previousLevel = level;
        } else ACTIVE.removeIf(e -> e.runtime().isFinished() || !e.runtime().isValid());
        effectsThisTick = 0;
        if (level != null && !PENDING.isEmpty()) {
            // A Minimal client can show one rescue per tick while ordinary cues keep
            // two slots. Hold only emergency packets briefly; never replay stale saves.
            PENDING.removeIf(p -> {
                if (p.phase() != WolfVfxPhase.EMERGENCY || clientTick - EMERGENCY_ARRIVAL.getOrDefault(p, clientTick) <= EMERGENCY_MAX_WAIT) return false;
                reject(p); return true;
            });
            int limit = tickLimit(quality());
            var selected = WolfVfxBudget.select(PENDING, limit, level.getGameTime(), EMERGENCY_SLOTS - activeEmergencyEffects());
            var remaining = new ArrayList<>(selected);
            var deferred = new ArrayList<WolfVfxPayload>();
            for (var payload : PENDING) {
                int index = -1;
                for (int i = 0; i < remaining.size(); i++) if (remaining.get(i) == payload) { index = i; break; }
                if (index >= 0) remaining.remove(index);
                else if (payload.phase() == WolfVfxPhase.EMERGENCY) deferred.add(payload);
                else reject(payload);
            }
            PENDING.clear(); PENDING.addAll(deferred);
            // Rescue confirmations arrive before ordinary signatures; source order
            // within both shares stays fair. Normal cast tells are not sorted last.
            selected.sort(java.util.Comparator.comparingInt((WolfVfxPayload p) -> p.phase() == WolfVfxPhase.EMERGENCY ? 2 : p.signature() ? 1 : 0).reversed());
            for (var payload : selected) play(payload);
        }
    }

    private static int quality() {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 0; case DECREASED -> 1; case MINIMAL -> 2;
        };
    }

    static int tickLimit(int quality) { return quality == 2 ? 3 : quality == 1 ? 8 : 16; }

    static FXRuntime play(WolfVfxPayload payload) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || minecraft.player == null
                || !level.dimension().identifier().equals(payload.dimension())
                || minecraft.player.distanceToSqr(payload.x(), payload.y(), payload.z()) > WolfVfx.VIEW_RANGE * WolfVfx.VIEW_RANGE) {
            reject(payload); return null;
        }
        int quality = quality();
        if (effectsThisTick >= tickLimit(quality)) { reject(payload); return null; }
        boolean signature = payload.signature();
        boolean emergency = payload.phase() == WolfVfxPhase.EMERGENCY;
        if (emergency && activeEmergencyEffects() >= EMERGENCY_SLOTS) { reject(payload); return null; }
        int ordinaryEffects = ACTIVE.size() - activeEmergencyEffects();
        if (!emergency && ordinaryEffects >= MAX_ACTIVE_EFFECTS - EMERGENCY_SLOTS && (!signature || !retireDecoration())) {
            reject(payload); return null;
        }
        int pressure = ACTIVE.size() >= 60 || reservedParticles() >= 1800 ? 2
                : ACTIVE.size() >= 40 || reservedParticles() >= 1200 ? 1 : 0;
        long now = level.getGameTime();
        if (!signature) {
            long decoration = ACTIVE.stream().filter(e -> !e.signature()).count();
            if (decoration >= DECORATION_SLOTS) { reject(payload); return null; }
            // Avoid stacking the same small passive mote while its previous cue is
            // still alive. Moving trail segments remain separate to retain direction.
            if (payload.phase() == WolfVfxPhase.PASSIVE && ACTIVE.stream().anyMatch(e -> !e.signature()
                    && e.source() == payload.sourceKey() && now - e.born() < 4)) {
                reject(payload); return null;
            }
        }
        if (ACTIVE.size() >= MAX_ACTIVE_EFFECTS && (!signature || !retireDecoration())) {
            reject(payload); return null;
        }
        var fx = new FX();
        var profile = WolfVfxProfile.of(payload.species());
        boolean generic = payload.species().equals("generic");
        boolean glow = generic ? !payload.style().smoke() : profile.magical() && !payload.style().smoke();
        int primary = generic ? payload.style().color() : blend(profile.primary(), payload.style().color(), .15f);
        int accent = generic ? blend(primary, 0xFFF6EAD8, .45f) : profile.accent();
        Motif motif = generic ? styleMotif(payload.style()) : profile.motif();
        int detail = Math.max(3, Math.min(payload.phase() == WolfVfxPhase.PASSIVE ? 3 : 18, payload.count()) / (quality + 1));
        boolean sampled = !payload.points().isEmpty();
        var geometry = sampled ? payload.points() : geometry(motif, payload, detail);
        int lifetime = emergency ? 8 : lifetime(payload, pressure);
        float size = emergency ? .14f : payload.phase() == WolfVfxPhase.PASSIVE ? .038f : sampled ? .045f : .075f;
        boolean mist = !emergency && payload.style().smoke() && !sampled && payload.phase() != WolfVfxPhase.IMPACT;
        String texture = emergency ? "white" : mist ? "smoke" : motif == Motif.CLAW || motif == Motif.FANG || motif == Motif.SHARD ? "laser" : glow ? "circle" : "white";
        int coreCount = Math.max(1, Math.min(32, geometry.size()) / (quality + 1));
        // Keep enough authored samples for a recognisable outline, including both
        // beam endpoints; shed delayed flecks and redundant glints before the contour.
        if (pressure > 0) coreCount = Math.min(coreCount, signature ? (pressure == 2 ? 8 : 12) : (pressure == 2 ? 2 : 4));
        if (emergency) coreCount = EMERGENCY_PARTICLES;
        var core = emitter("silhouette", texture, emergency ? blend(primary, accent, .7f) : primary, coreCount, lifetime, size, !emergency && glow, emergency ? .95f : mist ? .26f : glow ? .48f : .7f);
        core.config.shape.setShape(new WolfGeometryShape(geometry, false));
        if (texture.equals("laser")) {
            core.config.setStartSize(new NumberFunction3(size * .5f, size * 3.8f, size));
            core.config.setStartRotation(new NumberFunction3(NumberFunction.constant(0), NumberFunction.constant(0), new RandomConstant(-35, 35)));
        }
        if (mist) core.config.setStartSize(new NumberFunction3(.28f, .28f, .28f));
        fx.getFxData().objects().add(core);

        if (!emergency && pressure == 0 && quality < 2 && payload.phase() != WolfVfxPhase.PASSIVE && (!sampled || geometry.size() > 2)) {
            int glints = Math.max(2, Math.min(8, geometry.size() / 2));
            var highlights = emitter("accent", "circle", accent, glints, Math.max(8, lifetime - 4), size * .48f, glow, .36f);
            highlights.config.setStartDelay(NumberFunction.constant(2));
            highlights.config.shape.setShape(new WolfGeometryShape(geometry, false));
            fx.getFxData().objects().add(highlights);
            if (!sampled) {
                var dust = emitter("settling_flecks", "white", primary, quality == 0 ? 6 : 3, lifetime, .024f, false, .55f);
                dust.config.setStartDelay(NumberFunction.constant(3));
                dust.config.setStartSpeed(NumberFunction.constant(.5f));
                dust.config.shape.setShape(new WolfGeometryShape(geometry, true));
                fx.getFxData().objects().add(dust);
            }
        }
        // Area boundaries convey range, so retain this one-particle edge even when
        // an ultimate loses secondary ornament under pressure.
        if (!emergency && !sampled && glow && quality < 2 && (payload.phase() == WolfVfxPhase.ULTIMATE
                || payload.phase() == WolfVfxPhase.AREA || pressure == 0 && motif == Motif.HALO && payload.phase() == WolfVfxPhase.CAST)) {
            float radius = Math.clamp(Math.max(payload.spreadX(), payload.spreadZ()), .45f, 3.5f);
            var ring = emitter("area_edge", "ring", accent, 1, Math.min(lifetime, 20), radius, true, .3f);
            ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
            ring.config.shape.setShape(new WolfGeometryShape(List.of(new Vec3(0, -.12, 0)), false));
            var expansion = curve(.75f, 1.65f);
            ring.config.sizeOverLifetime.setSize(new NumberFunction3(expansion, expansion, expansion));
            fx.getFxData().objects().add(ring);
        }
        int cost = fx.getFxData().objects().stream().filter(ParticleEmitter.class::isInstance)
                .map(ParticleEmitter.class::cast).mapToInt(p -> p.config.getMaxParticles()).sum();
        int activeCost = reservedParticles();
        int budgetCost = emergency ? activeCost : ACTIVE.stream().filter(e -> !e.emergency()).mapToInt(LiveEffect::cost).sum();
        int budgetLimit = emergency ? MAX_ACTIVE_PARTICLES : MAX_ACTIVE_PARTICLES - EMERGENCY_SLOTS * EMERGENCY_PARTICLES;
        while (signature && budgetCost + cost > budgetLimit && retireDecoration()) {
            activeCost = reservedParticles();
            budgetCost = emergency ? activeCost : ACTIVE.stream().filter(e -> !e.emergency()).mapToInt(LiveEffect::cost).sum();
        }
        if (budgetCost + cost > budgetLimit || activeCost + cost > MAX_ACTIVE_PARTICLES) { reject(payload); return null; }
        effectsThisTick++;
        var runtime = fx.createInternalRuntime();
        runtime.getRoot().updatePos(new Vector3f((float) payload.x(), (float) payload.y(), (float) payload.z()));
        runtime.emit(() -> level);
        ACTIVE.add(new LiveEffect(runtime, cost, payload.sourceKey(), signature, payload.presentationPriority(), now, emergency));
        if (emergency) EMERGENCY_ARRIVAL.remove(payload);
        return runtime;
    }

    private static int lifetime(WolfVfxPayload payload, int pressure) {
        if (pressure == 0) return payload.phase().lifetime();
        if (!payload.signature()) return pressure == 2 ? 4 : 6;
        return Math.min(payload.phase().lifetime(), switch (payload.phase()) {
            case ULTIMATE -> pressure == 2 ? 7 : 16;
            case AREA -> pressure == 2 ? 5 : 14;
            case IMPACT -> pressure == 2 ? 4 : 10;
            default -> pressure == 2 ? 5 : 10;
        });
    }

    private static boolean retireDecoration() {
        int victim = -1;
        for (int i = 0; i < ACTIVE.size(); i++) {
            var effect = ACTIVE.get(i);
            if (effect.signature()) continue;
            if (victim < 0 || effect.priority() < ACTIVE.get(victim).priority()
                    || effect.priority() == ACTIVE.get(victim).priority() && effect.born() < ACTIVE.get(victim).born()) victim = i;
        }
        if (victim < 0) return false;
        ACTIVE.remove(victim).runtime().destroy(true);
        return true;
    }

    private static Motif styleMotif(WolfVfxStyle style) {
        return switch (style) {
            case EMBER, ASH -> Motif.CINDER; case FROST -> Motif.CRYSTAL; case STORM -> Motif.SHARD;
            case WATER -> Motif.CURRENT; case SOUL, SMOKE -> Motif.WISP; case ARCANE -> Motif.RUNE;
            case LIGHT -> Motif.HALO; case NATURE -> Motif.ROOT; case CHERRY -> Motif.PETAL; case CELEBRATION -> Motif.FESTIVE;
        };
    }

    private static List<Vec3> geometry(Motif motif, WolfVfxPayload payload, int count) {
        var result = new ArrayList<Vec3>();
        if (payload.phase() == WolfVfxPhase.EMERGENCY) {
            // An open bracket sits above the saved ally, never buried in the body.
            // Face each observer using geometry only; eight small points and the
            // species' existing accent retain contrast without extra glow/layers.
            var viewer = Minecraft.getInstance().player;
            var toViewer = viewer == null ? new Vec3(0, 0, 1) : viewer.position().subtract(payload.x(), payload.y(), payload.z());
            var right = new Vec3(-toViewer.z, 0, toViewer.x);
            right = right.lengthSqr() < .0001 ? new Vec3(1, 0, 0) : right.normalize();
            double r = Math.clamp(payload.spreadX(), .55, 1.8);
            double[] x = {-1, -1, -.85, -.42, .42, .85, 1, 1};
            double[] y = {.15, .4, .55, .55, .55, .55, .4, .15};
            for (int i = 0; i < EMERGENCY_PARTICLES; i++) result.add(right.scale(x[i] * r).add(0, y[i], 0));
            return result;
        }
        double radius = payload.phase() == WolfVfxPhase.PASSIVE ? .24
                : Math.clamp(Math.max(payload.spreadX(), payload.spreadZ()) + .32, .38, 3.5);
        if (payload.phase() == WolfVfxPhase.ULTIMATE) radius = Math.max(1.2, radius);
        if (payload.phase() == WolfVfxPhase.IMPACT) radius = .45;
        int samples = Math.max(payload.phase() == WolfVfxPhase.PASSIVE ? 3 : 10, count);
        for (int i = 0; i < samples; i++) {
            double t = samples <= 1 ? 0 : i / (double) (samples - 1), angle = t * Math.PI * 2;
            double x, y, z;
            switch (motif) {
                case CLAW, FANG -> {
                    int slash = i % 3; double along = (i / 3d) / (samples / 3d) - .5;
                    x = (slash - 1) * .24 + along * .4; y = along * .9; z = Math.sin(along * 2.3) * .12;
                }
                case CRYSTAL -> {
                    double arm = i % 3 * Math.PI * 2 / 3, reach = .2 + t;
                    x = Math.cos(arm) * reach * radius; z = Math.sin(arm) * reach * radius; y = Math.sin(t * Math.PI) * radius * .65;
                }
                case SHARD -> {
                    x = (t - .5) * radius * 2; y = (i % 2 == 0 ? .22 : -.14) * radius; z = Math.sin(t * 6) * .16;
                }
                case CINDER -> {
                    double arm = (i % 4) * Math.PI / 2;
                    x = Math.cos(arm + t * 1.4) * radius * (1 - t * .6); z = Math.sin(arm + t * 1.4) * radius * (1 - t * .6); y = t * radius * 1.2 - .2;
                }
                case CURRENT -> {
                    x = (t - .5) * radius * 2; y = Math.sin(t * Math.PI * 3) * radius * .24; z = Math.cos(t * Math.PI * 3) * radius * .35;
                }
                case ROOT -> {
                    double arm = i % 4 * Math.PI / 2 + Math.sin(t * 8) * .2;
                    x = Math.cos(arm) * radius * t; z = Math.sin(arm) * radius * t; y = -.24 + Math.sin(t * Math.PI) * .2;
                }
                case CRESCENT -> {
                    double crescent = -.7 * Math.PI + t * 1.4 * Math.PI;
                    x = Math.cos(crescent) * radius; y = Math.sin(crescent) * radius * .6; z = Math.sin(crescent) * radius * .3;
                }
                case WISP -> {
                    x = Math.cos(angle) * radius * (1 - t * .5); z = Math.sin(angle) * radius * (1 - t * .5); y = t * radius * 1.2 - .25;
                }
                case PETAL -> {
                    double petalRadius = radius * (.65 + .35 * Math.cos(angle * 5));
                    x = Math.cos(angle) * petalRadius; z = Math.sin(angle) * petalRadius; y = Math.sin(angle * 2) * .15;
                }
                case RUNE -> {
                    double edge = t * 6, side = Math.min(5, Math.floor(edge)), u = edge - side;
                    double a = side * Math.PI / 3, b = (side + 1) * Math.PI / 3;
                    x = (Math.cos(a) * (1 - u) + Math.cos(b) * u) * radius;
                    z = (Math.sin(a) * (1 - u) + Math.sin(b) * u) * radius; y = -.12;
                }
                case SPORE -> {
                    x = Math.cos(i * 2.39996) * radius * Math.sqrt(t); z = Math.sin(i * 2.39996) * radius * Math.sqrt(t); y = .1 + t * .55;
                }
                case FESTIVE -> {
                    double ray = (i % 6) * Math.PI / 3, reach = .4 + t * radius;
                    x = Math.cos(ray) * reach; y = Math.sin(ray) * reach * .8; z = Math.sin(t * 7) * .2;
                }
                default -> { x = Math.cos(angle) * radius; z = Math.sin(angle) * radius; y = -.12; }
            }
            result.add(new Vec3(x, y, z));
        }
        return result;
    }

    private static ParticleEmitter emitter(String name, String texture, int color, int count,
            int lifetime, float size, boolean glow, float opacity) {
        var emitter = new ParticleEmitter();
        emitter.setName("wolfism_" + name);
        var config = emitter.config;
        config.setDuration(1);
        config.setLooping(false);
        config.setMaxParticles(count);
        config.setSimulationSpace(ParticleConfig.Space.World);
        config.setStartLifetime(NumberFunction.constant(lifetime));
        config.setStartSpeed(NumberFunction.constant(0));
        config.setStartSize(new NumberFunction3(size, size, size));
        config.setStartColor(NumberFunction.color(color));
        config.emission.setEmissionRate(NumberFunction.constant(0));
        var burst = new EmissionSetting.Burst();
        burst.setCount(NumberFunction.constant(count));
        config.emission.getBursts().add(burst);
        config.colorOverLifetime.setEnable(true);
        config.colorOverLifetime.setColor(fade(opacity));
        config.sizeOverLifetime.setEnable(true);
        var shrink = curve(1, .15f);
        config.sizeOverLifetime.setSize(new NumberFunction3(shrink, shrink, shrink));
        config.physics.setEnable(false);
        var material = new TextureMaterial(Identifier.fromNamespaceAndPath("photon", "textures/particle/" + texture + ".png"));
        material.setDiscardThreshold(.01f);
        var setting = new MaterialSetting(material);
        setting.setCull(false); setting.setDepthTest(true); setting.setDepthMask(false);
        if (glow) setting.getBlendMode().setDstColorFactor(DestFactor.ONE);
        config.renderer.getMaterials().clear(); config.renderer.getMaterials().add(setting);
        return emitter;
    }

    private static Gradient fade(float opacity) {
        var gradient = new Gradient(0xFFFFFFFF);
        var alpha = gradient.getGradientColor().getAP();
        alpha.clear();
        alpha.add(new Vector2f(0, 0)); alpha.add(new Vector2f(.08f, opacity));
        alpha.add(new Vector2f(.42f, opacity * .7f)); alpha.add(new Vector2f(1, 0));
        return gradient;
    }

    private static int blend(int a, int b, float factor) {
        int r = Math.round(((a >> 16) & 255) * (1 - factor) + ((b >> 16) & 255) * factor);
        int g = Math.round(((a >> 8) & 255) * (1 - factor) + ((b >> 8) & 255) * factor);
        int blue = Math.round((a & 255) * (1 - factor) + (b & 255) * factor);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    private static Curve curve(float start, float end) {
        float max = Math.max(start, end);
        return new Curve(0, max, 0, max, "lifetime", "scale",
                new ECBCurves(0, start / max, .25f, start / max, .75f, end / max, 1, end / max));
    }
}
