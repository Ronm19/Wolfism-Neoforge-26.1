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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.entity.custom.VampireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Reads the fight for Vampire Wolf. This sensor deliberately separates broad
 * awareness from physical aggro: ranged/Charm candidates may be remembered at
 * distance, but VampireWolf itself decides whether to bite, charge, control,
 * protect, shoot, or cast Rain of Blood.
 */
public final class VampireWolfTacticalSensor extends Sensor<VampireWolf> {
    public static final double TACTICAL_SCAN_RADIUS = 30.0D;
    private static final double LOCAL_COMBAT_RADIUS = 14.0D;

    @Override
    protected void doTick(ServerLevel level, VampireWolf wolf) {
        AABB tacticalArea = wolf.getBoundingBox().inflate(TACTICAL_SCAN_RADIUS);

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                tacticalArea,
                candidate -> candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && wolf.isValidVampireThreat(candidate));

        LivingEntity flying = threats.stream()
                .filter(target -> !target.onGround() || target.getY() > wolf.getY() + 2.0D)
                .min(Comparator.comparingDouble(wolf::vampireThreatScore))
                .orElse(null);

        LivingEntity drain = threats.stream()
                .filter(target -> wolf.distanceToSqr(target) <= 12.0D * 12.0D)
                .min(Comparator
                        .comparingDouble((LivingEntity target) -> wolf.distanceToSqr(target))
                        .thenComparingDouble(target -> target.getHealth() / Math.max(1.0F, target.getMaxHealth())))
                .orElse(null);

        List<LivingEntity> localThreats = threats.stream()
                .filter(target -> wolf.distanceToSqr(target) <= LOCAL_COMBAT_RADIUS * LOCAL_COMBAT_RADIUS)
                .toList();

        LivingEntity charm = localThreats.stream()
                .filter(target -> target instanceof Mob mob
                        && mob.getTarget() != null
                        && wolf.isVampireFamilyMember(mob.getTarget()))
                .min(Comparator.comparingDouble(wolf::vampireThreatScore))
                .orElseGet(() -> localThreats.stream()
                        .filter(Mob.class::isInstance)
                        .min(Comparator.comparingDouble(wolf::vampireThreatScore))
                        .orElse(null));

        LivingEntity vulnerableFamily = wolf.getNearbyVampireFamily(level, 14.0D).stream()
                .filter(wolf::isVulnerableFamilyMember)
                .min(Comparator.comparingDouble(
                        member -> member.getHealth() / Math.max(1.0F, member.getMaxHealth())))
                .orElse(null);

        LivingEntity clusterAnchor = localThreats.stream()
                .max(Comparator.comparingInt(candidate -> countNeighbors(localThreats, candidate)))
                .orElse(null);

        Brain<VampireWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.VAMPIRE_FLYING_TARGET.get(), flying);
        setOrErase(brain, ModMemoryModuleTypes.VAMPIRE_DRAIN_TARGET.get(), drain);
        setOrErase(brain, ModMemoryModuleTypes.VAMPIRE_CHARM_TARGET.get(), charm);
        setOrErase(brain, ModMemoryModuleTypes.VAMPIRE_VULNERABLE_FAMILY.get(), vulnerableFamily);
        setOrErase(brain, ModMemoryModuleTypes.VAMPIRE_CLUSTER_TARGET.get(), clusterAnchor);
        brain.setMemory(ModMemoryModuleTypes.VAMPIRE_HOSTILE_COUNT.get(), localThreats.size());
    }

    private static int countNeighbors(List<LivingEntity> threats, LivingEntity center) {
        int count = 0;
        for (LivingEntity other : threats) {
            if (other.position().distanceToSqr(center.position()) <= 6.0D * 6.0D) {
                ++count;
            }
        }
        return count;
    }

    private static <T> void setOrErase(
            Brain<VampireWolf> brain,
            MemoryModuleType<T> memory,
            T value) {
        if (value != null) brain.setMemory(memory, value);
        else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.VAMPIRE_FLYING_TARGET.get(),
                ModMemoryModuleTypes.VAMPIRE_DRAIN_TARGET.get(),
                ModMemoryModuleTypes.VAMPIRE_CHARM_TARGET.get(),
                ModMemoryModuleTypes.VAMPIRE_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.VAMPIRE_CLUSTER_TARGET.get(),
                ModMemoryModuleTypes.VAMPIRE_HOSTILE_COUNT.get());
    }
}
