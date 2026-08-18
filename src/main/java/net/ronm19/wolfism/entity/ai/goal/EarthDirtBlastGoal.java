package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/**
 * Long-range terrain attack. The moving object is a real relocated block state;
 * no projectile entity is created.
 */
public final class EarthDirtBlastGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 7.0D * 7.0D;
    public static final double MAX_START_DISTANCE_SQR = 17.0D * 17.0D;

    private final EarthWolf wolf;
    private LivingEntity target;

    public EarthDirtBlastGoal(EarthWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (!this.wolf.canStartDirtBlastAgainst(candidate)) {
            return false;
        }
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.wolf.isDirtBlastActive();
    }

    @Override
    public void start() {
        this.wolf.getNavigation().stop();
        if (!this.wolf.beginDirtBlast(this.target)) {
            this.target = null;
        }
    }

    @Override
    public void tick() {
        if (this.target != null && this.target.isAlive()) {
            this.wolf.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        if (this.wolf.isDirtBlastActive()) {
            this.wolf.cancelDirtBlast(true);
        }
        this.target = null;
    }
}
