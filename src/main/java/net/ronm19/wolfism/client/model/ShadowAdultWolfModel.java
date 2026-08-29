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
import net.ronm19.wolfism.client.renderer.state.ShadowWolfRenderState;

/** Vanilla-compatible Shadow Wolf model with subtle ability-specific combat poses. */
public final class ShadowAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "shadow_wolf"),
            "main");

    private final ModelPart upperBody;

    public ShadowAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)), 64, 32);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof ShadowWolfRenderState shadow) || state.isSitting) {
            return;
        }

        // A faint tail drift keeps the supernatural silhouette alive even while idle.
        this.tail.yRot += Mth.sin(shadow.shadowCycle * 0.15F) * 0.045F;

        if (shadow.voidDashActive) {
            this.head.xRot -= 0.28F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.10F;
            this.tail.xRot = 0.20F;
        }
        if (shadow.shadowBladesActive) {
            this.head.xRot -= 0.10F;
            this.tail.yRot += Mth.sin(shadow.shadowCycle * 0.42F) * 0.12F;
        }
        if (shadow.duskVeilActive) {
            this.head.xRot += 0.13F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) + 0.05F;
            this.tail.yRot *= 0.35F;
        }
        if (shadow.shadowAssassinActive) {
            this.head.xRot -= 0.38F;
            this.upperBody.xRot = (float) (Math.PI / 2.0D) - 0.16F;
            this.tail.xRot = 0.30F;
        }
    }
}
