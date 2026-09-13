package net.ronm19.wolfism.event;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.Comparator;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.OmenWolf;

/**
 * Cross-entity mechanics for Omen's Misfortune and predictive family evasion.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class OmenWolfGameplayEvents {
    private static final double MISFORTUNE_SEARCH_RADIUS = 64.0D;
    private static final double PREDICTION_SEARCH_RADIUS =
            OmenWolf.HARBINGER_FAMILY_RADIUS;

    private OmenWolfGameplayEvents() {
    }

    /**
     * Misfortune reduces the marked attacker's overall combat effectiveness.
     * Premonition / Harbinger can also turn a fully landed hit into a partial
     * predictive dodge instead of granting permanent armor or invulnerability.
     */
    @SubscribeEvent
    public static void onLivingDamagePre(
            LivingDamageEvent.Pre event) {

        LivingEntity victim = event.getEntity();

        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }

        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();

        float damage = event.getNewDamage();

        if (sourceEntity instanceof LivingEntity attacker) {
            int severity =
                    findStrongestMisfortune(
                            level,
                            attacker);

            if (severity > 0) {
                float multiplier = switch (severity) {
                    case 1 -> 0.90F;
                    case 2 -> 0.80F;
                    default -> 0.70F;
                };

                /*
                 * Ranged attacks get a little more disruption. This represents
                 * poor aim / mistimed shots without trying to rewrite every
                 * vanilla and modded projectile's trajectory.
                 */
                if (source.getDirectEntity()
                        instanceof Projectile) {
                    multiplier -= 0.05F;
                }

                damage *= Math.max(0.55F, multiplier);
            }
        }

        OmenWolf predictor =
                findBestPredictor(
                        level,
                        victim);

        if (predictor != null) {
            int tier =
                    predictor.getPredictionTierFor(victim);

            if (tier > 0) {
                float dodgeChance =
                        tier >= 2
                                ? 0.60F
                                : 0.35F;

                if (predictor.getRandom().nextFloat()
                        < dodgeChance) {

                    float dodgeMultiplier =
                            tier >= 2
                                    ? 0.35F
                                    : 0.55F;

                    damage *= dodgeMultiplier;

                    predictiveSidestep(
                            victim,
                            source,
                            predictor,
                            tier);

                    WolfVfx.sendParticles("omen_wolf", level,
                            tier >= 2
                                    ? ParticleTypes.WITCH
                                    : ParticleTypes.REVERSE_PORTAL,
                            victim.getX(),
                            victim.getY()
                                    + victim.getBbHeight()
                                    * 0.60D,
                            victim.getZ(),
                            tier >= 2 ? 7 : 4,
                            0.22D, 0.28D, 0.22D,
                            0.02D);
                }
            }
        }

        event.setNewDamage(damage);
    }

    /**
     * Hurting Omen's owner or any tamed-wolf family member makes the attacker
     * dig its own grave: an existing mark escalates, otherwise Omen applies the
     * first severity level.
     */
    @SubscribeEvent
    public static void onLivingDamagePost(
            LivingDamageEvent.Post event) {

        LivingEntity victim = event.getEntity();
        Entity sourceEntity =
                event.getSource().getEntity();

        if (!(victim.level() instanceof ServerLevel level)
                || !(sourceEntity instanceof LivingEntity attacker)
                || attacker == victim
                || !attacker.isAlive()) {
            return;
        }

        for (OmenWolf omen : level.getEntitiesOfClass(
                OmenWolf.class,
                victim.getBoundingBox().inflate(36.0D),
                candidate -> candidate.isAlive()
                        && candidate.isTame()
                        && !candidate.isBaby()
                        && candidate.isOmenFamilyMember(victim))) {

            int existing =
                    omen.getMisfortuneSeverityAgainst(attacker);

            if (existing > 0) {
                omen.intensifyMisfortune(
                        level,
                        attacker);
            } else {
                omen.applyBaseMisfortune(
                        level,
                        attacker);
            }

            if (!(attacker
                    instanceof net.minecraft.world.entity.monster.Creeper)) {
                omen.alertPackToThreat(attacker);
            }
        }
    }

    private static int findStrongestMisfortune(
            ServerLevel level,
            LivingEntity attacker) {

        return level.getEntitiesOfClass(
                OmenWolf.class,
                attacker.getBoundingBox().inflate(
                        MISFORTUNE_SEARCH_RADIUS),
                omen -> omen.isAlive()
                        && !omen.isBaby())
                .stream()
                .mapToInt(
                        omen ->
                                omen.getMisfortuneSeverityAgainst(
                                        attacker))
                .max()
                .orElse(0);
    }

    private static OmenWolf findBestPredictor(
            ServerLevel level,
            LivingEntity victim) {

        if (!(victim instanceof Player)
                && !(victim instanceof Wolf wolf
                && wolf.isTame())) {
            return null;
        }

        return level.getEntitiesOfClass(
                OmenWolf.class,
                victim.getBoundingBox().inflate(
                        PREDICTION_SEARCH_RADIUS),
                omen -> omen.isAlive()
                        && omen.isTame()
                        && !omen.isBaby()
                        && omen.getPredictionTierFor(victim) > 0)
                .stream()
                .max(
                        Comparator.comparingInt(
                                omen ->
                                        omen.getPredictionTierFor(
                                                victim)))
                .orElse(null);
    }

    private static void predictiveSidestep(
            LivingEntity victim,
            DamageSource source,
            OmenWolf omen,
            int tier) {

        /*
         * Do not take movement control away from the player. For wolves the
         * warning becomes a visible physical evasive reaction.
         */
        if (!(victim instanceof Wolf wolf)
                || wolf.isOrderedToSit()) {
            return;
        }

        Entity danger = source.getDirectEntity();

        if (danger == null) {
            danger = source.getEntity();
        }

        Vec3 away;

        if (danger != null) {
            away = victim.position()
                    .subtract(danger.position());
        } else {
            double angle =
                    omen.getRandom().nextDouble()
                            * Math.PI * 2.0D;

            away = new Vec3(
                    Math.cos(angle),
                    0.0D,
                    Math.sin(angle));
        }

        Vec3 horizontal =
                new Vec3(
                        away.x,
                        0.0D,
                        away.z);

        if (horizontal.lengthSqr() <= 1.0E-5D) {
            horizontal = new Vec3(
                    1.0D,
                    0.0D,
                    0.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        /*
         * Sideways relative to the incoming direction, not simply straight
         * backward. Harbinger produces a sharper dodge than Premonition.
         */
        boolean left =
                omen.getRandom().nextBoolean();

        Vec3 side =
                new Vec3(
                        left
                                ? -horizontal.z
                                : horizontal.z,
                        0.0D,
                        left
                                ? horizontal.x
                                : -horizontal.x);

        double strength =
                tier >= 2
                        ? 0.55D
                        : 0.36D;

        wolf.push(
                side.x * strength,
                0.06D,
                side.z * strength);

        wolf.hurtMarked = true;
    }
}
