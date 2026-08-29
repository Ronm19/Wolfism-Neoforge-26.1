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
import net.ronm19.wolfism.entity.custom.InfernalWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social/combat awareness for wild Infernal packs and same-owner tamed packs.
 */
public final class InfernalWolfPackSensor extends Sensor<InfernalWolf> {
    public static final double PACK_SCAN_RADIUS = 34.0D;

    @Override
    protected void doTick(ServerLevel level, InfernalWolf wolf) {
        List<InfernalWolf> packmates = level.getEntitiesOfClass(
                InfernalWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && wolf.isInfernalWolfPackmate(candidate));

        InfernalWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        InfernalWolf nearestAdult = packmates.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = packmates.stream()
                .map(InfernalWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(target -> !(target instanceof Creeper))
                .filter(wolf::isValidInfernalPackThreat)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        Brain<InfernalWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_INFERNAL_PACKMATE.get(),
                nearest);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_INFERNAL_ADULT_PACKMATE.get(),
                nearestAdult);

        brain.setMemory(
                ModMemoryModuleTypes.INFERNAL_PACK_SIZE.get(),
                packmates.size() + 1);

        setOrErase(
                brain,
                ModMemoryModuleTypes.INFERNAL_PACK_THREAT.get(),
                sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<InfernalWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_INFERNAL_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_INFERNAL_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.INFERNAL_PACK_SIZE.get(),
                ModMemoryModuleTypes.INFERNAL_PACK_THREAT.get());
    }
}
