package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/** Vanilla baby-wolf geometry for the Shadow Wolf pup. */
public final class ShadowBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "shadow_wolf_baby"),
            "main");

    public ShadowBabyWolfModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createShadowBodyLayer() {
        return BabyWolfModel.createBodyLayer();
    }
}
