package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.FireWolf;

/** Fire Wolf Burning Bite, Flame Rush damage, and pack hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class FireWolfGameplayEvents {
    private FireWolfGameplayEvents() {
    }

    /** Flame Rush hits harder than an ordinary bite. */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof FireWolf fire && !fire.isBaby() && fire.isFlameRushing()) {
            event.setNewDamage(event.getNewDamage() * FireWolf.FLAME_RUSH_DAMAGE_MULTIPLIER);
        }
    }

    /**
     * Successful Fire Wolf bites ignite living victims. Rush impacts burn longer,
     * while ordinary bites provide the species' persistent three-second pressure.
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof FireWolf fireAttacker && !fireAttacker.isBaby() && victim.isAlive()) {
            victim.igniteForSeconds(
                    fireAttacker.isFlameRushing()
                            ? FireWolf.FLAME_RUSH_BURN_SECONDS
                            : FireWolf.BURNING_BITE_SECONDS);
        }

        if (victim instanceof FireWolf fireVictim && sourceEntity instanceof LivingEntity attacker) {
            fireVictim.alertPackToThreat(attacker, fireVictim.isBaby());
        }

        if (sourceEntity instanceof FireWolf hunter
                && FireWolf.isPreferredPrey(victim)
                && !victim.isAlive()) {
            hunter.beginPackHuntCooldown();
        }
    }
}
