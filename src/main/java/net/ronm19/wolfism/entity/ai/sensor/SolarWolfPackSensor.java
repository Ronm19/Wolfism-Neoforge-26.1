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
import net.ronm19.wolfism.entity.custom.SolarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social/combat awareness for Solar Wolf packs. */
public final class SolarWolfPackSensor extends Sensor<SolarWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, SolarWolf wolf) {
        Brain<SolarWolf> brain = wolf.getBrain();

        List<SolarWolf> packmates = level.getEntitiesOfClass(
                SolarWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isSolarPackmate(candidate));

        SolarWolf nearestPackmate = null;
        SolarWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;

        List<SolarWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (SolarWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidSolarCombatTarget(candidateTarget)) {
                continue;
            }

            if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        SolarWolf leader = adultPack.stream()
                .min(Comparator.comparing(SolarWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.SOLAR_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.SOLAR_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SOLAR_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SOLAR_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.SOLAR_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.SOLAR_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<SolarWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SOLAR_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SOLAR_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SOLAR_PACK_LEADER.get(),
                ModMemoryModuleTypes.SOLAR_PACK_SIZE.get(),
                ModMemoryModuleTypes.SOLAR_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.SOLAR_PACK_THREAT.get(),

                // Ability state is registered through sensor requirements in 26.1.
                ModMemoryModuleTypes.SOLAR_FLARE_COOLDOWN.get(),
                ModMemoryModuleTypes.SOLAR_SUNBEAM_TARGET.get(),
                ModMemoryModuleTypes.SOLAR_SUNBEAM_COOLDOWN.get(),
                ModMemoryModuleTypes.SOLAR_DASH_TARGET.get(),
                ModMemoryModuleTypes.SOLAR_DASH_COOLDOWN.get(),
                ModMemoryModuleTypes.SOLAR_ASCENSION_TARGET.get(),
                ModMemoryModuleTypes.SOLAR_ASCENSION_COOLDOWN.get());
    }
}
