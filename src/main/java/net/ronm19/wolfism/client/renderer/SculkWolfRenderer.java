package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.SculkAdultWolfModel;
import net.ronm19.wolfism.client.model.SculkBabyWolfModel;
import net.ronm19.wolfism.entity.custom.SculkWolf;

/**
 * Sculk Wolf renderer.
 *
 * <p>The adult is intentionally kept at vanilla wolf scale. The sensory
 * tendrils provide the distinctive silhouette without enlarging the whole
 * animal.</p>
 */
public final class SculkWolfRenderer extends AgeableMobRenderer<SculkWolf, WolfRenderState, WolfModel> {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/sculk_wolf.png");

    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/sculk_wolf_baby.png");

    private final WolfRenderer vanillaRenderer;

    public SculkWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new SculkAdultWolfModel(
                        context.bakeLayer(SculkAdultWolfModel.LAYER_LOCATION)),
                new SculkBabyWolfModel(
                        context.bakeLayer(SculkBabyWolfModel.LAYER_LOCATION)),
                0.55F);

        this.vanillaRenderer = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(
                this,
                context.getModelSet(),
                context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public WolfRenderState createRenderState() {
        return new WolfRenderState();
    }

    @Override
    public void extractRenderState(
            SculkWolf entity,
            WolfRenderState state,
            float partialTick) {

        this.vanillaRenderer.extractRenderState(entity, state, partialTick);

        /*
         * DO NOT multiply state.scale here.
         * Sculk Wolf uses vanilla wolf body size.
         */
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}