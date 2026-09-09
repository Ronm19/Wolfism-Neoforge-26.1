package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

import net.ronm19.wolfism.entity.custom.IllagerWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Raider-awareness / anti-armor perception for Illager Wolf.
 *
 * Awareness extends beyond physical melee aggro.
 */
public final class IllagerWolfTacticalSensor
        extends Sensor<IllagerWolf> {

    private static final double SCAN_RADIUS = 28.0D;
    private static final double ABILITY_RADIUS = 20.0D;

    @Override
    protected void doTick(
            ServerLevel level,
            IllagerWolf wolf) {

        AABB scan =
                wolf.getBoundingBox()
                        .inflate(SCAN_RADIUS);

        List<LivingEntity> threats =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        scan,
                        wolf::isValidIllagerThreat);

        LivingEntity primary =
                threats.stream()
                        .min(
                                Comparator.comparingDouble(
                                        wolf::illagerThreatScore))
                        .orElse(null);

        LivingEntity armored =
                threats.stream()
                        .filter(target ->
                                target.getArmorValue() > 0)
                        .max(
                                Comparator
                                        .comparingInt(
                                                LivingEntity::getArmorValue)
                                        .thenComparingDouble(
                                                target ->
                                                        -wolf.distanceToSqr(
                                                                target)))
                        .orElse(null);

        LivingEntity raider =
                threats.stream()
                        .filter(Raider.class::isInstance)
                        .min(
                                Comparator.comparingDouble(
                                        wolf::illagerThreatScore))
                        .orElse(null);

        int localHostiles =
                (int) threats.stream()
                        .filter(target ->
                                wolf.distanceToSqr(target)
                                        <= ABILITY_RADIUS
                                        * ABILITY_RADIUS)
                        .count();

        Brain<IllagerWolf> brain =
                wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ILLAGER_PRIMARY_TARGET
                        .get(),
                primary);

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ILLAGER_ARMORED_TARGET
                        .get(),
                armored);

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ILLAGER_RAIDER_TARGET
                        .get(),
                raider);

        brain.setMemory(
                ModMemoryModuleTypes
                        .ILLAGER_HOSTILE_COUNT
                        .get(),
                localHostiles);
    }

    private static <T> void setOrErase(
            Brain<IllagerWolf> brain,
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
                        .ILLAGER_PRIMARY_TARGET
                        .get(),

                ModMemoryModuleTypes
                        .ILLAGER_ARMORED_TARGET
                        .get(),

                ModMemoryModuleTypes
                        .ILLAGER_RAIDER_TARGET
                        .get(),

                ModMemoryModuleTypes
                        .ILLAGER_HOSTILE_COUNT
                        .get());
    }
}