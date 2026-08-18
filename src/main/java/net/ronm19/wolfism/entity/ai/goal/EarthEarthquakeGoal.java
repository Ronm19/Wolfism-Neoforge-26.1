package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/** Earth Wolf's large-area ultimate. */
public final class EarthEarthquakeGoal extends Goal {
    private final EarthWolf wolf;

    public EarthEarthquakeGoal(EarthWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.wolf.canStartEarthquake();
    }

    @Override
    public boolean canContinueToUse() {
        return this.wolf.isEarthquakeActive();
    }

    @Override
    public void start() {
        this.wolf.beginEarthquake();
    }
}
