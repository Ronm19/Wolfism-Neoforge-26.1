package net.ronm19.wolfism.tab;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModItems;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wolfism.MOD_ID);

    /**
     * Wolfism blocks only.
     *
     * Oak Log is a temporary icon until Wolfism has a suitable registered block.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WOLFISM_BLOCKS =
            CREATIVE_MODE_TABS.register("wolfism_blocks", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wolfism.blocks"))
                    .icon(() -> new ItemStack(Blocks.OAK_LOG))
                    .displayItems((parameters, output) -> {
                        // Add Wolfism block items here as they are implemented.
                    })
                    .build());

    /**
     * Wolfism non-block, non-spawn-egg items only.
     *
     * Bone is a temporary icon until Wolfism has a suitable normal item.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WOLFISM_ITEMS =
            CREATIVE_MODE_TABS.register("wolfism_items", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wolfism.items"))
                    .icon(() -> new ItemStack(Items.BONE))
                    .displayItems((parameters, output) -> {
                        // Add normal Wolfism items here as they are implemented.
                    })
                    .build());

    /**
     * Main Wolfism mob tab. Spawn eggs belong here rather than in Minecraft's
     * vanilla Spawn Eggs tab.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WOLFISM_MAIN =
            CREATIVE_MODE_TABS.register("wolfism_main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wolfism.main"))
                    .icon(() -> new ItemStack(ModItems.TIMBER_WOLF_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TIMBER_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ARCTIC_WOLF_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
