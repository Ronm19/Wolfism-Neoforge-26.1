package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/** Black-Wolf-specific renderer while preserving vanilla wolf models/layers. */
public final class BlackWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/black_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/black_wolf_baby.png");

    public BlackWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
