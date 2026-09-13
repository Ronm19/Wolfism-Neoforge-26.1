package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.EasterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Guides Easter Wolf toward a holiday cache or nearby spring activity. */
public final class EasterWolfSpringGoal extends Goal {
    private final EasterWolf wolf;
    private BlockPos destination;
    private int repathTicks;
    private int activeTicks;
    private int noPathTicks;

    public EasterWolfSpringGoal(EasterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!wolf.canProvideEasterSupport()
                || wolf.hasWolfStaffCommand()
                || wolf.getTarget() != null) {
            return false;
        }
        LivingEntity owner = wolf.getOwner();
        if (owner == null || wolf.distanceToSqr(owner) > 20.0D * 20.0D) {
            return false;
        }
        destination = wolf.getEasterCachePosition();
        if (destination == null) {
            destination = wolf.getBrain()
                    .getMemory(ModMemoryModuleTypes.EASTER_NATURE_POS.get())
                    .orElse(null);
        }
        return destination != null
                && wolf.distanceToSqr(
                        destination.getX() + 0.5D,
                        destination.getY(),
                        destination.getZ() + 0.5D) > 4.0D * 4.0D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = wolf.getOwner();
        return destination != null
                && owner != null
                && wolf.canProvideEasterSupport()
                && !wolf.hasWolfStaffCommand()
                && wolf.getTarget() == null
                && wolf.isEasterDestinationValid(destination)
                && wolf.distanceToSqr(
                        destination.getX() + 0.5D,
                        destination.getY(),
                        destination.getZ() + 0.5D) > 2.7D * 2.7D
                && activeTicks < 20 * 12
                && noPathTicks < 60
                && destination.distSqr(owner.blockPosition()) <= 28.0D * 28.0D;
    }

    @Override
    public void start() {
        repathTicks = 0;
        activeTicks = 0;
        noPathTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ++activeTicks;
        wolf.getLookControl().setLookAt(
                destination.getX() + 0.5D,
                destination.getY() + 0.5D,
                destination.getZ() + 0.5D);
        if (--repathTicks <= 0) {
            repathTicks = 10;
            boolean moved = wolf.getNavigation().moveTo(
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    2,
                    1.15D);
            if (moved) noPathTicks = 0;
            else noPathTicks += 10;
        }
        wolf.tryBunnyHopToward(destination);
    }

    @Override
    public void stop() {
        wolf.getNavigation().stop();
        destination = null;
    }
}
