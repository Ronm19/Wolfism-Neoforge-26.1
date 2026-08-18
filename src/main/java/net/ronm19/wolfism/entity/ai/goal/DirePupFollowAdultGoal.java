package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Wild pups stay near a remembered adult pack member instead of wandering independently. */
public final class DirePupFollowAdultGoal extends Goal {
    private static final double START_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double STOP_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double SPEED = 1.1D;

    private final DireWolf pup;
    private DireWolf adult;
    private int repathDelay;

    public DirePupFollowAdultGoal(DireWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby()
                || this.pup.isTame()
                || this.pup.isOrderedToSit()
                || this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.DIRE_PACK_THREAT.get())) {
            return false;
        }

        Optional<DireWolf> rememberedAdult = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_DIRE_ADULT_PACKMATE.get());
        if (rememberedAdult.isEmpty() || !rememberedAdult.get().isAlive()) {
            return false;
        }

        DireWolf candidate = rememberedAdult.get();
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
                && !this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.DIRE_PACK_THREAT.get())
                && this.pup.distanceToSqr(this.adult) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
    }

    @Override
    public void tick() {
        if (this.adult != null && --this.repathDelay <= 0) {
            this.repathDelay = 10;
            this.pup.getNavigation().moveTo(this.adult, SPEED);
        }
    }

    @Override
    public void stop() {
        this.adult = null;
        this.pup.getNavigation().stop();
    }
}
