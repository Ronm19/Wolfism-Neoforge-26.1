package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.entity.custom.RiftWolf;

/**
 * Rift Wolf deliberately keeps vanilla wolf geometry.
 *
 * <p>The standard 64x32 adult / 32x32 baby UVs mean vanilla WolfRenderer safely
 * preserves collar, armor, sitting, wet shading and shake behavior.</p>
 */
public final class RiftWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath("wolfism", "textures/entity/wolf/rift_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath("wolfism", "textures/entity/wolf/rift_wolf_baby.png");

    public RiftWolfRenderer(
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
