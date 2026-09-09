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

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.custom.AshWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Ash Wolf's perception-control decision sensor.
 *
 * <p>The sensor does not itself cast abilities. It tells Ash what kind of fight
 * she is in: ranged pressure, burning targets, vulnerable family, clustering,
 * or multiple enemies hard-focusing her. AshWolf then chooses the correct tool.</p>
 */
public final class AshWolfAwarenessSensor extends Sensor<AshWolf> {

    private static final double CLUSTER_RADIUS = 7.0D;
    private static final double CLUSTER_RADIUS_SQR = CLUSTER_RADIUS * CLUSTER_RADIUS;

    @Override
    protected void doTick(ServerLevel level, AshWolf wolf) {
        List<LivingEntity> threats = wolf.getNearbyAshThreats(
                level,
                AshWolf.ASH_AWARENESS_RADIUS);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(wolf::ashThreatScore))
                .orElse(null);

        LivingEntity ranged = threats.stream()
                .filter(wolf::isRangedAshThreat)
                .min(Comparator.comparingDouble(wolf::ashThreatScore))
                .orElse(null);

        LivingEntity burning = threats.stream()
                .filter(wolf::isBurningAshThreat)
                .min(Comparator.comparingDouble(wolf::ashThreatScore))
                .orElse(null);

        LivingEntity vulnerable = null;

        for (AbstractWolfismWolf family : wolf.getNearbyAshFamily(
                level,
                AshWolf.ASH_FAMILY_RADIUS)) {

            if (!wolf.isVulnerableAshFamilyMember(family)) continue;

            if (vulnerable == null
                    || family.getHealth() / Math.max(1.0F, family.getMaxHealth())
                    < vulnerable.getHealth() / Math.max(1.0F, vulnerable.getMaxHealth())) {
                vulnerable = family;
            }
        }

        LivingEntity owner = wolf.getOwner();
        if (owner != null
                && owner.isAlive()
                && wolf.distanceToSqr(owner)
                <= AshWolf.ASH_FAMILY_RADIUS * AshWolf.ASH_FAMILY_RADIUS
                && wolf.isVulnerableAshFamilyMember(owner)) {

            if (vulnerable == null
                    || owner.getHealth() / Math.max(1.0F, owner.getMaxHealth())
                    < vulnerable.getHealth() / Math.max(1.0F, vulnerable.getMaxHealth())) {
                vulnerable = owner;
            }
        }

        int clusterSize = 0;
        if (priority != null) {
            for (LivingEntity threat : threats) {
                if (threat.distanceToSqr(priority) <= CLUSTER_RADIUS_SQR) {
                    ++clusterSize;
                }
            }
        }

        int focusingAsh = 0;
        for (LivingEntity threat : threats) {
            if (threat instanceof Mob mob && mob.getTarget() == wolf) {
                ++focusingAsh;
            }
        }

        Brain<AshWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.ASH_PRIORITY_THREAT.get(),
                priority);
        setOrErase(
                brain,
                ModMemoryModuleTypes.ASH_RANGED_THREAT.get(),
                ranged);
        setOrErase(
                brain,
                ModMemoryModuleTypes.ASH_BURNING_THREAT.get(),
                burning);
        setOrErase(
                brain,
                ModMemoryModuleTypes.ASH_VULNERABLE_FAMILY.get(),
                vulnerable);

        brain.setMemory(
                ModMemoryModuleTypes.ASH_HOSTILE_COUNT.get(),
                threats.size());
        brain.setMemory(
                ModMemoryModuleTypes.ASH_CLUSTER_SIZE.get(),
                clusterSize);
        brain.setMemory(
                ModMemoryModuleTypes.ASH_FOCUSING_COUNT.get(),
                focusingAsh);
    }

    private static <T> void setOrErase(
            Brain<AshWolf> brain,
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
                ModMemoryModuleTypes.ASH_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.ASH_RANGED_THREAT.get(),
                ModMemoryModuleTypes.ASH_BURNING_THREAT.get(),
                ModMemoryModuleTypes.ASH_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.ASH_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.ASH_CLUSTER_SIZE.get(),
                ModMemoryModuleTypes.ASH_FOCUSING_COUNT.get());
    }
}
