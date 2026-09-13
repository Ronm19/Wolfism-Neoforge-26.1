package net.ronm19.wolfism.entity.ai.support;

import java.util.ArrayDeque;
import java.util.Deque;

/** Positional voice admission; stores no entity/world references or persistent gameplay state. */
public final class WolfVoiceBudget {
    public enum Kind { AMBIENT, GROWL, HOWL }
    private record Voice(long tick, double x, double y, double z, Kind kind, boolean important) {}
    private final Deque<Voice> voices = new ArrayDeque<>();
    private long lastTick = Long.MIN_VALUE;

    public boolean tryAcquire(long now, double x, double y, double z, Kind kind, boolean important) {
        if (now < lastTick) voices.clear();
        lastTick = now;
        while (!voices.isEmpty() && now - voices.peekFirst().tick >= 240) voices.removeFirst();
        int window = kind == Kind.HOWL ? (important ? 100 : 240) : kind == Kind.GROWL ? 40 : 80;
        int limit = kind == Kind.HOWL ? (important ? 2 : 1) : kind == Kind.GROWL ? 3 : 2;
        int nearby = 0;
        for (Voice voice : voices) {
            if (voice.kind != kind || now - voice.tick >= window
                    || important && !voice.important) continue;
            double dx = x - voice.x, dy = y - voice.y, dz = z - voice.z;
            if (dx * dx + dy * dy + dz * dz <= 24.0D * 24.0D && ++nearby >= limit) return false;
        }
        voices.addLast(new Voice(now, x, y, z, kind, important));
        // Bound bookkeeping even on servers with thousands of distant wolves.
        while (voices.size() > 512) voices.removeFirst();
        return true;
    }
}
