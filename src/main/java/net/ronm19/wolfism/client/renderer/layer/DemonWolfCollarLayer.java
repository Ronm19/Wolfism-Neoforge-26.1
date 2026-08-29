package net.ronm19.wolfism.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;

/** Dedicated collar-only render pass for Demon Wolf. */
public final class DemonWolfCollarLayer extends RenderLayer<WolfRenderState, WolfModel> {
    private static final Identifier ADULT_COLLAR = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/wolfism_collar.png");

    private static final Identifier BABY_COLLAR = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/wolfism_baby_collar.png");

    private final WolfismAdultCollarModel adultModel;
    private final WolfismBabyCollarModel babyModel;

    public DemonWolfCollarLayer(
            RenderLayerParent<WolfRenderState, WolfModel> parent,
            WolfismAdultCollarModel adultModel,
            WolfismBabyCollarModel babyModel) {
        super(parent);
        this.adultModel = adultModel;
        this.babyModel = babyModel;
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            WolfRenderState state,
            float yRot,
            float xRot) {
        if (state.collarColor == null) {
            return;
        }

        WolfModel model = state.isBaby ? this.babyModel : this.adultModel;
        Identifier texture = state.isBaby ? BABY_COLLAR : ADULT_COLLAR;

        model.setupAnim(state);

        int color = 0xFF000000 | state.collarColor.getTextureDiffuseColor();

        renderColoredCutoutModel(
                model,
                texture,
                poseStack,
                nodeCollector,
                packedLight,
                state,
                color,
                0);
    }
}