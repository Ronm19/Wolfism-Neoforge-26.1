package net.ronm19.wolfism.event;

import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.GemWolf;
import net.ronm19.wolfism.tag.ModBlockTags;

/** Connects Miner's Instinct to legitimate ore-block drops. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class GemWolfResourceEvents {
    private GemWolfResourceEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !event.getState().is(ModBlockTags.GEM_TRACKABLE_ORE)) {
            return;
        }

        ServerLevel level = event.getLevel();
        GemWolf gem = level.getEntitiesOfClass(
                        GemWolf.class,
                        player.getBoundingBox().inflate(GemWolf.RESOURCE_EVENT_RADIUS),
                        wolf -> wolf.isAlive() && wolf.canProvideMinersInstinctTo(player))
                .stream()
                .min(Comparator.comparingDouble(wolf -> wolf.distanceToSqr(player)))
                .orElse(null);
        if (gem == null) {
            return;
        }

        gem.onTrackedOreHarvest(level, event.getPos());

        boolean grantedBonus = false;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }

            // Silk Touch must never turn one source ore block into two source blocks.
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() == event.getState().getBlock()) {
                continue;
            }

            if (gem.getRandom().nextDouble() < GemWolf.MINERS_INSTINCT_BONUS_CHANCE) {
                stack.grow(1);
                grantedBonus = true;
            }
        }

        if (grantedBonus) {
            gem.emitMinersInstinctBonusVisual(level, event.getPos());
        }
    }
}
