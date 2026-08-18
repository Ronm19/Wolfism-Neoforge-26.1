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
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social perception for Dire Wolves.
 *
 * <p>Wild Dires coordinate with wild Dires. Tamed Dires coordinate
 * tactically with Dires sharing their owner, while Wolfism's universal tamed
 * wolf family rule still prevents hostility toward every other tamed wolf.</p>
 */
public final class DireWolfPackSensor extends Sensor<DireWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, DireWolf wolf) {
        Brain<DireWolf> brain = wolf.getBrain();

        List<DireWolf> packmates = level.getEntitiesOfClass(
                DireWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isDirePackmate(candidate));

        DireWolf nearestPackmate = null;
        DireWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<DireWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (DireWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidDireCombatTarget(candidateTarget)) {
                continue;
            }

            if (DireWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        DireWolf leader = adultPack.stream()
                .min(Comparator.comparing(DireWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.DIRE_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.DIRE_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_DIRE_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_DIRE_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.DIRE_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.DIRE_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.DIRE_HUNT_TARGET.get(), sharedHuntTarget);
    }

    private static <T> void setOrErase(Brain<DireWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_DIRE_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_DIRE_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.DIRE_PACK_LEADER.get(),
                ModMemoryModuleTypes.DIRE_PACK_SIZE.get(),
                ModMemoryModuleTypes.DIRE_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.DIRE_PACK_THREAT.get(),
                ModMemoryModuleTypes.DIRE_HUNT_TARGET.get());
    }
}
