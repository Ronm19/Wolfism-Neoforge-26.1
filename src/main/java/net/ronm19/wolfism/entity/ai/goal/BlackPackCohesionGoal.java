package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Keeps idle wild Black Wolves as a loose pack, slightly tighter at night. */
public final class BlackPackCohesionGoal extends Goal {
    private static final double DAY_START_DISTANCE_SQR = 11.0D * 11.0D;
    private static final double NIGHT_START_DISTANCE_SQR = 9.0D * 9.0D;
    private static final double STOP_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double MAX_DISTANCE_SQR = 30.0D * 30.0D;
    private static final double SPEED = 1.08D;

    private final BlackWolf wolf;
    private BlackWolf packmate;
    private int repathDelay;

    public BlackPackCohesionGoal(BlackWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get())) {
            return false;
        }

        Optional<BlackWolf> rememberedPackmate = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_BLACK_PACKMATE.get());
        if (rememberedPackmate.isEmpty()) {
            return false;
        }

        BlackWolf candidate = rememberedPackmate.get();
        if (!candidate.isAlive()) {
            return false;
        }

        double distance = this.wolf.distanceToSqr(candidate);
        double startDistance = this.wolf.isNightActive() ? NIGHT_START_DISTANCE_SQR : DAY_START_DISTANCE_SQR;
        if (distance <= startDistance || distance > MAX_DISTANCE_SQR) {
            return false;
        }

        this.packmate = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.packmate != null
                && this.packmate.isAlive()
                && !this.wolf.isTame()
                && this.wolf.getTarget() == null
                && !this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get())
                && this.wolf.distanceToSqr(this.packmate) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
    }

    @Override
    public void tick() {
        if (this.packmate != null && --this.repathDelay <= 0) {
            this.repathDelay = 10;
            this.wolf.getNavigation().moveTo(this.packmate, SPEED);
        }
    }

    @Override
    public void stop() {
        this.packmate = null;
        this.wolf.getNavigation().stop();
    }
}
