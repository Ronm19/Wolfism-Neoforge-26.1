package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.HuskWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Pack awareness and exhaustive Brain-memory registration for Husk Wolf.
 */
public final class HuskWolfPackSensor extends Sensor<HuskWolf> {
    public static final double PACK_SCAN_RADIUS = 36.0D;

    @Override
    protected void doTick(ServerLevel level, HuskWolf wolf) {
        Brain<HuskWolf> brain = wolf.getBrain();

        List<HuskWolf> pack = level.getEntitiesOfClass(
                HuskWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf
                        && other.isAlive()
                        && wolf.isHuskPackmate(other));

        HuskWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        HuskWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = pack.stream()
                .map(HuskWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidHuskCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_HUSK_PACKMATE.get(),
                nearest);
        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_HUSK_ADULT_PACKMATE.get(),
                nearestAdult);

        brain.setMemory(
                ModMemoryModuleTypes.HUSK_PACK_SIZE.get(),
                pack.size() + 1);

        setOrErase(
                brain,
                ModMemoryModuleTypes.HUSK_PACK_THREAT.get(),
                sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<HuskWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_HUSK_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_HUSK_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.HUSK_PACK_SIZE.get(),
                ModMemoryModuleTypes.HUSK_PACK_THREAT.get(),
                ModMemoryModuleTypes.HUSK_SANDSTORM_COOLDOWN.get(),
                ModMemoryModuleTypes.HUSK_DRYING_HOWL_COOLDOWN.get());
    }
}
