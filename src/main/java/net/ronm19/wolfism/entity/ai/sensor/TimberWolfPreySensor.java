package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Timber-specific scent and woodland awareness.
 *
 * <p>Dense nearby logs/leaves count as forest cover. Timber Wolves can scent
 * preferred prey farther through their home terrain than they can in exposed
 * terrain, giving the species a subtle environmental advantage without magic.</p>
 */
public final class TimberWolfPreySensor extends Sensor<TimberWolf> {
    private static final double OPEN_PREY_RADIUS = 22.0D;
    private static final double FOREST_PREY_RADIUS = 28.0D;
    private static final int COVER_RADIUS = 3;
    private static final int COVER_THRESHOLD = 8;

    @Override
    protected void doTick(ServerLevel level, TimberWolf wolf) {
        Brain<TimberWolf> brain = wolf.getBrain();
        boolean forestCover = hasForestCover(level, wolf.blockPosition());
        brain.setMemory(ModMemoryModuleTypes.TIMBER_FOREST_COVER.get(), forestCover);

        if (wolf.isTame() || wolf.isBaby()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_TIMBER_PREY.get());
            return;
        }

        double radius = forestCover ? FOREST_PREY_RADIUS : OPEN_PREY_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && TimberWolf.isPreferredPrey(candidate)
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
            brain.setMemory(ModMemoryModuleTypes.NEAREST_TIMBER_PREY.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_TIMBER_PREY.get());
        }
    }

    private static boolean hasForestCover(ServerLevel level, BlockPos center) {
        int naturalCover = 0;
        BlockPos min = center.offset(-COVER_RADIUS, -1, -COVER_RADIUS);
        BlockPos max = center.offset(COVER_RADIUS, 3, COVER_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(BlockTags.LOGS) || level.getBlockState(pos).is(BlockTags.LEAVES)) {
                naturalCover++;
                if (naturalCover >= COVER_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_TIMBER_PREY.get(),
                ModMemoryModuleTypes.TIMBER_FOREST_COVER.get(),
                ModMemoryModuleTypes.TIMBER_HUNT_COOLDOWN.get());
    }
}
