package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.MushroomAdultWolfModel;
import net.ronm19.wolfism.client.model.MushroomBabyWolfModel;
import net.ronm19.wolfism.client.renderer.layer.MushroomWolfMushroomLayer;
import net.ronm19.wolfism.client.renderer.state.MushroomWolfRenderState;
import net.ronm19.wolfism.entity.custom.MushroomWolf;

/** Dedicated renderer using vanilla wolf animation + real vanilla red-mushroom block models. */
public final class MushroomWolfRenderer
        extends AgeableMobRenderer<MushroomWolf, WolfRenderState, WolfModel> {
    public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/mushroom_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/mushroom_wolf_baby.png");

    private final WolfRenderer vanillaStateExtractor;
    private final BlockModelResolver blockModelResolver;

    public MushroomWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new MushroomAdultWolfModel(context.bakeLayer(MushroomAdultWolfModel.LAYER_LOCATION)),
                new MushroomBabyWolfModel(context.bakeLayer(MushroomBabyWolfModel.LAYER_LOCATION)),
                0.5F);

        this.vanillaStateExtractor = new WolfRenderer(context);
        this.blockModelResolver = context.getBlockModelResolver();

        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
        this.addLayer(new MushroomWolfMushroomLayer(this));
    }

    @Override
    public MushroomWolfRenderState createRenderState() {
        return new MushroomWolfRenderState();
    }

    @Override
    public void extractRenderState(MushroomWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaStateExtractor.extractRenderState(entity, baseState, partialTick);
        if (baseState instanceof MushroomWolfRenderState state) {
            this.blockModelResolver.update(
                    state.mushroomModel,
                    Blocks.RED_MUSHROOM.defaultBlockState(),
                    BLOCK_DISPLAY_CONTEXT);
        }
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
