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
import net.ronm19.wolfism.client.model.CherryAdultWolfModel;
import net.ronm19.wolfism.client.model.CherryBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.CherryWolfRenderState;
import net.ronm19.wolfism.entity.custom.CherryWolf;

/** Cherry Wolf renderer retaining vanilla collar, armor, wet-state and shake behavior. */
public final class CherryWolfRenderer extends AgeableMobRenderer<CherryWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/cherry_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/cherry_wolf_baby.png");

    private final WolfRenderer vanillaRenderer;

    public CherryWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new CherryAdultWolfModel(context.bakeLayer(CherryAdultWolfModel.LAYER_LOCATION)),
                new CherryBabyWolfModel(context.bakeLayer(CherryBabyWolfModel.LAYER_LOCATION)),
                0.5F);
        this.vanillaRenderer = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public CherryWolfRenderState createRenderState() {
        return new CherryWolfRenderState();
    }

    @Override
    public void extractRenderState(CherryWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaRenderer.extractRenderState(entity, baseState, partialTick);
        if (baseState instanceof CherryWolfRenderState state) {
            state.petalAidActive = entity.isPetalAidActive();
            state.cherryBlossomBurstActive = entity.isCherryBlossomBurstActive();
            state.bloomingPathActive = entity.isBloomingPathActive();
            state.sakuraSanctuaryActive = entity.isSakuraSanctuaryActive();
            state.cherryCycle = entity.tickCount + partialTick;
        }
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
