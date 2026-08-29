package net.ronm19.wolfism.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.tag.ModBlockTags;

/** Wolfism-owned block classifications used by species gameplay. */
public final class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Wolfism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        this.tag(ModBlockTags.EARTH_MANIPULABLE).addTag(BlockTags.DIRT);
        this.tag(ModBlockTags.GOLDEN_VALUABLE)
                .addTag(BlockTags.COAL_ORES)
                .addTag(BlockTags.IRON_ORES)
                .addTag(BlockTags.COPPER_ORES)
                .addTag(BlockTags.GOLD_ORES)
                .addTag(BlockTags.REDSTONE_ORES)
                .addTag(BlockTags.EMERALD_ORES)
                .addTag(BlockTags.LAPIS_ORES)
                .addTag(BlockTags.DIAMOND_ORES)
                .add(
                        Blocks.ANCIENT_DEBRIS,
                        Blocks.NETHER_QUARTZ_ORE,
                        Blocks.NETHER_GOLD_ORE,
                        Blocks.SUSPICIOUS_SAND,
                        Blocks.SUSPICIOUS_GRAVEL);

        this.tag(ModBlockTags.GEM_TRACKABLE_ORE)
                .addTag(BlockTags.COAL_ORES)
                .addTag(BlockTags.IRON_ORES)
                .addTag(BlockTags.COPPER_ORES)
                .addTag(BlockTags.GOLD_ORES)
                .addTag(BlockTags.REDSTONE_ORES)
                .addTag(BlockTags.EMERALD_ORES)
                .addTag(BlockTags.LAPIS_ORES)
                .addTag(BlockTags.DIAMOND_ORES)
                .add(
                        Blocks.ANCIENT_DEBRIS,
                        Blocks.NETHER_QUARTZ_ORE,
                        Blocks.NETHER_GOLD_ORE,
                        Blocks.BUDDING_AMETHYST,
                        Blocks.AMETHYST_CLUSTER);
    }
}
