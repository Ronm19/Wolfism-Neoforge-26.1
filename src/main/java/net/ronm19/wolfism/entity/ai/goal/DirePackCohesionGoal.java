package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Keeps idle wild adult Dire Wolves moving as a loose pack. */
public final class DirePackCohesionGoal extends Goal {
    private static final double START_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double STOP_DISTANCE_SQR = 6.0D * 6.0D;
    private static final double MAX_DISTANCE_SQR = 30.0D * 30.0D;
    private static final double SPEED = 1.05D;

    private final DireWolf wolf;
    private DireWolf packmate;
    private int repathDelay;

    public DirePackCohesionGoal(DireWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isTame() || this.wolf.isBaby() || this.wolf.isOrderedToSit() || this.wolf.getTarget() != null) {
            return false;
        }

        Optional<DireWolf> rememberedPackmate = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_DIRE_PACKMATE.get());
        if (rememberedPackmate.isEmpty()) {
            return false;
        }

        DireWolf candidate = rememberedPackmate.get();
        if (!candidate.isAlive()) {
            return false;
        }

        double distance = this.wolf.distanceToSqr(candidate);
        if (distance <= START_DISTANCE_SQR || distance > MAX_DISTANCE_SQR) {
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
