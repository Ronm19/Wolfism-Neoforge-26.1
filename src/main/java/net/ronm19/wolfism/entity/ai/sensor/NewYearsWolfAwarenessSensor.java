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
import net.ronm19.wolfism.entity.ai.support.NewYearsCooldownSupport;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family readiness and nearby battle pressure, refreshed every ten ticks. */
public final class NewYearsWolfAwarenessSensor extends Sensor<NewYearsWolf> {
    public NewYearsWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, NewYearsWolf wolf) {
        Brain<NewYearsWolf> brain = wolf.getBrain();

        if (!wolf.canProvideNewYearsSupport()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEW_YEARS_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.NEW_YEARS_FAMILY_IN_NEED.get());
            brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_INJURED_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_HARMFUL_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_FATIGUED_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_HOSTILE_COUNT.get(), 0);
            return;
        }

        List<LivingEntity> family = wolf.getNewYearsFamily(
                level,
                NewYearsWolf.FAMILY_SENSE_RADIUS);
        List<LivingEntity> threats = wolf.getNewYearsThreats(level);

        LivingEntity need = family.stream()
                .filter(member -> wolf.newYearsNeedScore(member) > 1.0D)
                .max(Comparator.comparingDouble(wolf::newYearsNeedScore))
                .orElse(null);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double score = wolf.distanceToSqr(entity);
                    if (entity instanceof Mob mob
                            && wolf.isNewYearsFamily(mob.getTarget())) {
                        score -= 10000.0D;
                    }
                    return score;
                }))
                .orElse(null);

        int injured = (int) family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.75F)
                .count();
        int harmful = (int) family.stream()
                .filter(member -> NewYearsWolf.countHarmfulEffects(member) > 0)
                .count();
        int fatigued = (int) family.stream()
                .filter(member -> member instanceof net.ronm19.wolfism.entity.AbstractWolfismWolf other
                        && NewYearsCooldownSupport.countEligible(other) > 0)
                .count();

        setOrErase(brain, ModMemoryModuleTypes.NEW_YEARS_FAMILY_IN_NEED.get(), need);
        setOrErase(brain, ModMemoryModuleTypes.NEW_YEARS_PRIORITY_THREAT.get(), priority);
        brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_INJURED_FAMILY_COUNT.get(), injured);
        brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_HARMFUL_FAMILY_COUNT.get(), harmful);
        brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_FATIGUED_FAMILY_COUNT.get(), fatigued);
        brain.setMemory(ModMemoryModuleTypes.NEW_YEARS_HOSTILE_COUNT.get(), threats.size());
    }

    private static <T> void setOrErase(
            Brain<NewYearsWolf> brain,
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
                ModMemoryModuleTypes.NEW_YEARS_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.NEW_YEARS_FAMILY_IN_NEED.get(),
                ModMemoryModuleTypes.NEW_YEARS_INJURED_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.NEW_YEARS_HARMFUL_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.NEW_YEARS_FATIGUED_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.NEW_YEARS_HOSTILE_COUNT.get());
    }
}
