package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.DemonWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Detects a charmable Nether/demonic mob that is actively threatening family.
 */
public final class DemonWolfCharmSensor extends Sensor<DemonWolf> {
    public DemonWolfCharmSensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, DemonWolf wolf) {
        Brain<DemonWolf> brain = wolf.getBrain();

        Mob charmThreat = level.getEntitiesOfClass(
                        Mob.class,
                        wolf.getBoundingBox().inflate(DemonWolf.CHARM_RADIUS),
                        wolf::isValidDemonCharmThreat)
                .stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);

        if (charmThreat != null) {
            brain.setMemory(
                    ModMemoryModuleTypes.DEMON_CHARM_THREAT.get(),
                    (LivingEntity) charmThreat);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.DEMON_CHARM_THREAT.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(ModMemoryModuleTypes.DEMON_CHARM_THREAT.get());
    }
}
