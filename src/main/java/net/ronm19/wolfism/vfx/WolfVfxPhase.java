package net.ronm19.wolfism.vfx;

/** Presentation intensity, independent of ability damage, duration or cooldown. */
public enum WolfVfxPhase {
    PASSIVE(0, 8), CAST(3, 16), PATH(1, 12), AREA(4, 22), IMPACT(5, 14), ULTIMATE(6, 28), EMERGENCY(7, 8);

    private final int priority;
    private final int lifetime;

    WolfVfxPhase(int priority, int lifetime) {
        this.priority = priority;
        this.lifetime = lifetime;
    }

    public int priority() { return priority; }
    public int lifetime() { return lifetime; }

    public static WolfVfxPhase fromEmission(int count, double x, double y, double z) {
        if (count == 1) return PATH;
        if (count <= 3) return PASSIVE;
        if (count >= 32) return ULTIMATE;
        if (Math.max(x, z) >= 1.7) return AREA;
        return CAST;
    }
}
