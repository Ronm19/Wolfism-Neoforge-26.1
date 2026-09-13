package net.ronm19.wolfism.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ChristmasWolf;
import net.ronm19.wolfism.entity.custom.EasterWolf;
import net.ronm19.wolfism.entity.custom.FireworkWolf;
import net.ronm19.wolfism.entity.custom.HalloweenWolf;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;
import net.ronm19.wolfism.entity.custom.SaintPatricksWolf;
import net.ronm19.wolfism.entity.custom.ValentinesWolf;
import net.ronm19.wolfism.registry.ModEntities;

/** Keeps unavailable holiday encounters out of ordinary natural-spawn draws. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class HolidayWolfPotentialSpawns {
    private HolidayWolfPotentialSpawns() {
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.CREATURE
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Selection precedes the placement predicate. Leaving closed seasons in
        // this list spends valid habitat attempts on wolves that cannot spawn.
        // NeoForge owns the list: its removal API preserves copy-on-write behavior.
        for (var entry : List.copyOf(event.getSpawnerDataList())) {
            if (isOutOfSeason(entry.value().type(), level)) {
                event.removeSpawnerData(entry);
            }
        }
    }

    /** Filters a chunk-generation draw without changing the shared biome table. */
    public static WeightedList<MobSpawnSettings.SpawnerData> withoutClosedSeasons(
            WeightedList<MobSpawnSettings.SpawnerData> spawns,
            ServerLevel level) {
        var entries = spawns.unwrap();
        List<Weighted<MobSpawnSettings.SpawnerData>> filtered = null;
        for (int index = 0; index < entries.size(); ++index) {
            var entry = entries.get(index);
            if (isOutOfSeason(entry.value().type(), level)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(entries.subList(0, index));
                }
            } else if (filtered != null) {
                filtered.add(entry);
            }
        }
        return filtered == null ? spawns : WeightedList.of(filtered);
    }

    private static boolean isOutOfSeason(EntityType<?> type, ServerLevel level) {
        // Reuse the same season helpers as natural spawning and encounters,
        // including the server's forceHolidaySpawns override.
        if (type == ModEntities.HALLOWEEN_WOLF.get()) {
            return !HalloweenWolf.isHalloweenSeasonOpen(level);
        }
        if (type == ModEntities.CHRISTMAS_WOLF.get()) {
            return !ChristmasWolf.isChristmasSeasonOpen(level);
        }
        if (type == ModEntities.SAINT_PATRICKS_WOLF.get()) {
            return !SaintPatricksWolf.isSaintPatricksSeasonOpen(level);
        }
        if (type == ModEntities.NEW_YEARS_WOLF.get()) {
            return !NewYearsWolf.isNewYearsSeasonOpen(level);
        }
        if (type == ModEntities.VALENTINES_WOLF.get()) {
            return !ValentinesWolf.isValentinesSeasonOpen(level);
        }
        if (type == ModEntities.EASTER_WOLF.get()) {
            return !EasterWolf.isEasterSeasonOpen(level);
        }
        if (type == ModEntities.FIREWORK_WOLF.get()) {
            return !FireworkWolf.isFireworkSeasonOpen(level);
        }
        return false;
    }
}
