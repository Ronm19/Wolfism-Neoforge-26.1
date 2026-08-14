package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Arctic scent and cold-environment perception.
 *
 * <p>Cold biomes extend prey awareness significantly. Once Arctic Wolves pick
 * up a valid trail they remember it briefly even when the prey moves outside
 * immediate scent range, representing non-magical snow/scent tracking.</p>
 */
public final class ArcticWolfPreySensor extends Sensor<ArcticWolf> {
    private static final double NORMAL_PREY_RADIUS = 18.0D;
    private static final double COLD_PREY_RADIUS = 30.0D;
    private static final double MAX_TRACK_RADIUS_SQR = 42.0D * 42.0D;

    @Override
    protected void doTick(ServerLevel level, ArcticWolf wolf) {
        Brain<ArcticWolf> brain = wolf.getBrain();
        boolean coldEnvironment = level.getBiome(wolf.blockPosition())
                .is(ModBiomeTags.ARCTIC_WOLF_COLD_ENVIRONMENT);
        brain.setMemory(ModMemoryModuleTypes.ARCTIC_COLD_ENVIRONMENT.get(), coldEnvironment);

        if (wolf.isTame() || wolf.isBaby()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get());
            brain.eraseMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get());
            return;
        }

        double radius = coldEnvironment ? COLD_PREY_RADIUS : NORMAL_PREY_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && ArcticWolf.isPreferredPrey(candidate)
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
            wolf.refreshPreyTrack(nearest);
            return;
        }

        // Snow tracking: do not instantly forget a recently scented animal just
        // because it crossed a ridge or left the immediate scent radius.
        LivingEntity tracked = brain.getMemory(ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get()).orElse(null);
        int trackingTicks = brain.getMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get()).orElse(0);
        if (tracked == null
                || !tracked.isAlive()
                || trackingTicks <= 0
                || wolf.distanceToSqr(tracked) > MAX_TRACK_RADIUS_SQR) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get());
            brain.eraseMemory(ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_ARCTIC_PREY.get(),
                ModMemoryModuleTypes.ARCTIC_COLD_ENVIRONMENT.get(),
                ModMemoryModuleTypes.ARCTIC_TRACKING_TICKS.get(),
                ModMemoryModuleTypes.ARCTIC_HUNT_COOLDOWN.get());
    }
}
