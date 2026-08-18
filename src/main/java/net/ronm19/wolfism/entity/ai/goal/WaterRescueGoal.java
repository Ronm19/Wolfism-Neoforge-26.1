package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Emergency behavior that makes Water Wolf actively swim toward a family member
 * whose air supply is running low.
 */
public final class WaterRescueGoal extends Goal {
    private static final double RESCUE_REACH_SQR = 2.25D * 2.25D;
    private final WaterWolf wolf;
    private LivingEntity rescueTarget;
    private int repathDelay;

    public WaterRescueGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> remembered = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_RESCUE_TARGET.get());
        if (remembered.isEmpty() || !this.wolf.needsWaterRescue(remembered.get())) {
            return false;
        }

        this.rescueTarget = remembered.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.rescueTarget != null
                && this.rescueTarget.isAlive()
                && this.wolf.needsWaterRescue(this.rescueTarget)
                && !this.wolf.isOrderedToSit();
    }

    @Override
    public void start() {
        this.repathDelay = 0;
        // Rescue movement outranks ordinary hunting; family defense can still
        // replace this via the higher-priority shared emergency system.
        if (!this.wolf.hasFamilyDefenseEmergency()) {
            this.wolf.setTarget(null);
        }
    }

    @Override
    public void tick() {
        if (this.rescueTarget == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(this.rescueTarget, 30.0F, 30.0F);
        double distance = this.wolf.distanceToSqr(this.rescueTarget);

        if (distance <= RESCUE_REACH_SQR) {
            this.wolf.performRescueSupport(this.rescueTarget);
            return;
        }

        if (--this.repathDelay <= 0) {
            this.repathDelay = 2;
            // Direct 3D water steering lets the wolf dive or ascend toward the
            // ally instead of asking a ground navigation graph to solve it.
            this.wolf.swimTowardRescueTarget(this.rescueTarget);
        }
    }

    @Override
    public void stop() {
        this.rescueTarget = null;
        this.wolf.stopWaterAwareMovement();
    }
}
