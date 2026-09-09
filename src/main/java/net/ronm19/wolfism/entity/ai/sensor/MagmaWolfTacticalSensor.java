package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Enemy;
import net.ronm19.wolfism.entity.custom.MagmaWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Broad battlefield/environment awareness for Magma Wolf.
 *
 * <p>The sensor may see much farther than Magma physically commits. Its distant
 * memory is mainly for Eruption/Shower/family emergency decisions.</p>
 */
public final class MagmaWolfTacticalSensor extends Sensor<MagmaWolf> {
    private static final int LAVA_SEARCH_RADIUS = 8;

    public MagmaWolfTacticalSensor() {
        super(8);
    }

    @Override
    protected void doTick(ServerLevel level, MagmaWolf wolf) {
        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(MagmaWolf.TACTICAL_AWARENESS_RADIUS),
                candidate -> candidate.isAlive()
                        && candidate != wolf
                        && wolf.isValidMagmaThreat(candidate)
                        && (candidate instanceof Enemy
                                || candidate instanceof Mob mob
                                && mob.getTarget() != null
                                && wolf.isMagmaFamilyMember(mob.getTarget())));

        LivingEntity priority = threats.stream()
                .max(Comparator.comparingDouble(wolf::scoreMagmaThreat))
                .orElse(null);

        int nearbyHostiles = (int) threats.stream()
                .filter(threat -> wolf.distanceToSqr(threat) <= 14.0D * 14.0D)
                .count();

        BlockPos lavaSurface = findNearestLavaSurface(level, wolf.blockPosition());

        Brain<MagmaWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.MAGMA_TACTICAL_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.MAGMA_LAVA_SURFACE.get(), lavaSurface);
        brain.setMemory(ModMemoryModuleTypes.MAGMA_HOSTILE_COUNT.get(), nearbyHostiles);
    }

    private static BlockPos findNearestLavaSurface(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -LAVA_SEARCH_RADIUS; dx <= LAVA_SEARCH_RADIUS; ++dx) {
            for (int dz = -LAVA_SEARCH_RADIUS; dz <= LAVA_SEARCH_RADIUS; ++dz) {
                for (int dy = -3; dy <= 3; ++dy) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getFluidState(pos).is(FluidTags.LAVA)
                            || !level.getFluidState(pos).isSource()
                            || level.getFluidState(pos.above()).is(FluidTags.LAVA)) {
                        continue;
                    }

                    double distance = pos.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }

        return best;
    }

    private static <T> void setOrErase(
            Brain<MagmaWolf> brain,
            MemoryModuleType<T> memory,
            T value) {
        if (value != null) brain.setMemory(memory, value);
        else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.MAGMA_TACTICAL_THREAT.get(),
                ModMemoryModuleTypes.MAGMA_LAVA_SURFACE.get(),
                ModMemoryModuleTypes.MAGMA_HOSTILE_COUNT.get());
    }
}
