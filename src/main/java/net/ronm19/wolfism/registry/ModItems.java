package net.ronm19.wolfism.registry;

import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wolfism.MOD_ID);

    public static final DeferredItem<SpawnEggItem> TIMBER_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "timber_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.TIMBER_WOLF.get())));

    public static final DeferredItem<SpawnEggItem> ARCTIC_WOLF_SPAWN_EGG = ITEMS.registerItem(
            "arctic_wolf_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ARCTIC_WOLF.get())));

    private ModItems() {
    }
}
