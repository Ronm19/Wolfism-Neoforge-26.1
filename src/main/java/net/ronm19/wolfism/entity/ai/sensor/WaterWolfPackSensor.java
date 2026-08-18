package net.ronm19.wolfism.entity.ai.sensor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social, shared-threat and rescue awareness for Water Wolf packs. */
public final class WaterWolfPackSensor extends Sensor<WaterWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, WaterWolf wolf) {
        Brain<WaterWolf> brain = wolf.getBrain();

        List<WaterWolf> packmates = level.getEntitiesOfClass(
                WaterWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isWaterPackmate(candidate));

        WaterWolf nearestPackmate = null;
        WaterWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;

        List<WaterWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (WaterWolf packmate : packmates) {
            double distance = wolf.distanceToSqr(packmate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPackmate = packmate;
            }

            if (!packmate.isBaby()) {
                adultPack.add(packmate);
                if (distance < nearestAdultDistance) {
                    nearestAdultDistance = distance;
                    nearestAdultPackmate = packmate;
                }
            }

            LivingEntity candidateTarget = packmate.getTarget();
            if (candidateTarget != null
                    && candidateTarget.isAlive()
                    && wolf.isValidWaterCombatTarget(candidateTarget)
                    && sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        WaterWolf leader = adultPack.stream()
                .min(Comparator.comparing(WaterWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.WATER_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.WATER_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_WATER_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_WATER_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.WATER_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.WATER_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<WaterWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_WATER_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_WATER_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.WATER_PACK_LEADER.get(),
                ModMemoryModuleTypes.WATER_PACK_SIZE.get(),
                ModMemoryModuleTypes.WATER_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.WATER_PACK_THREAT.get());
    }
}
