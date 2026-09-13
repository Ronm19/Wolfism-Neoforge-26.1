package net.ronm19.wolfism.event;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.vfx.WolfVfx;
import net.ronm19.wolfism.vfx.WolfVfxPhase;

/** Actual successful attacks and projectile launches, with no new damage, AI or idle glow. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WolfPresentationEvents {
    private WolfPresentationEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beginTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level() instanceof ServerLevel) {
            if (event.getEntity() instanceof AbstractWolfismWolf) WolfVfx.beginEntity(event.getEntity());
            else if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof AbstractWolfismWolf wolf) {
                WolfVfx.beginEntity(wolf);
            } else WolfVfx.endEntity();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void endTick(EntityTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof AbstractWolfismWolf wolf
                && !projectile.isRemoved() && projectile.tickCount % 4 == 0 && projectile.getDeltaMovement().lengthSqr() > .015) {
            var travel = projectile.getDeltaMovement().scale(-2);
            WolfVfx.present(wolf, projectile.position(), WolfVfxPhase.PATH, List.of(Vec3.ZERO, travel.scale(.5), travel));
        }
        WolfVfx.endEntity();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterDamage(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel)
                || !(event.getSource().getEntity() instanceof AbstractWolfismWolf wolf)
                || wolf.isWolfismFamily(event.getEntity())) return;
        var hit = event.getEntity().position().add(0, event.getEntity().getBbHeight() * .55, 0);
        WolfVfx.present(wolf, hit, WolfVfxPhase.IMPACT, List.of());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectile(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof AbstractWolfismWolf wolf && !event.loadedFromDisk()) {
            WolfVfx.present(wolf, projectile.position(), WolfVfxPhase.CAST, List.of());
        }
    }
}
