package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.SculkWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Turns shared Sculk-pack danger memory into ordinary coordinated defense. */
public final class SculkPackAssistGoal extends Goal {
    private final SculkWolf wolf;
    private LivingEntity sharedThreat;

    public SculkPackAssistGoal(SculkWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> remembered = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.SCULK_PACK_THREAT.get());
        if (remembered.isEmpty()) {
            return false;
        }

        LivingEntity threat = remembered.get();
        if (threat instanceof Creeper || !this.wolf.isValidSculkCombatTarget(threat)) {
            return false;
        }

        LivingEntity current = this.wolf.getTarget();
        if (current instanceof Creeper) {
            return false;
        }

        this.sharedThreat = threat;
        return current == null || !current.isAlive() || current == threat;
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
}
