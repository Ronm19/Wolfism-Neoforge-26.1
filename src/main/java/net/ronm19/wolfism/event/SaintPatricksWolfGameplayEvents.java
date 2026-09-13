package net.ronm19.wolfism.event;

import java.util.Comparator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.SaintPatricksWolf;

/** Cross-entity luck mechanics that cannot be handled from the wolf's own tick. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class SaintPatricksWolfGameplayEvents {
    private static final double PROTECTION_SEARCH_RADIUS = 28.0D;
    private static final String NEXT_OWNER_LUCKY_FIND = "WolfismSaintPatricksNextLuckyFind";
    private static final long OWNER_LUCKY_FIND_GAP = 20L;

    private SaintPatricksWolfGameplayEvents() {
    }

    /**
     * Fortune's Favor can produce a capped clean dodge; Lucky Break can soften
     * a critical hit and keep a family member alive. Only one strongest nearby
     * Saint Patrick's Wolf is allowed to roll for a given hit.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        SaintPatricksWolf protector = level.getEntitiesOfClass(
                        SaintPatricksWolf.class,
                        victim.getBoundingBox().inflate(PROTECTION_SEARCH_RADIUS),
                        wolf -> wolf.getLuckProtectionTier(victim) > 0)
                .stream()
                .max(Comparator
                        .comparingInt((SaintPatricksWolf wolf) ->
                                wolf.getLuckProtectionTier(victim))
                        .thenComparingDouble(wolf -> -wolf.distanceToSqr(victim)))
                .orElse(null);

        if (protector == null) return;

        if (protector.tryFortuneDodge(level, victim, event.getSource())) {
            event.setCanceled(true);
            return;
        }

        float adjusted = protector.tryLuckyBreak(
                level,
                victim,
                event.getSource(),
                event.getAmount());
        if (adjusted < event.getAmount()) {
            if (adjusted <= 0.0F) event.setCanceled(true);
            else event.setAmount(adjusted);
        }
    }

    /**
     * Controlled combat loot opportunity. This is intentionally a tiny bonus
     * pool with a per-owner throttle, not a second Golden Wolf ore-duplication
     * system and not an infinite emerald generator.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity fallen = event.getEntity();
        if (!(fallen.level() instanceof ServerLevel level)
                || !(fallen instanceof Enemy)) {
            return;
        }

        ServerPlayer beneficiary = resolveBeneficiary(event.getSource().getEntity());
        if (beneficiary == null || !beneficiary.isAlive()) return;

        long now = level.getGameTime();
        if (beneficiary.getPersistentData()
                .getLongOr(NEXT_OWNER_LUCKY_FIND, 0L) > now) {
            return;
        }

        SaintPatricksWolf luckyWolf = level.getEntitiesOfClass(
                        SaintPatricksWolf.class,
                        fallen.getBoundingBox().inflate(SaintPatricksWolf.LUCKY_FIND_RADIUS),
                        wolf -> wolf.isAlive()
                                && !wolf.isBaby()
                                && wolf.isTame()
                                && wolf.getOwner() == beneficiary
                                && wolf.getLuckyFindChance(
                                        beneficiary,
                                        fallen.position()) > 0.0D)
                .stream()
                .max(Comparator
                        .comparingDouble((SaintPatricksWolf wolf) ->
                                wolf.getLuckyFindChance(beneficiary, fallen.position()))
                        .thenComparingDouble(wolf -> -wolf.distanceToSqr(fallen)))
                .orElse(null);

        if (luckyWolf == null) return;

        double chance = luckyWolf.getLuckyFindChance(beneficiary, fallen.position());
        if (luckyWolf.getRandom().nextDouble() >= chance) return;

        ItemStack bonus = luckyWolf.createControlledLuckyFind();
        if (bonus.isEmpty() || fallen.spawnAtLocation(level, bonus) == null) return;

        beneficiary.getPersistentData().putLong(
                NEXT_OWNER_LUCKY_FIND,
                now + OWNER_LUCKY_FIND_GAP);
        luckyWolf.onLuckyFindGranted(level, fallen.position());
    }

    private static ServerPlayer resolveBeneficiary(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof TamableAnimal pet
                && pet.isTame()
                && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }
}
