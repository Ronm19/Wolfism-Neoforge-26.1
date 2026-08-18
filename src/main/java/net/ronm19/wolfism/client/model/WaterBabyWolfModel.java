package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.WaterWolfRenderState;

/** Baby Water Wolf swimming animation using the vanilla baby wolf geometry. */
public final class WaterBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "water_wolf_baby"),
            "main");

    public WaterBabyWolfModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createWaterBodyLayer() {
        return BabyWolfModel.createBodyLayer();
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        if (!(state instanceof WaterWolfRenderState water) || !water.isSwimmingInWater) {
            return;
        }

        float amount = Mth.clamp(water.swimAmount, 0.0F, 1.0F);
        float cycle = water.swimCycle * 0.66F;
        float stroke = Mth.sin(cycle) * 0.72F * amount;

        this.body.xRot = Mth.sin(cycle * 0.50F) * 0.045F * amount;
        this.rightFrontLeg.xRot = -0.20F + stroke;
        this.leftFrontLeg.xRot = -0.20F - stroke;
        this.rightHindLeg.xRot = -0.12F - stroke * 0.85F;
        this.leftHindLeg.xRot = -0.12F + stroke * 0.85F;
        this.tail.xRot = 0.58F + Mth.cos(cycle * 0.70F) * 0.12F * amount;
        this.tail.yRot = Mth.sin(cycle * 0.70F) * 0.42F * amount;
        this.head.xRot += -0.05F + Mth.sin(cycle * 0.50F) * 0.035F * amount;
    }
}
