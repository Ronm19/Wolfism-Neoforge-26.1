package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SculkWolf;

/**
 * Global hooks that cannot live entirely inside SculkWolf.
 *
 * Deep Dark Sense itself is NOT implemented here. SculkWolf owns a real
 * vanilla VibrationSystem listener. This event class only supplies Pack
 * Silence and Sculk Shield damage handling.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SculkWolfGameplayEvents {
    private SculkWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Holder<GameEvent> vanillaEvent = event.getVanillaEvent();
        Entity cause = event.getCause();

        /*
         * HARD personal vibration immunity.
         *
         * SculkWolf#dampensVibrations() remains the vanilla-native first line of
         * defense. This explicit NeoForge cancellation is the second line:
         * movement GameEvents caused by a Sculk Wolf are stopped before ANY
         * nearby GameEventListener (Sensor, Shrieker, etc.) can receive them.
         */
        if (cause instanceof SculkWolf && isMovementVibration(vanillaEvent)) {
            event.setCanceled(true);
            return;
        }

        if (cause instanceof Wolf sourceWolf
                && sourceWolf.isTame()
                && isMovementVibration(vanillaEvent)
                && hasPackSilenceSource(level, sourceWolf)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity damaged = event.getEntity();
        if (!(damaged.level() instanceof ServerLevel level)) {
            return;
        }

        boolean sonicBoom = event.getSource().is(DamageTypes.SONIC_BOOM);
        Entity directSource = event.getSource().getEntity();
        boolean wardenAttack = directSource instanceof Warden;

        /*
         * Innate Warden resistance. This applies to both wild and tamed adult
         * Sculk Wolves, making the species a true Warden counter rather than a
         * normal wolf that merely has a good shield.
         */
        if (damaged instanceof SculkWolf sculkWolf
                && !sculkWolf.isBaby()
                && (sonicBoom || wardenAttack)) {
            event.setNewDamage(
                    event.getNewDamage()
                            * (sonicBoom
                            ? SculkWolf.WARDEN_SONIC_DAMAGE_MULTIPLIER
                            : SculkWolf.WARDEN_MELEE_DAMAGE_MULTIPLIER));
        }

        if (!(damaged instanceof Wolf victim) || !victim.isTame()) {
            return;
        }

        float projectedHealth = victim.getHealth() - event.getNewDamage();
        boolean projectedCritical =
                projectedHealth / Math.max(1.0F, victim.getMaxHealth()) <= 0.65F;

        SculkWolf shieldSource =
                findShieldSource(level, victim, sonicBoom || wardenAttack || projectedCritical);

        if (shieldSource == null || !shieldSource.isSculkShieldActive()) {
            return;
        }

        /*
         * Shield reduction stacks with the Sculk Wolf's innate Warden
         * resistance when the protected victim is itself a Sculk Wolf. That is
         * deliberate: a bonded adult should be extraordinarily hard for the
         * Warden to remove while still remaining damageable.
         */
        event.setNewDamage(
                event.getNewDamage()
                        * (sonicBoom
                        ? SculkWolf.SCULK_SHIELD_SONIC_MULTIPLIER
                        : SculkWolf.SCULK_SHIELD_DAMAGE_MULTIPLIER));
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity();

        if (!(victim.level() instanceof ServerLevel level)
                || !(source instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }

        if (victim instanceof SculkWolf sculkVictim) {
            sculkVictim.alertPackToThreat(attacker);
        }

        if (!(victim instanceof Wolf hurtWolf) || !hurtWolf.isTame()) {
            return;
        }

        for (SculkWolf guardian : level.getEntitiesOfClass(
                SculkWolf.class,
                hurtWolf.getBoundingBox().inflate(SculkWolf.SCULK_SHIELD_RADIUS),
                candidate -> candidate.isAlive()
                        && candidate.isTame()
                        && !candidate.isBaby())) {

            if (guardian.isValidSculkPackThreat(attacker)) {
                guardian.alertPackToThreat(attacker);
            }

            boolean emergency =
                    attacker instanceof Warden
                            || hurtWolf.getHealth()
                            / Math.max(1.0F, hurtWolf.getMaxHealth()) <= 0.65F
                            || guardian.countNearbyHostileThreats(8.0D) >= 3;

            if (emergency) {
                guardian.tryActivateSculkShield();
            }
        }
    }

    private static boolean hasPackSilenceSource(ServerLevel level, Wolf sourceWolf) {
        return level.getEntitiesOfClass(
                        SculkWolf.class,
                        sourceWolf.getBoundingBox().inflate(SculkWolf.PACK_SILENCE_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate.isTame()
                                && !candidate.isBaby()
                                && candidate.distanceToSqr(sourceWolf)
                                <= SculkWolf.PACK_SILENCE_RADIUS
                                * SculkWolf.PACK_SILENCE_RADIUS)
                .stream()
                .findAny()
                .isPresent();
    }

    private static SculkWolf findShieldSource(
            ServerLevel level,
            Wolf victim,
            boolean mayActivate) {
        return level.getEntitiesOfClass(
                        SculkWolf.class,
                        victim.getBoundingBox().inflate(SculkWolf.SCULK_SHIELD_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate.isTame()
                                && !candidate.isBaby()
                                && candidate.distanceToSqr(victim)
                                <= SculkWolf.SCULK_SHIELD_RADIUS
                                * SculkWolf.SCULK_SHIELD_RADIUS)
                .stream()
                .sorted(Comparator
                        .comparing(SculkWolf::isSculkShieldActive)
                        .reversed()
                        .thenComparingDouble(candidate -> candidate.distanceToSqr(victim)))
                .filter(candidate -> candidate.isSculkShieldActive()
                        || (mayActivate && candidate.tryActivateSculkShield()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isMovementVibration(Holder<GameEvent> event) {
        return event.is(GameEvent.STEP)
                || event.is(GameEvent.SWIM)
                || event.is(GameEvent.SPLASH)
                || event.is(GameEvent.HIT_GROUND)
                || event.is(GameEvent.FLAP)
                || event.is(GameEvent.ELYTRA_GLIDE);
    }
}
