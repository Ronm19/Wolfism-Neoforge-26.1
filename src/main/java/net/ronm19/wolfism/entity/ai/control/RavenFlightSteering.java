package net.ronm19.wolfism.entity.ai.control;

/** Deterministic steering used only by Raven Wolf's flight motor. */
public final class RavenFlightSteering {
    private RavenFlightSteering() {}

    public record Velocity(double x, double y, double z) {}

    /**
     * Accelerate toward a bounded arrival velocity. Power strokes accelerate
     * more firmly than coasting; braking remains available throughout a glide.
     * Error is destination minus current position, in blocks.
     */
    public static Velocity step(double dx, double dy, double dz,
                                double vx, double vy, double vz,
                                double maxSpeed, boolean landing, boolean powered) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || !Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)
                || !Double.isFinite(maxSpeed) || maxSpeed <= 0.0D) {
            return new Velocity(0.0D, 0.0D, 0.0D);
        }
        maxSpeed = Math.min(maxSpeed, 0.70D);
        double horizontal = Math.hypot(dx, dz);
        double acceleration = landing ? 0.045D : powered ? 0.055D : 0.028D;
        double horizontalSpeed = Math.min(maxSpeed, horizontal * 0.35D);
        double tx = horizontal > 1.0E-8D ? dx / horizontal * horizontalSpeed : 0.0D;
        double tz = horizontal > 1.0E-8D ? dz / horizontal * horizontalSpeed : 0.0D;
        double verticalLimit = landing ? 0.18D : 0.28D;
        double ty = Math.clamp(dy * 0.30D, -verticalLimit, verticalLimit);
        if (landing && horizontal < 0.6D && dy < 0.15D) ty = Math.min(ty, -0.055D);
        double desiredLength = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (desiredLength > maxSpeed) {
            double scale = maxSpeed / desiredLength;
            tx *= scale; ty *= scale; tz *= scale;
        }
        double ax = tx - vx, ay = ty - vy, az = tz - vz;
        double change = Math.sqrt(ax * ax + ay * ay + az * az);
        // Arrival and reversals need braking even during the coast portion.
        if (vx * tx + vy * ty + vz * tz <= 0.0D
                || desiredLength < Math.sqrt(vx * vx + vy * vy + vz * vz)) {
            acceleration = Math.max(acceleration, 0.055D);
        }
        double fraction = change > acceleration ? acceleration / change : 1.0D;
        vx += ax * fraction; vy += ay * fraction; vz += az * fraction;
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length > maxSpeed) {
            double scale = maxSpeed / length;
            vx *= scale; vy *= scale; vz *= scale;
        }
        return new Velocity(vx, vy, vz);
    }
}
