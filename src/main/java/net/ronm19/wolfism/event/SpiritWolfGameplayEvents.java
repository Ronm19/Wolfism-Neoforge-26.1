package net.ronm19.wolfism.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.ai.sensor.SpiritWolfPackSensor;
import net.ronm19.wolfism.entity.custom.SpiritWolf;

/** Immediate Spirit-family danger alert; healing/protection remains Brain-driven. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SpiritWolfGameplayEvents {
    private SpiritWolfGameplayEvents() {}

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || attacker == victim) return;

        // A Spirit should react when ANY nearby family member is attacked, not only
        // when the Spirit Wolf herself happens to take the hit.
        for (SpiritWolf spirit : level.getEntitiesOfClass(
                SpiritWolf.class,
                victim.getBoundingBox().inflate(SpiritWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && candidate.isSpiritFamilyMember(victim))) {
            if (spirit.isValidSpiritCombatTarget(attacker)) {
                spirit.alertPackToThreat(attacker);
            }
        }
    }
}
