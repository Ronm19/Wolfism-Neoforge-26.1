package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.ValentinesWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family-bond and threat awareness, refreshed every ten ticks. */
public final class ValentinesWolfAwarenessSensor extends Sensor<ValentinesWolf> {
    public ValentinesWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, ValentinesWolf wolf) {
        Brain<ValentinesWolf> brain = wolf.getBrain();
        if (!wolf.canProvideValentinesSupport()) {
            brain.eraseMemory(ModMemoryModuleTypes.VALENTINES_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.VALENTINES_FAMILY_IN_NEED.get());
            brain.eraseMemory(ModMemoryModuleTypes.VALENTINES_MARKED_TARGET.get());
            brain.setMemory(ModMemoryModuleTypes.VALENTINES_INJURED_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.VALENTINES_HOSTILE_COUNT.get(), 0);
            return;
        }

        List<LivingEntity> family = wolf.getValentinesFamily(
                level,
                ValentinesWolf.FAMILY_SENSE_RADIUS);
        List<LivingEntity> threats = wolf.getValentinesThreats(level);

        LivingEntity need = family.stream()
                .filter(member -> wolf.valentineNeedScore(member) > 1.0D)
                .max(Comparator.comparingDouble(wolf::valentineNeedScore))
                .orElse(null);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double score = wolf.distanceToSqr(entity);
                    if (entity instanceof Mob mob
                            && wolf.isValentinesFamily(mob.getTarget())) {
                        score -= 10000.0D;
                    }
                    if (wolf.hasRecentlyHurtFamily(entity)) {
                        score -= 7000.0D;
                    }
                    return score;
                }))
                .orElse(null);

        LivingEntity marked = wolf.getMarkedTargetForSensor();
        setOrErase(brain, ModMemoryModuleTypes.VALENTINES_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.VALENTINES_FAMILY_IN_NEED.get(), need);
        setOrErase(brain, ModMemoryModuleTypes.VALENTINES_MARKED_TARGET.get(), marked);
        brain.setMemory(
                ModMemoryModuleTypes.VALENTINES_INJURED_FAMILY_COUNT.get(),
                (int) family.stream()
                        .filter(member -> member.getHealth() < member.getMaxHealth() * 0.75F)
                        .count());
        brain.setMemory(
                ModMemoryModuleTypes.VALENTINES_HOSTILE_COUNT.get(),
                threats.size());
    }

    private static <T> void setOrErase(
            Brain<ValentinesWolf> brain,
            MemoryModuleType<T> key,
            T value) {
        if (value == null) {
            brain.eraseMemory(key);
        } else {
            brain.setMemory(key, value);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.VALENTINES_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.VALENTINES_FAMILY_IN_NEED.get(),
                ModMemoryModuleTypes.VALENTINES_MARKED_TARGET.get(),
                ModMemoryModuleTypes.VALENTINES_INJURED_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.VALENTINES_HOSTILE_COUNT.get());
    }
}
