package net.ronm19.wolfism.entity.ai.support;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/** A four-tick owner-area snapshot; every caller still validates allegiance, range and life. */
public final class WolfStaffThreatCache {
    private record Snapshot(long tick, Vec3 center, List<WeakReference<LivingEntity>> threats) {}
    private static final Map<ServerLevel, Map<UUID, Snapshot>> LEVELS = new WeakHashMap<>();
    private WolfStaffThreatCache() {}

    public static List<LivingEntity> around(ServerLevel level, LivingEntity owner) {
        Map<UUID, Snapshot> owners = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        long now = level.getGameTime();
        Snapshot snapshot = owners.get(owner.getUUID());
        if (snapshot == null || now < snapshot.tick || now - snapshot.tick >= 4
                || snapshot.center.distanceToSqr(owner.position()) > 4.0D) {
            List<WeakReference<LivingEntity>> threats = level.getEntitiesOfClass(
                    LivingEntity.class, owner.getBoundingBox().inflate(26.0D),
                    entity -> entity instanceof Enemy && entity.isAlive()).stream()
                    .map(WeakReference::new).toList();
            snapshot = new Snapshot(now, owner.position(), threats);
            owners.put(owner.getUUID(), snapshot);
            if (owners.size() > 128) owners.entrySet().removeIf(entry -> now - entry.getValue().tick > 40);
        }
        List<LivingEntity> live = new ArrayList<>(snapshot.threats.size());
        for (WeakReference<LivingEntity> reference : snapshot.threats) {
            LivingEntity threat = reference.get();
            if (threat != null && threat.isAlive() && threat.level() == level) live.add(threat);
        }
        return live;
    }
}
