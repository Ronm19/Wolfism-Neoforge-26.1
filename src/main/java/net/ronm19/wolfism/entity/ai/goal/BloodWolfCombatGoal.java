package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.ronm19.wolfism.entity.custom.BloodWolf;

/**
 * Physical melee motor for Blood Wolf.
 *
 * <p>The entity class decides WHO Blood wants dead and how intense the current
 * berserker state is. This goal turns that intent into relentless movement and
 * attack pressure.</p>
 *
 * <p>Creepers are intentionally rejected here. Wolfism's shared validated
 * Creeper pack-attack / fuse-retreat system remains authoritative.</p>
 */
public final class BloodWolfCombatGoal extends Goal {
    private final BloodWolf wolf;

    private int attackCooldown;
    private int repathCooldown;
    private int pressureLungeCooldown;
    private int stuckTicks;
    private double lastX;
    private double lastY;
    private double lastZ;

    public BloodWolfCombatGoal(BloodWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.wolf.getTarget();
        return this.wolf.canUseBloodCombatSystems()
                && target != null
                && !(target instanceof Creeper)
                && this.wolf.isValidBloodCombatTarget(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.repathCooldown = 0;
        this.pressureLungeCooldown = 0;
        this.stuckTicks = 0;
        this.rememberPosition();
    }

    @Override
    public void stop() {
        this.wolf.getNavigation().stop();
        this.stuckTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.wolf.getTarget();
        if (target == null
                || target instanceof Creeper
                || !this.wolf.isValidBloodCombatTarget(target)) {
            return;
        }

        if (this.attackCooldown > 0) {
            --this.attackCooldown;
        }
        if (this.repathCooldown > 0) {
            --this.repathCooldown;
        }
        if (this.pressureLungeCooldown > 0) {
            --this.pressureLungeCooldown;
        }

        this.wolf.getLookControl().setLookAt(
                target,
                30.0F,
                30.0F);

        if (this.repathCooldown <= 0) {
            this.repathCooldown = this.wolf.getBloodRepathInterval();
            boolean started = this.wolf.getNavigation().moveTo(
                    target,
                    this.wolf.getBloodPursuitSpeedModifier(target));

            if (!started && this.wolf.distanceToSqr(target) > this.getAttackReachSqr(target)) {
                this.stuckTicks += 4;
            }
        }

        if (this.pressureLungeCooldown <= 0) {
            this.pressureLungeCooldown = this.wolf.isBloodBerserkerActive() ? 8 : 14;
            this.wolf.tryBloodPressureLunge(target);
        }

        this.checkStuck(target);

        if (this.wolf.distanceToSqr(target) <= this.getAttackReachSqr(target)
                && this.attackCooldown <= 0
                && this.wolf.getSensing().hasLineOfSight(target)
                && this.wolf.level() instanceof ServerLevel level) {
            this.attackCooldown = this.wolf.getBloodAttackInterval(target);
            this.wolf.doHurtTarget(target);
        }
    }

    private void checkStuck(LivingEntity target) {
        if (this.wolf.distanceToSqr(target) <= this.getAttackReachSqr(target)) {
            this.stuckTicks = 0;
            this.rememberPosition();
            return;
        }

        double dx = this.wolf.getX() - this.lastX;
        double dy = this.wolf.getY() - this.lastY;
        double dz = this.wolf.getZ() - this.lastZ;
        double movedSqr = dx * dx + dy * dy + dz * dz;

        if (movedSqr < 0.0025D && !this.wolf.getNavigation().isDone()) {
            ++this.stuckTicks;
        } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - 2);
        }

        this.rememberPosition();

        /*
         * Blood does not stare at an unreachable victim forever. Dropping the
         * target lets Blood Hunter immediately choose another executable enemy.
         * Family defense will simply restore its priority target if the emergency
         * is still valid.
         */
        if (this.stuckTicks >= 45) {
            this.wolf.getNavigation().stop();
            if (!this.wolf.hasFamilyDefenseEmergency()) {
                this.wolf.setTarget(null);
            }
            this.stuckTicks = 0;
            this.repathCooldown = 0;
        }
    }

    private void rememberPosition() {
        this.lastX = this.wolf.getX();
        this.lastY = this.wolf.getY();
        this.lastZ = this.wolf.getZ();
    }

    private double getAttackReachSqr(LivingEntity target) {
        double reach = this.wolf.getBbWidth() * 2.35D + target.getBbWidth();
        return reach * reach;
    }
}
