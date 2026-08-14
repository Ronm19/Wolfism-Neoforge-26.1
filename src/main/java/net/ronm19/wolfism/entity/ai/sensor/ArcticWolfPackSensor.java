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
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social perception for Arctic Wolves.
 *
 * <p>Wild Arctics coordinate with wild Arctics. Tamed Arctics coordinate
 * tactically with Arctics sharing their owner, while Wolfism's universal tamed
 * wolf family rule still prevents hostility toward every other tamed wolf.</p>
 */
public final class ArcticWolfPackSensor extends Sensor<ArcticWolf> {
    public static final double PACK_SCAN_RADIUS = 28.0D;

    @Override
    protected void doTick(ServerLevel level, ArcticWolf wolf) {
        Brain<ArcticWolf> brain = wolf.getBrain();

        List<ArcticWolf> packmates = level.getEntitiesOfClass(
                ArcticWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isArcticPackmate(candidate));

        ArcticWolf nearestPackmate = null;
        ArcticWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<ArcticWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (ArcticWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidArcticCombatTarget(candidateTarget)) {
                continue;
            }

            if (ArcticWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        ArcticWolf leader = adultPack.stream()
                .min(Comparator.comparing(ArcticWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.ARCTIC_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.ARCTIC_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_ARCTIC_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_ARCTIC_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.ARCTIC_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.ARCTIC_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.ARCTIC_HUNT_TARGET.get(), sharedHuntTarget);
    }

    private static <T> void setOrErase(Brain<ArcticWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_ARCTIC_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_ARCTIC_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.ARCTIC_PACK_LEADER.get(),
                ModMemoryModuleTypes.ARCTIC_PACK_SIZE.get(),
                ModMemoryModuleTypes.ARCTIC_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.ARCTIC_PACK_THREAT.get(),
                ModMemoryModuleTypes.ARCTIC_HUNT_TARGET.get());
    }
}
