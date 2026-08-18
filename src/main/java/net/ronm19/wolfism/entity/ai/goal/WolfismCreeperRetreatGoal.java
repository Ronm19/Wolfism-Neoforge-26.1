package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/**
 * Shared Creeper-safety intelligence for every Wolfism wolf.
 *
 * <p>This is deliberately broader than ordinary combat retreat. Any nearby
 * Wolfism wolf can react to a priming Creeper, even when that Creeper is not
 * its own target. Pups also evacuate pre-emptively when an adult Wolfism wolf
 * is already engaging a nearby Creeper, giving them time to clear the blast
 * area before a fuse begins.</p>
 *
 * <p>When a fuse starts, the goal immediately takes movement control, fans
 * wolves away from the Creeper so a crowded pack does not all choose the same
 * escape square, and applies a direct emergency dash when navigation alone is
 * not opening distance quickly enough. After the Creeper fully calms down,
 * wolves keep their distance for roughly two seconds (slightly staggered per
 * wolf) before ordinary melee movement is allowed to resume.</p>
 */
public final class WolfismCreeperRetreatGoal extends Goal {
    private static final double SAFETY_SCAN_RADIUS = 16.0D;
    private static final double MAX_SAFETY_TRACK_DISTANCE_SQR = 22.0D * 22.0D;

    private static final double ADULT_RETREAT_DISTANCE = 10.5D;
    private static final double PUP_RETREAT_DISTANCE = 12.0D;
    private static final double ADULT_RETREAT_SPEED = 2.05D;
    private static final double PUP_RETREAT_SPEED = 2.30D;

    private static final double ADULT_EMERGENCY_DISTANCE = 6.5D;
    private static final double PUP_EMERGENCY_DISTANCE = 8.0D;
    private static final double EMERGENCY_DASH_SPEED = 0.58D;
    private static final double LATE_FUSE_DASH_SPEED = 0.76D;
    private static final float LATE_FUSE_THRESHOLD = 0.42F;

    private static final double PUP_PREEMPTIVE_RADIUS = 13.0D;
    private static final double ADULT_ENGAGEMENT_SCAN_RADIUS = 14.0D;

    private static final int BASE_CALM_DOWN_TICKS = 40;
    private static final int CALM_DOWN_STAGGER_TICKS = 10;
    private static final int REPATH_INTERVAL_TICKS = 1;

    private final AbstractWolfismWolf wolf;
    private Creeper creeper;
    private int repathDelay;
    private int calmDownTicks;

    public WolfismCreeperRetreatGoal(AbstractWolfismWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Creeper candidate = this.findMostUrgentCreeper();
        if (candidate == null) {
            return false;
        }

        this.creeper = candidate;
        if (isFuseDangerous(candidate)) {
            this.refreshCalmDown();
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.creeper == null || !this.creeper.isAlive()) {
            return false;
        }

        if (this.wolf.distanceToSqr(this.creeper) > MAX_SAFETY_TRACK_DISTANCE_SQR) {
            return false;
        }

        boolean fuseDangerous = isFuseDangerous(this.creeper);
        boolean pupStillPreEvacuating = this.wolf.isBaby()
                && this.isAdultWolfismWolfEngaging(this.creeper)
                && this.wolf.distanceToSqr(this.creeper) < PUP_RETREAT_DISTANCE * PUP_RETREAT_DISTANCE;

        return fuseDangerous || this.calmDownTicks > 0 || pupStillPreEvacuating;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
        this.wolf.getNavigation().stop();
        this.wolf.setSprinting(true);
    }

    @Override
    public void tick() {
        // If a second Creeper becomes a more immediate danger, switch to it
        // instead of doggedly retreating from the original one.
        Creeper urgent = this.findMostUrgentCreeper();
        if (urgent != null && (this.creeper == null
                || !this.creeper.isAlive()
                || this.wolf.distanceToSqr(urgent) + 1.0D < this.wolf.distanceToSqr(this.creeper)
                || (isFuseDangerous(urgent) && !isFuseDangerous(this.creeper)))) {
            this.creeper = urgent;
            this.repathDelay = 0;
        }

        if (this.creeper == null || !this.creeper.isAlive()) {
            return;
        }

        boolean fuseDangerous = isFuseDangerous(this.creeper);
        if (fuseDangerous) {
            // The calm-down timer starts only after the fuse has actually wound
            // back down, and is refreshed while any swelling is still present.
            this.refreshCalmDown();
        } else if (this.calmDownTicks > 0) {
            --this.calmDownTicks;
        }

        double retreatDistance = this.wolf.isBaby() ? PUP_RETREAT_DISTANCE : ADULT_RETREAT_DISTANCE;
        double retreatDistanceSqr = retreatDistance * retreatDistance;
        double distanceSqr = this.wolf.distanceToSqr(this.creeper);

        // During the full calm-down window, maintain the safety perimeter.
        // Do not instantly charge a Creeper the exact tick its swelling reaches
        // zero; that just causes rapid re-priming and unnecessary explosions.
        if (distanceSqr >= retreatDistanceSqr) {
            this.wolf.getNavigation().stop();
            return;
        }

        if (--this.repathDelay <= 0) {
            this.repathDelay = REPATH_INTERVAL_TICKS;
            this.pathAwayFromCreeper(retreatDistance);
        }

        // Navigation is the normal escape method. This direct dash is the
        // fallback for crowding, corners, slow path creation, and pups that are
        // already too close when a fuse begins.
        if (fuseDangerous) {
            double emergencyDistance = this.wolf.isBaby()
                    ? PUP_EMERGENCY_DISTANCE
                    : ADULT_EMERGENCY_DISTANCE;
            float swelling = this.creeper.getSwelling(1.0F);

            if (distanceSqr < emergencyDistance * emergencyDistance || swelling >= LATE_FUSE_THRESHOLD) {
                double dashSpeed = swelling >= LATE_FUSE_THRESHOLD
                        ? LATE_FUSE_DASH_SPEED
                        : EMERGENCY_DASH_SPEED;
                this.applyEmergencyDash(dashSpeed);
            }
        }
    }

    @Override
    public void stop() {
        this.creeper = null;
        this.repathDelay = 0;
        this.calmDownTicks = 0;
        this.wolf.setSprinting(false);
        this.wolf.getNavigation().stop();
    }

    private Creeper findMostUrgentCreeper() {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        List<Creeper> nearby = serverLevel.getEntitiesOfClass(
                Creeper.class,
                this.wolf.getBoundingBox().inflate(SAFETY_SCAN_RADIUS),
                candidate -> candidate.isAlive()
                        && (isFuseDangerous(candidate)
                        || (this.wolf.isBaby()
                        && this.wolf.distanceToSqr(candidate) <= PUP_PREEMPTIVE_RADIUS * PUP_PREEMPTIVE_RADIUS
                        && this.isAdultWolfismWolfEngaging(candidate))));

        Creeper best = null;
        double bestScore = Double.MAX_VALUE;
        for (Creeper candidate : nearby) {
            // Priming Creepers always outrank merely pre-emptive pup evacuation.
            double score = this.wolf.distanceToSqr(candidate);
            if (isFuseDangerous(candidate)) {
                score -= 10_000.0D;
                score -= candidate.getSwelling(1.0F) * 1_000.0D;
            }

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean isAdultWolfismWolfEngaging(Creeper candidate) {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        return !serverLevel.getEntitiesOfClass(
                        AbstractWolfismWolf.class,
                        candidate.getBoundingBox().inflate(ADULT_ENGAGEMENT_SCAN_RADIUS),
                        other -> other.isAlive()
                                && !other.isBaby()
                                && other != this.wolf
                                && other.getTarget() == candidate)
                .isEmpty();
    }

    private void pathAwayFromCreeper(double retreatDistance) {
        Vec3 escape = this.escapeDirection();
        double retreatX = this.creeper.getX() + escape.x * retreatDistance;
        double retreatZ = this.creeper.getZ() + escape.z * retreatDistance;
        double speed = this.wolf.isBaby() ? PUP_RETREAT_SPEED : ADULT_RETREAT_SPEED;

        this.wolf.getNavigation().moveTo(retreatX, this.wolf.getY(), retreatZ, speed);
    }

    private void applyEmergencyDash(double speed) {
        Vec3 escape = this.escapeDirection();
        Vec3 current = this.wolf.getDeltaMovement();

        this.wolf.setDeltaMovement(
                escape.x * speed,
                current.y,
                escape.z * speed);
    }

    private Vec3 escapeDirection() {
        double dx = this.wolf.getX() - this.creeper.getX();
        double dz = this.wolf.getZ() - this.creeper.getZ();
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);

        if (horizontalLength < 1.0E-4D) {
            double radians = Math.toRadians(this.wolf.getYRot());
            dx = -Math.sin(radians);
            dz = Math.cos(radians);
            horizontalLength = 1.0D;
        }

        dx /= horizontalLength;
        dz /= horizontalLength;

        // Slight per-entity angular scatter keeps a crowded pack from trying to
        // flee through the exact same block and body-blocking its own pups.
        double scatterDegrees = Math.floorMod(this.wolf.getId() * 47, 41) - 20.0D;
        double radians = Math.toRadians(scatterDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec3(
                dx * cos - dz * sin,
                0.0D,
                dx * sin + dz * cos);
    }

    private void refreshCalmDown() {
        this.calmDownTicks = BASE_CALM_DOWN_TICKS
                + Math.floorMod(this.wolf.getId(), CALM_DOWN_STAGGER_TICKS + 1);
    }

    private static boolean isFuseDangerous(Creeper creeper) {
        return creeper.isIgnited()
                || creeper.getSwellDir() > 0
                || creeper.getSwelling(1.0F) > 0.0F;
    }
}