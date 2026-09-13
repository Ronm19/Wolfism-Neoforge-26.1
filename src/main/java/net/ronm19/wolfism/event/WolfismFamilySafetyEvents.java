package net.ronm19.wolfism.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/** Last-line family protection for abilities and vanilla wolf-owned projectiles. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WolfismFamilySafetyEvents {
    private static final String FAMILY_PROJECTILE = "wolfism_family_projectile";
    private static final String FAMILY_OWNER = "wolfism_family_projectile_owner";

    private WolfismFamilySafetyEvents() {
    }

    @SubscribeEvent
    public static void onProjectileAdded(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof AbstractWolfismWolf wolf
                && wolf.isTame()) {
            // Vanilla forgets a removed shooter's entity reference. Persist the
            // minimum family identity so saved/in-flight shots remain safe.
            projectile.getPersistentData().putBoolean(FAMILY_PROJECTILE, true);
            if (wolf.getOwnerReference() != null) {
                projectile.getPersistentData().putString(
                        FAMILY_OWNER, wolf.getOwnerReference().getUUID().toString());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (attacker instanceof AbstractWolfismWolf wolf && wolf.isWolfismFamily(event.getEntity())
                || direct instanceof Projectile projectile
                && isProjectileFamily(projectile, event.getEntity())) {
            // Cancel before shields or emergency saves spend their charges.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && isProjectileFamily(event.getProjectile(), hit.getEntity())) {
            // A launch-time firing-lane check cannot protect family that moves
            // into the path later. Skipping impact also skips fire/Wither effects.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHarmfulFamilyEffect(MobEffectEvent.Applicable event) {
        Entity source = event.getEffectSource();
        if (source == event.getEntity()
                || event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (source instanceof AbstractWolfismWolf wolf && wolf.isWolfismFamily(event.getEntity())
                || source instanceof Projectile projectile && isProjectileFamily(projectile, event.getEntity())) {
            // Damage cancellation alone cannot stop a separately applied fear,
            // slowness or Wither debuff. Only attributable friendly abilities are
            // rejected; enemy attacks, potions and world hazards behave normally.
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getIndirectSourceEntity();
        if (source instanceof AbstractWolfismWolf wolf) {
            // Removing family before blast processing prevents knockback as
            // well as damage from a Wither Wolf skull that hits nearby terrain.
            event.getAffectedEntities().removeIf(wolf::isWolfismFamily);
        } else if (event.getExplosion().getDirectSourceEntity() instanceof Projectile projectile) {
            event.getAffectedEntities().removeIf(entity -> isProjectileFamily(projectile, entity));
        }
    }

    private static boolean isProjectileFamily(Projectile projectile, Entity target) {
        if (projectile.getOwner() instanceof AbstractWolfismWolf wolf) {
            return wolf.isWolfismFamily(target);
        }

        return projectile.getPersistentData().getBooleanOr(FAMILY_PROJECTILE, false)
                && (target instanceof Wolf wolf && wolf.isTame()
                || target.getUUID().toString().equals(
                        projectile.getPersistentData().getStringOr(FAMILY_OWNER, "")));
    }
}
