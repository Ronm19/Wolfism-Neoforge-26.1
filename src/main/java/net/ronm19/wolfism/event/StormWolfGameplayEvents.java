package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.StormWolf;

/** Storm Wolf electrical charge, localized-storm damage, and pack-defense hooks. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class StormWolfGameplayEvents {
    private static final int DEALT_DAMAGE_CHARGE = 8;
    private static final int TAKEN_DAMAGE_CHARGE = 12;

    private StormWolfGameplayEvents() {
    }

    /** Ordinary melee becomes more dangerous while the localized Thunderstorm is active. */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof StormWolf storm
                && !storm.isBaby()
                && storm.isThunderstormActive()
                && !storm.isElectricalImpactActive()) {
            event.setNewDamage(event.getNewDamage() * StormWolf.THUNDERSTORM_MELEE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof StormWolf stormAttacker
                && !stormAttacker.isBaby()
                && !stormAttacker.isElectricalImpactActive()) {
            stormAttacker.addThunderCharge(DEALT_DAMAGE_CHARGE);
        }

        if (victim instanceof StormWolf stormVictim && sourceEntity instanceof LivingEntity attacker) {
            if (!stormVictim.isBaby()) {
                stormVictim.addThunderCharge(TAKEN_DAMAGE_CHARGE);
            }
            stormVictim.alertPackToThreat(attacker, stormVictim.isBaby());
        }
    }
}
