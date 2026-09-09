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
import net.ronm19.wolfism.client.renderer.state.RavenWolfRenderState;

/**
 * Adult Raven Wolf.
 *
 * Uses the user's approved Blockbench Raven geometry and 64x64 UV layout.
 *
 * The exported wing-tip pieces are parented to their matching base wings so
 * Raven receives cumulative, two-piece feathered wing motion.
 */
public final class RavenWolfModel
		extends AdultWolfModel {

	public static final ModelLayerLocation LAYER_LOCATION =
			new ModelLayerLocation(
					Identifier.fromNamespaceAndPath(
							Wolfism.MOD_ID,
							"raven_wolf"),
					"main");

	// =====================================================================
	// Wing roots
	// =====================================================================

	private static final float LEFT_WING_FLIGHT_X = 3.0F;
	private static final float RIGHT_WING_FLIGHT_X = -3.0F;

	private static final float WING_FLIGHT_Y = 11.0F;
	private static final float WING_FLIGHT_Z = 0.2F;

	/*
	 * AdultWolfModel's normal body pivot:
	 *
	 * position = (0, 14, 2)
	 * X rotation = PI / 2
	 *
	 * Raven's Blockbench wing roots:
	 *
	 * (+/-3, 11, 0.2)
	 *
	 * Relative world offset:
	 *
	 * (+/-3, -3, -1.8)
	 */
	private static final float ADULT_BODY_DEFAULT_X_ROT =
			(float) (Math.PI / 2.0D);

	private static final float SITTING_WING_RELATIVE_X = 3.0F;
	private static final float SITTING_WING_RELATIVE_Y = -3.0F;
	private static final float SITTING_WING_RELATIVE_Z = -1.8F;

	private static final float SITTING_WING_FOLD_Z = 0.48F;

	// =====================================================================
	// Parts
	// =====================================================================

	private final ModelPart rightWing;
	private final ModelPart rightWingTip;

	private final ModelPart leftWing;
	private final ModelPart leftWingTip;

	public RavenWolfModel(
			ModelPart root) {

		super(root);

		this.rightWing =
				root.getChild(
						"right_wing");

		this.rightWingTip =
				this.rightWing.getChild(
						"right_wing_tip");

		this.leftWing =
				root.getChild(
						"left_wing");

		this.leftWingTip =
				this.leftWing.getChild(
						"left_wing_tip");
	}

	// =====================================================================
	// Geometry
	// =====================================================================

	public static LayerDefinition createBodyLayer() {

		MeshDefinition mesh =
				new MeshDefinition();

		PartDefinition root =
				mesh.getRoot();

		CubeDeformation none =
				new CubeDeformation(
						0.0F);

		// ================================================================
		// Head
		// ================================================================

		PartDefinition head =
				root.addOrReplaceChild(
						"head",
						CubeListBuilder.create(),
						PartPose.offset(
								-1.0F,
								13.5F,
								-7.0F));

		head.addOrReplaceChild(
				"real_head",
				CubeListBuilder.create()

						.texOffs(
								24,
								42)
						.addBox(
								-2.0F,
								-3.0F,
								-2.0F,
								6.0F,
								6.0F,
								4.0F,
								none)

						.texOffs(
								24,
								33)
						.addBox(
								2.0F,
								-5.0F,
								0.0F,
								2.0F,
								2.0F,
								1.0F,
								none)

						.texOffs(
								24,
								36)
						.addBox(
								-2.0F,
								-5.0F,
								0.0F,
								2.0F,
								2.0F,
								1.0F,
								none)

						.texOffs(
								44,
								0)
						.addBox(
								-0.5F,
								-0.02F,
								-5.0F,
								3.0F,
								3.0F,
								4.0F,
								none),

				PartPose.ZERO);

		// ================================================================
		// Mane / upper body
		// ================================================================

		root.addOrReplaceChild(
				"upper_body",
				CubeListBuilder.create()

						.texOffs(
								0,
								20)

						.addBox(
								-3.0F,
								-3.0F,
								-3.0F,
								8.0F,
								6.0F,
								7.0F,
								none),

				PartPose.offsetAndRotation(
						-1.0F,
						14.0F,
						-3.0F,

						(float)
								(Math.PI
										/ 2.0D),

						0.0F,
						0.0F));

		// ================================================================
		// Body
		// ================================================================

		root.addOrReplaceChild(
				"body",
				CubeListBuilder.create()

						.texOffs(
								0,
								33)

						.addBox(
								-3.0F,
								-2.0F,
								-3.0F,
								6.0F,
								9.0F,
								6.0F,
								none),

				PartPose.offsetAndRotation(
						0.0F,
						14.0F,
						2.0F,

						(float)
								(Math.PI
										/ 2.0D),

						0.0F,
						0.0F));

		// ================================================================
		// Right wing
		// ================================================================

		PartDefinition rightWing =
				root.addOrReplaceChild(
						"right_wing",

						CubeListBuilder.create()

								.texOffs(
										30,
										31)

								.mirror()

								.addBox(
										-6.0F,
										0.0F,
										-0.9F,
										6.0F,
										2.0F,
										9.0F,
										none)

								.mirror(false),

						PartPose.offset(
								RIGHT_WING_FLIGHT_X,
								WING_FLIGHT_Y,
								WING_FLIGHT_Z));

		/*
		 * Raw Blockbench pivot:
		 *
		 * -9, 11, 0.2
		 *
		 * Wing root:
		 *
		 * -3, 11, 0.2
		 *
		 * Therefore child offset = -6, 0, 0.
		 */
		rightWing.addOrReplaceChild(
				"right_wing_tip",

				CubeListBuilder.create()

						.texOffs(
								0,
								10)

						.mirror()

						.addBox(
								-13.0F,
								0.0F,
								-0.8F,
								13.0F,
								1.0F,
								9.0F,
								none)

						.mirror(false),

				PartPose.offset(
						-6.0F,
						0.0F,
						0.0F));

		// ================================================================
		// Left wing
		// ================================================================

		PartDefinition leftWing =
				root.addOrReplaceChild(
						"left_wing",

						CubeListBuilder.create()

								.texOffs(
										30,
										20)

								.addBox(
										0.0F,
										0.0F,
										-1.2F,
										6.0F,
										2.0F,
										9.0F,
										none),

						PartPose.offset(
								LEFT_WING_FLIGHT_X,
								WING_FLIGHT_Y,
								WING_FLIGHT_Z));

		leftWing.addOrReplaceChild(
				"left_wing_tip",

				CubeListBuilder.create()

						.texOffs(
								0,
								0)

						.addBox(
								0.0F,
								0.0F,
								-1.2F,
								13.0F,
								1.0F,
								9.0F,
								none),

				PartPose.offset(
						6.0F,
						0.0F,
						0.0F));

		// ================================================================
		// Legs
		// ================================================================

		root.addOrReplaceChild(
				"right_hind_leg",

				CubeListBuilder.create()
						.texOffs(
								44,
								7)

						.addBox(
								0.0F,
								0.0F,
								-1.0F,
								2.0F,
								8.0F,
								2.0F,
								none),

				PartPose.offset(
						-2.5F,
						16.0F,
						7.0F));

		root.addOrReplaceChild(
				"left_hind_leg",

				CubeListBuilder.create()
						.texOffs(
								44,
								42)

						.addBox(
								0.0F,
								0.0F,
								-1.0F,
								2.0F,
								8.0F,
								2.0F,
								none),

				PartPose.offset(
						0.5F,
						16.0F,
						7.0F));

		root.addOrReplaceChild(
				"right_front_leg",

				CubeListBuilder.create()
						.texOffs(
								0,
								48)

						.addBox(
								0.0F,
								0.0F,
								-1.0F,
								2.0F,
								8.0F,
								2.0F,
								none),

				PartPose.offset(
						-2.5F,
						16.0F,
						-4.0F));

		root.addOrReplaceChild(
				"left_front_leg",

				CubeListBuilder.create()
						.texOffs(
								8,
								48)

						.addBox(
								0.0F,
								0.0F,
								-1.0F,
								2.0F,
								8.0F,
								2.0F,
								none),

				PartPose.offset(
						0.5F,
						16.0F,
						-4.0F));

		// ================================================================
		// Tail
		// ================================================================

		/*
		 * The raw Blockbench export placed the tail root at Z=10.
		 *
		 * We deliberately use the proven Wolf-compatible Z=8 attachment from
		 * Phantom/Angel here. It prevents the custom tail from visually
		 * detaching when AdultWolfModel applies sitting/tail animation.
		 */
		PartDefinition tail =
				root.addOrReplaceChild(
						"tail",

						CubeListBuilder.create(),

						PartPose.offsetAndRotation(
								-1.0F,
								12.0F,
								8.0F,

								(float)
										(Math.PI
												/ 5.0D),

								0.0F,
								0.0F));

		tail.addOrReplaceChild(
				"real_tail",

				CubeListBuilder.create()
						.texOffs(
								16,
								48)

						.addBox(
								0.0F,
								0.0F,
								-1.0F,
								2.0F,
								8.0F,
								2.0F,
								none),

				PartPose.ZERO);

		return LayerDefinition.create(
				mesh,
				64,
				64);
	}

	// =====================================================================
	// Animation
	// =====================================================================

	@Override
	public void setupAnim(
			WolfRenderState state) {

		super.setupAnim(state);

		if (!(state
				instanceof RavenWolfRenderState ravenState)) {

			applyGroundedWingPose(
					state);

			return;
		}

		/*
		 * Sitting ALWAYS wins.
		 */
		if (state.isSitting) {

			applyGroundedWingPose(
					state);

		} else if (ravenState.isFlying) {

			applyFlightPose(
					ravenState);

		} else {

			applyGroundedWingPose(
					state);
		}
	}

	private void applyFlightPose(RavenWolfRenderState state) {
		restoreFlightWingRoots();

		// Raven powers forward in short strokes, then holds her feathered wings
		// out to coast. The tips lag behind the shoulders instead of duplicating
		// the Phantom's continuous, symmetrical flap.
		float ageAmplitude = state.isBaby ? 0.86F : 1.0F;
		this.leftWing.zRot = state.wingAngle * ageAmplitude;
		this.rightWing.zRot = -this.leftWing.zRot;
		this.leftWingTip.zRot = state.wingTipAngle * ageAmplitude;
		this.rightWingTip.zRot = -this.leftWingTip.zRot;
		this.leftWing.xRot = state.isLanding ? -0.18F : 0.0F;
		this.rightWing.xRot = this.leftWing.xRot;
		this.leftWing.yRot = -0.08F;
		this.rightWing.yRot = 0.08F;
		this.leftWingTip.yRot = -0.08F;
		this.rightWingTip.yRot = 0.08F;

		// The renderer pitches the complete body; keep the head level with the
		// requested look direction rather than applying that pitch twice.
		this.head.xRot = (state.xRot - state.flightPitch) * Mth.DEG_TO_RAD;
		float frontLeg = state.isLanding ? 0.02F
				: state.ravensRageActive ? 0.30F : 0.65F;
		float rearLeg = state.isLanding ? 0.08F : 0.85F;
		this.rightFrontLeg.xRot = frontLeg;
		this.leftFrontLeg.xRot = frontLeg;
		this.rightHindLeg.xRot = rearLeg;
		this.leftHindLeg.xRot = rearLeg;
	}
	private void applyGroundedWingPose(
			WolfRenderState state) {

		if (state.isSitting) {

			attachWingsToSittingBody();

			return;
		}

		/*
		 * Raven's approved Blockbench silhouette is proudly broad-winged.
		 * Keep only a tiny resting angle when standing.
		 */
		attachWingToAnimatedBody(this.leftWing, SITTING_WING_RELATIVE_X, 0.07F);
		attachWingToAnimatedBody(this.rightWing, -SITTING_WING_RELATIVE_X, -0.07F);
		this.leftWingTip.zRot = 0.03F;
		this.rightWingTip.zRot = -0.03F;
	}

	// =====================================================================
	// Sitting wing attachment
	// =====================================================================

	private void attachWingsToSittingBody() {

		attachWingToAnimatedBody(
				this.leftWing,
				SITTING_WING_RELATIVE_X,
				SITTING_WING_FOLD_Z);

		attachWingToAnimatedBody(
				this.rightWing,
				-SITTING_WING_RELATIVE_X,
				-SITTING_WING_FOLD_Z);

		this.leftWingTip.xRot = 0.0F;
		this.leftWingTip.yRot = 0.0F;
		this.leftWingTip.zRot = 0.06F;

		this.rightWingTip.xRot = 0.0F;
		this.rightWingTip.yRot = 0.0F;
		this.rightWingTip.zRot = -0.06F;
	}

	private void attachWingToAnimatedBody(
			ModelPart wing,
			float relativeX,
			float foldZ) {

		/*
		 * Adult body starts at PI/2.
		 *
		 * Calculate only the CHANGE from its normal orientation so the original
		 * Blockbench wing-root position stays exact when standing.
		 */
		float deltaX =
				this.body.xRot
						- ADULT_BODY_DEFAULT_X_ROT;

		float cosX =
				Mth.cos(deltaX);

		float sinX =
				Mth.sin(deltaX);

		float rotatedY =
				SITTING_WING_RELATIVE_Y
						* cosX
						- SITTING_WING_RELATIVE_Z
						* sinX;

		float rotatedZ =
				SITTING_WING_RELATIVE_Y
						* sinX
						+ SITTING_WING_RELATIVE_Z
						* cosX;

		float cosY =
				Mth.cos(
						this.body.yRot);

		float sinY =
				Mth.sin(
						this.body.yRot);

		float rotatedX =
				relativeX
						* cosY
						+ rotatedZ
						* sinY;

		float finalZ =
				-relativeX
						* sinY
						+ rotatedZ
						* cosY;

		// Body roll also moves the shoulder pivot during the wet shake.
		float cosZ = Mth.cos(this.body.zRot);
		float sinZ = Mth.sin(this.body.zRot);
		wing.setPos(
				this.body.x + rotatedX * cosZ - rotatedY * sinZ,
				this.body.y + rotatedX * sinZ + rotatedY * cosZ,
				this.body.z + finalZ);

		wing.xRot =
				deltaX;

		wing.yRot =
				this.body.yRot;

		wing.zRot =
				this.body.zRot
						+ foldZ;
	}

	private void restoreFlightWingRoots() {

		this.leftWing.setPos(
				LEFT_WING_FLIGHT_X,
				WING_FLIGHT_Y,
				WING_FLIGHT_Z);

		this.rightWing.setPos(
				RIGHT_WING_FLIGHT_X,
				WING_FLIGHT_Y,
				WING_FLIGHT_Z);

		this.leftWing.xRot = 0.0F;
		this.leftWing.yRot = 0.0F;

		this.rightWing.xRot = 0.0F;
		this.rightWing.yRot = 0.0F;
	}
}
