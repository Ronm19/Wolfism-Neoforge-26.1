package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
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
 * Ancient Wolf ♂
 *
 * Ancient / Survival / Adaptive / Defensive.
 *
 * Primordial survives through original instinct.
 * Ancient survives through accumulated experience.
 *
 * Core kit:
 * - Ancient Instinct
 * - Ancestral Bite
 * - Survival Adaptation
 * - Ancestral Guidance
 * - Ancient Endurance
 * - Ancestral Howl
 * - Echoes of Ancestors
 */
public final class AncientWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Baseline / Ancient Endurance
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 52.0D;

    private static final int DECISION_INTERVAL = 6;

    // =====================================================================
    // Ancestral Bite
    // =====================================================================

    private static final float ANCESTRAL_BITE_MIN_BONUS = 0.50F;
    private static final float ANCESTRAL_BITE_MAX_BONUS = 4.00F;

    // =====================================================================
    // Survival Adaptation
    // =====================================================================

    private static final int ADAPTATION_ACTIVATION_SCORE = 3;
    private static final int ADAPTATION_MAX_SCORE = 6;

    private static final int ADAPTATION_DURATION_TICKS = 20 * 20;
    private static final int EXPOSURE_DECAY_INTERVAL = 20 * 4;

    private static final float PHYSICAL_MULTIPLIER = 0.75F;
    private static final float PROJECTILE_MULTIPLIER = 0.65F;
    private static final float FIRE_MULTIPLIER = 0.55F;
    private static final float EXPLOSION_MULTIPLIER = 0.60F;
    private static final float MAGIC_MULTIPLIER = 0.70F;

    // =====================================================================
    // Ancestral Guidance
    // =====================================================================

    private static final double GUIDANCE_RADIUS = 12.0D;
    private static final double HAZARD_ESCAPE_DISTANCE = 3.25D;

    // =====================================================================
    // Ancestral Howl
    // =====================================================================

    public static final int ANCESTRAL_HOWL_DURATION_TICKS = 20 * 18;
    public static final int ANCESTRAL_HOWL_COOLDOWN_TICKS = 20 * 35;

    private static final double ANCESTRAL_HOWL_RADIUS = 12.0D;
    private static final double HOWL_KNOCKBACK_BONUS = 0.25D;

    private static final Identifier HOWL_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "ancient_wolf_howl_knockback");

    // =====================================================================
    // Echoes of Ancestors
    // =====================================================================

    public static final int ECHOES_DURATION_TICKS = 20 * 14;
    public static final int ECHOES_COOLDOWN_TICKS = 20 * 70;

    private static final double ECHOES_RADIUS = 18.0D;
    private static final double ECHOES_KNOCKBACK_BONUS = 0.40D;

    private static final Identifier ECHOES_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "ancient_wolf_echoes_knockback");

    // =====================================================================
    // Runtime
    // =====================================================================

    private final EnumMap<ThreatType, Integer> exposure =
            new EnumMap<>(ThreatType.class);

    private ThreatType activeAdaptation;

    private int adaptationTicks;
    private int exposureDecayTicks;

    private int ancestralHowlCooldownTicks;
    private int ancestralHowlTicks;

    private int echoesCooldownTicks;
    private int echoesTicks;

    private final Map<UUID, LivingEntity> howlRecipients =
            new HashMap<>();

    private final Map<UUID, LivingEntity> echoesRecipients =
            new HashMap<>();

    // =====================================================================
    // Brain
    // =====================================================================

    private static final class BrainHolder {

        private static final Brain.Provider<AncientWolf> PROVIDER =
                Brain.<AncientWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.ANCIENT_TACTICAL.get()),
                        wolf -> List.of());
    }

    public AncientWolf(
            EntityType<? extends AncientWolf> type,
            Level level) {

        super(type, level);

        for (ThreatType typeValue : ThreatType.values()) {
            exposure.put(typeValue, 0);
        }
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf>
    wolfismEntityType() {

        return ModEntities.ANCIENT_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {

        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.50D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {

        AttributeInstance maxHealth =
                this.getAttribute(
                        Attributes.MAX_HEALTH);

        if (maxHealth == null) {
            return;
        }

        float previousHealth =
                this.getHealth();

        maxHealth.setBaseValue(
                BASE_MAX_HEALTH);

        if (this.isTame()) {

            this.setHealth(
                    this.getMaxHealth());

        } else {

            this.setHealth(
                    Math.min(
                            previousHealth,
                            this.getMaxHealth()));
        }
    }

    // =====================================================================
    // Brain plumbing
    // =====================================================================

    @Override
    protected Brain<AncientWolf> makeBrain(
            Brain.Packed packedBrain) {

        return BrainHolder.PROVIDER.makeBrain(
                this,
                packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<AncientWolf> getBrain() {

        return (Brain<AncientWolf>)
                super.getBrain();
    }

    @Override
    protected void customServerAiStep(
            ServerLevel level) {

        this.getBrain().tick(
                level,
                this);

        super.customServerAiStep(level);
    }

    // =====================================================================
    // Main tick
    // =====================================================================

    @Override
    public void tick() {

        super.tick();

        if (!(this.level()
                instanceof ServerLevel level)) {

            return;
        }

        tickCooldowns();
        tickSurvivalAdaptation();

        /*
         * Pups may never use Ancient's adult kit.
         */
        if (this.isBaby()) {

            clearAdultAncientStates();

            this.setTarget(null);

            return;
        }

        /*
         * Keep physical sitting state synchronized with the owner's command.
         *
         * Most importantly:
         *
         * ORDERED TO SIT = NO MOVEMENT.
         */
        if (this.isOrderedToSit()) {

            enforceAncientSitCommand();

        } else if (this.isInSittingPose()) {

            /*
             * Prevent stale "walking while sitting" state after the owner
             * commands Ancient to stand.
             */
            this.setInSittingPose(false);
        }

        /*
         * Existing defensive states may continue their durations while sitting.
         *
         * Buff sharing is allowed.
         * Forced movement is NOT.
         */
        tickAncestralHowl(level);
        tickEchoesOfAncestors(level);

        /*
         * Absolutely no Guidance, Endurance repositioning, combat decisions,
         * interception, following, or other navigation after a sit command.
         */
        if (isAncientSitting()) {
            return;
        }

        if (this.isWolfismWorkTick(10)) {

            tickAncestralGuidance(level);
            tickAncientEndurance(level);
        }

        if (this.tickCount
                % DECISION_INTERVAL == 0) {

            tickAncientDecisionBrain(level);
        }
    }

    private void tickCooldowns() {

        if (ancestralHowlCooldownTicks > 0) {
            --ancestralHowlCooldownTicks;
        }

        if (echoesCooldownTicks > 0) {
            --echoesCooldownTicks;
        }
    }

    // =====================================================================
    // Sitting discipline
    // =====================================================================

    private boolean isAncientSitting() {

        return this.isOrderedToSit()
                || this.isInSittingPose();
    }

    /**
     * Owner sit commands are authoritative.
     *
     * Ancient may still:
     * - receive buffs
     * - maintain adaptation
     * - share already-active defensive aura effects
     *
     * Ancient may NOT:
     * - follow
     * - navigate
     * - pursue
     * - intercept
     * - regroup
     * - acquire a target
     */
    private void enforceAncientSitCommand() {

        this.getNavigation().stop();

        if (this.getTarget() != null) {
            this.setTarget(null);
        }

        this.setSprinting(false);

        /*
         * Do not leave him visually sitting in mid-air after knockback.
         */
        this.setInSittingPose(
                this.onGround());

        /*
         * Kill residual horizontal movement.
         *
         * Preserve vertical motion so gravity still works normally.
         */
        Vec3 motion =
                this.getDeltaMovement();

        if (Math.abs(motion.x) > 1.0E-4D
                || Math.abs(motion.z) > 1.0E-4D) {

            this.setDeltaMovement(
                    0.0D,
                    motion.y,
                    0.0D);

            this.hurtMarked = true;
        }
    }

    /**
     * A sitting family wolf is still family and still receives buffs.
     *
     * It is simply NOT a legal target for Ancient's navigation/reposition
     * systems.
     */
    private boolean mayRepositionFamilyMember(
            LivingEntity family) {

        if (!(family instanceof Mob)) {
            return false;
        }

        if (family instanceof Wolf wolf) {

            if (wolf.isOrderedToSit()
                    || wolf.isInSittingPose()) {

                wolf.getNavigation().stop();

                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Main decision brain
    // =====================================================================

    private void tickAncientDecisionBrain(
            ServerLevel level) {

        if (isAncientSitting()) {

            this.getNavigation().stop();
            this.setTarget(null);

            return;
        }

        LivingEntity primary =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ANCIENT_PRIMARY_THREAT
                                        .get())
                        .orElse(null);

        LivingEntity vulnerable =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ANCIENT_VULNERABLE_FAMILY
                                        .get())
                        .orElse(null);

        int hostileCount =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ANCIENT_HOSTILE_COUNT
                                        .get())
                        .orElse(0);

        /*
         * Echoes is Ancient's true emergency defense state.
         */
        if (this.canUseActiveWolfismAbility()
                && echoesTicks <= 0
                && echoesCooldownTicks <= 0
                && shouldStartEchoes(
                level,
                vulnerable,
                hostileCount)) {

            startEchoesOfAncestors(level);

            return;
        }

        /*
         * Lesser sustained pressure uses Ancestral Howl.
         */
        if (this.canUseActiveWolfismAbility()
                && ancestralHowlTicks <= 0
                && echoesTicks <= 0
                && ancestralHowlCooldownTicks <= 0
                && shouldStartAncestralHowl(
                vulnerable,
                hostileCount)) {

            startAncestralHowl(level);

            return;
        }

        /*
         * Awareness does not become unlimited physical aggro.
         */
        if (primary != null
                && this.isWithinWolfismPhysicalAggroAcquireRange(
                primary)) {

            this.setTarget(primary);
        }
    }

    // =====================================================================
    // Ancient Instinct
    // =====================================================================

    public double ancientThreatScore(
            LivingEntity target) {

        if (target == null) {
            return Double.MAX_VALUE;
        }

        /*
         * Lower score = higher priority.
         */
        double score =
                Math.sqrt(
                        this.distanceToSqr(
                                target));

        if (target
                == this.getFamilyDefenseTarget()) {

            score -= 400.0D;
        }

        if (isThreateningAncientFamily(
                target)) {

            score -= 220.0D;
        }

        score -=
                Math.min(
                        32.0D,
                        target.getMaxHealth()
                                * 0.12D);

        score -=
                Math.min(
                        18.0D,
                        target.getArmorValue()
                                * 0.90D);

        if (target instanceof WitherBoss
                || target instanceof EnderDragon) {

            score -= 60.0D;
        }

        return score;
    }

    public boolean isThreateningAncientFamily(
            LivingEntity target) {

        if (!(target instanceof Mob mob)) {
            return false;
        }

        LivingEntity victim =
                mob.getTarget();

        return victim != null
                && isAncientFamilyMember(
                victim);
    }

    // =====================================================================
    // Ancestral Bite
    // =====================================================================

    @Override
    public boolean doHurtTarget(
            ServerLevel level,
            Entity target) {

        if (isAncientSitting()) {
            return false;
        }

        boolean hit =
                super.doHurtTarget(
                        level,
                        target);

        if (!hit
                || this.isBaby()
                || !(target
                instanceof LivingEntity living)
                || !isValidAncientThreat(
                living)) {

            return hit;
        }

        float threatBonus =
                ANCESTRAL_BITE_MIN_BONUS;

        /*
         * High-health enemies give Ancient slightly more opportunity to apply
         * his accumulated knowledge.
         */
        threatBonus +=
                Math.min(
                        2.75F,
                        living.getMaxHealth()
                                / 80.0F);

        /*
         * Armored opponents also receive a modest veteran bonus.
         */
        threatBonus +=
                Math.min(
                        1.25F,
                        living.getArmorValue()
                                * 0.07F);

        if (living instanceof WitherBoss
                || living instanceof EnderDragon) {

            threatBonus += 0.75F;
        }

        threatBonus =
                Math.min(
                        ANCESTRAL_BITE_MAX_BONUS,
                        threatBonus);

        living.hurtServer(
                level,
                this.damageSources()
                        .mobAttack(this),
                threatBonus);

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.CRIT,
                living.getX(),
                living.getY()
                        + living.getBbHeight()
                        * 0.45D,
                living.getZ(),
                5,
                0.18D,
                0.18D,
                0.18D,
                0.035D);

        return true;
    }

    // =====================================================================
    // Survival Adaptation
    // =====================================================================

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float amount) {

        ThreatType incoming =
                classifyThreat(source);

        registerThreatExposure(
                level,
                incoming);

        float adjusted =
                amount;

        if (activeAdaptation
                == incoming
                && adaptationTicks > 0) {

            adjusted *=
                    adaptationMultiplier(
                            incoming);
        }

        return super.hurtServer(
                level,
                source,
                adjusted);
    }

    private ThreatType classifyThreat(
            DamageSource source) {

        if (source.is(
                DamageTypeTags.IS_PROJECTILE)) {

            return ThreatType.PROJECTILE;
        }

        if (source.is(
                DamageTypeTags.IS_FIRE)) {

            return ThreatType.FIRE;
        }

        if (source.is(
                DamageTypeTags.IS_EXPLOSION)) {

            return ThreatType.EXPLOSION;
        }

        /*
         * Special / armor-bypassing damage is Ancient's broad MAGIC bucket.
         */
        if (source.is(
                DamageTypeTags.BYPASSES_ARMOR)) {

            return ThreatType.MAGIC;
        }

        return ThreatType.PHYSICAL;
    }

    private void registerThreatExposure(
            ServerLevel level,
            ThreatType type) {

        /*
         * Echoes makes Ancient process new survival information faster.
         */
        int gain =
                echoesTicks > 0
                        ? 2
                        : 1;

        int newScore =
                Math.min(
                        ADAPTATION_MAX_SCORE,
                        exposure.getOrDefault(
                                type,
                                0)
                                + gain);

        exposure.put(
                type,
                newScore);

        if (activeAdaptation
                == type) {

            adaptationTicks =
                    ADAPTATION_DURATION_TICKS;

            return;
        }

        if (newScore
                < ADAPTATION_ACTIVATION_SCORE) {

            return;
        }

        int activeScore =
                activeAdaptation == null
                        ? 0
                        : exposure.getOrDefault(
                        activeAdaptation,
                        0);

        /*
         * One major adaptation at a time.
         *
         * A new threat replaces the old adaptation only when it has genuinely
         * become dominant.
         */
        if (activeAdaptation == null
                || echoesTicks > 0
                || newScore > activeScore) {

            activateAdaptation(
                    level,
                    type);
        }
    }

    private void activateAdaptation(
            ServerLevel level,
            ThreatType type) {

        activeAdaptation =
                type;

        adaptationTicks =
                ADAPTATION_DURATION_TICKS;

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                16,
                0.45D,
                0.35D,
                0.45D,
                0.035D);
    }

    private void tickSurvivalAdaptation() {

        if (adaptationTicks > 0) {

            --adaptationTicks;

            if (adaptationTicks <= 0) {
                activeAdaptation = null;
            }
        }

        ++exposureDecayTicks;

        if (exposureDecayTicks
                < EXPOSURE_DECAY_INTERVAL) {

            return;
        }

        exposureDecayTicks = 0;

        for (ThreatType type
                : ThreatType.values()) {

            int old =
                    exposure.getOrDefault(
                            type,
                            0);

            if (old > 0) {

                exposure.put(
                        type,
                        old - 1);
            }
        }
    }

    private float adaptationMultiplier(
            ThreatType type) {

        return switch (type) {

            case PHYSICAL ->
                    PHYSICAL_MULTIPLIER;

            case PROJECTILE ->
                    PROJECTILE_MULTIPLIER;

            case FIRE ->
                    FIRE_MULTIPLIER;

            case EXPLOSION ->
                    EXPLOSION_MULTIPLIER;

            case MAGIC ->
                    MAGIC_MULTIPLIER;
        };
    }

    public String getActiveAdaptationName() {

        return activeAdaptation == null
                ? "NONE"
                : activeAdaptation.name();
    }

    // =====================================================================
    // Ancestral Guidance
    // =====================================================================

    private void tickAncestralGuidance(
            ServerLevel level) {

        if (!this.isTame()
                || isAncientSitting()) {

            return;
        }

        BlockPos hazard =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ANCIENT_HAZARD_POS
                                        .get())
                        .orElse(null);

        if (hazard != null) {

            Vec3 hazardCenter =
                    Vec3.atCenterOf(
                            hazard);

            /*
             * Ancient himself avoids an obvious environmental hazard,
             * unless he is currently handling an immediate family emergency.
             */
            if (this.position()
                    .distanceToSqr(
                            hazardCenter)
                    <= HAZARD_ESCAPE_DISTANCE
                    * HAZARD_ESCAPE_DISTANCE
                    && this.getFamilyDefenseTarget()
                    == null) {

                moveMobAwayFrom(
                        this,
                        hazardCenter,
                        4.0D,
                        1.25D);
            }

            /*
             * Share the warning with nearby family.
             *
             * CRITICAL:
             * Sitting wolves receive knowledge/buffs but are NEVER navigated.
             */
            for (LivingEntity family
                    : getNearbyAncientFamily(
                    level,
                    GUIDANCE_RADIUS)) {

                if (family == this
                        || !mayRepositionFamilyMember(
                        family)) {

                    continue;
                }

                Mob mob =
                        (Mob) family;

                if (family.position()
                        .distanceToSqr(
                                hazardCenter)
                        <= HAZARD_ESCAPE_DISTANCE
                        * HAZARD_ESCAPE_DISTANCE) {

                    moveMobAwayFrom(
                            mob,
                            hazardCenter,
                            3.5D,
                            1.20D);
                }
            }
        }

        /*
         * During Howl / Echoes, severely injured family members may regroup.
         *
         * Again:
         * SIT overrides this completely.
         */
        if (ancestralHowlTicks > 0
                || echoesTicks > 0) {

            double radius =
                    echoesTicks > 0
                            ? ECHOES_RADIUS
                            : ANCESTRAL_HOWL_RADIUS;

            for (LivingEntity family
                    : getNearbyAncientFamily(
                    level,
                    radius)) {

                if (family == this
                        || !mayRepositionFamilyMember(
                        family)) {

                    continue;
                }

                Mob mob =
                        (Mob) family;

                float healthFraction =
                        family.getHealth()
                                / Math.max(
                                1.0F,
                                family.getMaxHealth());

                if (healthFraction > 0.35F) {
                    continue;
                }

                mob.setTarget(null);

                mob.getNavigation()
                        .moveTo(
                                this.getX(),
                                this.getY(),
                                this.getZ(),
                                1.25D);
            }
        }
    }

    private static void moveMobAwayFrom(
            Mob mob,
            Vec3 danger,
            double distance,
            double speed) {

        /*
         * Final defensive check in case some caller accidentally attempts to
         * move a sitting wolf in the future.
         */
        if (mob instanceof Wolf wolf
                && (wolf.isOrderedToSit()
                || wolf.isInSittingPose())) {

            wolf.getNavigation().stop();

            return;
        }

        Vec3 away =
                mob.position()
                        .subtract(
                                danger);

        away =
                new Vec3(
                        away.x,
                        0.0D,
                        away.z);

        if (away.lengthSqr()
                < 1.0E-4D) {

            away =
                    new Vec3(
                            1.0D,
                            0.0D,
                            0.0D);
        }

        Vec3 destination =
                mob.position()
                        .add(
                                away.normalize()
                                        .scale(
                                                distance));

        mob.getNavigation()
                .moveTo(
                        destination.x,
                        destination.y,
                        destination.z,
                        speed);
    }

    // =====================================================================
    // Ancient Endurance
    // =====================================================================

    private void tickAncientEndurance(
            ServerLevel level) {

        if (isAncientSitting()) {

            this.getNavigation().stop();

            return;
        }

        float healthFraction =
                this.getHealth()
                        / Math.max(
                        1.0F,
                        this.getMaxHealth());

        if (healthFraction > 0.30F
                || echoesTicks > 0) {

            return;
        }

        LivingEntity current =
                this.getTarget();

        /*
         * Ancient does NOT abandon an enemy currently attacking family.
         */
        if (current != null
                && isThreateningAncientFamily(
                current)) {

            return;
        }

        /*
         * Otherwise, a badly wounded veteran knows when to regroup.
         */
        this.setTarget(null);

        if (this.isTame()
                && this.getOwner() != null) {

            LivingEntity owner =
                    this.getOwner();

            if (this.distanceToSqr(
                    owner)
                    > 4.0D * 4.0D) {

                this.getNavigation()
                        .moveTo(
                                owner.getX(),
                                owner.getY(),
                                owner.getZ(),
                                1.25D);
            }
        }
    }

    @Override
    public boolean causeFallDamage(
            double fallDistance,
            float damageMultiplier,
            DamageSource damageSource) {

        /*
         * Ancient Endurance reduces environmental attrition,
         * but Ancient is NOT fall-immune.
         */
        return super.causeFallDamage(
                fallDistance,
                damageMultiplier * 0.70F,
                damageSource);
    }

    // =====================================================================
    // Ancestral Howl
    // =====================================================================

    private boolean shouldStartAncestralHowl(
            LivingEntity vulnerable,
            int hostileCount) {

        if (!this.isTame()
                || isAncientSitting()) {

            return false;
        }

        if (vulnerable != null) {
            return true;
        }

        if (hostileCount >= 4) {
            return true;
        }

        return activeAdaptation != null
                && hostileCount >= 3;
    }

    private boolean startAncestralHowl(
            ServerLevel level) {

        if (!this.isTame()
                || this.isBaby()
                || isAncientSitting()
                || ancestralHowlTicks > 0
                || ancestralHowlCooldownTicks > 0
                || echoesTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        ancestralHowlTicks =
                ANCESTRAL_HOWL_DURATION_TICKS;

        ancestralHowlCooldownTicks =
                ANCESTRAL_HOWL_COOLDOWN_TICKS;

        applyHowlFamilyEffects(level);
        this.playSound(this.getWolfismHowlSound(), 1.5F, 1.0F);

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.POOF,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                36,
                1.10D,
                0.45D,
                1.10D,
                0.055D);

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.70D,
                this.getZ(),
                28,
                1.20D,
                0.55D,
                1.20D,
                0.04D);

        return true;
    }

    private void tickAncestralHowl(
            ServerLevel level) {

        if (ancestralHowlTicks <= 0) {
            return;
        }

        --ancestralHowlTicks;

        /*
         * Existing Howl may keep sharing defensive knowledge while Ancient
         * sits, but never forces anyone to move.
         */
        if (ancestralHowlTicks % 10 == 0) {

            applyHowlFamilyEffects(level);
        }

        if (ancestralHowlTicks <= 0) {

            clearKnockbackRecipients(
                    howlRecipients,
                    HOWL_KNOCKBACK_MODIFIER);
        }
    }

    private void applyHowlFamilyEffects(
            ServerLevel level) {

        for (LivingEntity family
                : getNearbyAncientFamily(
                level,
                ANCESTRAL_HOWL_RADIUS)) {

            int resistanceAmplifier =
                    activeAdaptation
                            == ThreatType.EXPLOSION
                            || activeAdaptation
                            == ThreatType.MAGIC
                            ? 1
                            : 0;

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            30,
                            resistanceAmplifier,
                            true,
                            true));

            if (activeAdaptation
                    == ThreatType.FIRE) {

                family.addEffect(
                        new MobEffectInstance(
                                MobEffects.FIRE_RESISTANCE,
                                30,
                                0,
                                true,
                                true));
            }

            if (activeAdaptation
                    == ThreatType.PROJECTILE) {

                family.addEffect(
                        new MobEffectInstance(
                                MobEffects.SPEED,
                                30,
                                0,
                                true,
                                true));
            }

            double kbBonus =
                    HOWL_KNOCKBACK_BONUS
                            + (activeAdaptation
                            == ThreatType.PHYSICAL
                            ? 0.10D
                            : 0.0D);

            applyKnockbackBonus(
                    family,
                    howlRecipients,
                    HOWL_KNOCKBACK_MODIFIER,
                    kbBonus);
        }
    }

    // =====================================================================
    // Echoes of Ancestors
    // =====================================================================

    private boolean shouldStartEchoes(
            ServerLevel level,
            LivingEntity vulnerable,
            int hostileCount) {

        if (!this.isTame()
                || isAncientSitting()) {

            return false;
        }

        int criticalFamily =
                countCriticalFamily(
                        level,
                        ECHOES_RADIUS,
                        0.42F);

        if (criticalFamily >= 2) {
            return true;
        }

        if (vulnerable != null
                && hostileCount >= 4) {

            return true;
        }

        return hostileCount >= 7;
    }

    private boolean startEchoesOfAncestors(
            ServerLevel level) {

        if (!this.isTame()
                || this.isBaby()
                || isAncientSitting()
                || echoesTicks > 0
                || echoesCooldownTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        /*
         * Echoes supersedes the lesser Howl.
         */
        if (ancestralHowlTicks > 0) {

            ancestralHowlTicks = 0;

            clearKnockbackRecipients(
                    howlRecipients,
                    HOWL_KNOCKBACK_MODIFIER);
        }

        echoesTicks =
                ECHOES_DURATION_TICKS;

        echoesCooldownTicks =
                ECHOES_COOLDOWN_TICKS;

        applyEchoesFamilyEffects(level);

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                72,
                2.0D,
                0.85D,
                2.0D,
                0.075D);

        WolfVfx.sendParticles("ancient_wolf", level,
                ParticleTypes.POOF,
                this.getX(),
                this.getY() + 0.40D,
                this.getZ(),
                32,
                1.50D,
                0.45D,
                1.50D,
                0.045D);

        return true;
    }

    private void tickEchoesOfAncestors(
            ServerLevel level) {

        if (echoesTicks <= 0) {
            return;
        }

        --echoesTicks;

        if (echoesTicks % 10 == 0) {

            applyEchoesFamilyEffects(level);
        }

        /*
         * Interception is physical movement.
         *
         * Therefore:
         * sitting Ancient = NO interception.
         */
        if (echoesTicks % 5 == 0
                && !isAncientSitting()) {

            tickEchoesInterception(level);
        }

        if (echoesTicks % 20 == 0) {

            WolfVfx.sendParticles("ancient_wolf", level,
                    ParticleTypes.ENCHANTED_HIT,
                    this.getX(),
                    this.getY() + 0.55D,
                    this.getZ(),
                    12,
                    0.70D,
                    0.40D,
                    0.70D,
                    0.025D);
        }

        if (echoesTicks <= 0) {

            clearKnockbackRecipients(
                    echoesRecipients,
                    ECHOES_KNOCKBACK_MODIFIER);
        }
    }

    private void applyEchoesFamilyEffects(
            ServerLevel level) {

        for (LivingEntity family
                : getNearbyAncientFamily(
                level,
                ECHOES_RADIUS)) {

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            30,
                            1,
                            true,
                            true));

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.SPEED,
                            30,
                            0,
                            true,
                            true));

            if (activeAdaptation
                    == ThreatType.FIRE) {

                family.addEffect(
                        new MobEffectInstance(
                                MobEffects.FIRE_RESISTANCE,
                                30,
                                0,
                                true,
                                true));
            }

            applyKnockbackBonus(
                    family,
                    echoesRecipients,
                    ECHOES_KNOCKBACK_MODIFIER,
                    ECHOES_KNOCKBACK_BONUS);
        }
    }

    /**
     * Echoes is accumulated battlefield experience, not supernatural
     * future-sight.
     *
     * Ancient recognizes a developing family emergency and physically moves
     * to intercept it.
     */
    private void tickEchoesInterception(
            ServerLevel level) {

        if (isAncientSitting()) {

            this.getNavigation().stop();
            this.setTarget(null);

            return;
        }

        LivingEntity vulnerable =
                findMostVulnerableFamily(
                        level,
                        ECHOES_RADIUS);

        if (vulnerable == null) {
            return;
        }

        LivingEntity interceptThreat =
                getNearbyAncientThreats(
                        level,
                        ECHOES_RADIUS)
                        .stream()
                        .filter(
                                threat ->
                                        threat
                                                instanceof Mob mob
                                                && mob.getTarget()
                                                == vulnerable)
                        .min(
                                Comparator.comparingDouble(
                                        threat ->
                                                threat.distanceToSqr(
                                                        vulnerable)))
                        .orElse(null);

        if (interceptThreat != null) {

            this.getNavigation()
                    .moveTo(
                            interceptThreat.getX(),
                            interceptThreat.getY(),
                            interceptThreat.getZ(),
                            1.45D);

            if (this
                    .isWithinWolfismPhysicalAggroAcquireRange(
                            interceptThreat)) {

                this.setTarget(
                        interceptThreat);
            }

            return;
        }

        float fraction =
                vulnerable.getHealth()
                        / Math.max(
                        1.0F,
                        vulnerable.getMaxHealth());

        if (fraction <= 0.40F
                && this.distanceToSqr(
                vulnerable)
                > 3.0D * 3.0D) {

            this.getNavigation()
                    .moveTo(
                            vulnerable.getX(),
                            vulnerable.getY(),
                            vulnerable.getZ(),
                            1.35D);
        }
    }

    // =====================================================================
    // Family helpers
    // =====================================================================

    public boolean isAncientFamilyMember(
            LivingEntity entity) {

        if (entity == null
                || !entity.isAlive()) {

            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {
            return false;
        }

        if (entity == this.getOwner()) {
            return true;
        }

        /*
         * Wolfism family canon:
         * all tamed wolves are protected family.
         */
        return entity
                instanceof Wolf wolf
                && wolf.isTame();
    }

    public List<LivingEntity>
    getNearbyAncientFamily(
            ServerLevel level,
            double radius) {

        List<LivingEntity> family =
                new ArrayList<>();

        double radiusSqr =
                radius * radius;

        if (this.isAlive()) {
            family.add(this);
        }

        if (this.isTame()
                && this.getOwner() != null
                && this.getOwner().isAlive()
                && this.distanceToSqr(
                this.getOwner())
                <= radiusSqr) {

            family.add(
                    this.getOwner());
        }

        if (this.isTame()) {

            List<Wolf> wolves =
                    level.getEntitiesOfClass(
                            Wolf.class,
                            this.getBoundingBox()
                                    .inflate(radius),
                            wolf ->
                                    wolf.isAlive()
                                            && wolf.isTame()
                                            && wolf != this);

            family.addAll(wolves);
        }

        return family;
    }

    private LivingEntity findMostVulnerableFamily(
            ServerLevel level,
            double radius) {

        return getNearbyAncientFamily(
                level,
                radius)
                .stream()
                .filter(
                        member ->
                                member != this)
                .min(
                        Comparator.comparingDouble(
                                member ->
                                        member.getHealth()
                                                / Math.max(
                                                1.0F,
                                                member.getMaxHealth())))
                .orElse(null);
    }

    private int countCriticalFamily(
            ServerLevel level,
            double radius,
            float threshold) {

        int count = 0;

        for (LivingEntity family
                : getNearbyAncientFamily(
                level,
                radius)) {

            if (family == this) {
                continue;
            }

            float fraction =
                    family.getHealth()
                            / Math.max(
                            1.0F,
                            family.getMaxHealth());

            if (fraction <= threshold) {
                ++count;
            }
        }

        return count;
    }

    // =====================================================================
    // Threat helpers
    // =====================================================================

    public boolean isValidAncientThreat(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target == this
                || isAncientFamilyMember(
                target)) {

            return false;
        }

        if (!(target instanceof Enemy)
                && !(target instanceof WitherBoss)
                && !(target instanceof EnderDragon)) {

            return false;
        }

        LivingEntity owner =
                this.getOwner();

        return !this.isTame()
                || owner == null
                || this.wantsToAttack(
                target,
                owner);
    }

    public List<LivingEntity>
    getNearbyAncientThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr =
                radius * radius;

        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox()
                                .inflate(radius),
                        this::isValidAncientThreat)
                .stream()
                .filter(
                        threat ->
                                this.distanceToSqr(
                                        threat)
                                        <= radiusSqr)
                .toList();
    }

    // =====================================================================
    // Knockback support
    // =====================================================================

    private void applyKnockbackBonus(
            LivingEntity entity,
            Map<UUID, LivingEntity> recipients,
            Identifier modifierId,
            double amount) {

        AttributeInstance attribute =
                entity.getAttribute(
                        Attributes.KNOCKBACK_RESISTANCE);

        if (attribute == null) {
            return;
        }

        attribute
                .addOrUpdateTransientModifier(
                        new AttributeModifier(
                                modifierId,
                                amount,
                                AttributeModifier.Operation
                                        .ADD_VALUE));

        recipients.put(
                entity.getUUID(),
                entity);
    }

    private void clearKnockbackRecipients(
            Map<UUID, LivingEntity> recipients,
            Identifier modifierId) {

        for (LivingEntity entity
                : recipients.values()) {

            if (entity == null) {
                continue;
            }

            AttributeInstance attribute =
                    entity.getAttribute(
                            Attributes.KNOCKBACK_RESISTANCE);

            if (attribute != null) {

                attribute.removeModifier(
                        modifierId);
            }
        }

        recipients.clear();
    }

    // =====================================================================
    // Cleanup
    // =====================================================================

    private void clearAdultAncientStates() {

        adaptationTicks = 0;
        activeAdaptation = null;

        ancestralHowlTicks = 0;
        echoesTicks = 0;

        clearKnockbackRecipients(
                howlRecipients,
                HOWL_KNOCKBACK_MODIFIER);

        clearKnockbackRecipients(
                echoesRecipients,
                ECHOES_KNOCKBACK_MODIFIER);
    }

    public boolean isAncientCombatVisualActive() {

        return ancestralHowlTicks > 0
                || echoesTicks > 0;
    }

    // =====================================================================
    // Natural spawning
    // =====================================================================

    public static boolean checkAncientWolfSpawnRules(
            EntityType<AncientWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Spawn egg, /summon, breeding and debugging are unrestricted.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason
                != EntitySpawnReason.CHUNK_GENERATION) {

            return true;
        }

        /*
         * Ancient remains fundamentally a wolf.
         */
        if (!level.getBlockState(
                        pos.below())
                .is(
                        BlockTags.WOLVES_SPAWNABLE_ON)) {

            return false;
        }

        /*
         * No fluid spawning.
         */
        if (!level.getFluidState(pos)
                .isEmpty()
                || !level.getFluidState(
                        pos.above())
                .isEmpty()) {

            return false;
        }

        /*
         * Body must have physical room.
         */
        if (!level.getBlockState(pos)
                .getCollisionShape(
                        level,
                        pos)
                .isEmpty()
                || !level
                .getBlockState(
                        pos.above())
                .getCollisionShape(
                        level,
                        pos.above())
                .isEmpty()) {

            return false;
        }

        /*
         * Ancient Wolves are solitary veteran encounters.
         *
         * Tamed Ancients do not suppress wild spawns.
         */
        if (level
                instanceof ServerLevel serverLevel) {

            boolean anotherWildAncient =
                    !serverLevel
                            .getEntitiesOfClass(
                                    AncientWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    72.0D,
                                                    36.0D,
                                                    72.0D),
                                    wolf ->
                                            wolf.isAlive()
                                                    && !wolf.isTame())
                            .isEmpty();

            if (anotherWildAncient) {
                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output) {

        super.addAdditionalSaveData(
                output);

        output.putInt(
                "AncientAdaptation",
                activeAdaptation == null
                        ? -1
                        : activeAdaptation.ordinal());

        output.putInt(
                "AncientAdaptationTicks",
                adaptationTicks);

        output.putInt(
                "AncientHowlCooldown",
                ancestralHowlCooldownTicks);

        output.putInt(
                "AncientEchoesCooldown",
                echoesCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {

        super.readAdditionalSaveData(
                input);

        int adaptationOrdinal =
                input.getIntOr(
                        "AncientAdaptation",
                        -1);

        if (adaptationOrdinal >= 0
                && adaptationOrdinal
                < ThreatType.values().length) {

            activeAdaptation =
                    ThreatType.values()[
                            adaptationOrdinal];

        } else {

            activeAdaptation = null;
        }

        adaptationTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AncientAdaptationTicks",
                                0));

        ancestralHowlCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AncientHowlCooldown",
                                0));

        echoesCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AncientEchoesCooldown",
                                0));

        /*
         * Temporary runtime states never survive a reload.
         *
         * Cooldowns do.
         */
        ancestralHowlTicks = 0;
        echoesTicks = 0;

        howlRecipients.clear();
        echoesRecipients.clear();
    }

    // =====================================================================
    // Threat categories
    // =====================================================================

    public enum ThreatType {

        PHYSICAL,
        PROJECTILE,
        FIRE,
        EXPLOSION,
        MAGIC
    }
}
