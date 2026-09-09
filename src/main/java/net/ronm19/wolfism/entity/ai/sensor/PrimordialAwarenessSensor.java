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
import net.ronm19.wolfism.entity.custom.PrimordialWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Primordial Awareness / Primal Senses.
 * Runs frequently and evaluates threat, movement, health, distance and family danger.
 */
public final class PrimordialAwarenessSensor extends Sensor<PrimordialWolf> {
    public PrimordialAwarenessSensor() { super(3); }

    @Override
    protected void doTick(ServerLevel level, PrimordialWolf wolf) {
        double radius = wolf.getCurrentAwarenessRadius();
        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                wolf::isPotentialPrimordialThreat);

        LivingEntity shared = wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_SHARED_THREAT.get())
                .filter(wolf::isPotentialPrimordialThreat)
                .orElse(null);

        LivingEntity priority = threats.stream()
                .max(Comparator.comparingDouble(wolf::scorePrimordialThreat))
                .orElse(null);
        if (shared != null && (priority == null || wolf.scorePrimordialThreat(shared) > wolf.scorePrimordialThreat(priority))) {
            priority = shared;
        }

        LivingEntity familyInDanger = null;
        LivingEntity familyAttacker = null;
        double bestDangerScore = 0.0D;
        for (LivingEntity threat : threats) {
            if (!(threat instanceof Mob mob)) continue;
            LivingEntity victim = mob.getTarget();
            if (victim == null || !wolf.isPrimordialFamilyMember(victim)) continue;

            double ratio = victim.getHealth() / Math.max(1.0F, victim.getMaxHealth());
            double dangerScore = (1.0D - ratio) * 100.0D + wolf.scorePrimordialThreat(threat);
            if (ratio <= 0.55D) dangerScore += 35.0D;
            if (dangerScore > bestDangerScore) {
                bestDangerScore = dangerScore;
                familyInDanger = victim;
                familyAttacker = threat;
            }
        }

        // Low-health family is still considered vulnerable even before an enemy acquires it.
        if (wolf.isTame()) {
            LivingEntity vulnerable = wolf.findFamily(level, PrimordialWolf.SURVIVAL_INTERCEPT_RADIUS).stream()
                    .filter(member -> member != wolf)
                    .filter(LivingEntity::isAlive)
                    .filter(member -> member.getHealth() / Math.max(1.0F, member.getMaxHealth()) <= 0.38D)
                    .min(Comparator.comparingDouble(member -> member.getHealth() / Math.max(1.0F, member.getMaxHealth())))
                    .orElse(null);
            if (vulnerable != null && familyInDanger == null) familyInDanger = vulnerable;
        }

        int combatReadyPack = wolf.findOperationalPackWolves(level, 22.0D).stream()
                .filter(packmate -> !packmate.isBaby())
                .filter(packmate -> !packmate.isOrderedToSit() && !packmate.isInSittingPose())
                .mapToInt(packmate -> 1).sum() + 1;
        int hostileCount = (int) threats.stream().filter(t -> wolf.distanceToSqr(t) <= 22.0D * 22.0D).count();
        boolean outnumbered = hostileCount >= combatReadyPack + 1;

        Brain<PrimordialWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.PRIMORDIAL_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.PRIMORDIAL_FAMILY_IN_DANGER.get(), familyInDanger);
        setOrErase(brain, ModMemoryModuleTypes.PRIMORDIAL_FAMILY_ATTACKER.get(), familyAttacker);
        brain.setMemory(ModMemoryModuleTypes.PRIMORDIAL_HOSTILE_COUNT.get(), hostileCount);
        brain.setMemory(ModMemoryModuleTypes.PRIMORDIAL_PACK_OUTNUMBERED.get(), outnumbered);
    }

    private static <T> void setOrErase(Brain<PrimordialWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) brain.setMemory(memory, value); else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.PRIMORDIAL_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.PRIMORDIAL_FAMILY_IN_DANGER.get(),
                ModMemoryModuleTypes.PRIMORDIAL_FAMILY_ATTACKER.get(),
                ModMemoryModuleTypes.PRIMORDIAL_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.PRIMORDIAL_PACK_OUTNUMBERED.get(),
                ModMemoryModuleTypes.PRIMORDIAL_SHARED_THREAT.get());
    }
}
