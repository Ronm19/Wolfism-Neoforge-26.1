package net.ronm19.wolfism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.wolfism.entity.custom.BoneShardProjectile;

/**
 * Skeleton Wolf's Bone Shot / Skeletal Volley projectile renderer.
 *
 * <p>The projectile itself supplies the normal vanilla Bone item. We rotate the
 * render pose by 90 degrees in the camera-facing plane before delegating to
 * Minecraft's normal {@link ThrownItemRenderer}. This keeps the vanilla Bone
 * artwork while making it travel sideways instead of standing vertically.</p>
 */
public final class BoneShardRenderer extends ThrownItemRenderer<BoneShardProjectile> {
    public BoneShardRenderer(EntityRendererProvider.Context context) {
        super(context, 0.70F, false);
    }

    @Override
    public void submit(
            ThrownItemRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {

        poseStack.pushPose();

        // ThrownItemRenderer billboards the item toward the camera. Rotating
        // around Z therefore turns the 2D Bone sprite sideways on screen.
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));

        super.submit(state, poseStack, submitNodeCollector, camera);
        poseStack.popPose();
    }
}
