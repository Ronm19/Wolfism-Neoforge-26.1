package net.ronm19.wolfism.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.ronm19.wolfism.Wolfism;

public final class ModBiomeTags {
    public static final TagKey<Biome> TIMBER_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/timber_wolf"));

    public static final TagKey<Biome> ARCTIC_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/arctic_wolf"));

    public static final TagKey<Biome> ARCTIC_WOLF_COLD_ENVIRONMENT = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "environment/arctic_wolf_cold"));

    public static final TagKey<Biome> BLACK_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/black_wolf"));

    public static final TagKey<Biome> SAND_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/sand_wolf"));

    public static final TagKey<Biome> SAND_WOLF_DESERT_ENVIRONMENT = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "environment/sand_wolf_desert"));


    public static final TagKey<Biome> DIRE_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/dire_wolf"));


    public static final TagKey<Biome> FIRE_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/fire_wolf"));

    public static final TagKey<Biome> FIRE_WOLF_HOT_ENVIRONMENT = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "environment/fire_wolf_hot"));


    public static final TagKey<Biome> FROST_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/frost_wolf"));

    public static final TagKey<Biome> FROST_WOLF_COLD_ENVIRONMENT = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "environment/frost_wolf_cold"));

    public static final TagKey<Biome> STORM_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/storm_wolf"));

    public static final TagKey<Biome> WATER_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/water_wolf"));

    public static final TagKey<Biome> EARTH_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/earth_wolf"));

    public static final TagKey<Biome> SOLAR_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/solar_wolf"));

    public static final TagKey<Biome> LUNAR_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/lunar_wolf"));

    private ModBiomeTags() {
    }
}
