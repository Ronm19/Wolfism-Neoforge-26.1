package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.DireWolf;

/**
 * Executes an owner-commanded Dire demolition charge.
 *
 * <p>The player chooses the block through the interaction event; this goal only
 * handles lining the Dire up, establishing a short run-up, and committing the
 * actual charge. Creeper retreat remains priority 0 and can still interrupt it.</p>
 */
public final class DireDemolitionGoal extends Goal {
    private static final double MIN_CHARGE_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double MAX_CHARGE_DISTANCE_SQR = 14.0D * 14.0D;
    private static final double STAGING_DISTANCE = 7.0D;
    private static final double APPROACH_SPEED = 1.30D;

    private final DireWolf wolf;

    public DireDemolitionGoal(DireWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.wolf.hasDemolitionTarget()
                && this.wolf.isTame()
                && !this.wolf.isBaby()
                && !this.wolf.isOrderedToSit()
                && !this.wolf.isInSittingPose()
                && this.wolf.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos target = this.wolf.getDemolitionTarget();
        return target != null
                && this.wolf.isTame()
                && !this.wolf.isBaby()
                && !this.wolf.isOrderedToSit()
                && !this.wolf.isInSittingPose()
                && this.wolf.getTarget() == null
                && this.wolf.isDemolitionTargetValid(target);
    }

    @Override
    public void start() {
        this.wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos target = this.wolf.getDemolitionTarget();
        if (target == null || !this.wolf.isDemolitionTargetValid(target)) {
            this.wolf.cancelDemolitionCommand();
            return;
        }

        Vec3 center = Vec3.atCenterOf(target);
        this.wolf.getLookControl().setLookAt(center.x, center.y, center.z, 45.0F, 35.0F);

        if (this.wolf.isCharging()) {
            return;
        }

        Vec3 flatToTarget = new Vec3(center.x - this.wolf.getX(), 0.0D, center.z - this.wolf.getZ());
        double distanceSqr = flatToTarget.lengthSqr();

        if (this.wolf.canStartCharge()
                && distanceSqr >= MIN_CHARGE_DISTANCE_SQR
                && distanceSqr <= MAX_CHARGE_DISTANCE_SQR) {
            this.wolf.getNavigation().stop();
            this.wolf.beginDemolitionCharge(target);
            return;
        }

        Vec3 away = new Vec3(this.wolf.getX() - center.x, 0.0D, this.wolf.getZ() - center.z);
        if (away.lengthSqr() < 1.0E-5D) {
            away = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 staging = center.add(away.normalize().scale(STAGING_DISTANCE));
        this.wolf.getNavigation().moveTo(staging.x, this.wolf.getY(), staging.z, APPROACH_SPEED);
    }

    @Override
    public void stop() {
        if (!this.wolf.isCharging()) {
            this.wolf.getNavigation().stop();
        }
    }
}
