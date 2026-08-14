package net.ronm19.wolfism.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.world.level.biome.Biomes;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.tag.ModBiomeTags;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public final class ModBiomeTagProvider extends BiomeTagsProvider {
    public ModBiomeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Wolfism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider lookupProvider) {
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
    }
}