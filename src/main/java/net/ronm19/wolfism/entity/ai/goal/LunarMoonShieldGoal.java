package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.LunarWolf;

/** Defensive moonlight barrier for Lunar Wolf's nearby family. */
public final class LunarMoonShieldGoal extends Goal {
    private final LunarWolf wolf;
    public LunarMoonShieldGoal(LunarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }
    @Override public boolean canUse() { return this.wolf.canStartMoonShield(); }
    @Override public boolean canContinueToUse() { return false; }
    @Override public void start() { this.wolf.performMoonShield(); }
}
