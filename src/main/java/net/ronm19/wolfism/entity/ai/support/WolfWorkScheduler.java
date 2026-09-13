package net.ronm19.wolfism.entity.ai.support;

/** Keeps an ability's original frequency while spreading a newly spawned pack across ticks. */
public final class WolfWorkScheduler {
    private WolfWorkScheduler() {}

    public static boolean isDue(long gameTime, int entityId, int interval) {
        if (interval <= 0) throw new IllegalArgumentException("interval must be positive");
        return Math.floorMod(gameTime, interval) == Math.floorMod(entityId, interval);
    }
}
