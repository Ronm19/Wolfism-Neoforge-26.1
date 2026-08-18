package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family/pack danger outranks Water Wolf's ordinary aquatic patrol behavior. */
public final class WaterPackAssistGoal extends Goal {
    private final WaterWolf wolf;
    private LivingEntity sharedThreat;

    public WaterPackAssistGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> rememberedThreat = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_PACK_THREAT.get());
        if (rememberedThreat.isEmpty()) {
            return false;
        }

        LivingEntity threat = rememberedThreat.get();
        if (!this.wolf.isValidWaterCombatTarget(threat)) {
            return false;
        }

        LivingEntity current = this.wolf.getTarget();
        if (current != null && current.isAlive()) {
            return false;
        }

        this.sharedThreat = threat;
        return true;
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
