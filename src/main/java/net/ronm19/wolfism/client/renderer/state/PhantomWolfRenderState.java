package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Client-only render data added on top of the normal vanilla wolf state. */
public final class PhantomWolfRenderState extends WolfRenderState {
    public float flapTime;
    public boolean isFlying;
    public boolean isSwooping;
}
