package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;

import net.ronm19.wolfism.Wolfism;

/**
 * Ancient Wolf keeps vanilla adult/baby wolf geometry.
 *
 * His identity is entirely texture + gameplay driven.
 */
public final class AncientWolfRenderer
        extends WolfRenderer {

    private static final Identifier ADULT_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/ancient_wolf.png");

    private static final Identifier BABY_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/ancient_wolf_baby.png");

    public AncientWolfRenderer(
            EntityRendererProvider.Context context) {

        super(context);
    }

    @Override
    public Identifier getTextureLocation(
            WolfRenderState state) {

        return state.isBaby
                ? BABY_TEXTURE
                : ADULT_TEXTURE;
    }
}