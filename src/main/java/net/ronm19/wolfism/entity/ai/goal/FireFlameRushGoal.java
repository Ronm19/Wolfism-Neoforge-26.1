package net.ronm19.wolfism.entity.ai.goal;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.FireWolf;

/**
 * Fire Wolf's signature gap-closing attack.
 *
 * <p>A short visible wind-up precedes the rush. The rush itself is handled by
 * the entity so damage, cooldown, steering and particles remain one coherent
 * state rather than being split across several competing goals.</p>
 */
public final class FireFlameRushGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 5.0D * 5.0D;
    public static final double MAX_START_DISTANCE_SQR = 14.0D * 14.0D;

    private static final int PREPARE_TICKS = 6;

    private final FireWolf wolf;
    private LivingEntity target;
    private int prepareTicks;
    private boolean committed;

    public FireFlameRushGoal(FireWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartFlameRushAgainst(candidate)) {
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
                && (!this.committed || this.wolf.isFlameRushing());
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

        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 35.0F);

        if (!this.committed) {
            this.wolf.getNavigation().stop();

            if (this.wolf.level() instanceof ServerLevel level && this.prepareTicks % 2 == 0) {
                WolfVfx.sendParticles(level,
                        ParticleTypes.FLAME,
                        this.wolf.getX(),
                        this.wolf.getY() + 0.20D,
                        this.wolf.getZ(),
                        3,
                        0.25D,
                        0.08D,
                        0.25D,
                        0.01D);
            }

            --this.prepareTicks;
            if (this.prepareTicks <= 0) {
                this.committed = this.wolf.beginFlameRush(this.target);
                if (!this.committed) {
                    this.target = null;
                }
            }
        }
    }

    @Override
    public void stop() {
        if (this.committed && this.wolf.isFlameRushing()) {
            // If a higher-priority movement goal (most importantly Creeper
            // retreat) preempts this goal, do not leave a detached rush running.
            this.wolf.endFlameRush();
        }
        this.target = null;
        this.prepareTicks = 0;
        this.committed = false;
    }
}
