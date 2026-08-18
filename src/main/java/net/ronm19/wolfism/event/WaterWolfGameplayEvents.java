package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/** Water Wolf family-defense hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WaterWolfGameplayEvents {
    private WaterWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (victim instanceof WaterWolf waterVictim && sourceEntity instanceof LivingEntity attacker) {
            waterVictim.alertPackToThreat(attacker, waterVictim.isBaby());
        }
    }
}
