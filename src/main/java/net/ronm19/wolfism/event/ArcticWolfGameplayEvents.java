package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ArcticWolf;

/** Arctic combat/pack hooks that do not belong in vanilla Wolf behavior. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ArcticWolfGameplayEvents {
    private ArcticWolfGameplayEvents() {
    }

    /** Arctic Wolves are moderately more effective against their natural prey. */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ArcticWolf arctic
                && !arctic.isBaby()
                && ArcticWolf.isPreferredPrey(event.getEntity())) {
            event.setNewDamage(event.getNewDamage() * ArcticWolf.PREY_DAMAGE_MULTIPLIER);
        }
    }

    /** Damage alerts the local pack; pup danger gets the larger emergency radius. */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof ArcticWolf arcticVictim && sourceEntity instanceof LivingEntity attacker) {
            arcticVictim.alertPackToThreat(attacker, arcticVictim.isBaby());
        }

        if (sourceEntity instanceof ArcticWolf hunter
                && ArcticWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}
