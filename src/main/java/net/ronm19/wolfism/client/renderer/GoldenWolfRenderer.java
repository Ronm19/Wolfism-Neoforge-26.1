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
import net.ronm19.wolfism.client.model.GoldenAdultWolfModel;
import net.ronm19.wolfism.client.model.GoldenBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.GoldenWolfRenderState;
import net.ronm19.wolfism.entity.custom.GoldenWolf;

/** Golden Wolf renderer retaining vanilla collar, armor, wet-state and shake behavior. */
public final class GoldenWolfRenderer extends AgeableMobRenderer<GoldenWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/golden_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/golden_wolf_baby.png");

    private final WolfRenderer vanillaRenderer;

    public GoldenWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new GoldenAdultWolfModel(context.bakeLayer(GoldenAdultWolfModel.LAYER_LOCATION)),
                new GoldenBabyWolfModel(context.bakeLayer(GoldenBabyWolfModel.LAYER_LOCATION)),
                0.5F);
        this.vanillaRenderer = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public GoldenWolfRenderState createRenderState() {
        return new GoldenWolfRenderState();
    }

    @Override
    public void extractRenderState(GoldenWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaRenderer.extractRenderState(entity, baseState, partialTick);
        if (baseState instanceof GoldenWolfRenderState state) {
            state.fortuneDigActive = entity.isFortuneDigActive();
            state.radiantShareActive = entity.isRadiantShareActive();
            state.goldenBarrierActive = entity.isGoldenBarrierActive();
            state.blessingActive = entity.isBlessingOfWealthActive();
            state.goldenCycle = entity.tickCount + partialTick;
        }
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
