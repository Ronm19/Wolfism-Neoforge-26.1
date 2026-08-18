package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.LunarWolf;

/** Short-range moonstep that repositions behind a threat or away when wounded. */
public final class LunarDreamstepGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 3.0D * 3.0D;
    public static final double MAX_START_DISTANCE_SQR = 14.0D * 14.0D;
    private final LunarWolf wolf;
    private LivingEntity target;

    public LunarDreamstepGoal(LunarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartDreamstepAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override public boolean canContinueToUse() { return false; }
    @Override public void start() { this.wolf.performDreamstep(this.target); }
    @Override public void stop() { this.target = null; }
}
