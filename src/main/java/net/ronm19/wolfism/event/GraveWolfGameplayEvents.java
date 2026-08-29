package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.GraveWolf;

/**
 * Two hooks that cannot be implemented correctly from GraveWolf#tick alone:
 *
 * <ul>
 *     <li>Soul Collector needs the authoritative death event.</li>
 *     <li>Death Interception must see/cancel a genuinely lethal incoming hit
 *         before Minecraft applies it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class GraveWolfGameplayEvents {
    private GraveWolfGameplayEvents() {
    }

    // ---------------------------------------------------------------------
    // Soul Collector
    // ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity fallen = event.getEntity();

        if (!(fallen.level() instanceof ServerLevel level)
                || !(fallen instanceof Enemy)) {
            return;
        }

        GraveWolf grave = level.getEntitiesOfClass(
                        GraveWolf.class,
                        fallen.getBoundingBox().inflate(
                                GraveWolf.SOUL_COLLECTION_RADIUS),
                        wolf -> wolf.isAlive()
                                && !wolf.isBaby()
                                && wolf.getSoulCount()
                                < GraveWolf.MAX_SOULS)
                .stream()
                .min(Comparator.comparingDouble(
                        wolf -> wolf.distanceToSqr(fallen)))
                .orElse(null);

        if (grave == null) {
            return;
        }

        /*
         * Heavy hostile deaths are worth two souls. Ordinary hostiles are one.
         * Only the nearest eligible Grave receives the death so a pack cannot
         * duplicate one corpse into several full soul meters.
         */
        int souls = fallen.getMaxHealth() >= 40.0F
                ? 2
                : 1;

        grave.absorbSoul(level, fallen, souls);
    }

    // ---------------------------------------------------------------------
    // Death Interception
    // ---------------------------------------------------------------------

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

        if (projectedHealth > 0.0F) {
            return;
        }

        GraveWolf grave = level.getEntitiesOfClass(
                        GraveWolf.class,
                        victim.getBoundingBox().inflate(
                                GraveWolf.DEATH_INTERCEPTION_RADIUS),
                        wolf -> wolf.canInterceptDeathFor(victim))
                .stream()
                .min(Comparator.comparingDouble(
                        wolf -> wolf.distanceToSqr(victim)))
                .orElse(null);

        if (grave == null) {
            return;
        }

        /*
         * Cancel first so the lethal hit never reaches normal health resolution.
         * The entity method then pays the souls/health cost and establishes the
         * rescued state.
         */
        event.setCanceled(true);

        if (!grave.performDeathInterception(level, victim)) {
            /*
             * This should only happen if state changed during the same event.
             * Restore the original hit rather than granting a free save.
             */
            event.setCanceled(false);
        }
    }
}
