package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.phys.AABB;

import net.ronm19.wolfism.entity.custom.WarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Tactical perception for War Wolf.
 *
 * <p>War can understand a battle farther out than he is allowed to physically
 * acquire ordinary melee targets. Awareness does not expand physical aggro.</p>
 */
public final class WarWolfTacticalSensor extends Sensor<WarWolf> {

    private static final double SCAN_RADIUS = 28.0D;
    private static final double LOCAL_COMBAT_RADIUS = 12.0D;
    private static final double FAMILY_SCAN_RADIUS = 20.0D;

    @Override
    protected void doTick(ServerLevel level, WarWolf wolf) {
        AABB scan = wolf.getBoundingBox().inflate(SCAN_RADIUS);

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                scan,
                wolf::isValidWarThreat);

        LivingEntity primary = threats.stream()
                .min(Comparator.comparingDouble(wolf::warThreatScore))
                .orElse(null);

        int localHostiles = (int) threats.stream()
                .filter(target -> wolf.distanceToSqr(target)
                        <= LOCAL_COMBAT_RADIUS * LOCAL_COMBAT_RADIUS)
                .count();

        LivingEntity vulnerableFamily = wolf.getNearbyWarFamily(
                        level,
                        FAMILY_SCAN_RADIUS)
                .stream()
                .filter(wolf::isVulnerableWarFamilyMember)
                .min(Comparator.comparingDouble(
                        member -> member.getHealth()
                                / Math.max(1.0F, member.getMaxHealth())))
                .orElse(null);

        Brain<WarWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.WAR_PRIMARY_TARGET.get(),
                primary);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WAR_VULNERABLE_FAMILY.get(),
                vulnerableFamily);

        brain.setMemory(
                ModMemoryModuleTypes.WAR_HOSTILE_COUNT.get(),
                localHostiles);
    }

    private static <T> void setOrErase(
            Brain<WarWolf> brain,
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
                ModMemoryModuleTypes.WAR_PRIMARY_TARGET.get(),
                ModMemoryModuleTypes.WAR_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.WAR_HOSTILE_COUNT.get());
    }
}
