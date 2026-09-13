package net.ronm19.wolfism.entity.ai.goal;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SolarWolf;

/** Piercing ranged sunlight beam with a short deliberate aim tell. */
public final class SolarSunbeamGoal extends Goal {
    public static final double MIN_START_DISTANCE_SQR = 5.0D * 5.0D;
    public static final double MAX_START_DISTANCE_SQR = 18.0D * 18.0D;
    private static final int AIM_TICKS = 7;

    private final SolarWolf wolf;
    private LivingEntity target;
    private int aimTicks;

    public SolarSunbeamGoal(SolarWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.wolf.getTarget();
        if (candidate == null || !this.wolf.canStartSunbeamAgainst(candidate)) return false;
        this.target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.aimTicks > 0
                && this.wolf.isValidSolarCombatTarget(this.target);
    }

    @Override
    public void start() {
        this.aimTicks = AIM_TICKS;
        this.wolf.getNavigation().stop();
        this.wolf.reserveSunbeamTarget(this.target);
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        this.wolf.getNavigation().stop();
        this.wolf.getLookControl().setLookAt(this.target, 45.0F, 40.0F);
        if (this.wolf.level() instanceof ServerLevel level && this.aimTicks % 2 == 1) {
            WolfVfx.sendParticles(level, ParticleTypes.END_ROD, this.wolf.getX(), this.wolf.getY() + 0.62D, this.wolf.getZ(), 3, 0.15D, 0.16D, 0.15D, 0.004D);
        }
        if (--this.aimTicks <= 0) this.wolf.performSunbeam(this.target);
    }

    @Override
    public void stop() {
        this.wolf.clearSunbeamReservation();
        this.target = null;
        this.aimTicks = 0;
    }
}
