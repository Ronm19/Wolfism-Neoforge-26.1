package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.ShadowAdultWolfModel;
import net.ronm19.wolfism.client.model.ShadowBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.ShadowWolfRenderState;
import net.ronm19.wolfism.entity.custom.ShadowWolf;

/** Dedicated Shadow renderer retaining vanilla wolf collar/armor/wet-state behavior. */
public final class ShadowWolfRenderer extends AgeableMobRenderer<ShadowWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/shadow_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/shadow_wolf_baby.png");

    private final WolfRenderer vanillaRenderer;

    public ShadowWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ShadowAdultWolfModel(context.bakeLayer(ShadowAdultWolfModel.LAYER_LOCATION)),
                new ShadowBabyWolfModel(context.bakeLayer(ShadowBabyWolfModel.LAYER_LOCATION)),
                0.5F);
        this.vanillaRenderer = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public ShadowWolfRenderState createRenderState() {
        return new ShadowWolfRenderState();
    }

    @Override
    public void extractRenderState(ShadowWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaRenderer.extractRenderState(entity, baseState, partialTick);
        if (baseState instanceof ShadowWolfRenderState state) {
            state.voidDashActive = entity.isVoidDashActive();
            state.shadowBladesActive = entity.isShadowBladesActive();
            state.duskVeilActive = entity.isDuskVeilActive();
            state.shadowAssassinActive = entity.isShadowAssassinActive();
            state.shadowCycle = entity.tickCount + partialTick;
        }
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
