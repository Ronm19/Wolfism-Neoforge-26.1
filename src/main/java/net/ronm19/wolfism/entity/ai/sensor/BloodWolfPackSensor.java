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
import net.ronm19.wolfism.entity.custom.BloodWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social/tactical perception for Blood Wolves.
 *
 * <p>Wild Blood Wolves coordinate with wild Blood Wolves. Tamed Blood Wolves
 * coordinate tactically with same-owner Blood Wolves, while Wolfism's universal
 * tamed-wolf family rule still protects every tamed wolf regardless of species.</p>
 */
public final class BloodWolfPackSensor extends Sensor<BloodWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, BloodWolf wolf) {
        List<BloodWolf> packmates = level.getEntitiesOfClass(
                BloodWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && wolf.isBloodPackmate(candidate));

        BloodWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        BloodWolf nearestAdult = packmates.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        /*
         * Share a real pack fight, but never convert a Creeper fight into Blood's
         * ordinary combat architecture. Lowest remaining-health percentage is used
         * as the first tactical tie-breaker to match Blood Hunter's identity.
         */
        LivingEntity sharedThreat = packmates.stream()
                .map(BloodWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(target -> !(target instanceof Creeper))
                .filter(wolf::isValidBloodPackThreat)
                .min(Comparator
                        .comparingDouble(BloodWolfPackSensor::healthRatio)
                        .thenComparingDouble(wolf::distanceToSqr))
                .orElse(null);

        Brain<BloodWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BLOOD_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BLOOD_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.BLOOD_PACK_SIZE.get(), packmates.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.BLOOD_PACK_THREAT.get(), sharedThreat);
    }

    private static double healthRatio(LivingEntity entity) {
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
    }

    private static <T> void setOrErase(
            Brain<BloodWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_BLOOD_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_BLOOD_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.BLOOD_PACK_SIZE.get(),
                ModMemoryModuleTypes.BLOOD_PACK_THREAT.get());
    }
}
