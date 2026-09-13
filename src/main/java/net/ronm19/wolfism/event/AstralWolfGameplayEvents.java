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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.AstralWolf;

/**
 * Cross-entity Celestial Area mitigation / reflection.
 */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class AstralWolfGameplayEvents {
    private static final ThreadLocal<Boolean> REFLECTING =
            ThreadLocal.withInitial(() -> false);

    private AstralWolfGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePre(
            LivingDamageEvent.Pre event) {

        if (REFLECTING.get()) {
            return;
        }

        LivingEntity victim =
                event.getEntity();

        if (!(victim.level()
                instanceof ServerLevel level)
                || !isSupportedFamilyVictim(victim)) {
            return;
        }

        DamageSource source =
                event.getSource();

        Entity attackingEntity =
                source.getEntity();

        Entity directEntity =
                source.getDirectEntity();

        if (attackingEntity == null
                && directEntity == null) {
            return;
        }

        AstralWolf astral =
                findBestAreaAstral(
                        level,
                        victim);

        if (astral == null) {
            return;
        }

        boolean rangedPressure =
                directEntity instanceof Projectile;

        boolean emergency =
                victim.getHealth()
                        <= victim.getMaxHealth()
                        * 0.55F;

        if (!astral.isCelestialAreaActive()
                && (rangedPressure || emergency)) {
            astral.activateCelestialArea(level);
        }

        if (!astral.isCelestialAreaActive()) {
            return;
        }

        float original =
                event.getNewDamage();

        float mitigated =
                original * 0.65F;

        event.setNewDamage(mitigated);

        if (!(attackingEntity
                instanceof LivingEntity attacker)
                || attacker == victim
                || astral.isAstralFamilyMember(attacker)
                || !attacker.isAlive()) {
            return;
        }

        float reflected =
                Math.min(
                        5.0F,
                        original * 0.20F);

        if (reflected <= 0.0F) {
            return;
        }

        try {
            REFLECTING.set(true);

            attacker.hurtServer(
                    level,
                    astral.damageSources()
                            .thorns(astral),
                    reflected);
        } finally {
            REFLECTING.set(false);
        }

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                attacker.getX(),
                attacker.getY()
                        + attacker.getBbHeight()
                        * 0.55D,
                attacker.getZ(),
                5,
                0.20D, 0.22D, 0.20D,
                0.02D);
    }

    private static boolean isSupportedFamilyVictim(
            LivingEntity victim) {

        return victim instanceof Player
                || (victim instanceof Wolf wolf
                && wolf.isTame());
    }

    private static AstralWolf findBestAreaAstral(
            ServerLevel level,
            LivingEntity victim) {

        return level.getEntitiesOfClass(
                AstralWolf.class,
                victim.getBoundingBox().inflate(
                        AstralWolf.CELESTIAL_AREA_RADIUS),
                astral -> astral.isAlive()
                        && astral.isTame()
                        && !astral.isBaby()
                        && astral.isAstralFamilyMember(victim)
                        && (astral.isCelestialAreaActive()
                        || astral.canActivateCelestialArea()))
                .stream()
                .min(
                        Comparator.comparingDouble(
                                astral ->
                                        astral.distanceToSqr(victim)))
                .orElse(null);
    }
}
