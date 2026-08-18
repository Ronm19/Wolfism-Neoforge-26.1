package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.StormWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Weather and electrical-resource awareness for Storm Wolf. */
public final class StormWolfCombatSensor extends Sensor<StormWolf> {
    @Override
    protected void doTick(ServerLevel level, StormWolf wolf) {
        Brain<StormWolf> brain = wolf.getBrain();

        int weatherState = level.isThundering() && level.isRainingAt(wolf.blockPosition())
                ? 2
                : (level.isRainingAt(wolf.blockPosition()) ? 1 : 0);
        brain.setMemory(ModMemoryModuleTypes.STORM_WEATHER_STATE.get(), weatherState);

        if (!brain.hasMemoryValue(ModMemoryModuleTypes.STORM_CHARGE.get()) && !wolf.isBaby()) {
            brain.setMemory(ModMemoryModuleTypes.STORM_CHARGE.get(), 0);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.STORM_WEATHER_STATE.get(),
                ModMemoryModuleTypes.STORM_CHARGE.get(),
                ModMemoryModuleTypes.STORM_THUNDER_STRIKE_TARGET.get(),
                ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get(),
                ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get());
    }
}
