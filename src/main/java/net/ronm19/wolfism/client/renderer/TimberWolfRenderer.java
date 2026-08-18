package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;

/**
 * Renderer for the Timber Wolf.
 *
 * <p>We deliberately extend the vanilla {@link WolfRenderer} so Timber keeps
 * Minecraft's adult and 26.1 baby wolf models, collar layer, wolf armor layer,
 * wet shading and shake animation while using Wolfism-owned textures.</p>
 */
public final class TimberWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/timber_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/timber_wolf_baby.png");

    public TimberWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}