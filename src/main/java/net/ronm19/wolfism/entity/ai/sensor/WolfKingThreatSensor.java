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
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.entity.custom.WolfKing;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Battlefield prioritization for Wolf King.
 *
 * <p>Owner attackers and enemies already committed to the King's operational
 * pack outrank ordinary hostiles. Operational pack means recruited Alpha-Hunt
 * followers while wild, or same-owner wolves while tamed. The entity consumes
 * these memories instead of selecting abilities from a random tick roll.</p>
 */
public final class WolfKingThreatSensor extends Sensor<WolfKing> {

    private static final double ACTIVE_BATTLE_RADIUS = 22.0D;

    public WolfKingThreatSensor() {
        super(5);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            WolfKing king) {

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                king.getBoundingBox().inflate(WolfKing.THREAT_SCAN_RADIUS),
                king::isPotentialRoyalThreat);

        int hostileCount = 0;
        for (LivingEntity threat : threats) {
            if (king.distanceToSqr(threat)
                    <= ACTIVE_BATTLE_RADIUS * ACTIVE_BATTLE_RADIUS) {
                ++hostileCount;
            }
        }

        LivingEntity ownerThreat = findOwnerThreat(king, threats);
        LivingEntity threatenedAlly = findThreatenedAlly(level, king, threats);

        LivingEntity sharedThreat = king.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_SHARED_THREAT.get())
                .filter(king::isPotentialRoyalThreat)
                .orElse(null);

        LivingEntity priority = threats.stream()
                .max(Comparator.comparingDouble(king::scoreRoyalThreat))
                .orElse(null);

        if (ownerThreat != null
                && (priority == null
                || king.scoreRoyalThreat(ownerThreat)
                > king.scoreRoyalThreat(priority))) {
            priority = ownerThreat;
        }

        if (sharedThreat != null
                && (priority == null
                || king.scoreRoyalThreat(sharedThreat)
                > king.scoreRoyalThreat(priority))) {
            priority = sharedThreat;
        }

        int allyCount = level.getEntitiesOfClass(
                Wolf.class,
                king.getBoundingBox().inflate(ACTIVE_BATTLE_RADIUS),
                candidate -> candidate != king
                        && candidate.isAlive()
                        && !candidate.isBaby()
                        && !candidate.isOrderedToSit()
                        && !candidate.isInSittingPose()
                        && king.isWolfKingOperationalPackmate(candidate))
                .size() + 1;

        boolean outnumbered = hostileCount >= allyCount + 1;

        Brain<WolfKing> brain = king.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.WOLF_KING_PRIORITY_TARGET.get(),
                priority);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WOLF_KING_OWNER_THREAT.get(),
                ownerThreat);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WOLF_KING_THREATENED_ALLY.get(),
                threatenedAlly);

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_HOSTILE_COUNT.get(),
                hostileCount);

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_PACK_OUTNUMBERED.get(),
                outnumbered);
    }

    private static LivingEntity findOwnerThreat(
            WolfKing king,
            List<LivingEntity> threats) {

        LivingEntity owner = king.getOwner();
        if (owner == null || !owner.isAlive()) {
            return null;
        }

        LivingEntity lastAttacker = owner.getLastHurtByMob();
        if (lastAttacker != null
                && lastAttacker.isAlive()
                && king.isPotentialRoyalThreat(lastAttacker)) {
            return lastAttacker;
        }

        return threats.stream()
                .filter(candidate -> candidate instanceof Mob mob
                        && mob.getTarget() == owner)
                .max(Comparator.comparingDouble(king::scoreRoyalThreat))
                .orElse(null);
    }

    private static LivingEntity findThreatenedAlly(
            ServerLevel level,
            WolfKing king,
            List<LivingEntity> threats) {

        return threats.stream()
                .filter(candidate -> candidate instanceof Mob mob
                        && mob.getTarget() instanceof Wolf target
                        && target.isAlive()
                        && king.isWolfKingOperationalPackmate(target))
                .map(candidate -> ((Mob) candidate).getTarget())
                .filter(target -> target != null && target.isAlive())
                .min(Comparator.comparingDouble(
                        target -> target.getHealth()
                                / Math.max(1.0F, target.getMaxHealth())))
                .orElse(null);
    }

    private static <T> void setOrErase(
            Brain<WolfKing> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.WOLF_KING_PRIORITY_TARGET.get(),
                ModMemoryModuleTypes.WOLF_KING_OWNER_THREAT.get(),
                ModMemoryModuleTypes.WOLF_KING_THREATENED_ALLY.get(),
                ModMemoryModuleTypes.WOLF_KING_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.WOLF_KING_PACK_OUTNUMBERED.get(),
                ModMemoryModuleTypes.WOLF_KING_SHARED_THREAT.get());
    }
}
