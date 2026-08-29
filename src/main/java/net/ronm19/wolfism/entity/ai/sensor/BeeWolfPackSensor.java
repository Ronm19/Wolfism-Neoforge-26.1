package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.BeeWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack/family awareness and exhaustive Brain-memory registration for Bee Wolf. */
public final class BeeWolfPackSensor extends Sensor<BeeWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, BeeWolf wolf) {
        Brain<BeeWolf> brain = wolf.getBrain();
        List<BeeWolf> pack = level.getEntitiesOfClass(
                BeeWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isBeePackmate(other));

        BeeWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        BeeWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(BeeWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidBeeCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BEE_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_BEE_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.BEE_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.BEE_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<BeeWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_BEE_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_BEE_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.BEE_PACK_SIZE.get(),
                ModMemoryModuleTypes.BEE_PACK_THREAT.get(),
                ModMemoryModuleTypes.BEE_HONEY_FIND_POS.get(),
                ModMemoryModuleTypes.BEE_POLLINATION_COOLDOWN.get(),
                ModMemoryModuleTypes.BEE_HASTE_AURA_COOLDOWN.get());
    }
}
