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

import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.RavenWolfBabyModel;
import net.ronm19.wolfism.client.model.RavenWolfModel;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;
import net.ronm19.wolfism.client.renderer.layer.RavenWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.state.RavenWolfRenderState;
import net.ronm19.wolfism.entity.custom.RavenWolf;

/**
 * Raven Wolf renderer.
 */
public final class RavenWolfRenderer extends AgeableMobRenderer<RavenWolf, WolfRenderState, WolfModel> {

    private static final Identifier ADULT_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID, "textures/entity/wolf/raven_wolf.png");

    private static final Identifier BABY_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID, "textures/entity/wolf/raven_wolf_baby.png");

    public RavenWolfRenderer(
            EntityRendererProvider.Context context) {

        super(
                context,

                new RavenWolfModel(
                        context.bakeLayer(
                                RavenWolfModel.LAYER_LOCATION)),

                new RavenWolfBabyModel(
                        context.bakeLayer(
                                RavenWolfBabyModel.LAYER_LOCATION)),

                0.65F);

        // =============================================================
        // Armor
        // =============================================================

        this.addLayer(
                new WolfArmorLayer(
                        this,
                        context.getModelSet(),
                        context.getEquipmentRenderer()));

        // =============================================================
        // Wolfism custom collar
        // =============================================================

        this.addLayer(
                new RavenWolfCollarLayer(
                        this,

                        new WolfismAdultCollarModel(
                                context.bakeLayer(
                                        WolfismAdultCollarModel
                                                .LAYER_LOCATION)),

                        new WolfismBabyCollarModel(
                                context.bakeLayer(
                                        WolfismBabyCollarModel
                                                .LAYER_LOCATION))));
    }

    // =================================================================
    // Texture
    // =================================================================

    @Override
    public Identifier getTextureLocation(
            WolfRenderState state) {

        return state.isBaby
                ? BABY_TEXTURE
                : ADULT_TEXTURE;
    }

    // =================================================================
    // Wet tint
    // =================================================================

    @Override
    protected int getModelTint(
            WolfRenderState state) {

        float wetShade =
                state.wetShade;

        return wetShade == 1.0F
                ? -1

                : ARGB.colorFromFloat(
                1.0F,
                wetShade,
                wetShade,
                wetShade);
    }

    // =================================================================
    // Render state
    // =================================================================

    @Override
    public RavenWolfRenderState createRenderState() {

        return new RavenWolfRenderState();
    }

    @Override
    public void extractRenderState(
            RavenWolf entity,
            WolfRenderState state,
            float partialTick) {

        super.extractRenderState(
                entity,
                state,
                partialTick);

        state.isAngry =
                entity.isAngry()
                        || entity.isRavensRageActive();

        // Only the server-synchronized pose is a client animation authority.
        // orderedToSit is server-only; predicting it with onGround flickered
        // while Raven was landing or crossing a block edge.
        state.isSitting = entity.isInSittingPose();

        state.tailAngle =
                entity.getTailAngle();

        state.headRollAngle =
                entity.getHeadRollAngle(
                        partialTick);

        state.shakeAnim =
                entity.getShakeAnim(
                        partialTick);

        state.texture =
                state.isBaby
                        ? BABY_TEXTURE
                        : ADULT_TEXTURE;

        state.wetShade =
                entity.getWetShade(
                        partialTick);

        state.collarColor =
                entity.isTame()
                        ? entity.getCollarColor()
                        : null;

        state.bodyArmorItem =
                entity.getBodyArmorItem()
                        .copy();

        if (state
                instanceof RavenWolfRenderState ravenState) {

            /*
             * Synced Raven flight state is now the sole flight authority.
             */
            ravenState.isFlying =
                    !state.isSitting
                            && entity.isRavenFlying();

            ravenState.isLanding = ravenState.isFlying && entity.isRavenLanding();

            ravenState.eyeOfRavenActive =
                    entity.isEyeOfRavenActive();

            ravenState.ravensRageActive =
                    entity.isRavensRageActive();

            updateFlightPose(entity, ravenState, partialTick);

            if (state.isSitting || ravenState.isFlying) {

                ravenState.walkAnimationPos =
                        0.0F;

                ravenState.walkAnimationSpeed =
                        0.0F;
            }
        }
    }

    private static void updateFlightPose(
            RavenWolf entity, RavenWolfRenderState state, float partialTick) {
        state.wingAngle = 0.0F;
        state.wingTipAngle = 0.0F;
        state.flightPitch = 0.0F;
        state.flightBank = 0.0F;
        if (!state.isFlying) {
            return;
        }

        // One deliberate power stroke, then a held glide. The same phase is
        // used by Raven's flight motor, so wing motion follows her acceleration.
        float phase = entity.getRavenFlightPhase(partialTick);
        float stroke = Mth.clamp(phase / 14.0F, 0.0F, 1.0F);
        float envelope = Mth.sin(stroke * Mth.PI);
        float angle = stroke * Mth.TWO_PI;
        float strength = state.ravensRageActive ? 0.68F : 0.56F;
        if (state.eyeOfRavenActive) {
            strength *= 0.9F;
        }

        if (state.isLanding) {
            // Broad braking strokes and lowered feet replace the cruising pose.
            angle = (entity.tickCount + partialTick) * 0.44F;
            envelope = 1.0F;
            strength = 0.58F;
        }

        state.wingAngle = 0.10F + Mth.sin(angle) * envelope * strength;
        state.wingTipAngle = -0.05F
                + Mth.sin(angle - 0.7F) * envelope * strength * 0.48F;
        state.flightPitch = Mth.clamp(state.xRot, -28.0F, 28.0F);
        state.flightBank = state.isLanding ? 0.0F : Mth.clamp(
                Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * -2.0F,
                -16.0F, 16.0F);
    }

    // =================================================================
    // Visual flight pitch
    // =================================================================

    @Override
    protected void setupRotations(
            WolfRenderState state,
            PoseStack poseStack,
            float bodyRot,
            float scale) {

        super.setupRotations(
                state,
                poseStack,
                bodyRot,
                scale);

        if (!(state
                instanceof RavenWolfRenderState ravenState)

                || !ravenState.isFlying) {

            return;
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(ravenState.flightPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(ravenState.flightBank));
    }
}
