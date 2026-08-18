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
import net.ronm19.wolfism.client.model.LunarAdultWolfModel;
import net.ronm19.wolfism.client.model.LunarBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.LunarWolfRenderState;
import net.ronm19.wolfism.entity.custom.LunarWolf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Lunar renderer with vanilla collar/armor and a cold moonlight beacon-style beam. */
public final class LunarWolfRenderer extends AgeableMobRenderer<LunarWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "textures/entity/wolf/lunar_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "textures/entity/wolf/lunar_wolf_baby.png");
    private static final int MOONBEAM_COLOR = 0xFFA59BFF;
    private static final float CORE_RADIUS = 0.075F;
    private static final float GLOW_RADIUS = 0.165F;
    private final WolfRenderer vanillaStateExtractor;

    public LunarWolfRenderer(EntityRendererProvider.Context context) {
        super(context,
                new LunarAdultWolfModel(context.bakeLayer(LunarAdultWolfModel.LAYER_LOCATION)),
                new LunarBabyWolfModel(context.bakeLayer(LunarBabyWolfModel.LAYER_LOCATION)),
                0.5F);
        this.vanillaStateExtractor = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override public LunarWolfRenderState createRenderState() { return new LunarWolfRenderState(); }

    @Override
    public void extractRenderState(LunarWolf entity, WolfRenderState base, float partialTick) {
        this.vanillaStateExtractor.extractRenderState(entity, base, partialTick);
        if (!(base instanceof LunarWolfRenderState state)) return;
        state.nightEmpowered = entity.isNightEmpowered();
        state.beamActive = entity.isLunarBeamActive();
        state.shieldActive = entity.isMoonShieldActive();
        state.dreamstepActive = entity.isDreamstepActive();
        state.howlActive = entity.isMoonlitHowlActive();
        state.lunarCycle = entity.tickCount + partialTick;
        state.lunarIntensity = entity.isInDirectMoonlight() ? 1.0F : (state.nightEmpowered ? 0.65F : 0.22F);

        state.beamHasEndpoint = false;
        state.beamStartY = entity.isBaby() ? 0.42F : 0.62F;
        state.beamDx = state.beamDy = state.beamDz = state.beamLength = 0.0F;
        if (state.beamActive) {
            int id = entity.getLunarBeamVisualTargetId();
            Entity target = id == 0 ? null : entity.level().getEntity(id);
            if (target != null) {
                Vec3 start = entity.position().add(0.0D, state.beamStartY, 0.0D);
                Vec3 raw = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D).subtract(start);
                double length = Math.min(20.0D, raw.length());
                if (length > 0.05D) {
                    Vec3 dir = raw.normalize();
                    state.beamDx=(float)dir.x; state.beamDy=(float)dir.y; state.beamDz=(float)dir.z;
                    state.beamLength=(float)length; state.beamHasEndpoint=true;
                }
            }
        }
    }

    @Override
    public void submit(WolfRenderState base, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.submit(base, poseStack, collector, cameraState);
        if (!(base instanceof LunarWolfRenderState state) || !state.beamActive || !state.beamHasEndpoint || state.beamLength <= 0.05F) return;
        Vector3f dir = new Vector3f(state.beamDx, state.beamDy, state.beamDz);
        if (dir.lengthSquared() < 1.0E-5F) return;
        dir.normalize();
        int beamHeight = Math.max(1, (int)Math.ceil(state.beamLength));
        float scale = state.beamLength / beamHeight;
        poseStack.pushPose();
        poseStack.translate(0.0F, state.beamStartY, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F,1.0F,0.0F), dir));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        poseStack.scale(1.0F, scale, 1.0F);
        BeaconRenderer.submitBeaconBeam(
                poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.lunarCycle * 0.08F,
                0, beamHeight, MOONBEAM_COLOR, CORE_RADIUS, GLOW_RADIUS);
        poseStack.popPose();
    }

    @Override public Identifier getTextureLocation(WolfRenderState state) { return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE; }
}
