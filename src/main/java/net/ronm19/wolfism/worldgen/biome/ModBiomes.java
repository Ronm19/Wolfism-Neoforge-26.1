package net.ronm19.wolfism.worldgen.biome;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.OverworldBiomes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.worldgen.feature.ModPlacedFeatures;

public final class ModBiomes {

    public static final ResourceKey<Biome> SCULK_PLAINS =
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath(
                            Wolfism.MOD_ID,
                            "sculk_plains"
                    )
            );

    private ModBiomes() {
    }

    public static void bootstrap(
            BootstrapContext<Biome> context
    ) {
        context.register(
                SCULK_PLAINS,
                createSculkPlains(context)
        );
    }

    private static Biome createSculkPlains(
            BootstrapContext<Biome> context
    ) {

        HolderGetter<PlacedFeature> placedFeatures =
                context.lookup(
                        Registries.PLACED_FEATURE
                );

        HolderGetter<ConfiguredWorldCarver<?>> carvers =
                context.lookup(
                        Registries.CONFIGURED_CARVER
                );

        /*
         * ==========================================================
         * MOB SPAWNS
         * ==========================================================
         */

        MobSpawnSettings.Builder spawns =
                new MobSpawnSettings.Builder();

        /*
         * Important:
         *
         * Do NOT use plainsSpawns() here.
         * That fills the tiny CREATURE cap with sheep/pigs/cows/chickens
         * and makes Sculk Wolf compete with the entire plains ecosystem.
         *
         * commonSpawns() gives us normal hostile/ambient Overworld life
         * without filling our creature pool with plains farm animals.
         */
        BiomeDefaultFeatures.commonSpawns(
                spawns,
                70
        );


        /*
         * Sculk Plains' signature spawn.
         *
         * Weight 12 replaces the temporary diagnostic weight of 200. The wolf
         * stays findable, but the local population cap in SculkWolf prevents
         * the non-despawning animal from flooding the MONSTER spawn budget.
         *
         * The EntityType deliberately uses MONSTER as its spawning budget even
         * though the wolf's AI remains non-hostile.
         */
        spawns.addSpawn(
                MobCategory.MONSTER,
                28,
                new MobSpawnSettings.SpawnerData(
                        ModEntities.SCULK_WOLF.get(),
                        1,
                        2
                )
        );

        /*
         * ==========================================================
         * GENERATION
         * ==========================================================
         */

        BiomeGenerationSettings.Builder generation =
                new BiomeGenerationSettings.Builder(
                        placedFeatures,
                        carvers
                );

        /*
         * Vanilla Overworld foundation.
         */
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);

        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);

        /*
         * ==========================================================
         * SCULK CONTAMINATION
         * ==========================================================
         */

        generation.addFeature(
                GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(
                        ModPlacedFeatures.SCULK_SURFACE_PATCH
                )
        );

        generation.addFeature(
                GenerationStep.Decoration.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(
                        ModPlacedFeatures.SCULK_SENSOR
                )
        );

        /*
         * ==========================================================
         * VANILLA PLAINS LIFE
         * ==========================================================
         */

        BiomeDefaultFeatures.addPlainVegetation(generation);
        BiomeDefaultFeatures.addDefaultMushrooms(generation);
        BiomeDefaultFeatures.addDefaultExtraVegetation(
                generation,
                true
        );

        /*
         * ==========================================================
         * ATMOSPHERE
         * ==========================================================
         */

        float temperature = 0.70F;

        return new Biome.BiomeBuilder()

                /*
                 * This is NOT the Deep Dark.
                 *
                 * Rain + daylight stay completely normal.
                 */
                .hasPrecipitation(true)
                .temperature(temperature)
                .downfall(0.50F)

                /*
                 * Keep the normal Overworld sky.
                 */
                .setAttribute(
                        EnvironmentAttributes.SKY_COLOR,
                        OverworldBiomes.calculateSkyColor(
                                temperature
                        )
                )

                /*
                 * Slightly darker/cooler water than vanilla plains.
                 *
                 * Subtle. We don't want Cyan Dimension 2.0.
                 */
                .specialEffects(
                        new BiomeSpecialEffects.Builder()
                                .waterColor(0x345B8C)
                                .build()
                )

                /*
                 * Slight atmospheric tint.
                 */
                .setAttribute(
                        EnvironmentAttributes.FOG_COLOR,
                        0xA2ADB5
                )

                .mobSpawnSettings(
                        spawns.build()
                )

                .generationSettings(
                        generation.build()
                )

                .build();
    }
}