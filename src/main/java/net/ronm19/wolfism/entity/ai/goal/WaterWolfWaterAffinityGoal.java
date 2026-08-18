package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/**
 * Habitat preference for Water Wolf.
 *
 * <p>Wild wolves on land actively return to nearby water. An idle Water Wolf
 * already in water instead chooses another submerged point and swims through the
 * water volume, preventing vanilla land-wolf wandering from immediately pulling
 * it back onto shore. Tamed wolves still obey higher-priority owner/combat goals;
 * they do not abandon an owner on land merely to find a pond.</p>
 */
public final class WaterWolfWaterAffinityGoal extends Goal {
    private static final int LAND_WATER_SEARCH_RADIUS = 14;
    private static final int LAND_WATER_VERTICAL_RADIUS = 4;
    private static final int WATER_WANDER_HORIZONTAL_RADIUS = 9;
    private static final int WATER_WANDER_VERTICAL_RADIUS = 4;
    private static final int WATER_WANDER_ATTEMPTS = 18;

    private static final double LAND_APPROACH_SPEED = 1.08D;
    private static final double WATER_SWIM_SPEED = 1.18D;
    private static final double REACHED_DISTANCE_SQR = 1.75D * 1.75D;

    private final WaterWolf wolf;
    private BlockPos wantedPos;
    private int repathDelay;
    private int lifeTicks;

    public WaterWolfWaterAffinityGoal(WaterWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isOrderedToSit()
                || this.wolf.isInSittingPose()
                || this.wolf.getTarget() != null
                || this.wolf.hasFamilyDefenseEmergency()) {
            return false;
        }

        if (this.wolf.isInWater()) {
            this.wantedPos = this.findRandomWaterPosition();
            return this.wantedPos != null;
        }

        // Wild Water Wolves are habitat-faithful. Tamed wolves remain free to
        // stay with their owner when the owner chooses to travel inland.
        if (this.wolf.isTame()) {
            return false;
        }

        this.wantedPos = this.findNearestWaterPosition();
        return this.wantedPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.wantedPos == null
                || this.wolf.isOrderedToSit()
                || this.wolf.getTarget() != null
                || this.wolf.hasFamilyDefenseEmergency()
                || this.lifeTicks >= 160) {
            return false;
        }

        if (!this.wolf.level().getFluidState(this.wantedPos).is(FluidTags.WATER)) {
            return false;
        }

        // A tamed wolf that has left the water should immediately return to
        // normal owner-directed land behavior instead of seeking water alone.
        if (this.wolf.isTame() && !this.wolf.isInWater()) {
            return false;
        }

        return this.wolf.distanceToSqr(
                this.wantedPos.getX() + 0.5D,
                this.wantedPos.getY() + 0.5D,
                this.wantedPos.getZ() + 0.5D) > REACHED_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathDelay = 0;
        this.lifeTicks = 0;
    }

    @Override
    public void tick() {
        ++this.lifeTicks;
        if (this.wantedPos == null || --this.repathDelay > 0) {
            return;
        }

        this.repathDelay = this.wolf.isInWater() ? 5 : 10;
        double x = this.wantedPos.getX() + 0.5D;
        double y = this.wantedPos.getY() + 0.5D;
        double z = this.wantedPos.getZ() + 0.5D;

        this.wolf.moveToWaterAware(
                x,
                y,
                z,
                this.wolf.isInWater() ? WATER_SWIM_SPEED : LAND_APPROACH_SPEED);
        this.wolf.getLookControl().setLookAt(x, y, z, 20.0F, 30.0F);
    }

    @Override
    public void stop() {
        this.wantedPos = null;
        this.lifeTicks = 0;
        this.wolf.stopWaterAwareMovement();
    }

    private BlockPos findRandomWaterPosition() {
        Level level = this.wolf.level();
        BlockPos origin = this.wolf.blockPosition();

        for (int attempt = 0; attempt < WATER_WANDER_ATTEMPTS; ++attempt) {
            int dx = this.wolf.getRandom().nextInt(WATER_WANDER_HORIZONTAL_RADIUS * 2 + 1)
                    - WATER_WANDER_HORIZONTAL_RADIUS;
            int dy = this.wolf.getRandom().nextInt(WATER_WANDER_VERTICAL_RADIUS * 2 + 1)
                    - WATER_WANDER_VERTICAL_RADIUS;
            int dz = this.wolf.getRandom().nextInt(WATER_WANDER_HORIZONTAL_RADIUS * 2 + 1)
                    - WATER_WANDER_HORIZONTAL_RADIUS;

            BlockPos candidate = origin.offset(dx, dy, dz);
            if (level.getFluidState(candidate).is(FluidTags.WATER)) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos findNearestWaterPosition() {
        Level level = this.wolf.level();
        BlockPos origin = this.wolf.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -LAND_WATER_SEARCH_RADIUS; dx <= LAND_WATER_SEARCH_RADIUS; ++dx) {
            for (int dz = -LAND_WATER_SEARCH_RADIUS; dz <= LAND_WATER_SEARCH_RADIUS; ++dz) {
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                    continue;
                }

                for (int dy = -LAND_WATER_VERTICAL_RADIUS; dy <= LAND_WATER_VERTICAL_RADIUS; ++dy) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.getFluidState(candidate).is(FluidTags.WATER)) {
                        continue;
                    }

                    double distance = origin.distSqr(candidate);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate;
                    }
                }
            }
        }
        return nearest;
    }
}
