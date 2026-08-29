package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.ZombieWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack/family awareness and exhaustive Brain-memory registration for Zombie Wolf. */
public final class ZombieWolfPackSensor extends Sensor<ZombieWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, ZombieWolf wolf) {
        Brain<ZombieWolf> brain = wolf.getBrain();
        List<ZombieWolf> pack = level.getEntitiesOfClass(
                ZombieWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isZombiePackmate(other));

        ZombieWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        ZombieWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(ZombieWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidZombieCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_ZOMBIE_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_ZOMBIE_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.ZOMBIE_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.ZOMBIE_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<ZombieWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_ZOMBIE_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_ZOMBIE_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.ZOMBIE_PACK_SIZE.get(),
                ModMemoryModuleTypes.ZOMBIE_PACK_THREAT.get(),
                ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get(),
                ModMemoryModuleTypes.ZOMBIE_DEATHLESS_RUSH_COOLDOWN.get(),
                ModMemoryModuleTypes.ZOMBIE_RISE_AGAIN_COOLDOWN.get());
    }
}
