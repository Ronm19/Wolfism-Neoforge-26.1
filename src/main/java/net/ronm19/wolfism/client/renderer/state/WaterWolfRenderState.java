package net.ronm19.wolfism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Extra render-only state used by Water Wolf's aquatic animation. */
public final class WaterWolfRenderState extends WolfRenderState {
    public boolean isSwimmingInWater;
    public boolean isUnderwater;
    public boolean bubbleShelterActive;
    public float swimCycle;
    public float swimAmount;
}
