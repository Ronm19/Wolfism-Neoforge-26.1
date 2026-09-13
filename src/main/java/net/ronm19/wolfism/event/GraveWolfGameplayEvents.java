package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.GraveWolf;

/**
 * Two hooks that cannot be implemented correctly from GraveWolf#tick alone:
 *
 * <ul>
 *     <li>Soul Collector needs the authoritative death event.</li>
 *     <li>Death Interception must resolve a lethal hit after damage mitigation
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

        float projectedHealth =
                victim.getHealth()
                        + victim.getAbsorptionAmount()
                        - event.getNewDamage();

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

        // Armor and resistance have resolved; absorption is applied after this hook.
        // Only suppress health damage once the rescue has successfully paid its cost.
        if (grave.performDeathInterception(level, victim)) event.setNewDamage(0.0F);
    }
}
