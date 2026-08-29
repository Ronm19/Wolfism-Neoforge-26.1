package net.ronm19.wolfism.entity.ai.goal;

import java.util.Comparator;
import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.wolfism.entity.custom.InfernalWolf;

/**
 * Emergency lava guide for Infernal Wolf.
 *
 * <p>When the owner or a nearby tamed wolf is actually stranded in lava,
 * Infernal stops casual guiding/combat movement, reaches the stranded family
 * member, then leads them toward the nearest solid shore. Infernal's normal
 * obsidian-path manager creates the physical route while this goal moves him.</p>
 */
public final class InfernalLavaRescueGoal extends Goal {
    private static final double FAMILY_SCAN_RADIUS = 32.0D;
    private static final double APPROACH_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double MAX_LEAD_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double SPEED = 1.18D;
    private static final int MAX_RESCUE_TICKS = 20 * 20;
    private static final int SAFE_SHORE_SEARCH_RADIUS = 24;

    private final InfernalWolf wolf;

    private LivingEntity rescueTarget;
    private BlockPos safeShore;
    private boolean reachedFamily;
    private int rescueTicks;
    private int repathTicks;

    public InfernalLavaRescueGoal(InfernalWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.isTame()
                || this.wolf.isBaby()
                || this.wolf.isOrderedToSit()
                || this.wolf.isInSittingPose()
                || !(this.wolf.level() instanceof ServerLevel level)) {
            return false;
        }

        this.rescueTarget = this.findStrandedFamily(level);
        if (this.rescueTarget == null) {
            return false;
        }

        this.safeShore = this.findSafeShore(
                level,
                this.rescueTarget.blockPosition());

        return this.safeShore != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.rescueTarget != null
                && this.rescueTarget.isAlive()
                && this.rescueTarget.isInLava()
                && this.safeShore != null
                && !this.wolf.isOrderedToSit()
                && this.rescueTicks < MAX_RESCUE_TICKS;
    }

    @Override
    public void start() {
        this.rescueTicks = 0;
        this.repathTicks = 0;
        this.reachedFamily = this.wolf.distanceToSqr(this.rescueTarget)
                <= APPROACH_DISTANCE_SQR;

        this.wolf.getNavigation().stop();
        this.moveForCurrentStage();
    }

    @Override
    public void tick() {
        ++this.rescueTicks;

        if (this.rescueTarget == null || this.safeShore == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(
                this.rescueTarget,
                35.0F,
                30.0F);

        if (!this.reachedFamily) {
            if (this.wolf.distanceToSqr(this.rescueTarget)
                    <= APPROACH_DISTANCE_SQR) {
                this.reachedFamily = true;
                this.wolf.getNavigation().stop();
                this.moveForCurrentStage();
                return;
            }
        } else if (this.rescueTarget.isInLava()
                && this.wolf.distanceToSqr(this.rescueTarget)
                > MAX_LEAD_DISTANCE_SQR) {
            /*
             * Don't sprint to shore and abandon the player in the middle of
             * the lake. Go back, let the owner/family catch up, then lead on.
             */
            this.reachedFamily = false;
            this.wolf.getNavigation().stop();
            this.moveForCurrentStage();
            return;
        }

        if (--this.repathTicks <= 0
                || this.wolf.getNavigation().isDone()) {
            this.repathTicks = 10;
            this.moveForCurrentStage();
        }
    }

    @Override
    public void stop() {
        this.rescueTarget = null;
        this.safeShore = null;
        this.reachedFamily = false;
        this.rescueTicks = 0;
        this.repathTicks = 0;
        this.wolf.getNavigation().stop();
    }

    private void moveForCurrentStage() {
        if (this.rescueTarget == null || this.safeShore == null) {
            return;
        }

        if (!this.reachedFamily) {
            this.wolf.getNavigation().moveTo(
                    this.rescueTarget,
                    SPEED);
            return;
        }

        this.wolf.getNavigation().moveTo(
                this.safeShore.getX() + 0.5D,
                this.safeShore.getY(),
                this.safeShore.getZ() + 0.5D,
                SPEED);
    }

    private LivingEntity findStrandedFamily(ServerLevel level) {
        LivingEntity owner = this.wolf.getOwner();

        if (owner != null
                && owner.isAlive()
                && owner.isInLava()
                && this.wolf.distanceToSqr(owner)
                        <= FAMILY_SCAN_RADIUS * FAMILY_SCAN_RADIUS) {
            return owner;
        }

        return level.getEntitiesOfClass(
                Wolf.class,
                this.wolf.getBoundingBox().inflate(FAMILY_SCAN_RADIUS),
                candidate -> candidate != this.wolf
                        && candidate.isAlive()
                        && candidate.isTame()
                        && candidate.isInLava())
                .stream()
                .min(Comparator.comparingDouble(this.wolf::distanceToSqr))
                .orElse(null);
    }

    private BlockPos findSafeShore(
            ServerLevel level,
            BlockPos center) {

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int radius = 2;
                radius <= SAFE_SHORE_SEARCH_RADIUS;
                ++radius) {

            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -4; dy <= 4; ++dy) {
                        BlockPos feet = center.offset(dx, dy, dz);

                        if (!this.isSafeShorePosition(level, feet)) {
                            continue;
                        }

                        double score = feet.distSqr(center);
                        if (score < bestScore) {
                            bestScore = score;
                            best = feet.immutable();
                        }
                    }
                }
            }

            /*
             * The first ring containing a valid landing is already the nearest
             * practical shore. Stop before scanning the entire 49x49 area.
             */
            if (best != null) {
                return best;
            }
        }

        return null;
    }

    private boolean isSafeShorePosition(
            ServerLevel level,
            BlockPos feet) {

        BlockPos groundPos = feet.below();
        BlockState ground = level.getBlockState(groundPos);

        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(feet)
                .getCollisionShape(level, feet)
                .isEmpty()
                || !level.getBlockState(feet.above())
                        .getCollisionShape(level, feet.above())
                        .isEmpty()) {
            return false;
        }

        /*
         * Don't call a one-block island inside the lava lake "safe shore".
         * Require a small lava-free neighborhood around the destination.
         */
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    if (level.getFluidState(
                            feet.offset(dx, dy, dz))
                            .is(FluidTags.LAVA)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
