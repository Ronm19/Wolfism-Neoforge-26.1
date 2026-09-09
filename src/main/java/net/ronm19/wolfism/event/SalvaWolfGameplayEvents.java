package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SalvaWolf;

/**
 * Authoritative pre-damage hook for Salva's No One Left Behind Last Stand.
 *
 * <p>Tick-based health checks cannot reliably protect a wolf from a large hit
 * that crosses the critical threshold and kills it in the same tick. The
 * incoming-damage event lets Salva spend the wolf's one emergency save before
 * normal health resolution.</p>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SalvaWolfGameplayEvents {

    private SalvaWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {

        LivingEntity victim = event.getEntity();

        if (!(victim.level() instanceof ServerLevel level)
                || victim.isDeadOrDying()
                || event.getAmount() <= 0.0F) {
            return;
        }

        float projectedHealth =
                victim.getHealth()
                        + victim.getAbsorptionAmount()
                        - event.getAmount();

        float trigger =
                victim.getMaxHealth()
                        * SalvaWolf.LAST_STAND_TRIGGER_RATIO;

        if (projectedHealth > trigger) {
            return;
        }

        SalvaWolf salva =
                level.getEntitiesOfClass(
                                SalvaWolf.class,
                                victim.getBoundingBox().inflate(
                                        SalvaWolf.NO_ONE_LEFT_BEHIND_RADIUS),
                                wolf -> wolf.canLastStandProtect(victim))
                        .stream()
                        .min(Comparator.comparingDouble(
                                wolf -> wolf.distanceToSqr(victim)))
                        .orElse(null);

        if (salva == null) {
            return;
        }

        /*
         * Cancel the dangerous hit, then spend exactly one Last Stand token for
         * this wolf. If state unexpectedly changed, restore the original hit.
         */
        event.setCanceled(true);

        if (!salva.performLastStandSave(level, victim)) {
            event.setCanceled(false);
        }
    }
}
