package net.ronm19.wolfism.vfx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

/** Shared visual vocabulary. This class deliberately has no rendering-library references. */
public enum WolfVfxStyle {
    EMBER(0xFFFF8C32, false), FROST(0xFFB5F5FF, false), STORM(0xFFFFFFA5, false),
    WATER(0xFF56CFFF, false), SOUL(0xFF55FFE5, false), ARCANE(0xFFBD82FF, false),
    LIGHT(0xFFFFEDB1, false), NATURE(0xFF92EA69, false), CHERRY(0xFFFFADD6, false),
    SMOKE(0xFFB4B5BC, true), ASH(0xFFA39789, true), CELEBRATION(0xFFFFC64D, false);

    private final int color;
    private final boolean smoke;

    WolfVfxStyle(int color, boolean smoke) {
        this.color = color;
        this.smoke = smoke;
    }

    public int color() { return color; }
    public boolean smoke() { return smoke; }

    @Nullable
    public static WolfVfxStyle fromParticle(ParticleOptions particle) {
        // ColorParticleOption (including colored flashes), block/item fragments and
        // dust carry data the palette adapter must never silently discard.
        if (!(particle instanceof SimpleParticleType)) return null;
        var id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
        if (!id.getNamespace().equals("minecraft")) return null;
        return switch (id.getPath()) {
            case "flame", "lava" -> EMBER;
            case "snowflake" -> FROST;
            case "electric_spark" -> STORM;
            case "bubble", "splash" -> WATER;
            case "soul", "soul_fire_flame", "sculk_soul" -> SOUL;
            case "portal", "reverse_portal", "witch", "enchant", "enchanted_hit" -> ARCANE;
            case "end_rod", "flash", "wax_on", "totem_of_undying" -> LIGHT;
            case "composter", "mycelium" -> NATURE;
            case "cherry_leaves" -> CHERRY;
            case "smoke", "large_smoke", "cloud", "poof", "explosion" -> SMOKE;
            case "ash" -> ASH;
            case "firework" -> CELEBRATION;
            // Keep hearts, critical-hit icons, block/item fragments, dust colors and other
            // structured particles intact: their data conveys specific gameplay information.
            default -> null;
        };
    }
}
