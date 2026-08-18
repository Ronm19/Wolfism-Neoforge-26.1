package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.LunarWolf;

/** Lunar Wolf's battlefield-support ultimate. */
public final class LunarMoonlitHowlGoal extends Goal {
    private final LunarWolf wolf;
    public LunarMoonlitHowlGoal(LunarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() { return this.wolf.canStartMoonlitHowl(); }
    @Override public boolean canContinueToUse() { return false; }
    @Override public void start() { this.wolf.performMoonlitHowl(); }
}
