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

public final class ModBiomeModifierProvider {
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

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModBiomeModifierProvider::bootstrap);

    private static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        context.register(
                ADD_TIMBER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.TIMBER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.TIMBER_WOLF.get(), 2, 4),
                                8
                        )
                )
        );

        context.register(
                ADD_ARCTIC_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.ARCTIC_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.ARCTIC_WOLF.get(), 2, 4),
                                7
                        )
                )
        );

        context.register(
                ADD_BLACK_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.BLACK_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.BLACK_WOLF.get(), 1, 3),
                                6
                        )
                )
        );

        context.register(
                ADD_SAND_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SAND_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SAND_WOLF.get(), 2, 4),
                                8
                        )
                )
        );


        context.register(
                ADD_DIRE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.DIRE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.DIRE_WOLF.get(), 1, 2),
                                3
                        )
                )
        );

        context.register(
                ADD_FIRE_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.FIRE_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.FIRE_WOLF.get(), 1, 3),
                                5
                        )
                )
        );

        context.register(
                ADD_FROST_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.FROST_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.FROST_WOLF.get(), 1, 3),
                                5
                        )
                )
        );

        context.register(
                ADD_STORM_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.STORM_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.STORM_WOLF.get(), 1, 2),
                                6
                        )
                )
        );

        context.register(
                ADD_WATER_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.WATER_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.WATER_WOLF.get(), 1, 2),
                                4
                        )
                )
        );

        context.register(
                ADD_EARTH_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.EARTH_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.EARTH_WOLF.get(), 1, 2),
                                5
                        )
                )
        );

        context.register(
                ADD_SOLAR_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.SOLAR_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.SOLAR_WOLF.get(), 1, 2),
                                4
                        )
                )
        );

        context.register(
                ADD_LUNAR_WOLF_SPAWNS,
                AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(ModBiomeTags.LUNAR_WOLF_SPAWNS),
                        new Weighted<>(
                                new SpawnerData(ModEntities.LUNAR_WOLF.get(), 1, 2),
                                4
                        )
                )
        );

    }

    private ModBiomeModifierProvider() {
    }
}
