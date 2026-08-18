package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;

/** Instant defensive radiant burst used when Solar Wolf is being crowded. */
public final class SolarFlareGoal extends Goal {
    private final SolarWolf wolf;

    public SolarFlareGoal(SolarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override public boolean canUse() { return this.wolf.canStartSolarFlare(); }
    @Override public boolean canContinueToUse() { return false; }
    @Override public void start() { this.wolf.performSolarFlare(); }
}
