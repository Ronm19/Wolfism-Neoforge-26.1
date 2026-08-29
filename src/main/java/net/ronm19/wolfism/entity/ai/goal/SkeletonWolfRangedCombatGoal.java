package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.SkeletonWolf;

/**
 * Keeps Skeleton Wolf in a visible ranged-skirmisher role instead of letting the
 * vanilla wolf melee goal immediately erase the identity of the species.
 *
 * <p>Outside roughly 14 blocks it closes distance; inside the 7-14 block comfort
 * band it circles/repositions; inside ~4.5 blocks this goal yields so vanilla melee
 * can take over while Bone Rattle provides the escape tool.</p>
 */
public final class SkeletonWolfRangedCombatGoal extends Goal {
    private static final double MIN_RANGED_DISTANCE = 4.5D;
    private static final double PREFERRED_MIN_DISTANCE = 7.0D;
    private static final double PREFERRED_MAX_DISTANCE = 14.0D;

    private final SkeletonWolf wolf;
    private int repathTicks;

    public SkeletonWolfRangedCombatGoal(SkeletonWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.wolf.getTarget();
        return this.canRange(target)
                && this.wolf.distanceToSqr(target) > MIN_RANGED_DISTANCE * MIN_RANGED_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.wolf.getTarget();
        return this.canRange(target)
                && this.wolf.distanceToSqr(target) > MIN_RANGED_DISTANCE * MIN_RANGED_DISTANCE;
    }

    private boolean canRange(LivingEntity target) {
        return this.wolf.canParticipateInWolfismCombat()
                && !this.wolf.isOrderedToSit()
                && !this.wolf.isInSittingPose()
                && target != null
                && this.wolf.isValidSkeletonCombatTarget(target);
    }

    @Override
    public void start() {
        this.repathTicks = 0;
    }

    @Override
    public void stop() {
        this.wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.wolf.getTarget();
        if (target == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--this.repathTicks > 0) {
            return;
        }
        this.repathTicks = 10;

        double distanceSqr = this.wolf.distanceToSqr(target);
        if (distanceSqr > PREFERRED_MAX_DISTANCE * PREFERRED_MAX_DISTANCE) {
            this.wolf.getNavigation().moveTo(target, 1.15D);
            return;
        }

        Vec3 flatAway = new Vec3(
                this.wolf.getX() - target.getX(),
                0.0D,
                this.wolf.getZ() - target.getZ());
        if (flatAway.lengthSqr() < 1.0E-5D) {
            flatAway = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            flatAway = flatAway.normalize();
        }

        double sideSign = (this.wolf.getId() & 1) == 0 ? 1.0D : -1.0D;
        Vec3 tangent = new Vec3(-flatAway.z, 0.0D, flatAway.x).scale(sideSign);

        if (distanceSqr < PREFERRED_MIN_DISTANCE * PREFERRED_MIN_DISTANCE) {
            Vec3 retreat = this.wolf.position()
                    .add(flatAway.scale(5.0D))
                    .add(tangent.scale(2.0D));
            this.wolf.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.20D);
            return;
        }

        // Small id-based side offset makes packs naturally spread instead of
        // stacking into one firing point.
        Vec3 firingPosition = target.position()
                .add(flatAway.scale(10.0D))
                .add(tangent.scale(2.5D));
        this.wolf.getNavigation().moveTo(
                firingPosition.x,
                firingPosition.y,
                firingPosition.z,
                0.90D);
    }
}
