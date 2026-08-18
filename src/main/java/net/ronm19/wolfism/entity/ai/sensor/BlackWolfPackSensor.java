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
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Night-biased social perception and tactical target sharing for Black Wolves. */
public final class BlackWolfPackSensor extends Sensor<BlackWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;
    private static final double DAY_PACK_SCAN_RADIUS = 22.0D;

    @Override
    protected void doTick(ServerLevel level, BlackWolf wolf) {
        Brain<BlackWolf> brain = wolf.getBrain();
        boolean darkOutside = level.isDarkOutside();
        double radius = darkOutside ? PACK_SCAN_RADIUS : DAY_PACK_SCAN_RADIUS;

        List<BlackWolf> packmates = level.getEntitiesOfClass(
                BlackWolf.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isBlackPackmate(candidate));

        BlackWolf nearestPackmate = null;
        BlackWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<BlackWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (BlackWolf packmate : packmates) {
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

            LivingEntity packHunt = packmate.getBrain()
                    .getMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get())
                    .orElse(null);
            if (darkOutside
                    && sharedHuntTarget == null
                    && packHunt != null
                    && packHunt.isAlive()
                    && BlackWolf.isPreferredPrey(packHunt)
                    && wolf.isValidBlackCombatTarget(packHunt)) {
                sharedHuntTarget = packHunt;
            }

            LivingEntity candidateTarget = packmate.getTarget();
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidBlackCombatTarget(candidateTarget)) {
                continue;
            }

            if (darkOutside && BlackWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        BlackWolf leader = adultPack.stream()
                .min(Comparator.comparing(BlackWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.BLACK_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.BLACK_ADULT_PACK_SIZE.get(), adultPack.size());
        brain.setMemory(ModMemoryModuleTypes.BLACK_NIGHT_ACTIVE.get(), darkOutside);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BLACK_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BLACK_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.BLACK_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.BLACK_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.BLACK_HUNT_TARGET.get(), darkOutside ? sharedHuntTarget : null);
    }

    private static <T> void setOrErase(Brain<BlackWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_BLACK_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_BLACK_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.BLACK_PACK_LEADER.get(),
                ModMemoryModuleTypes.BLACK_PACK_SIZE.get(),
                ModMemoryModuleTypes.BLACK_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.BLACK_PACK_THREAT.get(),
                ModMemoryModuleTypes.BLACK_HUNT_TARGET.get(),
                ModMemoryModuleTypes.BLACK_NIGHT_ACTIVE.get());
    }
}