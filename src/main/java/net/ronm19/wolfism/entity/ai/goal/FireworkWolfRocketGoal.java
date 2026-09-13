package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.FireworkWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Brain-fed mobility goal that chooses charge or vertical rocket leap. */
public final class FireworkWolfRocketGoal extends Goal {
    private final FireworkWolf wolf;
    private LivingEntity target;

    public FireworkWolfRocketGoal(FireworkWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!wolf.canUseFireworkCombat() || wolf.isFireworkMovementActive()) {
            return false;
        }
        target = wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.FIREWORK_PRIORITY_THREAT.get())
                .filter(wolf::isFireworkThreat)
                .orElse(null);
        if (target == null) return false;
        return wolf.canStartFireworkCharge(target)
                || wolf.canStartRocketLeap(target);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (target == null) return;
        wolf.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!wolf.tryStartFireworkCharge(target)) {
            wolf.tryStartRocketLeap(target);
        }
        target = null;
    }
}
