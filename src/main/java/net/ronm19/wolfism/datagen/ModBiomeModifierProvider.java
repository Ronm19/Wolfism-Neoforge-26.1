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
    }

    private ModBiomeModifierProvider() {
    }
}
