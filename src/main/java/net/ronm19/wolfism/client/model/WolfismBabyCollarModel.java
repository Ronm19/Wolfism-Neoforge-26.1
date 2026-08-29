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

/** Dedicated collar-only model for baby custom Wolfism wolves. */
public final class WolfismBabyCollarModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("wolfism", "wolfism_baby_collar"),
            "main");

    private static final float COLLAR_INFLATION = 0.10F;

    public WolfismBabyCollarModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 18.25F, -4.0F));

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -3.0F, -2.0F, -4.0F,
                                6.0F, 4.0F, 8.0F,
                                new CubeDeformation(COLLAR_INFLATION)),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        root.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create(),
                PartPose.offset(1.5F, 21.0F, 3.0F));

        root.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create(),
                PartPose.offset(-1.5F, 21.0F, 3.0F));

        root.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create(),
                PartPose.offset(1.5F, 21.0F, -3.0F));

        root.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create(),
                PartPose.offset(-1.5F, 21.0F, -3.0F));

        PartDefinition tail = root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        0.0F, 19.0F, 3.0F,
                        -0.5236F, 0.0F, 0.0F));

        tail.addOrReplaceChild(
                "tail_rotation",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        0.0F, -0.6F, 0.2F,
                        -3.1F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
