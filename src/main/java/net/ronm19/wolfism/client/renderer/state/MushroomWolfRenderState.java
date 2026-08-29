package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Render state for the real vanilla red-mushroom block model attached to Mushroom Wolf. */
public final class MushroomWolfRenderState extends WolfRenderState {
    public final BlockModelRenderState mushroomModel = new BlockModelRenderState();
}
