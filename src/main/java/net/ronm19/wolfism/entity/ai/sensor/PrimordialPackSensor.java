package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.entity.custom.PrimordialWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Pack Instinct sensor: family geometry and shared combat knowledge. */
public final class PrimordialPackSensor extends Sensor<PrimordialWolf> {
    public PrimordialPackSensor() { super(5); }

    @Override
    protected void doTick(ServerLevel level, PrimordialWolf wolf) {
        List<Wolf> pack = wolf.findOperationalPackWolves(level, PrimordialWolf.PACK_RADIUS);
        LivingEntity sharedThreat = pack.stream()
                .map(Wolf::getTarget)
                .filter(wolf::isPotentialPrimordialThreat)
                .max(Comparator.comparingDouble(wolf::scorePrimordialThreat))
                .orElse(null);

        Brain<PrimordialWolf> brain = wolf.getBrain();
        brain.setMemory(ModMemoryModuleTypes.PRIMORDIAL_PACK_COUNT.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.PRIMORDIAL_SHARED_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<PrimordialWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) brain.setMemory(memory, value); else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.PRIMORDIAL_PACK_COUNT.get(),
                ModMemoryModuleTypes.PRIMORDIAL_SHARED_THREAT.get(),
                ModMemoryModuleTypes.PRIMORDIAL_PRIMAL_STATE_ACTIVE.get(),
                ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_ACTIVE.get(),
                ModMemoryModuleTypes.PRIMORDIAL_PRIMAL_STATE_COOLDOWN.get(),
                ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_COOLDOWN.get());
    }
}
