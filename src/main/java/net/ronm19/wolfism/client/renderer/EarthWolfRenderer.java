package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
import org.jspecify.annotations.NonNull;

/** Earth-specific textures on the vanilla adult/baby wolf models and layers. */
public final class EarthWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/earth_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/earth_wolf_baby.png");

    public EarthWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NonNull Identifier getTextureLocation( WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
