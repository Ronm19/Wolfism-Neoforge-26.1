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
import net.ronm19.wolfism.client.renderer.state.AngelWolfRenderState;

/** Baby Angel Wolf with the same Phantom-derived two-piece wing animation. */
public final class AngelBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "angel_wolf_baby"),
            "main");

    private static final float LEFT_WING_FLIGHT_X = 3.0F;
    private static final float RIGHT_WING_FLIGHT_X = -3.0F;
    private static final float WING_FLIGHT_Y = 17.0F;
    private static final float WING_FLIGHT_Z = -3.1F;

    private static final float SITTING_WING_LOCAL_X = 3.0F;
    private static final float SITTING_WING_LOCAL_Y = -2.0F;
    private static final float SITTING_WING_LOCAL_Z = -3.1F;
    private static final float SITTING_WING_FOLD_Z = 0.56F;

    private static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    private static final float FLAP_AMPLITUDE_DEGREES = 16.0F;

    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;

    public AngelBabyWolfModel(ModelPart root) {
        super(root);
        this.rightWing = root.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.leftWing = root.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = new CubeDeformation(0.0F);

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 26)
                        .addBox(-2.99F, -3.25F, -3.0F, 6.0F, 5.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(28, 22)
                        .addBox(-1.5F, -0.24F, -5.0F, 3.0F, 2.0F, 2.0F, none)
                        .texOffs(16, 36)
                        .addBox(1.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, none)
                        .texOffs(38, 5)
                        .addBox(-3.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, none),
                PartPose.offset(0.0F, 18.25F, -4.0F));

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 14)
                        .addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F, none),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        PartDefinition rightWing = root.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(28, 14)
                        .mirror()
                        .addBox(-6.0F, 0.0F, 1.1F, 6.0F, 2.0F, 6.0F, none)
                        .mirror(false),
                PartPose.offset(RIGHT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z));
        rightWing.addOrReplaceChild(
                "right_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-13.0F, 0.0F, 1.1F, 13.0F, 1.0F, 6.0F, none)
                        .mirror(false),
                PartPose.offset(-6.0F, 0.0F, 0.0F));

        PartDefinition leftWing = root.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(22, 26)
                        .addBox(0.0F, 0.0F, 1.05F, 6.0F, 2.0F, 6.0F, none),
                PartPose.offset(LEFT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z));
        leftWing.addOrReplaceChild(
                "left_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 7)
                        .addBox(0.0F, 0.0F, 1.05F, 13.0F, 1.0F, 6.0F, none),
                PartPose.offset(6.0F, 0.0F, 0.0F));

        /*
         * The prepared baby texture repeats the same clean fur pattern across
         * its small-leg islands. Keep all four legs on the known-good (0,36)
         * island so the model cannot accidentally sample the gold halo pixels.
         */
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 36)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, none);
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(1.5F, 21.0F, 3.0F));
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.5F, 21.0F, 3.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(1.5F, 21.0F, -3.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.5F, 21.0F, -3.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 19.0F, 3.0F, -0.5236F, 0.0F, 0.0F));
        tail.addOrReplaceChild(
                "tail_rotation",
                CubeListBuilder.create()
                        .texOffs(0, 36)
                        .addBox(-1.0F, -5.7F, -1.0F, 2.0F, 6.0F, 2.0F, none),
                PartPose.offsetAndRotation(0.0F, -0.6F, 0.2F, -3.1F, 0.0F, 0.0F));

        // The baby texture's gold square ring is a 5x1x5 unwrap at (22,34).
        root.addOrReplaceChild(
                "halo",
                CubeListBuilder.create()
                        .texOffs(22, 34)
                        .addBox(
                                -2.5F,
                                -0.5F,
                                -2.5F,
                                5.0F,
                                0.10F,
                                5.0F,
                                new CubeDeformation(0.65F, 0.0F, 0.65F)),
                // Baby ears had even less clearance than the adult model.
                PartPose.offset(0.0F, 8.0F, -4.5F));




        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        if (!(state instanceof AngelWolfRenderState angelState)) {
            this.applyGroundedWingPose(state);
            return;
        }

        if (state.isSitting) {
            this.applyGroundedWingPose(state);
        } else if (angelState.flightBlend > 0.001F) {
            this.applyFlightPose(angelState);
        } else {
            this.applyGroundedWingPose(state);
        }
    }

    private void applyFlightPose(AngelWolfRenderState state) {
        this.restoreFlightWingRoots();

        float anim = state.flapTime
                * FLAP_DEGREES_PER_TICK
                * (float) (Math.PI / 180.0D);
        float flap = Mth.cos(anim)
                * FLAP_AMPLITUDE_DEGREES
                * (float) (Math.PI / 180.0D);
        float blend = Mth.clamp(state.flightBlend, 0.0F, 1.0F);

        this.leftWing.zRot = Mth.lerp(blend, 0.05F, flap);
        this.leftWingTip.zRot = Mth.lerp(blend, 0.02F, flap);
        this.rightWing.zRot = Mth.lerp(blend, -0.05F, -flap);
        this.rightWingTip.zRot = Mth.lerp(blend, -0.02F, -flap);
        this.leftWing.yRot = 0.0F;
        this.rightWing.yRot = 0.0F;

        this.rightFrontLeg.xRot = Mth.lerp(blend, this.rightFrontLeg.xRot, -0.12F);
        this.leftFrontLeg.xRot = Mth.lerp(blend, this.leftFrontLeg.xRot, -0.12F);
        this.rightHindLeg.xRot = Mth.lerp(blend, this.rightHindLeg.xRot, 0.22F);
        this.leftHindLeg.xRot = Mth.lerp(blend, this.leftHindLeg.xRot, 0.22F);
    }

    private void applyGroundedWingPose(WolfRenderState state) {
        if (state.isSitting) {
            this.attachWingsToSittingBody();
            return;
        }

        this.restoreFlightWingRoots();
        this.leftWing.zRot = 0.05F;
        this.leftWingTip.zRot = 0.02F;
        this.rightWing.zRot = -0.05F;
        this.rightWingTip.zRot = -0.02F;
    }

    private void attachWingsToSittingBody() {
        this.attachWingToBody(this.leftWing, SITTING_WING_LOCAL_X, SITTING_WING_FOLD_Z);
        this.attachWingToBody(this.rightWing, -SITTING_WING_LOCAL_X, -SITTING_WING_FOLD_Z);

        this.leftWingTip.xRot = 0.0F;
        this.leftWingTip.yRot = 0.0F;
        this.leftWingTip.zRot = 0.05F;
        this.rightWingTip.xRot = 0.0F;
        this.rightWingTip.yRot = 0.0F;
        this.rightWingTip.zRot = -0.05F;
    }

    private void attachWingToBody(ModelPart wing, float localX, float foldZ) {
        float cosX = Mth.cos(this.body.xRot);
        float sinX = Mth.sin(this.body.xRot);
        float rotatedY = SITTING_WING_LOCAL_Y * cosX - SITTING_WING_LOCAL_Z * sinX;
        float rotatedZ = SITTING_WING_LOCAL_Y * sinX + SITTING_WING_LOCAL_Z * cosX;

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
