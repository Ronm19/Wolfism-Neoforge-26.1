package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.LunarWolf;

/** Deliberate moonlight beam: a precise ranged pressure tool, not Solar fire. */
public final class LunarBeamGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 5.0D * 5.0D;
    public static final double MAX_START_DISTANCE_SQR = 20.0D * 20.0D;
    private static final int AIM_TICKS = 8;

    private final LunarWolf wolf;
    private LivingEntity target;
    private int aimTicks;

    public LunarBeamGoal(LunarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartLunarBeamAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.aimTicks > 0
                && this.wolf.isValidLunarCombatTarget(this.target);
    }

    @Override
    public void start() {
        this.aimTicks = AIM_TICKS;
        this.wolf.getNavigation().stop();
        this.wolf.reserveLunarBeamTarget(this.target);
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        this.wolf.getNavigation().stop();
        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 40.0F);
        if (this.wolf.level() instanceof ServerLevel level && this.aimTicks % 2 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, this.wolf.getX(), this.wolf.getY() + 0.62D, this.wolf.getZ(), 4, 0.12D, 0.14D, 0.12D, 0.015D);
            level.sendParticles(ParticleTypes.END_ROD, this.wolf.getX(), this.wolf.getY() + 0.62D, this.wolf.getZ(), 2, 0.10D, 0.12D, 0.10D, 0.002D);
        }
        if (--this.aimTicks <= 0) this.wolf.performLunarBeam(this.target);
    }

    @Override
    public void stop() {
        this.wolf.clearLunarBeamReservation();
        this.target = null;
        this.aimTicks = 0;
    }
}
