package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/** Vanilla adult-wolf geometry with torso anchors exposed for real block-model mushrooms. */
public final class MushroomAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "mushroom_wolf"),
            "main");

    private final ModelPart upperBody;

    public MushroomAdultWolfModel(ModelPart root) {
        super(root);
        this.upperBody = root.getChild("upper_body");
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(
                AdultWolfModel.createBodyLayer(new CubeDeformation(0.0F)),
                64,
                32);
    }

    public ModelPart mushroomUpperBodyAnchor() {
        return this.upperBody;
    }

    public ModelPart mushroomBodyAnchor() {
        return this.body;
    }
}
