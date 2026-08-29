package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.BloodPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.BloodPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.BloodPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.BloodPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.BloodPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.BloodWolfCombatGoal;
import net.ronm19.wolfism.entity.ai.sensor.BloodWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #26 - Blood Wolf.
 *
 * <p>Role: physical berserker / executioner / raw melee DPS.</p>
 *
 * <p>Combat loop:</p>
 * <pre>
 * Find wounded target -> reach wounded target -> destroy wounded target
 * -> use the momentum to find the next bastard.
 * </pre>
 *
 * <p>This class intentionally contains no blood projectiles, lifesteal, magical
 * blood artillery, or permanent crimson aura. Blood Wolf is frightening because
 * of target selection, movement, pain tolerance, chaining and escalating melee
 * pressure - not because he becomes a blood mage.</p>
 */
public final class BloodWolf extends AbstractWolfismWolf {

    /*
     * Only visual state is synchronized. The authoritative timers and combat
     * math stay server-side; clients simply need to know when to render Blood
     * with vanilla angry/berserker body language.
     */
    private static final EntityDataAccessor<Boolean> DATA_BLOOD_FRENZY_VISUAL =
            SynchedEntityData.defineId(BloodWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BLOOD_BERSERKER_VISUAL =
            SynchedEntityData.defineId(BloodWolf.class, EntityDataSerializers.BOOLEAN);

    // ---------------------------------------------------------------------
    // Baseline / ability timing
    // ---------------------------------------------------------------------

    private static final double BLOOD_WOLF_BASE_MAX_HEALTH = 40.0D;

    public static final int BLOOD_FRENZY_COOLDOWN_TICKS = 20 * 25;
    public static final int BLOOD_SHIELD_COOLDOWN_TICKS = 20 * 15;
    public static final int BLOOD_MODE_COOLDOWN_TICKS = 20 * 18;
    public static final int BLOOD_BERSERKER_COOLDOWN_TICKS = 20 * 90;

    public static final int BLOOD_SHIELD_DURATION_TICKS = 20 * 5;
    public static final int BLOOD_MODE_DURATION_TICKS = 20 * 8;

    public static final int BLOOD_BERSERKER_BASE_DURATION_TICKS = 20 * 14;
    public static final int BLOOD_BERSERKER_HARD_MAX_TICKS = 20 * 20;
    public static final int BLOOD_BERSERKER_KILL_EXTENSION_TICKS = 30;

    public static final int BLOODLUST_MAX_STACKS = 4;
    public static final int BLOOD_MODE_NORMAL_CHAIN_CAP = 5;
    public static final int BLOOD_MODE_BERSERKER_CHAIN_CAP = 6;

    private static final int BLOOD_HUNTER_NORMAL_SCAN_INTERVAL = 8;
    private static final int BLOOD_HUNTER_BERSERKER_SCAN_INTERVAL = 4;
    private static final double BLOOD_HUNTER_NORMAL_RANGE = 24.0D;
    private static final double BLOOD_HUNTER_BERSERKER_RANGE = 36.0D;
    private static final double BLOOD_MODE_CHAIN_RADIUS = 6.5D;
    private static final double BLOOD_MODE_BERSERKER_CHAIN_RADIUS = 8.0D;

    private static final float BLOOD_HUNTER_EXECUTION_THRESHOLD = 0.35F;
    private static final float BLOOD_FRENZY_START_HEALTH = 0.85F;
    private static final float BLOOD_FRENZY_MAX_HEALTH = 0.25F;
    private static final float BLOOD_FRENZY_END_HEALTH = 0.95F;
    private static final float BLOOD_SHIELD_TRIGGER_HEALTH = 0.68F;
    private static final float BERSERKER_HEALTH_TRIGGER = 0.45F;
    private static final float BERSERKER_HEAVY_TARGET_HEALTH = 60.0F;

    private static final int FRENZY_COMBAT_GRACE_TICKS = 60;
    private static final int BLOOD_CHAIN_RESET_TICKS = 12;

    // ---------------------------------------------------------------------
    // Physical combat tuning
    // ---------------------------------------------------------------------

    /** Normal Blood Shield: 28% incoming combat damage reduction. */
    private static final float BLOOD_SHIELD_DAMAGE_MULTIPLIER = 0.72F;

    /** Ultimate pain tolerance: 40% incoming combat damage reduction. */
    private static final float BERSERKER_DAMAGE_MULTIPLIER = 0.60F;

    private static final double FRENZY_MAX_DAMAGE_BONUS = 0.45D;
    private static final double FRENZY_MAX_SPEED_BONUS = 0.25D;
    private static final double FRENZY_MAX_KNOCKBACK_RESISTANCE_BONUS = 0.40D;

    private static final double BLOODLUST_DAMAGE_PER_STACK = 0.05D;
    private static final double BLOODLUST_SPEED_PER_STACK = 0.02D;

    private static final double BERSERKER_DAMAGE_BONUS = 0.15D;
    private static final double BERSERKER_SPEED_BONUS = 0.12D;
    private static final double BERSERKER_KNOCKBACK_RESISTANCE_BONUS = 0.15D;
    private static final double BLOOD_SHIELD_KNOCKBACK_RESISTANCE_BONUS = 0.30D;

    /** Keep total bonus below full immunity; base Blood Wolf already has 0.10 KBR. */
    private static final double MAX_TRANSIENT_KNOCKBACK_RESISTANCE_BONUS = 0.85D;

    private static final Identifier BLOOD_DAMAGE_MODIFIER = Identifier.fromNamespaceAndPath(
            "wolfism", "blood_wolf_combat_damage");
    private static final Identifier BLOOD_SPEED_MODIFIER = Identifier.fromNamespaceAndPath(
            "wolfism", "blood_wolf_combat_speed");
    private static final Identifier BLOOD_KNOCKBACK_MODIFIER = Identifier.fromNamespaceAndPath(
            "wolfism", "blood_wolf_combat_knockback_resistance");

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private boolean bloodFrenzyActive;
    private int bloodFrenzyCooldownTicks;
    private int frenzyCalmTicks;

    private int bloodShieldTicks;
    private int bloodShieldCooldownTicks;

    private int bloodModeTicks;
    private int bloodModeCooldownTicks;
    private int bloodChainResetTicks;
    private final Set<Integer> bloodChainVictims = new HashSet<>();

    private int bloodlustStacks;
    private int bloodlustTicks;

    private int bloodBerserkerTicks;
    private int bloodBerserkerElapsedTicks;
    private int bloodBerserkerCooldownTicks;

    private int bloodHunterScanTicks;
    private int bloodHunterRetargetLockTicks;

    /**
     * Lazy Brain provider: registry-backed SensorTypes are resolved only after
     * NeoForge has finished setting the registries up.
     */
    private static final class BrainHolder {
        private static final Brain.Provider<BloodWolf> PROVIDER = Brain.<BloodWolf>provider(
                ImmutableList.of(ModSensorTypes.BLOOD_PACK.get()),
                wolf -> List.of());
    }

    public BloodWolf(EntityType<? extends BloodWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLOOD_FRENZY_VISUAL, false);
        builder.define(DATA_BLOOD_BERSERKER_VISUAL, false);
    }

    public boolean isBloodCombatVisualActive() {
        return this.entityData.get(DATA_BLOOD_FRENZY_VISUAL)
                || this.entityData.get(DATA_BLOOD_BERSERKER_VISUAL);
    }

    private void syncBloodCombatVisualState() {
        this.entityData.set(DATA_BLOOD_FRENZY_VISUAL, this.bloodFrenzyActive);
        this.entityData.set(DATA_BLOOD_BERSERKER_VISUAL, this.isBloodBerserkerActive());
    }

    // ---------------------------------------------------------------------
    // Registration / baseline stats
    // ---------------------------------------------------------------------

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.BLOOD_WOLF.get();
    }

    /**
     * Strong enough to be a standalone executioner, but not an Infernal/Dire tank.
     * His absurdity comes from state escalation rather than a gigantic static statline.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BLOOD_WOLF_BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D);
    }

    /**
     * Vanilla Wolf changes its base max health when taming state side-effects run.
     * In particular, loading an untamed wolf can otherwise reset a Blood Wolf to
     * vanilla's 8-health wild value. Blood keeps its species health in both wild
     * and tamed states, while actual taming still restores it to full health.
     */
    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(BLOOD_WOLF_BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Shared family emergency + Creeper intelligence are inherited first.
        // Blood pups then get their normal retreat/follow/play architecture.
        this.goalSelector.addGoal(1, new BloodPupRetreatGoal(this));

        /*
         * Priority 2 deliberately sits below Wolfism's priority-0 Creeper retreat
         * and priority-1 Creeper pack attack. Blood's special melee motor therefore
         * never steals ownership of validated Creeper combat.
         */
        this.goalSelector.addGoal(2, new BloodWolfCombatGoal(this));

        this.goalSelector.addGoal(5, new BloodPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new BloodPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new BloodPupPlayGoal(this));

        // Species pack communication supplements universal family defense.
        this.targetSelector.addGoal(3, new BloodPackAssistGoal(this));
    }

    @Override
    protected Brain<BloodWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<BloodWolf> getBrain() {
        return (Brain<BloodWolf>) super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Server state machine
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof BloodWolf blood && this.isBloodPackmate(blood)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelAdultBloodStates();
            return;
        }

        this.tickCooldownsAndDurations(level);
        this.tickBloodFrenzy(level);
        this.tickBloodHunter(level);
        this.tryStartBloodMode(level);
        this.tryStartBloodBerserker(level);

        if (this.tickCount % 4 == 0) {
            this.refreshBloodCombatAttributes();
        }
    }

    private void tickCooldownsAndDurations(ServerLevel level) {
        if (this.bloodFrenzyCooldownTicks > 0) {
            --this.bloodFrenzyCooldownTicks;
        }
        if (this.bloodShieldCooldownTicks > 0) {
            --this.bloodShieldCooldownTicks;
        }
        if (this.bloodModeCooldownTicks > 0) {
            --this.bloodModeCooldownTicks;
        }
        if (this.bloodBerserkerCooldownTicks > 0) {
            --this.bloodBerserkerCooldownTicks;
        }
        if (this.bloodHunterRetargetLockTicks > 0) {
            --this.bloodHunterRetargetLockTicks;
        }
        if (this.bloodChainResetTicks > 0 && --this.bloodChainResetTicks == 0) {
            this.bloodChainVictims.clear();
        }

        if (this.bloodShieldTicks > 0) {
            --this.bloodShieldTicks;
            if (this.bloodShieldTicks == 0) {
                this.bloodShieldCooldownTicks = BLOOD_SHIELD_COOLDOWN_TICKS;
            }
        }

        if (this.bloodModeTicks > 0) {
            --this.bloodModeTicks;
            if (this.bloodModeTicks == 0) {
                this.bloodModeCooldownTicks = BLOOD_MODE_COOLDOWN_TICKS;
                if (!this.isBloodBerserkerActive()) {
                    this.resetBloodChain();
                }
            }
        }

        if (this.bloodlustTicks > 0) {
            --this.bloodlustTicks;
            if (this.bloodlustTicks == 0) {
                this.bloodlustStacks = 0;
            }
        }

        if (this.bloodBerserkerTicks > 0) {
            --this.bloodBerserkerTicks;
            ++this.bloodBerserkerElapsedTicks;

            if (this.bloodBerserkerTicks == 0) {
                this.finishBloodBerserker(level);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Blood Hunter - wounded-target execution intelligence
    // ---------------------------------------------------------------------

    private void tickBloodHunter(ServerLevel level) {
        if (!this.canUseBloodCombatSystems()) {
            return;
        }

        /* Family defense remains absolute. Blood does not abandon a family attacker
         * because a random Zombie nearby happens to have fewer hearts. */
        if (this.hasFamilyDefenseEmergency()) {
            return;
        }

        LivingEntity current = this.getTarget();

        /* Shared Wolfism Creeper AI owns a Creeper once selected. */
        if (current instanceof Creeper) {
            return;
        }

        /* Owner-directed/non-hostile retaliation targets are also respected. Blood
         * Hunter only autonomously retargets between natural hostile enemies. */
        if (current != null && current.isAlive() && !(current instanceof Enemy)) {
            return;
        }

        if (this.bloodHunterRetargetLockTicks > 0) {
            return;
        }

        if (this.bloodHunterScanTicks > 0) {
            --this.bloodHunterScanTicks;
            return;
        }

        int scanInterval = this.isBloodBerserkerActive()
                ? BLOOD_HUNTER_BERSERKER_SCAN_INTERVAL
                : BLOOD_HUNTER_NORMAL_SCAN_INTERVAL;
        this.bloodHunterScanTicks = scanInterval;

        double range = this.isBloodBerserkerActive()
                ? BLOOD_HUNTER_BERSERKER_RANGE
                : BLOOD_HUNTER_NORMAL_RANGE;

        LivingEntity best = this.findBestBloodHunterTarget(level, range);
        if (best == null || best == current) {
            return;
        }

        if (current == null || !current.isAlive() || !this.isValidBloodCombatTarget(current)) {
            this.setTarget(best);
            return;
        }

        double currentScore = this.getBloodHunterScore(current);
        double bestScore = this.getBloodHunterScore(best);

        double switchMargin;
        if (this.isBloodBerserkerActive()) {
            switchMargin = 1.0D;
        } else if (this.isBloodFrenzyEffective()) {
            switchMargin = 5.0D;
        } else {
            switchMargin = 10.0D;
        }

        if (bestScore + switchMargin < currentScore) {
            this.setTarget(best);
        }
    }

    private LivingEntity findBestBloodHunterTarget(ServerLevel level, double range) {
        AABB area = this.getBoundingBox().inflate(range);

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        candidate -> candidate instanceof Enemy
                                && !(candidate instanceof Creeper)
                                && this.isValidBloodCombatTarget(candidate))
                .stream()
                .min(Comparator
                        .comparingDouble(this::getBloodHunterScore)
                        .thenComparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /**
     * Remaining-health percentage is intentionally the dominant term.
     * Distance and target size are only tie-breakers.
     */
    private double getBloodHunterScore(LivingEntity target) {
        double healthRatio = this.healthRatio(target);
        double distance = Math.sqrt(this.distanceToSqr(target));
        double dangerTieBreak = Math.min(100.0D, target.getMaxHealth()) * 0.06D;

        double healthWeight = this.isBloodBerserkerActive() ? 135.0D : 100.0D;
        double score = healthRatio * healthWeight;
        score += distance * 0.12D;
        score -= dangerTieBreak;

        if (healthRatio <= BLOOD_HUNTER_EXECUTION_THRESHOLD) {
            score -= this.isBloodBerserkerActive() ? 30.0D : 18.0D;
        }

        return score;
    }

    // ---------------------------------------------------------------------
    // Blood Frenzy - the more hurt Blood gets, the worse the problem becomes
    // ---------------------------------------------------------------------

    private void tickBloodFrenzy(ServerLevel level) {
        if (this.isBloodBerserkerActive()) {
            this.frenzyCalmTicks = 0;
            return;
        }

        LivingEntity target = this.getTarget();
        boolean hasCombatTarget = target != null
                && target.isAlive()
                && this.isValidBloodCombatTarget(target);
        float healthRatio = this.getHealth() / this.getMaxHealth();

        if (!this.bloodFrenzyActive) {
            if (this.bloodFrenzyCooldownTicks <= 0
                    && hasCombatTarget
                    && healthRatio <= BLOOD_FRENZY_START_HEALTH) {
                this.bloodFrenzyActive = true;
                this.frenzyCalmTicks = 0;
                this.syncBloodCombatVisualState();
                this.playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 0.72F);
                level.sendParticles(
                        ParticleTypes.CRIT,
                        this.getX(), this.getY(0.45D), this.getZ(),
                        8, 0.30D, 0.22D, 0.30D, 0.10D);
            }
            return;
        }

        if (healthRatio >= BLOOD_FRENZY_END_HEALTH) {
            this.endBloodFrenzy();
            return;
        }

        if (hasCombatTarget) {
            this.frenzyCalmTicks = 0;
        } else if (++this.frenzyCalmTicks >= FRENZY_COMBAT_GRACE_TICKS) {
            this.endBloodFrenzy();
        }
    }

    private void endBloodFrenzy() {
        if (!this.bloodFrenzyActive) {
            return;
        }
        this.bloodFrenzyActive = false;
        this.frenzyCalmTicks = 0;
        this.bloodFrenzyCooldownTicks = BLOOD_FRENZY_COOLDOWN_TICKS;
        this.syncBloodCombatVisualState();
    }

    /** 0 -> normal, 1 -> maximum Frenzy. Berserker hard-forces 1. */
    public float getBloodFrenzyIntensity() {
        if (this.isBloodBerserkerActive()) {
            return 1.0F;
        }
        if (!this.bloodFrenzyActive) {
            return 0.0F;
        }

        float healthRatio = this.getHealth() / this.getMaxHealth();
        float span = BLOOD_FRENZY_START_HEALTH - BLOOD_FRENZY_MAX_HEALTH;
        return Mth.clamp((BLOOD_FRENZY_START_HEALTH - healthRatio) / span, 0.0F, 1.0F);
    }

    public boolean isBloodFrenzyEffective() {
        return this.bloodFrenzyActive || this.isBloodBerserkerActive();
    }

    // ---------------------------------------------------------------------
    // Blood Shield - physical pain tolerance, not a magic bubble
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isBaby()) {
            return super.hurtServer(level, source, damage);
        }

        boolean combatDamage = source.getEntity() instanceof LivingEntity;
        float projectedHealthRatio = (this.getHealth() - damage) / Math.max(1.0F, this.getMaxHealth());

        if (combatDamage
                && !this.isBloodBerserkerActive()
                && this.bloodShieldTicks <= 0
                && this.bloodShieldCooldownTicks <= 0
                && projectedHealthRatio <= BLOOD_SHIELD_TRIGGER_HEALTH) {
            this.startBloodShield(level);
        }

        float adjustedDamage = damage;
        if (combatDamage) {
            if (this.isBloodBerserkerActive()) {
                adjustedDamage *= BERSERKER_DAMAGE_MULTIPLIER;
            } else if (this.bloodShieldTicks > 0) {
                adjustedDamage *= BLOOD_SHIELD_DAMAGE_MULTIPLIER;
            }
        }

        boolean hurt = super.hurtServer(level, source, adjustedDamage);
        if (hurt) {
            if (source.getEntity() instanceof LivingEntity attacker
                    && attacker.isAlive()
                    && this.isValidBloodCombatTarget(attacker)) {
                // Do not steal target priority from an active family emergency.
                if (!this.hasFamilyDefenseEmergency() && !(this.getTarget() instanceof Creeper)) {
                    this.setTarget(attacker);
                }

                // Same-species Blood packs hear the fight too. Creepers are filtered
                // by alertPackToThreat so the shared Creeper coordinator keeps control.
                this.alertPackToThreat(attacker);
            }

            if (!this.bloodFrenzyActive
                    && !this.isBloodBerserkerActive()
                    && this.bloodFrenzyCooldownTicks <= 0
                    && this.getHealth() / this.getMaxHealth() <= BLOOD_FRENZY_START_HEALTH
                    && this.getTarget() != null) {
                this.bloodFrenzyActive = true;
                this.frenzyCalmTicks = 0;
                this.syncBloodCombatVisualState();
            }

            this.refreshBloodCombatAttributes();
        }

        return hurt;
    }

    private void startBloodShield(ServerLevel level) {
        this.bloodShieldTicks = BLOOD_SHIELD_DURATION_TICKS;
        this.playSound(SoundEvents.RAVAGER_ROAR, 0.75F, 0.62F);
        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(), this.getY(0.35D), this.getZ(),
                12, 0.38D, 0.20D, 0.38D, 0.08D);
        this.refreshBloodCombatAttributes();
    }

    public boolean isBloodShieldActive() {
        return this.bloodShieldTicks > 0 || this.isBloodBerserkerActive();
    }

    // ---------------------------------------------------------------------
    // Blood Mode - physical adaptive melee chaining
    // ---------------------------------------------------------------------

    private void tryStartBloodMode(ServerLevel level) {
        if (this.isBloodBerserkerActive()
                || this.bloodModeTicks > 0
                || this.bloodModeCooldownTicks > 0
                || !this.canUseBloodCombatSystems()
                || this.tickCount % 10 != 0) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || target instanceof Creeper || !this.isValidBloodCombatTarget(target)) {
            return;
        }

        int nearbyThreats = this.countNearbyBloodThreats(level, target.position(), BLOOD_MODE_CHAIN_RADIUS);
        if (nearbyThreats < 2) {
            return;
        }

        this.bloodModeTicks = BLOOD_MODE_DURATION_TICKS;
        this.resetBloodChain();
        this.playSound(SoundEvents.WARDEN_ROAR, 0.85F, 0.82F);
    }

    private void continueBloodModeChain(ServerLevel level, LivingEntity victim) {
        if (!this.isBloodModeActive()
                || victim instanceof Creeper
                || this.bloodChainResetTicks > 0) {
            return;
        }

        this.bloodChainVictims.add(victim.getId());

        int cap = this.isBloodBerserkerActive()
                ? BLOOD_MODE_BERSERKER_CHAIN_CAP
                : BLOOD_MODE_NORMAL_CHAIN_CAP;

        if (this.bloodChainVictims.size() >= cap) {
            this.bloodChainResetTicks = BLOOD_CHAIN_RESET_TICKS;
            return;
        }

        double radius = this.isBloodBerserkerActive()
                ? BLOOD_MODE_BERSERKER_CHAIN_RADIUS
                : BLOOD_MODE_CHAIN_RADIUS;

        LivingEntity next = this.findBloodChainTarget(level, victim.position(), radius);
        if (next == null) {
            // No forced target loss. Blood can keep mauling the current victim;
            // a short reset lets a later hit begin another adaptive sequence.
            this.bloodChainResetTicks = 6;
            return;
        }

        this.bloodHunterRetargetLockTicks = 10;
        this.setTarget(next);
        this.getNavigation().moveTo(next, this.getBloodPursuitSpeedModifier(next) + 0.12D);
        this.performPhysicalChainLunge(next);

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                victim.getX(), victim.getY(0.50D), victim.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private LivingEntity findBloodChainTarget(ServerLevel level, Vec3 center, double radius) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        candidate -> candidate instanceof Enemy
                                && !(candidate instanceof Creeper)
                                && !this.bloodChainVictims.contains(candidate.getId())
                                && this.isValidBloodCombatTarget(candidate))
                .stream()
                .min(Comparator
                        .comparingDouble(this::getBloodHunterScore)
                        .thenComparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void performPhysicalChainLunge(LivingEntity next) {
        Vec3 toward = next.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return;
        }

        double impulse = this.isBloodBerserkerActive() ? 0.52D : 0.38D;
        Vec3 dash = horizontal.normalize().scale(impulse);
        Vec3 current = this.getDeltaMovement();

        this.setDeltaMovement(
                current.x * 0.30D + dash.x,
                Math.max(current.y, 0.08D),
                current.z * 0.30D + dash.z);
        this.hurtMarked = true;
    }

    public boolean isBloodModeActive() {
        return this.bloodModeTicks > 0 || this.isBloodBerserkerActive();
    }

    private void resetBloodChain() {
        this.bloodChainVictims.clear();
        this.bloodChainResetTicks = 0;
    }

    // ---------------------------------------------------------------------
    // Bloodlust - kills keep the machine moving, but never forever
    // ---------------------------------------------------------------------

    private void registerBloodlustKill(ServerLevel level, LivingEntity killed) {
        this.bloodlustStacks = Math.min(BLOODLUST_MAX_STACKS, this.bloodlustStacks + 1);

        int base = this.isBloodBerserkerActive() ? 20 * 6 : 20 * 4;
        int extension = this.isBloodBerserkerActive() ? 40 : 30;
        int hardCap = this.isBloodBerserkerActive() ? 20 * 12 : 20 * 8;

        this.bloodlustTicks = Math.min(
                hardCap,
                Math.max(this.bloodlustTicks, base) + extension);

        if (this.isBloodBerserkerActive()) {
            int remainingHardBudget = Math.max(
                    0,
                    BLOOD_BERSERKER_HARD_MAX_TICKS - this.bloodBerserkerElapsedTicks);
            this.bloodBerserkerTicks = Math.min(
                    remainingHardBudget,
                    this.bloodBerserkerTicks + BLOOD_BERSERKER_KILL_EXTENSION_TICKS);
        }

        level.sendParticles(
                ParticleTypes.CRIT,
                killed.getX(), killed.getY(0.55D), killed.getZ(),
                10, 0.28D, 0.30D, 0.28D, 0.10D);

        this.refreshBloodCombatAttributes();
    }

    public int getBloodlustStacks() {
        return this.bloodlustStacks;
    }

    public boolean hasBloodlust() {
        return this.bloodlustTicks > 0 && this.bloodlustStacks > 0;
    }

    // ---------------------------------------------------------------------
    // Blood Berserker - maximum state
    // ---------------------------------------------------------------------

    private void tryStartBloodBerserker(ServerLevel level) {
        if (this.isBloodBerserkerActive()
                || this.bloodBerserkerCooldownTicks > 0
                || !this.canUseBloodCombatSystems()
                || this.tickCount % 10 != 0) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || target instanceof Creeper || !this.isValidBloodCombatTarget(target)) {
            return;
        }

        float healthRatio = this.getHealth() / this.getMaxHealth();
        int threats = this.countNearbyBloodThreats(level, this.position(), 10.0D);

        boolean badlyInjured = healthRatio <= BERSERKER_HEALTH_TRIGGER;
        boolean heavyweight = target.getMaxHealth() >= BERSERKER_HEAVY_TARGET_HEALTH;
        boolean outnumbered = threats >= 4;
        boolean executionMomentum = this.bloodlustStacks >= 3;

        if (!badlyInjured && !heavyweight && !outnumbered && !executionMomentum) {
            return;
        }

        this.startBloodBerserker(level);
    }

    private void startBloodBerserker(ServerLevel level) {
        this.bloodBerserkerTicks = BLOOD_BERSERKER_BASE_DURATION_TICKS;
        this.bloodBerserkerElapsedTicks = 0;
        this.bloodHunterRetargetLockTicks = 0;
        this.resetBloodChain();
        this.syncBloodCombatVisualState();

        this.playSound(SoundEvents.RAVAGER_ROAR, 1.35F, 0.52F);
        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(), this.getY(0.48D), this.getZ(),
                28, 0.55D, 0.38D, 0.55D, 0.16D);

        this.refreshBloodCombatAttributes();
    }

    private void finishBloodBerserker(ServerLevel level) {
        this.bloodBerserkerTicks = 0;
        this.bloodBerserkerElapsedTicks = 0;
        this.bloodBerserkerCooldownTicks = BLOOD_BERSERKER_COOLDOWN_TICKS;
        this.syncBloodCombatVisualState();

        if (this.bloodModeTicks <= 0) {
            this.resetBloodChain();
        }

        this.refreshBloodCombatAttributes();
    }

    public boolean isBloodBerserkerActive() {
        return this.bloodBerserkerTicks > 0;
    }

    public int getBloodBerserkerTicks() {
        return this.bloodBerserkerTicks;
    }

    // ---------------------------------------------------------------------
    // Melee impact hook
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(level, entity);

        if (!hurt || this.isBaby() || !(entity instanceof LivingEntity target)) {
            return hurt;
        }

        boolean killed = !target.isAlive() || target.getHealth() <= 0.0F;

        if (killed) {
            this.registerBloodlustKill(level, target);
        }

        if (this.isBloodModeActive()) {
            this.continueBloodModeChain(level, target);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Dynamic physical attributes
    // ---------------------------------------------------------------------

    private void refreshBloodCombatAttributes() {
        if (this.isBaby() || !this.isAlive()) {
            this.clearBloodCombatAttributes();
            return;
        }

        double frenzy = this.getBloodFrenzyIntensity();
        double bloodlust = this.hasBloodlust() ? this.bloodlustStacks : 0.0D;
        boolean berserker = this.isBloodBerserkerActive();
        boolean shield = this.isBloodShieldActive();

        double damageBonus = frenzy * FRENZY_MAX_DAMAGE_BONUS
                + bloodlust * BLOODLUST_DAMAGE_PER_STACK
                + (berserker ? BERSERKER_DAMAGE_BONUS : 0.0D);

        double speedBonus = frenzy * FRENZY_MAX_SPEED_BONUS
                + bloodlust * BLOODLUST_SPEED_PER_STACK
                + (berserker ? BERSERKER_SPEED_BONUS : 0.0D);

        double knockbackBonus = frenzy * FRENZY_MAX_KNOCKBACK_RESISTANCE_BONUS
                + (shield ? BLOOD_SHIELD_KNOCKBACK_RESISTANCE_BONUS : 0.0D)
                + (berserker ? BERSERKER_KNOCKBACK_RESISTANCE_BONUS : 0.0D);
        knockbackBonus = Math.min(MAX_TRANSIENT_KNOCKBACK_RESISTANCE_BONUS, knockbackBonus);

        this.applyOrRemoveModifier(
                Attributes.ATTACK_DAMAGE,
                BLOOD_DAMAGE_MODIFIER,
                damageBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.applyOrRemoveModifier(
                Attributes.MOVEMENT_SPEED,
                BLOOD_SPEED_MODIFIER,
                speedBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.applyOrRemoveModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                BLOOD_KNOCKBACK_MODIFIER,
                knockbackBonus,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private void applyOrRemoveModifier(
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        if (amount <= 1.0E-6D) {
            instance.removeModifier(id);
            return;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private void clearBloodCombatAttributes() {
        this.removeModifier(Attributes.ATTACK_DAMAGE, BLOOD_DAMAGE_MODIFIER);
        this.removeModifier(Attributes.MOVEMENT_SPEED, BLOOD_SPEED_MODIFIER);
        this.removeModifier(Attributes.KNOCKBACK_RESISTANCE, BLOOD_KNOCKBACK_MODIFIER);
    }

    private void removeModifier(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // ---------------------------------------------------------------------
    // Blood pack / Brain coordination
    // ---------------------------------------------------------------------

    public boolean isBloodPackmate(BloodWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isBloodFamilyMember(LivingEntity entity) {
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

            // Wolfism's universal family canon: every tamed wolf is family.
            return entity instanceof Wolf wolf && wolf.isTame();
        }

        return entity instanceof BloodWolf blood && this.isBloodPackmate(blood);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || threat instanceof Creeper
                || !this.isValidBloodCombatTarget(threat)) {
            return;
        }

        for (BloodWolf mate : level.getEntitiesOfClass(
                BloodWolf.class,
                this.getBoundingBox().inflate(BloodWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive()
                        && (wolf == this || this.isBloodPackmate(wolf)))) {
            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.BLOOD_PACK_THREAT.get(),
                    threat);

            if (mate.canParticipateInWolfismCombat()
                    && mate.isValidBloodCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {
        if (attacker != null
                && attacker.isAlive()
                && !(attacker instanceof Creeper)) {
            this.alertPackToThreat(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Goal-facing combat pressure helpers
    // ---------------------------------------------------------------------

    public boolean canUseBloodCombatSystems() {
        if (this.isBaby() || !this.isAlive() || !this.canParticipateInWolfismCombat()) {
            return false;
        }
        return !this.isOrderedToSit() || this.hasFamilyDefenseEmergency();
    }

    /**
     * Threat validity for pack perception. Unlike combat validity this deliberately
     * works for pups too, so a baby can remember danger and retreat without ever
     * being allowed to attack it.
     */
    public boolean isValidBloodPackThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || target instanceof Creeper
                || this.isAlliedTo(target)
                || this.isBloodFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public boolean isValidBloodCombatTarget(LivingEntity target) {
        return this.canUseBloodCombatSystems()
                && target != null
                && !(target instanceof Creeper)
                && this.canAttack(target)
                && this.isValidBloodPackThreat(target);
    }

    public double getBloodPursuitSpeedModifier(LivingEntity target) {
        double speed = 1.18D;
        speed += this.getBloodFrenzyIntensity() * 0.30D;

        if (this.hasBloodlust()) {
            speed += this.bloodlustStacks * 0.025D;
        }
        if (this.isBloodBerserkerActive()) {
            speed += 0.17D;
        }
        if (target != null && this.healthRatio(target) <= BLOOD_HUNTER_EXECUTION_THRESHOLD) {
            speed += this.isBloodBerserkerActive() ? 0.22D : 0.16D;
        }

        return Math.min(1.90D, speed);
    }

    public int getBloodAttackInterval(LivingEntity target) {
        int interval = 18;
        interval -= Math.round(this.getBloodFrenzyIntensity() * 6.0F);

        if (this.hasBloodlust()) {
            interval -= this.bloodlustStacks;
        }
        if (this.isBloodBerserkerActive()) {
            interval -= 3;
        }
        if (target != null && this.healthRatio(target) <= BLOOD_HUNTER_EXECUTION_THRESHOLD) {
            interval -= 2;
        }

        return Math.max(7, interval);
    }

    public int getBloodRepathInterval() {
        if (this.isBloodBerserkerActive()) {
            return 2;
        }
        if (this.getBloodFrenzyIntensity() >= 0.60F) {
            return 4;
        }
        return 6;
    }

    /**
     * Small non-teleporting gap closer used by the combat goal at high aggression.
     * It never owns Creeper movement.
     */
    public void tryBloodPressureLunge(LivingEntity target) {
        if (target == null
                || target instanceof Creeper
                || !this.isValidBloodCombatTarget(target)
                || !this.onGround()) {
            return;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < 9.0D || distanceSqr > 64.0D) {
            return;
        }

        float intensity = this.getBloodFrenzyIntensity();
        if (!this.isBloodBerserkerActive() && intensity < 0.55F) {
            return;
        }

        Vec3 toward = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return;
        }

        double impulse = this.isBloodBerserkerActive() ? 0.40D : 0.27D;
        Vec3 dash = horizontal.normalize().scale(impulse);
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                current.x + dash.x,
                Math.max(current.y, 0.14D),
                current.z + dash.z);
        this.hurtMarked = true;
    }

    // ---------------------------------------------------------------------
    // Persistence - unloading the chunk must not reset major combat cooldowns
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putBoolean("BloodFrenzyActive", this.bloodFrenzyActive);
        output.putInt("BloodFrenzyCooldown", this.bloodFrenzyCooldownTicks);
        output.putInt("BloodFrenzyCalm", this.frenzyCalmTicks);

        output.putInt("BloodShieldTicks", this.bloodShieldTicks);
        output.putInt("BloodShieldCooldown", this.bloodShieldCooldownTicks);

        output.putInt("BloodModeTicks", this.bloodModeTicks);
        output.putInt("BloodModeCooldown", this.bloodModeCooldownTicks);

        output.putInt("BloodlustStacks", this.bloodlustStacks);
        output.putInt("BloodlustTicks", this.bloodlustTicks);

        output.putInt("BloodBerserkerTicks", this.bloodBerserkerTicks);
        output.putInt("BloodBerserkerElapsed", this.bloodBerserkerElapsedTicks);
        output.putInt("BloodBerserkerCooldown", this.bloodBerserkerCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        this.bloodFrenzyActive = input.getBooleanOr("BloodFrenzyActive", false);
        this.bloodFrenzyCooldownTicks = Math.max(0, input.getIntOr("BloodFrenzyCooldown", 0));
        this.frenzyCalmTicks = Mth.clamp(input.getIntOr("BloodFrenzyCalm", 0), 0, FRENZY_COMBAT_GRACE_TICKS);

        this.bloodShieldTicks = Mth.clamp(
                input.getIntOr("BloodShieldTicks", 0),
                0,
                BLOOD_SHIELD_DURATION_TICKS);
        this.bloodShieldCooldownTicks = Math.max(0, input.getIntOr("BloodShieldCooldown", 0));

        this.bloodModeTicks = Mth.clamp(
                input.getIntOr("BloodModeTicks", 0),
                0,
                BLOOD_MODE_DURATION_TICKS);
        this.bloodModeCooldownTicks = Math.max(0, input.getIntOr("BloodModeCooldown", 0));

        this.bloodlustStacks = Mth.clamp(
                input.getIntOr("BloodlustStacks", 0),
                0,
                BLOODLUST_MAX_STACKS);
        this.bloodlustTicks = Mth.clamp(
                input.getIntOr("BloodlustTicks", 0),
                0,
                20 * 12);
        if (this.bloodlustTicks <= 0) {
            this.bloodlustStacks = 0;
        }

        this.bloodBerserkerElapsedTicks = Mth.clamp(
                input.getIntOr("BloodBerserkerElapsed", 0),
                0,
                BLOOD_BERSERKER_HARD_MAX_TICKS);
        int remainingBudget = Math.max(
                0,
                BLOOD_BERSERKER_HARD_MAX_TICKS - this.bloodBerserkerElapsedTicks);
        this.bloodBerserkerTicks = Mth.clamp(
                input.getIntOr("BloodBerserkerTicks", 0),
                0,
                remainingBudget);
        this.bloodBerserkerCooldownTicks = Math.max(0, input.getIntOr("BloodBerserkerCooldown", 0));

        // Runtime-only chain/retarget bookkeeping should start clean after a load.
        this.bloodHunterScanTicks = 0;
        this.bloodHunterRetargetLockTicks = 0;
        this.resetBloodChain();

        // Pups never retain adult combat states even if old data is malformed.
        if (this.isBaby()) {
            this.cancelAdultBloodStates();
        } else {
            this.syncBloodCombatVisualState();
            this.refreshBloodCombatAttributes();
        }
    }

    // ---------------------------------------------------------------------
    // Misc helpers / safety
    // ---------------------------------------------------------------------

    private int countNearbyBloodThreats(ServerLevel level, Vec3 center, double radius) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && this.isValidBloodCombatTarget(candidate)).size();
    }

    private double healthRatio(LivingEntity entity) {
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
    }

    private void cancelAdultBloodStates() {
        this.bloodFrenzyActive = false;
        this.frenzyCalmTicks = 0;
        this.bloodShieldTicks = 0;
        this.bloodModeTicks = 0;
        this.bloodlustTicks = 0;
        this.bloodlustStacks = 0;
        this.bloodBerserkerTicks = 0;
        this.bloodBerserkerElapsedTicks = 0;
        this.resetBloodChain();
        this.syncBloodCombatVisualState();
        this.clearBloodCombatAttributes();
    }

    public static boolean checkBloodWolfSpawnRules(
            EntityType<BloodWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        boolean night = level instanceof Level actual && actual.isDarkOutside();
        if (!night) {
            return false;
        }

        var ground = level.getBlockState(pos.below());
        return ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                || ground.is(BlockTags.BADLANDS_TERRACOTTA)
                || ground.is(BlockTags.SAND)
                || ground.is(BlockTags.MUD);
    }
}