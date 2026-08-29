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
import net.ronm19.wolfism.client.renderer.state.GoldenWolfRenderState;

/** Vanilla-compatible Golden Wolf model with utility-state poses. */
public final class GoldenAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "golden_wolf"),
            "main");

    private final ModelPart upperBody;

    public GoldenAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)), 64, 32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof GoldenWolfRenderState golden) || state.isSitting) {
            return;
        }

        // The shiny presence gets a restrained, proud tail motion rather than
        // turning the wolf into a permanently hyperactive particle mascot.
        this.tail.yRot += Mth.sin(golden.goldenCycle * 0.14F) * 0.055F;

        if (golden.fortuneDigActive) {
            this.head.xRot += 0.52F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) + 0.08F;
            this.tail.xRot = 0.58F;
        }
        if (golden.radiantShareActive) {
            this.head.xRot -= 0.08F;
            this.tail.yRot += Mth.sin(golden.goldenCycle * 0.45F) * 0.18F;
        }
        if (golden.goldenBarrierActive) {
            this.head.xRot -= 0.16F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.05F;
            this.tail.xRot = 0.24F;
        }
        if (golden.blessingActive) {
            this.head.xRot -= 0.30F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.10F;
            this.tail.yRot *= 0.30F;
        }
    }
}
