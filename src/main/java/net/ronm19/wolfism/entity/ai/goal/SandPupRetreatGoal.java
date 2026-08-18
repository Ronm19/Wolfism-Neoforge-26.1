package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Sand pups treat pack danger as a reason to seek cover beside an adult/owner. */
public final class SandPupRetreatGoal extends Goal {
    private static final double SPEED = 1.30D;
    private static final double SAFE_DISTANCE_SQR = 13.0D * 13.0D;

    private final SandWolf pup;
    private LivingEntity threat;
    private LivingEntity safeAnchor;
    private int repathDelay;

    public SandPupRetreatGoal(SandWolf pup) {
        this.pup = pup;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pup.isBaby() || this.pup.isOrderedToSit()) {
            return false;
        }

        Optional<LivingEntity> rememberedThreat = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.SAND_PACK_THREAT.get());
        if (rememberedThreat.isEmpty() || !rememberedThreat.get().isAlive()) {
            return false;
        }

        this.threat = rememberedThreat.get();
        this.safeAnchor = this.pup.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_SAND_ADULT_PACKMATE.get())
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

        double fleeX = this.pup.getX() + dx / length * 9.0D;
        double fleeZ = this.pup.getZ() + dz / length * 9.0D;
        this.pup.getNavigation().moveTo(fleeX, this.pup.getY(), fleeZ, SPEED);
    }

    @Override
    public void stop() {
        this.threat = null;
        this.safeAnchor = null;
    }
}
