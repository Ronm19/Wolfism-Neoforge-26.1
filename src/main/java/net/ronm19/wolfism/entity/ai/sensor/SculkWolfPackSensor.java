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
import net.ronm19.wolfism.entity.custom.SculkWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social awareness for wild packs and same-owner tamed Sculk Wolves. */
public final class SculkWolfPackSensor extends Sensor<SculkWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, SculkWolf wolf) {
        List<SculkWolf> packmates = level.getEntitiesOfClass(
                SculkWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isSculkPackmate(candidate));

        SculkWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        SculkWolf nearestAdult = packmates.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = packmates.stream()
                .map(SculkWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(target -> !(target instanceof Creeper))
                .filter(wolf::isValidSculkPackThreat)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        Brain<SculkWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SCULK_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SCULK_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.SCULK_PACK_SIZE.get(), packmates.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.SCULK_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<SculkWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SCULK_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SCULK_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SCULK_PACK_SIZE.get(),
                ModMemoryModuleTypes.SCULK_PACK_THREAT.get());
    }
}
