package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.FrostWolf;

/** Frost Bite application and Frost pack-defense hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class FrostWolfGameplayEvents {
    private FrostWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof FrostWolf frostAttacker
                && !frostAttacker.isBaby()
                && !frostAttacker.isIceSpikeImpactActive()
                && victim.isAlive()) {
            frostAttacker.applyFrostBite(victim);
        }

        if (victim instanceof FrostWolf frostVictim && sourceEntity instanceof LivingEntity attacker) {
            frostVictim.alertPackToThreat(attacker, frostVictim.isBaby());
        }
    }
}
