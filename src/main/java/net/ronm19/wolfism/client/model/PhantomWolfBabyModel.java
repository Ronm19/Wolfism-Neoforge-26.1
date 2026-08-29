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
import net.ronm19.wolfism.client.renderer.state.PhantomWolfRenderState;
import net.ronm19.wolfism.entity.custom.PhantomWolf;

/** Baby Phantom Wolf using the user's latest 64x64 Blockbench geometry/UVs. */
public final class PhantomWolfBabyModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("wolfism", "phantom_wolf_baby"),
            "main");

    private static final float LEFT_WING_FLIGHT_X = 3.0F;
    private static final float RIGHT_WING_FLIGHT_X = -3.0F;
    private static final float WING_FLIGHT_Y = 17.0F;
    private static final float WING_FLIGHT_Z = -3.1F;

    /*
     * Baby body default pivot is (0,19,0) and its wing roots are
     * (+/-3,17,-3.1), so the body-local attachment is directly
     * (+/-3,-2,-3.1). Reusing that local attachment against the animated body
     * makes the already-good baby sitting pose carry the wings with it.
     */
    private static final float SITTING_WING_LOCAL_X = 3.0F;
    private static final float SITTING_WING_LOCAL_Y = -2.0F;
    private static final float SITTING_WING_LOCAL_Z = -3.1F;
    private static final float SITTING_WING_FOLD_Z = 0.66F;

    private final ModelPart rightWingTip;
    private final ModelPart rightWing;
    private final ModelPart leftWingTip;
    private final ModelPart leftWing;

    public PhantomWolfBabyModel(ModelPart root) {
        super(root);
        this.rightWing = root.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.leftWing = root.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 26)
                        .addBox(-2.99F, -3.25F, -3.0F, 6.0F, 5.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(28, 22)
                        .addBox(-1.5F, -0.24F, -5.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 36)
                        .addBox(1.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 5)
                        .addBox(-3.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 18.25F, -4.0F));

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 14)
                        .addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        // Same absolute geometry as the user's latest export, re-parented only
        // so the baby wing tip inherits the base rotation like vanilla Phantom.
        PartDefinition rightWing = root.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(28, 14)
                        .mirror()
                        .addBox(-6.0F, 0.0F, 1.1F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-3.0F, 17.0F, -3.1F));

        rightWing.addOrReplaceChild(
                "right_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-13.0F, 0.0F, 1.1F, 13.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-6.0F, 0.0F, 0.0F));

        PartDefinition leftWing = root.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(22, 26)
                        .addBox(0.0F, 0.0F, 1.05F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.0F, 17.0F, -3.1F));

        leftWing.addOrReplaceChild(
                "left_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 7)
                        .addBox(0.0F, 0.0F, 1.05F, 13.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(6.0F, 0.0F, 0.0F));

        root.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create()
                        .texOffs(30, 34)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, 21.0F, 3.0F));

        root.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create()
                        .texOffs(0, 36)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.5F, 21.0F, 3.0F));

        root.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create()
                        .texOffs(8, 36)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, 21.0F, -3.0F));

        root.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create()
                        .texOffs(38, 0)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.5F, 21.0F, -3.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                // Match vanilla BabyWolfModel's connected tail parent pose so
                // sitting/shaking animations rotate from the correct rump pivot.
                PartPose.offsetAndRotation(0.0F, 19.0F, 3.0F, -0.5236F, 0.0F, 0.0F));

        tail.addOrReplaceChild(
                "tail_rotation",
                CubeListBuilder.create()
                        .texOffs(22, 34)
                        .addBox(-1.0F, -5.7F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -0.6F, 0.2F, -3.1F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        if (!(state instanceof PhantomWolfRenderState phantomState)) {
            return;
        }

        if (state.isSitting) {
            this.applyGroundedWingPose(state);
        } else if (phantomState.isFlying) {
            this.applyPhantomFlightPose(phantomState);
        } else {
            this.applyGroundedWingPose(state);
        }
    }

    private void applyPhantomFlightPose(PhantomWolfRenderState state) {
        // IMPORTANT: restores the exact tested Blockbench flight pivots before
        // applying the existing baby flight animation.
        this.restoreFlightWingRoots();

        float anim = state.flapTime
                * PhantomWolf.FLAP_DEGREES_PER_TICK
                * (float) (Math.PI / 180.0D);
        float flapDegrees = state.isSwooping ? 9.0F : 16.0F;
        float flap = Mth.cos(anim) * flapDegrees * (float) (Math.PI / 180.0D);

        this.leftWing.zRot = flap;
        this.rightWing.zRot = -flap;
        this.leftWingTip.zRot = state.isSwooping ? flap * 0.55F : flap;
        this.rightWingTip.zRot = state.isSwooping ? -flap * 0.55F : -flap;
        this.leftWing.yRot = 0.0F;
        this.rightWing.yRot = 0.0F;

        this.head.xRot = 0.0F;

        // A slightly softer tuck keeps the baby silhouette readable.
        this.rightFrontLeg.xRot = -0.12F;
        this.leftFrontLeg.xRot = -0.12F;
        this.rightHindLeg.xRot = 0.24F;
        this.leftHindLeg.xRot = 0.24F;
    }

    private void applyGroundedWingPose(WolfRenderState state) {
        if (state.isSitting) {
            this.attachWingsToSittingBody();
        } else {
            this.restoreFlightWingRoots();

            this.leftWing.zRot = 0.07F;
            this.leftWingTip.zRot = 0.035F;
            this.rightWing.zRot = -0.07F;
            this.rightWingTip.zRot = -0.035F;
        }
    }

    private void attachWingsToSittingBody() {
        this.attachWingToBody(this.leftWing, SITTING_WING_LOCAL_X, SITTING_WING_FOLD_Z);
        this.attachWingToBody(this.rightWing, -SITTING_WING_LOCAL_X, -SITTING_WING_FOLD_Z);

        this.leftWingTip.xRot = 0.0F;
        this.leftWingTip.yRot = 0.0F;
        this.leftWingTip.zRot = 0.06F;

        this.rightWingTip.xRot = 0.0F;
        this.rightWingTip.yRot = 0.0F;
        this.rightWingTip.zRot = -0.06F;
    }

    private void attachWingToBody(ModelPart wing, float localX, float foldZ) {
        float cosX = Mth.cos(this.body.xRot);
        float sinX = Mth.sin(this.body.xRot);

        float rotatedY = SITTING_WING_LOCAL_Y * cosX
                - SITTING_WING_LOCAL_Z * sinX;
        float rotatedZ = SITTING_WING_LOCAL_Y * sinX
                + SITTING_WING_LOCAL_Z * cosX;

        float cosY = Mth.cos(this.body.yRot);
        float sinY = Mth.sin(this.body.yRot);

        float rotatedX = localX * cosY + rotatedZ * sinY;
        float finalZ = -localX * sinY + rotatedZ * cosY;

        wing.setPos(
                this.body.x + rotatedX,
                this.body.y + rotatedY,
                this.body.z + finalZ);

        wing.xRot = this.body.xRot;
        wing.yRot = this.body.yRot;
        wing.zRot = this.body.zRot + foldZ;
    }

    private void restoreFlightWingRoots() {
        this.leftWing.setPos(LEFT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z);
        this.rightWing.setPos(RIGHT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z);

        this.leftWing.xRot = 0.0F;
        this.leftWing.yRot = 0.0F;
        this.rightWing.xRot = 0.0F;
        this.rightWing.yRot = 0.0F;
    }
}