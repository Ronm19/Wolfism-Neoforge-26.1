package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SolarWolf;

/** Solar bite pressure and pack-alert hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SolarWolfGameplayEvents {
    private SolarWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof SolarWolf solar && !solar.isBaby() && victim.isAlive()) {
            // Ordinary bites carry a short radiant burn; named abilities apply
            // their own stronger burn durations directly.
            victim.igniteForSeconds(solar.isInDirectSunlight() ? 3.0F : 2.0F);
        }

        if (victim instanceof SolarWolf solarVictim && sourceEntity instanceof LivingEntity attacker) {
            solarVictim.alertPackToThreat(attacker, solarVictim.isBaby());
        }
    }
}
