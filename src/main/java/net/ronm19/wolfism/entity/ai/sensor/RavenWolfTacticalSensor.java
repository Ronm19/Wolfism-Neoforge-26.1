package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.phys.AABB;

import net.ronm19.wolfism.entity.custom.RavenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Raven Wolf tactical perception.
 *
 * This sensor gathers information.
 * RavenWolf decides what that information means.
 */
public final class RavenWolfTacticalSensor
        extends Sensor<RavenWolf> {

    private static final double BASE_SCAN_RADIUS = 24.0D;
    private static final double EYE_SCAN_RADIUS = 36.0D;

    private static final double RAVEN_COORDINATION_RADIUS = 20.0D;

    public RavenWolfTacticalSensor() {

        super(5);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            RavenWolf raven) {

        double radius =
                raven.isEyeOfRavenActive()
                        ? EYE_SCAN_RADIUS
                        : BASE_SCAN_RADIUS;

        AABB scan =
                raven.getBoundingBox()
                        .inflate(
                                radius,
                                radius * 0.65D,
                                radius);

        List<LivingEntity> threats =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        scan,
                        raven::isValidRavenThreat);

        LivingEntity priority =
                threats.stream()
                        .min(
                                Comparator.comparingDouble(
                                        raven::ravenThreatScore))
                        .orElse(null);

        int nearbyRavens =
                level.getEntitiesOfClass(
                                RavenWolf.class,
                                raven.getBoundingBox()
                                        .inflate(
                                                RAVEN_COORDINATION_RADIUS),
                                other ->
                                        other.isAlive()
                                                && (other == raven
                                                || raven.isRavenPackmate(
                                                other)))
                        .size();

        Brain<RavenWolf> brain =
                raven.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .RAVEN_PRIORITY_THREAT
                        .get(),
                priority);

        brain.setMemory(
                ModMemoryModuleTypes
                        .RAVEN_HOSTILE_COUNT
                        .get(),
                threats.size());

        brain.setMemory(
                ModMemoryModuleTypes
                        .RAVEN_NEARBY_RAVEN_COUNT
                        .get(),
                Math.max(
                        1,
                        nearbyRavens));
    }

    private static <T> void setOrErase(
            Brain<RavenWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {

            brain.setMemory(
                    memory,
                    value);

        } else {

            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {

        return Set.of(
                ModMemoryModuleTypes
                        .RAVEN_PRIORITY_THREAT
                        .get(),

                ModMemoryModuleTypes
                        .RAVEN_OBSERVED_THREAT
                        .get(),

                ModMemoryModuleTypes
                        .RAVEN_REPORTED_THREAT
                        .get(),

                ModMemoryModuleTypes
                        .RAVEN_LOOT_POS
                        .get(),

                ModMemoryModuleTypes
                        .RAVEN_HOSTILE_COUNT
                        .get(),

                ModMemoryModuleTypes
                        .RAVEN_NEARBY_RAVEN_COUNT
                        .get());
    }
}