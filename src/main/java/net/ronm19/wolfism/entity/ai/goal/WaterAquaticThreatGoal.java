package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Lets Water Wolf patrol for hostile underwater mobs instead of behaving like a
 * land wolf that merely happens to tolerate swimming.
 */
public final class WaterAquaticThreatGoal extends Goal {
    private final WaterWolf wolf;
    private LivingEntity target;

    public WaterAquaticThreatGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || !this.wolf.isInWater()) {
            return false;
        }

        Optional<LivingEntity> remembered = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_AQUATIC_THREAT.get());
        if (remembered.isEmpty() || !this.wolf.isValidWaterCombatTarget(remembered.get())) {
            return false;
        }

        this.target = remembered.get();
        return true;
    }

    @Override
    public void start() {
        if (this.target != null) {
            this.wolf.setTarget(this.target);
        }
    }

    @Override
    public void stop() {
        this.target = null;
    }
}
