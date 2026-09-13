package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.InfernalLavaRescueGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.InfernalStrikeGoal;
import net.ronm19.wolfism.entity.ai.sensor.InfernalWolfAwarenessSensor;
import net.ronm19.wolfism.entity.ai.sensor.InfernalWolfPackSensor;
import net.ronm19.wolfism.entity.ai.util.InfernalObsidianPathManager;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #29 - Infernal Wolf.
 *
 * <p>Current canon: Wolfism's high-tier Nether war machine. Infernal combines
 * traversal, Nether awareness, family support, a rage state, a heavy
 * armor-piercing breakthrough strike, soul-fed escalation and a non-griefing
 * meteor-shower ultimate.</p>
 *
 * <p>This class intentionally replaces the older four-ability starter concept.
 * The authoritative kit implemented here is:</p>
 * <ol>
 *     <li>Complete Fire & Lava Immunity</li>
 *     <li>Lava Walking / Obsidian Path</li>
 *     <li>Infernal Awareness + Infernal Command/Presence</li>
 *     <li>Infernal's Arena</li>
 *     <li>Infernal's Rage</li>
 *     <li>Infernal Strike</li>
 *     <li>Infernal Souls</li>
 *     <li>Meteor Shower</li>
 * </ol>
 */
public final class InfernalWolf extends AbstractWolfismWolf {
    private static final double INFERNAL_BASE_MAX_HEALTH = 46.0D;

    public static final double PACK_SCAN_RADIUS = 34.0D;
    public static final double AWARENESS_RANGE = 30.0D;

    // ---------------------------------------------------------------------
    // #2 Lava Walking / Obsidian Path
    // ---------------------------------------------------------------------

    public static final int OBSIDIAN_PATH_LIFETIME_TICKS = 20 * 8;

    // ---------------------------------------------------------------------
    // #3 Infernal Awareness / Presence
    // ---------------------------------------------------------------------

    private static final double PRESENCE_RADIUS = 8.0D;
    private static final double PRESENCE_MAX_WEAK_HEALTH = 30.0D;
    private static final int PRESENCE_INTERVAL = 10;

    // ---------------------------------------------------------------------
    // #4 Infernal's Arena
    // Exact duration/radius/cooldown were not locked in the supplied canon,
    // so these are first-pass tuning values.
    // ---------------------------------------------------------------------

    public static final int ARENA_DURATION_TICKS = 20 * 12;
    public static final int ARENA_COOLDOWN_TICKS = 20 * 30;
    public static final double ARENA_RADIUS = 11.0D;
    private static final int ARENA_REFRESH_INTERVAL = 10;
    private static final int ARENA_EFFECT_REFRESH_TICKS = 30;

    // ---------------------------------------------------------------------
    // #5 Infernal's Rage
    // ---------------------------------------------------------------------

    public static final int RAGE_DURATION_TICKS = 20 * 11;
    public static final int RAGE_COOLDOWN_TICKS = 20 * 22;

    private static final double RAGE_DAMAGE_BONUS = 0.60D;
    private static final double RAGE_SPEED_BONUS = 0.25D;
    private static final double RAGE_ARMOR_BONUS = 8.0D;
    private static final double RAGE_KNOCKBACK_BONUS = 0.40D;
    private static final float RAGE_DAMAGE_TAKEN_MULTIPLIER = 0.85F;

    // ---------------------------------------------------------------------
    // #6 Infernal Strike
    // Cooldown/damage tuning was not fixed in the supplied canon.
    // ---------------------------------------------------------------------

    public static final int STRIKE_COOLDOWN_TICKS = 20 * 18;
    private static final int STRIKE_DURATION_TICKS = 14;
    private static final double STRIKE_SPEED = 1.12D;
    private static final double STRIKE_IMPACT_DISTANCE_SQR = 2.35D * 2.35D;
    private static final double STRIKE_STEER_BLEND = 0.20D;
    private static final float STRIKE_DAMAGE_MULTIPLIER = 1.80F;
    private static final double STRIKE_KNOCKBACK = 2.75D;

    // ---------------------------------------------------------------------
    // #7 Infernal Souls
    // ---------------------------------------------------------------------

    public static final int SOULS_BASE_DURATION_TICKS = 20 * 12;
    public static final int SOULS_MAX_DURATION_TICKS = 20 * 20;
    public static final int SOULS_COOLDOWN_TICKS = 20 * 45;
    private static final int SOULS_EXTENSION_PER_KILL_TICKS = 20 * 2;
    private static final int SOULS_MAX_STACKS = 5;

    private static final double SOULS_DAMAGE_PER_STACK = 0.10D;
    private static final double SOULS_SPEED_PER_STACK = 0.03D;
    private static final double SOULS_ARMOR_PER_STACK = 1.5D;
    private static final double SOULS_KNOCKBACK_PER_STACK = 0.05D;

    // ---------------------------------------------------------------------
    // #8 Meteor Shower
    // ---------------------------------------------------------------------

    public static final int METEOR_SHOWER_DURATION_TICKS = 20 * 15;
    public static final int METEOR_SHOWER_COOLDOWN_TICKS = 20 * 75;
    private static final int METEOR_SCHEDULE_INTERVAL = 15;
    private static final int METEOR_FALL_TICKS = 8;
    private static final double METEOR_TARGET_RANGE = 18.0D;

    // ---------------------------------------------------------------------
    // Dynamic modifier IDs
    // ---------------------------------------------------------------------

    private static final Identifier INFERNAL_DAMAGE_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "infernal_combat_damage");
    private static final Identifier INFERNAL_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "infernal_combat_speed");
    private static final Identifier INFERNAL_ARMOR_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "infernal_combat_armor");
    private static final Identifier INFERNAL_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "infernal_combat_knockback");

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    private int arenaTicks;
    private int arenaCooldownTicks;

    private int rageTicks;
    private int rageCooldownTicks;

    private int strikeTicks;
    private int strikeCooldownTicks;
    private int strikeTargetId = -1;
    private Vec3 strikeDirection = Vec3.ZERO;

    private int soulsTicks;
    private int soulsCooldownTicks;
    private int soulStacks;

    private int meteorShowerTicks;
    private int meteorShowerCooldownTicks;
    private final List<MeteorMarker> pendingMeteors = new ArrayList<>();

    private int combatTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<InfernalWolf> PROVIDER = Brain.<InfernalWolf>provider(
                ImmutableList.of(
                        ModSensorTypes.INFERNAL_PACK.get(),
                        ModSensorTypes.INFERNAL_AWARENESS.get()),
                wolf -> List.of());
    }

    public InfernalWolf(EntityType<? extends InfernalWolf> type, Level level) {
        super(type, level);

        // Borrow the important part of vanilla Strider pathing: lava/fire are
        // valid navigation terrain instead of hard-avoid hazards.
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.INFERNAL_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, INFERNAL_BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.FOLLOW_RANGE, 44.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.20D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(INFERNAL_BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    // ---------------------------------------------------------------------
    // Goals / Brain
    // ---------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new InfernalPupRetreatGoal(this));

        // Infernal Strike owns movement while preparing/rushing, but remains
        // below Wolfism's priority-0 Creeper retreat.
        this.goalSelector.addGoal(2, new InfernalStrikeGoal(this));

        /*
         * Real lava emergency only. General Infernal Awareness remains visual
         * (safe-position particles) so the wolf does not constantly stroll
         * around whenever it merely notices lava nearby.
         */
        this.goalSelector.addGoal(1, new InfernalLavaRescueGoal(this));

        this.goalSelector.addGoal(5, new InfernalPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new InfernalPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new InfernalPupPlayGoal(this));

        this.targetSelector.addGoal(3, new InfernalPackAssistGoal(this));
    }

    @Override
    protected Brain<InfernalWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<InfernalWolf> getBrain() {
        return (Brain<InfernalWolf>) super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // #1 Complete Fire & Lava Immunity
    // ---------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    // ---------------------------------------------------------------------
    // #2 Lava Walking / Obsidian Path
    // ---------------------------------------------------------------------

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA) || super.canStandOnFluid(fluid);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new InfernalPathNavigation(this, level);
    }

    private static final class InfernalPathNavigation extends GroundPathNavigation {
        private InfernalPathNavigation(InfernalWolf wolf, Level level) {
            super(wolf, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new WalkNodeEvaluator();
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }

        @Override
        protected boolean hasValidPathType(PathType pathType) {
            return pathType == PathType.LAVA
                    || pathType == PathType.FIRE
                    || pathType == PathType.FIRE_IN_NEIGHBOR
                    || super.hasValidPathType(pathType);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            return this.level.getFluidState(pos).is(FluidTags.LAVA)
                    || super.isStableDestination(pos);
        }
    }

    // ---------------------------------------------------------------------
    // Main server loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof InfernalWolf infernal
                && this.isInfernalWolfPackmate(infernal)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        InfernalObsidianPathManager.tick(level);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelAdultInfernalStates();
            this.clearInfernalCombatModifiers();
            return;
        }

        // The path is physical obsidian, so owner + every family wolf can use it.
        InfernalObsidianPathManager.createPath(level, this);

        this.tickCooldowns();

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            ++this.combatTicks;
        } else {
            this.combatTicks = 0;
        }

        this.tickInfernalAwareness(level);
        this.tickInfernalPresence(level);

        if (this.strikeTicks > 0) {
            this.tickInfernalStrike(level);
        }

        this.tickArena(level);
        this.tickRage(level);
        this.tickInfernalSouls(level);
        this.tickMeteorShower(level);
        this.tickPendingMeteors(level);

        if (this.tickCount % 4 == 0) {
            this.refreshInfernalCombatModifiers();
        }

        this.tryStartArena(level);
        this.tryStartRage(level);
        this.tryStartInfernalSouls(level);
        this.tryStartMeteorShower(level);
    }

    private void tickCooldowns() {
        if (this.arenaCooldownTicks > 0) {
            --this.arenaCooldownTicks;
        }
        if (this.rageCooldownTicks > 0) {
            --this.rageCooldownTicks;
        }
        if (this.strikeCooldownTicks > 0) {
            --this.strikeCooldownTicks;
        }
        if (this.soulsCooldownTicks > 0) {
            --this.soulsCooldownTicks;
        }
        if (this.meteorShowerCooldownTicks > 0) {
            --this.meteorShowerCooldownTicks;
        }
    }

    private void cancelAdultInfernalStates() {
        this.arenaTicks = 0;
        this.rageTicks = 0;
        this.strikeTicks = 0;
        this.strikeTargetId = -1;
        this.strikeDirection = Vec3.ZERO;
        this.soulsTicks = 0;
        this.soulStacks = 0;
        this.meteorShowerTicks = 0;
        this.pendingMeteors.clear();
        this.setSprinting(false);
    }

    // ---------------------------------------------------------------------
    // #3 Infernal Awareness
    // ---------------------------------------------------------------------

    private void tickInfernalAwareness(ServerLevel level) {
        if (!this.isWolfismWorkTick(10)
                || level.dimension() != Level.NETHER) {
            return;
        }

        LivingEntity awarenessThreat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.INFERNAL_AWARENESS_THREAT.get())
                .orElse(null);

        if (this.isTame()
                && this.getTarget() == null
                && awarenessThreat != null
                && awarenessThreat.isAlive()
                && !(awarenessThreat instanceof Creeper)
                && this.isValidInfernalCombatTarget(awarenessThreat)) {

            boolean threateningFamily = false;
            if (awarenessThreat instanceof Mob mob) {
                LivingEntity mobTarget = mob.getTarget();
                threateningFamily = mobTarget != null
                        && this.isInfernalFamilyMember(mobTarget);
            }

            if (threateningFamily
                    || this.distanceToSqr(awarenessThreat) <= 10.0D * 10.0D) {
                this.setTarget(awarenessThreat);
                this.alertPackToThreat(awarenessThreat);
            }
        }

        // A subtle marker at the sensor-selected safe ground gives the owner a
        // visible "this route is safer" cue without UI spam.
        if (this.isTame()
                && this.getTarget() == null
                && this.isWolfismWorkTick(20)) {

            BlockPos safePos = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.INFERNAL_SAFE_POS.get())
                    .orElse(null);

            BlockPos hazard = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.INFERNAL_LAVA_HAZARD.get())
                    .orElse(null);

            LivingEntity owner = this.getOwner();

            if (owner != null
                    && safePos != null
                    && hazard != null
                    && hazard.distToCenterSqr(
                            owner.getX(),
                            owner.getY(),
                            owner.getZ()) <= 7.0D * 7.0D) {

                WolfVfx.sendParticles("infernal_wolf", level,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        safePos.getX() + 0.5D,
                        safePos.getY() + 0.15D,
                        safePos.getZ() + 0.5D,
                        3,
                        0.22D, 0.05D, 0.22D,
                        0.01D);
            }
        }
    }

    // ---------------------------------------------------------------------
    // #3b Infernal Command / Presence
    // ---------------------------------------------------------------------

    private void tickInfernalPresence(ServerLevel level) {
        if (this.tickCount % PRESENCE_INTERVAL != 0
                || level.dimension() != Level.NETHER) {
            return;
        }

        for (Mob mob : level.getEntitiesOfClass(
                Mob.class,
                this.getBoundingBox().inflate(PRESENCE_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && candidate instanceof Enemy
                        && candidate.getMaxHealth() <= PRESENCE_MAX_WEAK_HEALTH
                        && !(candidate instanceof WitherBoss)
                        && !(candidate instanceof PiglinBrute)
                        && !this.isInfernalFamilyMember(candidate))) {

            LivingEntity mobTarget = mob.getTarget();
            if (mobTarget != null && this.isInfernalFamilyMember(mobTarget)) {
                mob.setTarget(null);
            }

            Vec3 away = mob.position().subtract(this.position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);

            if (horizontal.lengthSqr() > 1.0E-5D) {
                horizontal = horizontal.normalize().scale(0.28D);
                mob.push(horizontal.x, 0.04D, horizontal.z);
                mob.hurtMarked = true;
            }

            if (this.distanceToSqr(mob) <= 4.0D * 4.0D) {
                mob.getNavigation().stop();
            }
        }
    }

    // ---------------------------------------------------------------------
    // #4 Infernal's Arena
    // ---------------------------------------------------------------------

    public boolean isInfernalArenaActive() {
        return this.arenaTicks > 0;
    }

    private void tryStartArena(ServerLevel level) {
        if (!this.isTame()
                || !this.canUseActiveWolfismAbility()
                || this.arenaTicks > 0
                || this.arenaCooldownTicks > 0
                || (this.getTarget() == null && !this.hasFamilyDefenseEmergency())) {
            return;
        }

        boolean familyNearby = !level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(ARENA_RADIUS),
                this::isInfernalArenaFamilyTarget)
                .isEmpty();

        if (familyNearby) {
            this.arenaTicks = ARENA_DURATION_TICKS;

            WolfVfx.sendParticles("infernal_wolf", level,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(),
                    this.getY() + 0.55D,
                    this.getZ(),
                    28,
                    1.2D, 0.35D, 1.2D,
                    0.04D);
        }
    }

    private void tickArena(ServerLevel level) {
        if (this.arenaTicks <= 0) {
            return;
        }

        --this.arenaTicks;

        if (this.tickCount % ARENA_REFRESH_INTERVAL == 0) {
            for (LivingEntity family : level.getEntitiesOfClass(
                    LivingEntity.class,
                    this.getBoundingBox().inflate(ARENA_RADIUS),
                    this::isInfernalArenaFamilyTarget)) {

                family.addEffect(new MobEffectInstance(
                        MobEffects.FIRE_RESISTANCE,
                        ARENA_EFFECT_REFRESH_TICKS,
                        0));

                family.addEffect(new MobEffectInstance(
                        MobEffects.STRENGTH,
                        ARENA_EFFECT_REFRESH_TICKS,
                        0));

                family.addEffect(new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        ARENA_EFFECT_REFRESH_TICKS,
                        0));
            }

            WolfVfx.sendParticles("infernal_wolf", level,
                    ParticleTypes.FLAME,
                    this.getX(),
                    this.getY() + 0.35D,
                    this.getZ(),
                    6,
                    ARENA_RADIUS * 0.22D,
                    0.15D,
                    ARENA_RADIUS * 0.22D,
                    0.01D);
        }

        if (this.arenaTicks == 0) {
            this.arenaCooldownTicks = ARENA_COOLDOWN_TICKS;
        }
    }

    private boolean isInfernalArenaFamilyTarget(LivingEntity entity) {
        return entity == this
                || (this.isTame()
                && (entity == this.getOwner()
                || entity instanceof Wolf wolf && wolf.isTame()));
    }

    // ---------------------------------------------------------------------
    // #5 Infernal's Rage
    // ---------------------------------------------------------------------

    public boolean isInfernalRageActive() {
        return this.rageTicks > 0;
    }

    private void tryStartRage(ServerLevel level) {
        LivingEntity target = this.getTarget();

        if (!this.canUseActiveWolfismAbility()
                || this.rageTicks > 0
                || this.rageCooldownTicks > 0
                || target == null
                || !target.isAlive()
                || !this.isValidInfernalCombatTarget(target)) {
            return;
        }

        this.rageTicks = RAGE_DURATION_TICKS;
        this.refreshInfernalCombatModifiers();

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.LAVA,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                12,
                0.55D, 0.35D, 0.55D,
                0.02D);
    }

    private void tickRage(ServerLevel level) {
        if (this.rageTicks <= 0) {
            return;
        }

        --this.rageTicks;

        if (this.tickCount % 5 == 0) {
            WolfVfx.sendParticles("infernal_wolf", level,
                    ParticleTypes.FLAME,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    4,
                    0.45D, 0.25D, 0.45D,
                    0.015D);
        }

        if (this.rageTicks == 0) {
            this.rageCooldownTicks = RAGE_COOLDOWN_TICKS;
            this.refreshInfernalCombatModifiers();
        }
    }

    // ---------------------------------------------------------------------
    // #6 Infernal Strike
    // ---------------------------------------------------------------------

    public boolean isInfernalStriking() {
        return this.strikeTicks > 0;
    }

    public boolean canStartInfernalStrikeAgainst(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.canUseActiveWolfismAbility()
                || this.strikeTicks > 0
                || this.strikeCooldownTicks > 0
                || !this.onGround()
                || !this.isValidInfernalCombatTarget(target)
                || !this.getSensing().hasLineOfSight(target)
                || this.isStrikeReservedByPack(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        return distanceSqr >= InfernalStrikeGoal.MIN_START_DISTANCE_SQR
                && distanceSqr <= InfernalStrikeGoal.MAX_START_DISTANCE_SQR;
    }

    public boolean beginInfernalStrike(LivingEntity target) {
        if (!this.canStartInfernalStrikeAgainst(target)) {
            return false;
        }

        Vec3 direction = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (direction.lengthSqr() <= 1.0E-5D) {
            return false;
        }

        this.strikeDirection = direction.normalize();
        this.strikeTicks = STRIKE_DURATION_TICKS;
        this.strikeTargetId = target.getId();
        this.strikeCooldownTicks = STRIKE_COOLDOWN_TICKS;

        this.getNavigation().stop();
        this.setSprinting(true);
        this.faceStrikeDirection();

        return true;
    }

    private void tickInfernalStrike(ServerLevel level) {
        LivingEntity target = this.getStrikeTarget(level);

        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.isValidInfernalCombatTarget(target)) {
            this.endInfernalStrike();
            return;
        }

        Vec3 desired = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (desired.lengthSqr() > 1.0E-5D) {
            desired = desired.normalize();
            Vec3 steered = this.strikeDirection
                    .scale(1.0D - STRIKE_STEER_BLEND)
                    .add(desired.scale(STRIKE_STEER_BLEND));

            if (steered.lengthSqr() > 1.0E-5D) {
                this.strikeDirection = steered.normalize();
            }
        }

        this.faceStrikeDirection();

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.FLAME,
                this.getX() - this.strikeDirection.x * 0.45D,
                this.getY() + 0.30D,
                this.getZ() - this.strikeDirection.z * 0.45D,
                3,
                0.18D, 0.12D, 0.18D,
                0.01D);

        if (this.distanceToSqr(target) <= STRIKE_IMPACT_DISTANCE_SQR) {
            float strikeDamage =
                    (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            * STRIKE_DAMAGE_MULTIPLIER;

            // MAGIC is intentionally used for the breakthrough component:
            // vanilla tags it as BYPASSES_ARMOR, so the strike remains relevant
            // against Netherite-equivalent and high-tier modded armor.
            DamageSource source =
                    this.damageSources().source(DamageTypes.MAGIC, this);

            boolean hit = target.hurtServer(level, source, strikeDamage);

            if (hit) {
                double dx = this.getX() - target.getX();
                double dz = this.getZ() - target.getZ();
                target.knockback(STRIKE_KNOCKBACK, dx, dz);
                target.push(0.0D, 0.30D, 0.0D);

                WolfVfx.sendParticles("infernal_wolf", level,
                        ParticleTypes.EXPLOSION,
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.5D,
                        target.getZ(),
                        2,
                        0.18D, 0.18D, 0.18D,
                        0.0D);

                WolfVfx.sendParticles("infernal_wolf", level,
                        ParticleTypes.LAVA,
                        target.getX(),
                        target.getY() + 0.35D,
                        target.getZ(),
                        10,
                        0.45D, 0.30D, 0.45D,
                        0.03D);

                this.onInfernalKill(level, target);
            }

            this.endInfernalStrike();
            return;
        }

        if (this.horizontalCollision) {
            this.endInfernalStrike();
            return;
        }

        Vec3 currentMovement = this.getDeltaMovement();
        this.setDeltaMovement(
                this.strikeDirection.x * STRIKE_SPEED,
                currentMovement.y,
                this.strikeDirection.z * STRIKE_SPEED);

        this.hurtMarked = true;

        --this.strikeTicks;
        if (this.strikeTicks <= 0) {
            this.endInfernalStrike();
        }
    }

    public void endInfernalStrike() {
        this.strikeTicks = 0;
        this.strikeTargetId = -1;
        this.strikeDirection = Vec3.ZERO;
        this.setSprinting(false);
    }

    private LivingEntity getStrikeTarget(ServerLevel level) {
        if (this.strikeTargetId < 0) {
            return null;
        }

        Entity entity = level.getEntity(this.strikeTargetId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private boolean isStrikeReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        for (InfernalWolf packmate : level.getEntitiesOfClass(
                InfernalWolf.class,
                this.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isInfernalWolfPackmate(candidate))) {

            if (packmate.isInfernalStriking()
                    && packmate.strikeTargetId == target.getId()) {
                return true;
            }
        }

        return false;
    }

    private void faceStrikeDirection() {
        if (this.strikeDirection.lengthSqr() <= 1.0E-5D) {
            return;
        }

        float yaw = (float)Math.toDegrees(
                Math.atan2(-this.strikeDirection.x, this.strikeDirection.z));

        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    // ---------------------------------------------------------------------
    // #7 Infernal Souls
    // ---------------------------------------------------------------------

    public boolean isInfernalSoulsActive() {
        return this.soulsTicks > 0;
    }

    public int getInfernalSoulStacks() {
        return this.soulStacks;
    }

    private void tryStartInfernalSouls(ServerLevel level) {
        LivingEntity target = this.getTarget();

        if (!this.canUseActiveWolfismAbility()
                || this.soulsTicks > 0
                || this.soulsCooldownTicks > 0
                || target == null
                || !target.isAlive()
                || !this.isValidInfernalCombatTarget(target)) {
            return;
        }

        boolean crowdFight = this.countNearbyHostileThreats(10.0D) >= 3;
        boolean majorTarget = target.getMaxHealth() >= 35.0F;

        if (!crowdFight && !majorTarget) {
            return;
        }

        this.soulsTicks = SOULS_BASE_DURATION_TICKS;
        this.soulStacks = 0;

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.SOUL,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                12,
                0.65D, 0.45D, 0.65D,
                0.03D);
    }

    private void tickInfernalSouls(ServerLevel level) {
        if (this.soulsTicks <= 0) {
            return;
        }

        --this.soulsTicks;

        if (this.tickCount % 8 == 0) {
            int count = Math.max(2, 2 + this.soulStacks);

            WolfVfx.sendParticles("infernal_wolf", level,
                    ParticleTypes.SOUL,
                    this.getX(),
                    this.getY() + 0.65D,
                    this.getZ(),
                    count,
                    0.55D, 0.40D, 0.55D,
                    0.02D);
        }

        if (this.soulsTicks == 0) {
            this.soulsCooldownTicks = SOULS_COOLDOWN_TICKS;
            this.soulStacks = 0;
            this.refreshInfernalCombatModifiers();
        }
    }

    private void onInfernalKill(ServerLevel level, LivingEntity victim) {
        if (victim == null || victim.isAlive() || this.isInfernalFamilyMember(victim)) {
            return;
        }

        if (this.soulsTicks <= 0 && this.soulsCooldownTicks <= 0) {
            this.soulsTicks = SOULS_BASE_DURATION_TICKS;
            this.soulStacks = 0;
        }

        if (this.soulsTicks <= 0) {
            return;
        }

        this.soulStacks = Math.min(
                SOULS_MAX_STACKS,
                this.soulStacks + 1);

        this.soulsTicks = Math.min(
                SOULS_MAX_DURATION_TICKS,
                this.soulsTicks + SOULS_EXTENSION_PER_KILL_TICKS);

        this.refreshInfernalCombatModifiers();

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.55D,
                victim.getZ(),
                12,
                0.35D, 0.35D, 0.35D,
                0.03D);

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.SOUL,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                8,
                0.45D, 0.35D, 0.45D,
                0.03D);
    }

    // Ordinary melee kills also feed Infernal Souls.
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);

        if (hit && target instanceof LivingEntity living) {
            this.onInfernalKill(level, living);
        }

        return hit;
    }

    // ---------------------------------------------------------------------
    // #8 Meteor Shower
    // ---------------------------------------------------------------------

    public boolean isMeteorShowerActive() {
        return this.meteorShowerTicks > 0;
    }

    private void tryStartMeteorShower(ServerLevel level) {
        LivingEntity target = this.getTarget();

        if (!this.canUseActiveWolfismAbility()
                || this.meteorShowerTicks > 0
                || this.meteorShowerCooldownTicks > 0
                || this.combatTicks < 60
                || target == null
                || !target.isAlive()
                || !this.isValidInfernalCombatTarget(target)) {
            return;
        }

        boolean battlefield = this.countNearbyHostileThreats(16.0D) >= 4;
        boolean bossLikeTarget = target.getMaxHealth() >= 60.0F;

        if (!battlefield && !bossLikeTarget) {
            return;
        }

        this.meteorShowerTicks = METEOR_SHOWER_DURATION_TICKS;

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(),
                this.getY() + 1.0D,
                this.getZ(),
                32,
                1.4D, 0.8D, 1.4D,
                0.06D);
    }

    private void tickMeteorShower(ServerLevel level) {
        if (this.meteorShowerTicks <= 0) {
            return;
        }

        --this.meteorShowerTicks;

        if (this.meteorShowerTicks % METEOR_SCHEDULE_INTERVAL == 0) {
            this.scheduleInfernalProjectile(level);

            if (this.soulStacks >= 4
                    && this.random.nextInt(3) == 0) {
                this.scheduleInfernalProjectile(level);
            }
        }

        if (this.meteorShowerTicks == 0) {
            this.meteorShowerCooldownTicks = METEOR_SHOWER_COOLDOWN_TICKS;
        }
    }

    private void scheduleInfernalProjectile(ServerLevel level) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(METEOR_TARGET_RANGE),
                this::isValidInfernalHazardVictim);

        LivingEntity anchor = candidates.isEmpty()
                ? this.getTarget()
                : candidates.get(this.random.nextInt(candidates.size()));

        if (anchor == null || !anchor.isAlive()) {
            return;
        }

        double spread = 2.2D;
        double x = anchor.getX() + this.random.nextGaussian() * spread;
        double z = anchor.getZ() + this.random.nextGaussian() * spread;
        double y = anchor.getY();

        MeteorKind kind;
        int roll = this.random.nextInt(10);

        if (roll == 0) {
            kind = MeteorKind.HEAVY_METEOR;
        } else if (roll <= 2) {
            kind = MeteorKind.MAGMA_BOMB;
        } else if (roll <= 4) {
            kind = MeteorKind.FIREBALL;
        } else {
            kind = MeteorKind.METEOR;
        }

        this.pendingMeteors.add(
                new MeteorMarker(
                        new Vec3(x, y, z),
                        METEOR_FALL_TICKS,
                        kind));
    }

    private void tickPendingMeteors(ServerLevel level) {
        for (int i = this.pendingMeteors.size() - 1; i >= 0; --i) {
            MeteorMarker marker = this.pendingMeteors.get(i);
            --marker.ticks;

            double fallingY =
                    marker.impact.y + Math.max(1, marker.ticks) * 1.65D;

            WolfVfx.sendParticles("infernal_wolf", level,
                    marker.kind == MeteorKind.MAGMA_BOMB
                            ? ParticleTypes.LAVA
                            : ParticleTypes.FLAME,
                    marker.impact.x,
                    fallingY,
                    marker.impact.z,
                    marker.kind == MeteorKind.HEAVY_METEOR ? 5 : 3,
                    0.20D, 0.18D, 0.20D,
                    0.02D);

            if (marker.ticks <= 0) {
                this.impactInfernalProjectile(level, marker);
                this.pendingMeteors.remove(i);
            }
        }
    }

    private void impactInfernalProjectile(
            ServerLevel level,
            MeteorMarker marker) {

        double radius;
        float damage;
        int burnTicks;
        double knockback;

        switch (marker.kind) {
            case HEAVY_METEOR -> {
                radius = 3.8D;
                damage = 14.0F;
                burnTicks = 20 * 7;
                knockback = 1.8D;
            }
            case MAGMA_BOMB -> {
                radius = 2.4D;
                damage = 9.0F;
                burnTicks = 20 * 8;
                knockback = 1.0D;
            }
            case FIREBALL -> {
                radius = 2.1D;
                damage = 7.0F;
                burnTicks = 20 * 10;
                knockback = 0.8D;
            }
            default -> {
                radius = 2.7D;
                damage = 10.0F;
                burnTicks = 20 * 6;
                knockback = 1.25D;
            }
        }

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.EXPLOSION,
                marker.impact.x,
                marker.impact.y + 0.4D,
                marker.impact.z,
                marker.kind == MeteorKind.HEAVY_METEOR ? 4 : 2,
                0.35D, 0.22D, 0.35D,
                0.0D);

        WolfVfx.sendParticles("infernal_wolf", level,
                ParticleTypes.LAVA,
                marker.impact.x,
                marker.impact.y + 0.25D,
                marker.impact.z,
                marker.kind == MeteorKind.HEAVY_METEOR ? 18 : 10,
                radius * 0.35D,
                0.28D,
                radius * 0.35D,
                0.04D);

        AABB area = new AABB(
                marker.impact.x - radius,
                marker.impact.y - 1.2D,
                marker.impact.z - radius,
                marker.impact.x + radius,
                marker.impact.y + 3.0D,
                marker.impact.z + radius);

        // This is an impact/explosion source, not pure fire damage. Fire-immune
        // enemies still get hit in the skull by the meteor.
        DamageSource impactSource =
                this.damageSources().source(DamageTypes.EXPLOSION, this);

        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidInfernalHazardVictim)) {

            boolean hit = victim.hurtServer(level, impactSource, damage);

            if (!hit) {
                continue;
            }

            victim.setRemainingFireTicks(
                    Math.max(victim.getRemainingFireTicks(), burnTicks));

            double dx = marker.impact.x - victim.getX();
            double dz = marker.impact.z - victim.getZ();
            victim.knockback(knockback, dx, dz);

            this.onInfernalKill(level, victim);
        }
    }

    private enum MeteorKind {
        METEOR,
        MAGMA_BOMB,
        FIREBALL,
        HEAVY_METEOR
    }

    private static final class MeteorMarker {
        private final Vec3 impact;
        private int ticks;
        private final MeteorKind kind;

        private MeteorMarker(
                Vec3 impact,
                int ticks,
                MeteorKind kind) {

            this.impact = impact;
            this.ticks = ticks;
            this.kind = kind;
        }
    }

    // ---------------------------------------------------------------------
    // Dynamic Rage + Soul attributes
    // ---------------------------------------------------------------------

    private void refreshInfernalCombatModifiers() {
        if (this.isBaby() || !this.isAlive()) {
            this.clearInfernalCombatModifiers();
            return;
        }

        boolean rage = this.isInfernalRageActive();
        int souls = this.isInfernalSoulsActive()
                ? this.soulStacks
                : 0;

        double damageBonus =
                (rage ? RAGE_DAMAGE_BONUS : 0.0D)
                        + souls * SOULS_DAMAGE_PER_STACK;

        double speedBonus =
                (rage ? RAGE_SPEED_BONUS : 0.0D)
                        + souls * SOULS_SPEED_PER_STACK;

        double armorBonus =
                (rage ? RAGE_ARMOR_BONUS : 0.0D)
                        + souls * SOULS_ARMOR_PER_STACK;

        double knockbackBonus =
                (rage ? RAGE_KNOCKBACK_BONUS : 0.0D)
                        + souls * SOULS_KNOCKBACK_PER_STACK;

        this.applyOrRemoveModifier(
                Attributes.ATTACK_DAMAGE,
                INFERNAL_DAMAGE_MODIFIER,
                damageBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        this.applyOrRemoveModifier(
                Attributes.MOVEMENT_SPEED,
                INFERNAL_SPEED_MODIFIER,
                speedBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        this.applyOrRemoveModifier(
                Attributes.ARMOR,
                INFERNAL_ARMOR_MODIFIER,
                armorBonus,
                AttributeModifier.Operation.ADD_VALUE);

        this.applyOrRemoveModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                INFERNAL_KNOCKBACK_MODIFIER,
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

        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, operation));
    }

    private void clearInfernalCombatModifiers() {
        this.removeInfernalModifier(
                Attributes.ATTACK_DAMAGE,
                INFERNAL_DAMAGE_MODIFIER);

        this.removeInfernalModifier(
                Attributes.MOVEMENT_SPEED,
                INFERNAL_SPEED_MODIFIER);

        this.removeInfernalModifier(
                Attributes.ARMOR,
                INFERNAL_ARMOR_MODIFIER);

        this.removeInfernalModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                INFERNAL_KNOCKBACK_MODIFIER);
    }

    private void removeInfernalModifier(
            Holder<Attribute> attribute,
            Identifier id) {

        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // Rage durability applies on top of the temporary armor.
    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float damage) {

        float adjustedDamage = damage;

        if (this.isInfernalRageActive()
                && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            adjustedDamage *= RAGE_DAMAGE_TAKEN_MULTIPLIER;
        }

        return super.hurtServer(level, source, adjustedDamage);
    }

    // ---------------------------------------------------------------------
    // Obsidian-path landing protection
    // ---------------------------------------------------------------------

    @Override
    public boolean causeFallDamage(
            double fallDistance,
            float damageModifier,
            DamageSource damageSource) {

        if (this.level() instanceof ServerLevel level) {
            BlockPos landingPos =
                    this.getBlockPosBelowThatAffectsMyMovement();

            if (InfernalObsidianPathManager.isTemporaryInfernalPath(
                    level,
                    landingPos)) {
                this.fallDistance = 0.0D;
                return false;
            }
        }

        return super.causeFallDamage(
                fallDistance,
                damageModifier,
                damageSource);
    }

    // ---------------------------------------------------------------------
    // Pack / family intelligence
    // ---------------------------------------------------------------------

    public boolean isInfernalWolfPackmate(InfernalWolf other) {
        if (other == null
                || other == this
                || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(
                this.getOwnerReference(),
                other.getOwnerReference());
    }

    public boolean isInfernalFamilyMember(Entity entity) {
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

        return entity instanceof InfernalWolf infernal
                && this.isInfernalWolfPackmate(infernal);
    }

    public boolean isValidInfernalPackThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || target instanceof Creeper
                || this.isAlliedTo(target)
                || this.isInfernalFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public boolean isValidInfernalCombatTarget(LivingEntity target) {
        return !this.isBaby()
                && target != null
                && !(target instanceof Creeper)
                && this.canAttack(target)
                && this.isValidInfernalPackThreat(target);
    }

    public boolean isValidInfernalHazardVictim(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || candidate instanceof Creeper
                || this.isInfernalFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        return candidate == this.getTarget()
                || candidate instanceof Enemy;
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || threat instanceof Creeper
                || !this.isValidInfernalPackThreat(threat)) {
            return;
        }

        for (InfernalWolf mate : level.getEntitiesOfClass(
                InfernalWolf.class,
                this.getBoundingBox().inflate(InfernalWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive()
                        && (wolf == this
                        || this.isInfernalWolfPackmate(wolf)))) {

            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.INFERNAL_PACK_THREAT.get(),
                    threat);

            if (!mate.isBaby()
                    && mate.isValidInfernalCombatTarget(threat)) {
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

        // Family emergency is an immediate reason to establish the Arena.
        if (this.isTame()
                && !this.isBaby()
                && this.arenaTicks <= 0
                && this.arenaCooldownTicks <= 0
                && this.level() instanceof ServerLevel level) {

            this.arenaTicks = ARENA_DURATION_TICKS;

            WolfVfx.sendParticles("infernal_wolf", level,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(),
                    this.getY() + 0.55D,
                    this.getZ(),
                    24,
                    1.0D, 0.35D, 1.0D,
                    0.04D);
        }
    }

    public int countNearbyHostileThreats(double radius) {
        if (!(this.level() instanceof ServerLevel level)) {
            return 0;
        }

        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && candidate.isAlive()
                        && !this.isInfernalFamilyMember(candidate))
                .size();
    }

    // ---------------------------------------------------------------------
    // Natural spawn placement
    // ---------------------------------------------------------------------

    public static boolean checkInfernalWolfSpawnRules(
            EntityType<InfernalWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.NETHER) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos)
                        .getCollisionShape(level, pos)
                        .isEmpty()
                || !level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above())
                        .isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();

        if (!level.getBlockState(floor)
                .isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        if (reason == EntitySpawnReason.NATURAL) {
            int nearby = serverLevel.getEntitiesOfClass(
                    InfernalWolf.class,
                    new AABB(pos).inflate(56.0D, 24.0D, 56.0D),
                    wolf -> wolf.isAlive() && !wolf.isTame())
                    .size();

            if (nearby >= 2) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt("InfernalArenaTicks", this.arenaTicks);
        output.putInt("InfernalArenaCooldown", this.arenaCooldownTicks);

        output.putInt("InfernalRageTicks", this.rageTicks);
        output.putInt("InfernalRageCooldown", this.rageCooldownTicks);

        output.putInt("InfernalStrikeCooldown", this.strikeCooldownTicks);

        output.putInt("InfernalSoulsTicks", this.soulsTicks);
        output.putInt("InfernalSoulsCooldown", this.soulsCooldownTicks);
        output.putInt("InfernalSoulStacks", this.soulStacks);

        output.putInt("InfernalMeteorTicks", this.meteorShowerTicks);
        output.putInt(
                "InfernalMeteorCooldown",
                this.meteorShowerCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        this.arenaTicks = clamp(
                input.getIntOr("InfernalArenaTicks", 0),
                0,
                ARENA_DURATION_TICKS);

        this.arenaCooldownTicks = Math.max(
                0,
                input.getIntOr("InfernalArenaCooldown", 0));

        this.rageTicks = clamp(
                input.getIntOr("InfernalRageTicks", 0),
                0,
                RAGE_DURATION_TICKS);

        this.rageCooldownTicks = Math.max(
                0,
                input.getIntOr("InfernalRageCooldown", 0));

        this.strikeCooldownTicks = Math.max(
                0,
                input.getIntOr("InfernalStrikeCooldown", 0));

        this.soulsTicks = clamp(
                input.getIntOr("InfernalSoulsTicks", 0),
                0,
                SOULS_MAX_DURATION_TICKS);

        this.soulsCooldownTicks = Math.max(
                0,
                input.getIntOr("InfernalSoulsCooldown", 0));

        this.soulStacks = clamp(
                input.getIntOr("InfernalSoulStacks", 0),
                0,
                SOULS_MAX_STACKS);

        this.meteorShowerTicks = clamp(
                input.getIntOr("InfernalMeteorTicks", 0),
                0,
                METEOR_SHOWER_DURATION_TICKS);

        this.meteorShowerCooldownTicks = Math.max(
                0,
                input.getIntOr("InfernalMeteorCooldown", 0));

        // Mid-strike movement and pending meteor markers are deliberately not
        // serialized. They restart cleanly instead of restoring stale entity IDs.
        this.strikeTicks = 0;
        this.strikeTargetId = -1;
        this.strikeDirection = Vec3.ZERO;
        this.pendingMeteors.clear();

        if (this.isBaby()) {
            this.cancelAdultInfernalStates();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
