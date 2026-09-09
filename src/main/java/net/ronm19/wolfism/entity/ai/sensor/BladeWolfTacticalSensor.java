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

import net.ronm19.wolfism.entity.custom.BladeWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Fast tactical perception for Blade Wolf.
 *
 * Awareness is wider than ordinary melee acquisition so Blade can choose where
 * direct pressure matters without converting distant observations into
 * cross-biome pursuit.
 */
public final class BladeWolfTacticalSensor
        extends Sensor<BladeWolf> {

    private static final double SCAN_RADIUS = 24.0D;
    private static final double LOCAL_COMBAT_RADIUS = 12.0D;
    private static final double CLUSTER_RADIUS = 5.0D;

    public BladeWolfTacticalSensor() {
        super(4);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            BladeWolf wolf) {

        AABB scan =
                wolf.getBoundingBox()
                        .inflate(SCAN_RADIUS);

        List<LivingEntity> threats =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        scan,
                        wolf::isValidBladeThreat);

        LivingEntity primary =
                threats.stream()
                        .min(
                                Comparator.comparingDouble(
                                        wolf::bladeThreatScore))
                        .orElse(null);

        int localHostiles =
                (int)
                        threats.stream()
                                .filter(
                                        target ->
                                                wolf.distanceToSqr(
                                                        target)
                                                        <= LOCAL_COMBAT_RADIUS
                                                        * LOCAL_COMBAT_RADIUS)
                                .count();

        /*
         * "Clustered" means enemies are already entering Blade's immediate
         * formation-breaking envelope.
         */
        int clusteredHostiles =
                (int)
                        threats.stream()
                                .filter(
                                        target ->
                                                wolf.distanceToSqr(
                                                        target)
                                                        <= CLUSTER_RADIUS
                                                        * CLUSTER_RADIUS)
                                .count();

        Brain<BladeWolf> brain =
                wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .BLADE_PRIMARY_TARGET
                        .get(),
                primary);

        brain.setMemory(
                ModMemoryModuleTypes
                        .BLADE_HOSTILE_COUNT
                        .get(),
                localHostiles);

        brain.setMemory(
                ModMemoryModuleTypes
                        .BLADE_CLUSTERED_COUNT
                        .get(),
                clusteredHostiles);
    }

    private static <T> void setOrErase(
            Brain<BladeWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {

        return Set.of(
                ModMemoryModuleTypes
                        .BLADE_PRIMARY_TARGET
                        .get(),

                ModMemoryModuleTypes
                        .BLADE_HOSTILE_COUNT
                        .get(),

                ModMemoryModuleTypes
                        .BLADE_CLUSTERED_COUNT
                        .get());
    }
}
