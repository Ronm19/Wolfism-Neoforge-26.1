package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.entity.custom.VoidWolf;

/**
 * Void Wolf keeps vanilla adult/baby wolf geometry.
 * The approved design is texture-driven, so vanilla WolfRenderer preserves
 * collar, armor, sitting, wet/shake and baby rendering.
 */
public final class VoidWolfRenderer extends WolfRenderer {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/void_wolf.png");

    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            "wolfism",
            "textures/entity/wolf/void_wolf_baby.png");

    public VoidWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
