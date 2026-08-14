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
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social perception for Timber Wolves.
 *
 * <p>Wild Timbers coordinate with wild Timbers. Tamed Timbers coordinate
 * tactically with Timbers sharing their owner, while Wolfism's universal tamed
 * wolf family rule still prevents hostility toward every other tamed wolf.</p>
 */
public final class TimberWolfPackSensor extends Sensor<TimberWolf> {
    public static final double PACK_SCAN_RADIUS = 28.0D;

    @Override
    protected void doTick(ServerLevel level, TimberWolf wolf) {
        Brain<TimberWolf> brain = wolf.getBrain();

        List<TimberWolf> packmates = level.getEntitiesOfClass(
                TimberWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isTimberPackmate(candidate));

        TimberWolf nearestPackmate = null;
        TimberWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<TimberWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (TimberWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidTimberCombatTarget(candidateTarget)) {
                continue;
            }

            if (TimberWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        TimberWolf leader = adultPack.stream()
                .min(Comparator.comparing(TimberWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.TIMBER_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.TIMBER_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_TIMBER_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_TIMBER_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.TIMBER_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.TIMBER_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get(), sharedHuntTarget);
    }

    private static <T> void setOrErase(Brain<TimberWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_TIMBER_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_TIMBER_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.TIMBER_PACK_LEADER.get(),
                ModMemoryModuleTypes.TIMBER_PACK_SIZE.get(),
                ModMemoryModuleTypes.TIMBER_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.TIMBER_PACK_THREAT.get(),
                ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get());
    }
}
