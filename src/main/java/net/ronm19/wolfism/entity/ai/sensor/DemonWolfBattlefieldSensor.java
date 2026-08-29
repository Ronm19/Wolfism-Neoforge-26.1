package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.DemonWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Writes Demon's tactical battlefield facts into its Brain.
 *
 * <p>Cluster information is deliberately separate from target choice so Curse
 * and Hell on Earth can make group-aware decisions without turning the entity
 * into a nearest-target Goal machine.</p>
 */
public final class DemonWolfBattlefieldSensor extends Sensor<DemonWolf> {
    public DemonWolfBattlefieldSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, DemonWolf wolf) {
        Brain<DemonWolf> brain = wolf.getBrain();

        List<LivingEntity> threats =
                wolf.findDemonThreats(level, DemonWolf.COMBAT_AWARENESS_RADIUS);

        LivingEntity priorityTarget = threats.stream()
                .min(Comparator.comparingDouble(wolf::demonThreatScore))
                .orElse(null);

        LivingEntity clusterTarget = threats.stream()
                .max(Comparator
                        .comparingInt((LivingEntity target) ->
                                wolf.countDemonThreatsAround(threats, target))
                        .thenComparingDouble(target -> -wolf.demonThreatScore(target)))
                .orElse(null);

        brain.setMemory(
                ModMemoryModuleTypes.DEMON_HOSTILE_COUNT.get(),
                threats.size());

        setOrErase(
                brain,
                ModMemoryModuleTypes.DEMON_PRIORITY_TARGET.get(),
                priorityTarget);

        setOrErase(
                brain,
                ModMemoryModuleTypes.DEMON_CLUSTER_TARGET.get(),
                clusterTarget);
    }

    private static <T> void setOrErase(
            Brain<DemonWolf> brain,
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
                ModMemoryModuleTypes.DEMON_PRIORITY_TARGET.get(),
                ModMemoryModuleTypes.DEMON_CLUSTER_TARGET.get(),
                ModMemoryModuleTypes.DEMON_HOSTILE_COUNT.get());
    }
}
