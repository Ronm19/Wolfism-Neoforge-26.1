package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.entity.custom.WolfKing;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Pack awareness for Wolf King.
 *
 * <p>The sensor separates broad alliance from actual command authority. A tamed
 * King counts only same-owner wolves as his operational pack. A wild King counts
 * only wolves that have answered Alpha Hunt, while also remembering how many
 * untamed wolves are currently available to recruit with a howl.</p>
 */
public final class WolfKingPackSensor extends Sensor<WolfKing> {

    public WolfKingPackSensor() {
        super(10);
    }

    @Override
    protected void doTick(
            ServerLevel level,
            WolfKing king) {

        List<Wolf> allies = level.getEntitiesOfClass(
                Wolf.class,
                king.getBoundingBox().inflate(WolfKing.PACK_SCAN_RADIUS),
                candidate -> candidate != king
                        && candidate.isAlive()
                        && !candidate.isBaby()
                        && !candidate.isOrderedToSit()
                        && !candidate.isInSittingPose()
                        && king.isWolfKingOperationalPackmate(candidate));

        Wolf nearest = allies.stream()
                .min(Comparator.comparingDouble(king::distanceToSqr))
                .orElse(null);

        LivingEntity sharedThreat = allies.stream()
                .map(Wolf::getTarget)
                .filter(target -> target != null
                        && target.isAlive()
                        && king.isPotentialRoyalThreat(target))
                .max(Comparator.comparingDouble(king::scoreRoyalThreat))
                .orElse(null);

        int totalAllies = allies.size() + 1;

        int wildRecruitCount = 0;
        if (!king.isTame()) {
            wildRecruitCount = level.getEntitiesOfClass(
                    Wolf.class,
                    king.getBoundingBox().inflate(WolfKing.ALPHA_HOWL_RECRUIT_RADIUS),
                    king::canAnswerAlphaHuntHowl)
                    .size();
        }

        double totalDistance = 0.0D;
        double farthestDistance = 0.0D;

        for (Wolf ally : allies) {
            double distance = Math.sqrt(king.distanceToSqr(ally));
            totalDistance += distance;
            farthestDistance = Math.max(farthestDistance, distance);
        }

        double averageDistance = allies.isEmpty()
                ? 0.0D
                : totalDistance / allies.size();

        boolean scattered = totalAllies >= 3
                && (averageDistance > 8.0D || farthestDistance > 15.0D);

        Brain<WolfKing> brain = king.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.NEAREST_WOLF_KING_ALLY.get(),
                nearest);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WOLF_KING_SHARED_THREAT.get(),
                sharedThreat);

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALLY_COUNT.get(),
                totalAllies);

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_WILD_RECRUIT_COUNT.get(),
                wildRecruitCount);

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get(),
                king.isTame() ? 0 : king.getRuntimeAlphaFollowerCount());

        brain.setMemory(
                ModMemoryModuleTypes.WOLF_KING_PACK_SCATTERED.get(),
                scattered);
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
                ModMemoryModuleTypes.NEAREST_WOLF_KING_ALLY.get(),
                ModMemoryModuleTypes.WOLF_KING_ALLY_COUNT.get(),
                ModMemoryModuleTypes.WOLF_KING_SHARED_THREAT.get(),
                ModMemoryModuleTypes.WOLF_KING_PACK_SCATTERED.get(),

                // Register the King's transient ability state in this Brain.
                ModMemoryModuleTypes.WOLF_KING_MARKED_TARGET.get(),
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get(),
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_ACTIVE.get(),
                ModMemoryModuleTypes.WOLF_KING_WILD_RECRUIT_COUNT.get(),
                ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get(),
                ModMemoryModuleTypes.WOLF_KING_ROYAL_COMMAND_COOLDOWN.get(),
                ModMemoryModuleTypes.WOLF_KING_COMMANDING_HOWL_COOLDOWN.get(),
                ModMemoryModuleTypes.WOLF_KING_IRON_WILL_COOLDOWN.get(),
                ModMemoryModuleTypes.WOLF_KING_LEADERSHIP_DASH_COOLDOWN.get());
    }
}
