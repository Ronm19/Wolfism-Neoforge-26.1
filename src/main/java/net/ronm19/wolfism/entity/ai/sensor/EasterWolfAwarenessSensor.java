package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.EasterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Spring, family and exploration awareness for Easter Wolf. */
public final class EasterWolfAwarenessSensor extends Sensor<EasterWolf> {
    private static final int NATURE_SCAN_INTERVAL = 80;
    private long nextNatureScanGameTime = Long.MIN_VALUE;

    public EasterWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, EasterWolf wolf) {
        Brain<EasterWolf> brain = wolf.getBrain();
        if (!wolf.canProvideEasterSupport()) {
            brain.eraseMemory(ModMemoryModuleTypes.EASTER_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.EASTER_FAMILY_IN_NEED.get());
            brain.eraseMemory(ModMemoryModuleTypes.EASTER_NATURE_POS.get());
            brain.setMemory(ModMemoryModuleTypes.EASTER_HOSTILE_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.EASTER_NATURE_COUNT.get(), 0);
            this.nextNatureScanGameTime = level.getGameTime();
            return;
        }

        List<LivingEntity> family = wolf.getEasterFamily(
                level,
                EasterWolf.FAMILY_SENSE_RADIUS);
        List<LivingEntity> threats = wolf.getEasterThreats(level);

        LivingEntity need = family.stream()
                .filter(member -> wolf.easterNeedScore(member) > 1.0D)
                .max(Comparator.comparingDouble(wolf::easterNeedScore))
                .orElse(null);
        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        long now = level.getGameTime();
        BlockPos nature = brain.getMemory(ModMemoryModuleTypes.EASTER_NATURE_POS.get())
                .orElse(null);
        int natureCount = brain.getMemory(ModMemoryModuleTypes.EASTER_NATURE_COUNT.get())
                .orElse(0);
        if (now >= this.nextNatureScanGameTime) {
            this.nextNatureScanGameTime = now + NATURE_SCAN_INTERVAL;
            EasterWolf.SpringScan scan = wolf.scanSpringInterests(level);
            nature = scan.position();
            natureCount = scan.count();
        } else if (nature != null && !wolf.isEasterDestinationValid(nature)) {
            nature = null;
        }

        setOrErase(brain, ModMemoryModuleTypes.EASTER_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.EASTER_FAMILY_IN_NEED.get(), need);
        setOrErase(brain, ModMemoryModuleTypes.EASTER_NATURE_POS.get(), nature);
        brain.setMemory(ModMemoryModuleTypes.EASTER_HOSTILE_COUNT.get(), threats.size());
        brain.setMemory(ModMemoryModuleTypes.EASTER_NATURE_COUNT.get(), natureCount);
    }

    private static <T> void setOrErase(
            Brain<EasterWolf> brain,
            MemoryModuleType<T> key,
            T value) {
        if (value == null) brain.eraseMemory(key);
        else brain.setMemory(key, value);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.EASTER_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.EASTER_FAMILY_IN_NEED.get(),
                ModMemoryModuleTypes.EASTER_NATURE_POS.get(),
                ModMemoryModuleTypes.EASTER_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.EASTER_NATURE_COUNT.get());
    }
}
