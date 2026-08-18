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
import net.ronm19.wolfism.client.renderer.state.LunarWolfRenderState;

/** Vanilla adult wolf geometry with restrained lunar ability poses. */
public final class LunarAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "lunar_wolf"), "main");
    private final ModelPart upperBody;

    public LunarAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)), 64, 32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof LunarWolfRenderState lunar) || state.isSitting) return;
        float drift = Mth.sin(lunar.lunarCycle * 0.13F) * 0.035F * lunar.lunarIntensity;
        this.head.xRot += drift;
        this.tail.yRot += Mth.sin(lunar.lunarCycle * 0.11F) * 0.07F * lunar.lunarIntensity;

        if (lunar.beamActive) {
            this.head.xRot -= 0.08F;
            this.upperBody.xRot = (float)(Math.PI / 2.0) - 0.08F;
            this.rightFrontLeg.xRot = -0.18F;
            this.leftFrontLeg.xRot = -0.18F;
        }
        if (lunar.shieldActive) {
            this.head.xRot += 0.10F;
            this.upperBody.xRot = (float)(Math.PI / 2.0) + 0.08F;
            this.tail.yRot *= 0.35F;
        }
        if (lunar.dreamstepActive) {
            this.body.xRot = (float)(Math.PI / 2.0) - 0.16F;
            this.upperBody.xRot = (float)(Math.PI / 2.0) - 0.20F;
            this.rightFrontLeg.xRot = -0.58F;
            this.leftFrontLeg.xRot = -0.58F;
            this.rightHindLeg.xRot = 0.35F;
            this.leftHindLeg.xRot = 0.35F;
        }
        if (lunar.howlActive) {
            this.head.xRot -= 0.62F;
            this.upperBody.xRot = (float)(Math.PI / 2.0) + 0.10F;
            this.tail.xRot = 0.30F;
            this.tail.yRot *= 0.15F;
        }
    }
}
