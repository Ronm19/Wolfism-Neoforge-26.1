package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.custom.BloodWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Blood pups never fight. When their pack remembers a threat, they retreat
 * toward safety or physically away from the attacker.
 */
public final class BloodPupRetreatGoal extends Goal {
    private static final double SPEED = 1.25D;
    private static final double SAFE_DISTANCE_SQR = 12.0D * 12.0D;

    private final BloodWolf wolf;
    private LivingEntity threat;

    public BloodPupRetreatGoal(BloodWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.wolf.isBaby()) {
            return false;
        }

        this.threat = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.BLOOD_PACK_THREAT.get())
                .filter(LivingEntity::isAlive)
                .orElse(null);

        return this.threat != null
                && this.wolf.distanceToSqr(this.threat) <= SAFE_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.wolf.isBaby()
                && this.threat != null
                && this.threat.isAlive()
                && this.wolf.distanceToSqr(this.threat) <= SAFE_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.wolf.setTarget(null);
        this.chooseEscapePath();
    }

    @Override
    public void tick() {
        this.wolf.setTarget(null);

        if (this.threat == null) {
            return;
        }

        this.wolf.getLookControl().setLookAt(this.threat, 30.0F, 30.0F);

        if (this.wolf.tickCount % 8 == 0 || this.wolf.getNavigation().isDone()) {
            this.chooseEscapePath();
        }
    }

    @Override
    public void stop() {
        this.threat = null;
        this.wolf.setTarget(null);
        this.wolf.getNavigation().stop();
    }

    private void chooseEscapePath() {
        if (this.threat == null) {
            return;
        }

        BloodWolf adult = this.wolf.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_BLOOD_ADULT_PACKMATE.get())
                .orElse(null);

        LivingEntity owner = this.wolf.getOwner();
        LivingEntity anchor = adult != null && adult.isAlive() ? adult : owner;

        if (anchor != null
                && anchor.isAlive()
                && anchor.distanceToSqr(this.threat) > this.wolf.distanceToSqr(this.threat)) {
            this.wolf.getNavigation().moveTo(anchor, SPEED);
            return;
        }

        Vec3 away = this.wolf.position().subtract(this.threat.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);

        if (horizontal.lengthSqr() <= 1.0E-6D) {
            double angle = this.wolf.getRandom().nextDouble() * Math.PI * 2.0D;
            horizontal = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            horizontal = horizontal.normalize();
        }

        Vec3 destination = this.wolf.position().add(horizontal.scale(8.0D));
        this.wolf.getNavigation().moveTo(
                destination.x,
                destination.y,
                destination.z,
                SPEED);
    }
}
