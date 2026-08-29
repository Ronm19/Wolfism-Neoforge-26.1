package net.ronm19.wolfism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.client.model.PhantomWolfBabyModel;
import net.ronm19.wolfism.client.model.PhantomWolfModel;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;
import net.ronm19.wolfism.client.renderer.layer.PhantomWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.state.PhantomWolfRenderState;
import net.ronm19.wolfism.entity.custom.PhantomWolf;

/** Renderer for the custom adult/baby Phantom Wolf models. */
public final class PhantomWolfRenderer
        extends AgeableMobRenderer<PhantomWolf, WolfRenderState, WolfModel> {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/phantom_wolf.png");

    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/phantom_wolf_baby.png");

    public PhantomWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PhantomWolfModel(context.bakeLayer(PhantomWolfModel.LAYER_LOCATION)),
                new PhantomWolfBabyModel(context.bakeLayer(PhantomWolfBabyModel.LAYER_LOCATION)),
                0.65F);

        // Wolf armor has its own vanilla model/texture pipeline, so it remains
        // safe with the Phantom Wolf's custom 64x64 body UV layout.
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));

        // Do NOT use vanilla WolfCollarLayer: Phantom Wolf has repacked 64x64 UVs.
        // This dedicated transparent overlay follows the exact parent-model pose
        // while coloring only the custom neck-band pixels.
        this.addLayer(new PhantomWolfCollarLayer(
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
    public PhantomWolfRenderState createRenderState() {
        return new PhantomWolfRenderState();
    }

    @Override
    public void extractRenderState(
            PhantomWolf entity,
            WolfRenderState state,
            float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        // Same Wolf-specific extraction used by the project's normal custom
        // wolf renderers, preserving wet/shake/sit/armor state.
        state.isAngry = entity.isAngry();

        // Sitting is a vanilla Wolf state and must be authoritative.
        // V6 accidentally made it depend on !isPhantomFlying(), creating a
        // circular client-state bug: a stale flight flag could veto the sitting
        // animation forever even while the entity was stationary and ordered
        // to sit.
        state.isSitting = entity.isInSittingPose()
                || (entity.isOrderedToSit() && entity.onGround());

        state.tailAngle = entity.getTailAngle();
        state.headRollAngle = entity.getHeadRollAngle(partialTick);
        state.shakeAnim = entity.getShakeAnim(partialTick);
        state.texture = state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
        state.wetShade = entity.getWetShade(partialTick);
        state.collarColor = entity.isTame() ? entity.getCollarColor() : null;
        state.bodyArmorItem = entity.getBodyArmorItem().copy();

        if (state instanceof PhantomWolfRenderState phantomState) {
            phantomState.flapTime = entity.getUniqueFlapTickOffset() + entity.tickCount + partialTick;

            // Never let a stale Phantom flight state overwrite the already
            // synchronized vanilla wolf sitting pose.
            phantomState.isFlying = !state.isSitting && entity.isPhantomFlying();
            phantomState.isSwooping = !state.isSitting && entity.isSwooping();
        }
    }

    @Override
    protected void setupRotations(
            WolfRenderState state,
            PoseStack poseStack,
            float bodyRot,
            float scale) {
        super.setupRotations(state, poseStack, bodyRot, scale);

        // Keep the renderer generic as WolfRenderState so vanilla WolfArmorLayer
        // remains type-compatible. createRenderState() still returns our
        // PhantomWolfRenderState instance at runtime.
        if (state instanceof PhantomWolfRenderState phantomState && phantomState.isFlying) {
            // A literal Phantom can pitch almost vertically because its body is a
            // flat manta silhouette. A quadruped wolf looks broken at those same
            // angles. Keep the real flight vector untouched and soften ONLY the
            // rendered body tilt.
            float visualScale = phantomState.isSwooping ? 0.60F : 0.42F;
            float visualPitch = Mth.clamp(phantomState.xRot * visualScale, -38.0F, 38.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(visualPitch));
        }
    }
}
