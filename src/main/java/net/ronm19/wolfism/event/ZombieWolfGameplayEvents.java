package net.ronm19.wolfism.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ZombieWolf;

/** Intrinsic revival after mitigation, ahead of external Grave/Salva rescues. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ZombieWolfGameplayEvents {
    private ZombieWolfGameplayEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        // NORMAL damage modifiers have resolved. LOWEST support rescuers see
        // zero damage after a successful Rise Again and retain their resources.
        if (event.getEntity() instanceof ZombieWolf zombie
                && zombie.level() instanceof ServerLevel level
                && zombie.tryRiseAgain(level, event.getSource(), event.getNewDamage())) {
            event.setNewDamage(0.0F);
        }
    }
}
