package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.FireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/** Fire Wolf prey sensing plus hot-environment awareness. */
public final class FireWolfCombatSensor extends Sensor<FireWolf> {
    private static final double NORMAL_PREY_RADIUS = 18.0D;
    private static final double HOT_PREY_RADIUS = 24.0D;

    @Override
    protected void doTick(ServerLevel level, FireWolf wolf) {
        Brain<FireWolf> brain = wolf.getBrain();
        boolean hotEnvironment = level.getBiome(wolf.blockPosition())
                .is(ModBiomeTags.FIRE_WOLF_HOT_ENVIRONMENT);
        brain.setMemory(ModMemoryModuleTypes.FIRE_HOT_ENVIRONMENT.get(), hotEnvironment);

        if (wolf.isTame() || wolf.isBaby()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_FIRE_PREY.get());
            return;
        }

        double radius = hotEnvironment ? HOT_PREY_RADIUS : NORMAL_PREY_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && FireWolf.isPreferredPrey(candidate)
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
            brain.setMemory(ModMemoryModuleTypes.NEAREST_FIRE_PREY.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_FIRE_PREY.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_FIRE_PREY.get(),
                ModMemoryModuleTypes.FIRE_HOT_ENVIRONMENT.get(),
                ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get(),
                ModMemoryModuleTypes.FIRE_RUSH_TARGET.get(),
                ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get());
    }
}
