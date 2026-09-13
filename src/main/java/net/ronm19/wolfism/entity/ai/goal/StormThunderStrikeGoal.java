package net.ronm19.wolfism.entity.ai.goal;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.StormWolf;

/** Storm Wolf's full-charge ranged burst attack. */
public final class StormThunderStrikeGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 5.0D * 5.0D;
    public static final double MAX_START_DISTANCE_SQR = 16.0D * 16.0D;
    private static final int PREPARE_TICKS = 7;

    private final StormWolf wolf;
    private LivingEntity target;
    private int prepareTicks;

    public StormThunderStrikeGoal(StormWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartThunderStrikeAgainst(candidate)) {
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
                && this.wolf.isValidStormCombatTarget(this.target);
    }

    @Override
    public void start() {
        this.prepareTicks = PREPARE_TICKS;
        this.wolf.getNavigation().stop();
        this.wolf.reserveThunderStrikeTarget(this.target);
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }

        this.wolf.getNavigation().stop();
        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 35.0F);

        if (this.wolf.level() instanceof ServerLevel level && this.prepareTicks % 2 == 1) {
            WolfVfx.sendParticles(level,
                    ParticleTypes.ELECTRIC_SPARK,
                    this.wolf.getX(),
                    this.wolf.getY() + 0.38D,
                    this.wolf.getZ(),
                    5,
                    0.26D,
                    0.20D,
                    0.26D,
                    0.025D);
        }

        --this.prepareTicks;
        if (this.prepareTicks <= 0) {
            this.wolf.performThunderStrike(this.target);
        }
    }

    @Override
    public void stop() {
        this.wolf.clearThunderStrikeReservation();
        this.target = null;
        this.prepareTicks = 0;
    }
}
