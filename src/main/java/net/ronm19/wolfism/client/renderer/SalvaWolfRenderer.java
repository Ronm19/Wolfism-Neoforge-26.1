package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/**
 * Salva uses vanilla adult/baby wolf geometry.
 * The user's corrected 64x32 / 32x32 textures carry her visual identity, while
 * vanilla WolfRenderer preserves collar, armor, sit, wet/shake and baby state.
 */
public final class SalvaWolfRenderer extends WolfRenderer {

    private static final Identifier ADULT_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/salva_wolf.png");

    private static final Identifier BABY_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "textures/entity/wolf/salva_wolf_baby.png");

    public SalvaWolfRenderer(
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
