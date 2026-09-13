package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.ronm19.wolfism.entity.ai.support.SupportPositioning;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Moves New Year's Wolf close enough to reset and regroup a needy family member. */
public final class NewYearsWolfSupportGoal extends Goal {
    private final NewYearsWolf wolf;
    private LivingEntity patient;
    private int repathTicks;
    private int noPathTicks;
    private long nextAttemptTick;
    private Vec3 lastPosition;

    public NewYearsWolfSupportGoal(NewYearsWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (wolf.level().getGameTime() < nextAttemptTick) return false;
        if (!wolf.canProvideNewYearsSupport() || wolf.hasWolfStaffCommand()) {
            return false;
        }

        LivingEntity owner = wolf.getOwner();
        if (owner == null || wolf.distanceToSqr(owner) > 18.0D * 18.0D) {
            return false;
        }

        LivingEntity target = wolf.getTarget();
        if (target != null && wolf.distanceToSqr(target) < 5.0D * 5.0D) {
            return false;
        }

        patient = wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_FAMILY_IN_NEED.get())
                .filter(wolf::isNewYearsRecipient)
                .orElse(null);

        return patient != null
                && patient != wolf
                && wolf.distanceToSqr(patient) > 7.0D * 7.0D
                && patient.distanceToSqr(owner) <= 20.0D * 20.0D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = wolf.getOwner();
        return patient != null
                && owner != null
                && wolf.canProvideNewYearsSupport()
                && !wolf.hasWolfStaffCommand()
                && wolf.isNewYearsRecipient(patient)
                && wolf.newYearsNeedScore(patient) > 1.0D
                && wolf.distanceToSqr(patient) > 5.0D * 5.0D
                && wolf.distanceToSqr(patient) <= 22.0D * 22.0D
                && patient.distanceToSqr(owner) <= 20.0D * 20.0D
                && noPathTicks < 60
                && (wolf.getTarget() == null
                        || wolf.distanceToSqr(wolf.getTarget()) >= 25.0D);
    }

    @Override
    public void start() {
        repathTicks = 0;
        noPathTicks = 0;
        lastPosition = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (patient == null) {
            return;
        }

        wolf.getLookControl().setLookAt(patient, 25.0F, 25.0F);
        if (--repathTicks <= 0) {
            repathTicks = 10;
            boolean stalled = lastPosition != null && wolf.position().distanceToSqr(lastPosition) < 0.04D;
            lastPosition = wolf.position();
            if (SupportPositioning.moveToPatient(wolf, patient, 1.12D)) {
                noPathTicks = stalled ? noPathTicks + 10 : 0;
            } else {
                noPathTicks += 10;
            }
        }
    }

    @Override
    public void stop() {
        if (noPathTicks >= 60) nextAttemptTick = wolf.level().getGameTime() + 100;
        wolf.getNavigation().stop();
        patient = null;
    }
}
