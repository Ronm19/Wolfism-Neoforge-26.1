package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.AstralWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Shared Astral threat memory -> Astral target selection.
 */
public final class AstralPackAssistGoal extends Goal {
    private final AstralWolf wolf;
    private LivingEntity threat;

    public AstralPackAssistGoal(
            AstralWolf wolf) {
        this.wolf = wolf;
        this.setFlags(
                EnumSet.of(
                        Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby()
                || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> memory =
                this.wolf.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get());

        if (memory.isEmpty()) {
            return false;
        }

        LivingEntity candidate =
                memory.get();

        if (candidate instanceof Creeper
                || !this.wolf.isValidAstralCombatTarget(candidate)) {
            return false;
        }

        LivingEntity current =
                this.wolf.getTarget();

        if (current instanceof Creeper) {
            return false;
        }

        this.threat = candidate;

        return current == null
                || !current.isAlive()
                || current == candidate;
    }

    @Override
    public void start() {
        if (this.threat != null) {
            this.wolf.setTarget(
                    this.threat);
        }
    }

    @Override
    public void stop() {
        this.threat = null;
    }
}
