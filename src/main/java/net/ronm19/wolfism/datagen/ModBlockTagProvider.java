package net.ronm19.wolfism.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
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
    }
}
