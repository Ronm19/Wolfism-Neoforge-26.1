package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.GoldenWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBlockTags;

/**
 * Golden Wolf: Wolfism's first dedicated resource / prosperity utility wolf.
 *
 * <p>The Golden Wolf does not generate valuables from nothing. It locates existing
 * valuable blocks, helps the owner excavate them, can improve legitimate resource
 * drops through Radiant Share / Blessing of Wealth, and protects the working pack
 * with Golden Barrier. This keeps the utility meaningful without becoming an
 * infinite-resource machine.</p>
 */
public final class GoldenWolf extends AbstractWolfismWolf {
    public static final int FORTUNE_DIG_COOLDOWN_TICKS = 20 * 18;
    public static final int RADIANT_SHARE_COOLDOWN_TICKS = 20 * 20;
    public static final int GOLDEN_BARRIER_COOLDOWN_TICKS = 20 * 25;
    public static final int BLESSING_OF_WEALTH_COOLDOWN_TICKS = 20 * 90;

    public static final int FORTUNE_DIG_DURATION_TICKS = 20 * 4;
    public static final int RADIANT_SHARE_DURATION_TICKS = 20 * 8;
    public static final int GOLDEN_BARRIER_DURATION_TICKS = 20 * 6;
    public static final int BLESSING_OF_WEALTH_DURATION_TICKS = 20 * 10;

    public static final double RESOURCE_SCAN_HORIZONTAL = 16.0D;
    public static final int RESOURCE_SCAN_VERTICAL = 10;
    public static final double OWNER_UTILITY_RADIUS = 18.0D;
    public static final double FAMILY_SUPPORT_RADIUS = 10.0D;
    public static final double RESOURCE_EVENT_RADIUS = 16.0D;

    public static final double RADIANT_SHARE_BONUS_CHANCE = 0.25D;
    public static final double BLESSING_BONUS_CHANCE = 0.60D;

    private static final int RESOURCE_SCAN_INTERVAL = 80;
    private static final int UTILITY_DECISION_INTERVAL = 10;
    private static final int PASSIVE_LUCK_REFRESH_INTERVAL = 40;
    private static final int SHINY_PRESENCE_INTERVAL = 8;
    private static final int PACK_UTILITY_LOCKOUT_TICKS = 30;

    private static final EntityDataAccessor<Boolean> DATA_FORTUNE_DIG_ACTIVE =
            SynchedEntityData.defineId(GoldenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RADIANT_SHARE_ACTIVE =
            SynchedEntityData.defineId(GoldenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GOLDEN_BARRIER_ACTIVE =
            SynchedEntityData.defineId(GoldenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BLESSING_ACTIVE =
            SynchedEntityData.defineId(GoldenWolf.class, EntityDataSerializers.BOOLEAN);

    private int fortuneDigTicks;
    private int radiantShareTicks;
    private int goldenBarrierTicks;
    private int blessingTicks;
    private int packUtilityLockoutTicks;
    private BlockPos fortuneDigTarget;

    private static final class BrainHolder {
        private static final Brain.Provider<GoldenWolf> PROVIDER = Brain.<GoldenWolf>provider(
                ImmutableList.of(ModSensorTypes.GOLDEN_PACK.get()),
                wolf -> List.of());
    }

    public GoldenWolf(EntityType<? extends GoldenWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_FORTUNE_DIG_ACTIVE, false);
        data.define(DATA_RADIANT_SHARE_ACTIVE, false);
        data.define(DATA_GOLDEN_BARRIER_ACTIVE, false);
        data.define(DATA_BLESSING_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.GOLDEN_WOLF.get();
    }

    @Override
    protected Brain<GoldenWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<GoldenWolf> getBrain() {
        return (Brain<GoldenWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof GoldenWolf golden && this.isGoldenPackmate(golden)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.GOLDEN_FORTUNE_DIG_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.GOLDEN_RADIANT_SHARE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.GOLDEN_BARRIER_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.GOLDEN_BLESSING_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelActiveUtilityStates();
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.GOLDEN_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidGoldenCombatTarget(sharedThreat)) {
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

        if (this.packUtilityLockoutTicks > 0) {
            --this.packUtilityLockoutTicks;
        }

        this.tickShiningPresence(level);
        this.tickAbilityTimers(level);

        if (this.isBaby()) {
            return;
        }

        if (this.tickCount % RESOURCE_SCAN_INTERVAL == 0) {
            this.updateGoldenScent(level);
        }
        if (this.tickCount % PASSIVE_LUCK_REFRESH_INTERVAL == 0) {
            this.refreshOwnerProsperity();
        }
        if (this.tickCount % 20 == 0) {
            this.tickGoldenScentVisual(level);
        }

        if (this.tickCount % UTILITY_DECISION_INTERVAL == 0) {
            this.tryGoldenBarrier(level);
            this.tryFortuneDig(level);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof GoldenWolf golden && this.isGoldenPackmate(golden)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isGoldenPackmate(GoldenWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isGoldenFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity == this) {
            return true;
        }
        if (this.isTame()) {
            if (entity == this.getOwner()) {
                return true;
            }
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof GoldenWolf golden && this.isGoldenPackmate(golden);
    }

    public boolean isValidGoldenCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof GoldenWolf golden && this.isGoldenPackmate(golden)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    // ---------------------------------------------------------------------
    // Golden Scent + Shining Presence
    // ---------------------------------------------------------------------

    /**
     * Searches real blocks only. Golden Scent never invents an ore, chest, or item;
     * it simply remembers the nearest block classified as golden_valuable.
     */
    private void updateGoldenScent(ServerLevel level) {
        if (!this.isTame()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get());
            return;
        }

        BlockPos origin = this.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int bestPriority = Integer.MIN_VALUE;
        int horizontal = (int) RESOURCE_SCAN_HORIZONTAL;

        for (int dx = -horizontal; dx <= horizontal; ++dx) {
            for (int dz = -horizontal; dz <= horizontal; ++dz) {
                if (dx * dx + dz * dz > horizontal * horizontal) {
                    continue;
                }
                for (int dy = -RESOURCE_SCAN_VERTICAL; dy <= RESOURCE_SCAN_VERTICAL; ++dy) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(candidate);
                    if (!state.is(ModBlockTags.GOLDEN_VALUABLE)) {
                        continue;
                    }

                    int priority = this.getGoldenResourcePriority(state);
                    double distance = candidate.distSqr(origin);
                    // Rare valuables outrank common coal/iron even when the common
                    // resource is closer. Within the same tier, nearest wins.
                    if (priority > bestPriority || (priority == bestPriority && distance < nearestDistance)) {
                        bestPriority = priority;
                        nearestDistance = distance;
                        nearest = candidate.immutable();
                    }
                }
            }
        }

        if (nearest != null) {
            this.getBrain().setMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get(), nearest);
        } else {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get());
        }
    }

    private int getGoldenResourcePriority(BlockState state) {
        if (state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)) {
            return 4;
        }
        if (state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
            return 3;
        }
        if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)) {
            return 2;
        }
        return 1;
    }

    private void refreshOwnerProsperity() {
        if (!(this.getOwner() instanceof ServerPlayer owner)
                || owner.distanceToSqr(this) > OWNER_UTILITY_RADIUS * OWNER_UTILITY_RADIUS) {
            return;
        }

        // Luck improves loot-table rolls. Ore/resource doubling itself remains owned
        // by Radiant Share / Blessing, so the passive never becomes the whole kit.
        int amplifier = this.isBlessingOfWealthActive() ? 1 : 0;
        owner.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 6, amplifier));
        if (this.isBlessingOfWealthActive()) {
            owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 20 * 4, 1));
        }
    }

    private void tickGoldenScentVisual(ServerLevel level) {
        BlockPos resource = this.getBrain().getMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get()).orElse(null);
        if (resource == null || !(this.getOwner() instanceof ServerPlayer owner)
                || owner.distanceToSqr(this) > OWNER_UTILITY_RADIUS * OWNER_UTILITY_RADIUS) {
            return;
        }

        Vec3 direction = Vec3.atCenterOf(resource).subtract(this.position());
        if (direction.lengthSqr() < 0.01D) {
            return;
        }
        direction = direction.normalize();
        Vec3 marker = this.position().add(direction.scale(1.15D)).add(0.0D, 0.55D, 0.0D);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                marker.x, marker.y, marker.z, 2, 0.08D, 0.08D, 0.08D, 0.01D);
    }

    private void tickShiningPresence(ServerLevel level) {
        if (this.tickCount % SHINY_PRESENCE_INTERVAL != 0) {
            return;
        }

        int count = this.isBaby() ? 1 : (this.isBlessingOfWealthActive() ? 5 : 2);
        double spread = this.isBlessingOfWealthActive() ? 0.55D : 0.30D;
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                this.getX(), this.getY() + this.getBbHeight() * 0.58D, this.getZ(),
                count, spread, 0.32D, spread, 0.01D);

        if (!this.isBaby() && (this.isRadiantShareActive() || this.isBlessingOfWealthActive())) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(), this.getY() + this.getBbHeight() * 0.72D, this.getZ(),
                    this.isBlessingOfWealthActive() ? 3 : 1,
                    0.28D, 0.25D, 0.28D, 0.005D);
        }
    }

    // ---------------------------------------------------------------------
    // Fortune Dig
    // ---------------------------------------------------------------------

    private void tryFortuneDig(ServerLevel level) {
        if (!this.canUseResourceAbility()
                || this.packUtilityLockoutTicks > 0
                || this.isRadiantShareActive()
                || this.isBlessingOfWealthActive()
                || this.isCoolingDown(ModMemoryModuleTypes.GOLDEN_FORTUNE_DIG_COOLDOWN.get())) {
            return;
        }
        if (!(this.getOwner() instanceof ServerPlayer owner)
                || owner.distanceToSqr(this) > OWNER_UTILITY_RADIUS * OWNER_UTILITY_RADIUS) {
            return;
        }

        BlockPos resource = this.getBrain().getMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get()).orElse(null);
        if (resource == null || !level.getBlockState(resource).is(ModBlockTags.GOLDEN_VALUABLE)) {
            return;
        }
        if (this.position().distanceToSqr(Vec3.atCenterOf(resource)) > 12.0D * 12.0D) {
            return;
        }

        this.fortuneDigTarget = resource.immutable();
        this.fortuneDigTicks = FORTUNE_DIG_DURATION_TICKS;
        this.entityData.set(DATA_FORTUNE_DIG_ACTIVE, true);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.GOLDEN_FORTUNE_DIG_COOLDOWN.get(),
                FORTUNE_DIG_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(PACK_UTILITY_LOCKOUT_TICKS);
        this.getNavigation().stop();
        owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 20 * 6, 1));
        this.sendGoldenRing(level, this.position(), 2.2D, 20, ParticleTypes.TOTEM_OF_UNDYING);
    }

    private void tickFortuneDig(ServerLevel level) {
        if (this.fortuneDigTarget == null || !level.getBlockState(this.fortuneDigTarget).is(ModBlockTags.GOLDEN_VALUABLE)) {
            this.finishFortuneDig();
            return;
        }

        this.getNavigation().stop();
        this.getLookControl().setLookAt(
                this.fortuneDigTarget.getX() + 0.5D,
                this.fortuneDigTarget.getY() + 0.5D,
                this.fortuneDigTarget.getZ() + 0.5D,
                90.0F,
                90.0F);

        if (this.tickCount % 5 == 0) {
            level.sendParticles(
                    ParticleTypes.POOF,
                    this.getX(), this.getY() + 0.08D, this.getZ(),
                    3, 0.30D, 0.05D, 0.30D, 0.01D);
        }
        if (this.tickCount % 20 == 0) {
            this.sendResourceBreadcrumb(level, this.fortuneDigTarget);
        }
    }

    private void sendResourceBreadcrumb(ServerLevel level, BlockPos resource) {
        Vec3 start = this.position().add(0.0D, 0.45D, 0.0D);
        Vec3 end = Vec3.atCenterOf(resource);
        Vec3 delta = end.subtract(start);
        double length = Math.min(delta.length(), 8.0D);
        if (length < 0.1D) {
            return;
        }
        Vec3 direction = delta.normalize();
        int points = Math.max(4, (int) (length * 2.0D));
        for (int i = 1; i <= points; ++i) {
            Vec3 point = start.add(direction.scale((length * i) / points));
            level.sendParticles(
                    i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.TOTEM_OF_UNDYING,
                    point.x, point.y, point.z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
    }

    private void finishFortuneDig() {
        this.fortuneDigTicks = 0;
        this.fortuneDigTarget = null;
        this.entityData.set(DATA_FORTUNE_DIG_ACTIVE, false);
    }

    // ---------------------------------------------------------------------
    // Radiant Share + Blessing of Wealth
    // ---------------------------------------------------------------------

    /** Called by GoldenWolfResourceEvents after a real valuable block produced drops. */
    public void onValuableResourceHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (this.isBaby() || !this.isTame()) {
            return;
        }

        BlockPos remembered = this.getBrain().getMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get()).orElse(null);
        if (remembered != null && remembered.equals(pos)) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.GOLDEN_RESOURCE_POS.get());
        }

        if (this.isRareBlessingResource(state)
                && !this.isBlessingOfWealthActive()
                && !this.isCoolingDown(ModMemoryModuleTypes.GOLDEN_BLESSING_COOLDOWN.get())
                && !this.hasPackActiveBlessing()) {
            this.startBlessingOfWealth(level);
            return;
        }

        if (!this.isRadiantShareActive()
                && !this.isBlessingOfWealthActive()
                && !this.isCoolingDown(ModMemoryModuleTypes.GOLDEN_RADIANT_SHARE_COOLDOWN.get())
                && !this.hasPackActiveRadiantShare()) {
            this.startRadiantShare(level);
        }
    }

    private void startRadiantShare(ServerLevel level) {
        this.finishFortuneDig();
        this.radiantShareTicks = RADIANT_SHARE_DURATION_TICKS;
        this.entityData.set(DATA_RADIANT_SHARE_ACTIVE, true);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.GOLDEN_RADIANT_SHARE_COOLDOWN.get(),
                RADIANT_SHARE_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(PACK_UTILITY_LOCKOUT_TICKS);
        this.sendGoldenRing(level, this.position(), 3.5D, 28, ParticleTypes.TOTEM_OF_UNDYING);
    }

    private void tickRadiantShare(ServerLevel level) {
        if (this.tickCount % 10 == 0) {
            this.sendGoldenRing(level, this.position(), 2.4D, 16, ParticleTypes.TOTEM_OF_UNDYING);
        }
    }

    private void startBlessingOfWealth(ServerLevel level) {
        this.finishFortuneDig();
        this.radiantShareTicks = 0;
        this.entityData.set(DATA_RADIANT_SHARE_ACTIVE, false);
        this.blessingTicks = BLESSING_OF_WEALTH_DURATION_TICKS;
        this.entityData.set(DATA_BLESSING_ACTIVE, true);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.GOLDEN_BLESSING_COOLDOWN.get(),
                BLESSING_OF_WEALTH_COOLDOWN_TICKS);
        this.applyPackUtilityLockout(20 * 3);

        for (LivingEntity member : this.findFamily(FAMILY_SUPPORT_RADIUS)) {
            member.heal(3.0F);
        }
        this.refreshOwnerProsperity();
        this.sendGoldenRing(level, this.position(), 4.0D, 32, ParticleTypes.TOTEM_OF_UNDYING);
        this.sendGoldenRing(level, this.position(), 7.0D, 44, ParticleTypes.END_ROD);
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                this.getX(), this.getY() + 1.0D, this.getZ(),
                45, 1.0D, 1.0D, 1.0D, 0.08D);
    }

    private void tickBlessingOfWealth(ServerLevel level) {
        if (this.tickCount % 10 == 0) {
            this.refreshOwnerProsperity();
            this.sendGoldenRing(level, this.position(), 4.5D, 24, ParticleTypes.TOTEM_OF_UNDYING);
        }
        if (this.tickCount % 4 == 0) {
            double y = this.getY() + 0.25D + this.random.nextDouble() * 2.8D;
            level.sendParticles(
                    this.tickCount % 8 == 0 ? ParticleTypes.END_ROD : ParticleTypes.TOTEM_OF_UNDYING,
                    this.getX(), y, this.getZ(),
                    2, 0.45D, 0.05D, 0.45D, 0.01D);
        }
    }

    public double getResourceBonusChance() {
        if (this.isBlessingOfWealthActive()) {
            return BLESSING_BONUS_CHANCE;
        }
        if (this.isRadiantShareActive()) {
            return RADIANT_SHARE_BONUS_CHANCE;
        }
        return 0.0D;
    }

    public void emitResourceBonusVisual(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                center.x, center.y + 0.20D, center.z,
                this.isBlessingOfWealthActive() ? 18 : 10,
                0.35D, 0.35D, 0.35D, 0.04D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                center.x, center.y + 0.25D, center.z,
                this.isBlessingOfWealthActive() ? 6 : 3,
                0.20D, 0.22D, 0.20D, 0.01D);
    }

    private boolean isRareBlessingResource(BlockState state) {
        return state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    // ---------------------------------------------------------------------
    // Golden Barrier
    // ---------------------------------------------------------------------

    private void tryGoldenBarrier(ServerLevel level) {
        if (this.isCoolingDown(ModMemoryModuleTypes.GOLDEN_BARRIER_COOLDOWN.get())
                || this.isGoldenBarrierActive()
                || this.hasPackActiveGoldenBarrier()) {
            return;
        }

        LivingEntity critical = this.findFamily(FAMILY_SUPPORT_RADIUS).stream()
                .filter(member -> member.getHealth() <= member.getMaxHealth() * 0.45F)
                .min(Comparator.comparingDouble(member -> member.getHealth() / member.getMaxHealth()))
                .orElse(null);
        if (critical == null || this.countImmediateThreats(FAMILY_SUPPORT_RADIUS) == 0) {
            return;
        }

        this.goldenBarrierTicks = GOLDEN_BARRIER_DURATION_TICKS;
        this.entityData.set(DATA_GOLDEN_BARRIER_ACTIVE, true);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.GOLDEN_BARRIER_COOLDOWN.get(),
                GOLDEN_BARRIER_COOLDOWN_TICKS);

        for (LivingEntity member : this.findFamily(FAMILY_SUPPORT_RADIUS)) {
            member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, GOLDEN_BARRIER_DURATION_TICKS + 20, 0));
            member.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, GOLDEN_BARRIER_DURATION_TICKS + 40, 1));
        }
        this.sendGoldenRing(level, this.position(), 5.0D, 36, ParticleTypes.END_ROD);
        this.sendGoldenRing(level, this.position(), 9.0D, 52, ParticleTypes.TOTEM_OF_UNDYING);
    }

    private void tickGoldenBarrier(ServerLevel level) {
        if (this.tickCount % 12 == 0) {
            this.sendGoldenRing(level, this.position(), 6.0D, 28, ParticleTypes.END_ROD);
        }
    }

    private int countImmediateThreats(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isImmediateGoldenThreat).size();
    }

    private boolean isImmediateGoldenThreat(LivingEntity candidate) {
        if (candidate instanceof Creeper || !this.isValidGoldenCombatTarget(candidate)) {
            return false;
        }
        if (candidate instanceof Enemy) {
            return true;
        }
        if (candidate == this.getTarget() || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.GOLDEN_PACK_THREAT.get())
                .orElse(null);
        if (candidate == shared) {
            return true;
        }
        if (candidate instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            return mobTarget != null && this.isGoldenFamilyMember(mobTarget);
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Shared helpers / state
    // ---------------------------------------------------------------------

    private List<LivingEntity> findFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isGoldenFamilyMember);
    }

    private boolean canUseResourceAbility() {
        return !this.isBaby()
                && this.isTame()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.hasFamilyDefenseEmergency()
                && this.getTarget() == null;
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

    private void tickAbilityTimers(ServerLevel level) {
        if (this.fortuneDigTicks > 0) {
            this.tickFortuneDig(level);
            if (--this.fortuneDigTicks <= 0) {
                this.finishFortuneDig();
            }
        }
        if (this.radiantShareTicks > 0) {
            this.tickRadiantShare(level);
            if (--this.radiantShareTicks <= 0) {
                this.radiantShareTicks = 0;
                this.entityData.set(DATA_RADIANT_SHARE_ACTIVE, false);
            }
        }
        if (this.goldenBarrierTicks > 0) {
            this.tickGoldenBarrier(level);
            if (--this.goldenBarrierTicks <= 0) {
                this.goldenBarrierTicks = 0;
                this.entityData.set(DATA_GOLDEN_BARRIER_ACTIVE, false);
            }
        }
        if (this.blessingTicks > 0) {
            this.tickBlessingOfWealth(level);
            if (--this.blessingTicks <= 0) {
                this.blessingTicks = 0;
                this.entityData.set(DATA_BLESSING_ACTIVE, false);
            }
        }
    }

    private boolean hasPackActiveRadiantShare() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        GoldenWolf.class,
                        this.getBoundingBox().inflate(GoldenWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isGoldenPackmate(mate))
                .stream()
                .anyMatch(GoldenWolf::isRadiantShareActive);
    }

    private boolean hasPackActiveBlessing() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        GoldenWolf.class,
                        this.getBoundingBox().inflate(GoldenWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isGoldenPackmate(mate))
                .stream()
                .anyMatch(GoldenWolf::isBlessingOfWealthActive);
    }

    private boolean hasPackActiveGoldenBarrier() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        GoldenWolf.class,
                        this.getBoundingBox().inflate(GoldenWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isGoldenPackmate(mate))
                .stream()
                .anyMatch(GoldenWolf::isGoldenBarrierActive);
    }

    private void applyPackUtilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.packUtilityLockoutTicks = Math.max(this.packUtilityLockoutTicks, ticks);
            return;
        }
        for (GoldenWolf mate : level.getEntitiesOfClass(
                GoldenWolf.class,
                this.getBoundingBox().inflate(GoldenWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isGoldenPackmate(wolf)))) {
            mate.packUtilityLockoutTicks = Math.max(mate.packUtilityLockoutTicks, ticks);
        }
    }

    private void sendGoldenRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.16D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidGoldenCombatTarget(threat)) {
            return;
        }
        for (GoldenWolf mate : level.getEntitiesOfClass(
                GoldenWolf.class,
                this.getBoundingBox().inflate(GoldenWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isGoldenPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.GOLDEN_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidGoldenCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.finishFortuneDig();
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    private void cancelActiveUtilityStates() {
        this.fortuneDigTicks = 0;
        this.radiantShareTicks = 0;
        this.goldenBarrierTicks = 0;
        this.blessingTicks = 0;
        this.fortuneDigTarget = null;
        this.entityData.set(DATA_FORTUNE_DIG_ACTIVE, false);
        this.entityData.set(DATA_RADIANT_SHARE_ACTIVE, false);
        this.entityData.set(DATA_GOLDEN_BARRIER_ACTIVE, false);
        this.entityData.set(DATA_BLESSING_ACTIVE, false);
    }

    public boolean isFortuneDigActive() {
        return this.entityData.get(DATA_FORTUNE_DIG_ACTIVE);
    }

    public boolean isRadiantShareActive() {
        return this.entityData.get(DATA_RADIANT_SHARE_ACTIVE);
    }

    public boolean isGoldenBarrierActive() {
        return this.entityData.get(DATA_GOLDEN_BARRIER_ACTIVE);
    }

    public boolean isBlessingOfWealthActive() {
        return this.entityData.get(DATA_BLESSING_ACTIVE);
    }

    public static boolean checkGoldenWolfSpawnRules(
            EntityType<GoldenWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());
        boolean validGround = ground.is(Blocks.SAND)
                || ground.is(Blocks.RED_SAND)
                || ground.is(BlockTags.WOLVES_SPAWNABLE_ON);
        return validGround && isBrightEnoughToSpawn(level, pos);
    }
}
