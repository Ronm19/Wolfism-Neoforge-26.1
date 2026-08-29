package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.OmenPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.OmenPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.OmenPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.OmenPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.OmenPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.OmenWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #30 - Omen Wolf.
 *
 * <p>Omen is a prediction / misfortune / control specialist rather than a raw
 * damage wolf. She identifies developing danger, makes dangerous enemies less
 * effective, and feeds predictive information to nearby family so the pack can
 * react before a threat fully develops.</p>
 *
 * <p>Current ability kit:</p>
 * <ol>
 *     <li>Omen Sense</li>
 *     <li>Mark of Misfortune</li>
 *     <li>Premonition</li>
 *     <li>Ominous Bite</li>
 *     <li>Ominous Presence</li>
 *     <li>Bad Omen</li>
 *     <li>Harbinger's Warning</li>
 * </ol>
 */
public final class OmenWolf extends AbstractWolfismWolf {
    public static final double PACK_SCAN_RADIUS = 34.0D;
    public static final double OMEN_SENSE_RANGE = 34.0D;

    // ---------------------------------------------------------------------
    // 2) Mark of Misfortune
    // ---------------------------------------------------------------------

    public static final int MISFORTUNE_DURATION_TICKS = 20 * 20;
    public static final int MAX_MISFORTUNE_SEVERITY = 3;

    // ---------------------------------------------------------------------
    // 3) Premonition
    // Timing was not locked in the supplied canon, so this is first-pass tuning.
    // ---------------------------------------------------------------------

    public static final int PREMONITION_DURATION_TICKS = 20 * 10;
    public static final int PREMONITION_COOLDOWN_TICKS = 20 * 26;
    public static final double PREMONITION_FAMILY_RADIUS = 12.0D;

    // ---------------------------------------------------------------------
    // 4) Ominous Bite — heavy-threat exploitation
    // ---------------------------------------------------------------------

    /*
     * Omen is not a raw DPS wolf, but her prediction should let her exploit
     * openings in large / armored priority threats such as Ravagers.
     *
     * This is intentionally a moderate bonus and does NOT bypass armor.
     * Ravager example: 100 max HP -> qualifies automatically.
     * Modded heavy mobs can also qualify through high armor.
     */
    private static final float HEAVY_TARGET_HEALTH_THRESHOLD = 50.0F;
    private static final double HEAVY_TARGET_ARMOR_THRESHOLD = 8.0D;
    private static final double HEAVY_TARGET_DAMAGE_BONUS = 0.35D;

    private static final Identifier OMINOUS_HEAVY_BITE_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "ominous_heavy_bite");

    // ---------------------------------------------------------------------
    // 6) Bad Omen
    // ---------------------------------------------------------------------

    public static final int BAD_OMEN_EFFECT_DURATION_TICKS = 20 * 12;
    public static final int BAD_OMEN_COOLDOWN_TICKS = 20 * 34;
    public static final double BAD_OMEN_RADIUS = 10.0D;

    // ---------------------------------------------------------------------
    // 7) Harbinger's Warning
    // ---------------------------------------------------------------------

    public static final int HARBINGER_DURATION_TICKS = 20 * 14;
    public static final int HARBINGER_COOLDOWN_TICKS = 20 * 75;
    public static final double HARBINGER_FAMILY_RADIUS = 18.0D;

    private static final int OMEN_WARNING_INTERVAL = 20;
    private static final int PRESENCE_PARTICLE_INTERVAL = 20;
    private static final int HARBINGER_UPDATE_INTERVAL = 10;

    private int markedTargetId = -1;
    private int misfortuneSeverity;
    private int misfortuneTicks;

    /*
     * Bad Omen / Harbinger can spread short-lived secondary Misfortune to
     * several enemies without replacing Omen's primary marked target.
     */
    private final Map<Integer, AreaMisfortune> areaMisfortune = new HashMap<>();

    private int premonitionTicks;
    private int premonitionCooldownTicks;

    private int badOmenCooldownTicks;

    private int harbingerTicks;
    private int harbingerCooldownTicks;

    private int abilityLockoutTicks;
    private int combatTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<OmenWolf> PROVIDER = Brain.<OmenWolf>provider(
                ImmutableList.of(
                        ModSensorTypes.OMEN_PACK.get(),
                        ModSensorTypes.OMEN_THREAT.get()),
                wolf -> List.of());
    }

    private record AreaMisfortune(int severity, long expiresAt) {
    }

    public OmenWolf(EntityType<? extends OmenWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.OMEN_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new OmenPupRetreatGoal(this));
        this.goalSelector.addGoal(5, new OmenPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new OmenPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new OmenPupPlayGoal(this));

        this.targetSelector.addGoal(3, new OmenPackAssistGoal(this));
    }

    @Override
    protected Brain<OmenWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<OmenWolf> getBrain() {
        return (Brain<OmenWolf>)super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();

        if (current instanceof OmenWolf omen
                && this.isOmenPackmate(omen)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        if (this.isBaby()) {
            this.setTarget(null);
            this.clearAdultOmenState();
            return;
        }

        this.tickCooldowns();
        this.tickMisfortune(level);
        this.tickAreaMisfortune(level);

        LivingEntity threat = this.getDevelopingThreat();

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            ++this.combatTicks;
        } else if (this.hasFamilyDefenseEmergency()) {
            ++this.combatTicks;
        } else {
            this.combatTicks = 0;
        }

        this.tickOmenSense(level, threat);
        this.tickOminousPresence(level, threat);
        this.tickPremonition(level, threat);
        this.tickHarbinger(level);

        if (this.getTarget() == null) {
            LivingEntity shared = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.OMEN_PACK_THREAT.get())
                    .orElse(null);

            if (shared != null
                    && !(shared instanceof Creeper)
                    && this.isValidOmenCombatTarget(shared)) {
                this.setTarget(shared);
            } else if (threat != null
                    && !(threat instanceof Creeper)
                    && this.isThreatCommittedToFamily(threat)
                    && this.isValidOmenCombatTarget(threat)) {
                this.setTarget(threat);
            }
        }

        // Serious abilities are deliberately staggered so Omen does not dump
        // her entire kit on the exact same tick.
        if (this.abilityLockoutTicks <= 0) {
            if (!this.tryStartHarbinger(level, threat)) {
                if (!this.tryStartPremonition(level, threat)) {
                    this.tryUseBadOmen(level);
                }
            }
        }
    }

    private void tickCooldowns() {
        if (this.premonitionCooldownTicks > 0) {
            --this.premonitionCooldownTicks;
        }

        if (this.badOmenCooldownTicks > 0) {
            --this.badOmenCooldownTicks;
        }

        if (this.harbingerCooldownTicks > 0) {
            --this.harbingerCooldownTicks;
        }

        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }
    }

    private void clearAdultOmenState() {
        this.markedTargetId = -1;
        this.misfortuneSeverity = 0;
        this.misfortuneTicks = 0;
        this.areaMisfortune.clear();

        this.premonitionTicks = 0;
        this.harbingerTicks = 0;
        this.abilityLockoutTicks = 0;
    }

    // ---------------------------------------------------------------------
    // 1) Omen Sense
    // ---------------------------------------------------------------------

    public LivingEntity getDevelopingThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.OMEN_DEVELOPING_THREAT.get())
                .orElse(null);
    }

    private void tickOmenSense(ServerLevel level, LivingEntity threat) {
        if (threat == null || !threat.isAlive()) {
            return;
        }

        this.getLookControl().setLookAt(threat, 55.0F, 40.0F);

        if (this.tickCount % OMEN_WARNING_INTERVAL != 0) {
            return;
        }

        // The small violet warning around Omen is the owner's visual cue that
        // she has noticed something before ordinary combat begins.
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.72D,
                this.getZ(),
                5,
                0.22D, 0.18D, 0.22D,
                0.01D);

        this.warnNearbyFamily(threat);

        LivingEntity owner = this.getOwner();

        if (owner != null
                && owner.isAlive()
                && this.distanceToSqr(owner)
                        <= PREMONITION_FAMILY_RADIUS * PREMONITION_FAMILY_RADIUS) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    owner.getX(),
                    owner.getY() + owner.getBbHeight() * 0.8D,
                    owner.getZ(),
                    2,
                    0.18D, 0.20D, 0.18D,
                    0.0D);
        }

        /*
         * Omen Sense is predictive, not aggression-by-proximity. A merely
         * nearby zombie is watched. A threat already committing to family is
         * the point where the base Misfortune mark is applied.
         */
        if (!(threat instanceof Creeper)
                && this.isThreatCommittedToFamily(threat)) {
            this.applyBaseMisfortune(level, threat);
        }
    }

    private void warnNearbyFamily(LivingEntity threat) {
        if (!this.isTame() || threat == null) {
            return;
        }

        for (Wolf family : this.level().getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(PREMONITION_FAMILY_RADIUS),
                wolf -> wolf.isAlive()
                        && wolf.isTame()
                        && !wolf.isOrderedToSit())) {

            family.getLookControl().setLookAt(
                    threat,
                    45.0F,
                    35.0F);
        }
    }

    // ---------------------------------------------------------------------
    // 2) Mark of Misfortune
    // ---------------------------------------------------------------------

    public LivingEntity getMarkedTarget(ServerLevel level) {
        if (this.markedTargetId < 0 || this.misfortuneTicks <= 0) {
            return null;
        }

        Entity entity = level.getEntity(this.markedTargetId);
        return entity instanceof LivingEntity living
                && living.isAlive()
                ? living
                : null;
    }

    public int getMisfortuneSeverityAgainst(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return 0;
        }

        int severity = 0;

        if (this.markedTargetId == target.getId()
                && this.misfortuneTicks > 0) {
            severity = this.misfortuneSeverity;
        }

        AreaMisfortune area = this.areaMisfortune.get(target.getId());

        if (area != null
                && this.level() instanceof ServerLevel level
                && area.expiresAt() > level.getGameTime()) {
            severity = Math.max(severity, area.severity());
        }

        return severity;
    }

    public void applyBaseMisfortune(
            ServerLevel level,
            LivingEntity target) {

        if (!this.canApplyMisfortune(target)) {
            return;
        }

        int currentSeverity = this.getMisfortuneSeverityAgainst(target);
        int severity = Math.max(1, currentSeverity);

        this.setPrimaryMisfortune(
                level,
                target,
                severity);
    }

    public void intensifyMisfortune(
            ServerLevel level,
            LivingEntity target) {

        if (!this.canApplyMisfortune(target)) {
            return;
        }

        int currentSeverity = this.getMisfortuneSeverityAgainst(target);

        this.setPrimaryMisfortune(
                level,
                target,
                Math.min(
                        MAX_MISFORTUNE_SEVERITY,
                        Math.max(1, currentSeverity + 1)));
    }

    private void setPrimaryMisfortune(
            ServerLevel level,
            LivingEntity target,
            int severity) {

        this.markedTargetId = target.getId();
        this.misfortuneSeverity = clamp(
                severity,
                1,
                MAX_MISFORTUNE_SEVERITY);

        this.misfortuneTicks = MISFORTUNE_DURATION_TICKS;

        this.applyMisfortuneEffects(
                target,
                this.misfortuneSeverity,
                MISFORTUNE_DURATION_TICKS);

        level.sendParticles(
                ParticleTypes.WITCH,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.70D,
                target.getZ(),
                8 + this.misfortuneSeverity * 3,
                0.28D, 0.32D, 0.28D,
                0.03D);

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                4 + this.misfortuneSeverity * 2,
                0.20D, 0.24D, 0.20D,
                0.01D);
    }

    private void tickMisfortune(ServerLevel level) {
        if (this.misfortuneTicks <= 0) {
            this.clearPrimaryMisfortune();
            return;
        }

        LivingEntity target = this.getMarkedTarget(level);

        if (target == null || !this.canApplyMisfortune(target)) {
            this.clearPrimaryMisfortune();
            return;
        }

        --this.misfortuneTicks;

        if (this.tickCount % 10 == 0) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.72D,
                    target.getZ(),
                    2 + this.misfortuneSeverity,
                    0.22D, 0.30D, 0.22D,
                    0.01D);
        }

        if (this.misfortuneTicks == 0) {
            this.clearPrimaryMisfortune();
        }
    }

    private void clearPrimaryMisfortune() {
        this.markedTargetId = -1;
        this.misfortuneSeverity = 0;
        this.misfortuneTicks = 0;
    }

    private boolean canApplyMisfortune(LivingEntity target) {
        return target != null
                && target.isAlive()
                && target != this
                && !this.isOmenFamilyMember(target)
                && !this.isAlliedTo(target);
    }

    private void applyMisfortuneEffects(
            LivingEntity target,
            int severity,
            int duration) {

        int weaknessAmplifier = severity >= 3 ? 1 : 0;
        int slownessAmplifier = severity >= 2 ? 1 : 0;
        int unluckAmplifier = Math.max(0, severity - 1);

        target.addEffect(
                new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        duration,
                        weaknessAmplifier),
                this);

        target.addEffect(
                new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        duration,
                        slownessAmplifier),
                this);

        target.addEffect(
                new MobEffectInstance(
                        MobEffects.UNLUCK,
                        duration,
                        unluckAmplifier),
                this);
    }

    // ---------------------------------------------------------------------
    // 3) Premonition
    // ---------------------------------------------------------------------

    public boolean isPremonitionActive() {
        return this.premonitionTicks > 0;
    }

    private boolean tryStartPremonition(
            ServerLevel level,
            LivingEntity threat) {

        if (!this.canUseActiveWolfismAbility()
                || this.isHarbingerActive()
                || this.premonitionTicks > 0
                || this.premonitionCooldownTicks > 0
                || threat == null
                || !threat.isAlive()) {
            return false;
        }

        int hostileCount = this.getOmenHostileCount();
        boolean raid = this.isOmenRaidActive();

        if (!raid
                && hostileCount < 2
                && !this.isThreatCommittedToFamily(threat)) {
            return false;
        }

        this.premonitionTicks = PREMONITION_DURATION_TICKS;
        this.abilityLockoutTicks = 20;

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                18,
                0.70D, 0.36D, 0.70D,
                0.03D);

        return true;
    }

    private void tickPremonition(
            ServerLevel level,
            LivingEntity threat) {

        if (this.premonitionTicks <= 0) {
            return;
        }

        --this.premonitionTicks;

        if (threat != null
                && threat.isAlive()
                && this.tickCount % 8 == 0) {
            this.warnNearbyFamily(threat);
        }

        if (this.tickCount % 12 == 0) {
            this.sendPredictionFieldParticles(
                    level,
                    PREMONITION_FAMILY_RADIUS,
                    false);
        }

        if (this.premonitionTicks == 0) {
            this.premonitionCooldownTicks =
                    PREMONITION_COOLDOWN_TICKS;
        }
    }

    // ---------------------------------------------------------------------
    // 4) Ominous Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(
            ServerLevel level,
            Entity entity) {

        LivingEntity livingTarget =
                entity instanceof LivingEntity living
                        ? living
                        : null;

        AttributeInstance attackDamage =
                this.getAttribute(Attributes.ATTACK_DAMAGE);

        boolean heavyBonusApplied =
                livingTarget != null
                        && !this.isBaby()
                        && !(livingTarget instanceof Creeper)
                        && !this.isOmenFamilyMember(livingTarget)
                        && this.isHeavyOmenTarget(livingTarget)
                        && attackDamage != null;

        if (heavyBonusApplied) {
            attackDamage.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            OMINOUS_HEAVY_BITE_MODIFIER,
                            HEAVY_TARGET_DAMAGE_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        boolean hurt;

        try {
            /*
             * The temporary modifier makes the ORIGINAL physical bite stronger,
             * rather than applying a second damage event that could fight
             * Minecraft's hurt-invulnerability timing.
             */
            hurt = super.doHurtTarget(level, entity);
        } finally {
            if (heavyBonusApplied) {
                attackDamage.removeModifier(
                        OMINOUS_HEAVY_BITE_MODIFIER);
            }
        }

        if (!hurt
                || this.isBaby()
                || livingTarget == null
                || livingTarget instanceof Creeper
                || this.isOmenFamilyMember(livingTarget)) {
            return hurt;
        }

        /*
         * Every successful Omen melee strike is the dedicated Ominous Bite:
         * physical damage first, then Misfortune applies/intensifies.
         */
        this.intensifyMisfortune(
                level,
                livingTarget);

        return true;
    }

    private boolean isHeavyOmenTarget(
            LivingEntity target) {

        return target.getMaxHealth()
                        >= HEAVY_TARGET_HEALTH_THRESHOLD
                || target.getAttributeValue(Attributes.ARMOR)
                        >= HEAVY_TARGET_ARMOR_THRESHOLD;
    }

    // ---------------------------------------------------------------------
    // 5) Ominous Presence
    // ---------------------------------------------------------------------

    private void tickOminousPresence(
            ServerLevel level,
            LivingEntity threat) {

        boolean raid = this.isOmenRaidActive();
        int hostiles = this.getOmenHostileCount();

        if (!raid && hostiles < 5) {
            return;
        }

        if (this.tickCount % PRESENCE_PARTICLE_INTERVAL == 0) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    this.getX(),
                    this.getY() + 0.50D,
                    this.getZ(),
                    raid ? 12 : 7,
                    0.80D, 0.30D, 0.80D,
                    0.025D);

            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    this.getX(),
                    this.getY() + 0.78D,
                    this.getZ(),
                    raid ? 9 : 5,
                    0.45D, 0.20D, 0.45D,
                    0.015D);
        }

        /*
         * Raids / large hostile activity make Omen much more decisive:
         * a danger she is already watching gets a base mark before the
         * battlefield fully collapses into melee.
         */
        if (threat != null
                && threat.isAlive()
                && !(threat instanceof Creeper)
                && this.getMisfortuneSeverityAgainst(threat) <= 0) {
            this.applyBaseMisfortune(level, threat);
        }
    }

    // ---------------------------------------------------------------------
    // 6) Bad Omen
    // ---------------------------------------------------------------------

    private boolean tryUseBadOmen(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.badOmenCooldownTicks > 0
                || this.getOmenHostileCount() < 4) {
            return false;
        }

        List<LivingEntity> victims = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(BAD_OMEN_RADIUS),
                this::isValidBadOmenVictim);

        if (victims.size() < 3) {
            return false;
        }

        for (LivingEntity target : victims) {
            this.applyAreaMisfortune(
                    level,
                    target,
                    1,
                    BAD_OMEN_EFFECT_DURATION_TICKS);
        }

        this.badOmenCooldownTicks = BAD_OMEN_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 20;

        level.sendParticles(
                ParticleTypes.WITCH,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                34,
                BAD_OMEN_RADIUS * 0.30D,
                0.35D,
                BAD_OMEN_RADIUS * 0.30D,
                0.04D);

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                18,
                BAD_OMEN_RADIUS * 0.22D,
                0.25D,
                BAD_OMEN_RADIUS * 0.22D,
                0.02D);

        return true;
    }

    private void applyAreaMisfortune(
            ServerLevel level,
            LivingEntity target,
            int severity,
            int duration) {

        if (!this.canApplyMisfortune(target)) {
            return;
        }

        long expiresAt = level.getGameTime() + duration;
        int bounded = clamp(
                severity,
                1,
                MAX_MISFORTUNE_SEVERITY);

        AreaMisfortune existing =
                this.areaMisfortune.get(target.getId());

        if (existing != null
                && existing.expiresAt() > level.getGameTime()) {
            bounded = Math.max(
                    bounded,
                    existing.severity());

            expiresAt = Math.max(
                    expiresAt,
                    existing.expiresAt());
        }

        this.areaMisfortune.put(
                target.getId(),
                new AreaMisfortune(
                        bounded,
                        expiresAt));

        this.applyMisfortuneEffects(
                target,
                bounded,
                duration);
    }

    private void tickAreaMisfortune(ServerLevel level) {
        long now = level.getGameTime();

        this.areaMisfortune.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt() <= now) {
                return true;
            }

            Entity entity = level.getEntity(entry.getKey());
            return !(entity instanceof LivingEntity living)
                    || !living.isAlive();
        });
    }

    // ---------------------------------------------------------------------
    // 7) Harbinger's Warning
    // ---------------------------------------------------------------------

    public boolean isHarbingerActive() {
        return this.harbingerTicks > 0;
    }

    private boolean tryStartHarbinger(
            ServerLevel level,
            LivingEntity threat) {

        if (!this.canUseActiveWolfismAbility()
                || this.harbingerTicks > 0
                || this.harbingerCooldownTicks > 0
                || threat == null
                || !threat.isAlive()) {
            return false;
        }

        boolean raid = this.isOmenRaidActive();
        int hostiles = this.getOmenHostileCount();

        boolean majorThreat =
                threat.getMaxHealth() >= 60.0F;

        boolean familyEmergency =
                this.hasFamilyDefenseEmergency()
                        && threat.getMaxHealth() >= 35.0F;

        boolean seriousBattle =
                raid
                        || hostiles >= 6
                        || majorThreat
                        || familyEmergency;

        if (!seriousBattle
                || (!raid && this.combatTicks < 40)) {
            return false;
        }

        this.harbingerTicks = HARBINGER_DURATION_TICKS;

        /*
         * Harbinger is the stronger predictive state. It supersedes an active
         * Premonition rather than stacking a second dodge system on top.
         */
        this.premonitionTicks = 0;
        this.premonitionCooldownTicks =
                Math.max(
                        this.premonitionCooldownTicks,
                        20 * 12);

        this.abilityLockoutTicks = 30;

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.70D,
                this.getZ(),
                34,
                1.10D, 0.55D, 1.10D,
                0.045D);

        level.sendParticles(
                ParticleTypes.WITCH,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                24,
                0.95D, 0.40D, 0.95D,
                0.04D);

        return true;
    }

    private void tickHarbinger(ServerLevel level) {
        if (this.harbingerTicks <= 0) {
            return;
        }

        --this.harbingerTicks;

        if (this.tickCount % HARBINGER_UPDATE_INTERVAL == 0) {
            List<LivingEntity> threats =
                    this.findHarbingerThreats(level);

            for (int i = 0;
                    i < Math.min(3, threats.size());
                    ++i) {

                this.applyAreaMisfortune(
                        level,
                        threats.get(i),
                        2,
                        40);
            }

            if (!threats.isEmpty()) {
                LivingEntity priority = threats.getFirst();

                this.warnNearbyFamily(priority);
                this.prepareFamilyForPredictedThreat(priority);

                if (!(priority instanceof Creeper)
                        && this.getMisfortuneSeverityAgainst(priority) < 2) {
                    this.setPrimaryMisfortune(
                            level,
                            priority,
                            2);
                }
            }
        }

        if (this.tickCount % 8 == 0) {
            this.sendPredictionFieldParticles(
                    level,
                    HARBINGER_FAMILY_RADIUS,
                    true);
        }

        if (this.harbingerTicks == 0) {
            this.harbingerCooldownTicks =
                    HARBINGER_COOLDOWN_TICKS;
        }
    }

    private List<LivingEntity> findHarbingerThreats(
            ServerLevel level) {

        List<LivingEntity> threats =
                new ArrayList<>(
                        level.getEntitiesOfClass(
                                LivingEntity.class,
                                this.getBoundingBox().inflate(
                                        HARBINGER_FAMILY_RADIUS),
                                this::isPotentialOmenThreat));

        threats.sort(
                Comparator.comparingDouble(
                        this::harbingerPriorityScore)
                        .reversed());

        return threats;
    }

    private double harbingerPriorityScore(
            LivingEntity target) {

        double score = target.getMaxHealth();

        if (target == this.getFamilyDefenseTarget()) {
            score += 160.0D;
        }

        if (this.isThreatCommittedToFamily(target)) {
            score += 120.0D;
        }

        if (target instanceof Raider) {
            score += 50.0D;
        }

        score -= Math.sqrt(this.distanceToSqr(target)) * 1.5D;

        return score;
    }

    private void prepareFamilyForPredictedThreat(
            LivingEntity threat) {

        if (!this.isTame()
                || threat == null
                || !threat.isAlive()) {
            return;
        }

        for (Wolf family : this.level().getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(
                        HARBINGER_FAMILY_RADIUS),
                wolf -> wolf.isAlive()
                        && wolf.isTame())) {

            family.getLookControl().setLookAt(
                    threat,
                    55.0F,
                    40.0F);

            if (family == this
                    || family.isBaby()
                    || family.isOrderedToSit()
                    || threat instanceof Creeper
                    || family.getTarget() != null
                    || !family.canAttack(threat)) {
                continue;
            }

            /*
             * This is the ultimate's behavioral payoff: non-sitting tamed
             * family can begin reacting to a clearly hostile predicted threat
             * before waiting for the first family member to take damage.
             */
            family.setTarget(threat);
        }
    }

    private void sendPredictionFieldParticles(
            ServerLevel level,
            double radius,
            boolean harbinger) {

        level.sendParticles(
                harbinger
                        ? ParticleTypes.WITCH
                        : ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.48D,
                this.getZ(),
                harbinger ? 8 : 5,
                radius * 0.22D,
                0.22D,
                radius * 0.22D,
                0.015D);
    }

    // ---------------------------------------------------------------------
    // Shared prediction / event hooks
    // ---------------------------------------------------------------------

    /**
     * 0 = no prediction, 1 = Premonition, 2 = Harbinger's Warning.
     */
    public int getPredictionTierFor(
            LivingEntity familyMember) {

        if (!this.isTame()
                || this.isBaby()
                || !this.isAlive()
                || !this.isOmenFamilyMember(familyMember)) {
            return 0;
        }

        double distanceSqr =
                this.distanceToSqr(familyMember);

        if (this.isHarbingerActive()
                && distanceSqr
                        <= HARBINGER_FAMILY_RADIUS
                        * HARBINGER_FAMILY_RADIUS) {
            return 2;
        }

        if (this.isPremonitionActive()
                && distanceSqr
                        <= PREMONITION_FAMILY_RADIUS
                        * PREMONITION_FAMILY_RADIUS) {
            return 1;
        }

        return 0;
    }

    public boolean isOmenFamilyMember(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {
            return entity instanceof OmenWolf omen
                    && this.isOmenPackmate(omen);
        }

        if (entity == this.getOwner()) {
            return true;
        }

        return entity instanceof Wolf wolf
                && wolf.isTame();
    }

    public boolean isPotentialOmenThreat(
            LivingEntity candidate) {

        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || this.isOmenFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate instanceof Raider
                || candidate == this.getFamilyDefenseTarget()
                || candidate == this.getTarget();
    }

    public boolean isValidOmenCombatTarget(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.canAttack(target)
                || this.isOmenFamilyMember(target)
                || this.isAlliedTo(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    private boolean isValidBadOmenVictim(
            LivingEntity candidate) {

        return candidate != null
                && candidate.isAlive()
                && !(candidate instanceof Creeper)
                && this.isPotentialOmenThreat(candidate);
    }

    public boolean isThreatCommittedToFamily(
            LivingEntity threat) {

        if (threat == null || !threat.isAlive()) {
            return false;
        }

        if (threat == this.getFamilyDefenseTarget()) {
            return true;
        }

        if (threat instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null
                    && this.isOmenFamilyMember(target);
        }

        return false;
    }

    // ---------------------------------------------------------------------
    // Pack AI
    // ---------------------------------------------------------------------

    public boolean isOmenPackmate(OmenWolf other) {
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

    public boolean isValidOmenPackThreat(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target == this
                || target instanceof Creeper
                || this.isAlliedTo(target)
                || this.isOmenFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || threat instanceof Creeper
                || !this.isValidOmenPackThreat(threat)) {
            return;
        }

        for (OmenWolf mate : level.getEntitiesOfClass(
                OmenWolf.class,
                this.getBoundingBox().inflate(
                        OmenWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive()
                        && (wolf == this
                        || this.isOmenPackmate(wolf)))) {

            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.OMEN_PACK_THREAT.get(),
                    threat);

            if (!mate.isBaby()
                    && !mate.isOrderedToSit()
                    && mate.isValidOmenCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {

        if (attacker == null
                || !attacker.isAlive()) {
            return;
        }

        if (!(attacker instanceof Creeper)
                && this.level() instanceof ServerLevel level) {
            this.intensifyMisfortune(level, attacker);
            this.alertPackToThreat(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Threat-sensor memory helpers
    // ---------------------------------------------------------------------

    public int getOmenHostileCount() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.OMEN_HOSTILE_COUNT.get())
                .orElse(0);
    }

    public boolean isOmenRaidActive() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.OMEN_RAID_ACTIVE.get())
                .orElse(false);
    }

    // ---------------------------------------------------------------------
    // Natural spawn
    // ---------------------------------------------------------------------

    public static boolean checkOmenWolfSpawnRules(
            EntityType<OmenWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (!level.getBlockState(pos.below())
                .is(BlockTags.WOLVES_SPAWNABLE_ON)
                || !isBrightEnoughToSpawn(level, pos)) {
            return false;
        }

        if (reason == EntitySpawnReason.NATURAL
                && level instanceof ServerLevel serverLevel) {

            int nearby = serverLevel.getEntitiesOfClass(
                    OmenWolf.class,
                    new AABB(pos).inflate(
                            48.0D,
                            16.0D,
                            48.0D),
                    wolf -> wolf.isAlive()
                            && !wolf.isTame())
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

        output.putInt(
                "OmenPremonitionTicks",
                this.premonitionTicks);

        output.putInt(
                "OmenPremonitionCooldown",
                this.premonitionCooldownTicks);

        output.putInt(
                "OmenBadOmenCooldown",
                this.badOmenCooldownTicks);

        output.putInt(
                "OmenHarbingerTicks",
                this.harbingerTicks);

        output.putInt(
                "OmenHarbingerCooldown",
                this.harbingerCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        this.premonitionTicks = clamp(
                input.getIntOr(
                        "OmenPremonitionTicks",
                        0),
                0,
                PREMONITION_DURATION_TICKS);

        this.premonitionCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "OmenPremonitionCooldown",
                                0));

        this.badOmenCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "OmenBadOmenCooldown",
                                0));

        this.harbingerTicks = clamp(
                input.getIntOr(
                        "OmenHarbingerTicks",
                        0),
                0,
                HARBINGER_DURATION_TICKS);

        this.harbingerCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "OmenHarbingerCooldown",
                                0));

        // Entity IDs are runtime-only. Misfortune safely rebuilds after load.
        this.clearPrimaryMisfortune();
        this.areaMisfortune.clear();

        if (this.isBaby()) {
            this.clearAdultOmenState();
        }
    }

    private static int clamp( int value, int min, int max) {
        return Math.max(
                min,
                Math.min(max, value));
    }
}
