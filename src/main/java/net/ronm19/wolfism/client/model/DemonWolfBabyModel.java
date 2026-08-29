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
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/**
 * Baby Demon Wolf adapted directly from the supplied Blockbench export.
 * Visible cubes, UVs, pivots and rotations are preserved.
 */
public final class DemonWolfBabyModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "demon_wolf_baby"),
            "main");

    public DemonWolfBabyModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition head = partdefinition.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-2.99F, -3.25F, -3.0F, 6.0F, 5.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(8, 22).addBox(-1.5F, -0.24F, -5.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 27).addBox(1.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(28, 0).addBox(-3.0F, -5.25F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 18.25F, -4.0F));

        head.addOrReplaceChild(
                "horns",
                CubeListBuilder.create()
                        .texOffs(26, 22).addBox(-2.7F, -3.3F, -6.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(28, 3).addBox(-2.7F, -5.3F, -6.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(26, 22).addBox(1.7F, -3.3F, -6.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(28, 3).addBox(1.7F, -5.3F, -6.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        PartDefinition body = partdefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        PartDefinition spikes = body.addOrReplaceChild(
                "spikes",
                CubeListBuilder.create(),
                PartPose.ZERO);

        PartDefinition spike1 = spikes.addOrReplaceChild(
                "spike1",
                CubeListBuilder.create(),
                PartPose.ZERO);

        spike1.addOrReplaceChild(
                "spike1_r1",
                CubeListBuilder.create()
                        .texOffs(28, 6).addBox(1.0F, -4.0F, 0.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-0.9F, 0.0F, -1.5F, -0.0698F, 0.0F, 0.0F));

        PartDefinition spike2 = spikes.addOrReplaceChild(
                "spike2",
                CubeListBuilder.create(),
                PartPose.ZERO);

        spike2.addOrReplaceChild(
                "spike2_r1",
                CubeListBuilder.create()
                        .texOffs(0, 30).addBox(1.0F, -4.0F, 0.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-0.9F, 0.4F, 0.6F, -0.0698F, 0.0F, 0.0F));

        PartDefinition spike3 = spikes.addOrReplaceChild(
                "spike3",
                CubeListBuilder.create(),
                PartPose.ZERO);

        spike3.addOrReplaceChild(
                "spike3_r1",
                CubeListBuilder.create()
                        .texOffs(28, 9).addBox(1.0F, -4.0F, 0.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-0.9F, 0.9F, 2.5F, -0.0698F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create()
                        .texOffs(22, 12).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, 21.0F, 3.0F));

        partdefinition.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create()
                        .texOffs(22, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.5F, 21.0F, 3.0F));

        partdefinition.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create()
                        .texOffs(18, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, 21.0F, -3.0F));

        partdefinition.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create()
                        .texOffs(8, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.5F, 21.0F, -3.0F));

        PartDefinition tail = partdefinition.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 19.0F, 3.0F, 1.5708F, 0.0F, 0.0F));

        tail.addOrReplaceChild(
                "tail_rotation",
                CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-1.0F, -5.7F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -0.6F, 0.2F, -3.1F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}
