package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;

import net.ronm19.wolfism.entity.custom.WitherWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Wither Awareness — Wither Wolf's dedicated tactical perception layer.
 *
 * <p>This sensor deliberately separates perception from execution. It identifies
 * a persistent vanilla Wither quarry, records the boss phase, chooses the most
 * meaningful ordinary artillery threat, measures hostile density, identifies a
 * vulnerable family member, and detects incoming Wither-skull pressure. The
 * WitherWolf entity then consumes those memories in its decision brain and
 * chooses ranged hunt, melee pursuit, shielding, Wither Heart, or Wither Rage.</p>
 */
public final class WitherWolfAwarenessSensor extends Sensor<WitherWolf> {

    private static final double FAMILY_SCAN_RADIUS = WitherWolf.WITHER_SHIELD_RADIUS + 3.0D;
    private static final double SKULL_SCAN_RADIUS = WitherWolf.WITHER_SHIELD_RADIUS + 4.0D;
    private static final double SKULL_DANGER_RADIUS_SQR = 7.0D * 7.0D;

    public WitherWolfAwarenessSensor() {
        // Skull defense needs a faster refresh than a normal low-frequency
        // awareness scan; five ticks keeps the reaction useful without scanning
        // every server tick.
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, WitherWolf wolf) {
        Brain<WitherWolf> brain = wolf.getBrain();

        WitherBoss quarry = getPersistentQuarry(level, wolf, brain);
        int phase = getWitherPhase(quarry);

        List<LivingEntity> threats = wolf.getNearbyWitherThreats(
                level,
                WitherWolf.ARTILLERY_SCAN_RADIUS);

        threats.removeIf(WitherBoss.class::isInstance);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(wolf::witherThreatScore))
                .orElse(null);

        List<LivingEntity> family = wolf.getNearbyWitherFamily(level, FAMILY_SCAN_RADIUS);

        LivingEntity vulnerableFamily = family.stream()
                .filter(member -> member != wolf)
                .filter(wolf::isVulnerableWitherFamilyMember)
                .min(Comparator
                        .comparing((LivingEntity member) -> !member.hasEffect(net.minecraft.world.effect.MobEffects.WITHER))
                        .thenComparingDouble(member ->
                                member.getHealth() / Math.max(1.0F, member.getMaxHealth())))
                .orElse(null);

        int threateningSkulls = 0;
        for (WitherSkull skull : level.getEntitiesOfClass(
                WitherSkull.class,
                wolf.getBoundingBox().inflate(SKULL_SCAN_RADIUS),
                projectile -> projectile.isAlive()
                        && projectile.getOwner() instanceof WitherBoss)) {

            boolean threatensFamily = family.stream()
                    .anyMatch(member -> member.distanceToSqr(skull) <= SKULL_DANGER_RADIUS_SQR);

            if (threatensFamily) {
                ++threateningSkulls;
            }
        }

        setOrErase(
                brain,
                ModMemoryModuleTypes.WITHER_QUARRY.get(),
                quarry);

        brain.setMemory(
                ModMemoryModuleTypes.WITHER_BOSS_PHASE.get(),
                phase);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WITHER_PRIORITY_THREAT.get(),
                priority);

        setOrErase(
                brain,
                ModMemoryModuleTypes.WITHER_VULNERABLE_FAMILY.get(),
                vulnerableFamily);

        brain.setMemory(
                ModMemoryModuleTypes.WITHER_HOSTILE_COUNT.get(),
                threats.size());

        brain.setMemory(
                ModMemoryModuleTypes.WITHER_INCOMING_SKULL_COUNT.get(),
                threateningSkulls);
    }

    private static WitherBoss getPersistentQuarry(
            ServerLevel level,
            WitherWolf wolf,
            Brain<WitherWolf> brain) {

        LivingEntity remembered = brain
                .getMemory(ModMemoryModuleTypes.WITHER_QUARRY.get())
                .orElse(null);

        if (remembered instanceof WitherBoss boss
                && wolf.isValidWitherQuarry(boss)) {
            return boss;
        }

        return level.getEntitiesOfClass(
                        WitherBoss.class,
                        wolf.getBoundingBox().inflate(WitherWolf.WITHER_AWARENESS_RADIUS),
                        wolf::isValidWitherQuarry)
                .stream()
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
    }

    private static int getWitherPhase(WitherBoss quarry) {
        if (quarry == null) {
            return WitherWolf.WITHER_PHASE_NONE;
        }

        if (quarry.getInvulnerableTicks() > 0) {
            return WitherWolf.WITHER_PHASE_SPAWNING;
        }

        if (quarry.isPowered()) {
            return WitherWolf.WITHER_PHASE_ARMORED;
        }

        return WitherWolf.WITHER_PHASE_RANGED;
    }

    private static <T> void setOrErase(
            Brain<WitherWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.WITHER_QUARRY.get(),
                ModMemoryModuleTypes.WITHER_BOSS_PHASE.get(),
                ModMemoryModuleTypes.WITHER_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.WITHER_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.WITHER_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.WITHER_INCOMING_SKULL_COUNT.get());
    }
}
