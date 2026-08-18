package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Large-prey perception for wild adult Dire Wolves. */
public final class DireWolfPreySensor extends Sensor<DireWolf> {
    private static final double PREY_RADIUS = 28.0D;

    @Override
    protected void doTick(ServerLevel level, DireWolf wolf) {
        Brain<DireWolf> brain = wolf.getBrain();

        if (wolf.isTame() || wolf.isBaby()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_DIRE_PREY.get());
            return;
        }

        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(PREY_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && DireWolf.isPreferredPrey(candidate)
                        && wolf.hasEnoughHuntersFor(candidate));

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = wolf.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        if (nearest != null) {
            brain.setMemory(ModMemoryModuleTypes.NEAREST_DIRE_PREY.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_DIRE_PREY.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_DIRE_PREY.get(),
                ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get());
    }
}
