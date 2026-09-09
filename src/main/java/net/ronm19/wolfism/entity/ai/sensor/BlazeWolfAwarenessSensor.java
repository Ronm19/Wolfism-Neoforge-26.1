package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;

import net.ronm19.wolfism.entity.custom.BlazeWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Blaze Wolf's artillery perception layer.
 *
 * <p>Perception is intentionally separate from execution: this sensor identifies
 * the best general threat, best valid fireball target, a close-range rusher,
 * targets already locked down by family frontliners, hostile/cluster density,
 * fire-immune enemies, and incoming fireball pressure. BlazeWolf consumes those
 * memories to decide distance control, escalation, shielding and barrage spread.</p>
 */
public final class BlazeWolfAwarenessSensor extends Sensor<BlazeWolf> {

    private static final double CLUSTER_RADIUS_SQR = 7.0D * 7.0D;

    public BlazeWolfAwarenessSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, BlazeWolf wolf) {
        List<LivingEntity> threats = wolf.getNearbyBlazeThreats(
                level,
                BlazeWolf.BLAZE_AWARENESS_RADIUS);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(wolf::blazeThreatScore))
                .orElse(null);

        LivingEntity ranged = threats.stream()
                .filter(wolf::canUseBlazeFireballAgainst)
                .filter(wolf.getSensing()::hasLineOfSight)
                .min(Comparator.comparingDouble(wolf::blazeThreatScore))
                .orElse(null);

        LivingEntity close = threats.stream()
                .filter(entity -> wolf.distanceToSqr(entity)
                        <= BlazeWolf.BLAZE_EMERGENCY_RANGE * BlazeWolf.BLAZE_EMERGENCY_RANGE)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        LivingEntity frontlineLocked = threats.stream()
                .filter(wolf::canUseBlazeFireballAgainst)
                .filter(entity -> wolf.isFamilyFrontlinerEngaging(entity, level))
                .min(Comparator.comparingDouble(wolf::blazeThreatScore))
                .orElse(null);

        int clusterSize = 0;
        if (priority != null) {
            for (LivingEntity threat : threats) {
                if (threat.distanceToSqr(priority) <= CLUSTER_RADIUS_SQR) {
                    ++clusterSize;
                }
            }
        }

        int fireImmuneCount = 0;
        for (LivingEntity threat : threats) {
            if (threat.fireImmune()) ++fireImmuneCount;
        }

        int incomingFireballs = level.getEntitiesOfClass(
                        Fireball.class,
                        wolf.getBoundingBox().inflate(8.0D),
                        projectile -> projectile.isAlive()
                                && projectile.getOwner() != wolf
                                && (projectile.getOwner() == null
                                    || !wolf.isBlazeFamilyMember(projectile.getOwner())))
                .size();

        Brain<BlazeWolf> brain = wolf.getBrain();
        setOrErase(brain, ModMemoryModuleTypes.BLAZE_PRIORITY_THREAT.get(), priority);
        setOrErase(brain, ModMemoryModuleTypes.BLAZE_RANGED_TARGET.get(), ranged);
        setOrErase(brain, ModMemoryModuleTypes.BLAZE_CLOSE_THREAT.get(), close);
        setOrErase(brain, ModMemoryModuleTypes.BLAZE_FRONTLINE_LOCKED_TARGET.get(), frontlineLocked);

        brain.setMemory(ModMemoryModuleTypes.BLAZE_HOSTILE_COUNT.get(), threats.size());
        brain.setMemory(ModMemoryModuleTypes.BLAZE_CLUSTER_SIZE.get(), clusterSize);
        brain.setMemory(ModMemoryModuleTypes.BLAZE_FIRE_IMMUNE_COUNT.get(), fireImmuneCount);
        brain.setMemory(ModMemoryModuleTypes.BLAZE_INCOMING_FIREBALL_COUNT.get(), incomingFireballs);
    }

    private static <T> void setOrErase(
            Brain<BlazeWolf> brain,
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
                ModMemoryModuleTypes.BLAZE_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.BLAZE_RANGED_TARGET.get(),
                ModMemoryModuleTypes.BLAZE_CLOSE_THREAT.get(),
                ModMemoryModuleTypes.BLAZE_FRONTLINE_LOCKED_TARGET.get(),
                ModMemoryModuleTypes.BLAZE_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.BLAZE_CLUSTER_SIZE.get(),
                ModMemoryModuleTypes.BLAZE_FIRE_IMMUNE_COUNT.get(),
                ModMemoryModuleTypes.BLAZE_INCOMING_FIREBALL_COUNT.get());
    }
}
