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
import net.ronm19.wolfism.entity.custom.StormWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social and shared-threat awareness for Storm Wolf packs. */
public final class StormWolfPackSensor extends Sensor<StormWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, StormWolf wolf) {
        Brain<StormWolf> brain = wolf.getBrain();

        List<StormWolf> packmates = level.getEntitiesOfClass(
                StormWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isStormPackmate(candidate));

        StormWolf nearestPackmate = null;
        StormWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;

        List<StormWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (StormWolf packmate : packmates) {
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
                    && !StormWolf.isPreferredPrey(candidateTarget)
                    && wolf.isValidStormCombatTarget(candidateTarget)
                    && sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        StormWolf leader = adultPack.stream()
                .min(Comparator.comparing(StormWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.STORM_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.STORM_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_STORM_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_STORM_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.STORM_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.STORM_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<StormWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_STORM_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_STORM_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.STORM_PACK_LEADER.get(),
                ModMemoryModuleTypes.STORM_PACK_SIZE.get(),
                ModMemoryModuleTypes.STORM_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.STORM_PACK_THREAT.get());
    }
}
