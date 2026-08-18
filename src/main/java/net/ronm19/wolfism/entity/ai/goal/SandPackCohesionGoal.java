package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Keeps idle wild adult Sand Wolves moving as a loose open-terrain pack. */
public final class SandPackCohesionGoal extends Goal {
    private static final double START_DISTANCE_SQR = 11.0D * 11.0D;
    private static final double STOP_DISTANCE_SQR = 5.5D * 5.5D;
    private static final double MAX_DISTANCE_SQR = 32.0D * 32.0D;
    private static final double SPEED = 1.12D;

    private final SandWolf wolf;
    private SandWolf packmate;
    private int repathDelay;

    public SandPackCohesionGoal(SandWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isTame() || this.wolf.isBaby() || this.wolf.isOrderedToSit() || this.wolf.getTarget() != null) {
            return false;
        }

        Optional<SandWolf> rememberedPackmate = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_SAND_PACKMATE.get());
        if (rememberedPackmate.isEmpty()) {
            return false;
        }

        SandWolf candidate = rememberedPackmate.get();
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
