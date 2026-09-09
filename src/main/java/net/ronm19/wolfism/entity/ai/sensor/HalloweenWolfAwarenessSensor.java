package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.HalloweenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Fright Sense: low-light threat/family perception for Halloween Wolf. */
public final class HalloweenWolfAwarenessSensor extends Sensor<HalloweenWolf> {
    public HalloweenWolfAwarenessSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, HalloweenWolf wolf) {
        boolean lowLight = wolf.isHalloweenDarkEnvironment(level);
        double radius = lowLight
                ? HalloweenWolf.FRIGHT_SENSE_DARK_RADIUS
                : HalloweenWolf.FRIGHT_SENSE_DAY_RADIUS;

        List<LivingEntity> threats = wolf.getNearbyHalloweenThreats(level, radius);
        List<LivingEntity> visibleThreats = threats.stream()
                .filter(wolf.getSensing()::hasLineOfSight)
                .toList();

        LivingEntity priority = visibleThreats.stream()
                .min(Comparator.comparingDouble(wolf::halloweenThreatScore))
                .orElse(null);

        LivingEntity vulnerableFamily = wolf.getNearbyHalloweenFamily(level, 14.0D).stream()
                .filter(family -> family.getHealth() < family.getMaxHealth() * 0.60F)
                .min(Comparator.comparingDouble(
                        family -> family.getHealth() / Math.max(1.0F, family.getMaxHealth())))
                .orElse(null);

        Brain<HalloweenWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.HALLOWEEN_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.HALLOWEEN_VULNERABLE_FAMILY.get(), vulnerableFamily);
        brain.setMemory(ModMemoryModuleTypes.HALLOWEEN_HOSTILE_COUNT.get(), visibleThreats.size());
        brain.setMemory(ModMemoryModuleTypes.HALLOWEEN_LOW_LIGHT.get(), lowLight);
    }

    private static <T> void setOrErase(
            Brain<HalloweenWolf> brain,
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
                ModMemoryModuleTypes.HALLOWEEN_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.HALLOWEEN_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.HALLOWEEN_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.HALLOWEEN_LOW_LIGHT.get());
    }
}
