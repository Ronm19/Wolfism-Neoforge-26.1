package net.ronm19.wolfism.event;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SandWolf;

/** Sand Wolf environmental immunity and pack hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SandWolfGameplayEvents {
    private SandWolfGameplayEvents() {
    }

    /** Natural desert adaptation: cactus contact cannot damage a Sand Wolf. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof SandWolf && event.getSource().is(DamageTypes.CACTUS)) {
            event.setCanceled(true);
        }
    }

    /** Damage alerts the local pack; successful prey kills pause chain-hunting. */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof SandWolf sandVictim && sourceEntity instanceof LivingEntity attacker) {
            sandVictim.alertPackToThreat(attacker, sandVictim.isBaby());
        }

        if (sourceEntity instanceof SandWolf hunter
                && SandWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}
