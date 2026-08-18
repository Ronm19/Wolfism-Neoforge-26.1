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

    }
}
