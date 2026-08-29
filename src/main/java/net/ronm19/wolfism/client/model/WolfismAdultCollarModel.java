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
import net.minecraft.resources.Identifier;

/**
 * Dedicated collar-only model for adult custom Wolfism wolves.
 *
 * The complete custom wolf is never re-rendered for this pass. Only a slightly
 * inflated upper-body shell exists, using Minecraft's original adult wolf
 * collar UV coordinates.
 */
public final class WolfismAdultCollarModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("wolfism", "wolfism_adult_collar"),
            "main");

    private static final float COLLAR_INFLATION = 0.14F;

    public WolfismAdultCollarModel(ModelPart root) {
        super(root);
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
                CubeListBuilder.create(),
                PartPose.ZERO);

        root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(21, 0)
                        .addBox(
                                -3.0F, -3.0F, -3.0F,
                                8.0F, 6.0F, 7.0F,
                                new CubeDeformation(COLLAR_INFLATION)),
                PartPose.offsetAndRotation(
                        -1.0F, 14.0F, -3.0F,
                        (float) (Math.PI / 2.0D),
                        0.0F, 0.0F));

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        0.0F, 14.0F, 2.0F,
                        (float) (Math.PI / 2.0D),
                        0.0F, 0.0F));

        root.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create(),
                PartPose.offset(-2.5F, 16.0F, 7.0F));

        root.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create(),
                PartPose.offset(0.5F, 16.0F, 7.0F));

        root.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create(),
                PartPose.offset(-2.5F, 16.0F, -4.0F));

        root.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create(),
                PartPose.offset(0.5F, 16.0F, -4.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        -1.0F, 12.0F, 8.0F,
                        (float) (Math.PI / 5.0D),
                        0.0F, 0.0F));

        tail.addOrReplaceChild(
                "real_tail",
                CubeListBuilder.create(),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
