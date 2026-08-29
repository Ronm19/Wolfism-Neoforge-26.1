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
import net.ronm19.wolfism.Wolfism;

/**
 * Adult Demon Wolf model adapted from the supplied Blockbench export.
 *
 * Visible cubes, UV coordinates and pivots are preserved. The exported +24 root
 * is flattened only so Minecraft 26.1 AdultWolfModel can find its canonical
 * children directly.
 */
public final class DemonWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "demon_wolf"),
            "main");

    private final ModelPart demonTail;

    public DemonWolfModel(ModelPart root) {
        super(root);

        /*
         * `tail` itself remains the invisible canonical AdultWolfModel animation
         * driver at the root. The visible Demon tail is genuinely attached to
         * the animated torso hierarchy.
         */
        this.demonTail = this.body
                .getChild("demon_tail_anchor")
                .getChild("demon_tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition head = partdefinition.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(-1.0F, 13.5F, -7.0F));

        PartDefinition realHead = head.addOrReplaceChild(
                "real_head",
                CubeListBuilder.create()
                        .texOffs(24, 13).addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(30, 10).addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(30, 10).addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 23).addBox(-0.5F, -0.02F, -5.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        realHead.addOrReplaceChild(
                "horns",
                CubeListBuilder.create()
                        .texOffs(32, 33).addBox(-1.6F, -3.0F, -4.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 36).addBox(-1.6F, -5.0F, -4.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 33).addBox(2.4F, -3.0F, -4.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 36).addBox(2.4F, -5.0F, -4.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        PartDefinition upperBody = partdefinition.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 14.0F, -3.0F, 1.5708F, 0.0F, 0.0F));

        PartDefinition body = partdefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 13).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, 1.5708F, 0.0F, 0.0F));

        /*
         * ACTUAL DORSAL ATTACHMENT — CORRECT TORSO SECTION
         * =================================================
         *
         * The front two blades are genuine children of "upper_body" (shoulders /
         * mane), while the rear four are genuine children of "body".
         *
         * AdultWolfModel animates those two torso sections differently while
         * sitting. This split is required for every blade to remain physically
         * attached to the surface it was authored on.
         *
         * Original body:
         *   pivot = (0,14,2)
         *   XRot  = +90 degrees
         *
         * Every anchor position below is the ORIGINAL Blockbench spike pivot
         * converted into the local coordinates of its actual torso parent.
         *
         * Each parent torso part has the exported +90-degree X baseline.
         * Each anchor gets -90 degrees X so:
         *
         *   standing torso(+90) * anchor(-90) = identity
         *
         * Therefore the visible spike child keeps the exact world-space pose it
         * had in Blockbench while standing. When AdultWolfModel changes the body
         * pose for sitting, the spikes inherit that movement automatically.
         *
         * No setupAnim attachment math. No root-level fake following.
         */

        PartDefinition spike1Anchor = upperBody.addOrReplaceChild(
                "spike1_anchor",
                CubeListBuilder.create(),
                /*
                 * Original Blockbench pivot (-0.1,9.9,-4.1) converted into
                 * upper_body-local space.
                 */
                PartPose.offsetAndRotation(
                        0.9F, -1.1F, 4.1F,
                        -1.5708F, 0.0F, 0.0F));

        spike1Anchor.addOrReplaceChild(
                "spike",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -1.9F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.1F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));

        PartDefinition spike2Anchor = upperBody.addOrReplaceChild(
                "spike2_anchor",
                CubeListBuilder.create(),
                /*
                 * Original Blockbench pivot (-0.1,9.9,-1.7) converted into
                 * upper_body-local space.
                 */
                PartPose.offsetAndRotation(
                        0.9F, 1.3F, 4.1F,
                        -1.5708F, 0.0F, 0.0F));

        spike2Anchor.addOrReplaceChild(
                "spike2",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -1.9F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.1F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));

        PartDefinition spike3Anchor = body.addOrReplaceChild(
                "spike3_anchor",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -0.1F, -1.3F, 3.1F,
                        -1.5708F, 0.0F, 0.0F));

        spike3Anchor.addOrReplaceChild(
                "spike3",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -1.9F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.1F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));

        PartDefinition spike4Anchor = body.addOrReplaceChild(
                "spike4_anchor",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -0.1F, 1.0F, 2.9F,
                        -1.5708F, 0.0F, 0.0F));

        spike4Anchor.addOrReplaceChild(
                "spike4",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.0F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));

        PartDefinition spike5Anchor = body.addOrReplaceChild(
                "spike5_anchor",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -0.1F, 3.2F, 2.9F,
                        -1.5708F, 0.0F, 0.0F));

        spike5Anchor.addOrReplaceChild(
                "spike5",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.0F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));

        PartDefinition spike6Anchor = body.addOrReplaceChild(
                "spike6_anchor",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -0.1F, 5.4F, 2.9F,
                        -1.5708F, 0.0F, 0.0F));

        spike6Anchor.addOrReplaceChild(
                "spike6",
                CubeListBuilder.create()
                        .texOffs(8, 38)
                        .addBox(0.0F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 0)
                        .addBox(0.0F, 0.0F, -0.5F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(
                        0.0F, 0.0F, 0.0F,
                        -1.4736F, -1.3955F, 1.4691F));


        partdefinition.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create()
                        .texOffs(30, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.5F, 16.0F, 7.0F));

        partdefinition.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create()
                        .texOffs(30, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, 16.0F, 7.0F));

        partdefinition.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create()
                        .texOffs(30, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.5F, 16.0F, -4.0F));

        partdefinition.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create()
                        .texOffs(30, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, 16.0F, -4.0F));

        /*
         * Keep the canonical root tail because AdultWolfModel expects and
         * animates this exact child name. It is intentionally geometry-free and
         * acts only as the vanilla tail-angle driver.
         */
        PartDefinition tail = partdefinition.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                /*
                 * Canonical adult-wolf rump joint. Z=8 keeps the rotation pivot
                 * physically inside/against the rear torso instead of hanging
                 * behind it at the original Blockbench Z=10 export pivot.
                 */
                PartPose.offsetAndRotation(
                        -1.0F, 12.0F, 8.0F,
                        (float) (Math.PI / 5.0D),
                        0.0F, 0.0F));

        /*
         * REQUIRED BY AdultWolfModel.
         *
         * The vanilla adult wolf constructor explicitly looks up:
         *     tail -> real_tail
         *
         * This child is intentionally geometry-free. It exists only as the
         * canonical vanilla animation driver. The visible Demon tail lives under
         * the animated body hierarchy below.
         */
        tail.addOrReplaceChild(
                "real_tail",
                CubeListBuilder.create(),
                PartPose.ZERO);

        /*
         * ACTUAL DEMON TAIL ATTACHMENT
         * ============================
         *
         * The original Blockbench root was (-1,12,10), but that joint is two
         * model units behind the proven adult-wolf rump attachment. Rotating from
         * that rearward pivot creates the visible gap seen in-game.
         *
         * Corrected visible joint:
         *   world/model = (-1,12,8)
         *   body-local  = (-1,6,2)
         *
         * Anchor XRot -90 degrees cancels the body's standing +90-degree
         * baseline while the corrected joint remains genuinely embedded in the
         * animated torso hierarchy.
         */
        PartDefinition demonTailAnchor = body.addOrReplaceChild(
                "demon_tail_anchor",
                CubeListBuilder.create(),
                /*
                 * Desired world/model rump joint = (-1,12,8).
                 *
                 * Converted into the default adult body's local coordinates:
                 *     (-1, 6, 2)
                 *
                 * This is intentionally 2 model units farther into the rump than
                 * the original Blockbench tail pivot. The old Z=10 joint looked
                 * acceptable only while unrotated; once vanilla tail rotation was
                 * applied it visibly opened an air gap.
                 */
                PartPose.offsetAndRotation(
                        -1.0F, 6.0F, 2.0F,
                        -1.5708F, 0.0F, 0.0F));

        demonTailAnchor.addOrReplaceChild(
                "demon_tail",
                CubeListBuilder.create()
                        .texOffs(23, 30)
                        .addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);

        /*
         * `this.tail` is AdultWolfModel's invisible canonical tail driver.
         * Transfer its vanilla animation rotation to the visible body-attached
         * Demon tail. Position is NEVER manually moved here.
         *
         * Because the visible tail is already a real child of `body`, sitting
         * attachment now comes from the model hierarchy itself.
         */
        this.demonTail.xRot = this.tail.xRot;
        this.demonTail.yRot = this.tail.yRot;
        this.demonTail.zRot = this.tail.zRot;
    }
}
