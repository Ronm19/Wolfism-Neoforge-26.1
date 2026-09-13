package net.ronm19.wolfism.client.style;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.ronm19.wolfism.Wolfism;

/** Texel-aligned surface shading; it never repaints a wolf's markings or changes geometry. */
@EventBusSubscriber(modid = Wolfism.MOD_ID, value = Dist.CLIENT)
public final class WolfSurfaceMaterials {
    public static final Identifier ATLAS = id("textures/entity/material/wolf_surface_atlas.png");
    private static final Set<String> LARGE_LAYOUT = Set.of("angel_wolf", "bee_wolf", "demon_wolf", "phantom_wolf", "raven_wolf");
    private static final Map<String, Material> SPECIES = new HashMap<>();
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static final RenderPipeline[][] PIPELINES = new RenderPipeline[Material.values().length][4];

    static {
        group(Material.SHAGGY, "arctic_wolf frost_wolf dire_wolf wolf_king primordial_wolf christmas_wolf ancient_wolf");
        group(Material.SLEEK, "black_wolf shadow_wolf phantom_wolf vampire_wolf blood_wolf water_wolf drowned_wolf toxic_wolf void_wolf spirit_wolf spectral_wolf astral_wolf lunar_wolf solar_wolf infernal_wolf fire_wolf end_wolf rift_wolf");
        group(Material.FEATHER, "raven_wolf angel_wolf");
        group(Material.BONE, "skeleton_wolf wither_wolf grave_wolf");
        group(Material.STONE, "earth_wolf magma_wolf ash_wolf blaze_wolf sculk_wolf");
        group(Material.METAL, "blade_wolf war_wolf gem_wolf golden_wolf");
        group(Material.FOLIAGE, "cherry_wolf mushroom_wolf violet_wolf easter_wolf");
    }

    private WolfSurfaceMaterials() {}

    private enum Material {
        SOFT(0, 0, .5295f, 1.30f), SHAGGY(1, 0, .4444f, 1.10f),
        SLEEK(2, 0, .4886f, 1.20f), FEATHER(3, 0, .4845f, 1.10f),
        BONE(0, 1, .6929f, 1.15f), STONE(1, 1, .3247f, 1.25f),
        METAL(2, 1, .4912f, 1.20f), FOLIAGE(3, 1, .3862f, 1.10f);
        final int column, row;
        final float center, strength;
        Material(int column, int row, float center, float strength) {
            this.column = column; this.row = row; this.center = center; this.strength = strength;
        }
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, path); }
    private static void group(Material material, String species) {
        for (String name : species.split(" ")) SPECIES.put(name, material);
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        for (Material material : Material.values()) {
            for (int layout = 0; layout < 4; layout++) {
                boolean baby = (layout & 1) != 0;
                boolean large = layout >= 2;
                var pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                        .withLocation(id("pipeline/wolf_surface_" + material.name().toLowerCase(java.util.Locale.ROOT) + "_" + layout))
                        .withFragmentShader(id("core/wolf_surface"))
                        .withSampler("Sampler1").withSampler("WolfDetailSampler")
                        .withShaderDefine("ALPHA_CUTOUT", .1f)
                        .withShaderDefine("PER_FACE_LIGHTING")
                        .withShaderDefine("DETAIL_COLUMN", (float) material.column)
                        .withShaderDefine("DETAIL_ROW", (float) material.row)
                        .withShaderDefine("DETAIL_CENTER", material.center)
                        .withShaderDefine("DETAIL_STRENGTH", material.strength)
                        .withShaderDefine("UV_WIDTH", large || !baby ? 64f : 32f)
                        .withShaderDefine("UV_HEIGHT", large ? 64f : 32f)
                        .withShaderDefine("WOLF_BABY", baby ? 1 : 0)
                        .withCull(false).build();
                PIPELINES[material.ordinal()][layout] = pipeline;
                event.registerPipeline(pipeline);
            }
        }
    }

    public static boolean accepts(Identifier texture) {
        return texture.getNamespace().equals(Wolfism.MOD_ID)
                && texture.getPath().startsWith("textures/entity/wolf/");
    }

    public static RenderType surface(Identifier texture) {
        return TYPES.computeIfAbsent(texture, key -> {
            String name = key.getPath().substring(key.getPath().lastIndexOf('/') + 1).replace(".png", "");
            boolean baby = name.contains("_baby");
            String species = name.replace("_baby", "").replace("_active", "");
            int layout = (LARGE_LAYOUT.contains(species) ? 2 : 0) + (baby ? 1 : 0);
            var material = SPECIES.getOrDefault(species, Material.SOFT);
            var setup = RenderSetup.builder(PIPELINES[material.ordinal()][layout])
                    .withTexture("Sampler0", texture).withTexture("WolfDetailSampler", ATLAS)
                    .useLightmap().useOverlay().affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.NONE).createRenderSetup();
            return RenderType.create("wolfism_surface", setup);
        });
    }
}
