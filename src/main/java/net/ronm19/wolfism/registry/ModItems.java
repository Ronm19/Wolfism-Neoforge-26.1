package net.ronm19.wolfism.registry;

import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wolfism.MOD_ID);

    public static final DeferredItem<SpawnEggItem> TIMBER_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "timber_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.TIMBER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ARCTIC_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "arctic_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ARCTIC_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> BLACK_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "black_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BLACK_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SAND_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "sand_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SAND_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> DIRE_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "dire_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DIRE_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> FIRE_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "fire_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.FIRE_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> FROST_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "frost_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.FROST_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> STORM_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "storm_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.STORM_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> WATER_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "water_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.WATER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> EARTH_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "earth_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.EARTH_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SOLAR_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "solar_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SOLAR_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> LUNAR_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "lunar_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.LUNAR_WOLF.get())));

    private ModItems() {
    }
}
