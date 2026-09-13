package net.ronm19.wolfism.entity.ai.support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;

/**
 * Carefully bounded cross-species cooldown support for New Year's Wolf.
 *
 * <p>The older Wolfism species keep their ability timers as private integer
 * fields inside each concrete entity class. Rewriting the entire roster for one
 * support wolf would be brittle, so this helper discovers only concrete,
 * non-static integer fields whose names explicitly contain {@code cooldown}.</p>
 *
 * <p>Long-form ultimates and internal housekeeping timers are deny-listed. A
 * timer must also have at most 45 seconds remaining before it can be refreshed.
 * This makes Midnight Reset useful for ordinary abilities while preventing it
 * from erasing the roster's major ultimate cooldowns.</p>
 */
public final class NewYearsCooldownSupport {
    private static final int MAX_ELIGIBLE_REMAINING = 20 * 45;
    private static final int REFRESH_WINDOW = 20 * 10;
    private static final int REFRESH_BUDGET = 20 * 6;
    private static final String WINDOW_END = "WolfismNewYearsRefreshWindowEnd";
    private static final String WINDOW_SPENT = "WolfismNewYearsRefreshWindowSpent";
    private static final String NEXT_REFRESH = "WolfismNewYearsNextCooldownRefresh";

    private static final Set<String> DENIED_FIELDS = Set.of(
            // Holiday / major ultimates.
            "echoescooldownticks",
            "cinderstormcooldownticks",
            "ashestoashescooldownticks",
            "convergencecooldownticks",
            "rainofswordscooldownticks",
            "rainofarrowscooldownticks",
            "graveyardcooldownticks",
            "riftfieldcooldownticks",
            "infernocooldownticks",
            "spiritcooldown",
            "fortunecooldown",
            "nightoffrightcooldownticks",
            "meteorshowercooldownticks",
            "magmashowercooldownticks",
            "harbingercooldownticks",
            "firstpackcooldownticks",
            "ravensragecooldownticks",
            "masspossessioncooldownticks",
            "radioactiveraincooldownticks",
            "calltowarcooldownticks",
            "witherragecooldownticks",
            "bloodberserkercooldownticks",
            "voidcataclysmcooldownticks",
            "nooneleftbehindcooldownticks",
            "hellcooldownticks",
            "creatorwillcooldownticks",
            "raincooldownticks",
            "executemodecooldownticks",
            "commandchaincooldownticks",
            "newbeginningcooldown",
            "ultimatecooldown",
            "revivalcooldown",
            "grandfinalecooldown",

            // Non-ability/internal timers that should never be accelerated.
            "giftcooldown",
            "cachecooldown",
            "treasureannouncementcooldown",
            "roseinternalcooldownticks",
            "groundedflightcooldown",
            "underwaterpatrolcooldown",
            "ownerriftreturncooldownticks",
            "rageharasshitcooldownticks",
            "wolfstaffcommandscancooldown");

    private static final ConcurrentMap<Class<?>, List<Field>> FIELD_CACHE =
            new ConcurrentHashMap<>();

    private NewYearsCooldownSupport() {
    }

    public record RefreshResult(int fieldsRefreshed, int ticksRemoved) {
        public static final RefreshResult NONE = new RefreshResult(0, 0);
    }

    /** Returns how many ordinary ability cooldown fields can currently be helped. */
    public static int countEligible(AbstractWolfismWolf wolf) {
        if (wolf == null || wolf instanceof NewYearsWolf || remainingBudget(wolf) <= 0) {
            return 0;
        }

        int count = 0;
        for (Field field : fieldsFor(wolf.getClass())) {
            try {
                int value = field.getInt(wolf);
                if (value > 0 && value <= MAX_ELIGIBLE_REMAINING) {
                    ++count;
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                // A field that becomes inaccessible is simply not refreshable.
            }
        }
        return count;
    }

    /**
     * Removes a bounded number of ticks from each eligible ordinary ability.
     * New Year's Wolves are deliberately excluded to prevent reset loops.
     */
    public static RefreshResult refresh(AbstractWolfismWolf wolf, int requestedTicks) {
        if (wolf == null || wolf instanceof NewYearsWolf || requestedTicks <= 0) {
            return RefreshResult.NONE;
        }

        int budget = remainingBudget(wolf);
        if (budget <= 0) {
            return RefreshResult.NONE;
        }

        int fields = 0;
        int removed = 0;
        int largestReduction = 0;
        int ticks = Math.min(requestedTicks, budget);

        for (Field field : fieldsFor(wolf.getClass())) {
            try {
                int before = field.getInt(wolf);
                if (before <= 0 || before > MAX_ELIGIBLE_REMAINING) {
                    continue;
                }

                int after = Math.max(0, before - ticks);
                if (after == before) {
                    continue;
                }

                field.setInt(wolf, after);
                ++fields;
                removed += before - after;
                largestReduction = Math.max(largestReduction, before - after);
            } catch (IllegalAccessException | RuntimeException ignored) {
                // Fail closed: never crash a pack because one field is inaccessible.
            }
        }

        if (fields == 0) {
            return RefreshResult.NONE;
        }

        // The recipient owns this budget, so extra support wolves cannot stack
        // refreshes. Each ordinary cooldown loses at most six seconds per ten
        // seconds, with at most one refresh per second, across every source.
        long now = wolf.level().getGameTime();
        var data = wolf.getPersistentData();
        if (data.getLongOr(WINDOW_END, 0L) <= now) {
            data.putLong(WINDOW_END, now + REFRESH_WINDOW);
            data.putInt(WINDOW_SPENT, 0);
        }
        data.putInt(WINDOW_SPENT, data.getIntOr(WINDOW_SPENT, 0) + largestReduction);
        data.putLong(NEXT_REFRESH, now + 20);
        return new RefreshResult(fields, removed);
    }

    private static int remainingBudget(AbstractWolfismWolf wolf) {
        if (wolf.level().isClientSide()) return 0;
        long now = wolf.level().getGameTime();
        var data = wolf.getPersistentData();
        if (data.getLongOr(NEXT_REFRESH, 0L) > now) return 0;
        if (data.getLongOr(WINDOW_END, 0L) <= now) return REFRESH_BUDGET;
        return Math.max(0, REFRESH_BUDGET - Math.max(0, data.getIntOr(WINDOW_SPENT, 0)));
    }

    private static List<Field> fieldsFor(Class<?> concreteClass) {
        return FIELD_CACHE.computeIfAbsent(
                concreteClass,
                NewYearsCooldownSupport::discoverFields);
    }

    private static List<Field> discoverFields(Class<?> concreteClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = concreteClass;

        while (cursor != null
                && cursor != AbstractWolfismWolf.class
                && AbstractWolfismWolf.class.isAssignableFrom(cursor)) {
            for (Field field : cursor.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (field.getType() != int.class
                        || Modifier.isStatic(modifiers)
                        || Modifier.isFinal(modifiers)
                        || field.isSynthetic()) {
                    continue;
                }

                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("cooldown") || DENIED_FIELDS.contains(name)) {
                    continue;
                }

                try {
                    if (field.trySetAccessible()) {
                        fields.add(field);
                    }
                } catch (RuntimeException ignored) {
                    // Strong encapsulation/security rules must never break a pack tick.
                }
            }
            cursor = cursor.getSuperclass();
        }

        return List.copyOf(fields);
    }
}
