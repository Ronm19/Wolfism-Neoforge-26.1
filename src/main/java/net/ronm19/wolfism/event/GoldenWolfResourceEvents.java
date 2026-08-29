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
import net.ronm19.wolfism.entity.custom.GoldenWolf;
import net.ronm19.wolfism.tag.ModBlockTags;

/**
 * Connects Golden Wolf prosperity abilities to legitimate block drops.
 *
 * <p>This handler intentionally only touches blocks in wolfism:golden_valuable,
 * only for the Golden Wolf's own player, and never duplicates a Silk Touch drop
 * that is the original source block itself.</p>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class GoldenWolfResourceEvents {
    private GoldenWolfResourceEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !event.getState().is(ModBlockTags.GOLDEN_VALUABLE)) {
            return;
        }

        ServerLevel level = event.getLevel();
        GoldenWolf golden = level.getEntitiesOfClass(
                        GoldenWolf.class,
                        player.getBoundingBox().inflate(GoldenWolf.RESOURCE_EVENT_RADIUS),
                        wolf -> wolf.isAlive()
                                && !wolf.isBaby()
                                && wolf.isTame()
                                && wolf.getOwner() == player)
                .stream()
                .min(Comparator.comparingDouble(wolf -> wolf.distanceToSqr(player)))
                .orElse(null);
        if (golden == null) {
            return;
        }

        golden.onValuableResourceHarvest(level, event.getPos(), event.getState());
        double bonusChance = golden.getResourceBonusChance();
        if (bonusChance <= 0.0D) {
            return;
        }

        boolean grantedBonus = false;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }

            // Anti-exploit: Silk Touch ores / valuable blocks remain one block.
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() == event.getState().getBlock()) {
                continue;
            }

            if (golden.getRandom().nextDouble() < bonusChance) {
                stack.grow(1);
                grantedBonus = true;
            }
        }

        if (grantedBonus) {
            golden.emitResourceBonusVisual(level, event.getPos());
        }
    }
}
