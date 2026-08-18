package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.BlackWolf;

/** Black Wolf nighttime combat and pack hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class BlackWolfGameplayEvents {
    private BlackWolfGameplayEvents() {
    }

    /**
     * Black Wolves are broadly stronger at night; a prepared opening ambush is
     * stronger again, then enters its own cooldown so every bite is not an ambush.
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof BlackWolf black) || black.isBaby()) {
            return;
        }

        float damage = event.getNewDamage();
        if (black.isNightActive()) {
            damage *= BlackWolf.NIGHT_DAMAGE_MULTIPLIER;
            if (black.consumeAmbushAgainst(event.getEntity())) {
                damage *= BlackWolf.AMBUSH_DAMAGE_MULTIPLIER;
            }
        }
        event.setNewDamage(damage);
    }

    /** Damage alerts the pack; successful prey kills stop immediate chain-hunting. */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof BlackWolf blackVictim && sourceEntity instanceof LivingEntity attacker) {
            blackVictim.cancelAmbush();
            blackVictim.alertPackToThreat(attacker, blackVictim.isBaby());
        }

        if (sourceEntity instanceof BlackWolf hunter
                && BlackWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}
