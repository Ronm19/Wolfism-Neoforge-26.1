package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SculkWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Keeps wild adult Sculk Wolves from slowly dissolving into unrelated solo mobs.
 *
 * <p>Tamed wolves use normal owner-follow behavior instead.</p>
 */
public final class SculkPackCohesionGoal extends Goal {
    private static final double START_DISTANCE_SQR = 10.0D * 10.0D;
    private static final double STOP_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double SPEED = 1.05D;

    private final SculkWolf wolf;
    private SculkWolf packmate;

    public SculkPackCohesionGoal(SculkWolf wolf) {
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
                .getMemory(ModMemoryModuleTypes.NEAREST_SCULK_PACKMATE.get())
                .orElse(null);

        return this.packmate != null
                && this.packmate.isAlive()
                && this.wolf.distanceToSqr(this.packmate) > START_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.packmate != null
                && this.packmate.isAlive()
                && !this.wolf.isTame()
                && !this.wolf.isOrderedToSit()
                && this.wolf.getTarget() == null
                && !this.wolf.hasFamilyDefenseEmergency()
                && this.wolf.distanceToSqr(this.packmate) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.moveTowardPackmate();
    }

    @Override
    public void tick() {
        if (this.packmate != null && this.wolf.tickCount % 10 == 0) {
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
            this.wolf.getNavigation().moveTo(this.packmate, SPEED);
        }
    }
}
