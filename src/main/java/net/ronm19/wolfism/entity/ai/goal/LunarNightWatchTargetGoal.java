package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.LunarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Turns Moonlit Sight's remembered night-watch threat into a real target. */
public final class LunarNightWatchTargetGoal extends Goal {
    private final LunarWolf wolf;
    private LivingEntity threat;

    public LunarNightWatchTargetGoal(LunarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) return false;
        LivingEntity candidate = this.wolf.getBrain().getMemory(ModMemoryModuleTypes.LUNAR_WATCH_THREAT.get()).orElse(null);
        if (candidate == null || !this.wolf.isValidLunarCombatTarget(candidate)) return false;
        LivingEntity current = this.wolf.getTarget();
        if (current != null && current.isAlive() && current != candidate) return false;
        this.threat = candidate;
        return true;
    }

    @Override public void start() { if (this.threat != null) this.wolf.setTarget(this.threat); }
    @Override public void stop() { this.threat = null; }
}
