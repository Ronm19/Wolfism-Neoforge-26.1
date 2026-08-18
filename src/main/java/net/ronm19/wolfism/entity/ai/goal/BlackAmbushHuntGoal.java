package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Nocturnal stalking and flanking before a Black Wolf commits to melee.
 *
 * <p>The local leader originates the hunt. Other adults receive the same prey
 * through pack memory and approach from alternating side offsets, so the pack
 * does not simply run as one straight-line blob.</p>
 */
public final class BlackAmbushHuntGoal extends Goal {
    private static final double COMMIT_DISTANCE_SQR = 6.5D * 6.5D;
    private static final double MAX_STALK_DISTANCE_SQR = 36.0D * 36.0D;
    private static final double STALK_SPEED = 1.08D;
    private static final double FAR_STALK_SPEED = 1.18D;
    private static final double FLANK_OFFSET = 3.5D;

    private final BlackWolf wolf;
    private LivingEntity prey;
    private int repathDelay;

    public BlackAmbushHuntGoal(BlackWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || !this.wolf.isNightActive()
                || this.wolf.isHuntCoolingDown()
                || this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.BLACK_PACK_THREAT.get())) {
            return false;
        }

        Optional<LivingEntity> sharedHunt = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get());
        if (sharedHunt.isPresent() && isEligible(sharedHunt.get())) {
            this.prey = sharedHunt.get();
            return true;
        }

        Optional<BlackWolf> leader = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_PACK_LEADER.get());
        if (leader.isPresent() && leader.get() != this.wolf) {
            return false;
        }

        Optional<LivingEntity> nearestPrey = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_BLACK_PREY.get());
        if (nearestPrey.isEmpty() || !isEligible(nearestPrey.get())) {
            return false;
        }

        this.prey = nearestPrey.get();
        return true;
    }

    private boolean isEligible(LivingEntity target) {
        return target.isAlive()
                && BlackWolf.isPreferredPrey(target)
                && this.wolf.isValidBlackCombatTarget(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.prey != null
                && this.prey.isAlive()
                && this.wolf.getTarget() == null
                && !this.wolf.isTame()
                && !this.wolf.isBaby()
                && this.wolf.isNightActive()
                && !this.wolf.isHuntCoolingDown()
                && !this.wolf.getBrain().hasMemoryValue(ModMemoryModuleTypes.BLACK_PACK_THREAT.get())
                && this.wolf.distanceToSqr(this.prey) <= MAX_STALK_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
        if (this.prey != null) {
            this.wolf.getBrain().setMemory(ModMemoryModuleTypes.BLACK_HUNT_TARGET.get(), this.prey);
            this.wolf.primeAmbush(this.prey);
        }
    }

    @Override
    public void tick() {
        if (this.prey == null || !this.prey.isAlive()) {
            return;
        }

        double distance = this.wolf.distanceToSqr(this.prey);
        if (distance <= COMMIT_DISTANCE_SQR) {
            this.wolf.primeAmbush(this.prey);
            this.wolf.setTarget(this.prey);
            this.wolf.getNavigation().stop();
            return;
        }

        if (--this.repathDelay > 0) {
            return;
        }
        this.repathDelay = 8;

        BlackWolf leader = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.BLACK_PACK_LEADER.get())
                .orElse(null);

        double targetX = this.prey.getX();
        double targetZ = this.prey.getZ();

        if (leader != null && leader != this.wolf) {
            double dx = this.prey.getX() - this.wolf.getX();
            double dz = this.prey.getZ() - this.wolf.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0.001D) {
                double side = (this.wolf.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0D : -1.0D;
                targetX += (-dz / length) * FLANK_OFFSET * side;
                targetZ += (dx / length) * FLANK_OFFSET * side;
            }
        }

        double speed = distance > 16.0D * 16.0D ? FAR_STALK_SPEED : STALK_SPEED;
        this.wolf.getNavigation().moveTo(targetX, this.prey.getY(), targetZ, speed);
    }

    @Override
    public void stop() {
        this.prey = null;
        if (this.wolf.getTarget() == null) {
            this.wolf.getNavigation().stop();
        }
    }
}
