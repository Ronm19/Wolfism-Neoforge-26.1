package net.ronm19.wolfism.worldgen.feature;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.*;
import net.ronm19.wolfism.Wolfism;

import java.util.List;

public final class ModConfiguredFeatures {

    /*
     * Irregular Sculk stains that replace ordinary surface soil.
     */
    public static final ResourceKey<ConfiguredFeature<?, ?>>
            SCULK_SURFACE_PATCH = key("sculk_surface_patch");

    /*
     * Sparse surface Sensors.
     */
    public static final ResourceKey<ConfiguredFeature<?, ?>>
            SCULK_SENSOR = key("sculk_sensor");

    private ModConfiguredFeatures() {
    }

    public static void bootstrap(
            BootstrapContext<ConfiguredFeature<?, ?>> context
    ) {

        /*
         * ==========================================================
         * SCULK SURFACE PATCH
         * ==========================================================
         *
         * Replaces normal dirt-type surface blocks with vanilla Sculk.
         *
         * Radius:
         * 2–5 blocks
         *
         * Thickness:
         * 1 block
         *
         * This keeps the biome mostly grassy instead of turning the
         * entire thing into Surface Deep Dark™.
         */

        register(
                context,
                SCULK_SURFACE_PATCH,
                Feature.DISK,
                new DiskConfiguration(
                        BlockStateProvider.simple(
                                Blocks.SCULK.defaultBlockState()
                        ),

                        BlockPredicate.matchesBlocks(
                                Vec3i.ZERO,
                                List.of(
                                        Blocks.GRASS_BLOCK,
                                        Blocks.DIRT,
                                        Blocks.COARSE_DIRT,
                                        Blocks.ROOTED_DIRT,
                                        Blocks.MOSS_BLOCK
                                )
                        ),

                        UniformInt.of(
                                2,
                                5
                        ),

                        1
                )
        );

        /*
         * ==========================================================
         * SCULK SENSOR
         * ==========================================================
         */

        register(
                context,
                SCULK_SENSOR,
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(
                        BlockStateProvider.simple(
                                Blocks.SCULK_SENSOR.defaultBlockState()
                        )
                )
        );
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> key(
            String name
    ) {
        return ResourceKey.create(
                Registries.CONFIGURED_FEATURE,
                Identifier.fromNamespaceAndPath(
                        Wolfism.MOD_ID,
                        name
                )
        );
    }

    private static <
            FC extends FeatureConfiguration,
            F extends Feature<FC>
            > void register(
            BootstrapContext<ConfiguredFeature<?, ?>> context,
            ResourceKey<ConfiguredFeature<?, ?>> key,
            F feature,
            FC configuration
    ) {
        context.register(
                key,
                new ConfiguredFeature<>(
                        feature,
                        configuration
                )
        );
    }

    public static final class ModPlacedFeatures {

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
             * SCULK COLONIES
             * ==========================================================
             *
             * Instead of throwing one isolated disk into the biome, one successful
             * colony roll chooses a center and then places 4-6 small Sculk disks
             * within roughly 6 blocks of it. Overlap is intentional: it creates
             * irregular infected pockets with little satellite patches instead of
             * obvious lone circles.
             *
             * Average: one colony attempt every 3 chunks.
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
                            CountPlacement.of(UniformInt.of(4, 6)),
                            RandomOffsetPlacement.horizontal(UniformInt.of(-6, 6)),
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
}