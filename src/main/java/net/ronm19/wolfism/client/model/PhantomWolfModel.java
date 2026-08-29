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
import net.ronm19.wolfism.client.renderer.state.PhantomWolfRenderState;
import net.ronm19.wolfism.entity.custom.PhantomWolf;

/**
 * Adult Phantom Wolf model.
 *
 * <p>The visible boxes, UV coordinates and pivots are adapted directly from the
 * user's latest Blockbench export. The only structural change is the hierarchy
 * required by {@link AdultWolfModel}: the exported outer +24 root is flattened,
 * the head geometry lives under "real_head", the exported mane becomes
 * "upper_body", and the tail cube lives under "real_tail". Those hierarchy
 * changes are position-neutral and preserve the supplied visible geometry.</p>
 */
public final class PhantomWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("wolfism", "phantom_wolf"),
            "main");

    private static final float LEFT_WING_FLIGHT_X = 3.0F;
    private static final float RIGHT_WING_FLIGHT_X = -3.0F;
    private static final float WING_FLIGHT_Y = 11.0F;
    private static final float WING_FLIGHT_Z = 0.2F;

    /*
     * IMPORTANT ADULT-ONLY DETAIL:
     *
     * AdultWolfModel's BODY is authored at XRot = PI/2 by default, while these
     * custom wings are authored as ROOT-LEVEL parts at XRot = 0.
     *
     * Therefore the wings must inherit only the BODY'S CHANGE FROM ITS DEFAULT
     * orientation while sitting, not the body's absolute X rotation. Applying
     * the absolute body rotation adds an unwanted extra 90 degrees and is what
     * produced the broken V6.3 adult pose.
     *
     * Default root-space attachment relative to the body pivot:
     * body pivot = (0,14,2)
     * wing pivot = (+/-3,11,0.2)
     * relative   = (+/-3,-3,-1.8)
     */
    private static final float ADULT_BODY_DEFAULT_X_ROT = (float) (Math.PI / 2.0D);
    private static final float SITTING_WING_RELATIVE_X = 3.0F;
    private static final float SITTING_WING_RELATIVE_Y = -3.0F;
    private static final float SITTING_WING_RELATIVE_Z = -1.8F;
    private static final float SITTING_WING_FOLD_Z = 0.48F;

    private final ModelPart rightWingTip;
    private final ModelPart rightWing;
    private final ModelPart leftWingTip;
    private final ModelPart leftWing;

    public PhantomWolfModel(ModelPart root) {
        super(root);
        this.rightWing = root.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.leftWing = root.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(-1.0F, 13.5F, -7.0F));

        head.addOrReplaceChild(
                "real_head",
                CubeListBuilder.create()
                        .texOffs(24, 42)
                        .addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 33)
                        .addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 36)
                        .addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(44, 0)
                        .addBox(-0.5F, -0.02F, -5.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(0, 20)
                        .addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)),
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
                        .addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F,
                        14.0F,
                        2.0F,
                        (float) (Math.PI / 2.0D),
                        0.0F,
                        0.0F));

        // Keep the user's exact wing boxes/UVs/pivots, but parent each tip to
        // its base exactly like vanilla Phantom. The child offsets preserve the
        // same absolute positions while allowing Phantom's cumulative base+tip
        // flap animation instead of rotating two unrelated flat planes.
        PartDefinition rightWing = root.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(30, 31)
                        .mirror()
                        .addBox(-6.0F, 0.0F, -0.9F, 6.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-3.0F, 11.0F, 0.2F));

        rightWing.addOrReplaceChild(
                "right_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 10)
                        .mirror()
                        .addBox(-13.0F, 0.0F, -0.8F, 13.0F, 1.0F, 9.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-6.0F, 0.0F, 0.0F));

        PartDefinition leftWing = root.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(30, 20)
                        .addBox(0.0F, 0.0F, -1.2F, 6.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.0F, 11.0F, 0.2F));

        leftWing.addOrReplaceChild(
                "left_wing_tip",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(0.0F, 0.0F, -1.2F, 13.0F, 1.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(6.0F, 0.0F, 0.0F));

        root.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create()
                        .texOffs(44, 7)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.5F, 16.0F, 7.0F));

        root.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create()
                        .texOffs(44, 42)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, 16.0F, 7.0F));

        root.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create()
                        .texOffs(0, 48)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.5F, 16.0F, -4.0F));

        root.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create()
                        .texOffs(8, 48)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, 16.0F, -4.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                // Vanilla AdultWolfModel attachment point. The custom export used
                // Z=10, which visibly detached the tail from the rump in-game.
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
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        if (!(state instanceof PhantomWolfRenderState phantomState)) {
            return;
        }

        // Sitting must win even if a stale flight boolean ever reaches the
        // model. This second guard makes the pose impossible to regress back
        // into flapping while seated.
        if (state.isSitting) {
            this.applyGroundedWingPose(state);
        } else if (phantomState.isFlying) {
            this.applyPhantomFlightPose(phantomState);
        } else {
            this.applyGroundedWingPose(state);
        }
    }

    private void applyPhantomFlightPose(PhantomWolfRenderState state) {
        this.restoreFlightWingRoots();

        float anim = state.flapTime
                * PhantomWolf.FLAP_DEGREES_PER_TICK
                * (float) (Math.PI / 180.0D);
        float flapDegrees = state.isSwooping ? 10.0F : 16.0F;
        float flap = Mth.cos(anim) * flapDegrees * (float) (Math.PI / 180.0D);

        // Normal flight keeps the vanilla Phantom cadence. During a wolf-shaped
        // dive, soften the cumulative tip rotation so the huge wings do not fold
        // into a visually broken corkscrew.
        this.leftWing.zRot = flap;
        this.rightWing.zRot = -flap;
        this.leftWingTip.zRot = state.isSwooping ? flap * 0.55F : flap;
        this.rightWingTip.zRot = state.isSwooping ? -flap * 0.55F : -flap;
        this.leftWing.yRot = 0.0F;
        this.rightWing.yRot = 0.0F;

        // The entity renderer pitches the whole wolf with the flight vector, so
        // neutralize WolfModel's ordinary head-pitch application to avoid a
        // double pitch while diving/climbing.
        this.head.xRot = 0.0F;

        // Wolves have legs where a Phantom does not. Keep them naturally tucked
        // rather than playing an airborne walking cycle.
        this.rightFrontLeg.xRot = -0.20F;
        this.leftFrontLeg.xRot = -0.20F;
        this.rightHindLeg.xRot = 0.35F;
        this.leftHindLeg.xRot = 0.35F;

        if (state.isSwooping) {
            this.rightFrontLeg.xRot = -0.42F;
            this.leftFrontLeg.xRot = -0.42F;
        }
    }

    private void applyGroundedWingPose(WolfRenderState state) {
        if (state.isSitting) {
            this.attachWingsToSittingBody();
        } else {
            this.restoreFlightWingRoots();

            // Spread but quiet when briefly grounded between AI transitions.
            this.leftWing.zRot = 0.08F;
            this.leftWingTip.zRot = 0.04F;
            this.rightWing.zRot = -0.08F;
            this.rightWingTip.zRot = -0.04F;
        }
    }

    /**
     * Repositions the wing roots from the CURRENT animated body transform.
     *
     * <p>Vanilla AdultWolfModel changes body position and X rotation while
     * sitting. The Phantom wings are root-level custom parts, so they do not
     * inherit that transform automatically. This method performs the same
     * parent-child transform explicitly for the wing attachment point.</p>
     */
    private void attachWingsToSittingBody() {
        this.attachWingToAnimatedBody(this.leftWing, SITTING_WING_RELATIVE_X, SITTING_WING_FOLD_Z);
        this.attachWingToAnimatedBody(this.rightWing, -SITTING_WING_RELATIVE_X, -SITTING_WING_FOLD_Z);

        // Tips are children of the roots and therefore already inherit the
        // body's sitting orientation. Keep them almost straight so the full
        // wing lies cleanly down the side of the torso instead of forming a V.
        this.leftWingTip.xRot = 0.0F;
        this.leftWingTip.yRot = 0.0F;
        this.leftWingTip.zRot = 0.04F;

        this.rightWingTip.xRot = 0.0F;
        this.rightWingTip.yRot = 0.0F;
        this.rightWingTip.zRot = -0.04F;
    }

    private void attachWingToAnimatedBody(ModelPart wing, float relativeX, float foldZ) {
        /*
         * Adult body and adult wings do NOT share the same authored baseline:
         *
         * body default xRot = PI/2
         * wing default xRot = 0
         *
         * So calculate the body's ANIMATION DELTA from PI/2. This makes the
         * wings follow the seated torso without inheriting a bogus extra 90°.
         */
        float deltaX = this.body.xRot - ADULT_BODY_DEFAULT_X_ROT;

        float cosX = Mth.cos(deltaX);
        float sinX = Mth.sin(deltaX);

        float rotatedY = SITTING_WING_RELATIVE_Y * cosX
                - SITTING_WING_RELATIVE_Z * sinX;
        float rotatedZ = SITTING_WING_RELATIVE_Y * sinX
                + SITTING_WING_RELATIVE_Z * cosX;

        // Adult body has zero authored Y/Z rotation, so its current values are
        // already the animation deltas for those axes.
        float cosY = Mth.cos(this.body.yRot);
        float sinY = Mth.sin(this.body.yRot);

        float rotatedX = relativeX * cosY + rotatedZ * sinY;
        float finalZ = -relativeX * sinY + rotatedZ * cosY;

        wing.setPos(
                this.body.x + rotatedX,
                this.body.y + rotatedY,
                this.body.z + finalZ);

        // Match the torso's VISUAL animation delta, not its absolute authored
        // PI/2 baseline. Then add only the small left/right resting fold.
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