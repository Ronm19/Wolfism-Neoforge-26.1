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
import net.ronm19.wolfism.client.renderer.state.CherryWolfRenderState;

/** Vanilla-compatible Cherry Wolf model with clear support-state poses. */
public final class CherryAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "cherry_wolf"),
            "main");

    private final ModelPart upperBody;

    public CherryAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(
                AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)),
                64,
                32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof CherryWolfRenderState cherry) || state.isSitting) {
            return;
        }

        // Gentle baseline tail motion keeps Cherry expressive without turning the
        // support wolf into a constant visual distraction.
        this.tail.yRot += Mth.sin(cherry.cherryCycle * 0.13F) * 0.07F;

        if (cherry.petalAidActive) {
            this.head.xRot += 0.18F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.04F;
            this.tail.yRot += Mth.sin(cherry.cherryCycle * 0.30F) * 0.12F;
        }
        if (cherry.cherryBlossomBurstActive) {
            this.head.xRot -= 0.30F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) + 0.08F;
            this.tail.xRot = 0.24F;
        }
        if (cherry.bloomingPathActive) {
            this.head.xRot -= 0.08F;
            this.tail.yRot += Mth.sin(cherry.cherryCycle * 0.52F) * 0.20F;
        }
        if (cherry.sakuraSanctuaryActive) {
            this.head.xRot -= 0.38F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.08F;
            this.tail.yRot *= 0.30F;
        }
    }
}
