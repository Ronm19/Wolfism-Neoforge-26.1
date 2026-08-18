package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Wild Sand pups stay close to an adult in exposed desert terrain. */
public final class SandPupFollowAdultGoal extends Goal {
    private static final double START_DISTANCE_SQR = 6.5D * 6.5D;
    private static final double STOP_DISTANCE_SQR = 3.25D * 3.25D;
    private static final double SPEED = 1.15D;

    private final SandWolf pup;
    private SandWolf adult;
    private int repathDelay;

    public SandPupFollowAdultGoal(SandWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby()
                || this.pup.isTame()
                || this.pup.isOrderedToSit()
                || this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SAND_PACK_THREAT.get())) {
            return false;
        }

        Optional<SandWolf> rememberedAdult = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_SAND_ADULT_PACKMATE.get());
        if (rememberedAdult.isEmpty() || !rememberedAdult.get().isAlive()) {
            return false;
        }

        SandWolf candidate = rememberedAdult.get();
        if (this.pup.distanceToSqr(candidate) <= START_DISTANCE_SQR) {
            return false;
        }

        this.adult = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.adult != null
                && this.adult.isAlive()
                && this.pup.isBaby()
                && !this.pup.isTame()
                && !this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SAND_PACK_THREAT.get())
                && this.pup.distanceToSqr(this.adult) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
    }

    @Override
    public void tick() {
        if (this.adult != null && --this.repathDelay <= 0) {
            this.repathDelay = 8;
            this.pup.getNavigation().moveTo(this.adult, SPEED);
        }
    }

    @Override
    public void stop() {
        this.adult = null;
        this.pup.getNavigation().stop();
    }
}
