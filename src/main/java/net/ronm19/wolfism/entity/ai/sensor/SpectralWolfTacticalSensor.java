package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.entity.custom.SpectralWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Tactical perception for Spectral Wolf.
 *
 * <p>Broad awareness never directly forces a long chase. The entity decides
 * whether a remembered target deserves a local phase, Soul Bolt, suppression,
 * rescue or ordinary melee pursuit.</p>
 */
public final class SpectralWolfTacticalSensor extends Sensor<SpectralWolf> {

    private static final double SCAN_RADIUS = 30.0D;
    private static final double LOCAL_RADIUS = 14.0D;

    @Override
    protected void doTick(ServerLevel level, SpectralWolf wolf) {
        AABB scan = wolf.getBoundingBox().inflate(SCAN_RADIUS);

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                scan,
                candidate -> candidate instanceof Enemy
                        && wolf.isValidSpectralThreat(candidate));

        LivingEntity ranged = threats.stream()
                .filter(target -> !target.onGround()
                        || target.getY() > wolf.getY() + 2.0D
                        || wolf.distanceToSqr(target) > 10.0D * 10.0D)
                .min(Comparator.comparingDouble(wolf::spectralThreatScore))
                .orElse(null);

        LivingEntity phase = threats.stream()
                .filter(target -> wolf.distanceToSqr(target) >= 5.5D * 5.5D)
                .filter(target -> wolf.distanceToSqr(target) <= 14.0D * 14.0D)
                .filter(target -> !wolf.hasLineOfSight(target)
                        || target.getY() > wolf.getY() + 2.0D)
                .min(Comparator.comparingDouble(wolf::spectralThreatScore))
                .orElse(null);

        List<LivingEntity> localThreats = threats.stream()
                .filter(target -> wolf.distanceToSqr(target)
                        <= LOCAL_RADIUS * LOCAL_RADIUS)
                .toList();

        LivingEntity cluster = localThreats.stream()
                .max(Comparator.comparingInt(
                        candidate -> countNeighbors(localThreats, candidate)))
                .orElse(null);

        LivingEntity vulnerableFamily = wolf.getNearbySpectralFamily(
                        level,
                        16.0D)
                .stream()
                .filter(wolf::isVulnerableSpectralFamilyMember)
                .min(Comparator.comparingDouble(
                        member -> member.getHealth()
                                / Math.max(1.0F, member.getMaxHealth())))
                .orElse(null);

        Brain<SpectralWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.SPECTRAL_RANGED_TARGET.get(),
                ranged);
        setOrErase(
                brain,
                ModMemoryModuleTypes.SPECTRAL_PHASE_TARGET.get(),
                phase);
        setOrErase(
                brain,
                ModMemoryModuleTypes.SPECTRAL_VULNERABLE_FAMILY.get(),
                vulnerableFamily);
        setOrErase(
                brain,
                ModMemoryModuleTypes.SPECTRAL_CLUSTER_TARGET.get(),
                cluster);

        brain.setMemory(
                ModMemoryModuleTypes.SPECTRAL_HOSTILE_COUNT.get(),
                localThreats.size());
    }

    private static int countNeighbors(
            List<LivingEntity> threats,
            LivingEntity center) {

        int count = 0;

        for (LivingEntity other : threats) {
            if (other.position().distanceToSqr(center.position())
                    <= 6.0D * 6.0D) {
                ++count;
            }
        }

        return count;
    }

    private static <T> void setOrErase(
            Brain<SpectralWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) brain.setMemory(memory, value);
        else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.SPECTRAL_RANGED_TARGET.get(),
                ModMemoryModuleTypes.SPECTRAL_PHASE_TARGET.get(),
                ModMemoryModuleTypes.SPECTRAL_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.SPECTRAL_CLUSTER_TARGET.get(),
                ModMemoryModuleTypes.SPECTRAL_HOSTILE_COUNT.get());
    }
}
