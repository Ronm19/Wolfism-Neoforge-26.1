package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.MagmaWolf;

/**
 * Slow, obvious wind-up for Magma Wolf's heavyweight charge.
 * Fire Wolf rushes; Magma commits and then hits like a wall.
 */
public final class MagmaChargeGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_START_DISTANCE_SQR = 12.0D * 12.0D;

    private static final int PREPARE_TICKS = 10;

    private final MagmaWolf wolf;
    private LivingEntity target;
    private int prepareTicks;
    private boolean committed;

    public MagmaChargeGoal(MagmaWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartMagmaChargeAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        return (!this.wolf.hasFamilyDefenseEmergency()
                || this.wolf.getFamilyDefenseTarget() == this.target)
                && (!this.committed || this.wolf.isMagmaCharging());
    }

    @Override
    public void start() {
        this.prepareTicks = PREPARE_TICKS;
        this.committed = false;
        this.wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 30.0F);
        if (this.committed) return;

        this.wolf.getNavigation().stop();

        if (this.wolf.level() instanceof ServerLevel level && this.prepareTicks % 2 == 0) {
            level.sendParticles(
                    ParticleTypes.LAVA,
                    this.wolf.getX(),
                    this.wolf.getY() + 0.22D,
                    this.wolf.getZ(),
                    4,
                    0.32D, 0.10D, 0.32D,
                    0.018D);
        }

        --this.prepareTicks;
        if (this.prepareTicks <= 0) {
            this.committed = this.wolf.beginMagmaCharge(this.target);
            if (!this.committed) this.target = null;
        }
    }

    @Override
    public void stop() {
        if (this.committed && this.wolf.isMagmaCharging()) {
            this.wolf.endMagmaCharge();
        }

        this.target = null;
        this.prepareTicks = 0;
        this.committed = false;
    }
}
