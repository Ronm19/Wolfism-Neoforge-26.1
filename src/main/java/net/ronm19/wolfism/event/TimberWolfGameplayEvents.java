package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.TimberWolf;

/** Combat hooks that belong to Timber's species mechanics rather than vanilla Wolf. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class TimberWolfGameplayEvents {
    private TimberWolfGameplayEvents() {
    }

    /** Timber's planned +25% effectiveness against recognized prey. */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof TimberWolf timber
                && !timber.isBaby()
                && TimberWolf.isPreferredPrey(event.getEntity())) {
            event.setNewDamage(event.getNewDamage() * TimberWolf.PREY_DAMAGE_MULTIPLIER);
        }
    }

    /**
     * Damage is pack information. Adults immediately alert compatible packmates;
     * hitting a pup uses the larger emergency radius. A successful prey kill
     * also starts the pack hunt cooldown so wild packs do not chain-kill every
     * animal in sight indefinitely.
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof TimberWolf timberVictim && sourceEntity instanceof LivingEntity attacker) {
            timberVictim.alertPackToThreat(attacker, timberVictim.isBaby());
        }

        if (sourceEntity instanceof TimberWolf hunter
                && TimberWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}
