package net.ronm19.wolfism.event;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.DireWolf;

/** Dire Wolf charge damage and pack hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class DireWolfGameplayEvents {
    private DireWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(player.isShiftKeyDown() || player.isSecondaryUseActive())
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos target = event.getPos();
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)) {
            return;
        }

        if (!DireWolf.isDemolitionBreakableState(level.getBlockState(target))) {
            return;
        }

        DireWolf dire = level.getEntitiesOfClass(
                        DireWolf.class,
                        player.getBoundingBox().inflate(DireWolf.DEMOLITION_COMMAND_SEARCH_RADIUS),
                        candidate -> candidate.canAcceptDemolitionCommand(player, target))
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
                .orElse(null);

        if (dire == null) {
            return;
        }

        if (dire.commandDemolition(player, target)) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof DireWolf dire && !dire.isBaby() && dire.isCharging()) {
            event.setNewDamage(event.getNewDamage() * DireWolf.CHARGE_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof DireWolf direVictim && sourceEntity instanceof LivingEntity attacker) {
            direVictim.alertPackToThreat(attacker, direVictim.isBaby());
        }

        if (sourceEntity instanceof DireWolf hunter
                && DireWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}