package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ValentinesWolf;

/** Cross-entity Heartbound protection and family-attacker recording. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ValentinesWolfGameplayEvents {
    private ValentinesWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living
                ? living
                : null;

        ValentinesWolf protector = level.getEntitiesOfClass(
                        ValentinesWolf.class,
                        victim.getBoundingBox().inflate(ValentinesWolf.ULTIMATE_RADIUS),
                        wolf -> wolf.protectionMultiplierFor(victim) < 1.0F)
                .stream()
                .min(Comparator
                        .comparingDouble((ValentinesWolf wolf) ->
                                wolf.protectionMultiplierFor(victim))
                        .thenComparingDouble(wolf -> wolf.distanceToSqr(victim)))
                .orElse(null);

        if (protector == null) return;

        if (attacker != null) {
            protector.recordFamilyAttacker(level, attacker, victim);
        }

        float adjusted = event.getAmount() * protector.protectionMultiplierFor(victim);
        adjusted = protector.applyCriticalFamilySave(
                level,
                victim,
                event.getSource(),
                adjusted);

        if (adjusted <= 0.0F) event.setCanceled(true);
        else event.setAmount(adjusted);
    }
}
