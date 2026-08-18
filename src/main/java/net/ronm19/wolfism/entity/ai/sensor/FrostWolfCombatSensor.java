package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.FrostWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/** Frost environment awareness plus chilled-target combat awareness. */
public final class FrostWolfCombatSensor extends Sensor<FrostWolf> {
    @Override
    protected void doTick(ServerLevel level, FrostWolf wolf) {
        Brain<FrostWolf> brain = wolf.getBrain();

        boolean cold = level.getBiome(wolf.blockPosition())
                .is(ModBiomeTags.FROST_WOLF_COLD_ENVIRONMENT);
        brain.setMemory(ModMemoryModuleTypes.FROST_COLD_ENVIRONMENT.get(), cold);

        LivingEntity target = wolf.getTarget();
        if (target != null
                && target.isAlive()
                && !(target instanceof Creeper)
                && target.getTicksFrozen() > 0
                && wolf.isValidFrostCombatTarget(target)) {
            brain.setMemory(ModMemoryModuleTypes.FROST_CHILLED_TARGET.get(), target);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.FROST_CHILLED_TARGET.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.FROST_COLD_ENVIRONMENT.get(),
                ModMemoryModuleTypes.FROST_CHILLED_TARGET.get(),
                ModMemoryModuleTypes.FROST_ICE_SPIKE_TARGET.get(),
                ModMemoryModuleTypes.FROST_ICE_SPIKE_COOLDOWN.get());
    }
}
