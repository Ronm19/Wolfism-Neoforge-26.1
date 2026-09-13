package net.ronm19.wolfism.vfx;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;

/** Bounded visual data only. Geometry is relative to the origin, never executable effect code. */
public record WolfVfxPayload(Identifier dimension, double x, double y, double z,
        WolfVfxStyle style, int count, float spreadX, float spreadY, float spreadZ, float speed,
        String species, int sourceId, WolfVfxPhase phase, List<Vec3> points)
        implements CustomPacketPayload {
    public static final int MAX_POINTS = 32;
    public static final Type<WolfVfxPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "ability_vfx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WolfVfxPayload> STREAM_CODEC =
            StreamCodec.ofMember(WolfVfxPayload::write, WolfVfxPayload::read);

    public WolfVfxPayload(Identifier dimension, double x, double y, double z, WolfVfxStyle style,
            int count, float spreadX, float spreadY, float spreadZ, float speed) {
        this(dimension, x, y, z, style, count, spreadX, spreadY, spreadZ, speed, "generic", 0,
                WolfVfxPhase.fromEmission(count, spreadX, spreadY, spreadZ), List.of());
    }

    public WolfVfxPayload {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000
                || style == null || dimension == null || phase == null || species == null
                || species.length() > 48 || points == null || points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("Invalid wolf VFX position, palette or geometry");
        }
        count = Math.clamp(count, 1, 32);
        spreadX = finiteClamp(spreadX, 16);
        spreadY = finiteClamp(spreadY, 16);
        spreadZ = finiteClamp(spreadZ, 16);
        speed = finiteClamp(speed, 2);
        points = List.copyOf(points);
        for (var point : points) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                    || Math.abs(point.x) > 32 || Math.abs(point.y) > 32 || Math.abs(point.z) > 32) {
                throw new IllegalArgumentException("Invalid relative wolf VFX sample");
            }
        }
    }

    public long sourceKey() {
        if (sourceId != 0) return sourceId;
        long cellX = (long) Math.floor(x / 4), cellZ = (long) Math.floor(z / 4);
        return ((long) species.hashCode() << 32) ^ (cellX * 73428767L) ^ cellZ * 912931L;
    }

    /** Authored beams retain their endpoints; small repeating trail ornaments yield first. */
    public boolean signature() {
        return phase != WolfVfxPhase.PASSIVE && (phase != WolfVfxPhase.PATH || points.size() > 4);
    }

    public int presentationPriority() {
        return phase == WolfVfxPhase.PATH && signature() ? WolfVfxPhase.CAST.priority() : phase.priority();
    }

    private static float finiteClamp(float value, float max) {
        return Float.isFinite(value) ? Math.clamp(value, 0, max) : 0;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeIdentifier(dimension);
        buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z);
        buffer.writeByte(style.ordinal()); buffer.writeVarInt(count);
        buffer.writeFloat(spreadX); buffer.writeFloat(spreadY); buffer.writeFloat(spreadZ); buffer.writeFloat(speed);
        buffer.writeUtf(species, 48); buffer.writeVarInt(sourceId); buffer.writeByte(phase.ordinal());
        buffer.writeVarInt(points.size());
        for (var point : points) {
            buffer.writeFloat((float) point.x); buffer.writeFloat((float) point.y); buffer.writeFloat((float) point.z);
        }
    }

    private static WolfVfxPayload read(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readIdentifier();
        double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
        int style = buffer.readUnsignedByte();
        if (style >= WolfVfxStyle.values().length) throw new IllegalArgumentException("Unknown wolf VFX style");
        int count = buffer.readVarInt();
        float sx = buffer.readFloat(), sy = buffer.readFloat(), sz = buffer.readFloat(), speed = buffer.readFloat();
        String species = buffer.readUtf(48);
        int source = buffer.readVarInt(), phase = buffer.readUnsignedByte(), size = buffer.readVarInt();
        if (phase >= WolfVfxPhase.values().length || size < 0 || size > MAX_POINTS) {
            throw new IllegalArgumentException("Unknown wolf VFX phase or excessive geometry");
        }
        var points = new java.util.ArrayList<Vec3>(size);
        for (int i = 0; i < size; i++) points.add(new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
        return new WolfVfxPayload(dimension, x, y, z, WolfVfxStyle.values()[style], count, sx, sy, sz, speed,
                species, source, WolfVfxPhase.values()[phase], points);
    }

    @Override
    public Type<WolfVfxPayload> type() { return TYPE; }
}
