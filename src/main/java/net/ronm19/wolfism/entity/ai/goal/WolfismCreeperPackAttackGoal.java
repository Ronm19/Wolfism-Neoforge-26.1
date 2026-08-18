package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/**
 * Shared turn-based Creeper melee for adult Wolfism wolves.
 *
 * <p>Instead of allowing an entire pack to repeatedly dogpile the Creeper and
 * restart its fuse, one wolf owns the primary attack turn while the others hold
 * a loose support ring. A single wolf on the opposite side may exploit a rear
 * opening when the Creeper is committed to another packmate.</p>
 *
 * <p>The priority-0 {@link WolfismCreeperRetreatGoal} still wins instantly the
 * moment a fuse becomes dangerous.</p>
 */
public final class WolfismCreeperPackAttackGoal extends Goal {
    private static final double PRIMARY_SPEED = 1.78D;
    private static final double REAR_STRIKE_SPEED = 1.95D;
    private static final double SUPPORT_SPEED = 1.35D;
    private static final double SUPPORT_RADIUS = 7.25D;
    private static final double SUPPORT_TOLERANCE_SQR = 1.6D * 1.6D;
    private static final double ATTACK_REACH = 2.55D;
    private static final double ATTACK_REACH_SQR = ATTACK_REACH * ATTACK_REACH;
    private static final int ATTACK_COOLDOWN_TICKS = 18;
    private static final int FAILED_ATTACK_RETRY_TICKS = 6;
    private static final int POST_STRIKE_PEEL_TICKS = 7;

    private final AbstractWolfismWolf wolf;
    private Creeper creeper;
    private int attackCooldown;
    private int postStrikePeelTicks;

    public WolfismCreeperPackAttackGoal(AbstractWolfismWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        if (!(this.wolf.getTarget() instanceof Creeper target) || !target.isAlive()) {
            return false;
        }

        this.creeper = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.wolf.isBaby()
                && !this.wolf.isOrderedToSit()
                && this.creeper != null
                && this.creeper.isAlive()
                && this.wolf.getTarget() == this.creeper;
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.postStrikePeelTicks = 0;
    }

    @Override
    public void tick() {
        if (this.creeper == null || !this.creeper.isAlive()) {
            return;
        }

        if (this.attackCooldown > 0) {
            --this.attackCooldown;
        }
        if (this.postStrikePeelTicks > 0) {
            --this.postStrikePeelTicks;
        }

        this.wolf.getLookControl().setLookAt(this.creeper, 35.0F, 35.0F);

        // RetreatGoal is priority 0 and should normally own this tick first, but
        // keeping this guard makes the attack goal safe even during scheduler
        // transitions on the exact tick swelling begins.
        if (WolfismCreeperCombatCoordinator.isFuseDangerous(this.creeper)) {
            this.wolf.getNavigation().stop();
            return;
        }

        boolean primary = WolfismCreeperCombatCoordinator.isPrimaryAttacker(this.wolf, this.creeper);
        boolean rearOpening = !primary
                && WolfismCreeperCombatCoordinator.isRearOpeningAttacker(this.wolf, this.creeper);

        if (this.postStrikePeelTicks > 0) {
            this.peelAway();
            return;
        }

        if (primary || rearOpening) {
            double speed = rearOpening ? REAR_STRIKE_SPEED : PRIMARY_SPEED;
            this.wolf.setSprinting(true);
            this.wolf.getNavigation().moveTo(this.creeper, speed);

            if (this.wolf.distanceToSqr(this.creeper) <= ATTACK_REACH_SQR
                    && this.attackCooldown <= 0
                    && WolfismCreeperCombatCoordinator.canStrikeNow(this.wolf, this.creeper)
                    && this.wolf.level() instanceof ServerLevel serverLevel) {
                boolean hit = this.wolf.doHurtTarget(serverLevel, this.creeper);
                this.attackCooldown = hit ? ATTACK_COOLDOWN_TICKS : FAILED_ATTACK_RETRY_TICKS;
                if (hit) {
                    WolfismCreeperCombatCoordinator.onSuccessfulStrike(
                            this.wolf,
                            this.creeper,
                            rearOpening);
                    this.postStrikePeelTicks = POST_STRIKE_PEEL_TICKS;
                }
            }
            return;
        }

        this.wolf.setSprinting(false);
        this.holdSupportPosition();
    }

    @Override
    public void stop() {
        this.creeper = null;
        this.attackCooldown = 0;
        this.postStrikePeelTicks = 0;
        this.wolf.setSprinting(false);
        this.wolf.getNavigation().stop();
    }

    private void holdSupportPosition() {
        double angle = Math.toRadians(Math.floorMod(this.wolf.getId() * 137, 360));
        double targetX = this.creeper.getX() + Math.cos(angle) * SUPPORT_RADIUS;
        double targetZ = this.creeper.getZ() + Math.sin(angle) * SUPPORT_RADIUS;
        double dx = this.wolf.getX() - targetX;
        double dz = this.wolf.getZ() - targetZ;

        if (dx * dx + dz * dz <= SUPPORT_TOLERANCE_SQR) {
            this.wolf.getNavigation().stop();
            return;
        }

        this.wolf.getNavigation().moveTo(targetX, this.wolf.getY(), targetZ, SUPPORT_SPEED);
    }

    private void peelAway() {
        double dx = this.wolf.getX() - this.creeper.getX();
        double dz = this.wolf.getZ() - this.creeper.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4D) {
            double radians = Math.toRadians(this.wolf.getYRot());
            dx = -Math.sin(radians);
            dz = Math.cos(radians);
            length = 1.0D;
        }

        dx /= length;
        dz /= length;
        double targetX = this.wolf.getX() + dx * 4.5D;
        double targetZ = this.wolf.getZ() + dz * 4.5D;

        this.wolf.setSprinting(true);
        this.wolf.getNavigation().moveTo(targetX, this.wolf.getY(), targetZ, PRIMARY_SPEED);

        // Navigation fallback: make sure the successful attacker immediately
        // opens at least some physical distance before the Creeper can answer.
        Vec3 current = this.wolf.getDeltaMovement();
        this.wolf.setDeltaMovement(dx * 0.34D, current.y, dz * 0.34D);
    }
}
