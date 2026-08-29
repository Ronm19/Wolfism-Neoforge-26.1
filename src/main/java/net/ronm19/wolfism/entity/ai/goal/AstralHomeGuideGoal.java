package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.AstralWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Literal Astral home-leading behavior.
 *
 * <p>This is intentionally different from ordinary FollowOwnerGoal:</p>
 *
 * <ul>
 *     <li>Astral moves several blocks AHEAD of the owner toward home.</li>
 *     <li>If the owner falls too far behind, Astral waits/returns.</li>
 *     <li>As the owner follows, Astral advances the next short waypoint.</li>
 *     <li>Combat/family emergency/sitting immediately cancels guiding.</li>
 * </ul>
 *
 * <p>The goal only runs at night and only when Astral has a same-dimension
 * remembered home position from AstralWolfNightSensor.</p>
 */
public final class AstralHomeGuideGoal
        extends Goal {

    private static final double FINISH_DISTANCE_SQR =
            10.0D * 10.0D;

    private static final double OWNER_CATCHUP_DISTANCE_SQR =
            13.0D * 13.0D;

    private static final double LEAD_DISTANCE =
            7.5D;

    private static final double SPEED =
            1.12D;

    private static final int REPATH_INTERVAL_TICKS =
            10;

    private final AstralWolf wolf;

    private LivingEntity owner;
    private BlockPos home;
    private int repathTicks;

    public AstralHomeGuideGoal(
            AstralWolf wolf) {

        this.wolf = wolf;

        this.setFlags(
                EnumSet.of(
                        Flag.MOVE,
                        Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.refreshState();
    }

    @Override
    public boolean canContinueToUse() {
        return this.refreshState();
    }

    @Override
    public void start() {
        this.repathTicks = 0;
    }

    @Override
    public void tick() {
        if (this.owner == null
                || this.home == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(
                this.home.getX() + 0.5D,
                this.home.getY() + 0.5D,
                this.home.getZ() + 0.5D,
                40.0F,
                30.0F);

        if (--this.repathTicks > 0) {
            return;
        }

        this.repathTicks =
                REPATH_INTERVAL_TICKS;

        /*
         * Don't abandon the owner. If they stop following, Astral comes back
         * within companion range instead of vanishing toward the bed.
         */
        if (this.wolf.distanceToSqr(
                this.owner)
                > OWNER_CATCHUP_DISTANCE_SQR) {

            this.wolf.getNavigation()
                    .moveTo(
                            this.owner,
                            1.18D);

            return;
        }

        Vec3 ownerPos =
                this.owner.position();

        Vec3 toHome =
                Vec3.atCenterOf(this.home)
                        .subtract(ownerPos);

        Vec3 horizontal =
                new Vec3(
                        toHome.x,
                        0.0D,
                        toHome.z);

        if (horizontal.lengthSqr()
                <= 1.0E-6D) {
            this.wolf.getNavigation().stop();
            return;
        }

        double ownerHomeDistance =
                Math.sqrt(
                        horizontal.lengthSqr());

        double lead =
                Math.min(
                        LEAD_DISTANCE,
                        Math.max(
                                3.0D,
                                ownerHomeDistance - 2.0D));

        Vec3 direction =
                horizontal.normalize();

        Vec3 waypoint =
                ownerPos.add(
                        direction.scale(lead));

        /*
         * Local waypoint only. We intentionally do NOT pathfind all the way to
         * a bed hundreds/thousands of blocks away in one request. This keeps
         * the path cheap and creates the "Astral leads, owner follows, Astral
         * advances again" behavior.
         */
        this.wolf.getNavigation()
                .moveTo(
                        waypoint.x,
                        ownerPos.y,
                        waypoint.z,
                        SPEED);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.home = null;
        this.repathTicks = 0;
        this.wolf.getNavigation().stop();
    }

    private boolean refreshState() {
        if (this.wolf.isBaby()
                || !this.wolf.isTame()
                || this.wolf.isOrderedToSit()
                || this.wolf.level().isBrightOutside()
                || this.wolf.getTarget() != null
                || this.wolf.hasFamilyDefenseEmergency()) {
            return false;
        }

        this.owner =
                this.wolf.getOwner();

        if (this.owner == null
                || !this.owner.isAlive()) {
            return false;
        }

        /*
         * Immediate terrain danger uses the safe-ground system instead.
         * Don't try to lead "home" through an active lava/cliff emergency.
         */
        if (this.wolf.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.ASTRAL_SAFE_ROUTE_POS.get())
                .isPresent()) {
            return false;
        }

        this.home =
                this.wolf.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes.ASTRAL_HOME_POS.get())
                        .orElse(null);

        if (this.home == null) {
            return false;
        }

        return this.home.distToCenterSqr(
                this.owner.getX(),
                this.owner.getY(),
                this.owner.getZ())
                > FINISH_DISTANCE_SQR;
    }
}
