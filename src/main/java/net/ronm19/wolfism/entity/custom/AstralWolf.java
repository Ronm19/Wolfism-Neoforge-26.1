package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.AstralHomeGuideGoal;
import net.ronm19.wolfism.entity.ai.goal.AstralPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.AstralPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.AstralPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.AstralPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.AstralPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.AstralWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #31 - Astral Wolf.
 *
 * <p>Celestial / Night Support / Coordination specialist.</p>
 *
 * <p>Astral is deliberately not a celestial DPS cannon. Her strongest value is
 * turning nearby tamed wolves into a coordinated nighttime network.</p>
 *
 * <ol>
 *     <li>Celestial Awareness</li>
 *     <li>Celestial Projectile</li>
 *     <li>Celestial Area</li>
 *     <li>Night Navigation</li>
 *     <li>Starlight</li>
 *     <li>Celestial Convergence</li>
 * </ol>
 */
public final class AstralWolf extends AbstractWolfismWolf {

    /*
     * ================================================================
     * ASTRAL ABILITY CONTRACT — READ THIS FIRST
     * ================================================================
     *
     * 1) CELESTIAL AWARENESS
     *    WHEN: night, a valid hostile is sensed.
     *    DOES: Astral looks at it and shares that threat with idle family.
     *
     * 2) CELESTIAL PROJECTILE
     *    WHEN: adult Astral has a valid hostile ~5-18 blocks away with LOS.
     *    DOES: visible moving star bolt, 5 base damage, small knockback.
     *
     * 3) CELESTIAL AREA
     *    WHEN: nearby family is hit by a projectile OR is already <=55% HP.
     *    DOES: 10-block defensive field, -35% attack damage, 20% reflect
     *          capped at 5 damage.
     *
     * 4) NIGHT NAVIGATION
     *    WHEN: night + tamed Astral near owner.
     *    DOES: stable Night Vision; marks safer ground only near real hazards;
     *          points toward same-dimension bed/home when far away.
     *    DOES NOT: walk away and drag the owner around.
     *
     * 5) STARLIGHT
     *    WHEN: night + open sky directly above Astral.
     *    DOES: +10% attack, +8% speed, +2 armor.
     *
     * 6) CELESTIAL CONVERGENCE
     *    WHEN: night + serious fight:
     *          3+ hostiles OR 50+ HP enemy OR family emergency.
     *          Emergency starts immediately; ordinary fights warm up 1 sec.
     *    DOES: 14 sec coordinated target distribution + modest family buffs.
     *
     * PUPS NEVER ATTACK OR USE ADULT ACTIVE ABILITIES.
     * ================================================================
     */

    public static final double PACK_SCAN_RADIUS = 34.0D;
    public static final double CELESTIAL_AWARENESS_RANGE = 40.0D;
    public static final double NIGHT_SUPPORT_RADIUS = 20.0D;

    // ---------------------------------------------------------------------
    // 2) Celestial Projectile
    // ---------------------------------------------------------------------

    public static final int CELESTIAL_PROJECTILE_COOLDOWN_TICKS = 20 * 6;
    private static final double CELESTIAL_PROJECTILE_MIN_RANGE_SQR = 5.0D * 5.0D;
    private static final double CELESTIAL_PROJECTILE_MAX_RANGE_SQR = 18.0D * 18.0D;
    private static final double CELESTIAL_PROJECTILE_SPEED = 0.85D;
    private static final int CELESTIAL_PROJECTILE_MAX_TICKS = 28;
    private static final double CELESTIAL_PROJECTILE_HIT_DISTANCE_SQR = 1.15D * 1.15D;
    private static final float CELESTIAL_PROJECTILE_DAMAGE = 5.0F;

    // ---------------------------------------------------------------------
    // 3) Celestial Area
    // ---------------------------------------------------------------------

    public static final double CELESTIAL_AREA_RADIUS = 10.0D;
    public static final int CELESTIAL_AREA_DURATION_TICKS = 20 * 10;
    public static final int CELESTIAL_AREA_COOLDOWN_TICKS = 20 * 30;

    // ---------------------------------------------------------------------
    // 4) Night Navigation
    // ---------------------------------------------------------------------

    private static final double NIGHT_VISION_RADIUS = 18.0D;

    /*
     * Night Vision starts flashing when its remaining duration is short.
     * The old 6-second duration lived inside that warning window constantly,
     * so it visually flickered even though Astral kept refreshing it.
     *
     * Keep a comfortably long 15-second duration and refresh every 2 seconds.
     */
    private static final int NIGHT_VISION_DURATION_TICKS = 20 * 15;
    private static final int NIGHT_VISION_REFRESH_INTERVAL_TICKS = 20 * 2;

    /*
     * Navigation clarity / guide cadence.
     *
     * Safe-ground guidance is deliberately more obvious than before.
     * Home guidance is continuous while Astral is actively leading, and
     * displays relative direction + distance rather than pretending "64 blocks"
     * is a detection limit.
     */
    private static final int NAVIGATION_VISUAL_INTERVAL_TICKS = 20;
    private static final int NAVIGATION_MESSAGE_INTERVAL_TICKS = 20 * 4;

    /*
     * Safe Area is deliberately slower / cleaner than the home guide.
     * One large marker every two seconds and one short message every six
     * seconds is enough to communicate the destination without particle spam.
     */
    private static final int SAFE_AREA_VISUAL_INTERVAL_TICKS = 20 * 2;
    private static final int SAFE_AREA_MESSAGE_INTERVAL_TICKS = 20 * 6;
    private static final double SAFE_AREA_MARKER_RADIUS = 2.25D;

    private static final double HOME_GUIDE_FINISH_DISTANCE = 10.0D;

    // ---------------------------------------------------------------------
    // 5) Starlight
    // ---------------------------------------------------------------------

    private static final double STARLIGHT_DAMAGE_BONUS = 0.10D;
    private static final double STARLIGHT_SPEED_BONUS = 0.08D;
    private static final double STARLIGHT_ARMOR_BONUS = 2.0D;

    private static final Identifier STARLIGHT_DAMAGE_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "astral_starlight_damage");

    private static final Identifier STARLIGHT_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "astral_starlight_speed");

    private static final Identifier STARLIGHT_ARMOR_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "astral_starlight_armor");

    // ---------------------------------------------------------------------
    // 6) Celestial Convergence
    // ---------------------------------------------------------------------

    public static final double CONVERGENCE_RADIUS = 20.0D;
    public static final int CONVERGENCE_DURATION_TICKS = 20 * 14;
    public static final int CONVERGENCE_COOLDOWN_TICKS = 20 * 55;

    private static final int CONVERGENCE_UPDATE_INTERVAL = 10;
    private static final int CONVERGENCE_EFFECT_REFRESH_TICKS = 30;
    private static final int CONVERGENCE_MAX_THREATS = 12;

    /*
     * Clarity rule:
     * - 3+ nearby hostiles = group fight;
     * - 50+ max-HP enemy = heavy threat;
     * - endangered family / family-defense emergency = immediate emergency.
     *
     * Ordinary group/heavy fights need only one second of active combat before
     * Convergence begins. Emergency cases may begin immediately.
     */
    private static final int CONVERGENCE_REQUIRED_HOSTILES = 3;
    private static final int CONVERGENCE_COMBAT_WARMUP_TICKS = 20;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int celestialProjectileCooldownTicks;
    private CelestialBolt celestialBolt;

    private int celestialAreaTicks;
    private int celestialAreaCooldownTicks;

    private int convergenceTicks;
    private int convergenceCooldownTicks;

    private int combatTicks;
    private boolean starlightActive;

    private static final class BrainHolder {
        private static final Brain.Provider<AstralWolf> PROVIDER =
                Brain.<AstralWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.ASTRAL_PACK.get(),
                                ModSensorTypes.ASTRAL_NIGHT.get()),
                        wolf -> List.of());
    }

    private static final class CelestialBolt {
        private Vec3 position;
        private final int targetId;
        private int ticks;

        private CelestialBolt(
                Vec3 position,
                int targetId) {
            this.position = position;
            this.targetId = targetId;
        }
    }

    public AstralWolf(
            EntityType<? extends AstralWolf> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.ASTRAL_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    // ---------------------------------------------------------------------
    // Brain / pack basics
    // ---------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new AstralPupRetreatGoal(this));

        /*
         * Adult/tamed-only literal home guide. It owns movement only while
         * Astral is safely leading the owner toward a remembered home.
         */
        this.goalSelector.addGoal(4, new AstralHomeGuideGoal(this));

        this.goalSelector.addGoal(5, new AstralPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new AstralPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new AstralPupPlayGoal(this));

        this.targetSelector.addGoal(3, new AstralPackAssistGoal(this));
    }

    @Override
    protected Brain<AstralWolf> makeBrain(
            Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<AstralWolf> getBrain() {
        return (Brain<AstralWolf>)super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();

        if (current instanceof AstralWolf astral
                && this.isAstralPackmate(astral)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        if (this.isBaby()) {
            this.setTarget(null);
            this.clearAdultAstralState();
            this.refreshStarlightModifiers(false);
            return;
        }

        this.tickCooldowns();

        boolean night = !level.isBrightOutside();

        this.refreshStarlightModifiers(
                night
                        && level.canSeeSky(
                                this.blockPosition().above()));

        LivingEntity sharedThreat =
                this.getSharedCelestialThreat();

        if (this.getTarget() != null
                && this.getTarget().isAlive()) {
            ++this.combatTicks;
        } else if (this.hasFamilyDefenseEmergency()) {
            ++this.combatTicks;
        } else {
            this.combatTicks = 0;
        }

        if (night) {
            this.tickCelestialAwareness(
                    level,
                    sharedThreat);

            this.tickNightNavigation(level);
        }

        this.tickCelestialProjectile(level);
        this.tryStartCelestialProjectile(
                level,
                sharedThreat);

        this.tickCelestialArea(level);

        this.tickCelestialConvergence(level);
        this.tryStartCelestialConvergence(
                level,
                sharedThreat);
    }

    private void tickCooldowns() {
        if (this.celestialProjectileCooldownTicks > 0) {
            --this.celestialProjectileCooldownTicks;
        }

        if (this.celestialAreaCooldownTicks > 0) {
            --this.celestialAreaCooldownTicks;
        }

        if (this.convergenceCooldownTicks > 0) {
            --this.convergenceCooldownTicks;
        }
    }

    private void clearAdultAstralState() {
        this.celestialBolt = null;
        this.celestialAreaTicks = 0;
        this.convergenceTicks = 0;
        this.combatTicks = 0;
    }

    // ---------------------------------------------------------------------
    // 1) Celestial Awareness
    // ---------------------------------------------------------------------

    public LivingEntity getSharedCelestialThreat() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get())
                .orElse(null);
    }

    public LivingEntity getAstralFamilyInDanger() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.ASTRAL_FAMILY_IN_DANGER.get())
                .orElse(null);
    }

    public int getAstralHostileCount() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.ASTRAL_HOSTILE_COUNT.get())
                .orElse(0);
    }

    private void tickCelestialAwareness(
            ServerLevel level,
            LivingEntity threat) {

        if (threat == null
                || !threat.isAlive()) {
            return;
        }

        this.getLookControl().setLookAt(
                threat,
                55.0F,
                40.0F);

        if (!this.isWolfismWorkTick(10)) {
            return;
        }

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY() + 0.72D,
                this.getZ(),
                3,
                0.22D, 0.18D, 0.22D,
                0.005D);

        /*
         * Awareness is information-sharing, not War-Wolf command.
         *
         * Normally Astral wakes up at most two idle family wolves. If the
         * threat is already actively endangering family, she may wake four.
         * Convergence is the state that coordinates the whole pack.
         */
        int maxRecipients =
                this.isThreatCommittedToAstralFamily(threat)
                        ? 4
                        : 2;

        this.shareThreatWithIdleFamily(
                level,
                threat,
                maxRecipients);
    }

    private void shareThreatWithIdleFamily(
            ServerLevel level,
            LivingEntity threat,
            int maxRecipients) {

        if (!this.isTame()
                || threat instanceof Creeper
                || !this.isPotentialAstralThreat(threat)) {
            return;
        }

        List<Wolf> family =
                new ArrayList<>(
                        level.getEntitiesOfClass(
                                Wolf.class,
                                this.getBoundingBox().inflate(
                                        NIGHT_SUPPORT_RADIUS),
                                wolf -> wolf.isAlive()
                                        && wolf.isTame()
                                        && !wolf.isBaby()
                                        && !wolf.isOrderedToSit()
                                        && wolf != this
                                        && wolf.getTarget() == null
                                        && wolf.canAttack(threat)));

        family.sort(
                Comparator.comparingDouble(
                        wolf ->
                                wolf.distanceToSqr(threat)));

        int recipients = 0;

        for (Wolf wolf : family) {
            wolf.getLookControl().setLookAt(
                    threat,
                    45.0F,
                    35.0F);

            wolf.setTarget(threat);

            if (++recipients >= maxRecipients) {
                break;
            }
        }

        if (this.getTarget() == null
                && !(threat instanceof Creeper)
                && this.isValidAstralCombatTarget(threat)
                && (this.isThreatCommittedToAstralFamily(threat)
                || this.distanceToSqr(threat)
                        <= 12.0D * 12.0D)) {
            this.setTarget(threat);
        }
    }

    // ---------------------------------------------------------------------
    // 2) Celestial Projectile
    // ---------------------------------------------------------------------

    public boolean hasCelestialProjectileInFlight() {
        return this.celestialBolt != null;
    }

    private void tryStartCelestialProjectile(
            ServerLevel level,
            LivingEntity preferredThreat) {

        if (!this.canUseActiveWolfismAbility()
                || this.isBaby()
                || this.celestialBolt != null
                || this.celestialProjectileCooldownTicks > 0) {
            return;
        }

        LivingEntity target =
                this.getTarget();

        if (target == null
                || !target.isAlive()) {
            target = preferredThreat;
        }

        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.isValidAstralCombatTarget(target)) {
            return;
        }

        double distanceSqr =
                this.distanceToSqr(target);

        if (distanceSqr < CELESTIAL_PROJECTILE_MIN_RANGE_SQR
                || distanceSqr > CELESTIAL_PROJECTILE_MAX_RANGE_SQR
                || !this.getSensing().hasLineOfSight(target)) {
            return;
        }

        this.celestialBolt =
                new CelestialBolt(
                        this.getEyePosition()
                                .add(0.0D, 0.05D, 0.0D),
                        target.getId());

        this.celestialProjectileCooldownTicks =
                CELESTIAL_PROJECTILE_COOLDOWN_TICKS;

        // Bright launch flash so this ability is obvious during testing.
        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                this.celestialBolt.position.x,
                this.celestialBolt.position.y,
                this.celestialBolt.position.z,
                16,
                0.22D, 0.22D, 0.22D,
                0.040D);
    }

    private void tickCelestialProjectile(
            ServerLevel level) {

        if (this.celestialBolt == null) {
            return;
        }

        Entity entity =
                level.getEntity(
                        this.celestialBolt.targetId);

        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || target instanceof Creeper
                || !this.isPotentialAstralThreat(target)) {
            this.celestialBolt = null;
            return;
        }

        if (!this.getSensing().hasLineOfSight(target)) {
            this.celestialBolt = null;
            return;
        }

        Vec3 targetPos =
                target.getEyePosition();

        Vec3 delta =
                targetPos.subtract(
                        this.celestialBolt.position);

        if (delta.lengthSqr()
                <= CELESTIAL_PROJECTILE_HIT_DISTANCE_SQR) {

            float damage =
                    CELESTIAL_PROJECTILE_DAMAGE
                            * (this.starlightActive
                            ? 1.10F
                            : 1.0F);

            DamageSource source =
                    this.damageSources().source(
                            DamageTypes.MOB_PROJECTILE,
                            this);

            target.hurtServer(
                    level,
                    source,
                    damage);

            Vec3 knock =
                    target.position()
                            .subtract(this.position());

            if (knock.lengthSqr() > 1.0E-5D) {
                knock = knock.normalize();

                target.push(
                        knock.x * 0.22D,
                        0.06D,
                        knock.z * 0.22D);
            }

            // Distinct star-burst on impact.
            WolfVfx.sendParticles("astral_wolf", level,
                    ParticleTypes.END_ROD,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight() * 0.55D,
                    target.getZ(),
                    22,
                    0.36D, 0.40D, 0.36D,
                    0.050D);

            this.celestialBolt = null;
            return;
        }

        if (delta.lengthSqr() <= 1.0E-6D) {
            this.celestialBolt = null;
            return;
        }

        Vec3 movement =
                delta.normalize()
                        .scale(
                                CELESTIAL_PROJECTILE_SPEED);

        this.celestialBolt.position =
                this.celestialBolt.position
                        .add(movement);

        ++this.celestialBolt.ticks;

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                this.celestialBolt.position.x,
                this.celestialBolt.position.y,
                this.celestialBolt.position.z,
                2,
                0.06D, 0.06D, 0.06D,
                0.0D);

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                this.celestialBolt.position.x,
                this.celestialBolt.position.y,
                this.celestialBolt.position.z,
                1,
                0.04D, 0.04D, 0.04D,
                0.0D);

        if (this.celestialBolt.ticks
                >= CELESTIAL_PROJECTILE_MAX_TICKS) {
            this.celestialBolt = null;
        }
    }

    // ---------------------------------------------------------------------
    // 3) Celestial Area
    // ---------------------------------------------------------------------

    public boolean isCelestialAreaActive() {
        return this.celestialAreaTicks > 0;
    }

    public boolean canActivateCelestialArea() {
        return !this.isBaby()
                && this.isTame()
                && this.canUseActiveWolfismAbility()
                && this.celestialAreaTicks <= 0
                && this.celestialAreaCooldownTicks <= 0;
    }

    /**
     * Called both by Astral AI and by AstralWolfGameplayEvents when family
     * suddenly takes ranged / emergency pressure.
     */
    public boolean activateCelestialArea(
            ServerLevel level) {

        if (!this.canActivateCelestialArea()) {
            return false;
        }

        this.celestialAreaTicks =
                CELESTIAL_AREA_DURATION_TICKS;

        // Strong central burst = Celestial Area has activated.
        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                44,
                1.35D, 0.55D, 1.35D,
                0.055D);

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                30,
                1.15D, 0.45D, 1.15D,
                0.035D);

        return true;
    }

    private void tickCelestialArea(
            ServerLevel level) {

        if (this.celestialAreaTicks <= 0) {
            return;
        }

        --this.celestialAreaTicks;

        if (this.tickCount % 5 == 0) {
            this.sendCelestialAreaParticles(level);
        }

        if (this.celestialAreaTicks == 0) {
            this.celestialAreaCooldownTicks =
                    CELESTIAL_AREA_COOLDOWN_TICKS;
        }
    }

    private void sendCelestialAreaParticles(
            ServerLevel level) {

        for (int i = 0; i < 10; ++i) {
            double angle =
                    this.random.nextDouble()
                            * Math.PI
                            * 2.0D;

            double radius =
                    CELESTIAL_AREA_RADIUS
                            * (0.80D
                            + this.random.nextDouble()
                            * 0.18D);

            double x =
                    this.getX()
                            + Math.cos(angle)
                            * radius;

            double z =
                    this.getZ()
                            + Math.sin(angle)
                            * radius;

            double y =
                    this.getY()
                            + 0.25D
                            + this.random.nextDouble()
                            * 2.0D;

            WolfVfx.sendParticles("astral_wolf", level,
                    ParticleTypes.END_ROD,
                    x,
                    y,
                    z,
                    1,
                    0.0D, 0.02D, 0.0D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // 4) Night Navigation
    // ---------------------------------------------------------------------

    private void tickNightNavigation(
            ServerLevel level) {

        if (!this.isTame()) {
            return;
        }

        LivingEntity owner =
                this.getOwner();

        if (owner == null
                || !owner.isAlive()) {
            return;
        }

        // -------------------------------------------------------------
        // A) Stable Night Vision
        // -------------------------------------------------------------

        if (this.distanceToSqr(owner)
                <= NIGHT_VISION_RADIUS
                * NIGHT_VISION_RADIUS
                && this.tickCount
                % NIGHT_VISION_REFRESH_INTERVAL_TICKS
                == 0) {

            owner.addEffect(
                    new MobEffectInstance(
                            MobEffects.NIGHT_VISION,
                            NIGHT_VISION_DURATION_TICKS,
                            0,
                            true,
                            false),
                    this);
        }

        if (this.getTarget() != null
                || this.hasFamilyDefenseEmergency()) {
            return;
        }

        // -------------------------------------------------------------
        // B) Safer-ground guidance
        // -------------------------------------------------------------

        BlockPos safePos =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes.ASTRAL_SAFE_ROUTE_POS.get())
                        .orElse(null);

        /*
         * Hazard guidance takes visual/message priority over home guidance.
         * If Astral sees an immediate lava/cliff/etc. problem, tell the player
         * how to get safe FIRST.
         */
        if (safePos != null) {
            this.getLookControl().setLookAt(
                    safePos.getX() + 0.5D,
                    safePos.getY() + 0.5D,
                    safePos.getZ() + 0.5D,
                    45.0F,
                    35.0F);

            /*
             * ONE destination, not a breadcrumb carpet.
             *
             * Astral now marks the center/perimeter of one validated safe area.
             * The player should be able to glance at the field and understand:
             *
             *      "That celestial circle over there = safe."
             */
            if (this.tickCount
                    % SAFE_AREA_VISUAL_INTERVAL_TICKS
                    == 0) {

                this.sendSafeAreaMarker(
                        level,
                        safePos);
            }

            if (owner instanceof ServerPlayer player
                    && this.tickCount
                    % SAFE_AREA_MESSAGE_INTERVAL_TICKS
                    == 0) {

                double distance =
                        Math.sqrt(
                                safePos.distToCenterSqr(
                                        player.getX(),
                                        player.getY(),
                                        player.getZ()));

                player.sendOverlayMessage(
                        Component.literal(
                                "Astral: Safe area "
                                        + this.cardinalDirectionText(
                                        safePos.getX() + 0.5D
                                                - player.getX(),
                                        safePos.getZ() + 0.5D
                                                - player.getZ())
                                        + " • "
                                        + Math.max(
                                        1,
                                        (int)Math.round(distance))
                                        + " blocks"));
            }

            return;
        }

        // -------------------------------------------------------------
        // C) Home direction + literal guide support
        // -------------------------------------------------------------

        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        BlockPos home =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes.ASTRAL_HOME_POS.get())
                        .orElse(null);

        if (home == null) {
            return;
        }

        double homeDistance =
                Math.sqrt(
                        home.distToCenterSqr(
                                player.getX(),
                                player.getY(),
                                player.getZ()));

        if (homeDistance
                <= HOME_GUIDE_FINISH_DISTANCE) {

            if (this.tickCount
                    % NAVIGATION_MESSAGE_INTERVAL_TICKS
                    == 0) {
                player.sendOverlayMessage(
                        Component.literal(
                                "Astral: Home is here."));
            }

            return;
        }

        Vec3 from =
                player.position()
                        .add(0.0D, 1.20D, 0.0D);

        Vec3 toHome =
                Vec3.atCenterOf(home)
                        .subtract(from);

        Vec3 horizontal =
                new Vec3(
                        toHome.x,
                        0.0D,
                        toHome.z);

        if (horizontal.lengthSqr()
                <= 1.0E-5D) {
            return;
        }

        horizontal = horizontal.normalize();

        /*
         * Short celestial arrow in front of the owner.
         *
         * The dedicated AstralHomeGuideGoal simultaneously puts Astral herself
         * several blocks ahead on this same bearing, waits if the owner falls
         * behind, then advances again as the owner follows.
         */
        if (this.tickCount
                % NAVIGATION_VISUAL_INTERVAL_TICKS
                == 0) {

            for (int i = 1; i <= 7; ++i) {
                Vec3 point =
                        from.add(
                                horizontal.scale(
                                        i * 0.72D));

                WolfVfx.sendParticles("astral_wolf", level,
                        ParticleTypes.END_ROD,
                        point.x,
                        point.y,
                        point.z,
                        i == 7 ? 2 : 1,
                        0.025D, 0.025D, 0.025D,
                        0.0D);
            }
        }

        if (this.tickCount
                % NAVIGATION_MESSAGE_INTERVAL_TICKS
                == 0) {

            player.sendOverlayMessage(
                    Component.literal(
                            "Astral: Home is "
                                    + this.cardinalDirectionText(
                                    home.getX() + 0.5D
                                            - player.getX(),
                                    home.getZ() + 0.5D
                                            - player.getZ())
                                    + " • "
                                    + (int)Math.round(homeDistance)
                                    + " blocks"));
        }
    }

    /**
     * Marks ONE safe destination as a celestial zone.
     *
     * <p>The ring communicates usable area; the short center pillar makes the
     * destination visible through grass / slight terrain clutter. There is no
     * block-by-block breadcrumb trail.</p>
     */
    private void sendSafeAreaMarker(
            ServerLevel level,
            BlockPos safePos) {

        double centerX =
                safePos.getX() + 0.5D;

        double centerY =
                safePos.getY() + 0.20D;

        double centerZ =
                safePos.getZ() + 0.5D;

        // One clean perimeter ring around the safe zone.
        for (int i = 0; i < 12; ++i) {
            double angle =
                    (Math.PI * 2.0D * i)
                            / 12.0D;

            WolfVfx.sendParticles("astral_wolf", level,
                    ParticleTypes.END_ROD,
                    centerX
                            + Math.cos(angle)
                            * SAFE_AREA_MARKER_RADIUS,
                    centerY,
                    centerZ
                            + Math.sin(angle)
                            * SAFE_AREA_MARKER_RADIUS,
                    1,
                    0.015D, 0.015D, 0.015D,
                    0.0D);
        }

        // Small celestial "beacon" at the exact center.
        for (int i = 0; i < 4; ++i) {
            WolfVfx.sendParticles("astral_wolf", level,
                    ParticleTypes.END_ROD,
                    centerX,
                    centerY + 0.45D + i * 0.42D,
                    centerZ,
                    1,
                    0.02D, 0.02D, 0.02D,
                    0.0D);
        }

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                centerX,
                centerY + 0.10D,
                centerZ,
                5,
                0.55D, 0.08D, 0.55D,
                0.01D);
    }

    /**
     * Minecraft coordinate compass:
     * +X east, -X west, +Z south, -Z north.
     */
    private String cardinalDirectionText(
            double dx,
            double dz) {

        double absX =
                Math.abs(dx);

        double absZ =
                Math.abs(dz);

        if (absX
                > absZ * 1.8D) {
            return dx >= 0.0D
                    ? "east"
                    : "west";
        }

        if (absZ
                > absX * 1.8D) {
            return dz >= 0.0D
                    ? "south"
                    : "north";
        }

        if (dx >= 0.0D
                && dz >= 0.0D) {
            return "southeast";
        }

        if (dx >= 0.0D) {
            return "northeast";
        }

        if (dz >= 0.0D) {
            return "southwest";
        }

        return "northwest";
    }

    // ---------------------------------------------------------------------
    // 5) Starlight
    // ---------------------------------------------------------------------

    public boolean isStarlightActive() {
        return this.starlightActive;
    }

    private void refreshStarlightModifiers(
            boolean active) {

        boolean changed =
                this.starlightActive != active;

        if (!changed
                && !this.isWolfismWorkTick(20)) {
            return;
        }

        this.starlightActive = active;

        this.applyOrRemoveModifier(
                Attributes.ATTACK_DAMAGE,
                STARLIGHT_DAMAGE_MODIFIER,
                active
                        ? STARLIGHT_DAMAGE_BONUS
                        : 0.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        this.applyOrRemoveModifier(
                Attributes.MOVEMENT_SPEED,
                STARLIGHT_SPEED_MODIFIER,
                active
                        ? STARLIGHT_SPEED_BONUS
                        : 0.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        this.applyOrRemoveModifier(
                Attributes.ARMOR,
                STARLIGHT_ARMOR_MODIFIER,
                active
                        ? STARLIGHT_ARMOR_BONUS
                        : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);

        if (active
                && this.level()
                instanceof ServerLevel level) {

            if (changed) {
                // One clear "stars switched on" burst.
                WolfVfx.sendParticles("astral_wolf", level,
                        ParticleTypes.END_ROD,
                        this.getX(),
                        this.getY() + 0.65D,
                        this.getZ(),
                        18,
                        0.55D, 0.45D, 0.55D,
                        0.025D);
            } else if (this.isWolfismWorkTick(20)) {
                // Then remain intentionally subtle.
                WolfVfx.sendParticles("astral_wolf", level,
                        ParticleTypes.END_ROD,
                        this.getX(),
                        this.getY() + 0.55D,
                        this.getZ(),
                        3,
                        0.38D, 0.30D, 0.38D,
                        0.005D);
            }
        }
    }

    private void applyOrRemoveModifier(
            net.minecraft.core.Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation) {

        AttributeInstance instance =
                this.getAttribute(attribute);

        if (instance == null) {
            return;
        }

        if (amount <= 1.0E-6D) {
            instance.removeModifier(id);
            return;
        }

        instance.addOrUpdateTransientModifier(
                new AttributeModifier(
                        id,
                        amount,
                        operation));
    }

    // ---------------------------------------------------------------------
    // 6) Celestial Convergence
    // ---------------------------------------------------------------------

    public boolean isCelestialConvergenceActive() {
        return this.convergenceTicks > 0;
    }

    private void tryStartCelestialConvergence(
            ServerLevel level,
            LivingEntity threat) {

        if (level.isBrightOutside()
                || !this.isTame()
                || !this.canUseActiveWolfismAbility()
                || this.convergenceTicks > 0
                || this.convergenceCooldownTicks > 0
                || threat == null
                || !threat.isAlive()) {
            return;
        }

        LivingEntity familyInDanger =
                this.getAstralFamilyInDanger();

        boolean emergency =
                familyInDanger != null
                        || this.hasFamilyDefenseEmergency();

        boolean groupFight =
                this.getAstralHostileCount()
                        >= CONVERGENCE_REQUIRED_HOSTILES;

        boolean heavyThreat =
                threat.getMaxHealth() >= 50.0F;

        if (!emergency
                && !groupFight
                && !heavyThreat) {
            return;
        }

        /*
         * Emergency = immediate.
         * Ordinary group/heavy fight = one second of real combat first.
         */
        if (!emergency
                && this.combatTicks
                < CONVERGENCE_COMBAT_WARMUP_TICKS) {
            return;
        }

        this.convergenceTicks =
                CONVERGENCE_DURATION_TICKS;

        /*
         * Large activation burst: Convergence should be visually impossible
         * to confuse with ordinary Awareness or Starlight particles.
         */
        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY() + 0.75D,
                this.getZ(),
                64,
                1.80D, 0.80D, 1.80D,
                0.075D);

        WolfVfx.sendParticles("astral_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.60D,
                this.getZ(),
                42,
                1.55D, 0.60D, 1.55D,
                0.040D);
    }

    private void tickCelestialConvergence(
            ServerLevel level) {

        if (this.convergenceTicks <= 0) {
            return;
        }

        --this.convergenceTicks;

        if (this.tickCount
                % CONVERGENCE_UPDATE_INTERVAL
                == 0) {

            this.refreshConvergenceFamilyEffects(level);
            this.coordinateConvergenceTargets(level);
        }

        if (this.tickCount % 5 == 0) {
            WolfVfx.sendParticles("astral_wolf", level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.50D,
                    this.getZ(),
                    7,
                    CONVERGENCE_RADIUS * 0.18D,
                    0.22D,
                    CONVERGENCE_RADIUS * 0.18D,
                    0.012D);
        }

        if (this.convergenceTicks == 0) {
            this.convergenceCooldownTicks =
                    CONVERGENCE_COOLDOWN_TICKS;
        }
    }

    private void refreshConvergenceFamilyEffects(
            ServerLevel level) {

        for (LivingEntity family :
                this.getConvergenceFamily(level)) {

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.STRENGTH,
                            CONVERGENCE_EFFECT_REFRESH_TICKS,
                            0,
                            true,
                            false),
                    this);

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            CONVERGENCE_EFFECT_REFRESH_TICKS,
                            0,
                            true,
                            false),
                    this);

            if (family.getHealth()
                    <= family.getMaxHealth() * 0.40F) {

                family.addEffect(
                        new MobEffectInstance(
                                MobEffects.REGENERATION,
                                CONVERGENCE_EFFECT_REFRESH_TICKS,
                                0,
                                true,
                                false),
                        this);
            }
        }
    }

    private void coordinateConvergenceTargets(
            ServerLevel level) {

        List<Wolf> family =
                level.getEntitiesOfClass(
                        Wolf.class,
                        this.getBoundingBox().inflate(
                                CONVERGENCE_RADIUS),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && !wolf.isBaby()
                                && !wolf.isOrderedToSit());

        if (family.isEmpty()) {
            return;
        }

        List<LivingEntity> threats =
                this.findConvergenceThreats(level);

        if (threats.isEmpty()) {
            return;
        }

        Map<Integer, Integer> assigned =
                new HashMap<>();

        /*
         * Healthy wolves get first assignment. Wounded wolves can keep their
         * own valid target, but Convergence does not aggressively throw them
         * into a fresh fight unless they are healthy enough.
         */
        family.sort(
                Comparator.comparingDouble(
                        wolf ->
                                -(wolf.getHealth()
                                / Math.max(
                                        1.0F,
                                        wolf.getMaxHealth()))));

        for (Wolf wolf : family) {
            LivingEntity current =
                    wolf.getTarget();

            if (current != null
                    && current.isAlive()
                    && this.isConvergenceThreat(current)
                    && this.withinAssignmentCapacity(
                            current,
                            assigned)) {

                assigned.merge(
                        current.getId(),
                        1,
                        Integer::sum);

                continue;
            }

            if (wolf.getHealth()
                    < wolf.getMaxHealth() * 0.25F) {
                continue;
            }

            LivingEntity choice =
                    threats.stream()
                            .filter(
                                    target ->
                                            this.withinAssignmentCapacity(
                                                    target,
                                                    assigned))
                            .filter(wolf::canAttack)
                            .max(
                                    Comparator.comparingDouble(
                                            target ->
                                                    this.convergenceAssignmentScore(
                                                            wolf,
                                                            target,
                                                            assigned)))
                            .orElse(null);

            if (choice == null) {
                continue;
            }

            wolf.setTarget(choice);

            assigned.merge(
                    choice.getId(),
                    1,
                    Integer::sum);

            this.applyConvergenceApproachSpacing(
                    wolf,
                    choice,
                    assigned.get(
                            choice.getId()));
        }
    }

    private List<LivingEntity> findConvergenceThreats(
            ServerLevel level) {

        List<LivingEntity> threats =
                new ArrayList<>(
                        level.getEntitiesOfClass(
                                LivingEntity.class,
                                this.getBoundingBox().inflate(
                                        CONVERGENCE_RADIUS),
                                this::isConvergenceThreat));

        threats.sort(
                Comparator.comparingDouble(
                        this::convergenceThreatPriority)
                        .reversed());

        if (threats.size()
                > CONVERGENCE_MAX_THREATS) {
            return new ArrayList<>(
                    threats.subList(
                            0,
                            CONVERGENCE_MAX_THREATS));
        }

        return threats;
    }

    private boolean isConvergenceThreat(
            LivingEntity candidate) {

        if (candidate == null
                || !candidate.isAlive()
                || candidate instanceof Creeper
                || this.isAstralFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        if (candidate instanceof Enemy
                || candidate instanceof Raider
                || candidate == this.getFamilyDefenseTarget()
                || candidate == this.getTarget()) {
            return true;
        }

        if (candidate instanceof Mob mob) {
            LivingEntity target =
                    mob.getTarget();

            return target != null
                    && this.isAstralFamilyMember(target);
        }

        return false;
    }

    private double convergenceThreatPriority(
            LivingEntity target) {

        double score =
                target.getMaxHealth();

        if (target == this.getFamilyDefenseTarget()) {
            score += 220.0D;
        }

        if (this.isThreatCommittedToAstralFamily(target)) {
            score += 180.0D;
        }

        if (target instanceof Raider) {
            score += 45.0D;
        }

        score -=
                Math.sqrt(
                        this.distanceToSqr(target))
                        * 1.2D;

        return score;
    }

    private double convergenceAssignmentScore(
            Wolf wolf,
            LivingEntity target,
            Map<Integer, Integer> assigned) {

        int alreadyAssigned =
                assigned.getOrDefault(
                        target.getId(),
                        0);

        return this.convergenceThreatPriority(target)
                - alreadyAssigned * 55.0D
                - Math.sqrt(
                        wolf.distanceToSqr(target))
                        * 1.4D;
    }

    private boolean withinAssignmentCapacity(
            LivingEntity target,
            Map<Integer, Integer> assigned) {

        return assigned.getOrDefault(
                target.getId(),
                0)
                < this.targetAssignmentCapacity(target);
    }

    private int targetAssignmentCapacity(
            LivingEntity target) {

        float health =
                target.getMaxHealth();

        if (health <= 20.0F) {
            return 1;
        }

        if (health <= 50.0F) {
            return 2;
        }

        if (health <= 100.0F) {
            return 3;
        }

        return 4;
    }

    /**
     * A conservative first-pass flanking cue.
     *
     * <p>We do NOT continuously seize another species' navigation. That would
     * fight Fire/Storm/Blood/etc. combat goals. Instead, when a freshly
     * assigned wolf is still several blocks away and its navigation is idle,
     * Convergence seeds an offset approach point around a multi-wolf target.
     * Species-specific combat AI remains authoritative afterward.</p>
     */
    private void applyConvergenceApproachSpacing(
            Wolf wolf,
            LivingEntity target,
            int assignmentIndex) {

        int capacity =
                this.targetAssignmentCapacity(target);

        if (capacity <= 1
                || !wolf.getNavigation().isDone()
                || wolf.distanceToSqr(target)
                        <= 4.5D * 4.5D) {
            return;
        }

        double angle =
                (assignmentIndex - 1)
                        * (Math.PI * 2.0D / capacity)
                        + (wolf.getId() % 7) * 0.13D;

        double radius = 2.4D;

        wolf.getNavigation().moveTo(
                target.getX()
                        + Math.cos(angle)
                        * radius,
                target.getY(),
                target.getZ()
                        + Math.sin(angle)
                        * radius,
                1.10D);
    }

    private List<LivingEntity> getConvergenceFamily(
            ServerLevel level) {

        List<LivingEntity> family =
                new ArrayList<>();

        if (this.isTame()) {
            LivingEntity owner =
                    this.getOwner();

            if (owner != null
                    && owner.isAlive()
                    && this.distanceToSqr(owner)
                            <= CONVERGENCE_RADIUS
                            * CONVERGENCE_RADIUS) {
                family.add(owner);
            }

            family.addAll(
                    level.getEntitiesOfClass(
                            Wolf.class,
                            this.getBoundingBox().inflate(
                                    CONVERGENCE_RADIUS),
                            wolf -> wolf.isAlive()
                                    && wolf.isTame()));
        }

        return family;
    }

    // ---------------------------------------------------------------------
    // Family / pack helpers
    // ---------------------------------------------------------------------

    public boolean isAstralFamilyMember(
            Entity entity) {

        if (entity == null
                || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {
            return entity instanceof AstralWolf astral
                    && this.isAstralPackmate(astral);
        }

        if (entity == this.getOwner()) {
            return true;
        }

        return entity instanceof Wolf wolf
                && wolf.isTame();
    }

    public boolean isAstralPackmate(
            AstralWolf other) {

        if (other == null
                || other == this
                || other.isTame()
                != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(
                this.getOwnerReference(),
                other.getOwnerReference());
    }

    public boolean isPotentialAstralThreat(
            LivingEntity candidate) {

        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || this.isAstralFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        if (candidate instanceof Enemy
                || candidate instanceof Raider
                || candidate == this.getFamilyDefenseTarget()
                || candidate == this.getTarget()) {
            return true;
        }

        if (candidate instanceof Mob mob) {
            LivingEntity target =
                    mob.getTarget();

            return target != null
                    && this.isAstralFamilyMember(target);
        }

        return false;
    }

    public boolean isValidAstralCombatTarget(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.canAttack(target)
                || this.isAstralFamilyMember(target)
                || this.isAlliedTo(target)) {
            return false;
        }

        LivingEntity owner =
                this.getOwner();

        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(
                        target,
                        owner);
    }

    public boolean isValidAstralPackThreat(
            LivingEntity target) {

        return target != null
                && target.isAlive()
                && !(target instanceof Creeper)
                && this.isPotentialAstralThreat(target);
    }

    public boolean isThreatCommittedToAstralFamily(
            LivingEntity threat) {

        if (threat == null
                || !threat.isAlive()) {
            return false;
        }

        if (threat == this.getFamilyDefenseTarget()) {
            return true;
        }

        if (threat instanceof Mob mob) {
            LivingEntity target =
                    mob.getTarget();

            return target != null
                    && this.isAstralFamilyMember(target);
        }

        return false;
    }

    public void alertAstralPackToThreat(
            LivingEntity threat) {

        if (!(this.level()
                instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || threat instanceof Creeper
                || !this.isValidAstralPackThreat(threat)) {
            return;
        }

        for (AstralWolf mate :
                level.getEntitiesOfClass(
                        AstralWolf.class,
                        this.getBoundingBox().inflate(
                                AstralWolfPackSensor.PACK_SCAN_RADIUS),
                        wolf -> wolf.isAlive()
                                && (wolf == this
                                || this.isAstralPackmate(wolf)))) {

            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.ASTRAL_SHARED_THREAT.get(),
                    threat);

            if (!mate.isBaby()
                    && !mate.isOrderedToSit()
                    && mate.isValidAstralCombatTarget(threat)) {
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

        if (!(attacker instanceof Creeper)) {
            this.alertAstralPackToThreat(attacker);
        }

        if (this.level()
                instanceof ServerLevel level
                && this.canActivateCelestialArea()
                && (protectedFamily.getHealth()
                <= protectedFamily.getMaxHealth()
                * 0.55F
                || attacker.getMaxHealth() >= 35.0F)) {

            this.activateCelestialArea(level);
        }
    }

    // ---------------------------------------------------------------------
    // Natural spawn
    // ---------------------------------------------------------------------

    public static boolean checkAstralWolfSpawnRules(
            EntityType<AstralWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Proven Wolfism night-spawn pattern copied from Lunar Wolf:
         * night + open sky + normal wolf-spawnable ground.
         *
         * Astral's EntityType should use MobCategory.AMBIENT in ModEntities.
         */
        boolean night =
                level instanceof Level actual
                        && actual.isDarkOutside();

        return night
                && level.canSeeSky(pos)
                && level.getBlockState(pos.below())
                .is(BlockTags.WOLVES_SPAWNABLE_ON);
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt(
                "AstralProjectileCooldown",
                this.celestialProjectileCooldownTicks);

        output.putInt(
                "AstralAreaTicks",
                this.celestialAreaTicks);

        output.putInt(
                "AstralAreaCooldown",
                this.celestialAreaCooldownTicks);

        output.putInt(
                "AstralConvergenceTicks",
                this.convergenceTicks);

        output.putInt(
                "AstralConvergenceCooldown",
                this.convergenceCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {
        super.readAdditionalSaveData(input);

        this.celestialProjectileCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AstralProjectileCooldown",
                                0));

        this.celestialAreaTicks =
                clamp(
                        input.getIntOr(
                                "AstralAreaTicks",
                                0),
                        0,
                        CELESTIAL_AREA_DURATION_TICKS);

        this.celestialAreaCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AstralAreaCooldown",
                                0));

        this.convergenceTicks =
                clamp(
                        input.getIntOr(
                                "AstralConvergenceTicks",
                                0),
                        0,
                        CONVERGENCE_DURATION_TICKS);

        this.convergenceCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "AstralConvergenceCooldown",
                                0));

        this.celestialBolt = null;

        if (this.isBaby()) {
            this.clearAdultAstralState();
        }
    }

    private static int clamp(
            int value,
            int min,
            int max) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value));
    }
}
