package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.OmenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Keeps idle wild Omen Wolves together as a loose pack. */
public final class OmenPackCohesionGoal extends Goal {
    private static final double START_DISTANCE_SQR =
            10.0D * 10.0D;
    private static final double STOP_DISTANCE_SQR =
            5.0D * 5.0D;
    private static final double SPEED = 1.05D;

    private final OmenWolf wolf;
    private OmenWolf packmate;

    public OmenPackCohesionGoal(OmenWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby()
                || this.wolf.isTame()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || this.wolf.hasFamilyDefenseEmergency()) {
            return false;
        }

        this.packmate = this.wolf.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.NEAREST_OMEN_PACKMATE.get())
                .orElse(null);

        return this.packmate != null
                && this.packmate.isAlive()
                && this.wolf.distanceToSqr(this.packmate)
                        > START_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.packmate != null
                && this.packmate.isAlive()
                && !this.wolf.isTame()
                && !this.wolf.isOrderedToSit()
                && this.wolf.getTarget() == null
                && !this.wolf.hasFamilyDefenseEmergency()
                && this.wolf.distanceToSqr(this.packmate)
                        > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.moveTowardPackmate();
    }

    @Override
    public void tick() {
        if (this.packmate != null
                && this.wolf.tickCount % 10 == 0) {
            this.moveTowardPackmate();
        }
    }

    @Override
    public void stop() {
        this.packmate = null;
        this.wolf.getNavigation().stop();
    }

    private void moveTowardPackmate() {
        if (this.packmate != null) {
            this.wolf.getNavigation()
                    .moveTo(
                            this.packmate,
                            SPEED);
        }
    }
}
