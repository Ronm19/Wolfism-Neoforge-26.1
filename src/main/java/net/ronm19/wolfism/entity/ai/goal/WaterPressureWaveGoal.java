package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/**
 * Defensive underwater crowd-control pulse.
 *
 * <p>Pressure Wave is intentionally not an ordinary single-target attack. It
 * activates when the Water Wolf is being crowded, when nearby family needs
 * defensive space, or when a drowning rescue target is threatened. The pulse
 * itself is resolved by {@link WaterWolf#performPressureWave()} so targeting,
 * family safety, particles and cooldown logic remain centralized on the entity.</p>
 */
public final class WaterPressureWaveGoal extends Goal {
    private final WaterWolf wolf;

    public WaterPressureWaveGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.wolf.canStartPressureWave();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        this.wolf.performPressureWave();
    }
}
