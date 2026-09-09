package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.custom.BlazeWolf;

/**
 * Keeps Blaze Wolf in her artillery role.
 *
 * <p>She closes only when too far to fire, holds/circles in the preferred
 * 10-18 block band, and gives ground when pressured. Blaze Charge remains the
 * emergency burst escape; this goal handles ordinary intelligent positioning.</p>
 */
public final class BlazeWolfArtilleryPositionGoal extends Goal {
    private final BlazeWolf wolf;
    private int repathTicks;

    public BlazeWolfArtilleryPositionGoal(BlazeWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = wolf.getBlazeArtilleryTarget();
        return canArtillery(target)
                && wolf.distanceToSqr(target) > 4.5D * 4.5D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = wolf.getBlazeArtilleryTarget();
        return canArtillery(target)
                && wolf.distanceToSqr(target) > 4.5D * 4.5D;
    }

    private boolean canArtillery(LivingEntity target) {
        return wolf.canParticipateInWolfismCombat()
                && !wolf.isOrderedToSit()
                && !wolf.isInSittingPose()
                && target != null
                && wolf.canUseBlazeFireballAgainst(target);
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = wolf.getBlazeArtilleryTarget();
        if (target == null) return;

        wolf.getLookControl().setLookAt(target, 35.0F, 35.0F);
        if (--repathTicks > 0) return;
        repathTicks = 8;

        double distanceSqr = wolf.distanceToSqr(target);
        if (distanceSqr > BlazeWolf.BLAZE_PREFERRED_MAX_RANGE * BlazeWolf.BLAZE_PREFERRED_MAX_RANGE) {
            wolf.getNavigation().moveTo(target, 1.12D);
            return;
        }

        Vec3 away = new Vec3(
                wolf.getX() - target.getX(),
                0.0D,
                wolf.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-5D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            away = away.normalize();
        }

        double sideSign = (wolf.getId() & 1) == 0 ? 1.0D : -1.0D;
        Vec3 tangent = new Vec3(-away.z, 0.0D, away.x).scale(sideSign);

        if (distanceSqr < BlazeWolf.BLAZE_PREFERRED_MIN_RANGE * BlazeWolf.BLAZE_PREFERRED_MIN_RANGE) {
            Vec3 retreat = wolf.position()
                    .add(away.scale(6.0D))
                    .add(tangent.scale(2.5D));
            wolf.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.18D);
            return;
        }

        // Keep moving laterally enough that Blaze looks like mobile artillery,
        // while id-based side choice prevents packs from stacking perfectly.
        Vec3 firingPosition = target.position()
                .add(away.scale(14.0D))
                .add(tangent.scale(3.2D));
        wolf.getNavigation().moveTo(
                firingPosition.x,
                firingPosition.y,
                firingPosition.z,
                0.92D);
    }
}
