package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;

/** Radiant mobility attack: a fast physical interception, not a teleport. */
public final class SolarCelestialDashGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 6.0D * 6.0D;
    public static final double MAX_START_DISTANCE_SQR = 16.0D * 16.0D;
    private final SolarWolf wolf;
    private LivingEntity target;

    public SolarCelestialDashGoal(SolarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartCelestialDashAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override public boolean canContinueToUse() { return this.wolf.isCelestialDashing(); }
    @Override public void start() { if (!this.wolf.beginCelestialDash(this.target)) this.target = null; }
    @Override public void stop() { this.target = null; }
}
