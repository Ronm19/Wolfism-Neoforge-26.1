package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Sand Wolf environmental perception: long-range desert prey awareness and
 * practical sensing of suspicious/buried objects under sand.
 */
public final class SandWolfDesertSensor extends Sensor<SandWolf> {
    private static final double NORMAL_PREY_RADIUS = 16.0D;
    private static final double DESERT_PREY_RADIUS = 28.0D;
    private static final int BURIED_SCAN_RADIUS = 12;
    private static final int BURIED_SCAN_DOWN = 5;
    private static final int BURIED_SCAN_UP = 1;

    @Override
    protected void doTick(ServerLevel level, SandWolf wolf) {
        Brain<SandWolf> brain = wolf.getBrain();
        boolean desert = level.getBiome(wolf.blockPosition()).is(ModBiomeTags.SAND_WOLF_DESERT_ENVIRONMENT);
        brain.setMemory(ModMemoryModuleTypes.SAND_DESERT_ENVIRONMENT.get(), desert);

        updatePreyAwareness(level, wolf, brain, desert);
        updateBuriedInterest(level, wolf, brain);
    }

    private static void updatePreyAwareness(
            ServerLevel level,
            SandWolf wolf,
            Brain<SandWolf> brain,
            boolean desert) {
        if (wolf.isTame() || wolf.isBaby()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_SAND_PREY.get());
            return;
        }

        double radius = desert ? DESERT_PREY_RADIUS : NORMAL_PREY_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && SandWolf.isPreferredPrey(candidate));

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
            brain.setMemory(ModMemoryModuleTypes.NEAREST_SAND_PREY.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_SAND_PREY.get());
        }
    }

    private static void updateBuriedInterest(ServerLevel level, SandWolf wolf, Brain<SandWolf> brain) {
        // Buried-sand sense is primarily a player exploration tool. Wild wolves
        // do not path toward archaeology/loot, and pups leave the leading to adults.
        LivingEntity owner = wolf.getOwner();
        if (!wolf.isTame()
                || wolf.isBaby()
                || owner == null
                || !owner.isAlive()
                || wolf.distanceToSqr(owner) > 24.0D * 24.0D) {
            brain.eraseMemory(ModMemoryModuleTypes.SAND_BURIED_INTEREST.get());
            return;
        }

        BlockPos origin = wolf.blockPosition();
        BlockPos remembered = brain.getMemory(ModMemoryModuleTypes.SAND_BURIED_INTEREST.get()).orElse(null);
        if (remembered != null
                && remembered.distSqr(origin) <= (BURIED_SCAN_RADIUS + 2.0D) * (BURIED_SCAN_RADIUS + 2.0D)
                && isBuriedInterest(level, remembered)) {
            return;
        }

        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int y = origin.getY() - BURIED_SCAN_DOWN; y <= origin.getY() + BURIED_SCAN_UP; y++) {
            for (int x = origin.getX() - BURIED_SCAN_RADIUS; x <= origin.getX() + BURIED_SCAN_RADIUS; x++) {
                for (int z = origin.getZ() - BURIED_SCAN_RADIUS; z <= origin.getZ() + BURIED_SCAN_RADIUS; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!isBuriedInterest(level, candidate)) {
                        continue;
                    }

                    double distance = candidate.distSqr(origin);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate;
                    }
                }
            }
        }

        if (nearest != null) {
            brain.setMemory(ModMemoryModuleTypes.SAND_BURIED_INTEREST.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.SAND_BURIED_INTEREST.get());
        }
    }

    public static boolean isBuriedInterest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SUSPICIOUS_SAND)) {
            return true;
        }

        if (!state.is(Blocks.CHEST) && !state.is(Blocks.BARREL)) {
            return false;
        }

        BlockState above = level.getBlockState(pos.above());
        return above.is(Blocks.SAND)
                || above.is(Blocks.RED_SAND)
                || above.is(Blocks.SUSPICIOUS_SAND);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SAND_PREY.get(),
                ModMemoryModuleTypes.SAND_DESERT_ENVIRONMENT.get(),
                ModMemoryModuleTypes.SAND_BURIED_INTEREST.get(),
                ModMemoryModuleTypes.SAND_HUNT_COOLDOWN.get());
    }
}
