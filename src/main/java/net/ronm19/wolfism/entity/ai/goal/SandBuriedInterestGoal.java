package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.ai.sensor.SandWolfDesertSensor;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Lets a tamed adult Sand Wolf quietly guide its owner toward something it has
 * sensed beneath sand. It never breaks blocks or generates loot; it simply
 * approaches and stares at the location as a player-readable clue.
 */
public final class SandBuriedInterestGoal extends Goal {
    private static final double OWNER_MAX_DISTANCE_SQR = 20.0D * 20.0D;
    private static final double STOP_DISTANCE_SQR = 2.75D * 2.75D;
    private static final double SPEED = 1.15D;

    private final SandWolf wolf;
    private BlockPos interest;
    private int guideTicks;
    private int repathDelay;
    private int cooldownTicks;

    public SandBuriedInterestGoal(SandWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }

        if (!this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || !(this.wolf.level() instanceof ServerLevel level)) {
            return false;
        }

        LivingEntity owner = this.wolf.getOwner();
        if (owner == null || !owner.isAlive() || this.wolf.distanceToSqr(owner) > OWNER_MAX_DISTANCE_SQR) {
            return false;
        }

        BlockPos remembered = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_BURIED_INTEREST.get())
                .orElse(null);
        if (remembered == null || !SandWolfDesertSensor.isBuriedInterest(level, remembered)) {
            return false;
        }

        this.interest = remembered;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.interest == null
                || this.guideTicks >= 80
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || !(this.wolf.level() instanceof ServerLevel level)) {
            return false;
        }

        LivingEntity owner = this.wolf.getOwner();
        return owner != null
                && owner.isAlive()
                && this.wolf.distanceToSqr(owner) <= 24.0D * 24.0D
                && SandWolfDesertSensor.isBuriedInterest(level, this.interest);
    }

    @Override
    public void start() {
        this.guideTicks = 0;
        this.repathDelay = 0;
    }

    @Override
    public void tick() {
        this.guideTicks++;
        if (this.interest == null) {
            return;
        }

        double x = this.interest.getX() + 0.5D;
        double y = this.interest.getY() + 0.5D;
        double z = this.interest.getZ() + 0.5D;
        this.wolf.getLookControl().setLookAt(x, y, z);

        if (this.wolf.distanceToSqr(x, y, z) <= STOP_DISTANCE_SQR) {
            this.wolf.getNavigation().stop();
            return;
        }

        if (--this.repathDelay <= 0) {
            this.repathDelay = 8;
            this.wolf.getNavigation().moveTo(x, this.interest.getY(), z, SPEED);
        }
    }

    @Override
    public void stop() {
        this.wolf.getNavigation().stop();
        this.interest = null;
        this.cooldownTicks = 100;
    }
}
