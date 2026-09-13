package net.ronm19.wolfism.entity.ai.goal;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.FrostWolf;

/** Frost Wolf's ranged battlefield-control ability. */
public final class FrostIceSpikeGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_START_DISTANCE_SQR = 16.0D * 16.0D;
    private static final int PREPARE_TICKS = 8;

    private final FrostWolf wolf;
    private LivingEntity target;
    private int prepareTicks;

    public FrostIceSpikeGoal(FrostWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartIceSpikeAgainst(candidate)) {
            return false;
        }
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.target.isAlive()
                && this.prepareTicks > 0
                && (!this.wolf.hasFamilyDefenseEmergency()
                || this.wolf.getFamilyDefenseTarget() == this.target)
                && this.wolf.isValidFrostCombatTarget(this.target);
    }

    @Override
    public void start() {
        this.prepareTicks = PREPARE_TICKS;
        this.wolf.getNavigation().stop();
        this.wolf.reserveIceSpikeTarget(this.target);
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }

        this.wolf.getNavigation().stop();
        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 35.0F);

        if (this.wolf.level() instanceof ServerLevel level && this.prepareTicks % 2 == 0) {
            WolfVfx.sendParticles(level,
                    ParticleTypes.SNOWFLAKE,
                    this.target.getX(),
                    this.target.getY() + 0.10D,
                    this.target.getZ(),
                    5,
                    0.28D,
                    0.06D,
                    0.28D,
                    0.01D);
        }

        --this.prepareTicks;
        if (this.prepareTicks <= 0) {
            this.wolf.performIceSpike(this.target);
        }
    }

    @Override
    public void stop() {
        this.wolf.clearIceSpikeReservation();
        this.target = null;
        this.prepareTicks = 0;
    }
}
