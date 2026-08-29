package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.ronm19.wolfism.entity.custom.InfernalWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Small utility goal that turns Infernal Awareness into visible guidance.
 *
 * <p>If the tamed owner's immediate area is threatened by lava and the sensor
 * found nearby safer footing, Infernal briefly moves toward that spot and
 * looks back at the owner.</p>
 */
public final class InfernalGuideGoal extends Goal {
    private static final double SPEED = 1.05D;
    private static final int MAX_GUIDE_TICKS = 80;

    private final InfernalWolf wolf;
    private BlockPos safePos;
    private BlockPos hazard;
    private LivingEntity owner;
    private int guideTicks;

    public InfernalGuideGoal(InfernalWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.isInSittingPose()
                || this.wolf.getTarget() != null
                || this.wolf.hasFamilyDefenseEmergency()
                || this.wolf.level().dimension() != Level.NETHER) {
            return false;
        }

        this.owner = this.wolf.getOwner();
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }

        this.safePos = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.INFERNAL_SAFE_POS.get())
                .orElse(null);

        this.hazard = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.INFERNAL_LAVA_HAZARD.get())
                .orElse(null);

        if (this.safePos == null || this.hazard == null) {
            return false;
        }

        if (this.hazard.distToCenterSqr(
                this.owner.getX(),
                this.owner.getY(),
                this.owner.getZ()) > 7.0D * 7.0D) {
            return false;
        }

        return this.wolf.distanceToSqr(
                this.safePos.getX() + 0.5D,
                this.safePos.getY(),
                this.safePos.getZ() + 0.5D) > 2.0D * 2.0D;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null
                && this.owner.isAlive()
                && this.safePos != null
                && this.hazard != null
                && this.wolf.getTarget() == null
                && !this.wolf.hasFamilyDefenseEmergency()
                && !this.wolf.isOrderedToSit()
                && this.guideTicks < MAX_GUIDE_TICKS
                && this.wolf.distanceToSqr(
                        this.safePos.getX() + 0.5D,
                        this.safePos.getY(),
                        this.safePos.getZ() + 0.5D) > 1.8D * 1.8D;
    }

    @Override
    public void start() {
        this.guideTicks = 0;
        this.moveToSafePos();
    }

    @Override
    public void tick() {
        ++this.guideTicks;

        if (this.owner != null) {
            this.wolf.getLookControl().setLookAt(
                    this.owner,
                    30.0F,
                    25.0F);
        }

        if (this.guideTicks % 15 == 0) {
            this.moveToSafePos();
        }
    }

    @Override
    public void stop() {
        this.owner = null;
        this.safePos = null;
        this.hazard = null;
        this.guideTicks = 0;
        this.wolf.getNavigation().stop();
    }

    private void moveToSafePos() {
        if (this.safePos == null) {
            return;
        }

        this.wolf.getNavigation().moveTo(
                this.safePos.getX() + 0.5D,
                this.safePos.getY(),
                this.safePos.getZ() + 0.5D,
                SPEED);
    }
}
