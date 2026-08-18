package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Render-only Lunar state, including synchronized beam and ability poses. */
public final class LunarWolfRenderState extends WolfRenderState {
    public boolean nightEmpowered;
    public boolean beamActive;
    public boolean shieldActive;
    public boolean dreamstepActive;
    public boolean howlActive;
    public float lunarCycle;
    public float lunarIntensity;
    public boolean beamHasEndpoint;
    public float beamStartY;
    public float beamDx;
    public float beamDy;
    public float beamDz;
    public float beamLength;
}
