package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Starts and joins coordinated wild Timber hunts.
 *
 * <p>Only the local pack leader may originate a new hunt. Other adults join the
 * prey target already being pursued by a packmate, which naturally converges a
 * group on one animal rather than giving every wolf an unrelated target.</p>
 */
public final class TimberHuntGoal extends Goal {
    private final TimberWolf wolf;
    private LivingEntity prey;

    public TimberHuntGoal(TimberWolf wolf) {
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
                || this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.TIMBER_PACK_THREAT.get())) {
            return false;
        }

        Optional<LivingEntity> sharedHunt = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get());
        if (sharedHunt.isPresent() && isEligible(sharedHunt.get())) {
            this.prey = sharedHunt.get();
            return true;
        }

        Optional<TimberWolf> leader = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.TIMBER_PACK_LEADER.get());
        if (leader.isPresent() && leader.get() != this.wolf) {
            return false;
        }

        Optional<LivingEntity> nearestPrey = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_TIMBER_PREY.get());
        if (nearestPrey.isEmpty() || !isEligible(nearestPrey.get())) {
            return false;
        }

        this.prey = nearestPrey.get();
        return true;
    }

    private boolean isEligible(LivingEntity target) {
        return target.isAlive()
                && TimberWolf.isPreferredPrey(target)
                && this.wolf.hasEnoughHuntersFor(target)
                && this.wolf.isValidTimberCombatTarget(target);
    }

    @Override
    public void start() {
        if (this.prey != null) {
            this.wolf.getBrain().setMemory(ModMemoryModuleTypes.TIMBER_HUNT_TARGET.get(), this.prey);
            this.wolf.setTarget(this.prey);
        }
    }

    @Override
    public void stop() {
        this.prey = null;
    }
}
