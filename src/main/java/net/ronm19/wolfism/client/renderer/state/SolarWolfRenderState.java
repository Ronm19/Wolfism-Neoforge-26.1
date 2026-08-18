package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Render-only state for Solar Wolf's celestial ability poses and Sunbeam geometry. */
public final class SolarWolfRenderState extends WolfRenderState {
    public boolean directSunlight;
    public boolean solarFlareActive;
    public boolean sunbeamActive;
    public boolean celestialDashActive;
    public boolean solarAscensionActive;
    public float solarCycle;
    public float solarIntensity;

    // Sunbeam is represented entirely in render-state data so submit() does not
    // need to reach back into the living entity after extraction.
    public boolean sunbeamHasEndpoint;
    public float sunbeamStartY;
    public float sunbeamDx;
    public float sunbeamDy;
    public float sunbeamDz;
    public float sunbeamLength;
}
