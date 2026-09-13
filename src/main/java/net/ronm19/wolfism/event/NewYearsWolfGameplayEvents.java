package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.NewYearsWolf;

/** Bell taming and the cross-entity Second Chance damage intervention. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class NewYearsWolfGameplayEvents {
    private NewYearsWolfGameplayEvents() {
    }

    /**
     * BellBlock emits BLOCK_CHANGE after a successful ring. Listening here also
     * supports player-shot projectiles and excludes clicks on the bell's stand,
     * sneaking item use and interactions canceled by another mod.
     */
    @SubscribeEvent
    public static void onBellRing(VanillaGameEvent event) {
        if (!event.getVanillaEvent().equals(GameEvent.BLOCK_CHANGE)
                || !(event.getCause() instanceof ServerPlayer player)
                || player.isSpectator()
                || !(event.getLevel() instanceof ServerLevel level)
                || !level.getBlockState(BlockPos.containing(event.getEventPosition())).is(Blocks.BELL)) {
            return;
        }

        BlockPos bell = BlockPos.containing(event.getEventPosition());
        AABB area = new AABB(bell).inflate(NewYearsWolf.BELL_TAMING_RADIUS);
        NewYearsWolf wolf = level.getEntitiesOfClass(
                        NewYearsWolf.class,
                        area,
                        candidate -> !candidate.isTame()
                                && !candidate.isBaby()
                                && !candidate.isAngry()
                                && candidate.isAlive()
                                && !candidate.isHolidayRecovering()
                                && candidate.distanceToSqr(event.getEventPosition())
                                <= NewYearsWolf.BELL_TAMING_RADIUS * NewYearsWolf.BELL_TAMING_RADIUS)
                .stream()
                .min(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(
                                bell.getX() + 0.5D,
                                bell.getY() + 0.5D,
                                bell.getZ() + 0.5D)))
                .orElse(null);

        if (wolf != null) {
            wolf.tryBellTame(player, level);
        }
    }

    /** Only one best nearby New Year's Wolf may intervene in a single hit. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        NewYearsWolf protector = level.getEntitiesOfClass(
                        NewYearsWolf.class,
                        victim.getBoundingBox().inflate(NewYearsWolf.SECOND_CHANCE_RADIUS),
                        wolf -> wolf.canOfferSecondChanceTo(victim))
                .stream()
                .max(Comparator
                        .comparingDouble((NewYearsWolf wolf) ->
                                wolf.secondChanceNeedScore(victim))
                        .thenComparingDouble(wolf -> -wolf.distanceToSqr(victim)))
                .orElse(null);

        if (protector == null) {
            return;
        }

        float adjusted = protector.trySecondChance(
                level,
                victim,
                event.getSource(),
                event.getAmount());
        if (adjusted >= event.getAmount()) {
            return;
        }

        if (adjusted <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(adjusted);
        }
    }
}
