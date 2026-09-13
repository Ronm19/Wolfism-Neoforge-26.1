package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.FireworkWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Fast combat/cluster awareness for Wolfism's rocket dog. */
public final class FireworkWolfAwarenessSensor extends Sensor<FireworkWolf> {
    public FireworkWolfAwarenessSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, FireworkWolf wolf) {
        Brain<FireworkWolf> brain = wolf.getBrain();
        if (!wolf.canUseFireworkCombat()) {
            brain.eraseMemory(ModMemoryModuleTypes.FIREWORK_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.FIREWORK_CLOSE_THREAT.get());
            brain.setMemory(ModMemoryModuleTypes.FIREWORK_HOSTILE_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.FIREWORK_CLUSTER_SIZE.get(), 0);
            return;
        }

        List<LivingEntity> threats = wolf.getFireworkThreats(level);
        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double score = wolf.distanceToSqr(entity);
                    if (entity instanceof Mob mob && wolf.isFireworkFamily(mob.getTarget())) {
                        score -= 9000.0D;
                    }
                    return score;
                }))
                .orElse(null);
        LivingEntity close = threats.stream()
                .filter(entity -> wolf.distanceToSqr(entity) <= 6.0D * 6.0D)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        int cluster = priority == null
                ? 0
                : (int) threats.stream()
                        .filter(entity -> entity.distanceToSqr(priority) <= 5.5D * 5.5D)
                        .count();

        setOrErase(brain, ModMemoryModuleTypes.FIREWORK_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.FIREWORK_CLOSE_THREAT.get(), close);
        brain.setMemory(ModMemoryModuleTypes.FIREWORK_HOSTILE_COUNT.get(), threats.size());
        brain.setMemory(ModMemoryModuleTypes.FIREWORK_CLUSTER_SIZE.get(), cluster);
    }

    private static <T> void setOrErase(
            Brain<FireworkWolf> brain,
            MemoryModuleType<T> key,
            T value) {
        if (value == null) brain.eraseMemory(key);
        else brain.setMemory(key, value);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.FIREWORK_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.FIREWORK_CLOSE_THREAT.get(),
                ModMemoryModuleTypes.FIREWORK_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.FIREWORK_CLUSTER_SIZE.get());
    }
}
