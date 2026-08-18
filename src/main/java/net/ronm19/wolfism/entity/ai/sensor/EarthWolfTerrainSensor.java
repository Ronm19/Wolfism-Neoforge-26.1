package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.EarthWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Lightweight terrain awareness. It remembers a nearby manipulable surface and
 * a local score so abilities can fail early instead of repeatedly rescanning a
 * large volume every goal tick.
 */
public final class EarthWolfTerrainSensor extends Sensor<EarthWolf> {
    private static final int HORIZONTAL_RADIUS = 6;
    private static final int VERTICAL_RADIUS = 3;

    @Override
    protected void doTick(ServerLevel level, EarthWolf wolf) {
        Brain<EarthWolf> brain = wolf.getBrain();
        BlockPos origin = wolf.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int score = 0;

        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; ++dx) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; ++dz) {
                for (int dy = VERTICAL_RADIUS; dy >= -VERTICAL_RADIUS; --dy) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!EarthWolf.isManipulableEarthState(level.getBlockState(pos))
                            || !level.getBlockState(pos.above()).isAir()) {
                        continue;
                    }

                    ++score;
                    double distance = pos.distSqr(origin);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = pos.immutable();
                    }
                    break;
                }
            }
        }

        brain.setMemory(ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get(), score);
        if (nearest != null) {
            brain.setMemory(ModMemoryModuleTypes.EARTH_NEAREST_TERRAIN.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.EARTH_NEAREST_TERRAIN.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.EARTH_TERRAIN_SCORE.get(),
                ModMemoryModuleTypes.EARTH_NEAREST_TERRAIN.get());
    }
}
