package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Render-only state for Creator Wolf's large 3D beacon-style precision beam. */
public final class CreatorWolfRenderState extends WolfRenderState {
    public boolean creatorBeamActive;
    public boolean creatorBeamHasEndpoint;
    public float creatorCycle;

    public float beamStartX;
    public float beamStartY;
    public float beamStartZ;
    public float beamDx;
    public float beamDy;
    public float beamDz;
    public float beamLength;
}
