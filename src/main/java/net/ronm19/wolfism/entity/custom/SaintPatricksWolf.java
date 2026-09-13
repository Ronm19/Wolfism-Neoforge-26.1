package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.SaintPatricksWolfTreasureGoal;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #3: Saint Patrick's Wolf.
 *
 * <p>Its niche is short, controlled bursts of improbable good fortune rather
 * than Golden Wolf's broad permanent resource utility. Four-Leaf Sense points
 * at nearby existing treasure; Lucky Presence improves ordinary loot luck;
 * Pot of Gold creates a fixed fortune field; Lucky Break protects endangered
 * family; and Fortune's Favor temporarily pushes every capped luck mechanic to
 * its strongest level.</p>
 */
public final class SaintPatricksWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_RADIUS = 14.0D;
    public static final double THREAT_RADIUS = 24.0D;
    public static final double LUCKY_PRESENCE_RADIUS = 12.0D;
    public static final double LUCKY_BREAK_RADIUS = 14.0D;
    public static final double FORTUNE_RADIUS = 14.0D;
    public static final double POT_RADIUS = 7.0D;
    public static final double POT_VERTICAL_RADIUS = 3.0D;
    public static final double LUCKY_FIND_RADIUS = 18.0D;

    public static final int TREASURE_SCAN_HORIZONTAL = 18;
    public static final int TREASURE_SCAN_VERTICAL = 8;

    private static final double BASE_HEALTH = 38.0D;
    private static final int POT_DURATION = 20 * 10;
    private static final int POT_COOLDOWN = 20 * 38;
    private static final int FORTUNE_DURATION = 20 * 14;
    private static final int FORTUNE_COOLDOWN = 20 * 65;
    private static final int LUCKY_PRESENCE_REFRESH = 20 * 5;
    private static final int TREASURE_HINT_INTERVAL = 20 * 2;

    private static final long LUCKY_BREAK_COOLDOWN = 20L * 45L;
    private static final long LUCKY_BREAK_PITY_WINDOW = 20L * 20L;
    private static final int LUCKY_BREAK_MAX_RECENT_MISSES = 3;
    private static final float LUCKY_BREAK_PITY_PER_MISS = 0.10F;
    private static final float LUCKY_BREAK_LETHAL_BONUS = 0.10F;
    private static final float LUCKY_BREAK_MAX_CHANCE = 0.85F;

    private static final long FORTUNE_DODGE_COOLDOWN = 20L;

    private static final String NEXT_LUCKY_BREAK = "WolfismSaintPatricksNextLuckyBreak";
    private static final String LUCKY_BREAK_MISS_STREAK =
            "WolfismSaintPatricksLuckyBreakMissStreak";
    private static final String LUCKY_BREAK_LAST_MISS =
            "WolfismSaintPatricksLuckyBreakLastMiss";
    private static final String NEXT_FORTUNE_DODGE = "WolfismSaintPatricksNextFortuneDodge";

    private static final EntityDataAccessor<Boolean> DATA_FORTUNE_ACTIVE =
            SynchedEntityData.defineId(SaintPatricksWolf.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions SHAMROCK_GREEN =
            new DustParticleOptions(0x39C968, 1.05F);
    private static final DustParticleOptions LUCKY_GOLD =
            new DustParticleOptions(0xFFD45C, 1.1F);

    private int potTicks;
    private int potCooldown;
    private int fortuneTicks;
    private int fortuneCooldown;
    private int potPulseDelay;
    private int potVisualDelay;
    private int treasureHintDelay;
    private int treasureAnnouncementCooldown;
    private Vec3 potCenter;

    // Read-only diagnostics exposed through /data get.
    private int luckyPresencePulses;
    private int luckyBiteProcs;
    private int potCastCount;
    private int potPulseCount;
    private int fortuneCastCount;
    private int fortunePulseCount;
    private int luckyBreakCount;
    private int luckyBreakEligibleRolls;
    private int luckyBreakFailedRolls;
    private int luckyBreakLethalSaves;
    private int lastLuckyBreakChancePercent;
    private int fortuneDodgeCount;
    private int luckyFindCount;

    private static final class BrainHolder {
        private static final Brain.Provider<SaintPatricksWolf> PROVIDER =
                Brain.<SaintPatricksWolf>provider(
                        List.of(ModSensorTypes.SAINT_PATRICKS_AWARENESS.get()),
                        wolf -> List.of());
    }

    public SaintPatricksWolf(EntityType<? extends SaintPatricksWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SAINT_PATRICKS_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        /*
         * Guidance outranks ordinary FollowOwner/random strolling so it can
         * actually begin instead of waiting for unrelated navigation to end.
         * Panic/sit/leap still have higher priority, and the goal's own target
         * and Wolf Staff checks make combat/explicit commands win immediately.
         */
        this.goalSelector.addGoal(5, new SaintPatricksWolfTreasureGoal(this));
    }

    @Override
    protected Brain<SaintPatricksWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SaintPatricksWolf> getBrain() {
        return (Brain<SaintPatricksWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORTUNE_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 42.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            float previous = this.getHealth();
            health.setBaseValue(BASE_HEALTH);
            this.setHealth(this.isTame()
                    ? this.getMaxHealth()
                    : Math.min(previous, this.getMaxHealth()));
        }
    }

    @Override
    protected boolean isHolidaySeasonActive(LocalDate date) {
        return date.getMonthValue() == 3;
    }

    /** Full March; the shared gamerule keeps development possible year-round. */
    public static boolean isSaintPatricksSeasonOpen(ServerLevel level) {
        return level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())
                || LocalDate.now().getMonthValue() == 3;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()
                && !this.isAngry()
                && !this.isHolidayRecovering()
                && stack.is(Items.EMERALD)) {
            if (this.level() instanceof ServerLevel level) {
                stack.consume(1, player);
                // Emeralds are valuable: preferred taming succeeds 75% of the time.
                if (this.random.nextInt(4) != 0 && !EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    this.setOrderedToSit(true);
                    level.broadcastEntityEvent(this, (byte) 7);
                    this.luckyBurst(level, 18);
                    this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55F, 1.35F);
                } else {
                    level.broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Bones, meat, dyes, armor, breeding and ordinary wolf interactions remain.
        return super.mobInteract(player, hand);
    }

    public boolean canProvideSaintPatricksSupport() {
        return this.isAlive()
                && !this.isRemoved()
                && !this.isBaby()
                && !this.isNoAi()
                && !this.isHolidayRecovering()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isFortuneVisualActive() {
        return this.entityData.get(DATA_FORTUNE_ACTIVE);
    }

    public boolean isFortunesFavorActive() {
        return this.fortuneTicks > 0 && this.canProvideSaintPatricksSupport();
    }

    public boolean isPotOfGoldActive() {
        return this.potTicks > 0
                && this.potCenter != null
                && this.canProvideSaintPatricksSupport();
    }

    public boolean isSaintPatricksFamily(LivingEntity entity) {
        if (entity == this) return true;
        if (entity == null
                || !entity.isAlive()
                || entity.isRemoved()
                || entity.level() != this.level()
                || !this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;

        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            LivingEntity petOwner = pet.getOwner();
            return petOwner != null && petOwner.getUUID().equals(owner.getUUID());
        }

        return false;
    }

    public boolean isLuckRecipient(LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && this.isSaintPatricksFamily(entity)
                && !(entity instanceof AbstractWolfismHolidayWolf holiday
                && holiday.isHolidayRecovering());
    }

    public List<LivingEntity> getSaintPatricksFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        if (!this.isHolidayRecovering()) family.add(this);
        family.addAll(level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                member -> member != this
                        && this.isLuckRecipient(member)
                        && this.distanceToSqr(member) <= radius * radius));
        return family;
    }

    public boolean isSaintPatricksThreat(LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == this
                || entity.isSpectator()
                || this.isSaintPatricksFamily(entity)
                || this.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof Enemy
                || entity == this.getTarget()
                || entity instanceof Mob mob && this.isSaintPatricksFamily(mob.getTarget());
    }

    public List<LivingEntity> getSaintPatricksThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THREAT_RADIUS),
                entity -> this.isSaintPatricksThreat(entity)
                        && this.distanceToSqr(entity) <= THREAT_RADIUS * THREAT_RADIUS
                        && this.getSensing().hasLineOfSight(entity));
    }

    public double criticalFamilyScore(LivingEntity member) {
        double healthFraction = member.getHealth() / Math.max(1.0F, member.getMaxHealth());
        double score = (1.0D - healthFraction) * 100.0D;
        if (member.hurtTime > 0) score += 12.0D;
        if (member == this.getOwner() && healthFraction < 0.45D) score += 18.0D;
        return score;
    }

    /**
     * Limited-range treasure awareness. It only sees a small curated set of
     * existing treasure containers and valuable blocks and never generates loot.
     */
    public BlockPos findFourLeafTreasure(ServerLevel level) {
        if (!this.isTame() || this.isHolidayRecovering()) return null;

        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        int bestPriority = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

        for (int dx = -TREASURE_SCAN_HORIZONTAL; dx <= TREASURE_SCAN_HORIZONTAL; ++dx) {
            for (int dz = -TREASURE_SCAN_HORIZONTAL; dz <= TREASURE_SCAN_HORIZONTAL; ++dz) {
                if (dx * dx + dz * dz
                        > TREASURE_SCAN_HORIZONTAL * TREASURE_SCAN_HORIZONTAL) {
                    continue;
                }

                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;

                for (int dy = -TREASURE_SCAN_VERTICAL; dy <= TREASURE_SCAN_VERTICAL; ++dy) {
                    scan.set(x, origin.getY() + dy, z);
                    BlockState state = level.getBlockState(scan);
                    int priority = treasurePriority(state);
                    if (priority <= 0) continue;

                    double distance = scan.distSqr(origin);
                    if (priority > bestPriority
                            || priority == bestPriority && distance < bestDistance) {
                        bestPriority = priority;
                        bestDistance = distance;
                        best = scan.immutable();
                    }
                }
            }
        }

        return best;
    }

    public static boolean isFourLeafTreasure(BlockState state) {
        return treasurePriority(state) > 0;
    }

    private static int treasurePriority(BlockState state) {
        if (state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL)) {
            return 5;
        }
        if (state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
            return 4;
        }
        if (state.is(BlockTags.EMERALD_ORES)) return 3;
        if (state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.DIAMOND_ORES)) return 2;
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;

        tickCooldowns();

        if (this.treasureAnnouncementCooldown > 0) --this.treasureAnnouncementCooldown;
        if (this.treasureHintDelay > 0) --this.treasureHintDelay;

        if (!this.canProvideSaintPatricksSupport()) {
            cancelLuckStates();
            return;
        }

        if (this.potTicks > 0 && --this.potTicks == 0) {
            this.potCenter = null;
        }
        if (this.fortuneTicks > 0) --this.fortuneTicks;

        this.entityData.set(DATA_FORTUNE_ACTIVE,
                this.potTicks > 0 || this.fortuneTicks > 0);

        if (this.potTicks > 0 && this.potCenter != null) {
            if (--this.potPulseDelay <= 0) {
                pulsePotOfGold(level);
                this.potPulseDelay = 20;
            }
            if (--this.potVisualDelay <= 0) {
                drawPotOfGold(level);
                this.potVisualDelay = 10;
            }
        }

        if (this.isWolfismWorkTick(20)) {
            applyLuckyPresence(level);
            if (this.fortuneTicks > 0) pulseFortunesFavor(level);
        }

        if (this.treasureHintDelay <= 0) {
            this.treasureHintDelay = TREASURE_HINT_INTERVAL;
            this.getBrain().getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get())
                    .ifPresent(pos -> emitFourLeafHint(level, pos));
        }

        if (!this.isWolfismWorkTick(10) || this.fortuneTicks > 0) return;

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_HOSTILE_COUNT.get())
                .orElse(0);
        int injuredCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_INJURED_FAMILY_COUNT.get())
                .orElse(0);
        LivingEntity critical = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_CRITICAL_FAMILY.get())
                .orElse(null);

        if (this.fortuneCooldown == 0
                && (critical != null || hostileCount >= 4 && injuredCount >= 1)) {
            startFortunesFavor(level);
            return;
        }

        if (this.potCooldown == 0 && this.potTicks == 0) {
            BlockPos treasure = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get())
                    .orElse(null);
            if (hostileCount >= 2 || isOwnerCloseToTreasure(treasure)) {
                startPotOfGold(level, choosePotCenter(treasure, hostileCount > 0));
            }
        }
    }

    private void tickCooldowns() {
        if (this.potCooldown > 0) --this.potCooldown;
        if (this.fortuneCooldown > 0) --this.fortuneCooldown;
    }

    private void cancelLuckStates() {
        this.potTicks = 0;
        this.fortuneTicks = 0;
        this.potCenter = null;
        this.potPulseDelay = 0;
        this.potVisualDelay = 0;
        this.entityData.set(DATA_FORTUNE_ACTIVE, false);
    }

    private void applyLuckyPresence(ServerLevel level) {
        if (!this.isTame()) return;
        LivingEntity owner = this.getOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.level() != this.level()
                || this.distanceToSqr(owner)
                > LUCKY_PRESENCE_RADIUS * LUCKY_PRESENCE_RADIUS) {
            return;
        }

        ++this.luckyPresencePulses;
        applyAtLeast(owner, MobEffects.LUCK, LUCKY_PRESENCE_REFRESH, 0);
    }

    private boolean isOwnerCloseToTreasure(BlockPos treasure) {
        LivingEntity owner = this.getOwner();
        return owner != null
                && treasure != null
                && owner.position().distanceToSqr(Vec3.atCenterOf(treasure)) <= 8.0D * 8.0D;
    }

    private Vec3 choosePotCenter(BlockPos treasure, boolean combat) {
        LivingEntity owner = this.getOwner();
        if (!combat && treasure != null && this.isOwnerCloseToTreasure(treasure)) {
            return Vec3.atCenterOf(treasure);
        }
        if (owner != null && this.distanceToSqr(owner) <= FAMILY_RADIUS * FAMILY_RADIUS) {
            return owner.position();
        }
        return this.position();
    }

    private void startPotOfGold(ServerLevel level, Vec3 center) {
        this.potCenter = center;
        this.potTicks = POT_DURATION;
        this.potCooldown = POT_COOLDOWN;
        this.potPulseDelay = 20;
        this.potVisualDelay = 10;
        ++this.potCastCount;
        this.entityData.set(DATA_FORTUNE_ACTIVE, true);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55F, 1.15F);
        pulsePotOfGold(level);
        drawPotOfGold(level);
    }

    private void pulsePotOfGold(ServerLevel level) {
        if (!this.isPotOfGoldActive()) return;
        ++this.potPulseCount;

        Vec3 center = this.potCenter;
        AABB area = new AABB(
                center.x - POT_RADIUS,
                center.y - POT_VERTICAL_RADIUS,
                center.z - POT_RADIUS,
                center.x + POT_RADIUS,
                center.y + POT_VERTICAL_RADIUS,
                center.z + POT_RADIUS);

        for (LivingEntity member : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                member -> this.isLuckRecipient(member) && this.isInsidePot(member))) {
            applyAtLeast(member, MobEffects.SPEED, 60, 0);
            if (member instanceof Player) {
                applyAtLeast(member, MobEffects.LUCK, 60, 1);
            }
        }
    }

    private void drawPotOfGold(ServerLevel level) {
        if (this.potCenter == null || this.potTicks <= 0) return;
        Vec3 center = this.potCenter;

        for (int i = 0; i < 20; ++i) {
            double angle = Math.PI * 2.0D * i / 20.0D;
            WolfVfx.sendParticles("saint_patricks_wolf", level, (i & 1) == 0 ? SHAMROCK_GREEN : LUCKY_GOLD,
                    center.x + Math.cos(angle) * POT_RADIUS,
                    center.y + 0.55D,
                    center.z + Math.sin(angle) * POT_RADIUS,
                    1, 0.025D, 0.04D, 0.025D, 0.0D);
        }

        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                center.x, center.y + 0.6D, center.z,
                3, 0.15D, 0.20D, 0.15D, 0.0D);
    }

    private void startFortunesFavor(ServerLevel level) {
        this.fortuneTicks = FORTUNE_DURATION;
        this.fortuneCooldown = FORTUNE_COOLDOWN;
        ++this.fortuneCastCount;
        this.entityData.set(DATA_FORTUNE_ACTIVE, true);
        this.playSound(SoundEvents.TOTEM_USE, 0.55F, 1.25F);
        luckyBurst(level, 30);
        pulseFortunesFavor(level);
    }

    private void pulseFortunesFavor(ServerLevel level) {
        ++this.fortunePulseCount;
        for (LivingEntity member : getSaintPatricksFamily(level, FORTUNE_RADIUS)) {
            applyAtLeast(member, MobEffects.SPEED, 60, 0);
            applyAtLeast(member, MobEffects.RESISTANCE, 60, 0);
            if (member instanceof Player) {
                applyAtLeast(member, MobEffects.LUCK, 80, 2);
            }
        }

        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                this.getX(), this.getY(0.65D), this.getZ(),
                8, 0.75D, 0.45D, 0.75D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                this.getX(), this.getY(0.75D), this.getZ(),
                5, 0.65D, 0.40D, 0.65D, 0.0D);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!this.canUseActiveWolfismAbility() || this.isHolidayRecovering()) return false;
        boolean hit = super.doHurtTarget(level, target);
        if (!hit
                || !(target instanceof LivingEntity living)
                || this.isSaintPatricksFamily(living)
                || !living.isAlive()) {
            return hit;
        }

        float procChance = 0.35F;
        if (this.isPotOfGoldActive()) procChance += 0.15F;
        if (this.isFortunesFavorActive()) procChance += 0.25F;
        if (this.random.nextFloat() >= Math.min(0.75F, procChance)) return true;

        ++this.luckyBiteProcs;
        switch (this.random.nextInt(4)) {
            case 0 -> living.hurtServer(
                    level,
                    this.damageSources().mobAttack(this),
                    this.isFortunesFavorActive() ? 3.0F : 2.0F);
            case 1 -> this.heal(this.isFortunesFavorActive() ? 3.0F : 2.0F);
            case 2 -> {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 3, 0));
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 3, 0));
            }
            default -> {
                double dx = this.getX() - living.getX();
                double dz = this.getZ() - living.getZ();
                living.knockback(0.70D, dx, dz);
                applyAtLeast(this, MobEffects.SPEED, 20 * 3, 0);
            }
        }

        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                living.getX(), living.getY(0.55D), living.getZ(),
                6, 0.28D, 0.28D, 0.28D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                living.getX(), living.getY(0.55D), living.getZ(),
                4, 0.22D, 0.22D, 0.22D, 0.0D);
        this.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35F, 1.35F);
        return true;
    }

    /**
     * 0 = no protection, 1 = nearby Lucky Break, 2 = inside Pot of Gold,
     * 3 = Fortune's Favor. Event code selects one strongest wolf only.
     */
    public int getLuckProtectionTier(LivingEntity member) {
        if (!this.canProvideSaintPatricksSupport() || !this.isLuckRecipient(member)) return 0;
        if (this.isFortunesFavorActive()
                && this.distanceToSqr(member) <= FORTUNE_RADIUS * FORTUNE_RADIUS) {
            return 3;
        }
        if (this.isPotOfGoldActive() && this.isInsidePot(member)) return 2;
        if (this.distanceToSqr(member) <= LUCKY_BREAK_RADIUS * LUCKY_BREAK_RADIUS) return 1;
        return 0;
    }

    public boolean tryFortuneDodge(ServerLevel level, LivingEntity member, DamageSource source) {
        if (!this.isFortunesFavorActive()
                || this.getLuckProtectionTier(member) < 3
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        long now = level.getGameTime();
        if (member.getPersistentData().getLongOr(NEXT_FORTUNE_DODGE, 0L) > now
                || this.random.nextFloat() >= 0.18F) {
            return false;
        }

        member.getPersistentData().putLong(
                NEXT_FORTUNE_DODGE,
                now + FORTUNE_DODGE_COOLDOWN);
        ++this.fortuneDodgeCount;
        emitLuckySave(level, member, true);
        return true;
    }

    public float tryLuckyBreak(
            ServerLevel level,
            LivingEntity member,
            DamageSource source,
            float amount) {
        int tier = this.getLuckProtectionTier(member);
        if (tier == 0
                || amount <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }

        float currentEffectiveHealth =
                member.getHealth() + member.getAbsorptionAmount();
        float projected = currentEffectiveHealth - amount;
        float criticalThreshold = member.getMaxHealth() * 0.25F;

        if (projected > criticalThreshold) {
            return amount;
        }

        long now = level.getGameTime();
        if (member.getPersistentData().getLongOr(NEXT_LUCKY_BREAK, 0L) > now) {
            return amount;
        }

        long lastMiss = member.getPersistentData()
                .getLongOr(LUCKY_BREAK_LAST_MISS, 0L);
        long storedMisses = member.getPersistentData()
                .getLongOr(LUCKY_BREAK_MISS_STREAK, 0L);

        int recentMisses = lastMiss > 0L
                && now - lastMiss <= LUCKY_BREAK_PITY_WINDOW
                ? (int) Math.min(LUCKY_BREAK_MAX_RECENT_MISSES, storedMisses)
                : 0;

        float chance = switch (tier) {
            case 3 -> 0.55F;
            case 2 -> 0.42F;
            default -> 0.30F;
        };

        chance += recentMisses * LUCKY_BREAK_PITY_PER_MISS;

        boolean wouldBeLethal = projected <= 0.0F;
        if (wouldBeLethal) {
            chance += LUCKY_BREAK_LETHAL_BONUS;
        }

        chance = Math.min(LUCKY_BREAK_MAX_CHANCE, chance);
        ++this.luckyBreakEligibleRolls;
        this.lastLuckyBreakChancePercent = Math.round(chance * 100.0F);

        if (this.random.nextFloat() >= chance) {
            int nextMisses = Math.min(
                    LUCKY_BREAK_MAX_RECENT_MISSES,
                    recentMisses + 1);
            member.getPersistentData().putLong(
                    LUCKY_BREAK_MISS_STREAK,
                    nextMisses);
            member.getPersistentData().putLong(
                    LUCKY_BREAK_LAST_MISS,
                    now);
            ++this.luckyBreakFailedRolls;
            return amount;
        }

        // A successful break consumes the accumulated short bad-luck streak.
        member.getPersistentData().putLong(LUCKY_BREAK_MISS_STREAK, 0L);
        member.getPersistentData().putLong(LUCKY_BREAK_LAST_MISS, 0L);

        float reduced = amount * 0.40F;
        float survivalCap = Math.max(
                0.0F,
                currentEffectiveHealth - 1.0F);
        reduced = Math.min(reduced, survivalCap);

        member.getPersistentData().putLong(
                NEXT_LUCKY_BREAK,
                now + LUCKY_BREAK_COOLDOWN);

        int escapeBuffTicks = switch (tier) {
            case 3 -> 20 * 7;
            case 2 -> 20 * 6;
            default -> 20 * 5;
        };

        applyAtLeast(member, MobEffects.ABSORPTION, escapeBuffTicks, 1);
        applyAtLeast(member, MobEffects.SPEED, escapeBuffTicks, 1);

        float recovery = Math.min(
                4.0F,
                Math.max(2.0F, member.getMaxHealth() * 0.08F));
        member.heal(recovery);

        ++this.luckyBreakCount;
        if (wouldBeLethal) {
            ++this.luckyBreakLethalSaves;
        }

        emitLuckySave(level, member, false);
        return reduced;
    }

    public double getLuckyFindChance(LivingEntity beneficiary, Vec3 deathPosition) {
        if (!this.canProvideSaintPatricksSupport()
                || !this.isTame()
                || this.getOwner() != beneficiary
                || this.distanceToSqr(beneficiary)
                > LUCKY_FIND_RADIUS * LUCKY_FIND_RADIUS) {
            return 0.0D;
        }

        if (this.isFortunesFavorActive()) return 0.35D;
        if (this.isPotOfGoldActive()
                && this.potCenter.distanceToSqr(deathPosition)
                <= POT_RADIUS * POT_RADIUS) {
            return 0.18D;
        }
        return 0.06D;
    }

    public ItemStack createControlledLuckyFind() {
        int roll = this.random.nextInt(100);
        if (roll < 32) return new ItemStack(Items.GOLD_NUGGET, 1);
        if (roll < 54) return new ItemStack(Items.IRON_NUGGET, 1);
        if (roll < 70) return new ItemStack(Items.LAPIS_LAZULI, 1 + this.random.nextInt(2));
        if (roll < 84) return new ItemStack(Items.REDSTONE, 1 + this.random.nextInt(2));
        if (roll < 92) return new ItemStack(Items.EMERALD, 1);
        if (roll < 98) return new ItemStack(Items.RABBIT_FOOT, 1);
        return new ItemStack(Items.EXPERIENCE_BOTTLE, 1);
    }

    public void onLuckyFindGranted(ServerLevel level, Vec3 position) {
        ++this.luckyFindCount;
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                position.x, position.y + 0.45D, position.z,
                8, 0.32D, 0.25D, 0.32D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                position.x, position.y + 0.45D, position.z,
                5, 0.25D, 0.22D, 0.25D, 0.0D);
        this.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45F, 1.45F);
    }

    public boolean isInsidePot(LivingEntity member) {
        if (this.potCenter == null) return false;
        Vec3 delta = member.position().subtract(this.potCenter);
        return Math.abs(delta.y) <= POT_VERTICAL_RADIUS
                && delta.x * delta.x + delta.z * delta.z <= POT_RADIUS * POT_RADIUS;
    }

    public void announceFourLeafTreasure(ServerLevel level, BlockPos treasure) {
        if (this.treasureAnnouncementCooldown > 0
                || !level.hasChunk(treasure.getX() >> 4, treasure.getZ() >> 4)
                || !isFourLeafTreasure(level.getBlockState(treasure))) {
            return;
        }
        this.treasureAnnouncementCooldown = 20 * 4;
        Vec3 center = Vec3.atCenterOf(treasure);
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                center.x, center.y, center.z,
                8, 0.35D, 0.35D, 0.35D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                center.x, center.y, center.z,
                4, 0.25D, 0.25D, 0.25D, 0.0D);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.55F);
    }

    private void emitFourLeafHint(ServerLevel level, BlockPos treasure) {
        LivingEntity owner = this.getOwner();
        if (!this.isTame()
                || owner == null
                || owner.level() != this.level()
                || this.distanceToSqr(owner) > 16.0D * 16.0D
                || !level.hasChunk(treasure.getX() >> 4, treasure.getZ() >> 4)
                || !isFourLeafTreasure(level.getBlockState(treasure))) {
            return;
        }

        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.65D, 0.0D);
        Vec3 toward = Vec3.atCenterOf(treasure).subtract(start);
        if (toward.lengthSqr() < 0.01D) return;
        Vec3 step = toward.normalize().scale(0.55D);

        for (int i = 1; i <= 4; ++i) {
            Vec3 point = start.add(step.scale(i));
            WolfVfx.sendParticles("saint_patricks_wolf", level, (i & 1) == 0 ? LUCKY_GOLD : SHAMROCK_GREEN,
                    point.x, point.y, point.z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
    }

    private void emitLuckySave(ServerLevel level, LivingEntity member, boolean dodge) {
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                member.getX(), member.getY(0.55D), member.getZ(),
                dodge ? 12 : 10, 0.35D, 0.35D, 0.35D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                member.getX(), member.getY(0.6D), member.getZ(),
                dodge ? 8 : 7, 0.28D, 0.30D, 0.28D, 0.0D);

        if (!dodge) {
            emitLuckyBreakLink(level, member);
        }

        this.playSound(
                dodge ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.AMETHYST_BLOCK_CHIME,
                dodge ? 0.45F : 0.65F,
                dodge ? 1.65F : 1.32F);
    }

    private void emitLuckyBreakLink(ServerLevel level, LivingEntity member) {
        Vec3 start = this.position().add(
                0.0D,
                this.getBbHeight() * 0.65D,
                0.0D);
        Vec3 end = member.position().add(
                0.0D,
                member.getBbHeight() * 0.60D,
                0.0D);
        Vec3 difference = end.subtract(start);

        int points = Math.max(
                5,
                Math.min(14, (int) Math.ceil(difference.length() * 1.5D)));

        for (int i = 1; i < points; ++i) {
            Vec3 point = start.add(difference.scale(i / (double) points));
            WolfVfx.sendParticles("saint_patricks_wolf", level,
                    (i & 1) == 0 ? LUCKY_GOLD : SHAMROCK_GREEN,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);
        }

        // Four small lobes make the save read like a clover burst at a glance.
        double y = member.getY(0.72D);
        double radius = 0.58D;
        for (int i = 0; i < 4; ++i) {
            double angle = Math.PI * 0.5D * i + Math.PI * 0.25D;
            WolfVfx.sendParticles("saint_patricks_wolf", level,
                    i % 2 == 0 ? SHAMROCK_GREEN : LUCKY_GOLD,
                    member.getX() + Math.cos(angle) * radius,
                    y,
                    member.getZ() + Math.sin(angle) * radius,
                    3,
                    0.10D,
                    0.10D,
                    0.10D,
                    0.0D);
        }
    }

    private void luckyBurst(ServerLevel level, int count) {
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                this.getX(), this.getY(0.55D), this.getZ(),
                count, 0.65D, 0.40D, 0.65D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                this.getX(), this.getY(0.6D), this.getZ(),
                Math.max(3, count / 2), 0.55D, 0.35D, 0.55D, 0.0D);
    }

    private static void applyAtLeast(
            LivingEntity entity,
            Holder<MobEffect> effect,
            int duration,
            int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null
                || current.getAmplifier() < amplifier
                || current.getAmplifier() == amplifier
                && !current.isInfiniteDuration()
                && current.getDuration() < duration) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, true, true));
        }
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        WolfVfx.sendParticles("saint_patricks_wolf", level, SHAMROCK_GREEN,
                this.getX(), this.getY(0.45D), this.getZ(),
                finishing ? 22 : 5, 0.55D, 0.30D, 0.55D, 0.0D);
        WolfVfx.sendParticles("saint_patricks_wolf", level, LUCKY_GOLD,
                this.getX(), this.getY(0.50D), this.getZ(),
                finishing ? 12 : 3, 0.45D, 0.25D, 0.45D, 0.0D);
        if (finishing) {
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.65F, 1.45F);
        }
    }

    public static boolean checkSaintPatricksWolfSpawnRules(
            EntityType<SaintPatricksWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        return level.getLevel().dimension() == Level.OVERWORLD
                && isSaintPatricksSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.SAINT_PATRICKS_WOLF_SPAWNS)
                && isSafeSaintPatricksSurface(level, pos);
    }

    public static boolean isSafeSaintPatricksSurface(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);

        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && floor.isFaceSturdy(level, floorPos, Direction.UP)
                && (floor.is(BlockTags.DIRT)
                || floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.MOSS_BLOCK));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SaintPatricksPotCooldown", this.potCooldown);
        output.putInt("SaintPatricksFortuneCooldown", this.fortuneCooldown);

        ValueOutput diagnostics = output.child("SaintPatricksDiagnostics");
        diagnostics.putBoolean("SupportReady", this.canProvideSaintPatricksSupport());
        diagnostics.putInt("PotTicks", this.potTicks);
        diagnostics.putInt("PotCooldown", this.potCooldown);
        diagnostics.putInt("FortuneTicks", this.fortuneTicks);
        diagnostics.putInt("FortuneCooldown", this.fortuneCooldown);
        diagnostics.putInt("LuckyPresencePulses", this.luckyPresencePulses);
        diagnostics.putInt("LuckyBiteProcs", this.luckyBiteProcs);
        diagnostics.putInt("PotCastCount", this.potCastCount);
        diagnostics.putInt("PotPulseCount", this.potPulseCount);
        diagnostics.putInt("FortuneCastCount", this.fortuneCastCount);
        diagnostics.putInt("FortunePulseCount", this.fortunePulseCount);
        diagnostics.putInt("LuckyBreakCount", this.luckyBreakCount);
        diagnostics.putInt("LuckyBreakEligibleRolls", this.luckyBreakEligibleRolls);
        diagnostics.putInt("LuckyBreakFailedRolls", this.luckyBreakFailedRolls);
        diagnostics.putInt("LuckyBreakLethalSaves", this.luckyBreakLethalSaves);
        diagnostics.putInt(
                "LastLuckyBreakChancePercent",
                this.lastLuckyBreakChancePercent);
        diagnostics.putInt("FortuneDodgeCount", this.fortuneDodgeCount);
        diagnostics.putInt("LuckyFindCount", this.luckyFindCount);
        diagnostics.putBoolean("PotActive", this.isPotOfGoldActive());
        diagnostics.putBoolean("FortuneActive", this.isFortunesFavorActive());

        BlockPos rememberedTreasure = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SAINT_PATRICKS_TREASURE_POS.get())
                .orElse(null);
        diagnostics.putBoolean("TreasureRemembered", rememberedTreasure != null);
        if (rememberedTreasure != null) {
            ValueOutput treasure = diagnostics.child("TreasurePos");
            treasure.putInt("X", rememberedTreasure.getX());
            treasure.putInt("Y", rememberedTreasure.getY());
            treasure.putInt("Z", rememberedTreasure.getZ());
        }

        if (this.potCenter != null) {
            ValueOutput center = diagnostics.child("PotCenter");
            center.putDouble("X", this.potCenter.x);
            center.putDouble("Y", this.potCenter.y);
            center.putDouble("Z", this.potCenter.z);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.potCooldown = Math.max(0, input.getIntOr("SaintPatricksPotCooldown", 0));
        this.fortuneCooldown = Math.max(0, input.getIntOr("SaintPatricksFortuneCooldown", 0));
        cancelLuckStates();
    }
}
