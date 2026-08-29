package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.EndWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social/tactical perception for End Wolves.
 *
 * <p>Phase one intentionally keeps this sensor focused on pack awareness only.
 * End-specific spatial/teleport sensing can be added later without disturbing
 * the stable family and pup foundation.</p>
 */
public final class EndWolfPackSensor extends Sensor<EndWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, EndWolf wolf) {
        List<EndWolf> packmates = level.getEntitiesOfClass(
                EndWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && wolf.isEndPackmate(candidate));

        EndWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        EndWolf nearestAdult = packmates.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = packmates.stream()
                .map(EndWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(target -> !(target instanceof Creeper))
                .filter(wolf::isValidEndCombatTarget)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        Brain<EndWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_END_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_END_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.END_PACK_SIZE.get(), packmates.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.END_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<EndWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_END_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_END_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.END_PACK_SIZE.get(),
                ModMemoryModuleTypes.END_PACK_THREAT.get());
    }
}
