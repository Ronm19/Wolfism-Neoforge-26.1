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
import net.ronm19.wolfism.entity.custom.MagmaWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social/pack awareness for Magma Wolf without forcing broad aggro. */
public final class MagmaWolfPackSensor extends Sensor<MagmaWolf> {
    public MagmaWolfPackSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, MagmaWolf wolf) {
        List<MagmaWolf> packmates = level.getEntitiesOfClass(
                MagmaWolf.class,
                wolf.getBoundingBox().inflate(MagmaWolf.PACK_SCAN_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && wolf.isMagmaWolfPackmate(candidate));

        MagmaWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = packmates.stream()
                .map(MagmaWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(wolf::isValidMagmaThreat)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        Brain<MagmaWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_MAGMA_PACKMATE.get(), nearest);
        brain.setMemory(ModMemoryModuleTypes.MAGMA_PACK_SIZE.get(), packmates.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.MAGMA_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<MagmaWolf> brain,
            MemoryModuleType<T> memory,
            T value) {
        if (value != null) brain.setMemory(memory, value);
        else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_MAGMA_PACKMATE.get(),
                ModMemoryModuleTypes.MAGMA_PACK_SIZE.get(),
                ModMemoryModuleTypes.MAGMA_PACK_THREAT.get());
    }
}
