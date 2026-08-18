package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/** Defensive terrain response that raises a temporary dirt-class shell. */
public final class EarthDirtShellGoal extends Goal {
    private final EarthWolf wolf;

    public EarthDirtShellGoal(EarthWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.wolf.canStartDirtShell();
    }

    @Override
    public void start() {
        this.wolf.getNavigation().stop();
        this.wolf.beginDirtShell();
    }
}
