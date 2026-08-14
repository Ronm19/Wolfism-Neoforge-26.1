package net.ronm19.wolfism.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModItems;
import org.jspecify.annotations.NonNull;

public final class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, Wolfism.MOD_ID);
    }

    @Override
    protected void registerModels( @NonNull BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.TIMBER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ARCTIC_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
    }
}