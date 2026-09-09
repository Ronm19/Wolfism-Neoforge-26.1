package net.ronm19.wolfism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.CreatorWolfRenderState;
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Creator uses vanilla wolf geometry, rendered subtly larger than normal wolves.
 * Creator Beam reuses vanilla BeaconRenderer geometry as a thick arbitrary-3D beam.
 */
public final class CreatorWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/creator_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/creator_wolf_baby.png");

    // Stark white with a faint cool cast. Deliberately much larger than Solar/Lunar beams.
    private static final int CREATOR_BEAM_COLOR = 0xFFF4F4FF;
    private static final float CREATOR_BEAM_CORE_RADIUS = 0.145F;
    private static final float CREATOR_BEAM_GLOW_RADIUS = 0.310F;

    public CreatorWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CreatorWolfRenderState createRenderState() {
        return new CreatorWolfRenderState();
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }

    @Override
    public void extractRenderState(Wolf entity, WolfRenderState baseState, float partialTick) {
        super.extractRenderState(entity, baseState, partialTick);

        // Regular wolf < Creator < Dire Wolf (Dire remains 1.65x adult).
        baseState.scale *= baseState.isBaby ? 1.06F : 1.12F;

        if (!(entity instanceof CreatorWolf creator)
                || !(baseState instanceof CreatorWolfRenderState state)) {
            return;
        }

        state.creatorBeamActive = creator.isCreatorBeamActive();
        state.creatorBeamHasEndpoint = false;
        state.creatorCycle = creator.tickCount + partialTick;
        state.beamStartX = 0.0F;
        state.beamStartY = 0.0F;
        state.beamStartZ = 0.0F;
        state.beamDx = state.beamDy = state.beamDz = state.beamLength = 0.0F;

        if (!state.creatorBeamActive) return;

        int id = creator.getCreatorBeamVisualTargetId();
        Entity target = id == 0 ? null : creator.level().getEntity(id);
        if (target == null) return;

        Vec3 muzzle = creator.getEyePosition()
                .add(creator.getLookAngle().scale(0.40D));
        Vec3 relativeMuzzle = muzzle.subtract(creator.position());

        Vec3 raw = target.position()
                .add(0.0D, target.getBbHeight() * 0.55D, 0.0D)
                .subtract(muzzle);
        double length = Math.min(48.0D, raw.length());
        if (length <= 0.05D) return;

        Vec3 direction = raw.normalize();
        state.beamStartX = (float) relativeMuzzle.x;
        state.beamStartY = (float) relativeMuzzle.y;
        state.beamStartZ = (float) relativeMuzzle.z;
        state.beamDx = (float) direction.x;
        state.beamDy = (float) direction.y;
        state.beamDz = (float) direction.z;
        state.beamLength = (float) length;
        state.creatorBeamHasEndpoint = true;
    }

    @Override
    public void submit(
            WolfRenderState baseState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        super.submit(baseState, poseStack, collector, cameraState);

        if (!(baseState instanceof CreatorWolfRenderState state)
                || !state.creatorBeamActive
                || !state.creatorBeamHasEndpoint
                || state.beamLength <= 0.05F) {
            return;
        }

        Vector3f direction = new Vector3f(state.beamDx, state.beamDy, state.beamDz);
        if (direction.lengthSquared() < 1.0E-5F) return;
        direction.normalize();

        int beamHeight = Math.max(1, (int) Math.ceil(state.beamLength));
        float exactLengthScale = state.beamLength / beamHeight;

        poseStack.pushPose();
        poseStack.translate(state.beamStartX, state.beamStartY, state.beamStartZ);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F),
                direction));

        poseStack.translate(-0.5F, 0.0F, -0.5F);
        poseStack.scale(1.0F, exactLengthScale, 1.0F);

        BeaconRenderer.submitBeaconBeam(
                poseStack,
                collector,
                BeaconRenderer.BEAM_LOCATION,
                1.0F,
                state.creatorCycle * 0.14F,
                0,
                beamHeight,
                CREATOR_BEAM_COLOR,
                CREATOR_BEAM_CORE_RADIUS,
                CREATOR_BEAM_GLOW_RADIUS);

        poseStack.popPose();
    }
}
