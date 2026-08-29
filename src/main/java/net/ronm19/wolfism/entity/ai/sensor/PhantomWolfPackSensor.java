package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.PhantomWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack awareness and Brain-memory registration for Phantom Wolf. */
public final class PhantomWolfPackSensor extends Sensor<PhantomWolf> {
    public static final double PACK_SCAN_RADIUS = 40.0D;

    @Override
    protected void doTick(ServerLevel level, PhantomWolf wolf) {
        Brain<PhantomWolf> brain = wolf.getBrain();

        List<PhantomWolf> pack = level.getEntitiesOfClass(
                PhantomWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf
                        && other.isAlive()
                        && wolf.isPhantomPackmate(other));

        PhantomWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        PhantomWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = pack.stream()
                .map(PhantomWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidPhantomCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_PHANTOM_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_PHANTOM_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.PHANTOM_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.PHANTOM_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(
            Brain<PhantomWolf> brain,
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
                ModMemoryModuleTypes.NEAREST_PHANTOM_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_PHANTOM_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.PHANTOM_PACK_SIZE.get(),
                ModMemoryModuleTypes.PHANTOM_PACK_THREAT.get(),
                ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get(),
                ModMemoryModuleTypes.PHANTOM_DIVE_COOLDOWN.get(),
                ModMemoryModuleTypes.PHANTOM_PHASING_BITE_COOLDOWN.get(),
                ModMemoryModuleTypes.PHANTOM_SCREECH_COOLDOWN.get(),
                ModMemoryModuleTypes.PHANTOM_SWEEP_COOLDOWN.get(),
                ModMemoryModuleTypes.PHANTOM_SKY_HUNTER_COOLDOWN.get());
    }
}