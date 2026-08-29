package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.DrownedWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack awareness and Brain-memory registration for Drowned Wolf. */
public final class DrownedWolfPackSensor extends Sensor<DrownedWolf> {
    public static final double PACK_SCAN_RADIUS = 40.0D;

    @Override
    protected void doTick(ServerLevel level, DrownedWolf wolf) {
        Brain<DrownedWolf> brain = wolf.getBrain();

        List<DrownedWolf> pack = level.getEntitiesOfClass(
                DrownedWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf
                        && other.isAlive()
                        && wolf.isDrownedPackmate(other));

        DrownedWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        DrownedWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = pack.stream()
                .map(DrownedWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidDrownedCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_DROWNED_PACKMATE.get(),
                nearest);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_DROWNED_ADULT_PACKMATE.get(),
                nearestAdult);

        brain.setMemory(
                ModMemoryModuleTypes.DROWNED_PACK_SIZE.get(),
                pack.size() + 1);

        setOrErase(
                brain,
                ModMemoryModuleTypes.DROWNED_PACK_THREAT.get(),
                sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<DrownedWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_DROWNED_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_DROWNED_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.DROWNED_PACK_SIZE.get(),
                ModMemoryModuleTypes.DROWNED_PACK_THREAT.get(),
                ModMemoryModuleTypes.DROWNED_TRIDENT_COOLDOWN.get(),
                ModMemoryModuleTypes.DROWNED_DROWN_OTHERS_COOLDOWN.get());
    }
}
