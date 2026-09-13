package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.EasterWolf;

/** Vanilla wolf geometry with pastel spring/egg seasonal textures. */
public final class EasterWolfRenderer
        extends AgeableMobRenderer<EasterWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_NORMAL = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/easter_wolf.png");
    private static final Identifier ADULT_ACTIVE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/easter_wolf_active.png");
    private static final Identifier BABY_NORMAL = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/easter_wolf_baby.png");
    private static final Identifier BABY_ACTIVE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/easter_wolf_baby_active.png");

    public EasterWolfRenderer(EntityRendererProvider.Context context) {
        super(context,
                new AdultWolfModel(context.bakeLayer(ModelLayers.WOLF)),
                new BabyWolfModel(context.bakeLayer(ModelLayers.WOLF_BABY)),
                0.5F);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby
                ? state.isAngry ? BABY_ACTIVE : BABY_NORMAL
                : state.isAngry ? ADULT_ACTIVE : ADULT_NORMAL;
    }

    @Override
    protected int getModelTint(WolfRenderState state) {
        float wet = state.wetShade;
        return wet == 1.0F ? -1 : ARGB.colorFromFloat(1.0F, wet, wet, wet);
    }

    @Override
    public WolfRenderState createRenderState() {
        return new WolfRenderState();
    }

    @Override
    public void extractRenderState(EasterWolf entity, WolfRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isAngry = entity.isAngry() || entity.isSpringVisualActive();
        state.isSitting = entity.isInSittingPose();
        state.tailAngle = entity.getTailAngle();
        state.headRollAngle = entity.getHeadRollAngle(partialTick);
        state.shakeAnim = entity.getShakeAnim(partialTick);
        state.texture = this.getTextureLocation(state);
        state.wetShade = entity.getWetShade(partialTick);
        state.collarColor = entity.isTame() ? entity.getCollarColor() : null;
        state.bodyArmorItem = entity.getBodyArmorItem().copy();
    }
}
