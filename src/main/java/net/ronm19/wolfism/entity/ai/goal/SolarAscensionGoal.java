package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;

/** Rare high-impact celestial pillar used against crowds or boss-tier targets. */
public final class SolarAscensionGoal extends Goal {
    private final SolarWolf wolf;
    private LivingEntity target;

    public SolarAscensionGoal(SolarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartSolarAscensionAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override public boolean canContinueToUse() { return this.wolf.isSolarAscensionActive(); }
    @Override public void start() { if (!this.wolf.beginSolarAscension(this.target)) this.target = null; }
    @Override public void stop() { this.target = null; }
}
