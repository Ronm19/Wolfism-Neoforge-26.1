package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
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

/** User-authored 64x64 Bee Wolf baby geometry adapted to vanilla BabyWolfModel. */
public final class BeeBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "bee_wolf_baby"),
            "main");

    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public BeeBabyWolfModel(ModelPart root) {
        super(root);
        ModelPart wingAnchor = this.body.getChild("wing_anchor");
        this.rightWing = wingAnchor.getChild("right_wing");
        this.leftWing = wingAnchor.getChild("left_wing");
    }

    public static LayerDefinition createBeeBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = new CubeDeformation(0.0F);

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 24)
                        .addBox(-2.99F, -3.25F, -3.0F, 6.0F, 5.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(28, 0)
                        .addBox(-1.5F, -0.24F, -5.0F, 3.0F, 2.0F, 2.0F, none)
                        .texOffs(0, 34)
                        .addBox(1.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, none)
                        .texOffs(0, 34)
                        .addBox(-3.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, none),
                PartPose.offset(0.0F, 18.25F, -4.0F));

        // Reparented from Blockbench root siblings so antennae rotate with the head.
        head.addOrReplaceChild(
                "left_antenna",
                CubeListBuilder.create()
                        .texOffs(28, 4)
                        .addBox(1.4F, 1.5F, -1.0F, 1.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, -5.75F, -5.0F));
        head.addOrReplaceChild(
                "right_antenna",
                CubeListBuilder.create()
                        .texOffs(30, 9)
                        .addBox(-1.4F, 1.5F, -1.0F, 1.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, -5.75F, -5.0F));

        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F, none),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        // Blockbench placed the baby wing plane at y=17 with a pivot six pixels
        // above it. Moving the pivot to the actual attachment plane keeps the
        // exact rendered geometry while giving the flap a natural hinge.
        PartDefinition wingAnchor = body.addOrReplaceChild(
                "wing_anchor",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -2.0F, 0.0F));
        wingAnchor.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(0, 18)
                        .addBox(-9.5F, -0.1F, -2.1F, 9.0F, 0.0F, 6.0F, none),
                PartPose.offset(-1.5F, 0.0F, 0.0F));
        wingAnchor.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(0, 18)
                        .mirror()
                        .addBox(0.5F, -0.1F, -2.0F, 9.0F, 0.0F, 6.0F, none)
                        .mirror(false),
                PartPose.offset(1.5F, 0.0F, 0.0F));

        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(30, 29)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, none);
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(1.5F, 21.0F, 3.0F));
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.5F, 21.0F, 3.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(1.5F, 21.0F, -3.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.5F, 21.0F, -3.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 19.0F, 3.0F, ((float) (Math.PI / 2.0)), 0.0F, 0.0F));
        tail.addOrReplaceChild(
                "tail_rotation",
                CubeListBuilder.create()
                        .texOffs(22, 24)
                        .addBox(-1.0F, -5.7F, -1.0F, 2.0F, 6.0F, 2.0F, none)
                        .texOffs(28, 9)
                        .addBox(-0.2F, -7.7F, -0.5F, 0.0F, 2.0F, 1.0F, none),
                PartPose.offsetAndRotation(0.0F, -0.6F, 0.2F, -3.1F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        animateBeeWings(state);
    }

    private void animateBeeWings(WolfRenderState state) {
        this.rightWing.xRot = 0.0F;
        this.leftWing.xRot = 0.0F;
        this.rightWing.yRot = 0.0F;
        this.leftWing.yRot = 0.0F;
        this.rightWing.zRot = 0.0F;
        this.leftWing.zRot = 0.0F;

        if (state.isSitting) {
            this.rightWing.yRot = 0.28F;
            this.leftWing.yRot = -0.28F;
            return;
        }

        boolean shouldFlap = state instanceof BeeWolfRenderState beeState
                && beeState.catchUpFlying;
        if (!shouldFlap) {
            return;
        }

        float speed = state.ageInTicks * 120.32113F * ((float) (Math.PI / 180.0));
        float flap = Mth.cos(speed) * ((float) Math.PI) * 0.15F;
        this.rightWing.zRot = flap;
        this.leftWing.zRot = -flap;
    }
}
