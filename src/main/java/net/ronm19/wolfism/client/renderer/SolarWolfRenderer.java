package net.ronm19.wolfism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.SolarAdultWolfModel;
import net.ronm19.wolfism.client.model.SolarBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.SolarWolfRenderState;
import net.ronm19.wolfism.entity.custom.SolarWolf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Solar renderer retaining vanilla collar/armor layers with custom ability poses.
 * Sunbeam uses vanilla's BeaconRenderer geometry for a true luminous core/glow,
 * while server particles remain the charge, edge sparkle, and impact polish.
 */
public final class SolarWolfRenderer extends AgeableMobRenderer<SolarWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/solar_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/solar_wolf_baby.png");

    // Warm white-gold rather than beacon-white. ARGB is consumed directly by BeaconRenderer.
    private static final int SUNBEAM_COLOR = 0xFFFFD45A;
    private static final float SUNBEAM_CORE_RADIUS = 0.085F;
    private static final float SUNBEAM_GLOW_RADIUS = 0.185F;

    private final WolfRenderer vanillaStateExtractor;

    public SolarWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new SolarAdultWolfModel(context.bakeLayer(SolarAdultWolfModel.LAYER_LOCATION)),
                new SolarBabyWolfModel(context.bakeLayer(SolarBabyWolfModel.LAYER_LOCATION)),
                0.5F);
        this.vanillaStateExtractor = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public SolarWolfRenderState createRenderState() {
        return new SolarWolfRenderState();
    }

    @Override
    public void extractRenderState(SolarWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaStateExtractor.extractRenderState(entity, baseState, partialTick);
        if (!(baseState instanceof SolarWolfRenderState state)) return;

        state.directSunlight = entity.isInDirectSunlight();
        state.solarFlareActive = entity.isSolarFlareActive();
        state.sunbeamActive = entity.isSunbeamActive();
        state.celestialDashActive = entity.isCelestialDashing();
        state.solarAscensionActive = entity.isSolarAscensionActive();
        state.solarCycle = entity.tickCount + partialTick;
        state.solarIntensity = state.directSunlight ? 1.0F : 0.35F;

        // Render states must contain everything submit() needs. Resolve the synchronized
        // target here rather than touching the entity during the later submission phase.
        state.sunbeamHasEndpoint = false;
        state.sunbeamStartY = entity.isBaby() ? 0.42F : 0.62F;
        state.sunbeamDx = 0.0F;
        state.sunbeamDy = 0.0F;
        state.sunbeamDz = 0.0F;
        state.sunbeamLength = 0.0F;

        if (state.sunbeamActive) {
            int targetId = entity.getSunbeamVisualTargetId();
            Entity target = targetId == 0 ? null : entity.level().getEntity(targetId);
            if (target != null) {
                Vec3 start = entity.position().add(0.0D, state.sunbeamStartY, 0.0D);
                Vec3 raw = target.position()
                        .add(0.0D, target.getBbHeight() * 0.55D, 0.0D)
                        .subtract(start);
                double length = Math.min(18.0D, raw.length());
                if (length > 0.05D) {
                    Vec3 direction = raw.normalize();
                    state.sunbeamDx = (float) direction.x;
                    state.sunbeamDy = (float) direction.y;
                    state.sunbeamDz = (float) direction.z;
                    state.sunbeamLength = (float) length;
                    state.sunbeamHasEndpoint = true;
                }
            }
        }
    }

    @Override
    public void submit(
            WolfRenderState baseState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        super.submit(baseState, poseStack, collector, cameraState);
        if (!(baseState instanceof SolarWolfRenderState state)
                || !state.sunbeamActive
                || !state.sunbeamHasEndpoint
                || state.sunbeamLength <= 0.05F) return;

        submitSunbeam(state, poseStack, collector);
    }

    private static void submitSunbeam(
            SolarWolfRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector) {
        Vector3f direction = new Vector3f(state.sunbeamDx, state.sunbeamDy, state.sunbeamDz);
        if (direction.lengthSquared() < 1.0E-5F) return;
        direction.normalize();

        int beamHeight = Math.max(1, (int) Math.ceil(state.sunbeamLength));
        float exactLengthScale = state.sunbeamLength / beamHeight;

        poseStack.pushPose();
        poseStack.translate(0.0F, state.sunbeamStartY, 0.0F);

        // Vanilla beacon geometry is authored along local +Y. Rotate +Y onto our
        // wolf->target vector so the same renderer becomes an arbitrary 3D sun laser.
        Quaternionf beamRotation = new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F), direction);
        poseStack.mulPose(beamRotation);

        // BeaconRenderer centers its helper around (+0.5, +0.5) in local X/Z.
        // Counter-offset keeps the beam's core centered exactly on Solar Wolf's muzzle.
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        poseStack.scale(1.0F, exactLengthScale, 1.0F);

        BeaconRenderer.submitBeaconBeam(
                poseStack,
                collector,
                BeaconRenderer.BEAM_LOCATION,
                1.0F,
                state.solarCycle * 0.12F,
                0,
                beamHeight,
                SUNBEAM_COLOR,
                SUNBEAM_CORE_RADIUS,
                SUNBEAM_GLOW_RADIUS);

        poseStack.popPose();
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
