package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.DemonWolfBabyModel;
import net.ronm19.wolfism.client.model.DemonWolfModel;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;
import net.ronm19.wolfism.client.renderer.layer.DemonWolfCollarLayer;
import net.ronm19.wolfism.entity.custom.DemonWolf;

/** Renderer for the custom adult/baby Demon Wolf models. */
public final class DemonWolfRenderer
        extends AgeableMobRenderer<DemonWolf, WolfRenderState, WolfModel> {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/demon_wolf.png");

    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/demon_wolf_baby.png");

    public DemonWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new DemonWolfModel(context.bakeLayer(DemonWolfModel.LAYER_LOCATION)),
                new DemonWolfBabyModel(context.bakeLayer(DemonWolfBabyModel.LAYER_LOCATION)),
                0.60F);

        this.addLayer(new WolfArmorLayer(
                this,
                context.getModelSet(),
                context.getEquipmentRenderer()));

        this.addLayer(new DemonWolfCollarLayer(
                this,
                new WolfismAdultCollarModel(
                        context.bakeLayer(WolfismAdultCollarModel.LAYER_LOCATION)),
                new WolfismBabyCollarModel(
                        context.bakeLayer(WolfismBabyCollarModel.LAYER_LOCATION))));
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }

    @Override
    protected int getModelTint(WolfRenderState state) {
        float wetShade = state.wetShade;
        return wetShade == 1.0F
                ? -1
                : ARGB.colorFromFloat(1.0F, wetShade, wetShade, wetShade);
    }

    @Override
    public WolfRenderState createRenderState() {
        return new WolfRenderState();
    }

    @Override
    public void extractRenderState(
            DemonWolf entity,
            WolfRenderState state,
            float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        state.isAngry = entity.isAngry() || entity.isDemonCombatVisualActive();
        state.isSitting = entity.isInSittingPose();
        state.tailAngle = entity.getTailAngle();
        state.headRollAngle = entity.getHeadRollAngle(partialTick);
        state.shakeAnim = entity.getShakeAnim(partialTick);
        state.texture = state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
        state.wetShade = entity.getWetShade(partialTick);

        // Required by DemonWolfCollarLayer. Do not suppress the custom collar.
        state.collarColor = entity.isTame() ? entity.getCollarColor() : null;
        state.bodyArmorItem = entity.getBodyArmorItem().copy();
    }
}
