package net.ronm19.wolfism.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.ronm19.wolfism.Wolfism;

public final class ModBiomeTags {
    public static final TagKey<Biome> TIMBER_WOLF_SPAWNS = TagKey.create(
            Registries.BIOME, Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/timber_wolf"));

    public static final TagKey<Biome> ARCTIC_WOLF_SPAWNS = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "spawns/arctic_wolf"));
    public static final TagKey<Biome> ARCTIC_WOLF_COLD_ENVIRONMENT = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "environment/arctic_wolf_cold"));


    private ModBiomeTags() {
    }
}
