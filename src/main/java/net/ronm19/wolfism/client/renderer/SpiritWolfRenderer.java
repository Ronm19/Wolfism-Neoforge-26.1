package net.ronm19.wolfism.client.renderer;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.SpiritAdultWolfModel;
import net.ronm19.wolfism.client.model.SpiritBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.SpiritWolfRenderState;
import net.ronm19.wolfism.entity.custom.SpiritWolf;
public final class SpiritWolfRenderer extends AgeableMobRenderer<SpiritWolf,WolfRenderState,WolfModel>{
    private static final Identifier ADULT=Identifier.fromNamespaceAndPath(Wolfism.MOD_ID,"textures/entity/wolf/spirit_wolf.png"), BABY=Identifier.fromNamespaceAndPath(Wolfism.MOD_ID,"textures/entity/wolf/spirit_wolf_baby.png");
    private final WolfRenderer vanilla;
    public SpiritWolfRenderer(EntityRendererProvider.Context c){super(c,new SpiritAdultWolfModel(c.bakeLayer(SpiritAdultWolfModel.LAYER_LOCATION)),new SpiritBabyWolfModel(c.bakeLayer(SpiritBabyWolfModel.LAYER_LOCATION)),.5F);vanilla=new WolfRenderer(c);addLayer(new WolfArmorLayer(this,c.getModelSet(),c.getEquipmentRenderer()));addLayer(new WolfCollarLayer(this));}
    @Override public SpiritWolfRenderState createRenderState(){return new SpiritWolfRenderState();}
    @Override public void extractRenderState(SpiritWolf e,WolfRenderState base,float pt){vanilla.extractRenderState(e,base,pt);if(base instanceof SpiritWolfRenderState s){s.mendActive=e.isSpiritMendActive();s.guardActive=e.isSoulGuardActive();s.guardianActive=e.isGuardianOfSoulsActive();s.spiritCycle=e.tickCount+pt;}}
    @Override public Identifier getTextureLocation(WolfRenderState s){return s.isBaby?BABY:ADULT;}
}
