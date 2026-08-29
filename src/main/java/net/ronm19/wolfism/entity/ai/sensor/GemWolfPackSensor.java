package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.GemWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack coordination plus exhaustive Brain-memory registration for Gem Wolf. */
public final class GemWolfPackSensor extends Sensor<GemWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;

    @Override
    protected void doTick(ServerLevel level, GemWolf wolf) {
        Brain<GemWolf> brain = wolf.getBrain();
        List<GemWolf> pack = level.getEntitiesOfClass(
                GemWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isGemPackmate(other));

        GemWolf nearest = pack.stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        GemWolf nearestAdult = pack.stream()
                .filter(candidate -> !candidate.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
        LivingEntity sharedThreat = pack.stream()
                .map(GemWolf::getTarget)
                .filter(target -> target != null && target.isAlive())
                .filter(wolf::canAttack)
                .findFirst()
                .orElse(null);

        setOrErase(brain, ModMemoryModuleTypes.NEAREST_GEM_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_GEM_ADULT_PACKMATE.get(), nearestAdult);
        brain.setMemory(ModMemoryModuleTypes.GEM_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.GEM_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<GemWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_GEM_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_GEM_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.GEM_PACK_SIZE.get(),
                ModMemoryModuleTypes.GEM_PACK_THREAT.get(),
                ModMemoryModuleTypes.GEM_ORE_POS.get(),
                ModMemoryModuleTypes.GEM_MARKED_ORE_POS.get(),
                ModMemoryModuleTypes.GEM_VEIN_MARK_COOLDOWN.get(),
                ModMemoryModuleTypes.GEM_CRYSTAL_DASH_COOLDOWN.get(),
                ModMemoryModuleTypes.GEM_GEO_RESONANCE_COOLDOWN.get());
    }
}
