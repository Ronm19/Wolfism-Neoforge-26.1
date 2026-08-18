package net.ronm19.wolfism.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/** Universal tamed-family emergency alerts for every Wolfism species. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class WolfismFamilyDefenseEvents {
    public static final double FAMILY_ALERT_RADIUS = 36.0D;

    private WolfismFamilyDefenseEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || !(sourceEntity instanceof LivingEntity attacker)
                || attacker == victim
                || !attacker.isAlive()) {
            return;
        }

        if (victim instanceof Player owner) {
            // A player's owner-status is personal, not universal: only their own
            // nearby tamed Wolfism wolves treat damage to that player as the
            // direct owner emergency.
            level.getEntitiesOfClass(
                            AbstractWolfismWolf.class,
                            owner.getBoundingBox().inflate(FAMILY_ALERT_RADIUS),
                            wolf -> wolf.isAlive()
                                    && wolf.isTame()
                                    && wolf.isOwnedBy(owner))
                    .forEach(wolf -> wolf.beginFamilyDefense(attacker, owner));
            return;
        }

        if (victim instanceof Wolf hurtWolf && hurtWolf.isTame()) {
            // Wolfism family rule: every tamed wolf is family. Species and owner
            // differences do not matter for the protection alert itself.
            level.getEntitiesOfClass(
                            AbstractWolfismWolf.class,
                            hurtWolf.getBoundingBox().inflate(FAMILY_ALERT_RADIUS),
                            wolf -> wolf.isAlive() && wolf.isTame())
                    .forEach(wolf -> wolf.beginFamilyDefense(attacker, hurtWolf));
        }
    }
}
