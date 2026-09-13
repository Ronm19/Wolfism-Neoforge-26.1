package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.SaintPatricksWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Tracks family danger, battle pressure and limited-range existing treasure.
 * The expensive block scan runs every four seconds, not every sensor pulse.
 */
public final class SaintPatricksWolfAwarenessSensor extends Sensor<SaintPatricksWolf> {
    private static final int TREASURE_SCAN_INTERVAL = 80;

    /*
     * Sensors begin on a randomized tick offset. Checking
     * wolf.tickCount % TREASURE_SCAN_INTERVAL can therefore miss forever:
     * this sensor runs every 10 ticks, but its fixed randomized residue may
     * never equal zero modulo 80. Use absolute game time instead so every
     * wolf scans immediately and then once every four seconds.
     */
    private long nextTreasureScanGameTime = Long.MIN_VALUE;

    public SaintPatricksWolfAwarenessSensor() {
        super(10);
    }

    @Override
    protected void doTick(ServerLevel level, SaintPatricksWolf wolf) {
        Brain<SaintPatricksWolf> brain = wolf.getBrain();

        if (!wolf.canProvideSaintPatricksSupport()) {
            brain.eraseMemory(ModMemoryModuleTypes.SAINT_PATRICKS_PRIORITY_THREAT.get());
            brain.eraseMemory(ModMemoryModuleTypes.SAINT_PATRICKS_CRITICAL_FAMILY.get());
            brain.eraseMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get());
            brain.setMemory(ModMemoryModuleTypes.SAINT_PATRICKS_HOSTILE_COUNT.get(), 0);
            brain.setMemory(ModMemoryModuleTypes.SAINT_PATRICKS_INJURED_FAMILY_COUNT.get(), 0);

            // Scan immediately on the first sensor pulse after support resumes.
            this.nextTreasureScanGameTime = level.getGameTime();
            return;
        }

        List<LivingEntity> family = wolf.getSaintPatricksFamily(
                level,
                SaintPatricksWolf.FAMILY_RADIUS);
        List<LivingEntity> threats = wolf.getSaintPatricksThreats(level);

        LivingEntity critical = family.stream()
                .filter(member -> member.getHealth() <= member.getMaxHealth() * 0.35F)
                .max(Comparator.comparingDouble(wolf::criticalFamilyScore))
                .orElse(null);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double score = wolf.distanceToSqr(entity);
                    if (entity instanceof Mob mob
                            && wolf.isSaintPatricksFamily(mob.getTarget())) {
                        score -= 10000.0D;
                    }
                    return score;
                }))
                .orElse(null);

        int injured = (int) family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.80F)
                .count();

        setOrErase(
                brain,
                ModMemoryModuleTypes.SAINT_PATRICKS_CRITICAL_FAMILY.get(),
                critical);
        setOrErase(
                brain,
                ModMemoryModuleTypes.SAINT_PATRICKS_PRIORITY_THREAT.get(),
                priority);
        brain.setMemory(
                ModMemoryModuleTypes.SAINT_PATRICKS_HOSTILE_COUNT.get(),
                threats.size());
        brain.setMemory(
                ModMemoryModuleTypes.SAINT_PATRICKS_INJURED_FAMILY_COUNT.get(),
                injured);

        long gameTime = level.getGameTime();
        if (gameTime >= this.nextTreasureScanGameTime) {
            this.nextTreasureScanGameTime = gameTime + TREASURE_SCAN_INTERVAL;

            BlockPos treasure = wolf.findFourLeafTreasure(level);
            setOrErase(
                    brain,
                    ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get(),
                    treasure);
        } else {
            /*
             * Validate an existing memory on every normal sensor pulse without
             * requesting unloaded chunks. Removed chests/ores are forgotten
             * quickly instead of surviving until the next full area scan.
             */
            brain.getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get())
                    .filter(pos -> !isLoadedTreasure(level, pos))
                    .ifPresent(pos -> brain.eraseMemory(
                            ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get()));
        }
    }

    private static boolean isLoadedTreasure(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && SaintPatricksWolf.isFourLeafTreasure(level.getBlockState(pos));
    }

    private static <T> void setOrErase(
            Brain<SaintPatricksWolf> brain,
            MemoryModuleType<T> memory,
            T value) {
        if (value == null) brain.eraseMemory(memory);
        else brain.setMemory(memory, value);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.SAINT_PATRICKS_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.SAINT_PATRICKS_CRITICAL_FAMILY.get(),
                ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get(),
                ModMemoryModuleTypes.SAINT_PATRICKS_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.SAINT_PATRICKS_INJURED_FAMILY_COUNT.get());
    }
}
