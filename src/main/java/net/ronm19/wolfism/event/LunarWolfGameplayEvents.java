package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.LunarWolf;

/** Pack alert hook for Lunar Wolf family-defense/night-watch behavior. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class LunarWolfGameplayEvents {
    private LunarWolfGameplayEvents() {}

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity();
        if (victim instanceof LunarWolf lunar && source instanceof LivingEntity attacker) {
            lunar.alertPackToThreat(attacker, lunar.isBaby());
        }
    }
}
