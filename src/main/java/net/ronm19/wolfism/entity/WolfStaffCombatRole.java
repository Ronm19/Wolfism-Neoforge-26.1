package net.ronm19.wolfism.entity;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Coarse tactical role used only by Wolf Staff dispatch scoring.
 *
 * <p>This is deliberately centralized so the Staff does not require edits in
 * every species class. Unknown/future Wolfism wolves safely fall back to
 * MELEE until explicitly classified here.</p>
 */
public enum WolfStaffCombatRole {
    MELEE,
    RANGED,
    FLYING;

    private static final Set<String> FLYING_WOLVES = Set.of(
            "bee_wolf",
            "phantom_wolf",
            "angel_wolf",
            "raven_wolf");

    private static final Set<String> RANGED_WOLVES = Set.of(
            "storm_wolf",
            "earth_wolf",
            "skeleton_wolf",
            "drowned_wolf",
            "blood_wolf",
            "sculk_wolf",
            "infernal_wolf",
            "astral_wolf",
            "magma_wolf",
            "vampire_wolf",
            "toxic_wolf",
            "illager_wolf",
            "ash_wolf",
            "wither_wolf",
            "blaze_wolf");

    public static WolfStaffCombatRole classify(AbstractWolfismWolf wolf) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(wolf.getType()).getPath();

        if (FLYING_WOLVES.contains(path)) {
            return FLYING;
        }

        if (RANGED_WOLVES.contains(path)) {
            return RANGED;
        }

        return MELEE;
    }
}
