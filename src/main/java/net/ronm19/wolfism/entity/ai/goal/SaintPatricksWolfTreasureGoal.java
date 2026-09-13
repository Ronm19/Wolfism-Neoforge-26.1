package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SaintPatricksWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Four-Leaf Sense guidance. The wolf leads only a nearby owner toward a nearby
 * remembered treasure and never overrides combat, sitting or Wolf Staff orders.
 */
public final class SaintPatricksWolfTreasureGoal extends Goal {
    private final SaintPatricksWolf wolf;
    private BlockPos treasure;
    private int repathTicks;
    private int noPathTicks;
    private int activeTicks;

    public SaintPatricksWolfTreasureGoal(SaintPatricksWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.canProvideSaintPatricksSupport()
                || !this.wolf.isTame()
                || this.wolf.hasWolfStaffCommand()
                || this.wolf.getTarget() != null) {
            return false;
        }

        LivingEntity owner = this.wolf.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.level() != this.wolf.level()
                || this.wolf.distanceToSqr(owner) > 12.0D * 12.0D) {
            return false;
        }

        this.treasure = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get())
                .orElse(null);
        if (this.treasure == null || !this.isTreasureStillValid()) {
            this.wolf.getBrain().eraseMemory(
                    ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get());
            this.treasure = null;
            return false;
        }

        double distance = this.wolf.distanceToSqr(
                this.treasure.getX() + 0.5D,
                this.treasure.getY() + 0.5D,
                this.treasure.getZ() + 0.5D);
        return distance > 3.0D * 3.0D
                && distance <= 20.0D * 20.0D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = this.wolf.getOwner();
        if (this.treasure == null
                || owner == null
                || !owner.isAlive()
                || this.wolf.hasWolfStaffCommand()
                || !this.wolf.canProvideSaintPatricksSupport()
                || this.wolf.getTarget() != null
                || !this.isTreasureStillValid()
                || this.wolf.distanceToSqr(owner) > 18.0D * 18.0D
                || this.noPathTicks >= 60
                || this.activeTicks >= 20 * 8) {
            return false;
        }

        return this.wolf.distanceToSqr(
                this.treasure.getX() + 0.5D,
                this.treasure.getY() + 0.5D,
                this.treasure.getZ() + 0.5D) > 2.5D * 2.5D;
    }

    @Override
    public void start() {
        this.repathTicks = 10;
        this.noPathTicks = 0;
        this.activeTicks = 0;
        this.tryMoveToTreasure();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.treasure == null) return;
        ++this.activeTicks;

        this.wolf.getLookControl().setLookAt(
                this.treasure.getX() + 0.5D,
                this.treasure.getY() + 0.5D,
                this.treasure.getZ() + 0.5D);

        if (--this.repathTicks <= 0) {
            this.repathTicks = 10;
            this.tryMoveToTreasure();
        }
    }

    private void tryMoveToTreasure() {
        if (this.treasure == null) {
            return;
        }

        /*
         * Chests, barrels and ore blocks are solid targets. A normal reach
         * range of 1 may ask navigation to enter the target block and fail.
         * Reach range 2 deliberately paths to a usable adjacent position.
         */
        boolean foundPath = this.wolf.getNavigation().moveTo(
                this.treasure.getX() + 0.5D,
                this.treasure.getY() + 0.5D,
                this.treasure.getZ() + 0.5D,
                2,
                1.05D);

        if (foundPath) {
            this.noPathTicks = 0;
        } else {
            this.noPathTicks += 10;
        }
    }

    private boolean isTreasureStillValid() {
        if (this.treasure == null
                || !(this.wolf.level() instanceof ServerLevel level)
                || !level.hasChunk(this.treasure.getX() >> 4, this.treasure.getZ() >> 4)) {
            return false;
        }

        return SaintPatricksWolf.isFourLeafTreasure(
                level.getBlockState(this.treasure));
    }

    @Override
    public void stop() {
        this.wolf.getNavigation().stop();
        if (this.treasure != null
                && this.wolf.level() instanceof net.minecraft.server.level.ServerLevel level
                && this.wolf.distanceToSqr(
                        this.treasure.getX() + 0.5D,
                        this.treasure.getY() + 0.5D,
                        this.treasure.getZ() + 0.5D) <= 4.0D * 4.0D) {
            this.wolf.announceFourLeafTreasure(level, this.treasure);
        }
        this.treasure = null;
    }
}
