package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/**
 * Client-only render data for Angel Wolf's wing animation.
 *
 * <p>{@link #isFlying} mirrors Angel Wolf's synchronized Graceful Flight
 * state. Ordinary jumps/falls therefore do not trigger full wing flapping.</p>
 */
public final class AngelWolfRenderState extends WolfRenderState {
    public float flapTime;
    public float flightBlend;
    public boolean isFlying;
}
