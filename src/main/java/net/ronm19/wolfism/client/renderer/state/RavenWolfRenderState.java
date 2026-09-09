package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;

/**
 * Client render state for Raven Wolf.
 */
public final class RavenWolfRenderState extends WolfRenderState {

    public boolean isFlying;
    public boolean isLanding;
    public boolean eyeOfRavenActive;
    public boolean ravensRageActive;
    public float wingAngle;
    public float wingTipAngle;
    public float flightPitch;
    public float flightBank;
}
