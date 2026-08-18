package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/** Mid-range body charge with deliberately excessive knockback. */
public final class EarthChargeGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_START_DISTANCE_SQR = 12.0D * 12.0D;

    private final EarthWolf wolf;
    private LivingEntity target;

    public EarthChargeGoal(EarthWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (!this.wolf.canStartChargeAgainst(candidate)) {
            return false;
        }
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.wolf.isCharging();
    }

    @Override
    public void start() {
        if (!this.wolf.beginCharge(this.target)) {
            this.target = null;
        }
    }

    @Override
    public void stop() {
        if (this.wolf.isCharging()) {
            this.wolf.cancelCharge();
        }
        this.target = null;
    }
}
