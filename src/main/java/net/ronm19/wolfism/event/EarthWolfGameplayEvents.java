package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.EarthWolf;

/** Pack-alert hook for Earth Wolf family/pack intelligence. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class EarthWolfGameplayEvents {
    private EarthWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof EarthWolf earthWolf)) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof LivingEntity attacker) {
            earthWolf.alertPackToThreat(attacker, earthWolf.isBaby());
        }
    }
}
