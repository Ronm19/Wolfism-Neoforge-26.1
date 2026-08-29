package net.ronm19.wolfism.worldgen.feature;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.ronm19.wolfism.Wolfism;

import java.util.List;

public final class ModPlacedFeatures {

    public static final ResourceKey<PlacedFeature>
            SCULK_SURFACE_PATCH = key("sculk_surface_patch");

    public static final ResourceKey<PlacedFeature>
            SCULK_SENSOR = key("sculk_sensor");

    private ModPlacedFeatures() {
    }

    public static void bootstrap(
            BootstrapContext<PlacedFeature> context
    ) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures =
                context.lookup(
                        Registries.CONFIGURED_FEATURE
                );

        /*
         * ==========================================================
         * SCULK PATCHES
         * ==========================================================
         *
         * Average:
         * roughly one attempt every 3 chunks.
         *
         * We can make them more common after seeing the biome.
         */

        register(
                context,
                SCULK_SURFACE_PATCH,
                configuredFeatures.getOrThrow(
                        ModConfiguredFeatures.SCULK_SURFACE_PATCH
                ),
                List.of(
                        RarityFilter.onAverageOnceEvery(3),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()
                )
        );

        /*
         * ==========================================================
         * SCULK SENSORS
         * ==========================================================
         *
         * Intentionally much rarer than patches.
         *
         * About one attempt every 5 chunks initially.
         */

        register(
                context,
                SCULK_SENSOR,
                configuredFeatures.getOrThrow(
                        ModConfiguredFeatures.SCULK_SENSOR
                ),
                List.of(
                        RarityFilter.onAverageOnceEvery(5),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()
                )
        );
    }

    private static ResourceKey<PlacedFeature> key(
            String name
    ) {
        return ResourceKey.create(
                Registries.PLACED_FEATURE,
                Identifier.fromNamespaceAndPath(
                        Wolfism.MOD_ID,
                        name
                )
        );
    }

    private static void register(
            BootstrapContext<PlacedFeature> context,
            ResourceKey<PlacedFeature> key,
            Holder<ConfiguredFeature<?, ?>> configuredFeature,
            List<PlacementModifier> modifiers
    ) {
        context.register(
                key,
                new PlacedFeature(
                        configuredFeature,
                        modifiers
                )
        );
    }
}