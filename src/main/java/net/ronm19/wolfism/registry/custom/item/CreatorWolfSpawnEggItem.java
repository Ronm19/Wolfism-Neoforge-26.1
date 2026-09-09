package net.ronm19.wolfism.registry.custom.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Secret Creator spawn egg.
 *
 * <p>The creative/developer object intentionally never spoils the public-facing
 * "Creator Wolf" name. Its black gibberish label is stable even after a player
 * has discovered Creator through survival progression.</p>
 */
public final class CreatorWolfSpawnEggItem extends SpawnEggItem {

    public CreatorWolfSpawnEggItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.wolfism.creator_wolf_spawn_egg")
                .withStyle(ChatFormatting.BLACK);
    }
}
