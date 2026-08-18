package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Starts and joins coordinated wild Sand Wolf hunts. */
public final class SandHuntGoal extends Goal {
    private final SandWolf wolf;
    private LivingEntity prey;

    public SandHuntGoal(SandWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || this.wolf.isHuntCoolingDown()
                || this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.SAND_PACK_THREAT.get())) {
            return false;
        }

        Optional<LivingEntity> sharedHunt = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_HUNT_TARGET.get());
        if (sharedHunt.isPresent() && isEligible(sharedHunt.get())) {
            this.prey = sharedHunt.get();
            return true;
        }

        Optional<SandWolf> leader = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_PACK_LEADER.get());
        if (leader.isPresent() && leader.get() != this.wolf) {
            return false;
        }

        Optional<LivingEntity> nearestPrey = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_SAND_PREY.get());
        if (nearestPrey.isEmpty() || !isEligible(nearestPrey.get())) {
            return false;
        }

        this.prey = nearestPrey.get();
        return true;
    }

    private boolean isEligible(LivingEntity target) {
        return target.isAlive()
                && SandWolf.isPreferredPrey(target)
                && this.wolf.isValidSandCombatTarget(target);
    }

    @Override
    public void start() {
        if (this.prey != null) {
            this.wolf.getBrain().setMemory(ModMemoryModuleTypes.SAND_HUNT_TARGET.get(), this.prey);
            this.wolf.setTarget(this.prey);
        }
    }

    @Override
    public void stop() {
        this.prey = null;
    }
}
