package net.ronm19.wolfism.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/**
 * Water-only movement controller for Water Wolf.
 *
 * <p>On land this deliberately falls back to vanilla {@link MoveControl} so the
 * wolf keeps ordinary wolf movement. Once the wolf enters water, MOVE_TO is
 * interpreted as genuine three-dimensional swimming: yaw follows the requested
 * point and velocity is steered on X/Y/Z instead of trying to solve the movement
 * as a ground path.</p>
 */
public final class WaterWolfMoveControl extends MoveControl {
    private static final double MIN_DIRECTION_LENGTH_SQR = 1.0E-6D;
    private static final float MAX_YAW_CHANGE = 90.0F;
    private static final float SPEED_LERP = 0.125F;
    private static final double STEERING_FACTOR = 0.085D;
    private static final double IDLE_DAMPING = 0.82D;

    private final WaterWolf wolf;
    private boolean aquaticMoving;

    public WaterWolfMoveControl(WaterWolf wolf) {
        super(wolf);
        this.wolf = wolf;
    }

    @Override
    public void tick() {
        if (this.wolf.isOrderedToSit()) {
            this.stopAquaticMove();
            return;
        }

        if (!this.wolf.isInWater()) {
            this.aquaticMoving = false;
            super.tick();
            return;
        }

        if (this.operation != Operation.MOVE_TO) {
            this.wolf.setSpeed(0.0F);
            Vec3 movement = this.wolf.getDeltaMovement();
            this.wolf.setDeltaMovement(
                    movement.x * IDLE_DAMPING,
                    movement.y * 0.90D,
                    movement.z * IDLE_DAMPING);
            this.aquaticMoving = false;
            return;
        }

        Vec3 delta = new Vec3(
                this.wantedX - this.wolf.getX(),
                this.wantedY - this.wolf.getY(),
                this.wantedZ - this.wolf.getZ());

        double lengthSqr = delta.lengthSqr();
        if (lengthSqr <= MIN_DIRECTION_LENGTH_SQR) {
            this.stopAquaticMove();
            return;
        }

        double length = Math.sqrt(lengthSqr);
        double xd = delta.x / length;
        double yd = delta.y / length;
        double zd = delta.z / length;

        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * 180.0F / Math.PI) - 90.0F;
        this.wolf.setYRot(this.rotlerp(this.wolf.getYRot(), targetYaw, MAX_YAW_CHANGE));
        this.wolf.setYBodyRot(this.wolf.getYRot());

        float targetSpeed = (float) (this.speedModifier * this.wolf.getAttributeValue(Attributes.MOVEMENT_SPEED));
        float newSpeed = Mth.lerp(SPEED_LERP, this.wolf.getSpeed(), targetSpeed);
        this.wolf.setSpeed(newSpeed);

        // Guardian-inspired steering, but pointed directly along the full 3D
        // requested vector. The small side oscillation keeps the motion organic
        // without overpowering the destination steering.
        double steering = Math.max(0.012D, newSpeed * STEERING_FACTOR);
        double sway = Math.sin((this.wolf.tickCount + this.wolf.getId()) * 0.45D) * 0.012D;
        double cos = Math.cos(this.wolf.getYRot() * Math.PI / 180.0D);
        double sin = Math.sin(this.wolf.getYRot() * Math.PI / 180.0D);

        this.wolf.setDeltaMovement(this.wolf.getDeltaMovement().add(
                xd * steering + sway * cos,
                yd * steering,
                zd * steering + sway * sin));

        this.wolf.getLookControl().setLookAt(
                this.wolf.getX() + xd * 2.0D,
                this.wolf.getEyeY() + yd,
                this.wolf.getZ() + zd * 2.0D,
                18.0F,
                40.0F);
        this.aquaticMoving = true;
    }

    public boolean isAquaticMoving() {
        return this.aquaticMoving;
    }

    public void stopAquaticMove() {
        this.operation = Operation.WAIT;
        this.wolf.setSpeed(0.0F);
        this.aquaticMoving = false;
    }
}
