package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.ronm19.wolfism.entity.ai.support.SupportPositioning;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.ChristmasWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Brings Christmas's support radius to an injured family member.
 * Never pursues enemies or pulls a Staff-commanded wolf away from its order.
 */
public final class ChristmasWolfSupportGoal extends Goal {
    private final ChristmasWolf wolf;
    private LivingEntity patient;
    private int repathTicks;
    private int noPathTicks;
    private long nextAttemptTick;
    private Vec3 lastPosition;

    public ChristmasWolfSupportGoal(ChristmasWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (wolf.level().getGameTime() < nextAttemptTick) return false;
        if (!wolf.canProvideChristmasSupport() || wolf.hasWolfStaffCommand()) return false;
        LivingEntity owner = wolf.getOwner();
        if (owner == null || wolf.distanceToSqr(owner) > 16.0D * 16.0D) return false;
        LivingEntity target = wolf.getTarget();
        if (target != null && wolf.distanceToSqr(target) < 5.0D * 5.0D) return false;
        patient = wolf.getBrain().getMemory(ModMemoryModuleTypes.CHRISTMAS_FAMILY_IN_NEED.get())
                .filter(wolf::isSupportRecipient).orElse(null);
        return patient != null && patient != wolf
                && wolf.distanceToSqr(patient) > 7.0D * 7.0D
                && patient.distanceToSqr(owner) <= 18.0D * 18.0D;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && wolf.canProvideChristmasSupport()
                && !wolf.hasWolfStaffCommand() && wolf.isSupportRecipient(patient)
                && wolf.christmasNeedScore(patient) > 1.0D
                && wolf.distanceToSqr(patient) > 5.0D * 5.0D
                && wolf.distanceToSqr(patient) <= 20.0D * 20.0D
                && wolf.getOwner() != null
                && patient.distanceToSqr(wolf.getOwner()) <= 18.0D * 18.0D
                && noPathTicks < 60
                && (wolf.getTarget() == null || wolf.distanceToSqr(wolf.getTarget()) >= 25.0D);
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
        if (patient == null) return;
        wolf.getLookControl().setLookAt(patient, 25.0F, 25.0F);
        if (--repathTicks <= 0) {
            repathTicks = 10;
            boolean stalled = lastPosition != null && wolf.position().distanceToSqr(lastPosition) < 0.04D;
            lastPosition = wolf.position();
            if (!SupportPositioning.moveToPatient(wolf, patient, 1.10D)) noPathTicks += 10;
            else noPathTicks = stalled ? noPathTicks + 10 : 0;
        }
    }

    @Override
    public void stop() {
        if (noPathTicks >= 60) nextAttemptTick = wolf.level().getGameTime() + 100;
        wolf.getNavigation().stop();
        patient = null;
    }
}
