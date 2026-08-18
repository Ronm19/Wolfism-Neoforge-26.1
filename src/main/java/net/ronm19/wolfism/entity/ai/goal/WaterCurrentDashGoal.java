package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/**
 * A real underwater interception dash. The wolf charges briefly, accelerates
 * through the water, then deals damage only if its body reaches the target.
 */
public final class WaterCurrentDashGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 3.0D * 3.0D;
    public static final double MAX_START_DISTANCE_SQR = 12.0D * 12.0D;
    public static final double IMPACT_DISTANCE_SQR = 2.15D * 2.15D;

    private static final int CHARGE_TICKS = 4;
    private static final int MAX_DASH_TICKS = 12;

    private final WaterWolf wolf;
    private LivingEntity target;
    private int chargeTicks;
    private int dashTicks;
    private boolean dashStarted;
    private boolean finished;

    public WaterCurrentDashGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (!this.wolf.canStartCurrentDashAgainst(candidate)) {
            return false;
        }

        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.finished
                && this.target != null
                && this.target.isAlive()
                && this.wolf.isInWater()
                && this.target.isInWater()
                && this.chargeTicks + this.dashTicks < CHARGE_TICKS + MAX_DASH_TICKS;
    }

    @Override
    public void start() {
        this.chargeTicks = 0;
        this.dashTicks = 0;
        this.dashStarted = false;
        this.finished = false;
        this.wolf.reserveCurrentDashTarget(this.target);
        this.wolf.stopWaterAwareMovement();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            this.finished = true;
            return;
        }

        this.wolf.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.chargeTicks < CHARGE_TICKS) {
            ++this.chargeTicks;
            return;
        }

        this.dashStarted = true;
        ++this.dashTicks;

        if (!this.wolf.propelCurrentDashToward(this.target)) {
            this.finished = true;
            return;
        }

        if (this.wolf.distanceToSqr(this.target) <= IMPACT_DISTANCE_SQR) {
            this.wolf.performCurrentDashImpact(this.target);
            this.finished = true;
            return;
        }

        if (this.dashTicks >= MAX_DASH_TICKS) {
            this.finished = true;
        }
    }

    @Override
    public void stop() {
        this.wolf.finishCurrentDash(this.dashStarted);
        this.target = null;
        this.chargeTicks = 0;
        this.dashTicks = 0;
        this.dashStarted = false;
        this.finished = false;
    }
}
