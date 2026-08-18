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
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/** Social awareness for Sand Wolves, with longer open-desert pack perception. */
public final class SandWolfPackSensor extends Sensor<SandWolf> {
    private static final double NORMAL_PACK_SCAN_RADIUS = 24.0D;
    private static final double DESERT_PACK_SCAN_RADIUS = 32.0D;
    public static final double MAX_PACK_SCAN_RADIUS = DESERT_PACK_SCAN_RADIUS;

    @Override
    protected void doTick(ServerLevel level, SandWolf wolf) {
        Brain<SandWolf> brain = wolf.getBrain();
        boolean desert = level.getBiome(wolf.blockPosition()).is(ModBiomeTags.SAND_WOLF_DESERT_ENVIRONMENT);
        double radius = desert ? DESERT_PACK_SCAN_RADIUS : NORMAL_PACK_SCAN_RADIUS;

        List<SandWolf> packmates = level.getEntitiesOfClass(
                SandWolf.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isSandPackmate(candidate));

        SandWolf nearestPackmate = null;
        SandWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        LivingEntity sharedHuntTarget = null;

        List<SandWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (SandWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidSandCombatTarget(candidateTarget)) {
                continue;
            }

            if (SandWolf.isPreferredPrey(candidateTarget)) {
                if (sharedHuntTarget == null) {
                    sharedHuntTarget = candidateTarget;
                }
            } else if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        SandWolf leader = adultPack.stream()
                .min(Comparator.comparing(SandWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.SAND_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.SAND_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SAND_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SAND_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.SAND_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.SAND_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.SAND_HUNT_TARGET.get(), sharedHuntTarget);
    }

    private static <T> void setOrErase(Brain<SandWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SAND_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SAND_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SAND_PACK_LEADER.get(),
                ModMemoryModuleTypes.SAND_PACK_SIZE.get(),
                ModMemoryModuleTypes.SAND_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.SAND_PACK_THREAT.get(),
                ModMemoryModuleTypes.SAND_HUNT_TARGET.get());
    }
}
