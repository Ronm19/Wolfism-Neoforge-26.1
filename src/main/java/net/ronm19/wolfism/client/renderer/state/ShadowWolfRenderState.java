package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Synced client-readable state for Shadow Wolf ability poses. */
public final class ShadowWolfRenderState extends WolfRenderState {
    public boolean voidDashActive;
    public boolean shadowBladesActive;
    public boolean duskVeilActive;
    public boolean shadowAssassinActive;
    public float shadowCycle;
}
