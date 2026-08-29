package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.ronm19.wolfism.entity.custom.OmenWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Omen Sense + Ominous Presence.
 *
 * <p>This sensor deliberately looks for danger before combat begins. It scores
 * visible nearby hostiles, threats already committing to family, and raiders
 * during an active raid. Major hostile concentration is also remembered so
 * Omen can distinguish "one zombie exists" from "something is very wrong".</p>
 */
public final class OmenWolfThreatSensor extends Sensor<OmenWolf> {
    private static final double MAJOR_ACTIVITY_RADIUS = 24.0D;

    public OmenWolfThreatSensor() {
        super(10);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            OmenWolf wolf) {

        LivingEntity owner = wolf.getOwner();

        Raid raidAtWolf =
                level.getRaidAt(wolf.blockPosition());

        Raid raidAtOwner =
                owner == null
                        ? null
                        : level.getRaidAt(owner.blockPosition());

        boolean raidActive =
                isActiveRaid(raidAtWolf)
                        || isActiveRaid(raidAtOwner);

        double senseRange =
                raidActive
                        ? 48.0D
                        : OmenWolf.OMEN_SENSE_RANGE;

        List<LivingEntity> possible = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(senseRange),
                wolf::isPotentialOmenThreat);

        int hostileCount = 0;

        for (LivingEntity candidate : possible) {
            if (wolf.distanceToSqr(candidate)
                    <= MAJOR_ACTIVITY_RADIUS
                    * MAJOR_ACTIVITY_RADIUS) {
                ++hostileCount;
            }
        }

        LivingEntity developingThreat = possible.stream()
                .filter(candidate ->
                        shouldSenseCandidate(
                                wolf,
                                candidate,
                                raidActive))
                .max(
                        Comparator.comparingDouble(
                                candidate ->
                                        threatScore(
                                                wolf,
                                                candidate,
                                                raidActive)))
                .orElse(null);

        Brain<OmenWolf> brain = wolf.getBrain();

        brain.setMemory(
                ModMemoryModuleTypes.OMEN_HOSTILE_COUNT.get(),
                hostileCount);

        brain.setMemory(
                ModMemoryModuleTypes.OMEN_RAID_ACTIVE.get(),
                raidActive);

        if (developingThreat != null) {
            brain.setMemory(
                    ModMemoryModuleTypes.OMEN_DEVELOPING_THREAT.get(),
                    developingThreat);
        } else {
            brain.eraseMemory(
                    ModMemoryModuleTypes.OMEN_DEVELOPING_THREAT.get());
        }
    }

    private static boolean shouldSenseCandidate(
            OmenWolf wolf,
            LivingEntity candidate,
            boolean raidActive) {

        if (wolf.isThreatCommittedToFamily(candidate)) {
            return true;
        }

        if (raidActive && candidate instanceof Raider) {
            /*
             * Ominous Presence is intentionally stronger around raids:
             * Omen can notice raid participants without requiring direct
             * line-of-sight to each one.
             */
            return true;
        }

        double distanceSqr =
                wolf.distanceToSqr(candidate);

        return distanceSqr <= 10.0D * 10.0D
                || wolf.getSensing().hasLineOfSight(candidate);
    }

    private static double threatScore(
            OmenWolf wolf,
            LivingEntity candidate,
            boolean raidActive) {

        double score =
                Math.min(
                        100.0D,
                        candidate.getMaxHealth() * 1.2D);

        if (candidate == wolf.getFamilyDefenseTarget()) {
            score += 180.0D;
        }

        if (wolf.isThreatCommittedToFamily(candidate)) {
            score += 150.0D;
        }

        if (raidActive && candidate instanceof Raider) {
            score += 75.0D;
        }

        if (candidate instanceof Mob mob
                && mob.getTarget() == wolf) {
            score += 60.0D;
        }

        score += Math.max(
                0.0D,
                50.0D
                        - Math.sqrt(
                                wolf.distanceToSqr(candidate))
                        * 2.0D);

        return score;
    }

    private static boolean isActiveRaid(Raid raid) {
        return raid != null
                && raid.isActive()
                && !raid.isOver();
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.OMEN_DEVELOPING_THREAT.get(),
                ModMemoryModuleTypes.OMEN_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.OMEN_RAID_ACTIVE.get());
    }
}
