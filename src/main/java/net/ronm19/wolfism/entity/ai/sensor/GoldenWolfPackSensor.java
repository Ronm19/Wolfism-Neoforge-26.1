package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.GoldenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack coordination plus complete Brain-memory registration for Golden Wolf. */
public final class GoldenWolfPackSensor extends Sensor<GoldenWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, GoldenWolf wolf) {
        Brain<GoldenWolf> brain = wolf.getBrain();
        List<GoldenWolf> pack = level.getEntitiesOfClass(
                GoldenWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isGoldenPackmate(other));

        GoldenWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        GoldenWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(GoldenWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidGoldenCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_GOLDEN_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_GOLDEN_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.GOLDEN_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.GOLDEN_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<GoldenWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        // Every Golden memory touched by GoldenWolf is declared here. This is
        // intentionally exhaustive to prevent the unregistered-memory failure
        // previously encountered during the Earth Wolf pass.
        return Set.of(
                ModMemoryModuleTypes.NEAREST_GOLDEN_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_GOLDEN_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.GOLDEN_PACK_SIZE.get(),
                ModMemoryModuleTypes.GOLDEN_PACK_THREAT.get(),
                ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get(),
                ModMemoryModuleTypes.GOLDEN_FORTUNE_DIG_COOLDOWN.get(),
                ModMemoryModuleTypes.GOLDEN_RADIANT_SHARE_COOLDOWN.get(),
                ModMemoryModuleTypes.GOLDEN_BARRIER_COOLDOWN.get(),
                ModMemoryModuleTypes.GOLDEN_BLESSING_COOLDOWN.get());
    }
}
