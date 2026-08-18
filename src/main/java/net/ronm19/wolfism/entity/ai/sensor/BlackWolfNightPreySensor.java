package net.ronm19.wolfism.entity.ai.sensor;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Black Wolf night senses.
 *
 * <p>Wild adults deliberately hunt only after dark. The larger 30-block prey
 * awareness is behavioral night vision/hearing/scent, not a magical effect.</p>
 */
public final class BlackWolfNightPreySensor extends Sensor<BlackWolf> {
    public static final double NIGHT_PREY_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, BlackWolf wolf) {
        Brain<BlackWolf> brain = wolf.getBrain();
        boolean darkOutside = level.isDarkOutside();
        brain.setMemory(ModMemoryModuleTypes.BLACK_NIGHT_ACTIVE.get(), darkOutside);

        if (!darkOutside || wolf.isTame() || wolf.isBaby() || wolf.isHuntCoolingDown()) {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_BLACK_PREY.get());
            if (!darkOutside) {
                brain.eraseMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
                brain.eraseMemory(ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get());
            }
            return;
        }

        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(NIGHT_PREY_RADIUS),
                candidate -> candidate != wolf
                        && candidate.isAlive()
                        && BlackWolf.isPreferredPrey(candidate)
                        && wolf.isValidBlackCombatTarget(candidate));

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = wolf.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        if (nearest != null) {
            brain.setMemory(ModMemoryModuleTypes.NEAREST_BLACK_PREY.get(), nearest);
        } else {
            brain.eraseMemory(ModMemoryModuleTypes.NEAREST_BLACK_PREY.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_BLACK_PREY.get(),
                ModMemoryModuleTypes.BLACK_HUNT_TARGET.get(),
                ModMemoryModuleTypes.BLACK_NIGHT_ACTIVE.get(),
                ModMemoryModuleTypes.BLACK_AMBUSH_TARGET.get(),
                ModMemoryModuleTypes.BLACK_HUNT_COOLDOWN.get(),
                ModMemoryModuleTypes.BLACK_AMBUSH_COOLDOWN.get());
    }
}