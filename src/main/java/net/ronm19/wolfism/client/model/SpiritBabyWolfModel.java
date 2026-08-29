package net.ronm19.wolfism.client.model;
import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
public final class SpiritBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION=new ModelLayerLocation(Identifier.fromNamespaceAndPath(Wolfism.MOD_ID,"spirit_wolf_baby"),"main");
    public SpiritBabyWolfModel(ModelPart root){super(root);} public static LayerDefinition createSpiritBodyLayer(){return BabyWolfModel.createBodyLayer();}
}
