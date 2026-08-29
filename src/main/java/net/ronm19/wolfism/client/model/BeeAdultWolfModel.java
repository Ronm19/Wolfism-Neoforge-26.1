package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.BeeWolfRenderState;

/**
 * Bee Wolf adult model.
 *
 * <p>The wolf geometry/UVs are the user's 64x64 Blockbench export, adapted to
 * the current vanilla AdultWolfModel hierarchy so normal wolf walking, sitting,
 * tail, wet-shake, collar, and armor behavior remain intact. Wings are parented
 * to the torso through a neutralizing anchor so they follow body pose changes
 * while keeping the Blockbench neutral pose.</p>
 */
public final class BeeAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "bee_wolf"),
            "main");

    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public BeeAdultWolfModel(ModelPart root) {
        super(root);
        ModelPart wingAnchor = this.body.getChild("wing_anchor");
        this.rightWing = wingAnchor.getChild("right_wing");
        this.leftWing = wingAnchor.getChild("left_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation deformation = new CubeDeformation(0.0F);

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(-1.0F, 13.5F, -7.0F));

        PartDefinition realHead = head.addOrReplaceChild(
                "real_head",
                CubeListBuilder.create()
                        .texOffs(24, 25)
                        .addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F, deformation)
                        .texOffs(8, 35)
                        .addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, deformation)
                        .texOffs(8, 35)
                        .addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, deformation)
                        .texOffs(0, 28)
                        .addBox(-0.5F, -0.02F, -5.0F, 3.0F, 3.0F, 4.0F, deformation),
                PartPose.ZERO);

        // Blockbench exported these as root siblings. Reparenting them under the
        // real head preserves the exact neutral coordinates while making them
        // follow head look + wet-shake animation correctly.
        realHead.addOrReplaceChild(
                "left_antenna",
                CubeListBuilder.create()
                        .texOffs(38, 0)
                        .addBox(2.0F, -2.6F, -3.0F, 1.0F, 2.0F, 3.0F, deformation),
                PartPose.offset(1.0F, -1.0F, -2.0F));
        realHead.addOrReplaceChild(
                "right_antenna",
                CubeListBuilder.create()
                        .texOffs(38, 5)
                        .addBox(-2.0F, -2.7F, -3.0F, 1.0F, 2.0F, 3.0F, deformation),
                PartPose.offset(1.0F, -1.0F, -2.0F));

        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 13)
                        .addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, deformation),
                PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, ((float) (Math.PI / 2.0)), 0.0F, 0.0F));

        // The adult wolf torso has a built-in +90 degree X rotation. This anchor
        // cancels that neutral rotation for the wings, but remains a child of the
        // torso so sitting/body pose changes naturally carry the wings with it.
        PartDefinition wingAnchor = body.addOrReplaceChild(
                "wing_anchor",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -2.0F, 3.0F, -((float) (Math.PI / 2.0)), 0.0F, 0.0F));
        wingAnchor.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(24, 19)
                        .addBox(-9.5F, -0.1F, -0.1F, 9.0F, 0.0F, 6.0F, deformation),
                PartPose.offset(-1.5F, 0.0F, 0.0F));
        wingAnchor.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(24, 19)
                        .mirror()
                        .addBox(0.5F, -0.1F, 0.0F, 9.0F, 0.0F, 6.0F, deformation)
                        .mirror(false),
                PartPose.offset(1.5F, 0.0F, 0.0F));

        root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, deformation),
                PartPose.offsetAndRotation(-1.0F, 14.0F, -3.0F, ((float) (Math.PI / 2.0)), 0.0F, 0.0F));

        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(14, 28)
                .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, deformation);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(0.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5F, 16.0F, -4.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(0.5F, 16.0F, -4.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-1.0F, 12.0F, 8.0F, ((float) (Math.PI / 5.0)), 0.0F, 0.0F));
        tail.addOrReplaceChild(
                "real_tail",
                CubeListBuilder.create()
                        .texOffs(30, 9)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, deformation)
                        // Tiny zero-thickness stinger from the user's model.
                        .texOffs(22, 28)
                        .addBox(1.0F, 8.0F, -0.5F, 0.0F, 2.0F, 1.0F, deformation),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        animateBeeWings(state);
    }

    private void animateBeeWings(WolfRenderState state) {
        // Always write a full pose so no rotation can leak between render states.
        this.rightWing.xRot = 0.0F;
        this.leftWing.xRot = 0.0F;
        this.rightWing.yRot = 0.0F;
        this.leftWing.yRot = 0.0F;
        this.rightWing.zRot = 0.0F;
        this.leftWing.zRot = 0.0F;

        if (state.isSitting) {
            // Gentle backward fold while sitting.
            this.rightWing.yRot = 0.28F;
            this.leftWing.yRot = -0.28F;
            return;
        }

        boolean shouldFlap = state instanceof BeeWolfRenderState beeState
                && beeState.catchUpFlying;
        if (!shouldFlap) {
            return;
        }

        // Vanilla Bee wing cadence/formula, applied to the Wolfism wings.
        float speed = state.ageInTicks * 120.32113F * ((float) (Math.PI / 180.0));
        float flap = Mth.cos(speed) * ((float) Math.PI) * 0.15F;
        this.rightWing.zRot = flap;
        this.leftWing.zRot = -flap;
    }
}
