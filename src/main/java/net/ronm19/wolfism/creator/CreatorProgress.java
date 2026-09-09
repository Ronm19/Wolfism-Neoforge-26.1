package net.ronm19.wolfism.creator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import net.ronm19.wolfism.entity.custom.PrimordialWolf;
import net.ronm19.wolfism.entity.custom.SalvaWolf;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.entity.custom.WolfKing;

/**
 * Player-persistent Creator progression.
 *
 * <p>The four survival prerequisites are recorded when legitimately tamed:
 * Timber Wolf, Primordial Wolf, Wolf King, and Salva Wolf. They do NOT have to
 * remain physically beside one another afterward.</p>
 */
public final class CreatorProgress {
    private static final String PREFIX = "wolfism_creator_";

    public static final String TIMBER = PREFIX + "timber";
    public static final String PRIMORDIAL = PREFIX + "primordial";
    public static final String WOLF_KING = PREFIX + "wolf_king";
    public static final String SALVA = PREFIX + "salva";

    public static final String UNLOCK_ANNOUNCED = PREFIX + "unlock_announced";
    public static final String FIRST_ARRIVAL_SPAWNED = PREFIX + "first_arrival_spawned";
    public static final String ENCOUNTER_ACTIVE = PREFIX + "encounter_active";
    public static final String CREATOR_TAMED = PREFIX + "creator_tamed";
    /** Permanent knowledge flag: once true, vanish never makes the player "forget". */
    public static final String DISCOVERED = PREFIX + "discovered";
    public static final String DEV_GRANTED = PREFIX + "dev_granted";
    public static final String POST_VANISH_HUNT = PREFIX + "post_vanish_hunt";

    /**
     * Increments every time Creator is deliberately vanished.
     * It acts as the hidden randomization salt for that reacquisition hunt.
     */
    public static final String HUNT_GENERATION = PREFIX + "hunt_generation";

    private static final String[] ALL_KEYS = {
            TIMBER, PRIMORDIAL, WOLF_KING, SALVA,
            UNLOCK_ANNOUNCED, FIRST_ARRIVAL_SPAWNED,
            ENCOUNTER_ACTIVE, CREATOR_TAMED, DISCOVERED, DEV_GRANTED, POST_VANISH_HUNT
    };

    private CreatorProgress() {
    }

    private static CompoundTag data(ServerPlayer player) {
        return player.getPersistentData();
    }

    public static boolean get(ServerPlayer player, String key) {
        return data(player).getBooleanOr(key, false);
    }

    public static void set(ServerPlayer player, String key, boolean value) {
        data(player).putBoolean(key, value);
    }

    public static int getInt(ServerPlayer player, String key) {
        return data(player).getIntOr(key, 0);
    }

    public static void setInt(ServerPlayer player, String key, int value) {
        data(player).putInt(key, value);
    }

    public static int getHuntGeneration(ServerPlayer player) {
        return Math.max(0, getInt(player, HUNT_GENERATION));
    }

    public static boolean hasAllPrerequisites(ServerPlayer player) {
        return get(player, TIMBER)
                && get(player, PRIMORDIAL)
                && get(player, WOLF_KING)
                && get(player, SALVA);
    }

    public static void onWolfTamed(ServerPlayer player, AbstractWolfismWolf wolf) {
        boolean changed = false;

        if (wolf instanceof TimberWolf) {
            changed |= mark(player, TIMBER);
        } else if (wolf instanceof PrimordialWolf) {
            changed |= mark(player, PRIMORDIAL);
        } else if (wolf instanceof WolfKing) {
            changed |= mark(player, WOLF_KING);
        } else if (wolf instanceof SalvaWolf) {
            changed |= mark(player, SALVA);
        } else if (wolf instanceof CreatorWolf) {
            set(player, CREATOR_TAMED, true);
            set(player, DISCOVERED, true);
            set(player, ENCOUNTER_ACTIVE, false);
            set(player, POST_VANISH_HUNT, false);
            return;
        }

        if (changed) announceUnlockIfReady(player);
    }

    private static boolean mark(ServerPlayer player, String key) {
        if (get(player, key)) return false;
        set(player, key, true);
        return true;
    }

    /**
     * Migration/recovery helper for existing worlds: once every few seconds the
     * event layer scans nearby already-owned wolves and restores any missing
     * prerequisite marks. No retaming required.
     */
    public static void scanNearbyOwnedPrerequisites(ServerPlayer player) {
        if (hasAllPrerequisites(player)) {
            announceUnlockIfReady(player);
            return;
        }

        player.level().getEntitiesOfClass(
                        AbstractWolfismWolf.class,
                        player.getBoundingBox().inflate(96.0D),
                        wolf -> wolf.isAlive() && wolf.isTame() && wolf.isOwnedBy(player))
                .forEach(wolf -> onWolfTamed(player, wolf));

        announceUnlockIfReady(player);
    }

    private static void announceUnlockIfReady(ServerPlayer player) {
        if (!hasAllPrerequisites(player) || get(player, UNLOCK_ANNOUNCED)) return;

        set(player, UNLOCK_ANNOUNCED, true);
        player.sendSystemMessage(
                Component.translatable("message.wolfism.creator_unlock_ready"));
    }

    public static void markFirstArrival(ServerPlayer player) {
        set(player, FIRST_ARRIVAL_SPAWNED, true);
        set(player, ENCOUNTER_ACTIVE, true);
        set(player, POST_VANISH_HUNT, false);
    }

    public static void markReacquisitionEncounter(ServerPlayer player) {
        set(player, ENCOUNTER_ACTIVE, true);
        set(player, POST_VANISH_HUNT, false);
    }

    public static void markDeveloperGranted(ServerPlayer player) {
        set(player, DEV_GRANTED, true);
        set(player, CREATOR_TAMED, true);
        set(player, DISCOVERED, true);
        set(player, ENCOUNTER_ACTIVE, false);
    }

    public static void onCreatorVanished(ServerPlayer player) {
        // The prerequisite "key" remains earned forever. Only the current
        // Creator leaves. The next encounter becomes a world search.
        set(player, CREATOR_TAMED, false);
        set(player, ENCOUNTER_ACTIVE, false);
        set(player, FIRST_ARRIVAL_SPAWNED, true);
        set(player, POST_VANISH_HUNT, true);

        // Every new vanish reshuffles the hidden structure hunt.
        int nextGeneration = getHuntGeneration(player) + 1;
        if (nextGeneration <= 0) nextGeneration = 1;
        setInt(player, HUNT_GENERATION, nextGeneration);
    }

    public static void copyToClone(ServerPlayer original, ServerPlayer replacement) {
        CompoundTag oldData = original.getPersistentData();
        CompoundTag newData = replacement.getPersistentData();

        for (String key : ALL_KEYS) {
            newData.putBoolean(key, oldData.getBooleanOr(key, false));
        }

        newData.putInt(
                HUNT_GENERATION,
                oldData.getIntOr(HUNT_GENERATION, 0));
    }
}
