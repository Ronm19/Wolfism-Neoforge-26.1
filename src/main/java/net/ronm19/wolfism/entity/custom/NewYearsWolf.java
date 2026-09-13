package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.NewYearsWolfSupportGoal;
import net.ronm19.wolfism.entity.ai.support.NewYearsCooldownSupport;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #4: New Year's Wolf.
 *
 * <p>A renewal/support specialist rather than an explosive Firework Wolf. Its
 * job is to shorten ordinary ability fatigue, cleanse harmful effects, restart
 * a stalled pack, and give endangered family a bounded second chance.</p>
 */
public final class NewYearsWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_SENSE_RADIUS = 20.0D;
    public static final double THREAT_SENSE_RADIUS = 26.0D;
    public static final double FRESH_START_RADIUS = 12.0D;
    public static final double SUPPORT_RADIUS = 14.0D;
    public static final double NEW_BEGINNING_RADIUS = 16.0D;
    public static final double SECOND_CHANCE_RADIUS = 16.0D;
    public static final double BELL_TAMING_RADIUS = 12.0D;

    private static final double BASE_HEALTH = 42.0D;

    private static final int FRESH_START_CALM_TICKS = 20 * 5;
    private static final int FRESH_START_PULSE_INTERVAL = 20 * 4;
    private static final int FRESH_START_EFFECT_REDUCTION = 20 * 2;
    private static final int FRESH_START_COOLDOWN_REFRESH = 20;

    private static final int MIDNIGHT_RESET_COOLDOWN = 20 * 32;
    private static final int MIDNIGHT_RESET_EFFECT_REDUCTION = 20 * 5;
    private static final int MIDNIGHT_RESET_COOLDOWN_REFRESH = 20 * 4;
    private static final long MIDNIGHT_RESET_RECIPIENT_GAP = 20L * 20L;

    private static final int RESOLUTION_READY_TICKS = 20 * 6;
    private static final double RESOLUTION_BONUS_DAMAGE = 4.0D;
    private static final Identifier RESOLUTION_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "new_years_resolution_strike");

    private static final int COUNTDOWN_DURATION = 20 * 3;
    private static final int COUNTDOWN_BUFF_DURATION = 20 * 8;
    private static final int COUNTDOWN_COOLDOWN = 20 * 28;

    private static final int SECOND_CHANCE_COOLDOWN = 20 * 38;
    private static final long SECOND_CHANCE_RECIPIENT_GAP = 20L * 45L;

    private static final int NEW_BEGINNING_DURATION = 20 * 14;
    private static final int NEW_BEGINNING_COOLDOWN = 20 * 70;
    private static final int NEW_BEGINNING_VISUAL_INTERVAL = 10;
    private static final long NEW_BEGINNING_CLEANSE_GAP = 20L * 2L;

    private static final String NEXT_FRESH_START = "WolfismNewYearsNextFreshStart";
    private static final String NEXT_MIDNIGHT_RESET = "WolfismNewYearsNextMidnightReset";
    private static final String NEXT_SECOND_CHANCE = "WolfismNewYearsNextSecondChance";
    private static final String NEXT_NEW_BEGINNING_CLEANSE =
            "WolfismNewYearsNextBeginningCleanse";
    private static final String NEXT_NEW_BEGINNING_PULSE = "WolfismNewYearsNextBeginningPulse";
    private static final String NEXT_BELL_TAME = "WolfismNewYearsNextBellTame";

    private static final DustParticleOptions MIDNIGHT_BLUE =
            new DustParticleOptions(0x274DA8, 1.05F);
    private static final DustParticleOptions COUNTDOWN_GOLD =
            new DustParticleOptions(0xFFD45D, 1.12F);
    private static final DustParticleOptions NEW_YEAR_SILVER =
            new DustParticleOptions(0xE7EEFF, 1.02F);
    private static final DustParticleOptions RENEWAL_CYAN =
            new DustParticleOptions(0x50E8FF, 1.02F);

    private static final EntityDataAccessor<Boolean> DATA_RENEWAL_ACTIVE =
            SynchedEntityData.defineId(NewYearsWolf.class, EntityDataSerializers.BOOLEAN);

    private int midnightResetCooldown;
    private int countdownTicks;
    private int countdownCooldown;
    private int secondChanceCooldown;
    private int newBeginningTicks;
    private int newBeginningCooldown;
    private int renewalVisualTicks;
    private int quietTicks;
    private int freshStartPulseDelay;
    private int resolutionChargeTicks = RESOLUTION_READY_TICKS;
    private int lastRecoveryCountdownStage;

    // Read-only diagnostics exposed through /data get.
    private int freshStartPulseCount;
    private int freshStartRecipients;
    private int freshStartEffectsShortened;
    private int freshStartCooldownFields;
    private int midnightResetCount;
    private int midnightResetRecipients;
    private int midnightResetEffectsRemoved;
    private int midnightResetCooldownFields;
    private int resolutionStrikeCount;
    private int countdownCount;
    private int countdownCompletionCount;
    private int secondChanceCount;
    private int secondChanceLethalSaves;
    private int newBeginningCount;
    private int newBeginningPulseCount;
    private int newBeginningVisualPulseCount;
    private int newBeginningEffectsRemoved;
    private int newBeginningCooldownFields;
    private boolean directCombatPressure;

    private static final class BrainHolder {
        private static final Brain.Provider<NewYearsWolf> PROVIDER =
                Brain.<NewYearsWolf>provider(
                        List.of(ModSensorTypes.NEW_YEARS_AWARENESS.get()),
                        wolf -> List.of());
    }

    public NewYearsWolf(EntityType<? extends NewYearsWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.NEW_YEARS_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new NewYearsWolfSupportGoal(this));
    }

    @Override
    protected Brain<NewYearsWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<NewYearsWolf> getBrain() {
        return (Brain<NewYearsWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RENEWAL_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.325D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            float previous = this.getHealth();
            maxHealth.setBaseValue(BASE_HEALTH);
            this.setHealth(this.isTame()
                    ? this.getMaxHealth()
                    : Math.min(previous, this.getMaxHealth()));
        }
    }

    @Override
    protected boolean isHolidaySeasonActive(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        return month == 12 && day >= 29 || month == 1 && day <= 3;
    }

    /** December 29 through January 3, or the shared development override. */
    public static boolean isNewYearsSeasonOpen(ServerLevel level) {
        if (level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())) {
            return true;
        }
        LocalDate date = LocalDate.now();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        return month == 12 && day >= 29 || month == 1 && day <= 3;
    }

    /** Called by the Bell interaction event. The Bell still performs its normal use. */
    public boolean tryBellTame(ServerPlayer player, ServerLevel level) {
        if (this.isTame()
                || this.isBaby()
                || this.isAngry()
                || this.isHolidayRecovering()
                || !this.isAlive()
                || this.isRemoved()
                || player.level() != level
                || this.level() != level
                || this.getPersistentData().getLongOr(NEXT_BELL_TAME, 0L) > level.getGameTime()) {
            return false;
        }

        this.getPersistentData().putLong(NEXT_BELL_TAME, level.getGameTime() + 20);
        if (this.random.nextFloat() < 0.75F && !EventHooks.onAnimalTame(this, player)) {
            this.tame(player);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setOrderedToSit(true);
            this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 60);
            level.broadcastEntityEvent(this, (byte) 7);
            this.renewalBurst(level, this.position(), 20, true);
            // The block itself supplies the Bell sound; add only a light celebratory cue.
            this.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.55F, 1.25F);
        } else {
            level.broadcastEntityEvent(this, (byte) 6);
            WolfVfx.sendParticles("new_years_wolf", level,
                    MIDNIGHT_BLUE,
                    this.getX(), this.getY(0.65D), this.getZ(),
                    5, 0.30D, 0.25D, 0.30D, 0.0D);
        }
        return true;
    }

    /** Support may run during Follow, but not while sitting, recalling, downed or a baby. */
    public boolean canProvideNewYearsSupport() {
        return this.isAlive()
                && !this.isRemoved()
                && !this.isBaby()
                && !this.isNoAi()
                && !this.isHolidayRecovering()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isRenewalVisualActive() {
        return this.entityData.get(DATA_RENEWAL_ACTIVE);
    }

    public boolean isNewBeginningActive() {
        return this.newBeginningTicks > 0 && this.canProvideNewYearsSupport();
    }

    public boolean isCountdownActive() {
        return this.countdownTicks > 0 && this.canProvideNewYearsSupport();
    }

    public boolean isNewYearsFamily(LivingEntity entity) {
        if (entity == this) {
            return true;
        }
        if (entity == null
                || !entity.isAlive()
                || entity.level() != this.level()
                || !this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null) {
            return false;
        }
        if (entity == owner) {
            return true;
        }
        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            LivingEntity petOwner = pet.getOwner();
            return petOwner != null && petOwner.getUUID().equals(owner.getUUID());
        }
        return false;
    }

    public boolean isNewYearsRecipient(LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && this.isNewYearsFamily(entity)
                && !(entity instanceof AbstractWolfismHolidayWolf holiday
                && holiday.isHolidayRecovering());
    }

    public List<LivingEntity> getNewYearsFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        if (!this.isHolidayRecovering()) {
            family.add(this);
        }
        family.addAll(level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                entity -> entity != this
                        && this.isNewYearsRecipient(entity)
                        && this.distanceToSqr(entity) <= radius * radius));
        return family;
    }

    public boolean isNewYearsThreat(LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == this
                || entity.isSpectator()
                || this.isNewYearsFamily(entity)
                || this.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof Player player && player.isCreative()) {
            return false;
        }
        return entity instanceof Enemy
                || entity == this.getTarget()
                || entity instanceof Mob mob && this.isNewYearsFamily(mob.getTarget());
    }

    public List<LivingEntity> getNewYearsThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THREAT_SENSE_RADIUS),
                entity -> this.isNewYearsThreat(entity)
                        && this.distanceToSqr(entity)
                        <= THREAT_SENSE_RADIUS * THREAT_SENSE_RADIUS
                        && this.getSensing().hasLineOfSight(entity));
    }

    public double newYearsNeedScore(LivingEntity member) {
        if (!this.isNewYearsRecipient(member)) {
            return 0.0D;
        }

        double healthFraction = member.getHealth()
                / Math.max(1.0F, member.getMaxHealth());
        int harmful = countHarmfulEffects(member);
        int fatigued = member instanceof AbstractWolfismWolf wolf
                ? NewYearsCooldownSupport.countEligible(wolf)
                : 0;

        double score = (1.0D - healthFraction) * 100.0D;
        score += harmful * 18.0D;
        score += Math.min(4, fatigued) * 10.0D;
        if (member.hurtTime > 0) {
            score += 10.0D;
        }
        if (member == this.getOwner() && healthFraction < 0.40D) {
            score += 22.0D;
        }
        return score;
    }

    public static int countHarmfulEffects(LivingEntity entity) {
        return (int) entity.getActiveEffects().stream()
                .filter(instance -> instance.getEffect().value().getCategory()
                        == MobEffectCategory.HARMFUL)
                .count();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }

        this.tickCooldowns();
        if (!this.isBaby() && !this.isHolidayRecovering()) {
            this.resolutionChargeTicks = Math.min(
                    RESOLUTION_READY_TICKS,
                    this.resolutionChargeTicks + 1);
        }

        if (!this.canProvideNewYearsSupport()) {
            this.cancelNewYearsStates();
            return;
        }

        if (this.renewalVisualTicks > 0) {
            --this.renewalVisualTicks;
        }
        if (this.newBeginningTicks > 0) {
            --this.newBeginningTicks;

            if (this.newBeginningTicks > 0
                    && this.newBeginningTicks % NEW_BEGINNING_VISUAL_INTERVAL == 0) {
                this.emitNewBeginningBeacon(level, false);
            }

            if (this.newBeginningTicks == 0) {
                this.finishNewBeginning(level);
            }
        }
        if (this.countdownTicks > 0) {
            this.tickCountdown(level);
        }

        this.entityData.set(
                DATA_RENEWAL_ACTIVE,
                this.renewalVisualTicks > 0
                        || this.countdownTicks > 0
                        || this.newBeginningTicks > 0);

        LivingEntity priority = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_PRIORITY_THREAT.get())
                .orElse(null);
        this.directCombatPressure = this.hurtTime > 0
                || hasLiveCombatTarget(this)
                || this.isEngagedFamilyThreat(priority);
        this.quietTicks = this.directCombatPressure
                ? 0
                : Math.min(20 * 60, this.quietTicks + 1);

        if (--this.freshStartPulseDelay <= 0) {
            this.freshStartPulseDelay = FRESH_START_PULSE_INTERVAL;
            this.applyFreshStart(level);
        }

        if (this.newBeginningTicks > 0 && this.isWolfismWorkTick(20)) {
            this.pulseNewBeginning(level);
        }

        if (!this.isWolfismWorkTick(10)
                || this.newBeginningTicks > 0
                || this.countdownTicks > 0) {
            return;
        }

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_HOSTILE_COUNT.get())
                .orElse(0);
        int injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_INJURED_FAMILY_COUNT.get())
                .orElse(0);
        int harmful = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_HARMFUL_FAMILY_COUNT.get())
                .orElse(0);
        int fatigued = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEW_YEARS_FATIGUED_FAMILY_COUNT.get())
                .orElse(0);

        List<LivingEntity> family = this.getNewYearsFamily(level, FAMILY_SENSE_RADIUS);
        LivingEntity critical = family.stream()
                .filter(this::isNewYearsRecipient)
                .filter(member -> member.getHealth()
                        <= member.getMaxHealth() * 0.35F)
                .max(Comparator.comparingDouble(this::newYearsNeedScore))
                .orElse(null);

        if (this.newBeginningCooldown == 0
                && (critical != null
                || hostileCount >= 4 && injured + harmful + fatigued >= 3)) {
            this.startNewBeginning(level);
            return;
        }

        LivingEntity proactive = family.stream()
                .filter(this::isNewYearsRecipient)
                .filter(member -> member.getHealth()
                        <= member.getMaxHealth() * 0.22F)
                .filter(member -> countHarmfulEffects(member) >= 2
                        || member instanceof AbstractWolfismWolf other
                        && NewYearsCooldownSupport.countEligible(other) >= 2)
                .max(Comparator.comparingDouble(this::newYearsNeedScore))
                .orElse(null);

        if (proactive != null && this.secondChanceCooldown == 0) {
            this.tryProactiveSecondChance(level, proactive);
            return;
        }

        if (this.midnightResetCooldown == 0 && (harmful > 0 || fatigued > 0)) {
            this.startMidnightReset(level);
            return;
        }

        if (this.countdownCooldown == 0
                && hostileCount >= 2
                && (injured + harmful + fatigued > 0 || hostileCount >= 3)) {
            this.startCountdown(level);
        }
    }

    private void tickCooldowns() {
        if (this.midnightResetCooldown > 0) --this.midnightResetCooldown;
        if (this.countdownCooldown > 0) --this.countdownCooldown;
        if (this.secondChanceCooldown > 0) --this.secondChanceCooldown;
        if (this.newBeginningCooldown > 0) --this.newBeginningCooldown;
    }

    private void cancelNewYearsStates() {
        this.countdownTicks = 0;
        this.newBeginningTicks = 0;
        this.renewalVisualTicks = 0;
        this.entityData.set(DATA_RENEWAL_ACTIVE, false);
        this.removeResolutionModifier();
    }

    private void applyFreshStart(ServerLevel level) {
        if (this.quietTicks < FRESH_START_CALM_TICKS) {
            return;
        }

        long now = level.getGameTime();
        int recipients = 0;
        int effects = 0;
        int cooldownFields = 0;

        for (LivingEntity member : this.getNewYearsFamily(level, FRESH_START_RADIUS)) {
            if (!this.isNewYearsRecipient(member)
                    || member.getPersistentData()
                    .getLongOr(NEXT_FRESH_START, 0L) > now) {
                continue;
            }

            boolean changed = false;
            if (shortenOneHarmfulEffect(member, FRESH_START_EFFECT_REDUCTION)) {
                ++effects;
                changed = true;
            }

            if (member instanceof AbstractWolfismWolf other) {
                NewYearsCooldownSupport.RefreshResult result =
                        NewYearsCooldownSupport.refresh(
                                other,
                                FRESH_START_COOLDOWN_REFRESH);
                cooldownFields += result.fieldsRefreshed();
                changed |= result.fieldsRefreshed() > 0;
            }

            if (!changed) {
                continue;
            }

            member.getPersistentData().putLong(
                    NEXT_FRESH_START,
                    now + FRESH_START_PULSE_INTERVAL);
            ++recipients;
            WolfVfx.sendParticles("new_years_wolf", level,
                    RENEWAL_CYAN,
                    member.getX(), member.getY(0.55D), member.getZ(),
                    4, 0.25D, 0.25D, 0.25D, 0.0D);
        }

        if (recipients > 0) {
            ++this.freshStartPulseCount;
            this.freshStartRecipients += recipients;
            this.freshStartEffectsShortened += effects;
            this.freshStartCooldownFields += cooldownFields;
            this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 20);
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25F, 1.65F);
        }
    }

    private void startMidnightReset(ServerLevel level) {
        this.midnightResetCooldown = MIDNIGHT_RESET_COOLDOWN;
        this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 60);
        ++this.midnightResetCount;

        long now = level.getGameTime();
        int recipients = 0;
        int effectsRemoved = 0;
        int cooldownFields = 0;

        for (LivingEntity member : this.getNewYearsFamily(level, SUPPORT_RADIUS)) {
            if (!this.isNewYearsRecipient(member)
                    || member.getPersistentData()
                    .getLongOr(NEXT_MIDNIGHT_RESET, 0L) > now) {
                continue;
            }

            boolean changed = false;
            if (removeOneHarmfulEffect(member)) {
                ++effectsRemoved;
                changed = true;
            }
            changed |= shortenOneHarmfulEffect(
                    member,
                    MIDNIGHT_RESET_EFFECT_REDUCTION);

            if (member instanceof AbstractWolfismWolf other) {
                NewYearsCooldownSupport.RefreshResult result =
                        NewYearsCooldownSupport.refresh(
                                other,
                                MIDNIGHT_RESET_COOLDOWN_REFRESH);
                cooldownFields += result.fieldsRefreshed();
                changed |= result.fieldsRefreshed() > 0;
            }

            if (!changed) {
                continue;
            }

            member.getPersistentData().putLong(
                    NEXT_MIDNIGHT_RESET,
                    now + MIDNIGHT_RESET_RECIPIENT_GAP);
            ++recipients;
            this.renewalBurst(level, member.position(), 8, false);
        }

        this.midnightResetRecipients += recipients;
        this.midnightResetEffectsRemoved += effectsRemoved;
        this.midnightResetCooldownFields += cooldownFields;
        this.renewalBurst(level, this.position(), 18, true);
        this.playSound(SoundEvents.BELL_BLOCK, 0.55F, 1.35F);
    }

    private void startCountdown(ServerLevel level) {
        this.countdownTicks = COUNTDOWN_DURATION;
        this.countdownCooldown = COUNTDOWN_COOLDOWN;
        this.renewalVisualTicks = Math.max(this.renewalVisualTicks, COUNTDOWN_DURATION);
        ++this.countdownCount;
        this.emitCountdownStage(level, 3);
    }

    private void tickCountdown(ServerLevel level) {
        --this.countdownTicks;
        if (this.countdownTicks == 40) {
            this.emitCountdownStage(level, 2);
        } else if (this.countdownTicks == 20) {
            this.emitCountdownStage(level, 1);
        } else if (this.countdownTicks <= 0) {
            this.finishCountdown(level);
        }
    }

    private void emitCountdownStage(ServerLevel level, int stage) {
        double height = this.getY(0.80D) + (3 - stage) * 0.18D;
        WolfVfx.sendParticles("new_years_wolf", level,
                stage == 1 ? RENEWAL_CYAN : COUNTDOWN_GOLD,
                this.getX(), height, this.getZ(),
                9, 0.32D, 0.20D, 0.32D, 0.0D);
        this.playSound(
                stage == 1 ? SoundEvents.NOTE_BLOCK_PLING.value()
                        : SoundEvents.NOTE_BLOCK_HAT.value(),
                0.55F,
                0.85F + (3 - stage) * 0.25F);

        this.sendOwnerOverlay(
                Component.literal(Integer.toString(stage))
                        .withStyle(stage == 1
                                ? ChatFormatting.AQUA
                                : ChatFormatting.GOLD));
    }

    private void finishCountdown(ServerLevel level) {
        this.countdownTicks = 0;
        this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 40);
        ++this.countdownCompletionCount;

        for (LivingEntity member : this.getNewYearsFamily(level, SUPPORT_RADIUS)) {
            applyAtLeast(member, MobEffects.SPEED, COUNTDOWN_BUFF_DURATION, 0);
            applyAtLeast(member, MobEffects.STRENGTH, COUNTDOWN_BUFF_DURATION, 0);
            WolfVfx.sendParticles("new_years_wolf", level,
                    NEW_YEAR_SILVER,
                    member.getX(), member.getY(0.55D), member.getZ(),
                    5, 0.25D, 0.28D, 0.25D, 0.0D);
        }

        for (LivingEntity threat : this.getNewYearsThreats(level)) {
            applyAtLeast(threat, MobEffects.GLOWING, COUNTDOWN_BUFF_DURATION, 0);
        }

        this.sendOwnerOverlay(
                Component.literal("A NEW BEGINNING!")
                        .withStyle(ChatFormatting.AQUA));

        this.renewalBurst(level, this.position(), 32, true);
        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.80F, 1.20F);
        this.playSound(SoundEvents.BELL_BLOCK, 0.55F, 1.75F);
    }

    private void startNewBeginning(ServerLevel level) {
        this.countdownTicks = 0;
        this.newBeginningTicks = NEW_BEGINNING_DURATION;
        this.newBeginningCooldown = NEW_BEGINNING_COOLDOWN;
        this.renewalVisualTicks = Math.max(
                this.renewalVisualTicks,
                NEW_BEGINNING_DURATION);
        ++this.newBeginningCount;

        this.sendOwnerOverlay(
                Component.literal("NEW BEGINNING - THE PACK RENEWS!")
                        .withStyle(ChatFormatting.GOLD));
        this.emitNewBeginningBeacon(level, true);

        // Apply the opening support pulse immediately rather than waiting for
        // this entity's next one-second tick alignment.
        this.pulseNewBeginning(level);

        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.90F, 0.95F);
        this.playSound(SoundEvents.BELL_BLOCK, 0.75F, 1.45F);
        this.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.85F, 1.15F);
    }

    private void finishNewBeginning(ServerLevel level) {
        this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 40);
        this.emitNewBeginningBeacon(level, true);
        this.renewalBurst(
                level,
                this.position().add(0.0D, 1.25D, 0.0D),
                28,
                true);
        this.sendOwnerOverlay(
                Component.literal("THE PACK IS RENEWED.")
                        .withStyle(ChatFormatting.AQUA));
        this.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.70F, 1.45F);
    }

    /**
     * Tall rotating clock/firework marker that remains visible above a crowded
     * pack. It identifies the casting New Year's Wolf without applying Glowing
     * or changing unrelated family effects.
     */
    private void emitNewBeginningBeacon(ServerLevel level, boolean majorBurst) {
        ++this.newBeginningVisualPulseCount;

        double age = NEW_BEGINNING_DURATION - this.newBeginningTicks;
        double phase = age * 0.16D;
        double baseY = this.getY() + 0.30D;

        int ringPoints = majorBurst ? 16 : 10;
        int ringCount = majorBurst ? 3 : 1;

        for (int ring = 0; ring < ringCount; ++ring) {
            double radius = majorBurst
                    ? 1.60D + ring * 1.35D
                    : 2.65D;

            for (int i = 0; i < ringPoints; ++i) {
                double angle = Math.PI * 2.0D * i / ringPoints
                        + phase
                        + ring * 0.25D;
                double y = baseY
                        + ring * 0.18D
                        + Math.sin(angle * 2.0D + phase) * 0.10D;

                WolfVfx.sendParticles("new_years_wolf", level,
                        (((i + ring) & 1) == 0)
                                ? RENEWAL_CYAN
                                : COUNTDOWN_GOLD,
                        this.getX() + Math.cos(angle) * radius,
                        y,
                        this.getZ() + Math.sin(angle) * radius,
                        1,
                        0.01D,
                        0.01D,
                        0.01D,
                        0.0D);
            }
        }

        int spiralPoints = majorBurst ? 12 : 7;
        for (int i = 0; i < spiralPoints; ++i) {
            double progress = i / (double) Math.max(1, spiralPoints - 1);
            double angle = phase + i * 1.10D;
            double radius = 0.38D + progress * 0.35D;

            WolfVfx.sendParticles("new_years_wolf", level,
                    i % 3 == 0
                            ? NEW_YEAR_SILVER
                            : i % 2 == 0
                                    ? COUNTDOWN_GOLD
                                    : RENEWAL_CYAN,
                    this.getX() + Math.cos(angle) * radius,
                    baseY + 0.35D + progress * 3.40D,
                    this.getZ() + Math.sin(angle) * radius,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }

        if (majorBurst || this.newBeginningTicks % 20 == 0) {
            WolfVfx.sendParticles("new_years_wolf", level,
                    ParticleTypes.FIREWORK,
                    this.getX(),
                    baseY + 3.85D,
                    this.getZ(),
                    majorBurst ? 12 : 4,
                    0.45D,
                    0.28D,
                    0.45D,
                    0.03D);
        }
    }

    private void sendOwnerOverlay(Component message) {
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(message);
        }
    }

    private void pulseNewBeginning(ServerLevel level) {
        ++this.newBeginningPulseCount;
        long now = level.getGameTime();

        for (LivingEntity member : this.getNewYearsFamily(level, NEW_BEGINNING_RADIUS)) {
            // Healing and the cooldown pulse share a recipient throttle across
            // all casting wolves; a large New Year's pack cannot multiply them.
            if (member.getPersistentData().getLongOr(NEXT_NEW_BEGINNING_PULSE, 0L) > now) {
                continue;
            }
            member.getPersistentData().putLong(NEXT_NEW_BEGINNING_PULSE, now + 20);
            applyAtLeast(member, MobEffects.REGENERATION, 50, 0);
            applyAtLeast(member, MobEffects.SPEED, 50, 0);
            applyAtLeast(member, MobEffects.STRENGTH, 50, 0);

            if (member.getHealth() < member.getMaxHealth()) {
                member.heal(0.5F);
            }

            if (member.getPersistentData()
                    .getLongOr(NEXT_NEW_BEGINNING_CLEANSE, 0L) <= now) {
                if (removeOneHarmfulEffect(member)) {
                    ++this.newBeginningEffectsRemoved;
                } else {
                    shortenOneHarmfulEffect(member, 20 * 4);
                }
                member.getPersistentData().putLong(
                        NEXT_NEW_BEGINNING_CLEANSE,
                        now + NEW_BEGINNING_CLEANSE_GAP);
            }

            if (member instanceof AbstractWolfismWolf other) {
                NewYearsCooldownSupport.RefreshResult result =
                        NewYearsCooldownSupport.refresh(other, 20);
                this.newBeginningCooldownFields += result.fieldsRefreshed();
            }

            this.regroupIdleFamily(member);
        }

        for (LivingEntity threat : this.getNewYearsThreats(level)) {
            applyAtLeast(threat, MobEffects.GLOWING, 45, 0);
        }

        if (this.isWolfismWorkTick(40)) {
            this.renewalBurst(level, this.position(), 14, false);
        }
    }

    private void regroupIdleFamily(LivingEntity member) {
        if (!(member instanceof AbstractWolfismWolf other)
                || other == this
                || other.hasWolfStaffCommand()
                || other.getTarget() != null
                || other.isOrderedToSit()
                || other.isInSittingPose()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || owner.level() != this.level()) {
            return;
        }

        if (member.distanceToSqr(owner) > 8.0D * 8.0D
                && member.distanceToSqr(owner) <= 24.0D * 24.0D) {
            other.getNavigation().moveTo(owner, 1.15D);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!this.canParticipateInWolfismCombat()) {
            this.setTarget(null);
            return false;
        }

        boolean resolution = this.resolutionChargeTicks >= RESOLUTION_READY_TICKS;
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);

        if (resolution && attack != null) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(
                    RESOLUTION_MODIFIER_ID,
                    RESOLUTION_BONUS_DAMAGE,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            this.removeResolutionModifier();
        }

        if (hit) {
            this.resolutionChargeTicks = 0;
            if (resolution && target instanceof LivingEntity living) {
                ++this.resolutionStrikeCount;
                living.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        20 * 3,
                        0,
                        true,
                        true),
                        this);
                living.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        20 * 3,
                        0,
                        true,
                        true),
                        this);
                this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 20);
                this.renewalBurst(level, living.position(), 10, false);
            }
        }

        return hit;
    }

    private void removeResolutionModifier() {
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(RESOLUTION_MODIFIER_ID);
        }
    }

    public double secondChanceNeedScore(LivingEntity member) {
        if (!this.canOfferSecondChanceTo(member)) {
            return 0.0D;
        }
        double healthFraction = member.getHealth()
                / Math.max(1.0F, member.getMaxHealth());
        return (1.0D - healthFraction) * 100.0D
                + countHarmfulEffects(member) * 16.0D
                + (member instanceof AbstractWolfismWolf other
                ? NewYearsCooldownSupport.countEligible(other) * 8.0D
                : 0.0D);
    }

    public boolean canOfferSecondChanceTo(LivingEntity member) {
        return this.canProvideNewYearsSupport()
                && this.isTame()
                && this.secondChanceCooldown == 0
                && this.isNewYearsRecipient(member)
                && this.distanceToSqr(member)
                <= SECOND_CHANCE_RADIUS * SECOND_CHANCE_RADIUS;
    }

    /**
     * Called before an incoming critical hit is applied. Returns the adjusted
     * damage amount; it never resurrects a dead entity.
     */
    public float trySecondChance(
            ServerLevel level,
            LivingEntity member,
            DamageSource source,
            float amount) {
        if (!this.canOfferSecondChanceTo(member)
                || amount <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }

        float currentEffective = member.getHealth() + member.getAbsorptionAmount();
        float projected = currentEffective - amount;
        int harmful = countHarmfulEffects(member);
        int fatigued = member instanceof AbstractWolfismWolf other
                ? NewYearsCooldownSupport.countEligible(other)
                : 0;

        if (projected > member.getMaxHealth() * 0.25F
                && !(member.getHealth() <= member.getMaxHealth() * 0.50F
                && (harmful >= 2 || fatigued >= 2))) {
            return amount;
        }

        long now = level.getGameTime();
        if (member.getPersistentData()
                .getLongOr(NEXT_SECOND_CHANCE, 0L) > now) {
            return amount;
        }

        boolean lethal = projected <= 0.0F;
        float adjusted = amount * 0.55F;
        if (lethal) {
            adjusted = Math.min(adjusted, Math.max(0.0F, currentEffective - 1.0F));
        }

        this.applySecondChancePackage(level, member, lethal);
        return adjusted;
    }

    private boolean tryProactiveSecondChance(ServerLevel level, LivingEntity member) {
        if (!this.canOfferSecondChanceTo(member)) {
            return false;
        }
        long now = level.getGameTime();
        if (member.getPersistentData()
                .getLongOr(NEXT_SECOND_CHANCE, 0L) > now) {
            return false;
        }
        this.applySecondChancePackage(level, member, false);
        return true;
    }

    private void applySecondChancePackage(
            ServerLevel level,
            LivingEntity member,
            boolean lethalSave) {
        long now = level.getGameTime();
        this.secondChanceCooldown = SECOND_CHANCE_COOLDOWN;
        member.getPersistentData().putLong(
                NEXT_SECOND_CHANCE,
                now + SECOND_CHANCE_RECIPIENT_GAP);

        removeUpToHarmfulEffects(member, 2);
        if (member instanceof AbstractWolfismWolf other) {
            NewYearsCooldownSupport.refresh(other, 20 * 2);
        }

        applyAtLeast(member, MobEffects.ABSORPTION, 20 * 6, 1);
        applyAtLeast(member, MobEffects.RESISTANCE, 20 * 6, 0);
        applyAtLeast(member, MobEffects.SPEED, 20 * 6, 0);
        member.heal(Math.min(5.0F, Math.max(2.0F, member.getMaxHealth() * 0.10F)));

        ++this.secondChanceCount;
        if (lethalSave) {
            ++this.secondChanceLethalSaves;
        }
        this.renewalVisualTicks = Math.max(this.renewalVisualTicks, 80);
        this.emitSecondChanceLink(level, member);
        this.playSound(SoundEvents.BELL_BLOCK, 0.75F, 1.55F);
        member.playSound(SoundEvents.TOTEM_USE, 0.45F, 1.35F);
    }

    private void emitSecondChanceLink(ServerLevel level, LivingEntity member) {
        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.70D, 0.0D);
        Vec3 end = member.position().add(0.0D, member.getBbHeight() * 0.62D, 0.0D);
        Vec3 difference = end.subtract(start);
        int points = Math.max(5, Math.min(16,
                (int) Math.ceil(difference.length() * 1.7D)));

        for (int i = 1; i < points; ++i) {
            Vec3 point = start.add(difference.scale(i / (double) points));
            WolfVfx.sendParticles("new_years_wolf", level,
                    (i & 1) == 0 ? RENEWAL_CYAN : COUNTDOWN_GOLD,
                    point.x, point.y, point.z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }

        WolfVfx.sendParticles("new_years_wolf", level,
                ParticleTypes.TOTEM_OF_UNDYING,
                member.getX(), member.getY(0.60D), member.getZ(),
                18, 0.40D, 0.45D, 0.40D, 0.05D);
    }

    private boolean isEngagedFamilyThreat(LivingEntity threat) {
        return threat instanceof Mob mob && this.isNewYearsFamily(mob.getTarget());
    }

    private static boolean hasLiveCombatTarget(LivingEntity member) {
        return member instanceof Mob mob
                && mob.getTarget() != null
                && mob.getTarget().isAlive();
    }

    private static boolean removeOneHarmfulEffect(LivingEntity member) {
        for (MobEffectInstance instance : new ArrayList<>(member.getActiveEffects())) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                    && member.removeEffect(instance.getEffect())) {
                return true;
            }
        }
        return false;
    }

    private static int removeUpToHarmfulEffects(LivingEntity member, int maximum) {
        int removed = 0;
        for (MobEffectInstance instance : new ArrayList<>(member.getActiveEffects())) {
            if (removed >= maximum) {
                break;
            }
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                    && member.removeEffect(instance.getEffect())) {
                ++removed;
            }
        }
        return removed;
    }

    private static boolean shortenOneHarmfulEffect(LivingEntity member, int ticks) {
        for (MobEffectInstance instance : new ArrayList<>(member.getActiveEffects())) {
            if (instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }

            Holder<MobEffect> effect = instance.getEffect();
            int remaining = instance.getDuration() - ticks;
            if (!member.removeEffect(effect)) {
                continue;
            }

            if (remaining > 0) {
                member.addEffect(new MobEffectInstance(
                        effect,
                        remaining,
                        instance.getAmplifier(),
                        instance.isAmbient(),
                        instance.isVisible(),
                        instance.showIcon()));
            }
            return true;
        }
        return false;
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
            entity.addEffect(new MobEffectInstance(
                    effect,
                    duration,
                    amplifier,
                    true,
                    true));
        }
    }

    private void renewalBurst(
            ServerLevel level,
            Vec3 center,
            int count,
            boolean fireworks) {
        WolfVfx.sendParticles("new_years_wolf", level,
                MIDNIGHT_BLUE,
                center.x, center.y + 0.55D, center.z,
                count, 0.62D, 0.38D, 0.62D, 0.0D);
        WolfVfx.sendParticles("new_years_wolf", level,
                COUNTDOWN_GOLD,
                center.x, center.y + 0.62D, center.z,
                Math.max(4, count / 2), 0.50D, 0.34D, 0.50D, 0.0D);
        WolfVfx.sendParticles("new_years_wolf", level,
                NEW_YEAR_SILVER,
                center.x, center.y + 0.68D, center.z,
                Math.max(3, count / 3), 0.42D, 0.30D, 0.42D, 0.0D);
        if (fireworks) {
            WolfVfx.sendParticles("new_years_wolf", level,
                    ParticleTypes.FIREWORK,
                    center.x, center.y + 0.85D, center.z,
                    Math.max(3, count / 4), 0.45D, 0.45D, 0.45D, 0.04D);
        }
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        if (!finishing) {
            int remaining = this.getHolidayRecoveryTicks();
            int stage = remaining == 60 ? 3 : remaining == 40 ? 2 : remaining == 20 ? 1 : 0;
            if (stage > 0 && stage != this.lastRecoveryCountdownStage) {
                this.lastRecoveryCountdownStage = stage;
                this.emitRecoveryCountdown(level, stage);
            }

            WolfVfx.sendParticles("new_years_wolf", level,
                    MIDNIGHT_BLUE,
                    this.getX(), this.getY(0.45D), this.getZ(),
                    5, 0.45D, 0.25D, 0.45D, 0.0D);
            return;
        }

        this.lastRecoveryCountdownStage = 0;
        this.renewalBurst(level, this.position(), 30, true);
        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.60F, 1.30F);
        this.playSound(SoundEvents.BELL_BLOCK, 0.55F, 1.60F);
    }

    private void emitRecoveryCountdown(ServerLevel level, int stage) {
        WolfVfx.sendParticles("new_years_wolf", level,
                stage == 1 ? RENEWAL_CYAN : COUNTDOWN_GOLD,
                this.getX(), this.getY(0.75D), this.getZ(),
                8, 0.30D, 0.20D, 0.30D, 0.0D);
        this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.50F,
                0.90F + (3 - stage) * 0.28F);
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(
                    Component.literal(Integer.toString(stage))
                            .withStyle(ChatFormatting.AQUA));
        }
    }

    public static boolean checkNewYearsWolfSpawnRules(
            EntityType<NewYearsWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        return level.getLevel().dimension() == Level.OVERWORLD
                && isNewYearsSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.NEW_YEARS_WOLF_SPAWNS)
                && isSafeNewYearsSurface(level, pos);
    }

    public static boolean isSafeNewYearsSurface(
            ServerLevelAccessor level,
            BlockPos pos) {
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
                || floor.is(Blocks.SNOW_BLOCK)
                || floor.is(Blocks.STONE));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("NewYearsMidnightResetCooldown", this.midnightResetCooldown);
        output.putInt("NewYearsCountdownCooldown", this.countdownCooldown);
        output.putInt("NewYearsSecondChanceCooldown", this.secondChanceCooldown);
        output.putInt("NewYearsNewBeginningCooldown", this.newBeginningCooldown);
        output.putInt("NewYearsResolutionChargeTicks", this.resolutionChargeTicks);

        ValueOutput diagnostics = output.child("NewYearsDiagnostics");
        diagnostics.putString("SupportState", this.supportBlockReason());
        diagnostics.putBoolean("RenewalVisualActive", this.isRenewalVisualActive());
        diagnostics.putBoolean("CountdownActive", this.isCountdownActive());
        diagnostics.putBoolean("NewBeginningActive", this.isNewBeginningActive());
        diagnostics.putBoolean("ResolutionReady",
                this.resolutionChargeTicks >= RESOLUTION_READY_TICKS);
        diagnostics.putBoolean("DirectCombatPressure", this.directCombatPressure);
        diagnostics.putInt("QuietTicks", this.quietTicks);
        diagnostics.putInt("FreshStartPulseCount", this.freshStartPulseCount);
        diagnostics.putInt("FreshStartRecipients", this.freshStartRecipients);
        diagnostics.putInt("FreshStartEffectsShortened", this.freshStartEffectsShortened);
        diagnostics.putInt("FreshStartCooldownFields", this.freshStartCooldownFields);
        diagnostics.putInt("MidnightResetCount", this.midnightResetCount);
        diagnostics.putInt("MidnightResetRecipients", this.midnightResetRecipients);
        diagnostics.putInt("MidnightResetEffectsRemoved", this.midnightResetEffectsRemoved);
        diagnostics.putInt("MidnightResetCooldownFields", this.midnightResetCooldownFields);
        diagnostics.putInt("ResolutionStrikeCount", this.resolutionStrikeCount);
        diagnostics.putInt("CountdownCount", this.countdownCount);
        diagnostics.putInt(
                "CountdownCompletionCount",
                this.countdownCompletionCount);
        diagnostics.putInt("CountdownTicks", this.countdownTicks);
        diagnostics.putInt("SecondChanceCount", this.secondChanceCount);
        diagnostics.putInt("SecondChanceLethalSaves", this.secondChanceLethalSaves);
        diagnostics.putInt("NewBeginningCount", this.newBeginningCount);
        diagnostics.putInt("NewBeginningTicks", this.newBeginningTicks);
        diagnostics.putInt("NewBeginningPulseCount", this.newBeginningPulseCount);
        diagnostics.putInt(
                "NewBeginningVisualPulseCount",
                this.newBeginningVisualPulseCount);
        diagnostics.putInt("NewBeginningEffectsRemoved", this.newBeginningEffectsRemoved);
        diagnostics.putInt("NewBeginningCooldownFields", this.newBeginningCooldownFields);
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
        this.midnightResetCooldown = Math.max(
                0,
                input.getIntOr("NewYearsMidnightResetCooldown", 0));
        this.countdownCooldown = Math.max(
                0,
                input.getIntOr("NewYearsCountdownCooldown", 0));
        this.secondChanceCooldown = Math.max(
                0,
                input.getIntOr("NewYearsSecondChanceCooldown", 0));
        this.newBeginningCooldown = Math.max(
                0,
                input.getIntOr("NewYearsNewBeginningCooldown", 0));
        this.resolutionChargeTicks = Math.max(
                0,
                Math.min(
                        RESOLUTION_READY_TICKS,
                        input.getIntOr(
                                "NewYearsResolutionChargeTicks",
                                RESOLUTION_READY_TICKS)));
        this.cancelNewYearsStates();
    }
}
