package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Detects two things that matter specifically underwater: family members that
 * are running out of air, and hostile aquatic threats.
 */
public final class WaterWolfAquaticSensor extends Sensor<WaterWolf> {
    private static final double THREAT_SCAN_RADIUS = 18.0D;

    @Override
    protected void doTick(ServerLevel level, WaterWolf wolf) {
        Brain<WaterWolf> brain = wolf.getBrain();

        List<LivingEntity> rescueCandidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(WaterWolf.RESCUE_SCAN_RADIUS),
                wolf::needsWaterRescue);

        LivingEntity rescueTarget = rescueCandidates.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        if (rescueTarget != null) {
            brain.setMemory(ModMemoryModuleTypes.WATER_RESCUE_TARGET.get(), rescueTarget);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.WATER_RESCUE_TARGET.get());
        }

        if (wolf.isBaby() || !wolf.isInWater()) {
            brain.eraseMemory(ModMemoryModuleTypes.WATER_AQUATIC_THREAT.get());
            return;
        }

        LivingEntity aquaticThreat = wolf.findNearestAquaticThreat(level, THREAT_SCAN_RADIUS);
        if (aquaticThreat != null) {
            brain.setMemory(ModMemoryModuleTypes.WATER_AQUATIC_THREAT.get(), aquaticThreat);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.WATER_AQUATIC_THREAT.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.WATER_RESCUE_TARGET.get(),
                ModMemoryModuleTypes.WATER_AQUATIC_THREAT.get(),
                ModMemoryModuleTypes.WATER_CURRENT_DASH_TARGET.get(),
                ModMemoryModuleTypes.WATER_CURRENT_DASH_COOLDOWN.get(),
                ModMemoryModuleTypes.WATER_BUBBLE_SHELTER_COOLDOWN.get(),
                ModMemoryModuleTypes.WATER_PRESSURE_WAVE_COOLDOWN.get());
    }
}
