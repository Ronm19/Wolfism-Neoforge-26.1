package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Wild pups stay near a remembered adult pack member instead of wandering independently. */
public final class SolarPupFollowAdultGoal extends Goal {
    private static final double START_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double STOP_DISTANCE_SQR = 3.5D * 3.5D;
    private static final double SPEED = 1.1D;

    private final SolarWolf pup;
    private SolarWolf adult;
    private int repathDelay;

    public SolarPupFollowAdultGoal(SolarWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby()
                || this.pup.isTame()
                || this.pup.isOrderedToSit()
                || this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get())) {
            return false;
        }

        Optional<SolarWolf> rememberedAdult = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_SOLAR_ADULT_PACKMATE.get());
        if (rememberedAdult.isEmpty() || !rememberedAdult.get().isAlive()) {
            return false;
        }

        SolarWolf candidate = rememberedAdult.get();
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
                && !this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get())
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
