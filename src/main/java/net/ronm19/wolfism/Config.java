package net.ronm19.wolfism;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Server-owned companion travel and habitat preferences. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue WAYSTONES_COMPANIONS = BUILDER
            .comment("Nearby standing companions travel with their owner through Waystones.",
                    "Sitting, disabled-AI and recovering wolves stay behind. Leashes use Waystones rules.")
            .define("waystonesCompanions", true);
    public static final ModConfigSpec.IntValue WAYSTONES_COMPANION_RANGE = BUILDER
            .comment("Maximum distance from the owner when preparing a trip. Only loaded companions are considered.")
            .defineInRange("waystonesCompanionRange", 24, 4, 48);
    public static final ModConfigSpec.BooleanValue HABITAT_REPLENISHMENT = BUILDER
            .comment("Allow bounded natural wolf encounters in already-loaded habitats.",
                    "Preserves biome weights, species spawn rules, seasonal systems and companion ownership.")
            .define("habitatReplenishment", true);
    public static final ModConfigSpec.IntValue HABITAT_WILD_CAP = BUILDER
            .comment("Maximum nearby wild wolves for supplemental habitat encounters. Tamed companions do not count.")
            .defineInRange("habitatWildCap", 12, 1, 32);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private Config() {}
}
