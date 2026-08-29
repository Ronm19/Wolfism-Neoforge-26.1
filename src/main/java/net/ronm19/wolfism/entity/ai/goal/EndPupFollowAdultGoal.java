package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EndWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Normal wild-pup attachment to the nearest adult End Wolf. */
public final class EndPupFollowAdultGoal extends Goal {
    private static final double START_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double STOP_DISTANCE_SQR = 3.5D * 3.5D;
    private static final double SPEED = 1.10D;

    private final EndWolf wolf;
    private EndWolf adult;

    public EndPupFollowAdultGoal(EndWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.isBaby()
                || this.wolf.isTame()
                || this.wolf.isOrderedToSit()
                || this.hasThreat()) {
            return false;
        }

        this.adult = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_END_ADULT_PACKMATE.get())
                .orElse(null);

        return this.adult != null
                && this.adult.isAlive()
                && this.wolf.distanceToSqr(this.adult) > START_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.adult != null
                && this.adult.isAlive()
                && this.wolf.isBaby()
                && !this.wolf.isTame()
                && !this.wolf.isOrderedToSit()
                && !this.hasThreat()
                && this.wolf.distanceToSqr(this.adult) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.moveToAdult();
    }

    @Override
    public void tick() {
        if (this.adult != null) {
            this.wolf.getLookControl().setLookAt(this.adult, 25.0F, 25.0F);
            if (this.wolf.tickCount % 10 == 0) {
                this.moveToAdult();
            }
        }
    }

    @Override
    public void stop() {
        this.adult = null;
        this.wolf.getNavigation().stop();
    }

    private void moveToAdult() {
        if (this.adult != null) {
            this.wolf.getNavigation().moveTo(this.adult, SPEED);
        }
    }

    private boolean hasThreat() {
        return this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.END_PACK_THREAT.get())
                .filter(entity -> entity.isAlive())
                .isPresent();
    }
}
