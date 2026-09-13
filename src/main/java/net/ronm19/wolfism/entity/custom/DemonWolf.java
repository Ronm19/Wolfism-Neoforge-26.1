package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 *
 * <p>Role: demonic offense / curses / pursuit / battlefield control.</p>
 *
 * <p>The Brain owns high-level target choice and battlefield awareness.
 * Vanilla/shared Wolfism goals still provide the normal wolf mechanics underneath,
 * while Demon-specific abilities are executed here from facts written by its sensors.</p>
 */
public final class DemonWolf extends AbstractWolfismWolf {
    // ---------------------------------------------------------------------
    // Identity / tags
    // ---------------------------------------------------------------------

    /**
     * Data-driven list of Nether/demonic mobs that can be intimidated by
     * Demon's Charm. Add modded mobs to #wolfism:demon_charmable without
     * touching this class.
     */
    public static final TagKey<EntityType<?>> DEMON_CHARMABLE_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "demon_charmable"));

    // ---------------------------------------------------------------------
    // Tunable balance
    // ---------------------------------------------------------------------

    public static final double COMBAT_AWARENESS_RADIUS = 32.0D;
    public static final double CLUSTER_RADIUS = 5.0D;
    public static final double CURSE_RADIUS = 8.0D;
    public static final double CHARM_RADIUS = 12.0D;
    public static final double HELL_RADIUS = 9.0D;

    private static final double DEMON_MAX_HEALTH = 36.0D;
    private static final double DEMON_ATTACK_DAMAGE = 7.0D;
    private static final double DEMON_MOVE_SPEED = 0.33D;

    private static final int ABILITY_DECISION_INTERVAL = 10;

    private static final float DEMON_BITE_BURN_SECONDS = 4.0F;

    private static final float CURSE_BURN_SECONDS = 10.0F;
    private static final int CURSE_COOLDOWN_TICKS = 20 * 18;
    private static final int CURSE_MARK_TICKS = 20 * 10;

    private static final int RUSH_DURATION_TICKS = 20 * 8;
    private static final int RUSH_COOLDOWN_TICKS = 20 * 24;
    private static final double RUSH_NAVIGATION_SPEED = 1.35D;

    private static final int HELL_DURATION_TICKS = 20 * 12;
    private static final int HELL_COOLDOWN_TICKS = 20 * 55;
    private static final int HELL_PULSE_INTERVAL = 10;

    /*
     * Demon's Charm V2:
     * ordinary tagged mobs submit reliably; heavy elites can resist; success
     * has a readable hesitation phase before retreating.
     */
    private static final int CHARM_SUCCESS_TICKS = 20 * 5;
    private static final int CHARM_HESITATION_TICKS = 14;
    private static final int CHARM_SUCCESS_RETRY_TICKS = 20 * 10;
    private static final int CHARM_RESIST_RETRY_TICKS = 20 * 4;
    private static final int CHARM_FLEE_REPATH_INTERVAL = 8;
    private static final float CHARM_ELITE_SUCCESS_CHANCE = 0.50F;
    private static final float CHARM_ELITE_MIN_MAX_HEALTH = 40.0F;

    // ---------------------------------------------------------------------
    // Client-visible combat state
    // ---------------------------------------------------------------------

    private static final EntityDataAccessor<Boolean> DATA_RUSHING =
            SynchedEntityData.defineId(DemonWolf.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_HELL_ACTIVE =
            SynchedEntityData.defineId(DemonWolf.class, EntityDataSerializers.BOOLEAN);

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int curseCooldownTicks;
    private int rushCooldownTicks;
    private int rushTicks;
    private int hellCooldownTicks;
    private int hellTicks;
    private int abilityLockoutTicks;

    private Vec3 hellCenter;

    /**
     * Short-lived tactical state. These maps are deliberately not persisted:
     * the Brain/sensors rebuild nearby combat context after a chunk reload.
     */
    private final Map<UUID, Integer> cursedTargets = new HashMap<>();
    private final Map<UUID, Integer> charmedMobTicks = new HashMap<>();
    private final Map<UUID, Integer> charmRetryTicks = new HashMap<>();

    /**
     * The family member threatened at the instant Charm succeeded.
     * Used so retreat direction is away from BOTH Demon and the protected target.
     */
    private final Map<UUID, UUID> charmProtectedTargetIds = new HashMap<>();

    private static final class BrainHolder {
        private static final Brain.Provider<DemonWolf> PROVIDER = Brain.<DemonWolf>provider(
                ImmutableList.of(
                        ModSensorTypes.DEMON_BATTLEFIELD.get(),
                        ModSensorTypes.DEMON_CHARM.get()),
                wolf -> List.of());
    }

    public DemonWolf(EntityType<? extends DemonWolf> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Base entity / Brain
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, DEMON_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DEMON_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, DEMON_MOVE_SPEED)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_RUSHING, false);
        entityData.define(DATA_HELL_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.DEMON_WOLF.get();
    }

    @Override
    protected Brain<DemonWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<DemonWolf> getBrain() {
        return (Brain<DemonWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        /*
         * Vanilla Wolf may replace max health when taming. Demon owns a fixed
         * legendary statline, so restore it explicitly.
         */
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(DEMON_MAX_HEALTH);
        }

        if (this.isTame()) {
            this.setHealth((float) DEMON_MAX_HEALTH);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        /*
         * An explicit owner sit command wins over Demon combat AI.
         * This prevents vanilla target goals and Demon sensors from reacquiring
         * a target while the wolf is supposed to remain parked.
         */
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isDemonFamilyMember(target)
                && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Server AI loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickDemonTimers(level);

        if (this.isBaby()) {
            this.clearAdultDemonState();
            return;
        }

        /*
         * OWNER COMMAND HARD GATE:
         *
         * Demon has Brain-driven targeting and a custom Rush that directly calls
         * navigation.moveTo(). Vanilla SitWhenOrderedToGoal cannot stop those
         * species-specific calls by itself, which is what allowed Demon to walk
         * around while still visually sitting.
         *
         * The sit command therefore wins before ANY Demon targeting/ability logic.
         */
        if (this.isOrderedToSit()) {
            this.enforceDemonSitCommand();
            return;
        }

        this.applyBrainTargetChoice();

        /*
         * Charm is passive control and may occur independently of the three
         * active combat abilities.
         */
        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0) {
            this.tryDemonCharm(level);
        }

        if (this.hellTicks > 0) {
            this.tickHellOnEarth(level);
        }

        if (this.rushTicks > 0) {
            this.tickDemonRush(level);
        }

        if (this.abilityLockoutTicks > 0
                || this.tickCount % ABILITY_DECISION_INTERVAL != 0) {
            return;
        }

        /*
         * Expensive territory control first, then the group curse, then pursuit.
         * Starting an ability adds a short lockout so Demon cannot fire all three
         * on the same decision tick.
         */
        if (this.hellTicks <= 0 && this.tryStartHellOnEarth(level)) {
            return;
        }

        if (this.tryCurseOfTheDemon(level)) {
            return;
        }

        if (this.rushTicks <= 0) {
            this.tryStartDemonRush(level);
        }
    }

    private void tickDemonTimers(ServerLevel level) {
        if (this.curseCooldownTicks > 0) {
            --this.curseCooldownTicks;
        }
        if (this.rushCooldownTicks > 0) {
            --this.rushCooldownTicks;
        }
        if (this.hellCooldownTicks > 0) {
            --this.hellCooldownTicks;
        }
        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        tickTimerMap(this.cursedTargets);
        tickTimerMap(this.charmRetryTicks);
        this.tickCharmedMobs(level);
    }

    private static void tickTimerMap(Map<UUID, Integer> timers) {
        Iterator<Map.Entry<UUID, Integer>> iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private void clearAdultDemonState() {
        this.setTarget(null);
        this.rushTicks = 0;
        this.hellTicks = 0;
        this.hellCenter = null;
        this.setRushing(false);
        this.setHellActive(false);
        this.setSprinting(false);
        this.cursedTargets.clear();
        this.charmedMobTicks.clear();
        this.charmRetryTicks.clear();
        this.charmProtectedTargetIds.clear();
    }

    /**
     * Keeps the synchronized physical sitting pose in lockstep with the owner's
     * command. This is intentionally server-authoritative: the renderer only
     * reads isInSittingPose() and never invents a sitting pose client-side.
     */
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            return;
        }

        if (this.isOrderedToSit()) {
            this.enforceDemonSitCommand();
        } else if (this.isInSittingPose()) {
            /*
             * Important stand-up half of the synchronization.
             * Without this, the physical pose can remain latched after the owner
             * cancels sitting, which creates "walking while seated".
             */
            this.setInSittingPose(false);
        }
    }

    private void enforceDemonSitCommand() {
        this.getNavigation().stop();

        if (this.getTarget() != null) {
            this.setTarget(null);
        }

        /*
         * Sitting cancels active movement/combat states, but their cooldowns stay
         * intact so the player cannot exploit sit toggling for free recasts.
         */
        if (this.rushTicks > 0 || this.isRushing()) {
            this.finishDemonRush();
        }

        if (this.hellTicks > 0 || this.isHellActive()) {
            this.finishHellOnEarth();
        }

        this.setSprinting(false);

        /*
         * Grounded Demon Wolves sit physically. If knockback briefly lifts one
         * from the ground, do not freeze it in the seated pose in mid-air.
         */
        this.setInSittingPose(this.onGround());

        Vec3 motion = this.getDeltaMovement();
        if (Math.abs(motion.x) > 1.0E-4D || Math.abs(motion.z) > 1.0E-4D) {
            this.setDeltaMovement(0.0D, motion.y, 0.0D);
            this.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------------
    // Brain target selection
    // ---------------------------------------------------------------------

    private void applyBrainTargetChoice() {
        LivingEntity brainTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.DEMON_PRIORITY_TARGET.get())
                .orElse(null);

        LivingEntity current = this.getTarget();

        if (!this.isRelevantDemonThreat(brainTarget)) {
            brainTarget = null;
        }
        if (!this.isRelevantDemonThreat(current)) {
            current = null;
        }

        if (this.isRushing() && current != null) {
            /*
             * Rush is deliberately sticky. The only thing that should steal the
             * target mid-hunt is a clearly more urgent family emergency.
             */
            if (brainTarget != null
                    && this.isTargetingProtectedFamily(brainTarget)
                    && !this.isTargetingProtectedFamily(current)) {
                this.setTarget(brainTarget);
            } else {
                this.setTarget(current);
            }
            return;
        }

        if (brainTarget == null) {
            return;
        }

        if (current == null
                || this.demonThreatScore(brainTarget)
                < this.demonThreatScore(current) - 40.0D) {
            this.setTarget(brainTarget);
        }
    }

    public List<LivingEntity> findDemonThreats(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isRelevantDemonThreat);
    }

    public boolean isRelevantDemonThreat(LivingEntity candidate) {
        if (!this.isValidDemonCombatTarget(candidate)) {
            return false;
        }

        if (candidate == this.getTarget()
                || candidate == this.getLastHurtByMob()
                || this.isTargetingProtectedFamily(candidate)) {
            return true;
        }

        /*
         * Once tamed, Demon actively pressures ordinary hostile mobs around the
         * family instead of waiting for the first hit.
         */
        return this.isTame() && candidate instanceof Enemy;
    }

    private boolean isValidDemonCombatTarget(LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && !this.isCharmed(candidate)
                && !this.isDemonFamilyMember(candidate)
                && !this.isAlliedTo(candidate)
                && this.canAttack(candidate);
    }

    public double demonThreatScore(LivingEntity candidate) {
        if (candidate == null) {
            return Double.MAX_VALUE;
        }

        double score = this.distanceToSqr(candidate);

        if (this.isTargetingProtectedFamily(candidate)) {
            score -= 520.0D;
        }

        if (candidate == this.getLastHurtByMob()) {
            score -= 320.0D;
        }

        if (this.isCursed(candidate)) {
            score -= 180.0D;
        }

        if (candidate == this.getTarget()) {
            score -= 80.0D;
        }

        score -= Math.min(160.0D, candidate.getMaxHealth() * 1.5D);
        return score;
    }

    public int countDemonThreatsAround(
            List<LivingEntity> threats,
            LivingEntity center) {

        if (center == null) {
            return 0;
        }

        double radiusSqr = CLUSTER_RADIUS * CLUSTER_RADIUS;
        int count = 0;

        for (LivingEntity threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && threat.distanceToSqr(center) <= radiusSqr) {
                ++count;
            }
        }

        return count;
    }

    // ---------------------------------------------------------------------
    // Family protection
    // ---------------------------------------------------------------------

    /**
     * Demon never harms the owner or a tamed wolf.
     *
     * <p>This deliberately mirrors Wolfism's broad "tamed wolves are family"
     * rule instead of protecting only Demon Wolves.</p>
     */
    public boolean isDemonFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && entity == owner) {
            return true;
        }

        return entity instanceof Wolf wolf && wolf.isTame();
    }

    public boolean isTargetingProtectedFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        return target != null && this.isDemonFamilyMember(target);
    }

    // ---------------------------------------------------------------------
    // 1) Curse of the Demon - AoE ignition
    // ---------------------------------------------------------------------

    private boolean tryCurseOfTheDemon(ServerLevel level) {
        if (this.curseCooldownTicks > 0 || this.isBaby()) {
            return false;
        }

        List<LivingEntity> threats = this.findDemonThreats(level, CURSE_RADIUS)
                .stream()
                .filter(threat -> !this.isCharmed(threat))
                .toList();

        if (threats.isEmpty()) {
            return false;
        }

        LivingEntity primary = this.getPrimaryDemonTarget();
        boolean dangerousSingle = threats.size() == 1
                && primary != null
                && primary.distanceToSqr(this) <= CURSE_RADIUS * CURSE_RADIUS
                && primary.getMaxHealth() >= 40.0F;

        if (threats.size() < 2 && !dangerousSingle) {
            return false;
        }

        this.curseCooldownTicks = CURSE_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 20;

        for (LivingEntity target : threats) {
            target.igniteForSeconds(CURSE_BURN_SECONDS);
            this.cursedTargets.put(target.getUUID(), CURSE_MARK_TICKS);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.FLAME,
                    target.getX(),
                    target.getY(0.55D),
                    target.getZ(),
                    10,
                    0.30D,
                    0.35D,
                    0.30D,
                    0.035D);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.SMOKE,
                    target.getX(),
                    target.getY(0.65D),
                    target.getZ(),
                    6,
                    0.24D,
                    0.30D,
                    0.24D,
                    0.015D);
        }

        this.sendRing(level, this.position(), CURSE_RADIUS * 0.78D, 42, ParticleTypes.FLAME);
        this.playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 0.85F, 0.72F);
        return true;
    }

    public boolean isCursed(LivingEntity target) {
        return target != null && this.cursedTargets.containsKey(target.getUUID());
    }

    // ---------------------------------------------------------------------
    // 2) Demon's Bite - physical melee + fire
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);

        if (hit
                && !this.isBaby()
                && target instanceof LivingEntity living
                && !this.isDemonFamilyMember(living)) {

            target.igniteForSeconds(DEMON_BITE_BURN_SECONDS);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.FLAME,
                    target.getX(),
                    target.getY(0.55D),
                    target.getZ(),
                    5,
                    0.18D,
                    0.20D,
                    0.18D,
                    0.025D);
        }

        return hit;
    }

    // ---------------------------------------------------------------------
    // 3) Demon Rush - pursuit combat state
    // ---------------------------------------------------------------------

    private boolean tryStartDemonRush(ServerLevel level) {
        if (this.rushCooldownTicks > 0
                || this.rushTicks > 0
                || this.isBaby()) {
            return false;
        }

        LivingEntity target = this.getPrimaryDemonTarget();
        if (!this.isRelevantDemonThreat(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        boolean fleeingCursedTarget = this.isCursed(target) && distanceSqr >= 9.0D;
        boolean gapClose = distanceSqr >= 20.25D;
        boolean dangerousTarget = target.getMaxHealth() >= 40.0F;

        if (!fleeingCursedTarget && !gapClose && !dangerousTarget) {
            return false;
        }

        this.rushTicks = RUSH_DURATION_TICKS;
        this.rushCooldownTicks = RUSH_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 16;
        this.setRushing(true);
        this.setSprinting(true);
        this.setTarget(target);

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.SPEED,
                        RUSH_DURATION_TICKS,
                        1,
                        true,
                        true),
                this);

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.STRENGTH,
                        RUSH_DURATION_TICKS,
                        0,
                        true,
                        true),
                this);

        WolfVfx.sendParticles("demon_wolf", level,
                ParticleTypes.FLAME,
                this.getX(),
                this.getY(0.55D),
                this.getZ(),
                18,
                0.35D,
                0.28D,
                0.35D,
                0.035D);

        this.playSound(this.getWolfismGrowlSound(), 1.0F, 0.65F);
        return true;
    }

    private void tickDemonRush(ServerLevel level) {
        --this.rushTicks;

        LivingEntity target = this.getTarget();
        LivingEntity brainTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.DEMON_PRIORITY_TARGET.get())
                .orElse(null);

        if (brainTarget != null
                && this.isRelevantDemonThreat(brainTarget)
                && this.isTargetingProtectedFamily(brainTarget)
                && (target == null || !this.isTargetingProtectedFamily(target))) {
            target = brainTarget;
            this.setTarget(target);
        }

        if (!this.isRelevantDemonThreat(target)) {
            this.finishDemonRush();
            return;
        }

        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.getNavigation().moveTo(target, RUSH_NAVIGATION_SPEED);

        if (this.tickCount % 4 == 0) {
            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY(0.35D),
                    this.getZ(),
                    3,
                    0.16D,
                    0.12D,
                    0.16D,
                    0.01D);
        }

        if (this.rushTicks <= 0) {
            this.finishDemonRush();
        }
    }

    private void finishDemonRush() {
        this.rushTicks = 0;
        this.setRushing(false);
        this.setSprinting(false);
    }

    // ---------------------------------------------------------------------
    // 4) Demon's Charm - passive intimidation/control
    // ---------------------------------------------------------------------

    public boolean isValidDemonCharmThreat(Mob mob) {
        if (mob == null
                || !mob.isAlive()
                || this.isDemonFamilyMember(mob)
                || this.isAlliedTo(mob)
                || !mob.is(DEMON_CHARMABLE_TYPES)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        return target != null && this.isDemonFamilyMember(target);
    }

    private void tryDemonCharm(ServerLevel level) {
        /*
         * Brain memory remains authoritative when present. The direct local
         * fallback only covers the sensor's in-between ticks so the passive
         * effect does not look randomly unresponsive.
         */
        Mob mob = this.getBrain()
                .getMemory(ModMemoryModuleTypes.DEMON_CHARM_THREAT.get())
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .filter(this::isValidDemonCharmThreat)
                .orElseGet(() -> this.findImmediateDemonCharmThreat(level));

        if (mob == null
                || this.charmedMobTicks.containsKey(mob.getUUID())
                || this.charmRetryTicks.containsKey(mob.getUUID())) {
            return;
        }

        LivingEntity threatenedFamily = mob.getTarget();
        boolean elite = mob.getMaxHealth() >= CHARM_ELITE_MIN_MAX_HEALTH;

        /*
         * Ordinary tagged mobs reliably recognize Demon's authority.
         * Heavy elites retain a real chance to refuse him.
         */
        boolean submitted = !elite
                || this.random.nextFloat() < CHARM_ELITE_SUCCESS_CHANCE;

        this.sendDemonCharmTether(level, mob, submitted);

        if (submitted) {
            UUID mobId = mob.getUUID();

            this.charmedMobTicks.put(mobId, CHARM_SUCCESS_TICKS);
            this.charmRetryTicks.put(mobId, CHARM_SUCCESS_RETRY_TICKS);

            if (threatenedFamily != null
                    && this.isDemonFamilyMember(threatenedFamily)) {
                this.charmProtectedTargetIds.put(
                        mobId,
                        threatenedFamily.getUUID());
            }

            boolean wasCurrentDemonTarget = this.getTarget() == mob;

            if (wasCurrentDemonTarget) {
                this.setTarget(null);
            }

            if (this.isRushing() && wasCurrentDemonTarget) {
                this.finishDemonRush();
            }

            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(this, 30.0F, 30.0F);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.REVERSE_PORTAL,
                    mob.getX(),
                    mob.getY(0.72D),
                    mob.getZ(),
                    18,
                    0.34D,
                    0.42D,
                    0.34D,
                    0.025D);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY(0.70D),
                    this.getZ(),
                    10,
                    0.26D,
                    0.24D,
                    0.26D,
                    0.01D);

            this.playSound(
                    this.getWolfismGrowlSound(),
                    0.85F,
                    0.58F);
        } else {
            /*
             * Resistance is deliberately obvious and becomes an immediate
             * challenge: no hesitation, no retreat, Demon retaliates.
             */
            this.charmRetryTicks.put(
                    mob.getUUID(),
                    CHARM_RESIST_RETRY_TICKS);

            if (this.isValidDemonCombatTarget(mob)) {
                this.setTarget(mob);
            }

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.ANGRY_VILLAGER,
                    mob.getX(),
                    mob.getY(0.80D),
                    mob.getZ(),
                    8,
                    0.28D,
                    0.32D,
                    0.28D,
                    0.01D);

            WolfVfx.sendParticles("demon_wolf", level,
                    ParticleTypes.FLAME,
                    this.getX(),
                    this.getY(0.62D),
                    this.getZ(),
                    7,
                    0.20D,
                    0.20D,
                    0.20D,
                    0.025D);
        }
    }

    private Mob findImmediateDemonCharmThreat(ServerLevel level) {
        return level.getEntitiesOfClass(
                        Mob.class,
                        this.getBoundingBox().inflate(CHARM_RADIUS),
                        this::isValidDemonCharmThreat)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void tickCharmedMobs(ServerLevel level) {
        Iterator<Map.Entry<UUID, Integer>> iterator =
                this.charmedMobTicks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            UUID mobId = entry.getKey();
            int next = entry.getValue() - 1;

            Entity entity = level.getEntity(mobId);

            if (!(entity instanceof Mob mob)
                    || !mob.isAlive()
                    || next <= 0) {
                iterator.remove();
                this.charmProtectedTargetIds.remove(mobId);
                continue;
            }

            entry.setValue(next);

            /*
             * Suppress family aggression every tick. The old five-tick interval
             * let hostile target goals visibly reacquire the owner between pulses.
             */
            LivingEntity currentTarget = mob.getTarget();
            if (currentTarget != null
                    && this.isDemonFamilyMember(currentTarget)) {
                mob.setTarget(null);
            }

            mob.setAggressive(false);

            int elapsed = CHARM_SUCCESS_TICKS - next;

            if (elapsed <= CHARM_HESITATION_TICKS) {
                /*
                 * Hesitation: stop, face Demon, visibly recognize his authority.
                 */
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(this, 30.0F, 30.0F);

                if (elapsed % 4 == 0) {
                    WolfVfx.sendParticles("demon_wolf", level,
                            ParticleTypes.REVERSE_PORTAL,
                            mob.getX(),
                            mob.getY(0.70D),
                            mob.getZ(),
                            3,
                            0.18D,
                            0.22D,
                            0.18D,
                            0.01D);
                }

                continue;
            }

            /*
             * Retreat away from the combined Demon + protected-family center.
             * This prevents the old case where Demon stood behind the player and
             * "away from Demon" accidentally sent the victim through the player.
             */
            if (elapsed % CHARM_FLEE_REPATH_INTERVAL == 0) {
                this.pushCharmTargetAway(level, mob);
            }

            if (elapsed % 20 == 0) {
                WolfVfx.sendParticles("demon_wolf", level,
                        ParticleTypes.SMOKE,
                        mob.getX(),
                        mob.getY(0.50D),
                        mob.getZ(),
                        4,
                        0.20D,
                        0.24D,
                        0.20D,
                        0.01D);
            }
        }
    }

    private void pushCharmTargetAway(
            ServerLevel level,
            Mob mob) {

        Vec3 dangerCenter = this.position();
        int dangerSources = 1;

        UUID protectedTargetId =
                this.charmProtectedTargetIds.get(mob.getUUID());

        Entity protectedEntity = protectedTargetId == null
                ? null
                : level.getEntity(protectedTargetId);

        if (protectedEntity instanceof LivingEntity protectedTarget
                && protectedTarget.isAlive()) {
            dangerCenter = dangerCenter.add(protectedTarget.position());
            ++dangerSources;
        } else {
            LivingEntity owner = this.getOwner();

            if (owner != null && owner.isAlive()) {
                dangerCenter = dangerCenter.add(owner.position());
                ++dangerSources;
            }
        }

        dangerCenter = dangerCenter.scale(1.0D / dangerSources);

        Vec3 away = mob.position().subtract(dangerCenter);
        Vec3 horizontal = new Vec3(
                away.x,
                0.0D,
                away.z);

        if (horizontal.lengthSqr() <= 1.0E-6D) {
            double side = ((mob.getId() & 1) == 0)
                    ? 1.0D
                    : -1.0D;
            horizontal = new Vec3(side, 0.0D, 0.0D);
        }

        horizontal = horizontal.normalize().scale(7.0D);

        mob.getNavigation().moveTo(
                mob.getX() + horizontal.x,
                mob.getY(),
                mob.getZ() + horizontal.z,
                1.20D);
    }

    private void sendDemonCharmTether(
            ServerLevel level,
            Mob mob,
            boolean submitted) {

        Vec3 start = this.position().add(
                0.0D,
                this.getBbHeight() * 0.72D,
                0.0D);

        Vec3 end = mob.position().add(
                0.0D,
                mob.getBbHeight() * 0.68D,
                0.0D);

        ParticleOptions particle = submitted
                ? ParticleTypes.REVERSE_PORTAL
                : ParticleTypes.FLAME;

        /*
         * The visible tether identifies exactly which mob Demon attempted to
         * dominate even in a crowded family battle.
         */
        for (int i = 1; i <= 8; ++i) {
            double t = i / 9.0D;

            WolfVfx.sendParticles("demon_wolf", level,
                    particle,
                    Mth.lerp(t, start.x, end.x),
                    Mth.lerp(t, start.y, end.y),
                    Mth.lerp(t, start.z, end.z),
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);
        }
    }

    private boolean isCharmed(LivingEntity target) {
        return target != null && this.charmedMobTicks.containsKey(target.getUUID());
    }

    // ---------------------------------------------------------------------
    // 5) Hell on Earth - ultimate battlefield territory
    // ---------------------------------------------------------------------

    private boolean tryStartHellOnEarth(ServerLevel level) {
        if (this.hellCooldownTicks > 0
                || this.hellTicks > 0
                || this.isBaby()) {
            return false;
        }

        List<LivingEntity> threats = this.findDemonThreats(level, HELL_RADIUS)
                .stream()
                .filter(threat -> !this.isCharmed(threat))
                .toList();

        if (threats.isEmpty()) {
            return false;
        }

        boolean familyEmergency = threats.size() >= 2
                && threats.stream().anyMatch(this::isTargetingProtectedFamily);

        LivingEntity primary = this.getPrimaryDemonTarget();
        boolean heavyweight = primary != null
                && primary.distanceToSqr(this) <= HELL_RADIUS * HELL_RADIUS
                && primary.getMaxHealth() >= 60.0F;

        if (threats.size() < 4 && !familyEmergency && !heavyweight) {
            return false;
        }

        this.hellCenter = this.position();
        this.hellTicks = HELL_DURATION_TICKS;
        this.hellCooldownTicks = HELL_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 40;
        this.setHellActive(true);

        this.sendRing(level, this.hellCenter, 3.0D, 28, ParticleTypes.FLAME);
        this.sendRing(level, this.hellCenter, HELL_RADIUS, 56, ParticleTypes.SMOKE);
        this.playSound(this.getWolfismGrowlSound(), 0.70F, 0.62F);
        return true;
    }

    private void tickHellOnEarth(ServerLevel level) {
        if (this.hellCenter == null) {
            this.finishHellOnEarth();
            return;
        }

        --this.hellTicks;

        if (this.tickCount % 5 == 0) {
            this.sendRing(
                    level,
                    this.hellCenter,
                    HELL_RADIUS * 0.82D,
                    34,
                    ParticleTypes.SMOKE);
        }

        if (this.tickCount % HELL_PULSE_INTERVAL == 0) {
            AABB zone = new AABB(
                    this.hellCenter.x - HELL_RADIUS,
                    this.hellCenter.y - HELL_RADIUS,
                    this.hellCenter.z - HELL_RADIUS,
                    this.hellCenter.x + HELL_RADIUS,
                    this.hellCenter.y + HELL_RADIUS,
                    this.hellCenter.z + HELL_RADIUS);

            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    zone,
                    this::isHellOnEarthTarget)) {

                target.igniteForSeconds(3.0F);

                target.addEffect(
                        new MobEffectInstance(
                                MobEffects.DARKNESS,
                                50,
                                0,
                                true,
                                true),
                        this);

                target.addEffect(
                        new MobEffectInstance(
                                MobEffects.WEAKNESS,
                                50,
                                0,
                                true,
                                true),
                        this);

                WolfVfx.sendParticles("demon_wolf", level,
                        ParticleTypes.FLAME,
                        target.getX(),
                        target.getY(0.35D),
                        target.getZ(),
                        3,
                        0.18D,
                        0.14D,
                        0.18D,
                        0.018D);
            }
        }

        if (this.hellTicks <= 0) {
            this.finishHellOnEarth();
        }
    }

    private boolean isHellOnEarthTarget(LivingEntity target) {
        if (!this.isValidDemonCombatTarget(target)
                || this.isCharmed(target)) {
            return false;
        }

        if (target == this.getTarget()
                || target == this.getLastHurtByMob()
                || this.isTargetingProtectedFamily(target)
                || this.isCursed(target)) {
            return true;
        }

        return this.isTame() && target instanceof Enemy;
    }

    private void finishHellOnEarth() {
        this.hellTicks = 0;
        this.hellCenter = null;
        this.setHellActive(false);
    }

    // ---------------------------------------------------------------------
    // Primary target helpers
    // ---------------------------------------------------------------------

    private LivingEntity getPrimaryDemonTarget() {
        LivingEntity target = this.getTarget();
        if (this.isRelevantDemonThreat(target)) {
            return target;
        }

        target = this.getBrain()
                .getMemory(ModMemoryModuleTypes.DEMON_PRIORITY_TARGET.get())
                .orElse(null);

        return this.isRelevantDemonThreat(target) ? target : null;
    }

    // ---------------------------------------------------------------------
    // Visual state accessors
    // ---------------------------------------------------------------------

    public boolean isRushing() {
        return this.entityData.get(DATA_RUSHING);
    }

    private void setRushing(boolean rushing) {
        this.entityData.set(DATA_RUSHING, rushing);
    }

    public boolean isHellActive() {
        return this.entityData.get(DATA_HELL_ACTIVE);
    }

    private void setHellActive(boolean active) {
        this.entityData.set(DATA_HELL_ACTIVE, active);
    }

    public boolean isDemonCombatVisualActive() {
        return this.isRushing() || this.isHellActive();
    }

    // ---------------------------------------------------------------------
    // Particle helpers
    // ---------------------------------------------------------------------

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {

        for (int i = 0; i < points; ++i) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            WolfVfx.sendParticles("demon_wolf", level,
                    particle,
                    x,
                    center.y + 0.16D,
                    z,
                    1,
                    0.02D,
                    0.03D,
                    0.02D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    public static boolean checkDemonWolfSpawnRules(
            EntityType<DemonWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        /*
         * Demon is legendary, so prevent natural local pile-ups even if a user
         * temporarily raises biome spawn weight for testing.
         */
        if (spawnReason == EntitySpawnReason.NATURAL
                && level instanceof ServerLevel serverLevel) {

            int nearbyWildDemons = serverLevel.getEntitiesOfClass(
                    DemonWolf.class,
                    new AABB(pos).inflate(48.0D, 16.0D, 48.0D),
                    wolf -> wolf.isAlive() && !wolf.isTame()).size();

            if (nearbyWildDemons >= 2) {
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

        output.putInt("DemonCurseCooldown", this.curseCooldownTicks);
        output.putInt("DemonRushCooldown", this.rushCooldownTicks);
        output.putInt("DemonRushTicks", this.rushTicks);
        output.putInt("DemonHellCooldown", this.hellCooldownTicks);
        output.putInt("DemonHellTicks", this.hellTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        /*
         * Restore Demon's max-health baseline before/after the vanilla Wolf data
         * path so a loaded Demon cannot remain on an unintended vanilla value.
         */
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(DEMON_MAX_HEALTH);
        }

        super.readAdditionalSaveData(input);

        if (maxHealth != null) {
            maxHealth.setBaseValue(DEMON_MAX_HEALTH);
        }

        this.curseCooldownTicks =
                Math.max(0, input.getIntOr("DemonCurseCooldown", 0));

        this.rushCooldownTicks =
                Math.max(0, input.getIntOr("DemonRushCooldown", 0));

        this.rushTicks = Mth.clamp(
                input.getIntOr("DemonRushTicks", 0),
                0,
                RUSH_DURATION_TICKS);

        this.hellCooldownTicks =
                Math.max(0, input.getIntOr("DemonHellCooldown", 0));

        this.hellTicks = Mth.clamp(
                input.getIntOr("DemonHellTicks", 0),
                0,
                HELL_DURATION_TICKS);

        this.setRushing(this.rushTicks > 0 && !this.isBaby());
        this.setHellActive(this.hellTicks > 0 && !this.isBaby());
        this.hellCenter = this.hellTicks > 0 ? this.position() : null;

        this.cursedTargets.clear();
        this.charmedMobTicks.clear();
        this.charmRetryTicks.clear();
        this.charmProtectedTargetIds.clear();

        if (this.isBaby()) {
            this.clearAdultDemonState();
        }
    }

    // ---------------------------------------------------------------------
    // Optional debug/read-only values for future HUD / tests
    // ---------------------------------------------------------------------

    public int getCurseCooldownTicks() {
        return this.curseCooldownTicks;
    }

    public int getRushCooldownTicks() {
        return this.rushCooldownTicks;
    }

    public int getHellCooldownTicks() {
        return this.hellCooldownTicks;
    }

    public int getHellTicks() {
        return this.hellTicks;
    }
}
