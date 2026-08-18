package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Black Wolf pups seek an adult/owner when the pack detects danger. */
public final class BlackPupRetreatGoal extends Goal {
    private static final double SPEED = 1.25D;
    private static final double SAFE_DISTANCE_SQR = 12.0D * 12.0D;

    private final BlackWolf pup;
    private LivingEntity threat;
    private LivingEntity safeAnchor;
    private int repathDelay;

    public BlackPupRetreatGoal(BlackWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby() || this.pup.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> rememberedThreat = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_PACK_THREAT.get());
        if (rememberedThreat.isEmpty() || !rememberedThreat.get().isAlive()) {
            return false;
        }

        this.threat = rememberedThreat.get();
        this.safeAnchor = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_BLACK_ADULT_PACKMATE.get())
                .map(LivingEntity.class::cast)
                .orElse(this.pup.getOwner());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.pup.isBaby()
                && this.threat != null
                && this.threat.isAlive()
                && this.pup.distanceToSqr(this.threat) < SAFE_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.pup.setTarget(null);
        this.repathDelay = 0;
    }

    @Override
    public void tick() {
        this.pup.setTarget(null);
        if (--this.repathDelay > 0 || this.threat == null) {
            return;
        }
        this.repathDelay = 5;

        if (this.safeAnchor != null && this.safeAnchor.isAlive()) {
            this.pup.getNavigation().moveTo(this.safeAnchor, SPEED);
            return;
        }

        double dx = this.pup.getX() - this.threat.getX();
        double dz = this.pup.getZ() - this.threat.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }

        double fleeX = this.pup.getX() + dx / length * 8.0D;
        double fleeZ = this.pup.getZ() + dz / length * 8.0D;
        this.pup.getNavigation().moveTo(fleeX, this.pup.getY(), fleeZ, SPEED);
    }

    @Override
    public void stop() {
        this.threat = null;
        this.safeAnchor = null;
    }
}
