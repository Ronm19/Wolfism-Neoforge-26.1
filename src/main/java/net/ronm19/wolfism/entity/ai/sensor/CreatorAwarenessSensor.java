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
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Whole-battlefield awareness for Creator Wolf. */
public final class CreatorAwarenessSensor extends Sensor<CreatorWolf> {
    public CreatorAwarenessSensor() { super(2); }

    @Override
    protected void doTick(ServerLevel level, CreatorWolf wolf) {
        double radius = wolf.getCurrentAwarenessRadius();
        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                wolf::isPotentialCreatorThreat);

        LivingEntity shared = wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_SHARED_THREAT.get())
                .filter(wolf::isPotentialCreatorThreat)
                .orElse(null);

        LivingEntity priority = threats.stream()
                .max(Comparator.comparingDouble(wolf::scoreCreatorThreat))
                .orElse(null);
        if (shared != null
                && (priority == null
                || wolf.scoreCreatorThreat(shared) > wolf.scoreCreatorThreat(priority))) {
            priority = shared;
        }

        LivingEntity endangered = null;
        LivingEntity attacker = null;
        double bestEmergency = 0.0D;

        for (LivingEntity threat : threats) {
            if (!(threat instanceof Mob mob)) continue;
            LivingEntity victim = mob.getTarget();
            if (victim == null || !wolf.isCreatorFamilyMember(victim)) continue;

            double healthRatio = victim.getHealth() / Math.max(1.0F, victim.getMaxHealth());
            double emergency = wolf.scoreCreatorThreat(threat) + (1.0D - healthRatio) * 140.0D;
            if (victim == wolf.getOwner()) emergency += 90.0D;

            if (emergency > bestEmergency) {
                bestEmergency = emergency;
                endangered = victim;
                attacker = threat;
            }
        }

        if (wolf.isTame() && endangered == null) {
            endangered = wolf.findCreatorFamily(level, CreatorWolf.FAMILY_RADIUS).stream()
                    .filter(member -> member != wolf)
                    .filter(LivingEntity::isAlive)
                    .filter(member -> member.getHealth() / Math.max(1.0F, member.getMaxHealth()) <= 0.34D)
                    .min(Comparator.comparingDouble(
                            member -> member.getHealth() / Math.max(1.0F, member.getMaxHealth())))
                    .orElse(null);
        }

        Brain<CreatorWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.CREATOR_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.CREATOR_ENDANGERED_FAMILY.get(), endangered);
        setOrErase(brain, ModMemoryModuleTypes.CREATOR_FAMILY_ATTACKER.get(), attacker);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_HOSTILE_COUNT.get(), threats.size());
    }

    private static <T> void setOrErase(Brain<CreatorWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) brain.setMemory(memory, value);
        else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.CREATOR_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.CREATOR_SHARED_THREAT.get(),
                ModMemoryModuleTypes.CREATOR_ENDANGERED_FAMILY.get(),
                ModMemoryModuleTypes.CREATOR_FAMILY_ATTACKER.get(),
                ModMemoryModuleTypes.CREATOR_HOSTILE_COUNT.get());
    }
}
