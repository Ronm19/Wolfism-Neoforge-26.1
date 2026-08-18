package net.ronm19.wolfism.client.model;

import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.renderer.state.LunarWolfRenderState;

/** Vanilla baby geometry with subtle night-aware motion. */
public final class LunarBabyWolfModel extends BabyWolfModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "lunar_wolf_baby"), "main");
    public LunarBabyWolfModel(ModelPart root) { super(root); }
    public static LayerDefinition createLunarBodyLayer() { return BabyWolfModel.createBodyLayer(); }

    @Override
    public void setupAnim(WolfRenderState state) {
        super.setupAnim(state);
        if (!(state instanceof LunarWolfRenderState lunar) || state.isSitting) return;
        if (lunar.nightEmpowered) {
            this.head.xRot += Mth.sin(lunar.lunarCycle * 0.14F) * 0.03F;
            this.tail.yRot += Mth.sin(lunar.lunarCycle * 0.18F) * 0.09F;
        }
    }
}
