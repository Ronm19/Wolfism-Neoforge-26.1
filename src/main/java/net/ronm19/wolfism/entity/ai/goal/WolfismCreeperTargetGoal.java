package net.ronm19.wolfism.entity.ai.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/**
 * Shared Wolfism intelligence for deliberately engaging nearby Creepers.
 *
 * <p>Vanilla wolves intentionally avoid treating Creepers as normal combat
 * targets. Wolfism wolves are allowed to engage them, while the separate
 * {@link WolfismCreeperRetreatGoal} makes the fight cautious instead of
 * suicidal.</p>
 */
public final class WolfismCreeperTargetGoal extends Goal {
    private final AbstractWolfismWolf wolf;
    private Creeper creeper;

    public WolfismCreeperTargetGoal(AbstractWolfismWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isBaby() || this.wolf.isOrderedToSit()) {
            return false;
        }

        if (this.wolf.getTarget() != null && this.wolf.getTarget().isAlive()) {
            return false;
        }

        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        List<Creeper> nearby = serverLevel.getEntitiesOfClass(
                Creeper.class,
                this.wolf.getBoundingBox().inflate(
                        this.wolf.getWolfismPhysicalAggroAcquireRadius()),
                candidate -> candidate.isAlive()
                        && this.wolf.canAttack(candidate)
                        && !this.wolf.isAlliedTo(candidate));

        this.creeper = nearby.stream()
                .min(Comparator.comparingDouble(this.wolf::distanceToSqr))
                .orElse(null);
        return this.creeper != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.wolf.isBaby()
                && !this.wolf.isOrderedToSit()
                && this.creeper != null
                && this.creeper.isAlive()
                && this.wolf.getTarget() == this.creeper
                && this.wolf.isWithinWolfismPhysicalAggroReleaseRange(this.creeper);
    }

    @Override
    public void start() {
        if (this.creeper != null) {
            this.wolf.setTarget(this.creeper);
        }
    }

    @Override
    public void stop() {
        if (this.wolf.getTarget() == this.creeper) {
            this.wolf.setTarget(null);
        }
        this.creeper = null;
    }
}