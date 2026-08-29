package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.AngelWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Family-defense and anti-undead awareness for Angel Wolf.
 *
 * <p>This replaces the extra Angel target Goal from the first implementation.
 * The sensor writes tactical facts into the Brain; Angel's entity then acts on
 * those memories while retaining vanilla Wolf baseline behavior.</p>
 */
public final class AngelWolfThreatSensor extends Sensor<AngelWolf> {
    public AngelWolfThreatSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, AngelWolf wolf) {
        Brain<AngelWolf> brain = wolf.getBrain();

        LivingEntity familyThreat = level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(AngelWolf.COMBAT_AWARENESS_RADIUS),
                        wolf::isRelevantAngelThreat)
                .stream()
                .min(Comparator.comparingDouble(wolf::angelThreatScore))
                .orElse(null);

        LivingEntity undeadThreat = level.getEntitiesOfClass(
                        LivingEntity.class,
                        wolf.getBoundingBox().inflate(AngelWolf.BANISHMENT_AWARENESS_RADIUS),
                        candidate -> wolf.isValidBanishmentTarget(candidate)
                                && wolf.isRelevantAngelThreat(candidate))
                .stream()
                .min(Comparator.comparingDouble(wolf::angelThreatScore))
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.ANGEL_FAMILY_THREAT.get(), familyThreat);
        setOrErase(brain, ModMemoryModuleTypes.ANGEL_UNDEAD_THREAT.get(), undeadThreat);
    }

    private static <T> void setOrErase(
            Brain<AngelWolf> brain,
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
                ModMemoryModuleTypes.ANGEL_FAMILY_THREAT.get(),
                ModMemoryModuleTypes.ANGEL_UNDEAD_THREAT.get());
    }
}
