package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.CherryWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Cherry Wolf pack/family sensor.
 *
 * <p>The requires() set is intentionally exhaustive: every Cherry memory used by
 * the entity is declared here so the Brain cannot hit an unregistered-memory
 * runtime failure.</p>
 */
public final class CherryWolfPackSensor extends Sensor<CherryWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;
    public static final double FAMILY_SCAN_RADIUS = 24.0D;

    @Override
    protected void doTick(ServerLevel level, CherryWolf wolf) {
        Brain<CherryWolf> brain = wolf.getBrain();

        List<CherryWolf> pack = level.getEntitiesOfClass(
                CherryWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isCherryPackmate(other));

        CherryWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        CherryWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = pack.stream()
                .map(CherryWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidCherryCombatTarget)
                .findFirst()
                .orElse(null);

        LivingEntity injuredFamily = level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(FAMILY_SCAN_RADIUS),
                        candidate -> candidate != wolf
                                && wolf.isCherryFamilyMember(candidate)
                                && candidate.getHealth() < candidate.getMaxHealth())
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_CHERRY_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_CHERRY_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.CHERRY_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.CHERRY_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.CHERRY_INJURED_FAMILY.get(), injuredFamily);
    }

    private static <T> void setOrErase(Brain<CherryWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_CHERRY_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_CHERRY_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.CHERRY_PACK_SIZE.get(),
                ModMemoryModuleTypes.CHERRY_PACK_THREAT.get(),
                ModMemoryModuleTypes.CHERRY_INJURED_FAMILY.get(),
                ModMemoryModuleTypes.CHERRY_BLOSSOM_POS.get(),
                ModMemoryModuleTypes.CHERRY_PETAL_AID_COOLDOWN.get(),
                ModMemoryModuleTypes.CHERRY_BURST_COOLDOWN.get(),
                ModMemoryModuleTypes.CHERRY_BLOOMING_PATH_COOLDOWN.get(),
                ModMemoryModuleTypes.CHERRY_SANCTUARY_COOLDOWN.get());
    }
}
