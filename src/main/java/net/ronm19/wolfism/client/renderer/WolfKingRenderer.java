package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/**
 * Wolf King uses vanilla adult/baby wolf geometry.
 *
 * <p>The approved 64x32 / 32x32 textures already exist in the project; using
 * vanilla WolfRenderer preserves collar, wolf armor, sitting and wet/shake
 * behavior without introducing unnecessary model code.</p>
 */
public final class WolfKingRenderer extends WolfRenderer {

    private static final Identifier ADULT_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/wolf_king.png");

    private static final Identifier BABY_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/wolf_king_baby.png");

    public WolfKingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby
                ? BABY_TEXTURE
                : ADULT_TEXTURE;
    }
}
