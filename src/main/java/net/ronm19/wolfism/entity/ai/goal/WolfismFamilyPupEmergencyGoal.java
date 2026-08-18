package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/** Tamed pups never retaliate; a family emergency overrides play/follow with flight. */
public final class WolfismFamilyPupEmergencyGoal extends Goal {
    private static final double ESCAPE_SPEED = 1.90D;
    private static final double ESCAPE_DISTANCE = 7.0D;
    private static final double DIRECT_DASH_DISTANCE_SQR = 4.5D * 4.5D;

    private final AbstractWolfismWolf pup;

    public WolfismFamilyPupEmergencyGoal(AbstractWolfismWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity threat = this.pup.getFamilyDefenseTarget();
        return this.pup.isBaby()
                && this.pup.hasFamilyDefenseEmergency()
                && threat != null
                && threat.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.pup.setTarget(null);
        this.pup.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity threat = this.pup.getFamilyDefenseTarget();
        if (threat == null || !threat.isAlive()) {
            return;
        }

        this.pup.setTarget(null);
        this.pup.getLookControl().setLookAt(threat, 35.0F, 30.0F);

        double dx = this.pup.getX() - threat.getX();
        double dz = this.pup.getZ() - threat.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            double angle = Math.toRadians(Math.floorMod(this.pup.getId() * 137, 360));
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }

        dx /= length;
        dz /= length;
        double targetX = this.pup.getX() + dx * ESCAPE_DISTANCE;
        double targetZ = this.pup.getZ() + dz * ESCAPE_DISTANCE;
        this.pup.getNavigation().moveTo(targetX, this.pup.getY(), targetZ, ESCAPE_SPEED);

        if (this.pup.distanceToSqr(threat) < DIRECT_DASH_DISTANCE_SQR) {
            Vec3 current = this.pup.getDeltaMovement();
            this.pup.setDeltaMovement(dx * 0.30D, current.y, dz * 0.30D);
        }
    }

    @Override
    public void stop() {
        this.pup.getNavigation().stop();
    }
}
