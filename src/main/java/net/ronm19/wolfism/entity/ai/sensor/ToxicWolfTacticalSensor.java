package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.phys.AABB;

import net.ronm19.wolfism.entity.custom.ToxicWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Tactical perception for Toxic Wolf.
 *
 * <p>The sensor can see farther than the shared physical-aggro envelope,
 * but it only records information. ToxicWolf itself decides whether that
 * observation deserves a ranged response. Awareness is not physical chase.</p>
 */
public final class ToxicWolfTacticalSensor extends Sensor<ToxicWolf> {

    private static final double SCAN_RADIUS = 40.0D;
    private static final double LOCAL_CLUSTER_RADIUS = 16.0D;

    @Override
    protected void doTick(ServerLevel level, ToxicWolf wolf) {
        AABB scan = wolf.getBoundingBox().inflate(SCAN_RADIUS);

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                scan,
                wolf::isValidToxicThreat);

        LivingEntity rangedTarget = threats.stream()
                .filter(wolf::canUseReactiveEyeAgainst)
                .min(Comparator.comparingDouble(wolf::toxicRangedPriorityScore))
                .orElse(null);

        List<LivingEntity> localThreats = threats.stream()
                .filter(target -> wolf.distanceToSqr(target)
                        <= LOCAL_CLUSTER_RADIUS * LOCAL_CLUSTER_RADIUS)
                .toList();

        LivingEntity clusterTarget = localThreats.stream()
                .max(Comparator.comparingInt(
                        candidate -> countNeighbors(
                                localThreats,
                                candidate,
                                7.0D)))
                .orElse(null);

        Brain<ToxicWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.TOXIC_RANGED_TARGET.get(),
                rangedTarget);

        setOrErase(
                brain,
                ModMemoryModuleTypes.TOXIC_CLUSTER_TARGET.get(),
                clusterTarget);

        brain.setMemory(
                ModMemoryModuleTypes.TOXIC_HOSTILE_COUNT.get(),
                localThreats.size());
    }

    private static int countNeighbors(
            List<LivingEntity> threats,
            LivingEntity center,
            double radius) {

        double radiusSqr = radius * radius;
        int count = 0;

        for (LivingEntity other : threats) {
            if (other.position().distanceToSqr(center.position()) <= radiusSqr) {
                ++count;
            }
        }

        return count;
    }

    private static <T> void setOrErase(
            Brain<ToxicWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.TOXIC_RANGED_TARGET.get(),
                ModMemoryModuleTypes.TOXIC_CLUSTER_TARGET.get(),
                ModMemoryModuleTypes.TOXIC_HOSTILE_COUNT.get());
    }
}
