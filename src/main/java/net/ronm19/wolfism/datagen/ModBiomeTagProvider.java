package net.ronm19.wolfism.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.world.level.biome.Biomes;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.tag.ModBiomeTags;

public final class ModBiomeTagProvider extends BiomeTagsProvider {
    public ModBiomeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Wolfism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(ModBiomeTags.TIMBER_WOLF_SPAWNS).add(
                Biomes.FOREST,
                Biomes.FLOWER_FOREST,
                Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST,
                Biomes.TAIGA,
                Biomes.SNOWY_TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.GROVE
        );

        tag(ModBiomeTags.ARCTIC_WOLF_SPAWNS).add(
                Biomes.SNOWY_PLAINS,
                Biomes.SNOWY_TAIGA,
                Biomes.GROVE,
                Biomes.ICE_SPIKES,
                Biomes.FROZEN_RIVER
        );


        tag(ModBiomeTags.BLACK_WOLF_SPAWNS).add(
                Biomes.FOREST,
                Biomes.DARK_FOREST,
                Biomes.TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA
        );

        tag(ModBiomeTags.SAND_WOLF_SPAWNS).add(
                Biomes.DESERT
        );

        tag(ModBiomeTags.DIRE_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.MEADOW,
                Biomes.TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.GROVE
        );


        tag(ModBiomeTags.FIRE_WOLF_SPAWNS).add(
                Biomes.SAVANNA,
                Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_SAVANNA,
                Biomes.BADLANDS,
                Biomes.WOODED_BADLANDS,
                Biomes.ERODED_BADLANDS
        );

        tag(ModBiomeTags.FIRE_WOLF_HOT_ENVIRONMENT).add(
                Biomes.SAVANNA,
                Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_SAVANNA,
                Biomes.BADLANDS,
                Biomes.WOODED_BADLANDS,
                Biomes.ERODED_BADLANDS
        );

        tag(ModBiomeTags.SAND_WOLF_DESERT_ENVIRONMENT).add(
                Biomes.DESERT
        );

        // Environmental awareness is intentionally broader than natural spawning.
        // A tamed Arctic Wolf taken to peaks, frozen coasts or frozen oceans still
        // recognizes that it is operating in its native climate.
        tag(ModBiomeTags.ARCTIC_WOLF_COLD_ENVIRONMENT).add(
                Biomes.SNOWY_PLAINS,
                Biomes.SNOWY_TAIGA,
                Biomes.GROVE,
                Biomes.ICE_SPIKES,
                Biomes.FROZEN_RIVER,
                Biomes.SNOWY_BEACH,
                Biomes.FROZEN_OCEAN,
                Biomes.DEEP_FROZEN_OCEAN,
                Biomes.FROZEN_PEAKS,
                Biomes.JAGGED_PEAKS
        );
        tag(ModBiomeTags.FROST_WOLF_SPAWNS).add(
                Biomes.SNOWY_PLAINS,
                Biomes.ICE_SPIKES,
                Biomes.SNOWY_TAIGA,
                Biomes.GROVE
        );

        tag(ModBiomeTags.FROST_WOLF_COLD_ENVIRONMENT).add(
                Biomes.SNOWY_PLAINS,
                Biomes.ICE_SPIKES,
                Biomes.SNOWY_TAIGA,
                Biomes.GROVE,
                Biomes.FROZEN_RIVER,
                Biomes.SNOWY_BEACH,
                Biomes.FROZEN_OCEAN,
                Biomes.DEEP_FROZEN_OCEAN,
                Biomes.FROZEN_PEAKS,
                Biomes.JAGGED_PEAKS
        );

        tag(ModBiomeTags.STORM_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.MEADOW,
                Biomes.SAVANNA
        );

        tag(ModBiomeTags.WATER_WOLF_SPAWNS).add(
                Biomes.RIVER,
                Biomes.FROZEN_RIVER,
                Biomes.BEACH,
                Biomes.STONY_SHORE,
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP,
                Biomes.OCEAN,
                Biomes.DEEP_OCEAN,
                Biomes.LUKEWARM_OCEAN,
                Biomes.DEEP_LUKEWARM_OCEAN,
                Biomes.WARM_OCEAN,
                Biomes.COLD_OCEAN,
                Biomes.DEEP_COLD_OCEAN,
                Biomes.FROZEN_OCEAN,
                Biomes.DEEP_FROZEN_OCEAN
        );

        tag(ModBiomeTags.EARTH_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.FOREST,
                Biomes.FLOWER_FOREST,
                Biomes.MEADOW,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS,
                Biomes.DRIPSTONE_CAVES
        );

        tag(ModBiomeTags.SOLAR_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.SAVANNA,
                Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_SAVANNA,
                Biomes.BADLANDS,
                Biomes.WOODED_BADLANDS,
                Biomes.ERODED_BADLANDS
        );

        tag(ModBiomeTags.LUNAR_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.FOREST,
                Biomes.FLOWER_FOREST,
                Biomes.TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS
        );

        tag(ModBiomeTags.SPIRIT_WOLF_SPAWNS).add(
                Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.GROVE);

        tag(ModBiomeTags.SHADOW_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP);

        tag(ModBiomeTags.GOLDEN_WOLF_SPAWNS).add(
                Biomes.DESERT,
                Biomes.BADLANDS,
                Biomes.WOODED_BADLANDS,
                Biomes.ERODED_BADLANDS);

        tag(ModBiomeTags.CHERRY_WOLF_SPAWNS).add(
                Biomes.CHERRY_GROVE,
                Biomes.FLOWER_FOREST);

        tag(ModBiomeTags.VIOLET_WOLF_SPAWNS).add(
                Biomes.MEADOW,
                Biomes.GROVE,
                Biomes.FLOWER_FOREST);

        tag(ModBiomeTags.GEM_WOLF_SPAWNS).add(
                Biomes.STONY_PEAKS,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_GRAVELLY_HILLS);

        tag(ModBiomeTags.MUSHROOM_WOLF_SPAWNS).add(
                Biomes.MUSHROOM_FIELDS,
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP
        );

        tag(ModBiomeTags.BEE_WOLF_SPAWNS).add(
                Biomes.FLOWER_FOREST,
                Biomes.MEADOW,
                Biomes.SUNFLOWER_PLAINS,
                Biomes.CHERRY_GROVE
        );

        tag(ModBiomeTags.ZOMBIE_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP
        );


        tag(ModBiomeTags.SKELETON_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.TAIGA,
                Biomes.SNOWY_TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.STONY_SHORE
        );


        tag(ModBiomeTags.HUSK_WOLF_SPAWNS).add(
                Biomes.DESERT,
                Biomes.BADLANDS,
                Biomes.WOODED_BADLANDS,
                Biomes.ERODED_BADLANDS
        );


        tag(ModBiomeTags.DROWNED_WOLF_SPAWNS).add(
                Biomes.OCEAN,
                Biomes.DEEP_OCEAN,
                Biomes.COLD_OCEAN,
                Biomes.DEEP_COLD_OCEAN,
                Biomes.FROZEN_OCEAN,
                Biomes.DEEP_FROZEN_OCEAN,
                Biomes.LUKEWARM_OCEAN,
                Biomes.DEEP_LUKEWARM_OCEAN,
                Biomes.WARM_OCEAN,
                Biomes.RIVER,
                Biomes.FROZEN_RIVER,
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP
        );

        tag(ModBiomeTags.PHANTOM_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.WINDSWEPT_GRAVELLY_HILLS,
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS
        );

        tag(ModBiomeTags.BLOOD_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.WOODED_BADLANDS,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.BADLANDS,
                Biomes.MANGROVE_SWAMP,
                Biomes.ERODED_BADLANDS
        );

        tag(ModBiomeTags.END_WOLF_SPAWNS).add(
                Biomes.THE_END,
                Biomes.END_HIGHLANDS,
                Biomes.END_MIDLANDS,
                Biomes.END_BARRENS,
                Biomes.SMALL_END_ISLANDS
        );

        tag(ModBiomeTags.INFERNAL_WOLF_SPAWNS).add(
                Biomes.NETHER_WASTES,
                Biomes.BASALT_DELTAS
        );

        tag(ModBiomeTags.OMEN_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA
        );

        tag(ModBiomeTags.ASTRAL_WOLF_SPAWNS).add(
                Biomes.MEADOW,
                Biomes.GROVE
        );

        tag(ModBiomeTags.ANGEL_WOLF_SPAWNS).add(
                Biomes.MEADOW,
                Biomes.CHERRY_GROVE
        );

        tag(ModBiomeTags.ANGEL_WOLF_HIGHLAND_SPAWNS).add(
                Biomes.GROVE,
                Biomes.SNOWY_SLOPES
        );

        tag(ModBiomeTags.DEMON_WOLF_SPAWNS).add(
                Biomes.NETHER_WASTES,
                Biomes.CRIMSON_FOREST,
                Biomes.BASALT_DELTAS
        );

        tag(ModBiomeTags.GRAVE_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.PALE_GARDEN
        );

        tag(ModBiomeTags.RIFT_WOLF_SPAWNS).add(
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS
        );

        tag(ModBiomeTags.VOID_WOLF_SPAWNS).add(
                Biomes.END_HIGHLANDS,
                Biomes.END_MIDLANDS
        );

        tag(ModBiomeTags.SALVA_WOLF_SPAWNS).add(
                Biomes.MEADOW,
                Biomes.GROVE
        );

        tag(ModBiomeTags.MAGMA_WOLF_SPAWNS).add(
                Biomes.BASALT_DELTAS,
                Biomes.NETHER_WASTES
        );

        tag(ModBiomeTags.VAMPIRE_WOLF_SPAWNS).add(
                Biomes.DARK_FOREST,
                Biomes.PALE_GARDEN
        );

        tag(ModBiomeTags.SPECTRAL_WOLF_SPAWNS).add(
                Biomes.PALE_GARDEN,
                Biomes.DARK_FOREST,
                Biomes.SOUL_SAND_VALLEY
        );

        tag(ModBiomeTags.TOXIC_WOLF_SPAWNS).add(
                Biomes.SWAMP,
                Biomes.MANGROVE_SWAMP,
                Biomes.DARK_FOREST
        );

        tag(ModBiomeTags.WAR_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.MEADOW,
                Biomes.WINDSWEPT_HILLS
        );

        tag(ModBiomeTags.ILLAGER_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.DESERT,
                Biomes.SAVANNA,
                Biomes.TAIGA,
                Biomes.SNOWY_PLAINS,
                Biomes.MEADOW,
                Biomes.GROVE
        );

        tag(ModBiomeTags.ANCIENT_WOLF_SPAWNS).add(
                Biomes.OLD_GROWTH_PINE_TAIGA,
                Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                Biomes.WINDSWEPT_FOREST,
                Biomes.WINDSWEPT_HILLS,
                Biomes.GROVE
        );

        tag(ModBiomeTags.BLADE_WOLF_SPAWNS).add(
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS,
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_GRAVELLY_HILLS,
                Biomes.WINDSWEPT_FOREST
        );

        tag(ModBiomeTags.RAVEN_WOLF_SPAWNS).add(
                Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_FOREST,
                Biomes.WINDSWEPT_GRAVELLY_HILLS,
                Biomes.STONY_PEAKS,
                Biomes.JAGGED_PEAKS

        );

        tag(ModBiomeTags.COMMAND_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.SUNFLOWER_PLAINS,
                Biomes.MEADOW,
                Biomes.SAVANNA_PLATEAU,
                Biomes.WINDSWEPT_HILLS
        );

        tag(ModBiomeTags.ASH_WOLF_SPAWNS).add(
                Biomes.BASALT_DELTAS
        );


        tag(ModBiomeTags.WITHER_WOLF_SPAWNS).add(
                Biomes.SOUL_SAND_VALLEY
        );

        tag(ModBiomeTags.BLAZE_WOLF_SPAWNS).add(
                Biomes.NETHER_WASTES
        );


        tag(ModBiomeTags.HALLOWEEN_WOLF_SPAWNS).add(
                Biomes.PLAINS,
                Biomes.SUNFLOWER_PLAINS,
                Biomes.FOREST,
                Biomes.FLOWER_FOREST,
                Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST
        );

    }
}