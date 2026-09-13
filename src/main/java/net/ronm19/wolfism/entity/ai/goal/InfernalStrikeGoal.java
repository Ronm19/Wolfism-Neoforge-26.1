package net.ronm19.wolfism.entity.ai.goal;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.InfernalWolf;

/**
 * Heavy breakthrough attack controller for Infernal Strike.
 */
public final class InfernalStrikeGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_START_DISTANCE_SQR = 15.0D * 15.0D;

    private static final int PREPARE_TICKS = 7;

    private final InfernalWolf wolf;
    private LivingEntity target;
    private int prepareTicks;
    private boolean committed;

    public InfernalStrikeGoal(InfernalWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();

        if (candidate == null
                || !this.wolf.canStartInfernalStrikeAgainst(candidate)) {
            return false;
        }

        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) {
            return false;
        }

        return (!this.wolf.hasFamilyDefenseEmergency()
                || this.wolf.getFamilyDefenseTarget() == this.target)
                && (!this.committed || this.wolf.isInfernalStriking());
    }

    @Override
    public void start() {
        this.prepareTicks = PREPARE_TICKS;
        this.committed = false;
        this.wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(
                this.target,
                50.0F,
                35.0F);

        if (this.committed) {
            return;
        }

        this.wolf.getNavigation().stop();

        if (this.wolf.level() instanceof ServerLevel level
                && this.prepareTicks % 2 == 0) {

            WolfVfx.sendParticles(level,
                    ParticleTypes.LAVA,
                    this.wolf.getX(),
                    this.wolf.getY() + 0.25D,
                    this.wolf.getZ(),
                    4,
                    0.28D, 0.10D, 0.28D,
                    0.015D);
        }

        --this.prepareTicks;

        if (this.prepareTicks <= 0) {
            this.committed =
                    this.wolf.beginInfernalStrike(this.target);

            if (!this.committed) {
                this.target = null;
            }
        }
    }

    @Override
    public void stop() {
        if (this.committed
                && this.wolf.isInfernalStriking()) {
            this.wolf.endInfernalStrike();
        }

        this.target = null;
        this.prepareTicks = 0;
        this.committed = false;
    }
}
