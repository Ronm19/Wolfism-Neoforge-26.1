package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Lightweight social play so Solar pups behave like pups when the pack is safe. */
public final class SolarPupPlayGoal extends Goal {
    private static final double PLAY_RADIUS = 10.0D;
    private static final double SPEED = 1.05D;

    private final SolarWolf pup;
    private SolarWolf playmate;
    private int playTicks;

    public SolarPupPlayGoal(SolarWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby()
                || this.pup.isOrderedToSit()
                || this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get())
                || this.pup.getRandom().nextInt(120) != 0) {
            return false;
        }

        List<SolarWolf> pups = this.pup.level().getEntitiesOfClass(
                SolarWolf.class,
                this.pup.getBoundingBox().inflate(PLAY_RADIUS),
                candidate -> candidate != this.pup
                        && candidate.isAlive()
                        && candidate.isBaby()
                        && this.pup.isSolarPackmate(candidate));
        if (pups.isEmpty()) {
            return false;
        }

        this.playmate = pups.get(this.pup.getRandom().nextInt(pups.size()));
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.playmate != null
                && this.playmate.isAlive()
                && this.pup.isBaby()
                && this.playTicks < 80
                && !this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get());
    }

    @Override
    public void start() {
        this.playTicks = 0;
    }

    @Override
    public void tick() {
        this.playTicks++;
        if (this.playmate != null && this.playTicks % 8 == 0) {
            this.pup.getNavigation().moveTo(this.playmate, SPEED);
        }
    }

    @Override
    public void stop() {
        this.playmate = null;
        this.pup.getNavigation().stop();
    }
}
