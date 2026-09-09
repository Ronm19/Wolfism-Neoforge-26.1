package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;

import net.ronm19.wolfism.Wolfism;

/**
 * Blade Wolf uses strict vanilla adult/baby wolf geometry.
 *
 * Vanilla WolfRenderer preserves:
 * - collar
 * - wolf armor
 * - wet shading
 * - shake animation
 * - physical sitting pose
 */
public final class BladeWolfRenderer
        extends WolfRenderer {

    private static final Identifier ADULT_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/blade_wolf.png");

    private static final Identifier BABY_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/blade_wolf_baby.png");

    public BladeWolfRenderer(
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
