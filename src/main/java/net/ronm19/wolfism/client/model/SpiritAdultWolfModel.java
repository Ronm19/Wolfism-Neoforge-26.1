package net.ronm19.wolfism.client.model;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.SpiritWolfRenderState;
public final class SpiritAdultWolfModel extends AdultWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION=new ModelLayerLocation(Identifier.fromNamespaceAndPath(Wolfism.MOD_ID,"spirit_wolf"),"main");
    private final ModelPart upperBody;
    public SpiritAdultWolfModel(ModelPart root){super(root);this.upperBody=root.getChild("upper_body");}
    public static LayerDefinition createBodyLayer(){return LayerDefinition.create(AdultWolfModel.createBodyLayer(new CubeDeformation(0)),64,32);}
    @Override public void setupAnim(WolfRenderState state){super.setupAnim(state); if(!(state instanceof SpiritWolfRenderState s)||state.isSitting)return;
        this.tail.yRot+=Mth.sin(s.spiritCycle*.12F)*.055F;
        if(s.mendActive){this.head.xRot-=.12F;this.upperBody.xRot=(float)(Math.PI/2)-.06F;}
        if(s.guardActive){this.head.xRot+=.10F;this.tail.yRot*=.25F;}
        if(s.guardianActive){this.head.xRot-=.48F;this.upperBody.xRot=(float)(Math.PI/2)+.08F;this.tail.xRot=.28F;}
    }
}
