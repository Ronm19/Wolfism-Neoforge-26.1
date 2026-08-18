package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Enemy;
import net.ronm19.wolfism.entity.custom.LunarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Moonlit Sight: owner-centered hostile detection with a much larger night radius. */
public final class LunarWolfNightWatchSensor extends Sensor<LunarWolf> {
    @Override
    protected void doTick(ServerLevel level, LunarWolf wolf) {
        Brain<LunarWolf> brain = wolf.getBrain();
        LivingEntity anchor = wolf.getOwner() != null ? wolf.getOwner() : wolf;
        double radius = wolf.isNightEmpowered() ? 40.0D : 24.0D;

        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                anchor.getBoundingBox().inflate(radius),
                entity -> entity instanceof Enemy && wolf.isValidLunarCombatTarget(entity));

        LivingEntity best = candidates.stream()
                .min(Comparator.comparingDouble(entity -> score(wolf, anchor, entity)))
                .orElse(null);
        if (best != null) brain.setMemory(ModMemoryModuleTypes.LUNAR_WATCH_THREAT.get(), best);
        else brain.eraseMemory(ModMemoryModuleTypes.LUNAR_WATCH_THREAT.get());
    }

    private static double score(LunarWolf wolf, LivingEntity anchor, LivingEntity threat) {
        double score = anchor.distanceToSqr(threat);
        if (threat instanceof net.minecraft.world.entity.Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target == anchor || wolf.isLunarFamilyMember(target)) score -= 400.0D;
        }
        return score;
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(ModMemoryModuleTypes.LUNAR_WATCH_THREAT.get());
    }
}
