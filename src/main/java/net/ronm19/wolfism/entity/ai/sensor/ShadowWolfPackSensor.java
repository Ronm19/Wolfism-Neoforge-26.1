package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.ShadowWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack coordination, darkness state, target sharing, and memory registration for Shadow Wolf. */
public final class ShadowWolfPackSensor extends Sensor<ShadowWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, ShadowWolf wolf) {
        Brain<ShadowWolf> brain = wolf.getBrain();
        List<ShadowWolf> pack = level.getEntitiesOfClass(
                ShadowWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isShadowPackmate(other));

        ShadowWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        ShadowWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(ShadowWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::isValidShadowCombatTarget)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SHADOW_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SHADOW_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.SHADOW_PACK_SIZE.get(), pack.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.SHADOW_DARKNESS_ACTIVE.get(), wolf.isShadowEmpowered());
        setOrErase(brain, ModMemoryModuleTypes.SHADOW_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<ShadowWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        // Every Shadow memory touched by ShadowWolf is registered here. This is
        // deliberate: ability memories must be part of the Brain's known-memory set.
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SHADOW_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SHADOW_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SHADOW_PACK_SIZE.get(),
                ModMemoryModuleTypes.SHADOW_PACK_THREAT.get(),
                ModMemoryModuleTypes.SHADOW_DARKNESS_ACTIVE.get(),
                ModMemoryModuleTypes.SHADOW_VOID_DASH_TARGET.get(),
                ModMemoryModuleTypes.SHADOW_VOID_DASH_COOLDOWN.get(),
                ModMemoryModuleTypes.SHADOW_BLADES_COOLDOWN.get(),
                ModMemoryModuleTypes.SHADOW_DUSK_VEIL_COOLDOWN.get(),
                ModMemoryModuleTypes.SHADOW_ASSASSIN_COOLDOWN.get());
    }
}
