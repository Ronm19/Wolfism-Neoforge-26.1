package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.SkeletonWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack/family awareness and exhaustive Brain-memory registration for Skeleton Wolf. */
public final class SkeletonWolfPackSensor extends Sensor<SkeletonWolf> {
    public static final double PACK_SCAN_RADIUS = 36.0D;

    @Override
    protected void doTick(ServerLevel level, SkeletonWolf wolf) {
        Brain<SkeletonWolf> brain = wolf.getBrain();
        List<SkeletonWolf> pack = level.getEntitiesOfClass(
                SkeletonWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isSkeletonPackmate(other));

        SkeletonWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        SkeletonWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(SkeletonWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidSkeletonCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SKELETON_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SKELETON_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.SKELETON_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.SKELETON_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<SkeletonWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SKELETON_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SKELETON_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SKELETON_PACK_SIZE.get(),
                ModMemoryModuleTypes.SKELETON_PACK_THREAT.get(),
                ModMemoryModuleTypes.SKELETON_BONE_SHOT_COOLDOWN.get(),
                ModMemoryModuleTypes.SKELETON_BONE_RATTLE_COOLDOWN.get(),
                ModMemoryModuleTypes.SKELETON_VOLLEY_COOLDOWN.get(),
                ModMemoryModuleTypes.SKELETON_MARROW_GUARD_COOLDOWN.get());
    }
}
