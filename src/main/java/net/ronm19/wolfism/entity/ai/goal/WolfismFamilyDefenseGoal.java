package net.ronm19.wolfism.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/**
 * Locks an adult tamed Wolfism wolf onto the current family attacker.
 *
 * <p>This is intentionally a TARGET-only goal. The family emergency replaces
 * ordinary prey/pack targeting immediately, while each species keeps using its
 * own best combat movement and abilities against the emergency target: Fire can
 * Flame Rush it, Frost can Ice Spike it, Storm can discharge into it, Dire can
 * charge it, and ordinary wolves can fall through to vanilla melee.</p>
 */
public final class WolfismFamilyDefenseGoal extends Goal {
    private final AbstractWolfismWolf wolf;

    public WolfismFamilyDefenseGoal(AbstractWolfismWolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity threat = this.wolf.getFamilyDefenseTarget();
        return !this.wolf.isBaby()
                && this.wolf.hasFamilyDefenseEmergency()
                && threat != null
                && threat.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity threat = this.wolf.getFamilyDefenseTarget();
        return !this.wolf.isBaby()
                && this.wolf.hasFamilyDefenseEmergency()
                && threat != null
                && threat.isAlive();
    }

    @Override
    public void start() {
        this.forceEmergencyTarget();
    }

    @Override
    public void tick() {
        this.forceEmergencyTarget();
    }

    private void forceEmergencyTarget() {
        LivingEntity threat = this.wolf.getFamilyDefenseTarget();
        if (threat != null && threat.isAlive() && this.wolf.getTarget() != threat) {
            this.wolf.setTarget(threat);
        }
    }
}
