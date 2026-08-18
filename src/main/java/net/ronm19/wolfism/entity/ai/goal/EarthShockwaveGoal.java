package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/** Close-range ground slam for damage, knockback and a brief stagger. */
public final class EarthShockwaveGoal extends Goal {
    private final EarthWolf wolf;

    public EarthShockwaveGoal(EarthWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.wolf.canStartShockwave();
    }

    @Override
    public void start() {
        this.wolf.getNavigation().stop();
        this.wolf.performShockwave();
    }
}
