package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.MushroomWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack/family awareness and exhaustive Brain-memory registration for Mushroom Wolf. */
public final class MushroomWolfPackSensor extends Sensor<MushroomWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, MushroomWolf wolf) {
        Brain<MushroomWolf> brain = wolf.getBrain();
        List<MushroomWolf> pack = level.getEntitiesOfClass(
                MushroomWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isMushroomPackmate(other));

        MushroomWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        MushroomWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(MushroomWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidMushroomCombatTarget)
                .findFirst()
                .orElse(null);

        LivingEntity injuredFamily = level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(16.0D),
                        candidate -> candidate.isAlive()
                                && wolf.isMushroomFamilyMember(candidate)
                                && candidate.getHealth() < candidate.getMaxHealth())
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_MUSHROOM_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_MUSHROOM_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.MUSHROOM_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.MUSHROOM_INJURED_FAMILY.get(), injuredFamily);
    }

    private static <T> void setOrErase(Brain<MushroomWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_MUSHROOM_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_MUSHROOM_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.MUSHROOM_PACK_SIZE.get(),
                ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get(),
                ModMemoryModuleTypes.MUSHROOM_FIND_POS.get(),
                ModMemoryModuleTypes.MUSHROOM_INJURED_FAMILY.get(),
                ModMemoryModuleTypes.MUSHROOM_SPORE_BURST_COOLDOWN.get());
    }
}
