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
import net.ronm19.wolfism.entity.custom.FireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social/combat awareness for Fire Wolf packs. */
public final class FireWolfPackSensor extends Sensor<FireWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, FireWolf wolf) {
        Brain<FireWolf> brain = wolf.getBrain();

        List<FireWolf> packmates = level.getEntitiesOfClass(
                FireWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isFirePackmate(candidate));

        FireWolf nearestPackmate = null;
        FireWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<FireWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (FireWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidFireCombatTarget(candidateTarget)) {
                continue;
            }

            if (FireWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        FireWolf leader = adultPack.stream()
                .min(Comparator.comparing(FireWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.FIRE_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.FIRE_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_FIRE_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_FIRE_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.FIRE_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.FIRE_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.FIRE_HUNT_TARGET.get(), sharedHuntTarget);
    }

    private static <T> void setOrErase(Brain<FireWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_FIRE_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_FIRE_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.FIRE_PACK_LEADER.get(),
                ModMemoryModuleTypes.FIRE_PACK_SIZE.get(),
                ModMemoryModuleTypes.FIRE_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.FIRE_PACK_THREAT.get(),
                ModMemoryModuleTypes.FIRE_HUNT_TARGET.get());
    }
}
