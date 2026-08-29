package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/** The pup keeps vanilla baby geometry; its sensory growths have not matured yet. */
public final class SculkBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "sculk_wolf_baby"),
            "main");

    public SculkBabyWolfModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        return BabyWolfModel.createBodyLayer();
    }
}
