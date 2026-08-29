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
import net.ronm19.wolfism.entity.custom.OmenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social awareness for wild Omen packs and same-owner tamed Omen packs.
 */
public final class OmenWolfPackSensor extends Sensor<OmenWolf> {
    public static final double PACK_SCAN_RADIUS = 34.0D;

    @Override
    protected void doTick(ServerLevel level, OmenWolf wolf) {
        List<OmenWolf> packmates = level.getEntitiesOfClass(
                OmenWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && wolf.isOmenPackmate(candidate));

        OmenWolf nearest = packmates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        OmenWolf nearestAdult = packmates.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = packmates.stream()
                .map(OmenWolf::getTarget)
                .filter(Objects::nonNull)
                .filter(LivingEntity::isAlive)
                .filter(target -> !(target instanceof Creeper))
                .filter(wolf::isValidOmenPackThreat)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        /*
         * If nobody has an active combat target yet, a packmate's primary
         * Misfortune mark can still become the shared tactical focus.
         */
        if (sharedThreat == null) {
            sharedThreat = packmates.stream()
                    .map(candidate -> candidate.getMarkedTarget(level))
                    .filter(Objects::nonNull)
                    .filter(LivingEntity::isAlive)
                    .filter(target -> !(target instanceof Creeper))
                    .filter(wolf::isValidOmenPackThreat)
                    .min(Comparator.comparingDouble(wolf::distanceToSqr))
                    .orElse(null);
        }

        Brain<OmenWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_OMEN_PACKMATE.get(),
                nearest);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_OMEN_ADULT_PACKMATE.get(),
                nearestAdult);

        brain.setMemory(
                ModMemoryModuleTypes.OMEN_PACK_SIZE.get(),
                packmates.size() + 1);

        setOrErase(
                brain,
                ModMemoryModuleTypes.OMEN_PACK_THREAT.get(),
                sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<OmenWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_OMEN_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_OMEN_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.OMEN_PACK_SIZE.get(),
                ModMemoryModuleTypes.OMEN_PACK_THREAT.get());
    }
}
