package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.level.block.Blocks;

import net.ronm19.wolfism.entity.custom.AncientWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Ancient Instinct.
 *
 * Ancient recognizes threats and environmental danger through learned
 * experience rather than supernatural foresight.
 */
public final class AncientWolfTacticalSensor
        extends Sensor<AncientWolf> {

    private static final double THREAT_SCAN_RADIUS = 26.0D;
    private static final double FAMILY_SCAN_RADIUS = 18.0D;

    private static final int HAZARD_HORIZONTAL_RADIUS = 5;
    private static final int HAZARD_VERTICAL_RADIUS = 2;

    @Override
    protected void doTick(
            ServerLevel level,
            AncientWolf wolf) {

        List<LivingEntity> threats =
                wolf.getNearbyAncientThreats(
                        level,
                        THREAT_SCAN_RADIUS);

        LivingEntity primary =
                threats.stream()
                        .min(
                                Comparator.comparingDouble(
                                        wolf::ancientThreatScore))
                        .orElse(null);

        var ref = new Object() {
            LivingEntity vulnerableFamily =
                    wolf.getNearbyAncientFamily(
                                    level,
                                    FAMILY_SCAN_RADIUS)
                            .stream()
                            .filter(
                                    family ->
                                            family != wolf)
                            .min(
                                    Comparator.comparingDouble(
                                            family ->
                                                    family.getHealth()
                                                            / Math.max(
                                                            1.0F,
                                                            family.getMaxHealth())))
                            .orElse(null);
        };

        /*
         * Don't report a perfectly healthy family member as "vulnerable"
         * unless an enemy is actively targeting them.
         */
        if (ref.vulnerableFamily != null) {

            float healthFraction =
                    ref.vulnerableFamily.getHealth()
                            / Math.max(
                            1.0F,
                            ref.vulnerableFamily.getMaxHealth());

            boolean underAttack =
                    threats.stream()
                            .anyMatch(
                                    threat ->
                                            threat
                                                    instanceof net.minecraft.world.entity.Mob mob
                                                    && mob.getTarget()
                                                    == ref.vulnerableFamily);

            if (healthFraction > 0.60F
                    && !underAttack) {

                ref.vulnerableFamily = null;
            }
        }

        int localHostiles =
                (int) threats.stream()
                        .filter(
                                threat ->
                                        wolf.distanceToSqr(
                                                threat)
                                                <= 20.0D
                                                * 20.0D)
                        .count();

        BlockPos hazard =
                findNearestHazard(
                        level,
                        wolf);

        Brain<AncientWolf> brain =
                wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ANCIENT_PRIMARY_THREAT
                        .get(),
                primary);

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ANCIENT_VULNERABLE_FAMILY
                        .get(),
                ref.vulnerableFamily);

        setOrErase(
                brain,
                ModMemoryModuleTypes
                        .ANCIENT_HAZARD_POS
                        .get(),
                hazard);

        brain.setMemory(
                ModMemoryModuleTypes
                        .ANCIENT_HOSTILE_COUNT
                        .get(),
                localHostiles);
    }

    // =====================================================================
    // Hazard recognition
    // =====================================================================

    private static BlockPos findNearestHazard(
            ServerLevel level,
            AncientWolf wolf) {

        BlockPos origin =
                wolf.blockPosition();

        BlockPos start =
                origin.offset(
                        -HAZARD_HORIZONTAL_RADIUS,
                        -HAZARD_VERTICAL_RADIUS,
                        -HAZARD_HORIZONTAL_RADIUS);

        BlockPos end =
                origin.offset(
                        HAZARD_HORIZONTAL_RADIUS,
                        HAZARD_VERTICAL_RADIUS,
                        HAZARD_HORIZONTAL_RADIUS);

        BlockPos best = null;
        double bestDistance =
                Double.MAX_VALUE;

        for (BlockPos candidate
                : BlockPos.betweenClosed(
                start,
                end)) {

            if (!isHazard(
                    level,
                    candidate)) {

                continue;
            }

            double distance =
                    candidate.distSqr(
                            origin);

            if (distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;

            best =
                    candidate.immutable();
        }

        return best;
    }

    private static boolean isHazard(
            ServerLevel level,
            BlockPos pos) {

        if (level.getFluidState(pos)
                .is(FluidTags.LAVA)) {

            return true;
        }

        var state =
                level.getBlockState(pos);

        if (state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {

            return true;
        }

        /*
         * Experienced cliff / hole recognition.
         *
         * An open space with no supporting surface for four blocks beneath it
         * is treated as a dangerous drop.
         */
        if (!state.getCollisionShape(
                        level,
                        pos)
                .isEmpty()) {

            return false;
        }

        for (int depth = 1;
             depth <= 4;
             ++depth) {

            BlockPos below =
                    pos.below(depth);

            if (!level.getBlockState(below)
                    .getCollisionShape(
                            level,
                            below)
                    .isEmpty()) {

                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Memory helpers
    // =====================================================================

    private static <T> void setOrErase(
            Brain<AncientWolf> brain,
            MemoryModuleType<T> memory,
            T value) {

        if (value != null) {

            brain.setMemory(
                    memory,
                    value);

        } else {

            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {

        return Set.of(
                ModMemoryModuleTypes
                        .ANCIENT_PRIMARY_THREAT
                        .get(),

                ModMemoryModuleTypes
                        .ANCIENT_VULNERABLE_FAMILY
                        .get(),

                ModMemoryModuleTypes
                        .ANCIENT_HAZARD_POS
                        .get(),

                ModMemoryModuleTypes
                        .ANCIENT_HOSTILE_COUNT
                        .get());
    }
}