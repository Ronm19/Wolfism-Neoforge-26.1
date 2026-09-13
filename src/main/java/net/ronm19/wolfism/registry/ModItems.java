package net.ronm19.wolfism.registry;

import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.custom.item.CreatorWolfSpawnEggItem;
import net.ronm19.wolfism.registry.custom.item.WolfStaffItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wolfism.MOD_ID);

    public static final DeferredItem<WolfStaffItem> WOLF_STAFF = ITEMS.registerItem(
            "wolf_staff",
            properties -> new WolfStaffItem(properties.stacksTo(1)));

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

    public static final DeferredItem<SpawnEggItem> SPIRIT_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "spirit_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SPIRIT_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SHADOW_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "shadow_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SHADOW_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> GOLDEN_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "golden_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GOLDEN_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> CHERRY_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "cherry_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.CHERRY_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> VIOLET_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "violet_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.VIOLET_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> GEM_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "gem_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GEM_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> MUSHROOM_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "mushroom_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.MUSHROOM_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> BEE_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "bee_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BEE_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ZOMBIE_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "zombie_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ZOMBIE_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> SKELETON_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "skeleton_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SKELETON_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> HUSK_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "husk_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.HUSK_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> DROWNED_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "drowned_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DROWNED_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> PHANTOM_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "phantom_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.PHANTOM_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> BLOOD_WOLF_SPAWN_EGG =
            ITEMS.registerItem("blood_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.BLOOD_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> END_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "end_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.END_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SCULK_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "sculk_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.SCULK_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> INFERNAL_WOLF_SPAWN_EGG =
            ITEMS.registerItem("infernal_wolf_spawn_egg", properties
                    -> new SpawnEggItem(properties.spawnEgg(ModEntities.INFERNAL_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> OMEN_WOLF_SPAWN_EGG =
            ITEMS.registerItem("omen_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.OMEN_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ASTRAL_WOLF_SPAWN_EGG =
            ITEMS.registerItem("astral_wolf_spawn_egg", properties -> new SpawnEggItem(
                            properties.spawnEgg(ModEntities.ASTRAL_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ANGEL_WOLF_SPAWN_EGG =
            ITEMS.registerItem("angel_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.ANGEL_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> DEMON_WOLF_SPAWN_EGG =
            ITEMS.registerItem("demon_wolf_spawn_egg", properties -> new SpawnEggItem(
                            properties.spawnEgg(ModEntities.DEMON_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> GRAVE_WOLF_SPAWN_EGG =
            ITEMS.registerItem("grave_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.GRAVE_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> RIFT_WOLF_SPAWN_EGG =
            ITEMS.registerItem("rift_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.RIFT_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> VOID_WOLF_SPAWN_EGG =
            ITEMS.registerItem("void_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.VOID_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SALVA_WOLF_SPAWN_EGG =
            ITEMS.registerItem("salva_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.SALVA_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> WOLF_KING_SPAWN_EGG =
            ITEMS.registerItem("wolf_king_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.WOLF_KING.get())));

    public static final DeferredItem<SpawnEggItem> PRIMORDIAL_WOLF_SPAWN_EGG =
            ITEMS.registerItem("primordial_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.PRIMORDIAL_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> TOXIC_WOLF_SPAWN_EGG =
            ITEMS.registerItem("toxic_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.TOXIC_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> MAGMA_WOLF_SPAWN_EGG =
            ITEMS.registerItem("magma_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.MAGMA_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> VAMPIRE_WOLF_SPAWN_EGG =
            ITEMS.registerItem("vampire_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.VAMPIRE_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SPECTRAL_WOLF_SPAWN_EGG =
            ITEMS.registerItem("spectral_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.SPECTRAL_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> WAR_WOLF_SPAWN_EGG =
            ITEMS.registerItem("war_wolf_spawn_egg", properties -> new SpawnEggItem(
                            properties.spawnEgg(ModEntities.WAR_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ILLAGER_WOLF_SPAWN_EGG = ITEMS.registerItem("illager_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ILLAGER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ANCIENT_WOLF_SPAWN_EGG = ITEMS.registerItem("ancient_wolf_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ANCIENT_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> BLADE_WOLF_SPAWN_EGG =
            ITEMS.registerItem("blade_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BLADE_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> RAVEN_WOLF_SPAWN_EGG =
            ITEMS.registerItem("raven_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.RAVEN_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> COMMAND_WOLF_SPAWN_EGG =
            ITEMS.registerItem("command_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.COMMAND_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ASH_WOLF_SPAWN_EGG =
            ITEMS.registerItem("ash_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ASH_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> WITHER_WOLF_SPAWN_EGG =
            ITEMS.registerItem("wither_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.WITHER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> BLAZE_WOLF_SPAWN_EGG =
            ITEMS.registerItem("blaze_wolf_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BLAZE_WOLF.get())));


    public static final DeferredItem<SpawnEggItem> HALLOWEEN_WOLF_SPAWN_EGG =
            ITEMS.registerItem("halloween_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.HALLOWEEN_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> CREATOR_WOLF_SPAWN_EGG =
            ITEMS.registerItem("creator_wolf_spawn_egg", properties -> new CreatorWolfSpawnEggItem(
                    properties.spawnEgg(ModEntities.CREATOR_WOLF.get())));




    public static final DeferredItem<SpawnEggItem> CHRISTMAS_WOLF_SPAWN_EGG =
            ITEMS.registerItem("christmas_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.CHRISTMAS_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> SAINT_PATRICKS_WOLF_SPAWN_EGG =
            ITEMS.registerItem("saint_patricks_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.SAINT_PATRICKS_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> NEW_YEARS_WOLF_SPAWN_EGG =
            ITEMS.registerItem("new_years_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.NEW_YEARS_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> VALENTINES_WOLF_SPAWN_EGG =
            ITEMS.registerItem("valentines_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.VALENTINES_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> EASTER_WOLF_SPAWN_EGG =
            ITEMS.registerItem("easter_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.EASTER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> FIREWORK_WOLF_SPAWN_EGG =
            ITEMS.registerItem("firework_wolf_spawn_egg", properties -> new SpawnEggItem(
                    properties.spawnEgg(ModEntities.FIREWORK_WOLF.get())));

    private ModItems() {
    }
}
