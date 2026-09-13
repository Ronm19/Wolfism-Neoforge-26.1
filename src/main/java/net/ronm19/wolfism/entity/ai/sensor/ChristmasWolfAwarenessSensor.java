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
import net.ronm19.wolfism.entity.custom.ChristmasWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family needs and incoming winter/projectile danger, refreshed every 10 ticks. */
public final class ChristmasWolfAwarenessSensor extends Sensor<ChristmasWolf> {
    public ChristmasWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, ChristmasWolf wolf) {
        Brain<ChristmasWolf> brain = wolf.getBrain();
        if (!wolf.canProvideChristmasSupport()) {
            brain.eraseMemory(ModMemoryModuleTypes.CHRISTMAS_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.CHRISTMAS_FAMILY_IN_NEED.get());
            brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_INJURED_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_COLD_FAMILY_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_INCOMING_PROJECTILE_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_HOSTILE_COUNT.get(), 0);
            return;
        }
        List<LivingEntity> family = wolf.getChristmasFamily(level, ChristmasWolf.FAMILY_SENSE_RADIUS);
        List<LivingEntity> threats = wolf.getChristmasThreats(level);
        LivingEntity need = family.stream()
                .filter(member -> wolf.christmasNeedScore(member) > 1.0D)
                .max(Comparator.comparingDouble(wolf::christmasNeedScore)).orElse(null);
        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double score = wolf.distanceToSqr(entity);
                    if (entity instanceof Mob mob && wolf.isChristmasFamily(mob.getTarget())) {
                        // An engaged family attacker outranks every ambient sighting
                        // in this bounded 24-block sensing area.
                        score -= 10000.0D;
                    }
                    return score;
                })).orElse(null);
        int injured = (int) family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.90F).count();
        int cold = (int) family.stream()
                .filter(member -> member.getTicksFrozen() > 0 || member.isInPowderSnow).count();

        setOrErase(brain, ModMemoryModuleTypes.CHRISTMAS_FAMILY_IN_NEED.get(), need);
        setOrErase(brain, ModMemoryModuleTypes.CHRISTMAS_PRIORITY_THREAT.get(), priority);
        brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_INJURED_FAMILY_COUNT.get(), injured);
        brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_COLD_FAMILY_COUNT.get(), cold);
        brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_INCOMING_PROJECTILE_COUNT.get(),
                wolf.countIncomingChristmasProjectiles(level, family));
        brain.setMemory(ModMemoryModuleTypes.CHRISTMAS_HOSTILE_COUNT.get(), threats.size());
    }

    private static <T> void setOrErase(Brain<ChristmasWolf> brain, MemoryModuleType<T> key, T value) {
        if (value == null) brain.eraseMemory(key);
        else brain.setMemory(key, value);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.CHRISTMAS_FAMILY_IN_NEED.get(),
                ModMemoryModuleTypes.CHRISTMAS_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.CHRISTMAS_INJURED_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.CHRISTMAS_COLD_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.CHRISTMAS_INCOMING_PROJECTILE_COUNT.get(),
                ModMemoryModuleTypes.CHRISTMAS_HOSTILE_COUNT.get());
    }
}
