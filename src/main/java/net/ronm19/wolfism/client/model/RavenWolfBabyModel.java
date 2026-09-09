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
import net.ronm19.wolfism.client.renderer.state.RavenWolfRenderState;

/**
 * Baby Raven Wolf.
 *
 * Preserves the user's actual Blockbench baby geometry/UV layout while adapting
 * it to BabyWolfModel and Wolfism's animated wing system.
 */
public final class RavenWolfBabyModel
		extends BabyWolfModel {

	public static final ModelLayerLocation LAYER_LOCATION =
			new ModelLayerLocation(
					Identifier.fromNamespaceAndPath(
							Wolfism.MOD_ID,
							"raven_wolf_baby"),
					"main");

	// =====================================================================
	// Wing roots
	// =====================================================================

	private static final float LEFT_WING_FLIGHT_X = 3.0F;
	private static final float RIGHT_WING_FLIGHT_X = -3.0F;

	private static final float WING_FLIGHT_Y = 17.0F;
	private static final float WING_FLIGHT_Z = -3.1F;

	/*
	 * Baby body pivot:
	 *
	 * (0, 19, 0)
	 *
	 * Wing root:
	 *
	 * (+/-3, 17, -3.1)
	 */
	private static final float SITTING_WING_LOCAL_X = 3.0F;
	private static final float SITTING_WING_LOCAL_Y = -2.0F;
	private static final float SITTING_WING_LOCAL_Z = -3.1F;

	private static final float SITTING_WING_FOLD_Z = 0.56F;

	// =====================================================================
	// Parts
	// =====================================================================

	private final ModelPart rightWing;
	private final ModelPart rightWingTip;

	private final ModelPart leftWing;
	private final ModelPart leftWingTip;

	public RavenWolfBabyModel(
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

		root.addOrReplaceChild(
				"head",

				CubeListBuilder.create()

						.texOffs(
								0,
								26)

						.addBox(
								-2.99F,
								-3.25F,
								-3.0F,
								6.0F,
								5.0F,
								5.0F,
								new CubeDeformation(
										0.025F))

						.texOffs(
								28,
								22)

						.addBox(
								-1.5F,
								-0.24F,
								-5.0F,
								3.0F,
								2.0F,
								2.0F,
								none)

						.texOffs(
								16,
								36)

						.addBox(
								1.0F,
								-5.25F,
								-1.0F,
								2.0F,
								2.0F,
								1.0F,
								none)

						.texOffs(
								38,
								5)

						.addBox(
								-3.0F,
								-5.25F,
								-1.0F,
								2.0F,
								2.0F,
								1.0F,
								none),

				PartPose.offset(
						0.0F,
						18.25F,
						-4.0F));

		// ================================================================
		// Body
		// ================================================================

		root.addOrReplaceChild(
				"body",

				CubeListBuilder.create()

						.texOffs(
								0,
								14)

						.addBox(
								-3.0F,
								-2.0F,
								-4.0F,
								6.0F,
								4.0F,
								8.0F,
								none),

				PartPose.offset(
						0.0F,
						19.0F,
						0.0F));

		// ================================================================
		// Right wing
		// ================================================================

		PartDefinition rightWing =
				root.addOrReplaceChild(
						"right_wing",

						CubeListBuilder.create()

								.texOffs(
										28,
										14)

								.mirror()

								.addBox(
										-6.0F,
										0.0F,
										1.1F,
										6.0F,
										2.0F,
										6.0F,
										none)

								.mirror(false),

						PartPose.offset(
								RIGHT_WING_FLIGHT_X,
								WING_FLIGHT_Y,
								WING_FLIGHT_Z));

		rightWing.addOrReplaceChild(
				"right_wing_tip",

				CubeListBuilder.create()

						.texOffs(
								0,
								0)

						.mirror()

						.addBox(
								-13.0F,
								0.0F,
								1.1F,
								13.0F,
								1.0F,
								6.0F,
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
										22,
										26)

								.addBox(
										0.0F,
										0.0F,
										1.05F,
										6.0F,
										2.0F,
										6.0F,
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
								7)

						.addBox(
								0.0F,
								0.0F,
								1.05F,
								13.0F,
								1.0F,
								6.0F,
								none),

				PartPose.offset(
						6.0F,
						0.0F,
						0.0F));

		// ================================================================
		// Legs
		// ================================================================

		root.addOrReplaceChild(
				"left_hind_leg",

				CubeListBuilder.create()
						.texOffs(
								30,
								34)

						.addBox(
								-1.0F,
								0.0F,
								-1.0F,
								2.0F,
								3.0F,
								2.0F,
								none),

				PartPose.offset(
						1.5F,
						21.0F,
						3.0F));

		root.addOrReplaceChild(
				"right_hind_leg",

				CubeListBuilder.create()
						.texOffs(
								0,
								36)

						.addBox(
								-1.0F,
								0.0F,
								-1.0F,
								2.0F,
								3.0F,
								2.0F,
								none),

				PartPose.offset(
						-1.5F,
						21.0F,
						3.0F));

		root.addOrReplaceChild(
				"left_front_leg",

				CubeListBuilder.create()
						.texOffs(
								8,
								36)

						.addBox(
								-1.0F,
								0.0F,
								-1.0F,
								2.0F,
								3.0F,
								2.0F,
								none),

				PartPose.offset(
						1.5F,
						21.0F,
						-3.0F));

		root.addOrReplaceChild(
				"right_front_leg",

				CubeListBuilder.create()
						.texOffs(
								38,
								0)

						.addBox(
								-1.0F,
								0.0F,
								-1.0F,
								2.0F,
								3.0F,
								2.0F,
								none),

				PartPose.offset(
						-1.5F,
						21.0F,
						-3.0F));

		// ================================================================
		// Tail
		// ================================================================

		PartDefinition tail =
				root.addOrReplaceChild(
						"tail",

						CubeListBuilder.create(),

						/*
						 * Wolf-compatible parent pose.
						 */
						PartPose.offsetAndRotation(
								0.0F,
								19.0F,
								3.0F,
								-0.5236F,
								0.0F,
								0.0F));

		tail.addOrReplaceChild(
				"tail_rotation",

				CubeListBuilder.create()

						.texOffs(
								22,
								34)

						.addBox(
								-1.0F,
								-5.7F,
								-1.0F,
								2.0F,
								6.0F,
								2.0F,
								none),

				PartPose.offsetAndRotation(
						0.0F,
						-0.6F,
						0.2F,
						-3.1F,
						0.0F,
						0.0F));

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

		attachWingToBody(this.leftWing, SITTING_WING_LOCAL_X, 0.06F);
		attachWingToBody(this.rightWing, -SITTING_WING_LOCAL_X, -0.06F);
		this.leftWingTip.zRot = 0.025F;
		this.rightWingTip.zRot = -0.025F;
	}

	// =====================================================================
	// Sitting
	// =====================================================================

	private void attachWingsToSittingBody() {

		attachWingToBody(
				this.leftWing,
				SITTING_WING_LOCAL_X,
				SITTING_WING_FOLD_Z);

		attachWingToBody(
				this.rightWing,
				-SITTING_WING_LOCAL_X,
				-SITTING_WING_FOLD_Z);

		this.leftWingTip.xRot = 0.0F;
		this.leftWingTip.yRot = 0.0F;
		this.leftWingTip.zRot = 0.05F;

		this.rightWingTip.xRot = 0.0F;
		this.rightWingTip.yRot = 0.0F;
		this.rightWingTip.zRot = -0.05F;
	}

	private void attachWingToBody(
			ModelPart wing,
			float localX,
			float foldZ) {

		float cosX =
				Mth.cos(
						this.body.xRot);

		float sinX =
				Mth.sin(
						this.body.xRot);

		float rotatedY =
				SITTING_WING_LOCAL_Y
						* cosX
						- SITTING_WING_LOCAL_Z
						* sinX;

		float rotatedZ =
				SITTING_WING_LOCAL_Y
						* sinX
						+ SITTING_WING_LOCAL_Z
						* cosX;

		float cosY =
				Mth.cos(
						this.body.yRot);

		float sinY =
				Mth.sin(
						this.body.yRot);

		float rotatedX =
				localX
						* cosY
						+ rotatedZ
						* sinY;

		float finalZ =
				-localX
						* sinY
						+ rotatedZ
						* cosY;

		float cosZ = Mth.cos(this.body.zRot);
		float sinZ = Mth.sin(this.body.zRot);
		wing.setPos(
				this.body.x + rotatedX * cosZ - rotatedY * sinZ,
				this.body.y + rotatedX * sinZ + rotatedY * cosZ,
				this.body.z + finalZ);

		wing.xRot =
				this.body.xRot;

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
