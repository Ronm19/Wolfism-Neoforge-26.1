package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.GemWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBlockTags;

/**
 * Gem Wolf: Wolfism's precision ore-tracking specialist.
 *
 * <p>Golden Wolf is the prosperity / better-haul companion. Gem Wolf instead
 * answers the practical mining question: "where is the ore?" Gem Sense searches
 * real blocks, Vein Mark keeps up to three deposits directionally marked,
 * Miner's Instinct improves nearby mining, Crystal Dash can punch through a short
 * run of ordinary natural rock toward a tracked deposit, and Geo Resonance turns
 * the owner into a temporary living mineral radar.</p>
 */
public final class GemWolf extends AbstractWolfismWolf {
    public static final int VEIN_MARK_COOLDOWN_TICKS = 20 * 25;
    public static final int CRYSTAL_DASH_COOLDOWN_TICKS = 20 * 20;
    public static final int GEO_RESONANCE_COOLDOWN_TICKS = 20 * 120;

    public static final int VEIN_MARK_DURATION_TICKS = 20 * 60 * 5;
    public static final int GEO_RESONANCE_DURATION_TICKS = 20 * 15;

    public static final double GEM_SENSE_HORIZONTAL_RANGE = 24.0D;
    public static final int GEM_SENSE_VERTICAL_RANGE = 16;
    public static final double MINERS_INSTINCT_RADIUS = 8.0D;
    public static final double RESOURCE_EVENT_RADIUS = 8.0D;
    public static final double CRYSTAL_DASH_MIN_RANGE = 4.0D;
    public static final double CRYSTAL_DASH_MAX_RANGE = 10.0D;
    public static final double GEO_RESONANCE_RADIUS = 24.0D;
    public static final double MINERS_INSTINCT_BONUS_CHANCE = 0.15D;

    private static final int GEM_SENSE_SCAN_INTERVAL = 40;
    private static final int UTILITY_DECISION_INTERVAL = 10;
    private static final int GEO_RESONANCE_DECISION_INTERVAL = 40;
    private static final int MAX_VEIN_MARKS = 3;
    private static final int CRYSTAL_DASH_MAX_TICKS = 14;
    private static final int CRYSTAL_DASH_MAX_BROKEN_BLOCKS = 8;
    private static final int PACK_UTILITY_LOCKOUT_TICKS = 24;

    private final List<MarkedVein> markedVeins = new ArrayList<>();
    private final List<BlockPos> resonanceTargets = new ArrayList<>();

    private int crystalDashTicks;
    private int geoResonanceTicks;
    private int utilityLockoutTicks;
    private int crystalDashBrokenBlocks;
    private BlockPos crystalDashOreTarget;

    private static final class MarkedVein {
        private BlockPos pos;
        private int ticks;

        private MarkedVein(BlockPos pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }

    private static final class OreTarget {
        private final BlockPos pos;
        private final int priority;
        private final double distance;

        private OreTarget(BlockPos pos, int priority, double distance) {
            this.pos = pos;
            this.priority = priority;
            this.distance = distance;
        }
    }

    private static final class BrainHolder {
        private static final Brain.Provider<GemWolf> PROVIDER = Brain.<GemWolf>provider(
                ImmutableList.of(ModSensorTypes.GEM_PACK.get()),
                wolf -> List.of());
    }

    public GemWolf(EntityType<? extends GemWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.GEM_WOLF.get();
    }

    @Override
    protected Brain<GemWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<GemWolf> getBrain() {
        return (Brain<GemWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof GemWolf gem && this.isGemPackmate(gem)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.GEM_VEIN_MARK_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.GEM_CRYSTAL_DASH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.GEM_GEO_RESONANCE_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelGemUtility();
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.GEM_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.canAttack(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.utilityLockoutTicks > 0) {
            --this.utilityLockoutTicks;
        }

        this.tickMarkedVeins(level);

        if (this.isBaby()) {
            return;
        }

        if (this.crystalDashTicks > 0) {
            this.tickCrystalDash(level);
            return;
        }

        if (this.geoResonanceTicks > 0) {
            this.tickGeoResonance(level);
        }

        if (!this.isTame()) {
            return;
        }

        if (this.tickCount % GEM_SENSE_SCAN_INTERVAL == Math.floorMod(this.getId(), GEM_SENSE_SCAN_INTERVAL)) {
            this.updateGemSense(level);
        }

        if (this.isWolfismWorkTick(20)) {
            this.tickGemSenseVisual(level);
            this.refreshMinersInstinct();
        }

        if (this.geoResonanceTicks <= 0
                && this.utilityLockoutTicks <= 0
                && this.tickCount % UTILITY_DECISION_INTERVAL == 0) {
            if (this.tickCount % GEO_RESONANCE_DECISION_INTERVAL == 0
                    && this.tryStartGeoResonance(level)) {
                return;
            }
            if (this.tryVeinMark(level)) {
                return;
            }
            this.tryStartCrystalDash(level);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof GemWolf gem && this.isGemPackmate(gem)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isGemPackmate(GemWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    // ---------------------------------------------------------------------
    // 1. Gem Sense (passive)
    // ---------------------------------------------------------------------

    private void updateGemSense(ServerLevel level) {
        ServerPlayer owner = this.getGemOwner();
        if (owner == null || owner.distanceToSqr(this) > 32.0D * 32.0D) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GEM_ORE_POS.get());
            return;
        }

        // Hold a valid lock instead of bouncing between nearby deposits every scan.
        // Gem is an ore tracker, so once she says "this way", the player should be
        // able to follow that lead until the vein is mined or actually leaves range.
        BlockPos current = this.getBrain().getMemory(ModMemoryModuleTypes.GEM_ORE_POS.get()).orElse(null);
        if (current != null
                && this.isTrackableOre(level.getBlockState(current))
                && this.isWithinGemSenseRange(current)) {
            return;
        }

        List<OreTarget> targets = this.findTopOreTargets(
                level,
                this.blockPosition(),
                (int) GEM_SENSE_HORIZONTAL_RANGE,
                GEM_SENSE_VERTICAL_RANGE,
                1);

        if (targets.isEmpty()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GEM_ORE_POS.get());
        } else {
            this.getBrain().setMemory(ModMemoryModuleTypes.GEM_ORE_POS.get(), targets.get(0).pos);
        }
    }

    private boolean isWithinGemSenseRange(BlockPos pos) {
        BlockPos origin = this.blockPosition();
        return Math.abs(pos.getX() - origin.getX()) <= (int) GEM_SENSE_HORIZONTAL_RANGE
                && Math.abs(pos.getZ() - origin.getZ()) <= (int) GEM_SENSE_HORIZONTAL_RANGE
                && Math.abs(pos.getY() - origin.getY()) <= GEM_SENSE_VERTICAL_RANGE;
    }

    private void tickGemSenseVisual(ServerLevel level) {
        BlockPos ore = this.getBrain().getMemory(ModMemoryModuleTypes.GEM_ORE_POS.get()).orElse(null);
        ServerPlayer owner = this.getGemOwner();
        if (ore == null || owner == null || !this.isTrackableOre(level.getBlockState(ore))) {
            return;
        }

        // Gem herself still gives a short cyan pointer, but the owner's pointer is
        // intentionally much stronger. The old 2-block needle was easy to miss and
        // gave almost no sense of vertical direction in a mine.
        this.sendDirectionalTrace(
                level,
                this.position().add(0.0D, 0.72D, 0.0D),
                ore,
                3.5D,
                9,
                ParticleTypes.ELECTRIC_SPARK);

        if (owner.distanceToSqr(this) <= 24.0D * 24.0D) {
            Vec3 ownerSource = owner.position().add(0.0D, 1.10D, 0.0D);
            this.sendDirectionalTrace(
                    level,
                    ownerSource,
                    ore,
                    5.5D,
                    16,
                    ParticleTypes.END_ROD);
            this.sendDirectionalTrace(
                    level,
                    ownerSource,
                    ore,
                    5.0D,
                    12,
                    ParticleTypes.ELECTRIC_SPARK);

            // The action-bar lock is the authoritative readout. Particles provide
            // direction in the world; the HUD tells the player exactly what Gem has
            // locked onto, how far away it is, and whether to mine up or down.
            owner.sendOverlayMessage(this.buildGemSenseReadout(level, owner, ore));
        }
    }

    // ---------------------------------------------------------------------
    // 2. Vein Mark
    // ---------------------------------------------------------------------

    private boolean tryVeinMark(ServerLevel level) {
        if (!this.canUseGemUtility()
                || this.isCoolingDown(ModMemoryModuleTypes.GEM_VEIN_MARK_COOLDOWN.get())) {
            return false;
        }

        BlockPos ore = this.getBrain().getMemory(ModMemoryModuleTypes.GEM_ORE_POS.get()).orElse(null);
        ServerPlayer owner = this.getGemOwner();
        if (ore == null || owner == null || owner.distanceToSqr(this) > 20.0D * 20.0D) {
            return false;
        }
        if (!this.isTrackableOre(level.getBlockState(ore)) || this.isNearExistingMark(ore, 3.0D)) {
            return false;
        }

        if (this.markedVeins.size() >= MAX_VEIN_MARKS) {
            this.markedVeins.remove(0);
        }
        this.markedVeins.add(new MarkedVein(ore.immutable(), VEIN_MARK_DURATION_TICKS));
        this.getBrain().setMemory(ModMemoryModuleTypes.GEM_MARKED_ORE_POS.get(), ore.immutable());
        this.setPackCooldown(ModMemoryModuleTypes.GEM_VEIN_MARK_COOLDOWN.get(), VEIN_MARK_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(PACK_UTILITY_LOCKOUT_TICKS);

        this.sendGemRing(level, this.position(), 2.0D, 28, ParticleTypes.ELECTRIC_SPARK);
        this.sendGemRing(level, owner.position(), 1.4D, 20, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    private void tickMarkedVeins(ServerLevel level) {
        if (this.markedVeins.isEmpty()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GEM_MARKED_ORE_POS.get());
            return;
        }

        Iterator<MarkedVein> iterator = this.markedVeins.iterator();
        while (iterator.hasNext()) {
            MarkedVein mark = iterator.next();
            --mark.ticks;
            if (mark.ticks <= 0 || !this.isTrackableOre(level.getBlockState(mark.pos))) {
                iterator.remove();
            }
        }

        if (this.markedVeins.isEmpty()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GEM_MARKED_ORE_POS.get());
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.GEM_MARKED_ORE_POS.get(), this.markedVeins.get(0).pos);

        ServerPlayer owner = this.getGemOwner();
        if (owner == null || !this.isWolfismWorkTick(10)) {
            return;
        }

        Vec3 source = owner.position().add(0.0D, 1.10D, 0.0D);
        for (int i = 0; i < this.markedVeins.size(); ++i) {
            MarkedVein mark = this.markedVeins.get(i);
            this.sendDirectionalTrace(
                    level,
                    source,
                    mark.pos,
                    1.8D + i * 0.55D,
                    6,
                    i == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK);
        }
    }

    private boolean isNearExistingMark(BlockPos pos, double radius) {
        double radiusSqr = radius * radius;
        return this.markedVeins.stream().anyMatch(mark -> mark.pos.distSqr(pos) <= radiusSqr);
    }

    // ---------------------------------------------------------------------
    // 3. Miner's Instinct
    // ---------------------------------------------------------------------

    private void refreshMinersInstinct() {
        ServerPlayer owner = this.getGemOwner();
        if (owner == null || owner.distanceToSqr(this) > MINERS_INSTINCT_RADIUS * MINERS_INSTINCT_RADIUS) {
            return;
        }

        boolean hasOreLead = this.getBrain().hasMemoryValue(ModMemoryModuleTypes.GEM_ORE_POS.get())
                || !this.markedVeins.isEmpty()
                || this.geoResonanceTicks > 0;
        if (!hasOreLead) {
            return;
        }

        int amplifier = this.geoResonanceTicks > 0 ? 1 : 0;
        owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, amplifier, true, true), this);
    }

    public boolean canProvideMinersInstinctTo(ServerPlayer player) {
        return !this.isBaby()
                && this.isTame()
                && this.getOwner() == player
                && player.distanceToSqr(this) <= RESOURCE_EVENT_RADIUS * RESOURCE_EVENT_RADIUS
                && (this.getBrain().hasMemoryValue(ModMemoryModuleTypes.GEM_ORE_POS.get())
                || !this.markedVeins.isEmpty()
                || this.geoResonanceTicks > 0);
    }

    public void onTrackedOreHarvest(ServerLevel level, BlockPos pos) {
        this.markedVeins.removeIf(mark -> mark.pos.distSqr(pos) <= 4.0D);
        BlockPos tracked = this.getBrain().getMemory(ModMemoryModuleTypes.GEM_ORE_POS.get()).orElse(null);
        if (tracked != null && tracked.distSqr(pos) <= 4.0D) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GEM_ORE_POS.get());
        }
        WolfVfx.sendParticles("gem_wolf", level,
                ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                10,
                0.28D,
                0.28D,
                0.28D,
                0.04D);
    }

    public void emitMinersInstinctBonusVisual(ServerLevel level, BlockPos pos) {
        WolfVfx.sendParticles("gem_wolf", level,
                ParticleTypes.ENCHANTED_HIT,
                pos.getX() + 0.5D,
                pos.getY() + 0.65D,
                pos.getZ() + 0.5D,
                18,
                0.35D,
                0.35D,
                0.35D,
                0.06D);
    }

    // ---------------------------------------------------------------------
    // 4. Crystal Dash
    // ---------------------------------------------------------------------

    private boolean tryStartCrystalDash(ServerLevel level) {
        if (!this.canUseGemUtility()
                || this.isCoolingDown(ModMemoryModuleTypes.GEM_CRYSTAL_DASH_COOLDOWN.get())
                || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }

        BlockPos ore = this.getBrain().getMemory(ModMemoryModuleTypes.GEM_ORE_POS.get()).orElse(null);
        ServerPlayer owner = this.getGemOwner();
        if (ore == null || owner == null || owner.distanceToSqr(this) > 14.0D * 14.0D) {
            return false;
        }

        Vec3 oreCenter = Vec3.atCenterOf(ore);
        double distance = this.position().distanceTo(oreCenter);
        if (distance < CRYSTAL_DASH_MIN_RANGE || distance > CRYSTAL_DASH_MAX_RANGE) {
            return false;
        }
        if (!this.hasDashBreakableObstacle(level, oreCenter)) {
            return false;
        }

        this.crystalDashOreTarget = ore.immutable();
        this.crystalDashTicks = CRYSTAL_DASH_MAX_TICKS;
        this.crystalDashBrokenBlocks = 0;
        this.setPackCooldown(ModMemoryModuleTypes.GEM_CRYSTAL_DASH_COOLDOWN.get(), CRYSTAL_DASH_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(20);
        this.getNavigation().stop();

        this.steerCrystalDash(oreCenter, 0.72D);
        WolfVfx.sendParticles("gem_wolf", level,
                ParticleTypes.ELECTRIC_SPARK,
                this.getX(),
                this.getY(0.55D),
                this.getZ(),
                28,
                0.30D,
                0.24D,
                0.30D,
                0.08D);
        return true;
    }

    private void tickCrystalDash(ServerLevel level) {
        BlockPos ore = this.crystalDashOreTarget;
        if (ore == null || !this.isTrackableOre(level.getBlockState(ore))) {
            this.finishCrystalDash();
            return;
        }

        --this.crystalDashTicks;
        Vec3 oreCenter = Vec3.atCenterOf(ore);
        this.steerCrystalDash(oreCenter, 0.66D);
        this.breakCrystalDashObstacles(level);

        WolfVfx.sendParticles("gem_wolf", level,
                ParticleTypes.ELECTRIC_SPARK,
                this.getX(),
                this.getY(0.48D),
                this.getZ(),
                6,
                0.18D,
                0.18D,
                0.18D,
                0.02D);

        if (this.position().distanceToSqr(oreCenter) <= 4.0D || this.crystalDashTicks <= 0) {
            this.finishCrystalDash();
        }
    }

    private void steerCrystalDash(Vec3 target, double speed) {
        Vec3 delta = target.subtract(this.position());
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 direction = delta.normalize();
        double y = Math.max(-0.22D, Math.min(0.22D, direction.y * speed));
        this.setDeltaMovement(direction.x * speed, y, direction.z * speed);
        this.hurtMarked = true;
    }

    private boolean hasDashBreakableObstacle(ServerLevel level, Vec3 oreCenter) {
        Vec3 direction = oreCenter.subtract(this.position());
        if (direction.lengthSqr() <= 1.0E-6D) {
            return false;
        }
        direction = direction.normalize();

        for (double step = 0.8D; step <= Math.min(4.8D, this.position().distanceTo(oreCenter) - 1.0D); step += 0.55D) {
            Vec3 sample = this.position().add(direction.scale(step));
            BlockPos pos = BlockPos.containing(sample.x, sample.y, sample.z);
            if (this.isCrystalDashBreakable(level, pos, level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    private void breakCrystalDashObstacles(ServerLevel level) {
        if (this.crystalDashBrokenBlocks >= CRYSTAL_DASH_MAX_BROKEN_BLOCKS
                || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        AABB sweep = this.getBoundingBox().expandTowards(motion.scale(1.4D)).inflate(0.12D, 0.18D, 0.12D);
        BlockPos min = BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ);
        BlockPos max = BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (this.crystalDashBrokenBlocks >= CRYSTAL_DASH_MAX_BROKEN_BLOCKS) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!this.isCrystalDashBreakable(level, pos, state)) {
                continue;
            }
            if (level.destroyBlock(pos, false, this)) {
                ++this.crystalDashBrokenBlocks;
                WolfVfx.sendParticles("gem_wolf", level,
                        ParticleTypes.ENCHANTED_HIT,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.5D,
                        pos.getZ() + 0.5D,
                        8,
                        0.28D,
                        0.28D,
                        0.28D,
                        0.04D);
            }
        }
    }

    private boolean isCrystalDashBreakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || this.isTrackableOre(state)) {
            return false;
        }
        if (!(state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.BASALT))) {
            return false;
        }
        return true;
    }

    private void finishCrystalDash() {
        this.crystalDashTicks = 0;
        this.crystalDashOreTarget = null;
        this.crystalDashBrokenBlocks = 0;
        this.utilityLockoutTicks = Math.max(this.utilityLockoutTicks, 10);
    }

    // ---------------------------------------------------------------------
    // 5. Geo Resonance (ultimate)
    // ---------------------------------------------------------------------

    private boolean tryStartGeoResonance(ServerLevel level) {
        if (!this.canUseGemUtility()
                || this.isCoolingDown(ModMemoryModuleTypes.GEM_GEO_RESONANCE_COOLDOWN.get())
                || this.hasPackActiveGeoResonance()) {
            return false;
        }

        ServerPlayer owner = this.getGemOwner();
        if (owner == null || owner.distanceToSqr(this) > 16.0D * 16.0D) {
            return false;
        }

        // Keep the ultimate mining-oriented instead of firing every time the player
        // walks over ore on the surface.
        if (level.canSeeSky(owner.blockPosition())) {
            return false;
        }

        List<OreTarget> targets = this.findTopOreTargets(
                level,
                owner.blockPosition(),
                (int) GEO_RESONANCE_RADIUS,
                20,
                12);
        if (targets.size() < 4) {
            return false;
        }

        this.resonanceTargets.clear();
        for (OreTarget target : targets) {
            this.resonanceTargets.add(target.pos);
        }
        this.geoResonanceTicks = GEO_RESONANCE_DURATION_TICKS;
        this.setPackCooldown(ModMemoryModuleTypes.GEM_GEO_RESONANCE_COOLDOWN.get(), GEO_RESONANCE_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(50);

        this.sendGemRing(level, owner.position(), 3.0D, 36, ParticleTypes.ELECTRIC_SPARK);
        this.sendGemRing(level, owner.position(), 5.0D, 48, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    private void tickGeoResonance(ServerLevel level) {
        --this.geoResonanceTicks;
        ServerPlayer owner = this.getGemOwner();
        if (owner == null) {
            this.finishGeoResonance();
            return;
        }

        this.resonanceTargets.removeIf(pos -> !this.isTrackableOre(level.getBlockState(pos)));
        if (this.resonanceTargets.isEmpty()) {
            this.finishGeoResonance();
            return;
        }

        if (this.tickCount % 5 == 0) {
            double radius = 2.5D + 1.3D * (0.5D + 0.5D * Math.sin(this.tickCount * 0.25D));
            this.sendGemRing(level, owner.position(), radius, 24, ParticleTypes.ELECTRIC_SPARK);
        }

        if (this.isWolfismWorkTick(10)) {
            Vec3 source = owner.position().add(0.0D, 1.05D, 0.0D);
            int shown = Math.min(12, this.resonanceTargets.size());
            for (int i = 0; i < shown; ++i) {
                BlockPos ore = this.resonanceTargets.get(i);
                this.sendDirectionalTrace(
                        level,
                        source,
                        ore,
                        2.0D + (i % 4) * 0.45D,
                        5,
                        this.getOrePriority(level.getBlockState(ore)) >= 6
                                ? ParticleTypes.END_ROD
                                : ParticleTypes.ELECTRIC_SPARK);
            }
        }

        if (this.geoResonanceTicks <= 0) {
            this.finishGeoResonance();
        }
    }

    private void finishGeoResonance() {
        this.geoResonanceTicks = 0;
        this.resonanceTargets.clear();
        this.utilityLockoutTicks = Math.max(this.utilityLockoutTicks, 20);
    }

    public boolean isGeoResonanceActive() {
        return this.geoResonanceTicks > 0;
    }

    // ---------------------------------------------------------------------
    // Ore scan / pack / visual helpers
    // ---------------------------------------------------------------------

    private List<OreTarget> findTopOreTargets(
            ServerLevel level,
            BlockPos origin,
            int horizontal,
            int vertical,
            int limit) {
        List<OreTarget> best = new ArrayList<>();
        double horizontalSqr = (double) horizontal * horizontal;

        for (int dx = -horizontal; dx <= horizontal; ++dx) {
            for (int dz = -horizontal; dz <= horizontal; ++dz) {
                if ((double) dx * dx + (double) dz * dz > horizontalSqr) {
                    continue;
                }
                for (int dy = -vertical; dy <= vertical; ++dy) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!this.isTrackableOre(state)) {
                        continue;
                    }

                    int priority = this.getOrePriority(state);
                    if (limit > 1 && best.stream().anyMatch(existing ->
                            existing.priority == priority && existing.pos.distSqr(pos) <= 9.0D)) {
                        continue;
                    }

                    OreTarget candidate = new OreTarget(
                            pos.immutable(),
                            priority,
                            pos.distSqr(origin));

                    if (best.size() < limit) {
                        best.add(candidate);
                        continue;
                    }

                    int worstIndex = 0;
                    for (int i = 1; i < best.size(); ++i) {
                        if (this.compareOreTargets(best.get(i), best.get(worstIndex)) < 0) {
                            worstIndex = i;
                        }
                    }
                    if (this.compareOreTargets(candidate, best.get(worstIndex)) > 0) {
                        best.set(worstIndex, candidate);
                    }
                }
            }
        }

        best.sort(Comparator
                .comparingInt((OreTarget target) -> target.priority).reversed()
                .thenComparingDouble(target -> target.distance));
        return best;
    }

    private int compareOreTargets(OreTarget a, OreTarget b) {
        int priority = Integer.compare(a.priority, b.priority);
        if (priority != 0) {
            return priority;
        }
        // For equal rarity, nearer is better.
        return Double.compare(b.distance, a.distance);
    }

    private boolean isTrackableOre(BlockState state) {
        return state.is(ModBlockTags.GEM_TRACKABLE_ORE);
    }

    private int getOrePriority(BlockState state) {
        if (state.is(BlockTags.DIAMOND_ORES)) {
            return 8;
        }
        if (state.is(BlockTags.EMERALD_ORES) || state.is(Blocks.ANCIENT_DEBRIS)) {
            return 7;
        }
        if (state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_CLUSTER)) {
            return 6;
        }
        if (state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.LAPIS_ORES)) {
            return 5;
        }
        if (state.is(BlockTags.REDSTONE_ORES)) {
            return 4;
        }
        if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)) {
            return 3;
        }
        if (state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.NETHER_GOLD_ORE)) {
            return 2;
        }
        if (state.is(BlockTags.COAL_ORES)) {
            return 1;
        }
        // Datapacks may extend wolfism:gem_trackable_ore with modded ores. Treat
        // unknown extensions as valuable rather than demoting them to coal tier.
        return 5;
    }

    private ServerPlayer getGemOwner() {
        return this.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    private boolean canUseGemUtility() {
        return this.isTame()
                && !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.crystalDashTicks <= 0
                && this.geoResonanceTicks <= 0
                && this.utilityLockoutTicks <= 0;
    }

    private boolean isCoolingDown(MemoryModuleType<Integer> memory) {
        return this.getBrain().getMemory(memory).orElse(0) > 0;
    }

    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int cooldown = this.getBrain().getMemory(memory).orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(memory, cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(memory);
        }
    }

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }

        for (GemWolf mate : level.getEntitiesOfClass(
                GemWolf.class,
                this.getBoundingBox().inflate(GemWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isGemPackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void applyPackUtilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.utilityLockoutTicks = Math.max(this.utilityLockoutTicks, ticks);
            return;
        }

        for (GemWolf mate : level.getEntitiesOfClass(
                GemWolf.class,
                this.getBoundingBox().inflate(GemWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isGemPackmate(wolf)))) {
            mate.utilityLockoutTicks = Math.max(mate.utilityLockoutTicks, ticks);
        }
    }

    private boolean hasPackActiveGeoResonance() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        GemWolf.class,
                        this.getBoundingBox().inflate(GemWolfPackSensor.PACK_SCAN_RADIUS),
                        wolf -> wolf != this && wolf.isAlive() && this.isGemPackmate(wolf))
                .stream()
                .anyMatch(GemWolf::isGeoResonanceActive);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level) || threat == null || !threat.isAlive()) {
            return;
        }
        for (GemWolf mate : level.getEntitiesOfClass(
                GemWolf.class,
                this.getBoundingBox().inflate(GemWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isGemPackmate(wolf)))) {
            if (mate.canAttack(threat)) {
                mate.getBrain().setMemory(ModMemoryModuleTypes.GEM_PACK_THREAT.get(), threat);
                if (!mate.isBaby()) {
                    mate.setTarget(threat);
                }
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    private void cancelGemUtility() {
        this.crystalDashTicks = 0;
        this.crystalDashOreTarget = null;
        this.crystalDashBrokenBlocks = 0;
        this.geoResonanceTicks = 0;
        this.resonanceTargets.clear();
    }


    private Component buildGemSenseReadout(ServerLevel level, ServerPlayer owner, BlockPos ore) {
        BlockState state = level.getBlockState(ore);
        Vec3 target = Vec3.atCenterOf(ore);
        Vec3 source = owner.position();

        double dx = target.x - source.x;
        double dz = target.z - source.z;
        int dy = ore.getY() - owner.blockPosition().getY();
        int distance = (int) Math.round(source.distanceTo(target));

        String horizontal = this.describeHorizontalDirection(dx, dz);
        String vertical = dy > 1 ? "UP " + dy : dy < -1 ? "DOWN " + (-dy) : "LEVEL";
        String oreName = state.getBlock().getName().getString();

        return Component.literal("Gem Sense  ◆  ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(oreName).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("  ◆  " + distance + " blocks  ◆  ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(horizontal).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  ◆  " + vertical).withStyle(
                        dy > 1 ? ChatFormatting.GREEN : dy < -1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    private String describeHorizontalDirection(double dx, double dz) {
        if (Math.abs(dx) < 1.25D && Math.abs(dz) < 1.25D) {
            return "HERE";
        }

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0.0D) {
            angle += 360.0D;
        }

        if (angle < 22.5D || angle >= 337.5D) {
            return "E";
        }
        if (angle < 67.5D) {
            return "SE";
        }
        if (angle < 112.5D) {
            return "S";
        }
        if (angle < 157.5D) {
            return "SW";
        }
        if (angle < 202.5D) {
            return "W";
        }
        if (angle < 247.5D) {
            return "NW";
        }
        if (angle < 292.5D) {
            return "N";
        }
        return "NE";
    }

    private void sendDirectionalTrace(
            ServerLevel level,
            Vec3 source,
            BlockPos targetPos,
            double visibleLength,
            int points,
            ParticleOptions particle) {
        Vec3 target = Vec3.atCenterOf(targetPos);
        Vec3 delta = target.subtract(source);
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }

        Vec3 direction = delta.normalize();
        double length = Math.min(visibleLength, Math.max(0.6D, delta.length()));
        for (int i = 1; i <= points; ++i) {
            double step = length * i / points;
            Vec3 point = source.add(direction.scale(step));
            WolfVfx.sendParticles("gem_wolf", level,
                    particle,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);
        }
    }

    private void sendGemRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("gem_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.12D,
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.01D,
                    0.02D,
                    0.01D,
                    0.0D);
        }
    }

    public static boolean checkGemWolfSpawnRules(
            EntityType<GemWolf> type, LevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());
        boolean mountainGround = ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                || ground.is(Blocks.STONE)
                || ground.is(Blocks.ANDESITE)
                || ground.is(Blocks.GRAVEL)
                || ground.is(Blocks.CALCITE);
        return mountainGround && isBrightEnoughToSpawn(level, pos);
    }
}