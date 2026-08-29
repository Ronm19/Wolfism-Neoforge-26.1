package net.ronm19.wolfism.worldgen.region;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.ronm19.wolfism.worldgen.biome.ModBiomes;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;

import java.util.function.Consumer;

import static terrablender.api.ParameterUtils.*;

public final class SculkPlainsRegion extends Region {

    public SculkPlainsRegion(
            Identifier name,
            int weight
    ) {
        super(
                name,
                RegionType.OVERWORLD,
                weight
        );
    }

    @Override
    public void addBiomes(
            Registry<Biome> registry,
            Consumer<Pair<
                    Climate.ParameterPoint,
                    ResourceKey<Biome>
                    >> mapper
    ) {

        VanillaParameterOverlayBuilder builder =
                new VanillaParameterOverlayBuilder();

        /*
         * ==========================================================
         * SCULK PLAINS CLIMATE
         * ==========================================================
         *
         * We're intentionally being narrow here.
         *
         * Goal:
         * - uncommon
         * - relatively small
         * - inland
         * - mostly flat / plains-like
         * - temperate
         * - not mountainous
         * - not desert
         * - not swamp
         */

        new ParameterPointListBuilder()

                /*
                 * Normal temperate climate.
                 */
                .temperature(
                        Temperature.NEUTRAL
                )

                /*
                 * Normal -> slightly damp.
                 *
                 * Fits living grass + surface Sculk without
                 * making it a swamp.
                 */
                .humidity(
                        Humidity.NEUTRAL,
                        Humidity.WET
                )

                /*
                 * Keep it away from oceans/coasts.
                 */
                .continentalness(
                        Continentalness.MID_INLAND
                )

                /*
                 * Higher erosion generally favors gentler terrain.
                 *
                 * This helps preserve the PLAINS part of
                 * Sculk Plains.
                 */
                .erosion(
                        Erosion.EROSION_4,
                        Erosion.EROSION_5
                )

                /*
                 * Surface biome only.
                 */
                .depth(
                        Depth.SURFACE
                )

                /*
                 * Limit the noise slices so this doesn't occupy
                 * every matching piece of temperate land.
                 */
                .weirdness(
                        Weirdness.MID_SLICE_NORMAL_DESCENDING,
                        Weirdness.MID_SLICE_VARIANT_ASCENDING
                )

                .build()

                .forEach(point ->
                        builder.add(
                                point,
                                ModBiomes.SCULK_PLAINS
                        )
                );

        builder.build().forEach(mapper);
    }
}