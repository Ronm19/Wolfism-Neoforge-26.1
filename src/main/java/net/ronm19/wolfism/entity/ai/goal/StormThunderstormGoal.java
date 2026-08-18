package net.ronm19.wolfism.entity.ai.goal;

import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.StormWolf;

/** Starts Storm Wolf's localized major electrical combat zone. */
public final class StormThunderstormGoal extends Goal {
    private final StormWolf wolf;

    public StormThunderstormGoal(StormWolf wolf) {
        this.wolf = wolf;
    }

    @Override
    public boolean canUse() {
        return this.wolf.canStartThunderstorm();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        this.wolf.beginThunderstorm();
    }
}
