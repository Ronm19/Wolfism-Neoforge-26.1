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
import net.ronm19.wolfism.entity.custom.FrostWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social and threat awareness for Frost Wolf packs. */
public final class FrostWolfPackSensor extends Sensor<FrostWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, FrostWolf wolf) {
        Brain<FrostWolf> brain = wolf.getBrain();

        List<FrostWolf> packmates = level.getEntitiesOfClass(
                FrostWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isFrostPackmate(candidate));

        FrostWolf nearestPackmate = null;
        FrostWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;

        List<FrostWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (FrostWolf packmate : packmates) {
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
                    && !FrostWolf.isPreferredPrey(candidateTarget)
                    && wolf.isValidFrostCombatTarget(candidateTarget)
                    && sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        FrostWolf leader = adultPack.stream()
                .min(Comparator.comparing(FrostWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.FROST_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.FROST_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_FROST_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_FROST_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.FROST_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.FROST_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<FrostWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_FROST_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_FROST_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.FROST_PACK_LEADER.get(),
                ModMemoryModuleTypes.FROST_PACK_SIZE.get(),
                ModMemoryModuleTypes.FROST_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.FROST_PACK_THREAT.get());
    }
}
