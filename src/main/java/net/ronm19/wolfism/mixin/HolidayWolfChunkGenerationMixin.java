package net.ronm19.wolfism.mixin;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.ronm19.wolfism.event.HolidayWolfPotentialSpawns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chunk generation reads the biome table directly, bypassing PotentialSpawns. */
@Mixin(NaturalSpawner.class)
public abstract class HolidayWolfChunkGenerationMixin {
    @Redirect(
            method = "spawnMobsForChunkGeneration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobs(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"),
            require = 1)
    private static WeightedList<MobSpawnSettings.SpawnerData> wolfism$seasonalChunkSpawns(
            MobSpawnSettings settings,
            MobCategory category,
            ServerLevelAccessor level,
            Holder<Biome> biome,
            ChunkPos chunkPos,
            RandomSource random) {
        var spawns = settings.getMobs(category);
        return category == MobCategory.CREATURE
                ? HolidayWolfPotentialSpawns.withoutClosedSeasons(spawns, level.getLevel())
                : spawns;
    }
}
