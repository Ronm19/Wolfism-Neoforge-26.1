package net.ronm19.wolfism.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ChristmasWolf;

/** Family winter protection; strongest eligible Christmas Wolf wins, never stacks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ChristmasWolfGameplayEvents {
    private ChristmasWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity member = event.getEntity();
        if (!(member.level() instanceof ServerLevel level)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        boolean freezing = event.getSource().is(DamageTypeTags.IS_FREEZING);
        boolean projectile = event.getSource().is(DamageTypeTags.IS_PROJECTILE);
        if (!freezing && !projectile) return;

        float multiplier = 1.0F;
        for (ChristmasWolf wolf : level.getEntitiesOfClass(
                ChristmasWolf.class, member.getBoundingBox().inflate(ChristmasWolf.SPIRIT_RADIUS))) {
            if (freezing && wolf.suppliesWarmthTo(member)) {
                member.setTicksFrozen(0);
                event.setCanceled(true);
                return;
            }
            if (projectile && wolf.suppliesWinterGuardTo(member)) {
                multiplier = Math.min(multiplier,
                        wolf.isSpiritOfChristmasActive() ? 0.65F : 0.75F);
            }
        }
        if (multiplier < 1.0F) event.setAmount(event.getAmount() * multiplier);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity member = event.getEntity();
        if (!(member.level() instanceof ServerLevel level)) return;
        float multiplier = 1.0F;
        for (ChristmasWolf wolf : level.getEntitiesOfClass(
                ChristmasWolf.class, member.getBoundingBox().inflate(ChristmasWolf.SPIRIT_RADIUS))) {
            if (wolf.suppliesWinterGuardTo(member)) {
                multiplier = Math.min(multiplier,
                        wolf.isSpiritOfChristmasActive() ? 0.50F : 0.65F);
            }
        }
        if (multiplier < 1.0F) event.setStrength(event.getStrength() * multiplier);
    }
}
