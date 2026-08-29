package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/** Vanilla baby-wolf geometry with its torso exposed for two smaller mushroom decorations. */
public final class MushroomBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "mushroom_wolf_baby"),
            "main");

    public MushroomBabyWolfModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createMushroomBodyLayer() {
        return BabyWolfModel.createBodyLayer();
    }

    public ModelPart mushroomBodyAnchor() {
        return this.body;
    }
}
