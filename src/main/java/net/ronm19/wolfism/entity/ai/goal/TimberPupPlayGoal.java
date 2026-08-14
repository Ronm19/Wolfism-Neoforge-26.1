package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Lightweight social play so Timber pups behave like pups when the pack is safe. */
public final class TimberPupPlayGoal extends Goal {
    private static final double PLAY_RADIUS = 10.0D;
    private static final double SPEED = 1.05D;

    private final TimberWolf pup;
    private TimberWolf playmate;
    private int playTicks;

    public TimberPupPlayGoal(TimberWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby()
                || this.pup.isOrderedToSit()
                || this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.TIMBER_PACK_THREAT.get())
                || this.pup.getRandom().nextInt(120) != 0) {
            return false;
        }

        List<TimberWolf> pups = this.pup.level().getEntitiesOfClass(
                TimberWolf.class,
                this.pup.getBoundingBox().inflate(PLAY_RADIUS),
                candidate -> candidate != this.pup
                        && candidate.isAlive()
                        && candidate.isBaby()
                        && this.pup.isTimberPackmate(candidate));
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
                && !this.pup.getBrain().hasMemoryValue(ModMemoryModuleTypes.TIMBER_PACK_THREAT.get());
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
