package net.ronm19.wolfism.entity.ai.support;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;

/** Brings support range to a patient while leaving room around their attacker and body. */
public final class SupportPositioning {
    private SupportPositioning() {}

    public static boolean moveToPatient(AbstractWolfismWolf wolf, LivingEntity patient, double speed) {
        LivingEntity threat = patient.getLastHurtByMob();
        Vec3 away = wolf.position().subtract(patient.position());
        if (threat != null && threat.isAlive() && threat.level() == wolf.level()
                && !wolf.isWolfismFamily(threat) && patient.distanceToSqr(threat) < 16.0D * 16.0D) {
            away = patient.position().subtract(threat.position());
        }
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) {
            double angle = Math.floorMod(wolf.getId(), 16) * Math.PI / 8.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }
        Vec3 destination = patient.position().add(away.normalize().scale(4.0D));
        BlockPos feet = BlockPos.containing(destination);
        if (!(wolf.level() instanceof ServerLevel level)
                || !level.getWorldBorder().isWithinBounds(feet)
                || level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null) return false;
        return wolf.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }
}
