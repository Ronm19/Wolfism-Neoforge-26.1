package net.ronm19.wolfism.entity.ai.support;

/**
 * Small, side-effect-free rules shared by Christmas support and its regression
 * tests. Tick-based timings deliberately use game time, not wall-clock time.
 */
public final class ChristmasSupportRules {
    public static final int CALM_TICKS = 100;
    public static final int WARMTH_HEAL_INTERVAL = 100;
    public static final float WARMTH_HEAL_AMOUNT = 0.5F;
    public static final double PRESENT_RADIUS = 6.0D;
    public static final double PRESENT_VERTICAL_RADIUS = 3.0D;
    public static final float PRESENT_HEALTH_FRACTION = 0.70F;
    public static final float CRITICAL_HEALTH_FRACTION = 0.35F;

    private ChristmasSupportRules() {
    }

    /** Ambient sightings must not reset a non-combat healing timer. */
    public static int advanceQuietTicks(int previous, boolean directlyEngaged) {
        return directlyEngaged ? 0 : Math.min(Math.max(0, previous), 1199) + 1;
    }

    /** A stale/future entity-age timestamp is not a recent combat interaction. */
    public static boolean isRecentInteraction(int now, int timestamp, boolean hasParticipant) {
        long age = (long) now - timestamp;
        return hasParticipant && age >= 0L && age < CALM_TICKS;
    }

    /**
     * Shared persistent throttles survive save/reload. Discard impossible future
     * deadlines (e.g. an imported entity from a world with a different game clock)
     * instead of disabling healing indefinitely. The caller writes a fresh deadline.
     */
    public static boolean isThrottled(long now, long next, long maximumGap) {
        return next > now && next - now <= maximumGap;
    }

    public static boolean eligiblePatient(float health, float maxHealth,
            float threshold, double distanceSquared, double range) {
        return Float.isFinite(health) && Float.isFinite(maxHealth)
                && health > 0.0F && maxHealth > 0.0F
                && health <= maxHealth * threshold
                && distanceSquared >= 0.0D && distanceSquared <= range * range;
    }

    /** Six-block horizontal field with a bounded three-block vertical tolerance. */
    public static boolean insidePresent(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= PRESENT_RADIUS * PRESENT_RADIUS
                && Math.abs(dy) <= PRESENT_VERTICAL_RADIUS;
    }

    /** Keep stronger or infinite effects, and let regeneration count down normally. */
    public static boolean shouldRefreshEffect(boolean present, boolean infinite,
            int currentAmplifier, int remainingTicks, int wantedAmplifier) {
        if (!present) return true;
        if (currentAmplifier > wantedAmplifier) return false;
        if (currentAmplifier < wantedAmplifier) return true;
        return !infinite && remainingTicks <= 20;
    }
}
