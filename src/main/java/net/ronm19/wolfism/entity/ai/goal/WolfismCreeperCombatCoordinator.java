package net.ronm19.wolfism.entity.ai.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/**
 * Lightweight server-side coordinator for Wolfism's shared Creeper pack combat.
 *
 * <p>The coordinator intentionally does not use Brain memories: this is a common
 * combat protocol shared by every Wolfism wolf. One adult owns the normal attack
 * turn at a time, while one well-positioned packmate can exploit a rear opening
 * when the Creeper is committed to another wolf.</p>
 */
public final class WolfismCreeperCombatCoordinator {
    private static final double PACK_RADIUS = 18.0D;
    private static final double PACK_RADIUS_SQR = PACK_RADIUS * PACK_RADIUS;
    private static final int MAX_TURN_TICKS = 46;
    private static final int ROTATE_AFTER_HIT_TICKS = 5;
    private static final int GLOBAL_STRIKE_GAP_TICKS = 7;
    private static final int REAR_STRIKE_GAP_TICKS = 12;

    private static final double REAR_OPENING_MAX_DISTANCE = 8.5D;
    private static final double REAR_OPENING_MAX_DISTANCE_SQR = REAR_OPENING_MAX_DISTANCE * REAR_OPENING_MAX_DISTANCE;
    private static final double FOCUS_COMMIT_DISTANCE = 7.5D;
    private static final double FOCUS_COMMIT_DISTANCE_SQR = FOCUS_COMMIT_DISTANCE * FOCUS_COMMIT_DISTANCE;
    private static final double REAR_DOT_THRESHOLD = -0.28D;

    private static final Map<Creeper, State> STATES = new WeakHashMap<>();

    private WolfismCreeperCombatCoordinator() {
    }

    public static boolean isPrimaryAttacker(AbstractWolfismWolf wolf, Creeper creeper) {
        if (!(wolf.level() instanceof ServerLevel level) || !isEligibleAdult(wolf, creeper)) {
            return false;
        }

        List<AbstractWolfismWolf> pack = eligiblePack(level, wolf, creeper);
        if (pack.isEmpty()) {
            STATES.remove(creeper);
            return false;
        }

        State state = STATES.computeIfAbsent(creeper, ignored -> new State());
        refreshPrimary(level, creeper, pack, state);
        return state.primaryWolfId == wolf.getId();
    }

    public static boolean isRearOpeningAttacker(AbstractWolfismWolf wolf, Creeper creeper) {
        if (!(wolf.level() instanceof ServerLevel level)
                || !isEligibleAdult(wolf, creeper)
                || isFuseDangerous(creeper)) {
            return false;
        }

        List<AbstractWolfismWolf> pack = eligiblePack(level, wolf, creeper);
        if (pack.size() < 2) {
            return false;
        }

        State state = STATES.computeIfAbsent(creeper, ignored -> new State());
        refreshPrimary(level, creeper, pack, state);

        if (state.primaryWolfId == wolf.getId() || level.getGameTime() < state.nextRearStrikeAt) {
            return false;
        }

        AbstractWolfismWolf focus = focusWolf(creeper, pack, state.primaryWolfId);
        if (focus == null
                || focus == wolf
                || focus.distanceToSqr(creeper) > FOCUS_COMMIT_DISTANCE_SQR
                || wolf.distanceToSqr(creeper) > REAR_OPENING_MAX_DISTANCE_SQR) {
            return false;
        }

        AbstractWolfismWolf bestRear = null;
        double bestScore = Double.MAX_VALUE;
        for (AbstractWolfismWolf candidate : pack) {
            if (candidate == focus || candidate.getId() == state.primaryWolfId) {
                continue;
            }

            double candidateDistanceSqr = candidate.distanceToSqr(creeper);
            if (candidateDistanceSqr > REAR_OPENING_MAX_DISTANCE_SQR) {
                continue;
            }

            double dot = oppositeSideDot(creeper, focus, candidate);
            if (dot > REAR_DOT_THRESHOLD) {
                continue;
            }

            // Prefer the wolf already nearest the opening and most directly
            // opposite the Creeper's current focus.
            double score = candidateDistanceSqr + (dot + 1.0D) * 4.0D;
            if (score < bestScore) {
                bestScore = score;
                bestRear = candidate;
            }
        }

        return bestRear == wolf;
    }

    public static boolean canStrikeNow(AbstractWolfismWolf wolf, Creeper creeper) {
        if (!(wolf.level() instanceof ServerLevel level)) {
            return false;
        }

        State state = STATES.computeIfAbsent(creeper, ignored -> new State());
        return level.getGameTime() >= state.nextAnyStrikeAt;
    }

    public static void onSuccessfulStrike(AbstractWolfismWolf wolf, Creeper creeper, boolean rearStrike) {
        if (!(wolf.level() instanceof ServerLevel level)) {
            return;
        }

        State state = STATES.computeIfAbsent(creeper, ignored -> new State());
        long now = level.getGameTime();
        state.nextAnyStrikeAt = now + GLOBAL_STRIKE_GAP_TICKS;

        if (rearStrike) {
            state.nextRearStrikeAt = now + REAR_STRIKE_GAP_TICKS;
        } else if (state.primaryWolfId == wolf.getId()) {
            // A primary turn is fundamentally "one clean bite, then rotate".
            // The tiny delay gives the successful wolf time to peel away before
            // another packmate becomes the lead attacker.
            state.turnExpiresAt = Math.min(state.turnExpiresAt, now + ROTATE_AFTER_HIT_TICKS);
            state.lastPrimaryWolfId = wolf.getId();
        }
    }

    public static boolean isFuseDangerous(Creeper creeper) {
        return creeper.isIgnited()
                || creeper.getSwellDir() > 0
                || creeper.getSwelling(1.0F) > 0.0F;
    }

    private static void refreshPrimary(
            ServerLevel level,
            Creeper creeper,
            List<AbstractWolfismWolf> pack,
            State state) {
        long now = level.getGameTime();
        boolean currentStillEligible = pack.stream().anyMatch(wolf -> wolf.getId() == state.primaryWolfId);
        if (currentStillEligible && now < state.turnExpiresAt) {
            return;
        }

        pack.sort(Comparator.comparingInt(AbstractWolfismWolf::getId));

        int nextIndex = 0;
        if (state.primaryWolfId != -1) {
            for (int i = 0; i < pack.size(); ++i) {
                if (pack.get(i).getId() == state.primaryWolfId) {
                    nextIndex = (i + 1) % pack.size();
                    break;
                }
            }
        } else if (state.lastPrimaryWolfId != -1) {
            for (int i = 0; i < pack.size(); ++i) {
                if (pack.get(i).getId() > state.lastPrimaryWolfId) {
                    nextIndex = i;
                    break;
                }
            }
        }

        state.primaryWolfId = pack.get(nextIndex).getId();
        state.lastPrimaryWolfId = state.primaryWolfId;
        state.turnExpiresAt = now + MAX_TURN_TICKS;
    }

    private static List<AbstractWolfismWolf> eligiblePack(
            ServerLevel level,
            AbstractWolfismWolf reference,
            Creeper creeper) {
        List<AbstractWolfismWolf> result = new ArrayList<>();
        for (AbstractWolfismWolf candidate : level.getEntitiesOfClass(
                AbstractWolfismWolf.class,
                creeper.getBoundingBox().inflate(PACK_RADIUS),
                candidate -> candidate.isAlive()
                        && isEligibleAdult(candidate, creeper)
                        && canCoordinate(reference, candidate)
                        && candidate.distanceToSqr(creeper) <= PACK_RADIUS_SQR)) {
            result.add(candidate);
        }
        return result;
    }

    private static boolean isEligibleAdult(AbstractWolfismWolf wolf, Creeper creeper) {
        return wolf.isAlive()
                && !wolf.isBaby()
                && !wolf.isOrderedToSit()
                && wolf.getTarget() == creeper;
    }

    private static boolean canCoordinate(AbstractWolfismWolf a, AbstractWolfismWolf b) {
        if (a == b) {
            return true;
        }

        // Tamed Wolfism wolves are family for safety and Creeper combat. Wild
        // wolves coordinate this tactical attack only with their own species.
        if (a.isTame() || b.isTame()) {
            return a.isTame() && b.isTame();
        }
        return a.getType() == b.getType();
    }

    private static AbstractWolfismWolf focusWolf(
            Creeper creeper,
            List<AbstractWolfismWolf> pack,
            int primaryWolfId) {
        LivingEntity target = creeper.getTarget();
        if (target instanceof AbstractWolfismWolf targetWolf
                && pack.contains(targetWolf)) {
            return targetWolf;
        }

        for (AbstractWolfismWolf wolf : pack) {
            if (wolf.getId() == primaryWolfId) {
                return wolf;
            }
        }
        return null;
    }

    private static double oppositeSideDot(
            Creeper creeper,
            AbstractWolfismWolf focus,
            AbstractWolfismWolf candidate) {
        Vec3 toFocus = new Vec3(
                focus.getX() - creeper.getX(),
                0.0D,
                focus.getZ() - creeper.getZ());
        Vec3 toCandidate = new Vec3(
                candidate.getX() - creeper.getX(),
                0.0D,
                candidate.getZ() - creeper.getZ());

        double focusLength = Math.sqrt(toFocus.x * toFocus.x + toFocus.z * toFocus.z);
        double candidateLength = Math.sqrt(toCandidate.x * toCandidate.x + toCandidate.z * toCandidate.z);
        if (focusLength < 1.0E-4D || candidateLength < 1.0E-4D) {
            return 1.0D;
        }

        return (toFocus.x * toCandidate.x + toFocus.z * toCandidate.z)
                / (focusLength * candidateLength);
    }

    private static final class State {
        private int primaryWolfId = -1;
        private int lastPrimaryWolfId = -1;
        private long turnExpiresAt;
        private long nextAnyStrikeAt;
        private long nextRearStrikeAt;
    }
}
