package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.AstralWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Social / pack memory for Astral Wolves.
 */
public final class AstralWolfPackSensor
        extends Sensor<AstralWolf> {

    public static final double PACK_SCAN_RADIUS = 34.0D;

    @Override
    protected void doTick(
            ServerLevel level,
            AstralWolf wolf) {

        List<AstralWolf> packmates =
                level.getEntitiesOfClass(
                        AstralWolf.class,
                        wolf.getBoundingBox().inflate(
                                PACK_SCAN_RADIUS),
                        candidate -> candidate != wolf
                                && candidate.isAlive()
                                && wolf.isAstralPackmate(candidate));

        AstralWolf nearest =
                packmates.stream()
                        .min(
                                Comparator.comparingDouble(
                                        wolf::distanceToSqr))
                        .orElse(null);

        AstralWolf nearestAdult =
                packmates.stream()
                        .filter(
                                candidate ->
                                        !candidate.isBaby())
                        .min(
                                Comparator.comparingDouble(
                                        wolf::distanceToSqr))
                        .orElse(null);

        LivingEntity sharedThreat =
                packmates.stream()
                        .map(AstralWolf::getTarget)
                        .filter(Objects::nonNull)
                        .filter(LivingEntity::isAlive)
                        .filter(
                                target ->
                                        !(target instanceof Creeper))
                        .filter(wolf::isValidAstralPackThreat)
                        .min(
                                Comparator.comparingDouble(
                                        wolf::distanceToSqr))
                        .orElse(null);

        Brain<AstralWolf> brain =
                wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_ASTRAL_PACKMATE.get(),
                nearest);

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_ASTRAL_ADULT_PACKMATE.get(),
                nearestAdult);

        brain.setMemory(
                ModMemoryModuleTypes.ASTRAL_PACK_SIZE.get(),
                packmates.size() + 1);

        /*
         * Do not erase a stronger night-sensor threat just because packmates
         * currently have no combat target.
         */
        if (sharedThreat != null) {
            brain.setMemory(
                    ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get(),
                    sharedThreat);
        }
    }

    private static <T> void setOrErase(
            Brain<AstralWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_ASTRAL_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_ASTRAL_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.ASTRAL_PACK_SIZE.get(),
                ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get());
    }
}
