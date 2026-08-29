package net.ronm19.wolfism.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.ronm19.wolfism.client.model.MushroomAdultWolfModel;
import net.ronm19.wolfism.client.model.MushroomBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.MushroomWolfRenderState;
import org.joml.Quaternionf;

/**
 * Attaches Minecraft's actual red-mushroom block model to the animated wolf torso.
 * Adult = three mushrooms. Baby = two smaller mushrooms.
 */
public final class MushroomWolfMushroomLayer extends RenderLayer<WolfRenderState, WolfModel> {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float ADULT_AXIS_CORRECTION = (float) (-Math.PI / 2.0D);

    private static final Attachment ADULT_FRONT = new Attachment(-1.15F, -0.80F, 4.05F, -18.0F, 0.46F);
    private static final Attachment ADULT_MIDDLE = new Attachment(1.05F, 1.20F, 3.05F, 20.0F, 0.53F);
    private static final Attachment ADULT_REAR = new Attachment(-1.00F, 5.10F, 3.05F, -10.0F, 0.43F);

    private static final Attachment BABY_FRONT = new Attachment(-0.85F, -2.05F, -1.20F, -15.0F, 0.31F);
    private static final Attachment BABY_REAR = new Attachment(0.85F, -2.05F, 1.90F, 17.0F, 0.28F);

    public MushroomWolfMushroomLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            WolfRenderState baseState,
            float yRot,
            float xRot) {

        if (!(baseState instanceof MushroomWolfRenderState state) || state.isInvisible) {
            return;
        }

        WolfModel model = this.getParentModel();
        if (baseState.isBaby && model instanceof MushroomBabyWolfModel baby) {
            submitBaby(poseStack, nodeCollector, packedLight, state, baby.mushroomBodyAnchor(), BABY_FRONT);
            submitBaby(poseStack, nodeCollector, packedLight, state, baby.mushroomBodyAnchor(), BABY_REAR);
            return;
        }

        if (model instanceof MushroomAdultWolfModel adult) {
            submitAdult(poseStack, nodeCollector, packedLight, state, adult.mushroomUpperBodyAnchor(), ADULT_FRONT);
            submitAdult(poseStack, nodeCollector, packedLight, state, adult.mushroomBodyAnchor(), ADULT_MIDDLE);
            submitAdult(poseStack, nodeCollector, packedLight, state, adult.mushroomBodyAnchor(), ADULT_REAR);
        }
    }

    private static void submitAdult(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            MushroomWolfRenderState state,
            ModelPart anchor,
            Attachment attachment) {
        poseStack.pushPose();
        anchor.translateAndRotate(poseStack);
        poseStack.translate(
                attachment.xPixels * PIXEL,
                attachment.yPixels * PIXEL,
                attachment.zPixels * PIXEL);
        poseStack.mulPose(new Quaternionf().rotationX(ADULT_AXIS_CORRECTION));
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(attachment.yawDegrees)));
        submitVanillaMushroom(poseStack, nodeCollector, packedLight, state, attachment.scale);
        poseStack.popPose();
    }

    private static void submitBaby(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            MushroomWolfRenderState state,
            ModelPart anchor,
            Attachment attachment) {
        poseStack.pushPose();
        anchor.translateAndRotate(poseStack);
        poseStack.translate(
                attachment.xPixels * PIXEL,
                attachment.yPixels * PIXEL,
                attachment.zPixels * PIXEL);
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(attachment.yawDegrees)));
        submitVanillaMushroom(poseStack, nodeCollector, packedLight, state, attachment.scale);
        poseStack.popPose();
    }

    private static void submitVanillaMushroom(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            MushroomWolfRenderState state,
            float scale) {
        // Vanilla block models are authored in block space. Center the X/Z footprint
        // while keeping Y=0 at the attachment surface so the stem grows out of the back.
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        state.mushroomModel.submit(
                poseStack,
                nodeCollector,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor);
    }

    private record Attachment(
            float xPixels,
            float yPixels,
            float zPixels,
            float yawDegrees,
            float scale) {
    }
}
