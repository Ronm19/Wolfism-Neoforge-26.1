package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.ronm19.wolfism.entity.custom.EndWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Converts End pack threat memory into a real combat target.
 *
 * <p>Universal Wolfism family defense remains higher priority and Creepers are
 * deliberately ignored here because the shared Creeper system owns them.</p>
 */
public final class EndPackAssistGoal extends Goal {
    private final EndWolf wolf;
    private LivingEntity sharedThreat;

    public EndPackAssistGoal(EndWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> remembered = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.END_PACK_THREAT.get());
        if (remembered.isEmpty()) {
            return false;
        }

        LivingEntity threat = remembered.get();
        if (threat instanceof Creeper || !this.wolf.isValidEndCombatTarget(threat)) {
            return false;
        }

        LivingEntity current = this.wolf.getTarget();
        if (current instanceof Creeper) {
            return false;
        }

        // Do not overwrite owner-commanded/retaliation targets that are not just
        // ordinary autonomous hostile prey.
        if (current != null && current.isAlive() && !(current instanceof Enemy)) {
            return false;
        }

        this.sharedThreat = threat;
        return current == null
                || !current.isAlive()
                || current == threat
                || this.healthRatio(threat) + 0.08F < this.healthRatio(current);
    }

    @Override
    public void start() {
        if (this.sharedThreat != null) {
            this.wolf.setTarget(this.sharedThreat);
        }
    }

    @Override
    public void stop() {
        this.sharedThreat = null;
    }

    private float healthRatio(LivingEntity entity) {
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
    }
}
