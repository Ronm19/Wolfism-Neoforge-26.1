package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.SolarWolfRenderState;

/** Vanilla adult wolf geometry with Solar-specific celestial combat poses. */
public final class SolarAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "solar_wolf"), "main");

    private final ModelPart upperBody;

    public SolarAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)), 64, 32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof SolarWolfRenderState solar) || state.isSitting) return;

        float pulse = Mth.sin(solar.solarCycle * 0.18F) * 0.04F * solar.solarIntensity;
        this.head.xRot += pulse;
        this.tail.yRot += Mth.sin(solar.solarCycle * 0.15F) * 0.08F * solar.solarIntensity;

        if (solar.sunbeamActive) {
            this.head.xRot -= 0.10F;
            this.body.xRot = (float) (Math.PI / 2.0) - 0.05F;
            this.upperBody.xRot = (float) (Math.PI / 2.0) - 0.10F;
            this.rightFrontLeg.xRot = -0.22F;
            this.leftFrontLeg.xRot = -0.22F;
        }

        if (solar.solarFlareActive) {
            this.body.xRot = (float) (Math.PI / 2.0) + 0.10F;
            this.upperBody.xRot = (float) (Math.PI / 2.0) + 0.16F;
            this.rightFrontLeg.xRot = 0.28F;
            this.leftFrontLeg.xRot = 0.28F;
            this.rightHindLeg.xRot = -0.20F;
            this.leftHindLeg.xRot = -0.20F;
            this.head.xRot += 0.12F;
        }

        if (solar.celestialDashActive) {
            this.body.xRot = (float) (Math.PI / 2.0) - 0.18F;
            this.upperBody.xRot = (float) (Math.PI / 2.0) - 0.24F;
            this.head.xRot -= 0.18F;
            this.rightFrontLeg.xRot = -0.70F;
            this.leftFrontLeg.xRot = -0.70F;
            this.rightHindLeg.xRot = 0.42F;
            this.leftHindLeg.xRot = 0.42F;
            this.tail.xRot = 0.42F;
        }

        if (solar.solarAscensionActive) {
            this.head.xRot -= 0.42F;
            this.body.xRot = (float) (Math.PI / 2.0) + 0.08F;
            this.upperBody.xRot = (float) (Math.PI / 2.0) + 0.15F;
            this.rightFrontLeg.xRot = 0.12F;
            this.leftFrontLeg.xRot = 0.12F;
            this.tail.xRot = 0.28F;
            this.tail.yRot *= 0.25F;
        }
    }
}
