package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Makes family/pack defense outrank an ongoing prey hunt.
 */
public final class DirePackAssistGoal extends Goal {
    private final DireWolf wolf;
    private LivingEntity sharedThreat;

    public DirePackAssistGoal(DireWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> rememberedThreat = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.DIRE_PACK_THREAT.get());
        if (rememberedThreat.isEmpty()) {
            return false;
        }

        LivingEntity threat = rememberedThreat.get();
        if (!this.wolf.isValidDireCombatTarget(threat)) {
            return false;
        }

        LivingEntity current = this.wolf.getTarget();
        if (current != null && current.isAlive() && !DireWolf.isPreferredPrey(current)) {
            return false;
        }

        this.sharedThreat = threat;
        return true;
    }

    @Override
    public void start() {
        if (this.sharedThreat != null) {
            this.wolf.cancelDemolitionCommand();
            this.wolf.setTarget(this.sharedThreat);
        }
    }

    @Override
    public void stop() {
        this.sharedThreat = null;
    }
}
