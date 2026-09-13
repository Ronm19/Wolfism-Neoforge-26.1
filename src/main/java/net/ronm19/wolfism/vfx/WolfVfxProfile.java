package net.ronm19.wolfism.vfx;

import java.util.Map;
import java.util.LinkedHashMap;

/** Sixty named palettes and silhouettes. Natural wolves never gain a magic aura. */
public record WolfVfxProfile(int primary, int accent, Motif motif, WolfVfxStyle style, boolean magical) {
    public enum Motif { CLAW, CINDER, CRYSTAL, CURRENT, ROOT, HALO, CRESCENT, RUNE, WISP, PETAL, SPORE, FANG, SHARD, FESTIVE }

    private static final Map<String, WolfVfxProfile> PROFILES = new LinkedHashMap<>();
    private static final WolfVfxProfile DEFAULT = new WolfVfxProfile(0xFFD6CFBD, 0xFFF3E9CC, Motif.CLAW, WolfVfxStyle.ASH, false);

    static {
        add("timber_wolf", 0xB6A17D, 0xE1D5B7, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("arctic_wolf", 0xD1E1E7, 0xF4FAFA, Motif.CLAW, WolfVfxStyle.FROST, false);
        add("black_wolf", 0x818693, 0xC1C5D0, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("sand_wolf", 0xC9A566, 0xEFE0AD, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("dire_wolf", 0x847D75, 0xC8BBA6, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("fire_wolf", 0xE86F2A, 0xFFD692, Motif.CINDER, WolfVfxStyle.EMBER, true);
        add("frost_wolf", 0x89CADA, 0xE8FBFF, Motif.CRYSTAL, WolfVfxStyle.FROST, true);
        add("storm_wolf", 0xCCCED9, 0xFFF0B2, Motif.SHARD, WolfVfxStyle.STORM, true);
        add("water_wolf", 0x3F99BC, 0xABE6E9, Motif.CURRENT, WolfVfxStyle.WATER, true);
        add("earth_wolf", 0x89775D, 0xB2C28B, Motif.ROOT, WolfVfxStyle.NATURE, true);
        add("solar_wolf", 0xE8AC43, 0xFFF0B5, Motif.HALO, WolfVfxStyle.LIGHT, true);
        add("lunar_wolf", 0x9AA4D4, 0xE0EBFF, Motif.CRESCENT, WolfVfxStyle.ARCANE, true);
        add("spirit_wolf", 0x82BCAF, 0xDBF2D8, Motif.WISP, WolfVfxStyle.SOUL, true);
        add("shadow_wolf", 0x7A7095, 0xB5A5C3, Motif.WISP, WolfVfxStyle.SMOKE, true);
        add("golden_wolf", 0xC5A23D, 0xF5E4A3, Motif.HALO, WolfVfxStyle.LIGHT, true);
        add("cherry_wolf", 0xD795AA, 0xF6DDE2, Motif.PETAL, WolfVfxStyle.CHERRY, true);
        add("violet_wolf", 0x9D7BB9, 0xDAC6EE, Motif.PETAL, WolfVfxStyle.ARCANE, true);
        add("gem_wolf", 0x69B3AD, 0xD1EAE3, Motif.CRYSTAL, WolfVfxStyle.LIGHT, true);
        add("mushroom_wolf", 0xA67562, 0xDACAA7, Motif.SPORE, WolfVfxStyle.NATURE, true);
        add("bee_wolf", 0xC8AB4D, 0xEEE0A2, Motif.PETAL, WolfVfxStyle.NATURE, true);
        add("zombie_wolf", 0x70845B, 0xB2B887, Motif.FANG, WolfVfxStyle.NATURE, false);
        add("skeleton_wolf", 0xC0B9A5, 0xE9E2CF, Motif.SHARD, WolfVfxStyle.ASH, false);
        add("husk_wolf", 0xA78F60, 0xD7C89D, Motif.FANG, WolfVfxStyle.ASH, false);
        add("drowned_wolf", 0x568E88, 0xAFC5A1, Motif.CURRENT, WolfVfxStyle.WATER, true);
        add("phantom_wolf", 0x698792, 0xBBDACB, Motif.WISP, WolfVfxStyle.SOUL, true);
        add("end_wolf", 0xA77FB3, 0xDAC7E4, Motif.RUNE, WolfVfxStyle.ARCANE, true);
        add("sculk_wolf", 0x3D8B86, 0xA6D0B5, Motif.CURRENT, WolfVfxStyle.SOUL, true);
        add("infernal_wolf", 0xBD5B30, 0xECC270, Motif.CINDER, WolfVfxStyle.EMBER, true);
        add("omen_wolf", 0x8C719A, 0xD6BDA8, Motif.RUNE, WolfVfxStyle.ARCANE, true);
        add("astral_wolf", 0x8F9ABC, 0xDFD9BC, Motif.RUNE, WolfVfxStyle.LIGHT, true);
        add("angel_wolf", 0xD7C797, 0xFFF7DD, Motif.HALO, WolfVfxStyle.LIGHT, true);
        add("demon_wolf", 0xBA6251, 0xE6A86C, Motif.FANG, WolfVfxStyle.EMBER, true);
        add("grave_wolf", 0x829078, 0xB8C7AA, Motif.WISP, WolfVfxStyle.SOUL, true);
        add("rift_wolf", 0x947CAC, 0xD9BFD9, Motif.RUNE, WolfVfxStyle.ARCANE, true);
        add("void_wolf", 0x716887, 0xBCA9D1, Motif.CRESCENT, WolfVfxStyle.ARCANE, true);
        add("blood_wolf", 0xB45858, 0xE0AB92, Motif.FANG, WolfVfxStyle.EMBER, true);
        add("vampire_wolf", 0xA65360, 0xD6A4AE, Motif.FANG, WolfVfxStyle.ARCANE, true);
        add("spectral_wolf", 0x7FAEB3, 0xD3E1D5, Motif.WISP, WolfVfxStyle.SOUL, true);
        add("salva_wolf", 0x94AD88, 0xD6E0B2, Motif.ROOT, WolfVfxStyle.NATURE, true);
        add("wolf_king", 0xB89C5D, 0xEADFAD, Motif.HALO, WolfVfxStyle.LIGHT, true);
        add("primordial_wolf", 0x958A64, 0xD5CCA3, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("toxic_wolf", 0x8BA95D, 0xCEE39E, Motif.SPORE, WolfVfxStyle.NATURE, true);
        add("magma_wolf", 0xB45934, 0xF1B662, Motif.CINDER, WolfVfxStyle.EMBER, true);
        add("war_wolf", 0x9D8270, 0xD8BB92, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("illager_wolf", 0x82958A, 0xD2CCAF, Motif.CLAW, WolfVfxStyle.ASH, false);
        add("ancient_wolf", 0x7B907E, 0xC7D3AA, Motif.ROOT, WolfVfxStyle.NATURE, true);
        add("blade_wolf", 0xA5ACB3, 0xE8EADF, Motif.SHARD, WolfVfxStyle.LIGHT, false);
        add("raven_wolf", 0x727A89, 0xB1B8C4, Motif.CRESCENT, WolfVfxStyle.SMOKE, true);
        add("command_wolf", 0x84AA9B, 0xE0D9AA, Motif.RUNE, WolfVfxStyle.LIGHT, true);
        add("ash_wolf", 0x96918C, 0xD2BC9B, Motif.CINDER, WolfVfxStyle.ASH, true);
        add("wither_wolf", 0x777C77, 0xB5C0A0, Motif.WISP, WolfVfxStyle.SMOKE, true);
        add("blaze_wolf", 0xD4993A, 0xF5DB85, Motif.CINDER, WolfVfxStyle.EMBER, true);
        add("halloween_wolf", 0xCE873E, 0xC8B3DD, Motif.FANG, WolfVfxStyle.EMBER, true);
        add("creator_wolf", 0xB3B4C0, 0xF2E7C4, Motif.RUNE, WolfVfxStyle.LIGHT, true);
        add("christmas_wolf", 0x7FAD9B, 0xE4D7B1, Motif.CRYSTAL, WolfVfxStyle.FROST, true);
        add("saint_patricks_wolf", 0x77A36E, 0xE9D18C, Motif.PETAL, WolfVfxStyle.NATURE, true);
        add("new_years_wolf", 0xCBAC65, 0xC7B4D9, Motif.FESTIVE, WolfVfxStyle.CELEBRATION, true);
        add("valentines_wolf", 0xCA8599, 0xF1D6D0, Motif.PETAL, WolfVfxStyle.CHERRY, true);
        add("easter_wolf", 0xB6C08F, 0xDCC0CD, Motif.PETAL, WolfVfxStyle.NATURE, true);
        add("firework_wolf", 0xC98B76, 0xAEC9D6, Motif.FESTIVE, WolfVfxStyle.CELEBRATION, true);
    }

    private static void add(String species, int primary, int accent, Motif motif, WolfVfxStyle style, boolean magical) {
        PROFILES.put(species, new WolfVfxProfile(0xFF000000 | primary, 0xFF000000 | accent, motif, style, magical));
    }

    public static WolfVfxProfile of(String species) { return PROFILES.getOrDefault(species, DEFAULT); }
    public static Map<String, WolfVfxProfile> all() { return Map.copyOf(PROFILES); }
}
