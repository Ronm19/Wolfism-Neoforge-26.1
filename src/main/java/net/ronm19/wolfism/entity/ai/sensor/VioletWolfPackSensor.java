package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.VioletWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack/family awareness for Violet Wolf. */
public final class VioletWolfPackSensor extends Sensor<VioletWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;
    public static final double FAMILY_SCAN_RADIUS = 24.0D;

    @Override
    protected void doTick(ServerLevel level, VioletWolf wolf) {
        Brain<VioletWolf> brain = wolf.getBrain();

        List<VioletWolf> pack = level.getEntitiesOfClass(
                VioletWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isVioletPackmate(other));

        VioletWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        VioletWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = pack.stream()
                .map(VioletWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidVioletCombatTarget)
                .findFirst()
                .orElse(null);

        LivingEntity injuredFamily = level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(FAMILY_SCAN_RADIUS),
                        candidate -> candidate != wolf
                                && wolf.isVioletFamilyMember(candidate)
                                && candidate.getHealth() < candidate.getMaxHealth())
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_VIOLET_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_VIOLET_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.VIOLET_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.VIOLET_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.VIOLET_INJURED_FAMILY.get(), injuredFamily);
    }

    private static <T> void setOrErase(Brain<VioletWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_VIOLET_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_VIOLET_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.VIOLET_PACK_SIZE.get(),
                ModMemoryModuleTypes.VIOLET_PACK_THREAT.get(),
                ModMemoryModuleTypes.VIOLET_INJURED_FAMILY.get(),
                ModMemoryModuleTypes.VIOLET_LEAP_TARGET.get(),
                ModMemoryModuleTypes.VIOLET_LEAP_COOLDOWN.get(),
                ModMemoryModuleTypes.VIOLET_AURA_COOLDOWN.get(),
                ModMemoryModuleTypes.VIOLET_HOWL_COOLDOWN.get(),
                ModMemoryModuleTypes.VIOLET_BLOOM_COOLDOWN.get());
    }
}
