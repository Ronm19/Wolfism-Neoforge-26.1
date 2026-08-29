
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
import net.ronm19.wolfism.client.renderer.state.AngelWolfRenderState;

/**
 * Adult Angel Wolf visual model.
 *
 * <p>The wolf body keeps vanilla {@link AdultWolfModel} pivots/animation while
 * using the user's packed 64x64 Angel UV layout. The wings deliberately retain
 * Phantom-style base + tip geometry, because that is the geometry used by the
 * finished Blockbench design, but their animation is kept clean and graceful:
 * full Phantom-style cumulative flapping in the air, a quiet spread on the
 * ground, and a body-following fold while sitting.</p>
 */
public final class AngelAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "angel_wolf"),
            "main");

    private static final float LEFT_WING_FLIGHT_X = 3.0F;
    private static final float RIGHT_WING_FLIGHT_X = -3.0F;
    private static final float WING_FLIGHT_Y = 11.0F;
    private static final float WING_FLIGHT_Z = 0.2F;

    private static final float ADULT_BODY_DEFAULT_X_ROT = (float) (Math.PI / 2.0D);
    private static final float SITTING_WING_RELATIVE_X = 3.0F;
    private static final float SITTING_WING_RELATIVE_Y = -3.0F;
    private static final float SITTING_WING_RELATIVE_Z = -1.8F;
    private static final float SITTING_WING_FOLD_Z = 0.42F;

    // Vanilla Phantom cadence supplied by the user as the animation reference.
    private static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    private static final float FLAP_AMPLITUDE_DEGREES = 16.0F;

    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;

    public AngelAdultWolfModel(ModelPart root) {
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

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(-1.0F, 13.5F, -7.0F));

        head.addOrReplaceChild(
                "real_head",
                CubeListBuilder.create()
                        // User's Angel UV packing keeps the finished face here.
                        .texOffs(24, 42)
                        .addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F, none)
                        // Both ears intentionally share one painted UV island.
                        .texOffs(24, 36)
                        .addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, none)
                        .texOffs(24, 36)
                        .addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, none)
                        .texOffs(44, 0)
                        .addBox(-0.5F, -0.02F, -5.0F, 3.0F, 3.0F, 4.0F, none),
                PartPose.ZERO);

        root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(0, 20)
                        .addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, none),
                PartPose.offsetAndRotation(
                        -1.0F,
                        14.0F,
                        -3.0F,
                        (float) (Math.PI / 2.0D),
                        0.0F,
                        0.0F));

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 33)
                        .addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, none),
                PartPose.offsetAndRotation(
                        0.0F,
                        14.0F,
                        2.0F,
                        (float) (Math.PI / 2.0D),
                        0.0F,
                        0.0F));

        /*
         * The finished adult texture intentionally stacks both left/right wing
         * UVs. Geometry remains mirrored, but both sides read the same painted
         * wing-base and wing-tip islands.
         */
        PartDefinition rightWing = root.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(30, 31)
                        .mirror()
                        .addBox(-6.0F, 0.0F, -0.9F, 6.0F, 2.0F, 9.0F, none)
                        .mirror(false),
                PartPose.offset(RIGHT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z));

        rightWing.addOrReplaceChild(
                "right_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 10)
                        .mirror()
                        .addBox(-13.0F, 0.0F, -0.8F, 13.0F, 1.0F, 9.0F, none)
                        .mirror(false),
                PartPose.offset(-6.0F, 0.0F, 0.0F));

        PartDefinition leftWing = root.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(30, 31)
                        .addBox(0.0F, 0.0F, -1.2F, 6.0F, 2.0F, 9.0F, none),
                PartPose.offset(LEFT_WING_FLIGHT_X, WING_FLIGHT_Y, WING_FLIGHT_Z));

        leftWing.addOrReplaceChild(
                "left_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 10)
                        .addBox(0.0F, 0.0F, -1.2F, 13.0F, 1.0F, 9.0F, none),
                PartPose.offset(6.0F, 0.0F, 0.0F));

        // The adult workflow deliberately stacked all four leg UVs together.
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(44, 42)
                .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, none);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(0.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5F, 16.0F, -4.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(0.5F, 16.0F, -4.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -1.0F,
                        12.0F,
                        8.0F,
                        (float) (Math.PI / 5.0D),
                        0.0F,
                        0.0F));
        tail.addOrReplaceChild(
                "real_tail",
                CubeListBuilder.create()
                        .texOffs(16, 48)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, none),
                PartPose.ZERO);

        /*
         * Halo: the texture's gold island is a normal 5x1x5 box unwrap at
         * (25,52). The center pixels are transparent, so one thin box renders
         * as the exact square ring drawn in Blockbench.
         */
        root.addOrReplaceChild(
                "halo",
                CubeListBuilder.create()
                        .texOffs(25, 52)
                        .addBox(-2.5F, -0.5F, -2.5F, 5.0F, 1.0F, 5.0F, none),
                PartPose.offset(0.0F, 5.8F, -7.0F));


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

        /*
         * Same cumulative base + tip motion as vanilla Phantom wings, but
         * smoothly blended from Angel's normal spread-wing pose. This removes
         * the visible snap when the synchronized flight state arrives.
         */
        this.leftWing.zRot = Mth.lerp(blend, 0.06F, flap);
        this.leftWingTip.zRot = Mth.lerp(blend, 0.025F, flap);
        this.rightWing.zRot = Mth.lerp(blend, -0.06F, -flap);
        this.rightWingTip.zRot = Mth.lerp(blend, -0.025F, -flap);
        this.leftWing.yRot = 0.0F;
        this.rightWing.yRot = 0.0F;

        // Ease out the wolf walk cycle instead of popping the legs instantly.
        this.rightFrontLeg.xRot = Mth.lerp(blend, this.rightFrontLeg.xRot, -0.16F);
        this.leftFrontLeg.xRot = Mth.lerp(blend, this.leftFrontLeg.xRot, -0.16F);
        this.rightHindLeg.xRot = Mth.lerp(blend, this.rightHindLeg.xRot, 0.28F);
        this.leftHindLeg.xRot = Mth.lerp(blend, this.leftHindLeg.xRot, 0.28F);
    }

    private void applyGroundedWingPose(WolfRenderState state) {
        if (state.isSitting) {
            this.attachWingsToSittingBody();
            return;
        }

        this.restoreFlightWingRoots();
        // The Blockbench neutral pose is proudly spread, not tightly folded.
        this.leftWing.zRot = 0.06F;
        this.leftWingTip.zRot = 0.025F;
        this.rightWing.zRot = -0.06F;
        this.rightWingTip.zRot = -0.025F;
    }

    private void attachWingsToSittingBody() {
        this.attachWingToAnimatedBody(this.leftWing, SITTING_WING_RELATIVE_X, SITTING_WING_FOLD_Z);
        this.attachWingToAnimatedBody(this.rightWing, -SITTING_WING_RELATIVE_X, -SITTING_WING_FOLD_Z);

        this.leftWingTip.xRot = 0.0F;
        this.leftWingTip.yRot = 0.0F;
        this.leftWingTip.zRot = 0.04F;
        this.rightWingTip.xRot = 0.0F;
        this.rightWingTip.yRot = 0.0F;
        this.rightWingTip.zRot = -0.04F;
    }

    private void attachWingToAnimatedBody(ModelPart wing, float relativeX, float foldZ) {
        float deltaX = this.body.xRot - ADULT_BODY_DEFAULT_X_ROT;

        float cosX = Mth.cos(deltaX);
        float sinX = Mth.sin(deltaX);
        float rotatedY = SITTING_WING_RELATIVE_Y * cosX - SITTING_WING_RELATIVE_Z * sinX;
        float rotatedZ = SITTING_WING_RELATIVE_Y * sinX + SITTING_WING_RELATIVE_Z * cosX;

        float cosY = Mth.cos(this.body.yRot);
        float sinY = Mth.sin(this.body.yRot);
        float rotatedX = relativeX * cosY + rotatedZ * sinY;
        float finalZ = -relativeX * sinY + rotatedZ * cosY;

        wing.setPos(
                this.body.x + rotatedX,
                this.body.y + rotatedY,
                this.body.z + finalZ);
        wing.xRot = deltaX;
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