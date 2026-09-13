package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SalvaWolf;

/**
 * Authoritative pre-damage hook for Salva's No One Left Behind Last Stand.
 *
 * <p>Tick-based health checks cannot reliably protect a wolf from a large hit
 * that crosses the critical threshold and kills it in the same tick. The
 * resolved-damage event lets Salva spend the wolf's one emergency save after
 * mitigation and before normal health resolution.</p>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SalvaWolfGameplayEvents {

    private SalvaWolfGameplayEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(
            LivingDamageEvent.Pre event) {

        LivingEntity victim = event.getEntity();

        if (!(victim.level() instanceof ServerLevel level)
                || victim.isDeadOrDying()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        float healthDamage = Math.max(0.0F, event.getNewDamage() - victim.getAbsorptionAmount());
        if (healthDamage <= 0.0F) return;
        float projectedHealth = victim.getHealth() - healthDamage;

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

        // A fully mitigated hit must never consume the companion's emergency token.
        if (salva.performLastStandSave(level, victim)) event.setNewDamage(0.0F);
    }
}
