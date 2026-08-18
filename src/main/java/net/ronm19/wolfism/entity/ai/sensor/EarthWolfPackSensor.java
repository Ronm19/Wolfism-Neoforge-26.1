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
import net.ronm19.wolfism.entity.custom.EarthWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social/combat awareness for Earth Wolf packs. */
public final class EarthWolfPackSensor extends Sensor<EarthWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, EarthWolf wolf) {
        Brain<EarthWolf> brain = wolf.getBrain();

        List<EarthWolf> packmates = level.getEntitiesOfClass(
                EarthWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isEarthPackmate(candidate));

        EarthWolf nearestPackmate = null;
        EarthWolf nearestAdultPackmate = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestAdultDistance = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;

        List<EarthWolf> adultPack = new ArrayList<>();
        if (!wolf.isBaby()) {
            adultPack.add(wolf);
        }

        for (EarthWolf packmate : packmates) {
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
            if (candidateTarget == null || !candidateTarget.isAlive() || !wolf.isValidEarthCombatTarget(candidateTarget)) {
                continue;
            }

            if (sharedThreat == null) {
                sharedThreat = candidateTarget;
            }
        }

        EarthWolf leader = adultPack.stream()
                .min(Comparator.comparing(EarthWolf::getUUID))
                .orElse(null);

        brain.setMemory(ModMemoryModuleTypes.EARTH_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.EARTH_ADULT_PACK_SIZE.get(), adultPack.size());

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_EARTH_PACKMATE.get(), nearestPackmate);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_EARTH_ADULT_PACKMATE.get(), nearestAdultPackmate);
        setOrErase(brain, ModMemoryModuleTypes.EARTH_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.EARTH_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<EarthWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_EARTH_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_EARTH_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.EARTH_PACK_LEADER.get(),
                ModMemoryModuleTypes.EARTH_PACK_SIZE.get(),
                ModMemoryModuleTypes.EARTH_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.EARTH_PACK_THREAT.get(),

                // Ability state must be registered as Brain memories too. In 26.1 the
                // second Brain.provider(...) argument supplies ActivityData, not memory
                // module types, so sensor requirements are the correct registration path.
                ModMemoryModuleTypes.EARTH_CHARGE_TARGET.get(),
                ModMemoryModuleTypes.EARTH_CHARGE_COOLDOWN.get(),
                ModMemoryModuleTypes.EARTH_DIRT_SHELL_COOLDOWN.get(),
                ModMemoryModuleTypes.EARTH_SHOCKWAVE_COOLDOWN.get(),
                ModMemoryModuleTypes.EARTH_DIRT_BLAST_TARGET.get(),
                ModMemoryModuleTypes.EARTH_DIRT_BLAST_COOLDOWN.get(),
                ModMemoryModuleTypes.EARTH_EARTHQUAKE_COOLDOWN.get());
    }
}
