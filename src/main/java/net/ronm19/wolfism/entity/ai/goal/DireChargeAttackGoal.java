package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.DireWolf;

/**
 * Commits an adult Dire Wolf to a straight-line impact charge when it has room.
 * Creepers are deliberately excluded because Wolfism's shared turn/retreat
 * protocol is safer and has higher movement priority for that enemy.
 */
public final class DireChargeAttackGoal extends Goal {
    private static final double MIN_START_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double MAX_START_DISTANCE_SQR = 18.0D * 18.0D;

    private final DireWolf wolf;
    private LivingEntity target;

    public DireChargeAttackGoal(DireWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.canStartCharge()
                || this.wolf.hasDemolitionTarget()
                || this.wolf.getTarget() == null
                || this.wolf.getTarget() instanceof Creeper) {
            return false;
        }

        LivingEntity candidate = this.wolf.getTarget();
        if (!candidate.isAlive() || !this.wolf.isValidDireCombatTarget(candidate)) {
            return false;
        }

        double distance = this.wolf.distanceToSqr(candidate);
        if (distance < MIN_START_DISTANCE_SQR || distance > MAX_START_DISTANCE_SQR) {
            return false;
        }

        if (!this.wolf.getSensing().hasLineOfSight(candidate)) {
            return false;
        }

        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.wolf.isCharging();
    }

    @Override
    public void start() {
        if (this.target != null) {
            this.wolf.beginAiCharge(this.target);
        }
    }

    @Override
    public void tick() {
        if (this.target != null) {
            this.wolf.getLookControl().setLookAt(this.target, 40.0F, 35.0F);
        }
    }

    @Override
    public void stop() {
        this.target = null;
    }
}
