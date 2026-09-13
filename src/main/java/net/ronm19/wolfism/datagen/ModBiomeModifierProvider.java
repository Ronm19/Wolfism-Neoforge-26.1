package net.ronm19.wolfism.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers.AddSpawnsBiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModBiomeTags;
import net.ronm19.wolfism.worldgen.biome.ModBiomes;
import net.ronm19.wolfism.worldgen.feature.ModConfiguredFeatures;
import net.ronm19.wolfism.worldgen.feature.ModPlacedFeatures;

public final class ModBiomeModifierProvider {
    public static final ResourceKey<BiomeModifier> ADD_WOLF_KING_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_wolf_king_spawns"));
    public static final ResourceKey<BiomeModifier> ADD_PRIMORDIAL_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_primordial_wolf_spawns"));
    public static final ResourceKey<BiomeModifier> ADD_TIMBER_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_timber_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ARCTIC_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_arctic_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_BLACK_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_black_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SAND_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_sand_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_DIRE_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_dire_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_FIRE_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_fire_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_FROST_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_frost_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_STORM_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_storm_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_WATER_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_water_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_EARTH_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_earth_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SOLAR_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_solar_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_LUNAR_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_lunar_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SPIRIT_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_spirit_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SHADOW_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_shadow_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_GOLDEN_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_golden_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_CHERRY_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_cherry_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_VIOLET_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_violet_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_GEM_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_gem_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_MUSHROOM_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_mushroom_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_BEE_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_bee_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ZOMBIE_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_zombie_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_SKELETON_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_skeleton_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_HUSK_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_husk_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_DROWNED_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_drowned_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_PHANTOM_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_phantom_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_BLOOD_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_blood_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_END_WOLF_SPAWNS = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_end_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_INFERNAL_WOLF_SPAWNS = ResourceKey.create
            (NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_infernal_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_OMEN_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(
                            Wolfism.MOD_ID, "add_omen_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ASTRAL_WOLF_SPAWNS = ResourceKey.create
            (NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_astral_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ANGEL_WOLF_SPAWNS = ResourceKey.create
            (NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_angel_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ANGEL_WOLF_HIGHLAND_SPAWNS = ResourceKey.create
                    (NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_angel_wolf_highland_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_DEMON_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_demon_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_GRAVE_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_grave_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_RIFT_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_rift_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_VOID_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_void_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SALVA_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_salva_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_MAGMA_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_magma_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_VAMPIRE_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_vampire_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SPECTRAL_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_spectral_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_TOXIC_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_toxic_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_WAR_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_war_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ILLAGER_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_illager_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ANCIENT_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_ancient_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_BLADE_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_blade_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_RAVEN_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_raven_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_COMMAND_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_command_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_ASH_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_ash_wolf_spawns"));


    public static final ResourceKey<BiomeModifier> ADD_WITHER_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_wither_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_BLAZE_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_blaze_wolf_spawns"));



    public static final ResourceKey<BiomeModifier> ADD_HALLOWEEN_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_halloween_wolf_spawns"));



    public static final ResourceKey<BiomeModifier> ADD_CHRISTMAS_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_christmas_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_SAINT_PATRICKS_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_saint_patricks_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_NEW_YEARS_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_new_years_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_VALENTINES_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_valentines_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_EASTER_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_easter_wolf_spawns"));

    public static final ResourceKey<BiomeModifier> ADD_FIREWORK_WOLF_SPAWNS =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "add_firework_wolf_spawns"));

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures ::bootstrap)
            .add(Registries.PLACED_FEATURE, ModPlacedFeatures ::bootstrap)
            .add(Registries.BIOME, ModBiomes::bootstrap)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModBiomeModifierProvider::bootstrap);


    private static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        context.register(
                ADD_TIMBER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.TIMBER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.TIMBER_WOLF.get(), 2, 4),
                                10
                        )
                )
        );

        context.register(
                ADD_ARCTIC_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.ARCTIC_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.ARCTIC_WOLF.get(), 2, 4),
                                10
                        )
                )
        );

        context.register(
                ADD_BLACK_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.BLACK_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.BLACK_WOLF.get(), 1, 3),
                                10
                        )
                )
        );

        context.register(
                ADD_SAND_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SAND_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SAND_WOLF.get(), 2, 4),
                                10
                        )
                )
        );


        context.register(
                ADD_DIRE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.DIRE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.DIRE_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_FIRE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.FIRE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.FIRE_WOLF.get(), 1, 3),
                                9
                        )
                )
        );

        context.register(
                ADD_FROST_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.FROST_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.FROST_WOLF.get(), 1, 3),
                                9
                        )
                )
        );

        context.register(
                ADD_STORM_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.STORM_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.STORM_WOLF.get(), 1, 2),
                                10
                        )
                )
        );

        context.register(
                ADD_WATER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.WATER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.WATER_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_EARTH_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.EARTH_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.EARTH_WOLF.get(), 1, 2),
                                9
                        )
                )
        );

        context.register(
                ADD_SOLAR_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SOLAR_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SOLAR_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_LUNAR_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.LUNAR_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.LUNAR_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        // The species' documented solitary taiga/grove encounter was missing
        // its actual biome-table registration.
        context.register(ADD_WOLF_KING_SPAWNS, AddSpawnsBiomeModifier.singleSpawn(
                biomes.getOrThrow(ModBiomeTags.WOLF_KING_SPAWNS),
                new Weighted<>(new SpawnerData(ModEntities.WOLF_KING.get(), 1, 1), 5)));
        context.register(ADD_PRIMORDIAL_WOLF_SPAWNS, AddSpawnsBiomeModifier.singleSpawn(
                biomes.getOrThrow(ModBiomeTags.PRIMORDIAL_WOLF_SPAWNS),
                new Weighted<>(new SpawnerData(ModEntities.PRIMORDIAL_WOLF.get(), 1, 1), 3)));

        context.register(ADD_SPIRIT_WOLF_SPAWNS, AddSpawnsBiomeModifier.singleSpawn(
                biomes.getOrThrow(ModBiomeTags.SPIRIT_WOLF_SPAWNS),
                new Weighted<>(new SpawnerData(ModEntities.SPIRIT_WOLF.get(), 1, 2), 6)));

        context.register(
                ADD_SHADOW_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SHADOW_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SHADOW_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_GOLDEN_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.GOLDEN_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.GOLDEN_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_CHERRY_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.CHERRY_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.CHERRY_WOLF.get(), 1, 2),
                                12
                        )
                )
        );

        context.register(
                ADD_VIOLET_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.VIOLET_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.VIOLET_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_GEM_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.GEM_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.GEM_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_MUSHROOM_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.MUSHROOM_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.MUSHROOM_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_BEE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.BEE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.BEE_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_ZOMBIE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.ZOMBIE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.ZOMBIE_WOLF.get(), 1, 2),
                                8
                        )
                )
        );


        context.register(
                ADD_SKELETON_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SKELETON_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SKELETON_WOLF.get(), 1, 2),
                                6
                        )
                )
        );


        context.register(
                ADD_HUSK_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.HUSK_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.HUSK_WOLF.get(), 1, 2),
                                6
                        )
                )
        );


        context.register(
                ADD_DROWNED_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.DROWNED_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.DROWNED_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_PHANTOM_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.PHANTOM_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.PHANTOM_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_BLOOD_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.BLOOD_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.BLOOD_WOLF.get(), 1, 2),
                                9
                        )));

        context.register(
                ADD_END_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.END_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.END_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_INFERNAL_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.INFERNAL_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.INFERNAL_WOLF.get(),
                                        1,
                                        2),
                                5
                        )
                )
        );

        context.register(
                ADD_OMEN_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.OMEN_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.OMEN_WOLF.get(),
                                        1,
                                        2),
                                4
                        )
                )
        );

        context.register(
                ADD_ASTRAL_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.ASTRAL_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.ASTRAL_WOLF.get(),
                                        1,
                                        2),
                                4
                        )
                )
        );

        context.register(
                ADD_ANGEL_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.ANGEL_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.ANGEL_WOLF.get(), 1, 2),
                                8
                        )
                )
        );

        context.register(
                ADD_ANGEL_WOLF_HIGHLAND_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.ANGEL_WOLF_HIGHLAND_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.ANGEL_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_DEMON_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.DEMON_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.DEMON_WOLF.get(), 1, 1),
                                4
                        )
                )
        );

        context.register(
                ADD_GRAVE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.GRAVE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.GRAVE_WOLF.get(), 1, 2),
                                5
                        )
                )
        );

        context.register(
                ADD_RIFT_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.RIFT_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.RIFT_WOLF.get(),
                                        1,
                                        1),
                                5
                        )
                )
        );

        context.register(
                ADD_VOID_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.VOID_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.VOID_WOLF.get(),
                                        1,
                                        1),
                                4
                        )
                )
        );

        context.register(
                ADD_SALVA_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.SALVA_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.SALVA_WOLF.get(),
                                        1,
                                        2),
                                4
                        )
                )
        );

        context.register(
                ADD_MAGMA_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.MAGMA_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.MAGMA_WOLF.get(), 1, 1),
                                6
                        )
                )
        );

        context.register(
                ADD_VAMPIRE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.VAMPIRE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.VAMPIRE_WOLF.get(), 1, 1),
                                5
                        )
                )
        );

        context.register(
                    ADD_SPECTRAL_WOLF_SPAWNS,
                    AddSpawnsBiomeModifier.singleSpawn(
                            biomes.getOrThrow(ModBiomeTags.SPECTRAL_WOLF_SPAWNS),
                            new Weighted<>(
                                    new SpawnerData(
                                            ModEntities.SPECTRAL_WOLF.get(), 1, 1),
                                    5
                            )
                    )
        );

        context.register(
                ADD_TOXIC_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.TOXIC_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.TOXIC_WOLF.get(), 1, 1),
                                5
                        )
                )
        );

        context.register(
                ADD_WAR_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.WAR_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.WAR_WOLF.get(),
                                        1,
                                        1),
                                5
                        )
                )
        );

        context.register(
                ADD_ILLAGER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.ILLAGER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.ILLAGER_WOLF.get(),
                                        1,
                                        1),
                                4
                        )
                )
        );

        context.register(
                ADD_ANCIENT_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.ANCIENT_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.ANCIENT_WOLF.get(),
                                        1,
                                        1),
                                6
                        )
                )
        );

        context.register(
                ADD_BLADE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.BLADE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.BLADE_WOLF.get(),
                                        1,
                                        1),
                                6
                        )
                )
        );

        context.register(
                ADD_RAVEN_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.RAVEN_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.RAVEN_WOLF.get(),
                                        1,
                                        1),
                                5
                        )
                )
        );

        context.register(
                ADD_COMMAND_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.COMMAND_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.COMMAND_WOLF.get(),
                                        1,
                                        1),
                                6
                        )
                )
        );

        context.register(
                ADD_ASH_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.ASH_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.ASH_WOLF.get(),
                                        1,
                                        1),
                                4
                        )
                )
        );


        context.register(
                ADD_WITHER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(
                                ModBiomeTags.WITHER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.WITHER_WOLF.get(),
                                        1,
                                        1),
                                5
                        )
                )
        );

        context.register(
                ADD_BLAZE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.BLAZE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(
                                        ModEntities.BLAZE_WOLF.get(),
                                        1,
                                        1),
                                5
                        )
                )
        );























        context.register(
                ADD_CHRISTMAS_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.CHRISTMAS_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.CHRISTMAS_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_SAINT_PATRICKS_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SAINT_PATRICKS_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SAINT_PATRICKS_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_NEW_YEARS_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.NEW_YEARS_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.NEW_YEARS_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_VALENTINES_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.VALENTINES_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.VALENTINES_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_EASTER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.EASTER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.EASTER_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_FIREWORK_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.FIREWORK_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.FIREWORK_WOLF.get(), 1, 3),
                                16
                        )
                )
        );

        context.register(
                ADD_HALLOWEEN_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.HALLOWEEN_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.HALLOWEEN_WOLF.get(), 1, 3),
                                16
                        )
                )
        );


    }

    private ModBiomeModifierProvider() {
    }
}
