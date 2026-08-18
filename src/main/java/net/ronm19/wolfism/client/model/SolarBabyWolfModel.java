package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.SolarWolfRenderState;

/** Vanilla baby wolf geometry with a subtle daylight Solar personality pose. */
public final class SolarBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "solar_wolf_baby"), "main");

    public SolarBabyWolfModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createSolarBodyLayer() {
        return BabyWolfModel.createBodyLayer();
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof SolarWolfRenderState solar) || state.isSitting) return;
        if (solar.directSunlight) {
            this.head.xRot += Mth.sin(solar.solarCycle * 0.16F) * 0.035F;
            this.tail.yRot += Mth.sin(solar.solarCycle * 0.20F) * 0.10F;
        }
    }
}
