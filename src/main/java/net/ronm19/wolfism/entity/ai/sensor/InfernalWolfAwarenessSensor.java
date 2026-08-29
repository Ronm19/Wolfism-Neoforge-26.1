package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.wolfism.entity.custom.InfernalWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Nether-specific environmental awareness.
 *
 * <p>Tracks a dangerous Nether enemy, nearby lava near the owner/wolf, and a
 * nearby solid position that can be used as a simple "safer route" cue.</p>
 */
public final class InfernalWolfAwarenessSensor extends Sensor<InfernalWolf> {
    private static final int HAZARD_HORIZONTAL_RADIUS = 5;
    private static final int HAZARD_VERTICAL_RADIUS = 2;
    private static final int SAFE_SEARCH_RADIUS = 7;

    public InfernalWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, InfernalWolf wolf) {
        Brain<InfernalWolf> brain = wolf.getBrain();

        if (level.dimension() != Level.NETHER) {
            brain.eraseMemory(
                    ModMemoryModuleTypes.INFERNAL_AWARENESS_THREAT.get());
            brain.eraseMemory(
                    ModMemoryModuleTypes.INFERNAL_LAVA_HAZARD.get());
            brain.eraseMemory(
                    ModMemoryModuleTypes.INFERNAL_SAFE_POS.get());
            return;
        }

        LivingEntity threat = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(InfernalWolf.AWARENESS_RANGE),
                candidate -> candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && candidate.isAlive()
                        && wolf.isValidInfernalPackThreat(candidate))
                .stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        setOrErase(
                brain,
                ModMemoryModuleTypes.INFERNAL_AWARENESS_THREAT.get(),
                threat);

        LivingEntity owner = wolf.getOwner();
        LivingEntity anchor =
                owner != null && owner.isAlive() ? owner : wolf;

        BlockPos lavaHazard = findNearestLavaHazard(
                level,
                anchor.blockPosition());

        setOrErase(
                brain,
                ModMemoryModuleTypes.INFERNAL_LAVA_HAZARD.get(),
                lavaHazard);

        BlockPos safePos = lavaHazard == null
                ? null
                : findSafeGround(level, anchor.blockPosition());

        setOrErase(
                brain,
                ModMemoryModuleTypes.INFERNAL_SAFE_POS.get(),
                safePos);
    }

    private static BlockPos findNearestLavaHazard(
            ServerLevel level,
            BlockPos center) {

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -HAZARD_HORIZONTAL_RADIUS;
                dx <= HAZARD_HORIZONTAL_RADIUS;
                ++dx) {

            for (int dz = -HAZARD_HORIZONTAL_RADIUS;
                    dz <= HAZARD_HORIZONTAL_RADIUS;
                    ++dz) {

                for (int dy = -HAZARD_VERTICAL_RADIUS;
                        dy <= HAZARD_VERTICAL_RADIUS;
                        ++dy) {

                    BlockPos pos = center.offset(dx, dy, dz);

                    if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
                        continue;
                    }

                    double distance = pos.distSqr(center);

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockPos(
                                pos.getX(),
                                pos.getY(),
                                pos.getZ());
                    }
                }
            }
        }

        return best;
    }

    private static BlockPos findSafeGround(
            ServerLevel level,
            BlockPos center) {

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int radius = 2; radius <= SAFE_SEARCH_RADIUS; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -2; dy <= 2; ++dy) {
                        BlockPos feet = center.offset(dx, dy, dz);

                        if (!isSafeStandingPosition(level, feet)) {
                            continue;
                        }

                        double score = feet.distSqr(center);

                        if (score < bestScore) {
                            bestScore = score;
                            best = new BlockPos(
                                    feet.getX(),
                                    feet.getY(),
                                    feet.getZ());
                        }
                    }
                }
            }

            if (best != null) {
                return best;
            }
        }

        return null;
    }

    private static boolean isSafeStandingPosition(
            ServerLevel level,
            BlockPos feet) {

        BlockPos groundPos = feet.below();
        BlockState ground = level.getBlockState(groundPos);

        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(feet)
                .getCollisionShape(level, feet)
                .isEmpty()
                || !level.getBlockState(feet.above())
                .getCollisionShape(level, feet.above())
                .isEmpty()) {
            return false;
        }

        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    if (level.getFluidState(
                            feet.offset(dx, dy, dz))
                            .is(FluidTags.LAVA)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static <T> void setOrErase(
            Brain<InfernalWolf> brain,
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
                ModMemoryModuleTypes.INFERNAL_AWARENESS_THREAT.get(),
                ModMemoryModuleTypes.INFERNAL_LAVA_HAZARD.get(),
                ModMemoryModuleTypes.INFERNAL_SAFE_POS.get());
    }
}
