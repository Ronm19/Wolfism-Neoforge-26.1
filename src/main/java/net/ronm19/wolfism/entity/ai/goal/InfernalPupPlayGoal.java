package net.ronm19.wolfism.entity.ai.goal;

import java.util.Comparator;
import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.InfernalWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Lightweight social play for Infernal pups when the pack is safe. */
public final class InfernalPupPlayGoal extends Goal {
    private static final double SEARCH_RADIUS = 10.0D;
    private static final double SPEED = 1.05D;
    private static final int MAX_PLAY_TICKS = 80;

    private final InfernalWolf wolf;
    private InfernalWolf playmate;
    private int playTicks;

    public InfernalPupPlayGoal(InfernalWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.hasThreat()
                || this.wolf.getRandom().nextInt(120) != 0) {
            return false;
        }

        this.playmate = this.wolf.level()
                .getEntitiesOfClass(
                        InfernalWolf.class,
                        this.wolf.getBoundingBox().inflate(SEARCH_RADIUS),
                        candidate -> candidate != this.wolf
                                && candidate.isAlive()
                                && candidate.isBaby()
                                && !candidate.isOrderedToSit()
                                && this.wolf.isInfernalWolfPackmate(candidate))
                .stream()
                .min(Comparator.comparingDouble(this.wolf::distanceToSqr))
                .orElse(null);

        return this.playmate != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.playmate != null
                && this.playmate.isAlive()
                && this.wolf.isBaby()
                && !this.wolf.isOrderedToSit()
                && !this.hasThreat()
                && this.playTicks < MAX_PLAY_TICKS;
    }

    @Override
    public void start() {
        this.playTicks = 0;
    }

    @Override
    public void tick() {
        ++this.playTicks;

        if (this.playmate == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(
                this.playmate,
                30.0F,
                30.0F);

        if (this.playTicks % 8 == 0) {
            this.wolf.getNavigation().moveTo(
                    this.playmate,
                    SPEED);
        }
    }

    @Override
    public void stop() {
        this.playmate = null;
        this.playTicks = 0;
        this.wolf.getNavigation().stop();
    }

    private boolean hasThreat() {
        return this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.INFERNAL_PACK_THREAT.get())
                .filter(entity -> entity.isAlive())
                .isPresent();
    }
}
