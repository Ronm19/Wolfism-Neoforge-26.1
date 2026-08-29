package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Extra render-only state needed for Bee-style wing gating. */
public final class BeeWolfRenderState extends WolfRenderState {
    public boolean onGround;
    public boolean catchUpFlying;
}
