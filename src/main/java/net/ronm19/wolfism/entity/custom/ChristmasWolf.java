package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.effect.MobEffectCategory;
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
import net.minecraft.world.entity.projectile.Projectile;
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
import net.ronm19.wolfism.entity.ai.goal.ChristmasWolfSupportGoal;
import net.ronm19.wolfism.entity.ai.support.ChristmasSupportRules;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #2: Christmas Wolf.
 * Comfort, modest gifts, winter protection, and family-first support.
 *
 * Brain sensors choose needs/threats; the support goal handles positioning.
 * All gameplay is server-side. No terrain replacement, fullbright coat, or
 * changes to Halloween Wolf / the shared Holiday Wolf recovery implementation.
 */
public final class ChristmasWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_SENSE_RADIUS = 18.0D;
    public static final double THREAT_SENSE_RADIUS = 24.0D;
    public static final double WARMTH_RADIUS = 10.0D;
    public static final double SUPPORT_RADIUS = 12.0D;
    public static final double SPIRIT_RADIUS = 14.0D;
    public static final double PRESENT_RADIUS = ChristmasSupportRules.PRESENT_RADIUS;
    public static final double PRESENT_VERTICAL_RADIUS = ChristmasSupportRules.PRESENT_VERTICAL_RADIUS;

    private static final double BASE_HEALTH = 40.0D;
    private static final int CHEER_DURATION = 20 * 8;
    private static final int CHEER_COOLDOWN = 20 * 24;
    private static final int GUARD_DURATION = 20 * 8;
    private static final int GUARD_COOLDOWN = 20 * 26;
    private static final int PRESENT_DURATION = 20 * 10;
    private static final int PRESENT_COOLDOWN = 20 * 36;
    private static final int SPIRIT_DURATION = 20 * 14;
    private static final int SPIRIT_COOLDOWN = 20 * 70;
    private static final int GIFT_COOLDOWN = 20 * 60 * 10;
    private static final long OWNER_GIFT_GAP = 20L * 60L * 2L;
    private static final String NEXT_OWNER_GIFT = "WolfismChristmasNextGift";
    private static final String NEXT_WARMTH_HEAL = "WolfismChristmasNextWarmthHeal";
    private static final String NEXT_CLEANSE = "WolfismChristmasNextCleanse";

    private static final EntityDataAccessor<Boolean> DATA_FESTIVE_ACTIVE =
            SynchedEntityData.defineId(ChristmasWolf.class, EntityDataSerializers.BOOLEAN);

    private int cheerTicks;
    private int cheerCooldown;
    private int guardTicks;
    private int guardCooldown;
    private int presentTicks;
    private int presentCooldown;
    private int spiritTicks;
    private int spiritCooldown;
    private int giftCooldown = GIFT_COOLDOWN;
    private int quietTicks;
    private Vec3 presentCenter;
    private int presentPulseDelay;
    private int presentVisualDelay;

    private static final DustParticleOptions PRESENT_RED = new DustParticleOptions(0xCE3346, 1.05F);
    private static final DustParticleOptions PRESENT_GREEN = new DustParticleOptions(0x35C878, 1.05F);
    private static final DustParticleOptions PRESENT_GOLD = new DustParticleOptions(0xFFD36A, 1.1F);

    // Read-only diagnostics written to NBT by /data get. These are NOT triggers.
    private int warmthPulseCount;
    private int warmthHealCount;
    private int warmthRejectedHealCount;
    private int lastWarmthRecipients;
    private int lastWarmthWounded;
    private int lastWarmthCombatBlocked;
    private int lastWarmthThrottled;
    private float lastWarmthHealedAmount;
    private int presentCastCount;
    private int presentPulseCount;
    private int lastPresentRecipients;
    private int lastPresentRegenRecipients;
    private int lastPresentResistanceRecipients;
    private boolean directCombatPressure;

    private static final class BrainHolder {
        private static final Brain.Provider<ChristmasWolf> PROVIDER =
                Brain.<ChristmasWolf>provider(
                        List.of(ModSensorTypes.CHRISTMAS_AWARENESS.get()),
                        wolf -> List.of());
    }

    public ChristmasWolf(EntityType<? extends ChristmasWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.CHRISTMAS_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Above ordinary leap/melee, below float, panic and absolute owner-sit.
        this.goalSelector.addGoal(3, new ChristmasWolfSupportGoal(this));
    }

    @Override
    protected Brain<ChristmasWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ChristmasWolf> getBrain() {
        return (Brain<ChristmasWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FESTIVE_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            float previous = this.getHealth();
            health.setBaseValue(BASE_HEALTH);
            this.setHealth(this.isTame() ? this.getMaxHealth()
                    : Math.min(previous, this.getMaxHealth()));
        }
    }

    @Override
    protected boolean isHolidaySeasonActive(LocalDate date) {
        return date.getMonthValue() == 12;
    }

    /** Full December, using the same server real-world clock as the Holiday base. */
    public static boolean isChristmasSeasonOpen(ServerLevel level) {
        return level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())
                || LocalDate.now().getMonthValue() == 12;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame() && !this.isAngry() && !this.isHolidayRecovering()
                && stack.is(Items.SNOWBALL)) {
            if (this.level() instanceof ServerLevel level) {
                stack.consume(1, player);
                // Snowballs are inexpensive; preferred 50%, versus vanilla bone RNG.
                if (this.random.nextInt(2) == 0 && !EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    this.setOrderedToSit(true);
                    level.broadcastEntityEvent(this, (byte) 7);
                    this.festiveBurst(level, 12);
                    this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.45F, 1.3F);
                } else {
                    level.broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Keep ordinary bones, meat feeding, dyes, breeding and Staff interactions.
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /**
     * Support does not acquire enemies, so FOLLOW still permits warmth/healing.
     * SIT, Recall, NoAI, infancy and Holiday Recovery suspend outgoing support.
     * Offensive actions still go through the shared base's normal command gates.
     */
    public boolean canProvideChristmasSupport() {
        return this.isAlive() && !this.isRemoved() && !this.isBaby()
                && !this.isNoAi() && !this.isHolidayRecovering()
                && !this.isOrderedToSit() && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isFestiveActive() {
        return this.entityData.get(DATA_FESTIVE_ACTIVE);
    }

    public boolean isSpiritOfChristmasActive() {
        return spiritTicks > 0 && this.canProvideChristmasSupport();
    }

    public boolean isWinterGuardActive() {
        return (guardTicks > 0 || spiritTicks > 0) && this.canProvideChristmasSupport();
    }

    public boolean isChristmasFamily(LivingEntity entity) {
        if (entity == this) return true;
        if (entity == null || !entity.isAlive() || entity.level() != this.level()
                || !this.isTame()) return false;
        LivingEntity owner = this.getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;
        // Includes vanilla wolves and other tamed family pets; never another owner's pack.
        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            LivingEntity petOwner = pet.getOwner();
            return petOwner != null && petOwner.getUUID().equals(owner.getUUID());
        }
        return false;
    }

    public boolean isSupportRecipient(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved()
                && this.isChristmasFamily(entity)
                && !(entity instanceof AbstractWolfismHolidayWolf holiday
                && holiday.isHolidayRecovering());
    }

    public List<LivingEntity> getChristmasFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        if (!this.isHolidayRecovering()) family.add(this);
        family.addAll(level.getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(radius),
                entity -> entity != this && this.isSupportRecipient(entity)
                        && this.distanceToSqr(entity) <= radius * radius));
        return family;
    }

    public boolean isChristmasThreat(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity == this
                || entity.isSpectator() || this.isChristmasFamily(entity)
                || this.isAlliedTo(entity)) return false;
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof Enemy || entity == this.getTarget()
                || (entity instanceof Mob mob && this.isChristmasFamily(mob.getTarget()));
    }

    public List<LivingEntity> getChristmasThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(THREAT_SENSE_RADIUS),
                entity -> this.isChristmasThreat(entity)
                        && this.distanceToSqr(entity) <= THREAT_SENSE_RADIUS * THREAT_SENSE_RADIUS
                        && this.getSensing().hasLineOfSight(entity));
    }

    public double christmasNeedScore(LivingEntity entity) {
        double healthFraction = entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
        double score = (1.0D - healthFraction) * 100.0D;
        if (entity.getTicksFrozen() > 0 || entity.isInPowderSnow) score += 25.0D;
        if (hasHarmfulEffects(entity)) score += 18.0D;
        if (entity.hurtTime > 0) score += 12.0D;
        if (entity == this.getOwner() && healthFraction < 0.45D) score += 25.0D;
        return score;
    }

    public static boolean hasHarmfulEffects(LivingEntity entity) {
        return entity.getActiveEffects().stream().anyMatch(
                effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
    }

    public int countIncomingChristmasProjectiles(ServerLevel level, List<LivingEntity> family) {
        int count = 0;
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class, this.getBoundingBox().inflate(FAMILY_SENSE_RADIUS),
                projectile -> projectile.isAlive())) {
            Entity shooter = projectile.getOwner();
            if (shooter == this
                    || shooter instanceof LivingEntity living && this.isChristmasFamily(living)) {
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 0.0025D) continue; // Embedded arrows are not "incoming".
            for (LivingEntity member : family) {
                Vec3 toward = member.position().add(0.0D, member.getBbHeight() * 0.5D, 0.0D)
                        .subtract(projectile.position());
                if (toward.lengthSqr() <= 12.0D * 12.0D
                        && velocity.dot(toward) > 0.0D
                        && velocity.normalize().dot(toward.normalize()) > 0.72D) {
                    ++count;
                    break;
                }
            }
        }
        return count;
    }

    /** Called from gameplay events: do not stack protection once per Christmas Wolf. */
    public boolean suppliesWarmthTo(LivingEntity member) {
        return this.canProvideChristmasSupport() && this.isSupportRecipient(member)
                && this.distanceToSqr(member) <= WARMTH_RADIUS * WARMTH_RADIUS;
    }

    public boolean suppliesWinterGuardTo(LivingEntity member) {
        double radius = spiritTicks > 0 ? SPIRIT_RADIUS : SUPPORT_RADIUS;
        return this.isWinterGuardActive() && this.isSupportRecipient(member)
                && this.distanceToSqr(member) <= radius * radius;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;
        tickCooldowns();

        if (!this.canProvideChristmasSupport()) {
            cancelChristmasStates();
            return;
        }

        // Cheap state timers; entity searches and support decisions run at 2 Hz.
        if (cheerTicks > 0) --cheerTicks;
        if (guardTicks > 0) --guardTicks;
        if (presentTicks > 0 && --presentTicks == 0) {
            presentCenter = null;
            lastPresentRecipients = lastPresentRegenRecipients = lastPresentResistanceRecipients = 0;
        }
        if (spiritTicks > 0) --spiritTicks;
        this.entityData.set(DATA_FESTIVE_ACTIVE,
                cheerTicks > 0 || guardTicks > 0 || presentTicks > 0 || spiritTicks > 0);

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CHRISTMAS_HOSTILE_COUNT.get()).orElse(0);
        LivingEntity priority = this.getBrain().getMemory(
                ModMemoryModuleTypes.CHRISTMAS_PRIORITY_THREAT.get()).orElse(null);
        int incoming = this.getBrain().getMemory(
                ModMemoryModuleTypes.CHRISTMAS_INCOMING_PROJECTILE_COUNT.get()).orElse(0);
        // A visible but uninvolved monster is NOT an ongoing fight. Direct damage,
        // a valid live target, a family-targeting enemy or incoming shots still are.
        directCombatPressure = this.hurtTime > 0 || hasLiveCombatTarget(this)
                || incoming > 0 || isEngagedFamilyThreat(priority);
        quietTicks = ChristmasSupportRules.advanceQuietTicks(quietTicks, directCombatPressure);

        // Both field schedules are relative to creation, not the wolf's tick phase.
        if (presentTicks > 0 && presentCenter != null) {
            if (--presentPulseDelay <= 0) {
                pulsePresent(level);
                presentPulseDelay = 20;
            }
            if (--presentVisualDelay <= 0) {
                drawPresentField(level);
                presentVisualDelay = 10;
            }
        }

        if (this.isWolfismWorkTick(20)) {
            applyHolidayWarmth(level);
            if (spiritTicks > 0) pulseSpirit(level);
            tryGiftOfChristmas(level);
        }

        if (!this.isWolfismWorkTick(10) || spiritTicks > 0) return;

        int injured = this.getBrain().getMemory(
                ModMemoryModuleTypes.CHRISTMAS_INJURED_FAMILY_COUNT.get()).orElse(0);
        int cold = this.getBrain().getMemory(
                ModMemoryModuleTypes.CHRISTMAS_COLD_FAMILY_COUNT.get()).orElse(0);
        // The Brain's general need score includes cold/debuffs. Never let a
        // healthier cold ally hide an injured/critical patient from a healing cast.
        List<LivingEntity> patients = getChristmasFamily(level, FAMILY_SENSE_RADIUS);
        LivingEntity criticalPatient = chooseHealthPatient(patients,
                ChristmasSupportRules.CRITICAL_HEALTH_FRACTION, SPIRIT_RADIUS);
        boolean critical = criticalPatient != null;
        if (spiritCooldown == 0 && (critical || (hostileCount >= 4 && injured >= 2))) {
            startSpiritOfChristmas(level);
            return;
        }
        if (guardCooldown == 0 && (incoming > 0 || cold > 0)) startWinterGuard(level);
        if (cheerCooldown == 0 && (injured > 0 || hostileCount >= 2)) startChristmasCheer(level);
        if (presentCooldown == 0 && presentTicks == 0) {
            LivingEntity patient = chooseHealthPatient(patients,
                    ChristmasSupportRules.PRESENT_HEALTH_FRACTION, SUPPORT_RADIUS);
            if (patient != null) startPresentDrop(level, patient);
        }
    }

    private void tickCooldowns() {
        if (cheerCooldown > 0) --cheerCooldown;
        if (guardCooldown > 0) --guardCooldown;
        if (presentCooldown > 0) --presentCooldown;
        if (spiritCooldown > 0) --spiritCooldown;
        // Unloaded wolves do not accumulate gifts; elapsed cooldown persists on save.
        if (this.isTame() && !this.isBaby() && giftCooldown > 0) --giftCooldown;
    }

    private void cancelChristmasStates() {
        cheerTicks = guardTicks = presentTicks = spiritTicks = quietTicks = 0;
        presentCenter = null;
        presentPulseDelay = presentVisualDelay = 0;
        directCombatPressure = false;
        lastPresentRecipients = lastPresentRegenRecipients = lastPresentResistanceRecipients = 0;
        this.entityData.set(DATA_FESTIVE_ACTIVE, false);
    }

    private LivingEntity chooseHealthPatient(List<LivingEntity> family,
            float threshold, double range) {
        return family.stream()
                .filter(this::isSupportRecipient)
                .filter(member -> ChristmasSupportRules.eligiblePatient(member.getHealth(),
                        member.getMaxHealth(), threshold, this.distanceToSqr(member), range))
                .min(Comparator.<LivingEntity>comparingDouble(member ->
                                member.getHealth() / Math.max(1.0F, member.getMaxHealth()))
                        .thenComparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private boolean isEngagedFamilyThreat(LivingEntity threat) {
        if (!(threat instanceof Mob mob) || !threat.isAlive()
                || threat.level() != this.level() || !this.isChristmasThreat(threat)) return false;
        LivingEntity victim = mob.getTarget();
        return victim != null && victim.isAlive() && this.isChristmasFamily(victim)
                && this.distanceToSqr(threat) <= THREAT_SENSE_RADIUS * THREAT_SENSE_RADIUS;
    }

    private void applyHolidayWarmth(ServerLevel level) {
        ++warmthPulseCount;
        lastWarmthRecipients = lastWarmthWounded = lastWarmthCombatBlocked = lastWarmthThrottled = 0;
        lastWarmthHealedAmount = 0.0F;
        for (LivingEntity member : getChristmasFamily(level, WARMTH_RADIUS)) {
            ++lastWarmthRecipients;
            member.setTicksFrozen(0);
            if (member.getHealth() >= member.getMaxHealth()) continue;
            ++lastWarmthWounded;
            if (quietTicks < ChristmasSupportRules.CALM_TICKS
                    || isRecentlyInCombat(this) || isRecentlyInCombat(member)) {
                ++lastWarmthCombatBlocked;
                continue;
            }
            long now = level.getGameTime();
            long next = member.getPersistentData().getLongOr(NEXT_WARMTH_HEAL, 0L);
            if (ChristmasSupportRules.isThrottled(now, next,
                    ChristmasSupportRules.WARMTH_HEAL_INTERVAL)) {
                ++lastWarmthThrottled;
                continue;
            }
            // Shared per-recipient deadline: several Christmas Wolves cannot
            // multiply the passive heal. Still use heal(), respecting heal events.
            member.getPersistentData().putLong(NEXT_WARMTH_HEAL,
                    now + ChristmasSupportRules.WARMTH_HEAL_INTERVAL);
            float before = member.getHealth();
            member.heal(ChristmasSupportRules.WARMTH_HEAL_AMOUNT);
            float gained = Math.max(0.0F, member.getHealth() - before);
            if (gained > 0.0F) {
                ++warmthHealCount;
                lastWarmthHealedAmount += gained;
                WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.HAPPY_VILLAGER,
                        member.getX(), member.getY(0.65D), member.getZ(),
                        2, 0.18D, 0.12D, 0.18D, 0.0D);
            } else {
                ++warmthRejectedHealCount;
            }
        }
    }

    private static boolean hasLiveCombatTarget(LivingEntity member) {
        if (!(member instanceof Mob mob)) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target != member && target.isAlive() && !target.isRemoved()
                && target.level() == member.level() && !member.isAlliedTo(target)
                && member.distanceToSqr(target) <= THREAT_SENSE_RADIUS * THREAT_SENSE_RADIUS;
    }

    private static boolean isRecentlyInCombat(LivingEntity member) {
        if (member.hurtTime > 0 || hasLiveCombatTarget(member)) return true;
        return ChristmasSupportRules.isRecentInteraction(member.tickCount,
                        member.getLastHurtByMobTimestamp(), member.getLastHurtByMob() != null)
                || ChristmasSupportRules.isRecentInteraction(member.tickCount,
                        member.getLastHurtMobTimestamp(), member.getLastHurtMob() != null);
    }

    private void startChristmasCheer(ServerLevel level) {
        cheerTicks = CHEER_DURATION;
        cheerCooldown = CHEER_COOLDOWN;
        for (LivingEntity member : getChristmasFamily(level, SUPPORT_RADIUS)) {
            applyAtLeast(member, MobEffects.REGENERATION, CHEER_DURATION, 0);
            applyAtLeast(member, MobEffects.RESISTANCE, CHEER_DURATION, 0);
            applyAtLeast(member, MobEffects.SPEED, CHEER_DURATION, 0);
        }
        this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.45F, 1.15F);
        festiveBurst(level, 14);
    }

    private void startWinterGuard(ServerLevel level) {
        guardTicks = GUARD_DURATION;
        guardCooldown = GUARD_COOLDOWN;
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45F, 0.9F);
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY(0.6D), this.getZ(), 18, 1.5D, 0.55D, 1.5D, 0.02D);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!this.canUseActiveWolfismAbility() || this.isHolidayRecovering()) return false;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && !this.isChristmasFamily(living)) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 3, 0, false, true));
            WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                    living.getX(), living.getY(0.5D), living.getZ(),
                    5, 0.25D, 0.25D, 0.25D, 0.01D);
        }
        return hit;
    }

    /** Present Drop is a timed particle field, not a placed/generating loot block. */
    private void startPresentDrop(ServerLevel level, LivingEntity recipient) {
        // Vec3 is immutable: store the exact cast point and never follow the caster.
        presentCenter = recipient.position();
        presentTicks = PRESENT_DURATION;
        presentCooldown = PRESENT_COOLDOWN;
        presentPulseDelay = 20;
        presentVisualDelay = 10;
        ++presentCastCount;
        this.entityData.set(DATA_FESTIVE_ACTIVE, true);
        this.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.1F);
        pulsePresent(level);
        drawPresentField(level);
    }

    private void pulsePresent(ServerLevel level) {
        if (presentCenter == null || presentTicks <= 0 || !this.canProvideChristmasSupport()) return;
        ++presentPulseCount;
        lastPresentRecipients = lastPresentRegenRecipients = lastPresentResistanceRecipients = 0;
        Vec3 center = presentCenter;
        AABB area = new AABB(center.x - PRESENT_RADIUS, center.y - PRESENT_VERTICAL_RADIUS,
                center.z - PRESENT_RADIUS, center.x + PRESENT_RADIUS,
                center.y + PRESENT_VERTICAL_RADIUS, center.z + PRESENT_RADIUS);
        for (LivingEntity member : level.getEntitiesOfClass(LivingEntity.class, area,
                member -> isSupportRecipient(member) && ChristmasSupportRules.insidePresent(
                        member.getX() - center.x, member.getY() - center.y,
                        member.getZ() - center.z))) {
            ++lastPresentRecipients;
            member.setTicksFrozen(0);
            refreshSupportEffect(member, MobEffects.REGENERATION, 100, 0);
            refreshSupportEffect(member, MobEffects.RESISTANCE, 60, 0);
            // These count actual effect coverage, not a promise of a healed hitpoint.
            // Effect immunity / other mods can legitimately reject an application.
            if (member.getEffect(MobEffects.REGENERATION) != null) ++lastPresentRegenRecipients;
            if (member.getEffect(MobEffects.RESISTANCE) != null) ++lastPresentResistanceRecipients;
        }
    }

    private void drawPresentField(ServerLevel level) {
        if (presentCenter == null || presentTicks <= 0) return;
        Vec3 center = presentCenter;
        // Red/green outline and gold center are distinct from snow terrain and
        // Cheer's moving snowflake burst. Particle-only; no block/chunk reads.
        for (int i = 0; i < 16; ++i) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            WolfVfx.sendParticles("christmas_wolf", level, (i & 1) == 0 ? PRESENT_RED : PRESENT_GREEN,
                    center.x + Math.cos(angle) * PRESENT_RADIUS, center.y + 0.65D,
                    center.z + Math.sin(angle) * PRESENT_RADIUS,
                    1, 0.03D, 0.05D, 0.03D, 0.0D);
        }
        for (int i = 0; i < 3; ++i) {
            WolfVfx.sendParticles("christmas_wolf", level, PRESENT_GOLD, center.x, center.y + 0.5D + i * 0.4D, center.z,
                    1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                center.x, center.y + 0.65D, center.z, 3, 0.35D, 0.2D, 0.35D, 0.005D);
    }

    private void startSpiritOfChristmas(ServerLevel level) {
        spiritTicks = SPIRIT_DURATION;
        spiritCooldown = SPIRIT_COOLDOWN;
        this.entityData.set(DATA_FESTIVE_ACTIVE, true);
        this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.65F, 0.85F);
        festiveBurst(level, 28);
        pulseSpirit(level);
    }

    private void pulseSpirit(ServerLevel level) {
        for (LivingEntity member : getChristmasFamily(level, SPIRIT_RADIUS)) {
            member.setTicksFrozen(0);
            refreshSupportEffect(member, MobEffects.REGENERATION, 100, 1);
            refreshSupportEffect(member, MobEffects.RESISTANCE, 60, 0);
            refreshSupportEffect(member, MobEffects.SPEED, 60, 0);
            cleanseOneHarmfulEffect(level, member);
            regroupIdleFamily(member);
        }
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY(0.7D), this.getZ(), 8, 1.0D, 0.5D, 1.0D, 0.015D);
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY(0.7D), this.getZ(), 3, 0.8D, 0.45D, 0.8D, 0.01D);
    }

    private void cleanseOneHarmfulEffect(ServerLevel level, LivingEntity member) {
        long now = level.getGameTime();
        if (member.getPersistentData().getLongOr(NEXT_CLEANSE, 0L) > now) return;
        MobEffectInstance harmful = member.getActiveEffects().stream()
                .filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .max(Comparator.comparingInt(MobEffectInstance::getAmplifier)
                        .thenComparingInt(MobEffectInstance::getDuration))
                .orElse(null);
        if (harmful != null) {
            member.removeEffect(harmful.getEffect());
            member.getPersistentData().putLong(NEXT_CLEANSE, now + 40L);
        }
    }

    private void regroupIdleFamily(LivingEntity member) {
        // Never override another wolf's Staff order, target, sitting or downed state.
        if (!(member instanceof AbstractWolfismWolf wolf) || wolf == this
                || wolf.isBaby() || wolf.isNoAi() || wolf.isOrderedToSit()
                || wolf.isInSittingPose() || wolf.isWolfStaffRecallActive()
                || wolf.hasWolfStaffCommand() || wolf.getTarget() != null
                || wolf.distanceToSqr(this) < 8.0D * 8.0D
                || !wolf.getNavigation().isDone()) return;
        LivingEntity owner = this.getOwner();
        if (owner != null && this.distanceToSqr(owner) <= 8.0D * 8.0D) {
            wolf.getNavigation().moveTo(this, 1.05D);
        }
    }

    private void tryGiftOfChristmas(ServerLevel level) {
        LivingEntity owner = this.getOwner();
        if (giftCooldown > 0 || !this.isTame() || quietTicks < 100
                || owner == null || !owner.isAlive() || this.distanceToSqr(owner) > 8.0D * 8.0D
                || isRecentlyInCombat(owner)) return;
        long now = level.getGameTime();
        long nextOwnerGift = owner.getPersistentData().getLongOr(NEXT_OWNER_GIFT, 0L);
        if (nextOwnerGift > now) return;

        ItemStack gift = switch (this.random.nextInt(6)) {
            case 0 -> new ItemStack(Items.COOKIE, 2);
            case 1 -> new ItemStack(Items.BREAD, 1);
            case 2 -> new ItemStack(Items.SWEET_BERRIES, 3);
            case 3 -> new ItemStack(Items.SNOWBALL, 4);
            case 4 -> new ItemStack(Items.BONE, 1);
            default -> new ItemStack(Items.STRING, 2);
        };
        if (this.spawnAtLocation(level, gift) != null) {
            giftCooldown = GIFT_COOLDOWN;
            owner.getPersistentData().putLong(NEXT_OWNER_GIFT, now + OWNER_GIFT_GAP);
            this.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.45F, 1.4F);
            festiveBurst(level, 10);
        }
    }

    /** Cast once: vanilla effect merging preserves stronger/longer effects. */
    private static void applyAtLeast(
            LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier
                || current.getAmplifier() == amplifier && !current.isInfiniteDuration()
                        && current.getDuration() < duration) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false));
        }
    }

    /** Refresh late, not every tick: do not repeatedly reset regeneration's timer. */
    private static void refreshSupportEffect(
            LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (ChristmasSupportRules.shouldRefreshEffect(current != null,
                current != null && current.isInfiniteDuration(),
                current == null ? -1 : current.getAmplifier(),
                current == null ? 0 : current.getDuration(), amplifier)) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false));
        }
    }

    private void festiveBurst(ServerLevel level, int count) {
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY(0.6D), this.getZ(), count, 0.7D, 0.5D, 0.7D, 0.02D);
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY(0.6D), this.getZ(), Math.max(3, count / 3),
                0.6D, 0.4D, 0.6D, 0.015D);
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        WolfVfx.sendParticles("christmas_wolf", level, ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY(0.4D), this.getZ(), finishing ? 22 : 4,
                0.55D, 0.3D, 0.55D, 0.01D);
        if (finishing) {
            this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.65F, 1.25F);
            festiveBurst(level, 12);
        }
    }

    // O(1) worldgen predicate: only local column reads, no neighborhood scans.
    public static boolean checkChristmasWolfSpawnRules(
            EntityType<ChristmasWolf> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        return level.getLevel().dimension() == Level.OVERWORLD
                && isChristmasSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.CHRISTMAS_WOLF_SPAWNS)
                && isSafeChristmasSurface(level, pos);
    }

    public static boolean isSafeChristmasSurface(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !feet.is(Blocks.POWDER_SNOW) && !head.is(Blocks.POWDER_SNOW)
                && (floor.isFaceSturdy(level, below, Direction.UP)
                || floor.is(Blocks.SNOW) && level.getBlockState(below.below())
                        .isFaceSturdy(level, below.below(), Direction.UP))
                && (floor.is(BlockTags.DIRT) || floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.SNOW_BLOCK) || floor.is(Blocks.SNOW)
                || floor.is(Blocks.ICE) || floor.is(Blocks.PACKED_ICE));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ChristmasCheerCooldown", cheerCooldown);
        output.putInt("ChristmasGuardCooldown", guardCooldown);
        output.putInt("ChristmasPresentCooldown", presentCooldown);
        output.putInt("ChristmasSpiritCooldown", spiritCooldown);
        output.putInt("ChristmasGiftCooldown", giftCooldown);
        // Diagnostic values. Timed fields/buffs intentionally do not resume after unload.
        output.putInt("ChristmasCheerTicks", cheerTicks);
        output.putInt("ChristmasGuardTicks", guardTicks);
        output.putInt("ChristmasPresentTicks", presentTicks);
        output.putInt("ChristmasSpiritTicks", spiritTicks);
        ValueOutput diagnostics = output.child("ChristmasDiagnostics");
        diagnostics.putString("Patch", "support-r2");
        diagnostics.putString("SupportState", supportBlockReason());
        diagnostics.putInt("QuietTicks", quietTicks);
        diagnostics.putBoolean("DirectCombatPressure", directCombatPressure);
        diagnostics.putInt("WarmthPulseCount", warmthPulseCount);
        diagnostics.putInt("WarmthHealCount", warmthHealCount);
        diagnostics.putInt("WarmthRejectedHealCount", warmthRejectedHealCount);
        diagnostics.putInt("WarmthRecipients", lastWarmthRecipients);
        diagnostics.putInt("WarmthWounded", lastWarmthWounded);
        diagnostics.putInt("WarmthCombatBlocked", lastWarmthCombatBlocked);
        diagnostics.putInt("WarmthThrottled", lastWarmthThrottled);
        diagnostics.putFloat("WarmthLastHealedAmount", lastWarmthHealedAmount);
        diagnostics.putInt("PresentCastCount", presentCastCount);
        diagnostics.putInt("PresentPulseCount", presentPulseCount);
        diagnostics.putInt("PresentTicks", presentTicks);
        diagnostics.putInt("PresentCooldown", presentCooldown);
        diagnostics.putInt("PresentRecipients", lastPresentRecipients);
        diagnostics.putInt("PresentRegenRecipients", lastPresentRegenRecipients);
        diagnostics.putInt("PresentResistanceRecipients", lastPresentResistanceRecipients);
        if (presentCenter != null) {
            ValueOutput center = diagnostics.child("PresentCenter");
            center.putDouble("X", presentCenter.x);
            center.putDouble("Y", presentCenter.y);
            center.putDouble("Z", presentCenter.z);
        }
    }

    private String supportBlockReason() {
        if (!this.isAlive() || this.isRemoved()) return "removed_or_dead";
        if (this.isHolidayRecovering()) return "holiday_recovery";
        if (this.isBaby()) return "baby";
        if (this.isNoAi()) return "no_ai";
        if (this.isWolfStaffRecallActive()) return "recall";
        if (this.isOrderedToSit() || this.isInSittingPose()) return "sitting";
        return "ready";
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cheerCooldown = Math.max(0, input.getIntOr("ChristmasCheerCooldown", 0));
        guardCooldown = Math.max(0, input.getIntOr("ChristmasGuardCooldown", 0));
        presentCooldown = Math.max(0, input.getIntOr("ChristmasPresentCooldown", 0));
        spiritCooldown = Math.max(0, input.getIntOr("ChristmasSpiritCooldown", 0));
        giftCooldown = Math.max(0, input.getIntOr("ChristmasGiftCooldown", GIFT_COOLDOWN));
        cancelChristmasStates();
    }
}
