package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.WaterWolfRenderState;

/** Vanilla adult wolf geometry with a Water-Wolf-specific swimming pose. */
public final class WaterAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "water_wolf"),
            "main");

    private final ModelPart upperBody;

    public WaterAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)), 64, 32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        if (!(state instanceof WaterWolfRenderState water) || !water.isSwimmingInWater) {
            return;
        }

        float amount = Mth.clamp(water.swimAmount, 0.0F, 1.0F);
        float cycle = water.swimCycle * 0.58F;
        float frontStroke = Mth.sin(cycle) * 0.78F * amount;
        float hindStroke = Mth.sin(cycle + (float) Math.PI) * 0.66F * amount;

        // Adult wolf body geometry is authored around PI/2 on X. The small
        // offsets below create a smooth dolphin-like undulation without changing
        // the familiar vanilla silhouette.
        this.body.xRot = (float) (Math.PI / 2.0) + Mth.sin(cycle * 0.50F) * 0.055F * amount;
        this.upperBody.xRot = (float) (Math.PI / 2.0) + Mth.sin(cycle * 0.50F + 0.65F) * 0.045F * amount;

        this.rightFrontLeg.xRot = -0.28F + frontStroke;
        this.leftFrontLeg.xRot = -0.28F - frontStroke;
        this.rightHindLeg.xRot = -0.18F + hindStroke;
        this.leftHindLeg.xRot = -0.18F - hindStroke;

        this.tail.xRot = 0.78F + Mth.cos(cycle * 0.72F) * 0.13F * amount;
        this.tail.yRot = Mth.sin(cycle * 0.72F) * 0.48F * amount;
        this.head.xRot += -0.08F + Mth.sin(cycle * 0.50F) * 0.04F * amount;

        if (water.bubbleShelterActive) {
            this.tail.yRot *= 0.55F;
        }
    }
}
