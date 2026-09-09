package net.ronm19.wolfism.tab;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
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
     * The Wolf Staff is now the tab icon as Wolfism's primary command item.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WOLFISM_ITEMS =
            CREATIVE_MODE_TABS.register("wolfism_items", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wolfism.items"))
                    .icon(() -> new ItemStack(ModItems.WOLF_STAFF.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.WOLF_STAFF.get());
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
                        output.accept(ModItems.BLACK_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SAND_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.DIRE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.FIRE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.FROST_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.STORM_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.WATER_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.EARTH_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SOLAR_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.LUNAR_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SPIRIT_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SHADOW_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.GOLDEN_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.CHERRY_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.VIOLET_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.GEM_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.MUSHROOM_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.BEE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ZOMBIE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SKELETON_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.HUSK_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.DROWNED_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.PHANTOM_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.BLOOD_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.END_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SCULK_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.INFERNAL_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.OMEN_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ASTRAL_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ANGEL_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.DEMON_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.GRAVE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.RIFT_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.VOID_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SALVA_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.WOLF_KING_SPAWN_EGG.get());
                        output.accept(ModItems.PRIMORDIAL_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.MAGMA_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.VAMPIRE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.SPECTRAL_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.TOXIC_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.WAR_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ILLAGER_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ANCIENT_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.BLADE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.RAVEN_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.COMMAND_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.ASH_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.WITHER_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.BLAZE_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.HALLOWEEN_WOLF_SPAWN_EGG.get());
                        output.accept(ModItems.CREATOR_WOLF_SPAWN_EGG.get());



                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
