package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.Wolfism;

/** Dire-specific renderer preserving vanilla wolf models/layers at a much larger scale. */
public final class DireWolfRenderer extends WolfRenderer {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/dire_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/dire_wolf_baby.png");

    public DireWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }

    @Override
    public void extractRenderState(Wolf entity, WolfRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.scale *= state.isBaby ? 1.35F : 1.65F;
    }
}
