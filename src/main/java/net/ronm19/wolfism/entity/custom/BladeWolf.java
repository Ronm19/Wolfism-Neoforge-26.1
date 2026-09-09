package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Blade Wolf #48
 *
 * Physical / Melee / Blade Specialist.
 *
 * Canon kit:
 * - Sharp Bite
 * - Sword Enhancement
 * - Blade's Charm
 * - Blade Charge
 * - Dancing Swords
 * - Rain of Swords
 *
 * Dancing Swords use vanilla ItemDisplay manifestations.
 * Rain of Swords uses invisible, non-interactable ArmorStand carriers holding
 * vanilla iron swords so Rain can use a true local arm pose without custom
 * item-model resources or collectible sword drops.
 * Blade applies all actual combat damage manually.
 */
public final class BladeWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Baseline
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 48.0D;
    private static final int DECISION_INTERVAL = 4;

    // =====================================================================
    // Sharp Bite
    // =====================================================================

    private static final float SHARP_BITE_BONUS_DAMAGE = 2.50F;
    private static final float ENHANCED_BITE_EXTRA_DAMAGE = 1.25F;

    private static final double ENHANCED_SWEEP_RADIUS = 2.75D;
    private static final float ENHANCED_SWEEP_DAMAGE = 4.0F;

    private static final int ENHANCED_SWEEP_MAX_TARGETS = 3;
    private static final int ENHANCED_SWEEP_INTERNAL_COOLDOWN = 10;

    // =====================================================================
    // Sword Enhancement
    // =====================================================================

    public static final int SWORD_ENHANCEMENT_DURATION_TICKS = 20 * 12;
    public static final int SWORD_ENHANCEMENT_COOLDOWN_TICKS = 20 * 24;

    // =====================================================================
    // Blade's Charm
    // =====================================================================

    public static final int BLADE_CHARM_DURATION_TICKS = 20 * 15;

    private static final int BLADE_CHARM_BASE_COOLDOWN_TICKS = 20 * 20;
    private static final int BLADE_CHARM_EXTRA_PER_RECIPIENT_TICKS = 20 * 2;
    private static final int BLADE_CHARM_MAX_COOLDOWN_TICKS = 20 * 30;

    private static final double BLADE_CHARM_RADIUS = 12.0D;

    // =====================================================================
    // Blade Charge
    // =====================================================================

    public static final int BLADE_CHARGE_COOLDOWN_TICKS = 20 * 12;

    private static final double BLADE_CHARGE_MIN_RANGE = 5.0D;
    private static final double BLADE_CHARGE_MAX_RANGE = 16.0D;

    private static final int BLADE_CHARGE_MAX_TICKS = 16;

    private static final double BLADE_CHARGE_SPEED = 1.34D;
    private static final float BLADE_CHARGE_IMPACT_DAMAGE = 8.0F;

    // =====================================================================
    // Dancing Swords
    // =====================================================================

    public static final int DANCING_SWORDS_DURATION_TICKS = 20 * 10;
    public static final int DANCING_SWORDS_COOLDOWN_TICKS = 20 * 28;

    private static final int DANCING_SWORD_COUNT = 6;

    /*
     * Slightly tighter than V1.
     *
     * This makes the swords feel more like Blade's personal defensive
     * perimeter instead of a loose decorative orbit.
     */
    private static final double DANCING_ORBIT_RADIUS = 1.45D;
    private static final double DANCING_ORBIT_HEIGHT = 0.78D;
    private static final double DANCING_ORBIT_SPEED = 0.20D;

    private static final double DANCING_TARGET_RANGE = 6.5D;
    private static final double DANCING_PROTECTION_RANGE = 3.25D;

    private static final int DANCING_FIRST_STRIKE_DELAY = 10;
    private static final int DANCING_STRIKE_INTERVAL = 14;

    private static final double DANCING_STRIKE_SPEED = 0.92D;
    private static final double DANCING_RETURN_SPEED = 0.82D;

    private static final float DANCING_SWORD_DAMAGE = 4.50F;
    private static final double DANCING_SWORD_KNOCKBACK = 0.35D;

    // =====================================================================
    // Rain of Swords
    // =====================================================================

    public static final int RAIN_OF_SWORDS_DURATION_TICKS = 20 * 15;
    public static final int RAIN_OF_SWORDS_COOLDOWN_TICKS = 20 * 65;

    private static final double RAIN_OF_SWORDS_RANGE = 22.0D;

    private static final int RAIN_BARRAGE_INTERVAL = 10;
    private static final int RAIN_SWORDS_PER_BARRAGE = 4;
    private static final int RAIN_MAX_ACTIVE_SWORDS = 24;

    private static final double RAIN_FALL_SPEED = 1.05D;

    /*
     * Rain is meant to look like swords falling from above, not missiles
     * homing diagonally through the air. Horizontal correction is therefore
     * deliberately much slower than vertical fall speed.
     */
    private static final double RAIN_HORIZONTAL_TRACK_SPEED = 0.18D;
    private static final double RAIN_HIT_RADIUS = 0.95D;

    private static final float RAIN_SWORD_DAMAGE = 5.5F;

    private static final int RAIN_SWORD_MAX_AGE = 35;

    /*
     * Proven hanging-tool ArmorStand pose:
     * hilt above, blade hanging downward in a vertical plane.
     *
     * The invisible carrier itself never becomes part of gameplay.
     */
    private static final Rotations RAIN_SWORD_ARM_POSE =
            new Rotations(
                    80.0F,
                    0.0F,
                    0.0F);

    // =====================================================================
    // Temporary sword manifestation ownership
    // =====================================================================

    private static final String BLADE_SWORD_TAG =
            "wolfism_blade_sword";

    // =====================================================================
    // Runtime
    // =====================================================================

    private int sharpSweepCooldownTicks;

    private int swordEnhancementCooldownTicks;
    private int swordEnhancementTicks;

    private int bladeCharmCooldownTicks;
    private int bladeCharmTicks;

    private int bladeChargeCooldownTicks;
    private int bladeChargeTicks;
    private int bladeChargeTargetId = -1;

    private int dancingSwordsCooldownTicks;
    private int dancingSwordsTicks;
    private int dancingNextStrikeTicks;
    private int dancingStrikeCursor;

    private int rainOfSwordsCooldownTicks;
    private int rainOfSwordsTicks;
    private int rainNextBarrageTicks;

    private boolean orphanSwordCleanupDone;

    private final List<TrackedSword> trackedSwords =
            new ArrayList<>();

    // =====================================================================
    // Brain
    // =====================================================================

    private static final class BrainHolder {

        private static final Brain.Provider<BladeWolf> PROVIDER =
                Brain.<BladeWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.BLADE_TACTICAL.get()),
                        wolf -> List.of());
    }

    public BladeWolf(
            EntityType<? extends BladeWolf> type,
            Level level) {

        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf>
    wolfismEntityType() {

        return ModEntities.BLADE_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {

        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
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

        float oldHealth =
                this.getHealth();

        maxHealth.setBaseValue(
                BASE_MAX_HEALTH);

        if (this.isTame()) {

            this.setHealth(
                    this.getMaxHealth());

        } else {

            this.setHealth(
                    Math.min(
                            oldHealth,
                            this.getMaxHealth()));
        }
    }

    // =====================================================================
    // Brain plumbing
    // =====================================================================

    @Override
    protected Brain<BladeWolf> makeBrain(
            Brain.Packed packedBrain) {

        return BrainHolder.PROVIDER.makeBrain(
                this,
                packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<BladeWolf> getBrain() {

        return (Brain<BladeWolf>)
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

        /*
         * Sword manifestations can survive a save/load even though
         * Blade's temporary runtime state does not.
         */
        if (!orphanSwordCleanupDone) {

            cleanupOrphanSwordManifestations(level);

            orphanSwordCleanupDone = true;
        }

        tickCooldowns();

        /*
         * Enhancement and Charm are timed buffs.
         *
         * They may expire naturally even after Blade receives a sit command.
         * Neither ability owns physical movement.
         */
        tickSwordEnhancement(level);
        tickBladeCharm(level);

        if (this.isBaby()) {

            clearAdultBladeStates(level);

            this.setTarget(null);

            return;
        }

        /*
         * ================================================================
         * OWNER SIT COMMAND
         * ================================================================
         *
         * Vanilla Wolf owns the real synchronized sitting pose.
         *
         * Blade never writes setInSittingPose(...) itself.
         */
        if (this.isOrderedToSit()) {

            enforceBladeSitCommand();

            cancelBladeCharge();

            if (dancingSwordsTicks > 0) {
                finishDancingSwords(level);
            }

            if (rainOfSwordsTicks > 0) {
                finishRainOfSwords(level);
            }

            if (!trackedSwords.isEmpty()) {
                discardAllSwordManifestations();
            }

            return;
        }

        /*
         * Offensive systems tick only after the owner-sit gate.
         */
        tickTrackedSwordManifestations(level);
        tickDancingSwords(level);
        tickRainOfSwords(level);
        tickBladeCharge(level);

        if (this.tickCount
                % DECISION_INTERVAL
                == 0) {

            tickBladeDecisionBrain(level);
        }
    }

    private void tickCooldowns() {

        if (sharpSweepCooldownTicks > 0) {
            --sharpSweepCooldownTicks;
        }

        if (swordEnhancementCooldownTicks > 0) {
            --swordEnhancementCooldownTicks;
        }

        if (bladeCharmCooldownTicks > 0) {
            --bladeCharmCooldownTicks;
        }

        if (bladeChargeCooldownTicks > 0) {
            --bladeChargeCooldownTicks;
        }

        if (dancingSwordsCooldownTicks > 0) {
            --dancingSwordsCooldownTicks;
        }

        if (rainOfSwordsCooldownTicks > 0) {
            --rainOfSwordsCooldownTicks;
        }
    }

    // =====================================================================
    // Sitting discipline
    // =====================================================================

    private boolean isBladeSitting() {

        return this.isOrderedToSit()
                || this.isInSittingPose();
    }

    private void enforceBladeSitCommand() {

        this.getNavigation().stop();

        if (this.getTarget() != null) {
            this.setTarget(null);
        }

        this.setSprinting(false);

        Vec3 movement =
                this.getDeltaMovement();

        if (Math.abs(movement.x) > 1.0E-4D
                || Math.abs(movement.z) > 1.0E-4D) {

            this.setDeltaMovement(
                    0.0D,
                    movement.y,
                    0.0D);

            this.hurtMarked = true;
        }
    }

    @Override
    public boolean canAttack(
            LivingEntity target) {

        if (this.isBaby()
                || isBladeSitting()
                || isBladeFamilyMember(target)) {

            return false;
        }

        return super.canAttack(target);
    }

    // =====================================================================
    // Decision Brain
    // =====================================================================

    private void tickBladeDecisionBrain(
            ServerLevel level) {

        if (isBladeSitting()) {
            return;
        }

        LivingEntity primary =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .BLADE_PRIMARY_TARGET
                                        .get())
                        .orElse(null);

        int localHostiles =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .BLADE_HOSTILE_COUNT
                                        .get())
                        .orElse(0);

        int clusteredHostiles =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .BLADE_CLUSTERED_COUNT
                                        .get())
                        .orElse(0);

        /*
         * Ultimate gets first battlefield priority.
         */
        if (rainOfSwordsTicks <= 0
                && rainOfSwordsCooldownTicks <= 0
                && dancingSwordsTicks <= 0
                && this.canUseActiveWolfismAbility()) {

            List<LivingEntity> rainThreats =
                    getNearbyBladeSpecialThreats(
                            level,
                            RAIN_OF_SWORDS_RANGE);

            if (rainThreats.size() >= 7
                    && startRainOfSwords(
                    level,
                    rainThreats)) {

                return;
            }
        }

        /*
         * Immediate close-range pressure.
         */
        if (rainOfSwordsTicks <= 0
                && dancingSwordsTicks <= 0
                && dancingSwordsCooldownTicks <= 0
                && clusteredHostiles >= 3
                && this.canUseActiveWolfismAbility()) {

            if (startDancingSwords(level)) {
                return;
            }
        }

        /*
         * Clustered enemies -> enhanced sweeping melee.
         */
        if (swordEnhancementTicks <= 0
                && swordEnhancementCooldownTicks <= 0
                && clusteredHostiles >= 3
                && this.canUseActiveWolfismAbility()) {

            if (startSwordEnhancement(level)) {
                return;
            }
        }

        /*
         * Active melee battle -> family inspiration.
         */
        if (bladeCharmTicks <= 0
                && bladeCharmCooldownTicks <= 0
                && localHostiles >= 2
                && this.canUseActiveWolfismAbility()) {

            if (startBladeCharm(level)) {
                return;
            }
        }

        /*
         * Target outside biting distance -> close the gap.
         */
        if (tryStartBladeCharge(
                level,
                primary)) {

            return;
        }

        /*
         * Ordinary focused melee.
         */
        if (primary != null
                && isValidBladeThreat(primary)
                && this.isWithinWolfismPhysicalAggroAcquireRange(
                primary)) {

            this.setTarget(primary);
        }
    }

    // =====================================================================
    // Sharp Bite
    // =====================================================================

    @Override
    public boolean doHurtTarget(
            ServerLevel level,
            Entity target) {

        if (this.isBaby()
                || isBladeSitting()) {

            return false;
        }

        boolean hit =
                super.doHurtTarget(
                        level,
                        target);

        if (!hit
                || !(target instanceof LivingEntity living)
                || isBladeFamilyMember(living)) {

            return hit;
        }

        float bonus =
                SHARP_BITE_BONUS_DAMAGE;

        if (swordEnhancementTicks > 0) {

            bonus +=
                    ENHANCED_BITE_EXTRA_DAMAGE;
        }

        living.hurtServer(
                level,
                this.damageSources()
                        .mobAttack(this),
                bonus);

        level.sendParticles(
                ParticleTypes.CRIT,
                living.getX(),
                living.getY()
                        + living.getBbHeight()
                        * 0.50D,
                living.getZ(),
                swordEnhancementTicks > 0
                        ? 12
                        : 7,
                0.26D,
                0.22D,
                0.26D,
                0.045D);

        if (swordEnhancementTicks > 0
                && sharpSweepCooldownTicks <= 0) {

            performEnhancedSweep(
                    level,
                    living);
        }

        return true;
    }

    private void performEnhancedSweep(
            ServerLevel level,
            LivingEntity primary) {

        List<LivingEntity> nearby =
                level.getEntitiesOfClass(
                                LivingEntity.class,
                                primary.getBoundingBox()
                                        .inflate(
                                                ENHANCED_SWEEP_RADIUS),
                                target ->
                                        target != primary
                                                && isValidBladeSpecialThreat(
                                                target))
                        .stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        primary::distanceToSqr))
                        .limit(
                                ENHANCED_SWEEP_MAX_TARGETS)
                        .toList();

        if (nearby.isEmpty()) {
            return;
        }

        sharpSweepCooldownTicks =
                ENHANCED_SWEEP_INTERNAL_COOLDOWN;

        for (LivingEntity target : nearby) {

            target.hurtServer(
                    level,
                    this.damageSources()
                            .mobAttack(this),
                    ENHANCED_SWEEP_DAMAGE);
        }

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                primary.getX(),
                primary.getY()
                        + primary.getBbHeight()
                        * 0.45D,
                primary.getZ(),
                3,
                0.55D,
                0.20D,
                0.55D,
                0.01D);
    }

    // =====================================================================
    // Sword Enhancement
    // =====================================================================

    private boolean startSwordEnhancement(
            ServerLevel level) {

        if (this.isBaby()
                || isBladeSitting()
                || swordEnhancementTicks > 0
                || swordEnhancementCooldownTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        swordEnhancementTicks =
                SWORD_ENHANCEMENT_DURATION_TICKS;

        swordEnhancementCooldownTicks =
                SWORD_ENHANCEMENT_COOLDOWN_TICKS;

        applySwordEnhancementEffects();

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                8,
                0.70D,
                0.35D,
                0.70D,
                0.02D);

        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                24,
                0.65D,
                0.40D,
                0.65D,
                0.06D);

        return true;
    }

    private void tickSwordEnhancement(
            ServerLevel level) {

        if (swordEnhancementTicks <= 0) {
            return;
        }

        --swordEnhancementTicks;

        if (swordEnhancementTicks % 10 == 0) {

            applySwordEnhancementEffects();
        }

        if (swordEnhancementTicks % 20 == 0) {

            level.sendParticles(
                    ParticleTypes.CRIT,
                    this.getX(),
                    this.getY() + 0.48D,
                    this.getZ(),
                    4,
                    0.35D,
                    0.22D,
                    0.35D,
                    0.025D);
        }
    }

    private void applySwordEnhancementEffects() {

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.STRENGTH,
                        30,
                        0,
                        true,
                        true));

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.SPEED,
                        30,
                        0,
                        true,
                        true));
    }

    // =====================================================================
    // Blade's Charm
    // =====================================================================

    private boolean startBladeCharm(
            ServerLevel level) {

        if (!this.isTame()
                || this.isBaby()
                || isBladeSitting()
                || bladeCharmTicks > 0
                || bladeCharmCooldownTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        List<LivingEntity> family =
                getNearbyBladeFamily(
                        level,
                        BLADE_CHARM_RADIUS);

        if (family.size() < 2) {
            return false;
        }

        bladeCharmTicks =
                BLADE_CHARM_DURATION_TICKS;

        int extraRecipients =
                Math.max(
                        0,
                        family.size() - 2);

        bladeCharmCooldownTicks =
                Math.min(
                        BLADE_CHARM_MAX_COOLDOWN_TICKS,
                        BLADE_CHARM_BASE_COOLDOWN_TICKS
                                + extraRecipients
                                * BLADE_CHARM_EXTRA_PER_RECIPIENT_TICKS);

        applyBladeCharmEffects(level);

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.60D,
                this.getZ(),
                32,
                1.10D,
                0.50D,
                1.10D,
                0.055D);

        return true;
    }

    private void tickBladeCharm(
            ServerLevel level) {

        if (bladeCharmTicks <= 0) {
            return;
        }

        --bladeCharmTicks;

        if (bladeCharmTicks % 10 == 0) {

            applyBladeCharmEffects(level);
        }
    }

    private void applyBladeCharmEffects(
            ServerLevel level) {

        for (LivingEntity family
                : getNearbyBladeFamily(
                level,
                BLADE_CHARM_RADIUS)) {

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.STRENGTH,
                            30,
                            0,
                            true,
                            true));

            family.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            30,
                            0,
                            true,
                            true));
        }
    }

    // =====================================================================
    // Blade Charge
    // =====================================================================

    private boolean tryStartBladeCharge(
            ServerLevel level,
            LivingEntity target) {

        if (bladeChargeCooldownTicks > 0
                || bladeChargeTicks > 0
                || target == null
                || !target.isAlive()
                || !isValidBladeSpecialThreat(target)
                || !this.hasLineOfSight(target)
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        double distanceSqr =
                this.distanceToSqr(target);

        if (distanceSqr
                < BLADE_CHARGE_MIN_RANGE
                * BLADE_CHARGE_MIN_RANGE
                || distanceSqr
                > BLADE_CHARGE_MAX_RANGE
                * BLADE_CHARGE_MAX_RANGE) {

            return false;
        }

        bladeChargeTicks =
                BLADE_CHARGE_MAX_TICKS;

        bladeChargeTargetId =
                target.getId();

        bladeChargeCooldownTicks =
                BLADE_CHARGE_COOLDOWN_TICKS;

        this.getNavigation().stop();
        this.setSprinting(true);

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                4,
                0.35D,
                0.20D,
                0.35D,
                0.02D);

        return true;
    }

    private void tickBladeCharge(
            ServerLevel level) {

        if (bladeChargeTicks <= 0) {
            return;
        }

        if (this.isBaby()
                || isBladeSitting()) {

            cancelBladeCharge();

            return;
        }

        --bladeChargeTicks;

        LivingEntity target =
                getLivingEntityById(
                        level,
                        bladeChargeTargetId);

        if (target == null
                || !target.isAlive()
                || !isValidBladeSpecialThreat(
                target)) {

            cancelBladeCharge();

            return;
        }

        Vec3 targetPoint =
                target.position()
                        .add(
                                0.0D,
                                target.getBbHeight()
                                        * 0.42D,
                                0.0D);

        Vec3 delta =
                targetPoint.subtract(
                        this.position());

        double distance =
                delta.length();

        if (distance <= 1.25D) {

            applyBladeChargeImpact(
                    level,
                    target);

            this.setTarget(target);

            cancelBladeCharge();

            return;
        }

        if (distance <= 0.001D) {

            cancelBladeCharge();

            return;
        }

        Vec3 step =
                delta.normalize()
                        .scale(
                                Math.min(
                                        BLADE_CHARGE_SPEED,
                                        distance));

        Vec3 next =
                this.position()
                        .add(step);

        if (familyOccupiesChargePath(
                level,
                next)) {

            cancelBladeCharge();

            return;
        }

        this.getNavigation().stop();
        this.setSprinting(true);

        Vec3 before =
                this.position();

        this.move(
                MoverType.SELF,
                step);

        Vec3 movement =
                this.getDeltaMovement();

        this.setDeltaMovement(
                0.0D,
                movement.y,
                0.0D);

        /*
         * Blade is not Dire Wolf.
         * Walls end the charge instead of being destroyed.
         */
        if (this.position()
                .distanceToSqr(before)
                < 0.04D) {

            cancelBladeCharge();

            return;
        }

        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.35D,
                this.getZ(),
                4,
                0.18D,
                0.12D,
                0.18D,
                0.02D);

        if (bladeChargeTicks <= 0) {

            cancelBladeCharge();
        }
    }

    private void applyBladeChargeImpact(
            ServerLevel level,
            LivingEntity target) {

        if (!isValidBladeSpecialThreat(target)) {
            return;
        }

        target.hurtServer(
                level,
                this.damageSources()
                        .mobAttack(this),
                BLADE_CHARGE_IMPACT_DAMAGE);

        double dx =
                this.getX()
                        - target.getX();

        double dz =
                this.getZ()
                        - target.getZ();

        target.knockback(
                0.65D,
                dx,
                dz);

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                target.getX(),
                target.getY()
                        + target.getBbHeight()
                        * 0.45D,
                target.getZ(),
                5,
                0.50D,
                0.25D,
                0.50D,
                0.02D);

        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY()
                        + target.getBbHeight()
                        * 0.45D,
                target.getZ(),
                18,
                0.42D,
                0.34D,
                0.42D,
                0.08D);
    }

    private void cancelBladeCharge() {

        bladeChargeTicks = 0;
        bladeChargeTargetId = -1;

        this.setSprinting(false);

        Vec3 movement =
                this.getDeltaMovement();

        /*
         * Remove horizontal charge momentum but preserve gravity.
         */
        this.setDeltaMovement(
                0.0D,
                movement.y,
                0.0D);
    }

    private boolean familyOccupiesChargePath(
            ServerLevel level,
            Vec3 position) {

        AABB area =
                new AABB(
                        position.x - 0.70D,
                        position.y - 0.40D,
                        position.z - 0.70D,
                        position.x + 0.70D,
                        position.y + 1.20D,
                        position.z + 0.70D);

        return !level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        entity ->
                                entity != this
                                        && isBladeFamilyMember(
                                        entity))
                .isEmpty();
    }

    // =====================================================================
    // Dancing Swords
    // =====================================================================

    private boolean startDancingSwords(
            ServerLevel level) {

        if (this.isBaby()
                || isBladeSitting()
                || dancingSwordsTicks > 0
                || dancingSwordsCooldownTicks > 0
                || rainOfSwordsTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        List<LivingEntity> threats =
                getNearbyBladeSpecialThreats(
                        level,
                        DANCING_TARGET_RANGE);

        if (threats.size() < 2) {
            return false;
        }

        dancingSwordsTicks =
                DANCING_SWORDS_DURATION_TICKS;

        dancingSwordsCooldownTicks =
                DANCING_SWORDS_COOLDOWN_TICKS;

        dancingNextStrikeTicks =
                DANCING_FIRST_STRIKE_DELAY;

        dancingStrikeCursor = 0;

        /*
         * Defensive value begins immediately.
         */
        applyDancingSwordGuard();

        for (int i = 0;
             i < DANCING_SWORD_COUNT;
             ++i) {

            spawnDancingSword(
                    level,
                    i);
        }

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.72D,
                this.getZ(),
                36,
                1.05D,
                0.45D,
                1.05D,
                0.055D);

        return true;
    }

    private void tickDancingSwords(
            ServerLevel level) {

        if (dancingSwordsTicks <= 0) {
            return;
        }

        if (isBladeSitting()) {

            finishDancingSwords(level);

            return;
        }

        --dancingSwordsTicks;
        --dancingNextStrikeTicks;

        /*
         * The orbit acts as a light physical guard around Blade.
         *
         * Resistance I is intentionally mild; Dancing Swords remains an
         * offensive/control ability rather than turning Blade into a tank.
         */
        if (dancingSwordsTicks % 10 == 0) {

            applyDancingSwordGuard();
        }

        if (dancingNextStrikeTicks <= 0) {

            releaseDancingSword(level);

            dancingNextStrikeTicks =
                    DANCING_STRIKE_INTERVAL;
        }

        if (dancingSwordsTicks <= 0) {

            finishDancingSwords(level);
        }
    }

    private void applyDancingSwordGuard() {

        this.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        25,
                        0,
                        true,
                        true));
    }

    private void spawnDancingSword(
            ServerLevel level,
            int orbitIndex) {

        double angle =
                orbitAngle(
                        orbitIndex);

        Vec3 pos =
                currentOrbitPosition(
                        orbitIndex,
                        angle);

        Display.ItemDisplay display =
                createSwordDisplay(
                        level,
                        pos);

        display.setXRot(-90.0F);

        display.setYRot(
                (float)
                        Math.toDegrees(angle));

        trackedSwords.add(
                new TrackedSword(
                        display.getId(),
                        orbitIndex,
                        SwordMode.DANCING_ORBIT,
                        -1));
    }

    private void releaseDancingSword(
            ServerLevel level) {

        LivingEntity target =
                findBestDancingSwordTarget(
                        level);

        if (target == null) {
            return;
        }

        List<TrackedSword> orbiters =
                trackedSwords.stream()
                        .filter(
                                state ->
                                        state.mode
                                                == SwordMode.DANCING_ORBIT)
                        .sorted(
                                Comparator.comparingInt(
                                        state ->
                                                state.orbitIndex))
                        .toList();

        if (orbiters.isEmpty()) {
            return;
        }

        TrackedSword sword =
                orbiters.get(
                        dancingStrikeCursor
                                % orbiters.size());

        ++dancingStrikeCursor;

        sword.mode =
                SwordMode.DANCING_STRIKE;

        sword.targetId =
                target.getId();

        sword.age = 0;
    }

    private LivingEntity findBestDancingSwordTarget(
            ServerLevel level) {

        return getNearbyBladeSpecialThreats(
                level,
                DANCING_TARGET_RANGE)
                .stream()
                .min(
                        Comparator.comparingDouble(
                                this::dancingSwordThreatScore))
                .orElse(null);
    }

    private double dancingSwordThreatScore(
            LivingEntity target) {

        /*
         * Start from Blade's general direct-pressure intelligence.
         */
        double score =
                bladeThreatScore(target);

        double distanceSqr =
                this.distanceToSqr(target);

        /*
         * Anything entering the immediate sword perimeter becomes highly
         * desirable. This is the "protection" part of Dancing Swords.
         */
        if (distanceSqr
                <= DANCING_PROTECTION_RANGE
                * DANCING_PROTECTION_RANGE) {

            score -= 90.0D;
        }

        /*
         * A hostile actively threatening family gets intercepted first.
         */
        if (isThreateningBladeFamily(target)) {

            score -= 140.0D;
        }

        /*
         * Maintain pressure on Blade's current melee opponent.
         */
        if (target == this.getTarget()) {

            score -= 35.0D;
        }

        return score;
    }

    private void finishDancingSwords(
            ServerLevel level) {

        dancingSwordsTicks = 0;
        dancingNextStrikeTicks = 0;

        discardSwordModes(
                level,
                SwordMode.DANCING_ORBIT,
                SwordMode.DANCING_STRIKE,
                SwordMode.DANCING_RETURN);
    }

    // =====================================================================
    // Rain of Swords
    // =====================================================================

    private boolean startRainOfSwords(
            ServerLevel level,
            List<LivingEntity> initialThreats) {

        if (this.isBaby()
                || isBladeSitting()
                || rainOfSwordsTicks > 0
                || rainOfSwordsCooldownTicks > 0
                || dancingSwordsTicks > 0
                || initialThreats.size() < 7
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        rainOfSwordsTicks =
                RAIN_OF_SWORDS_DURATION_TICKS;

        rainOfSwordsCooldownTicks =
                RAIN_OF_SWORDS_COOLDOWN_TICKS;

        rainNextBarrageTicks = 0;

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                12,
                1.25D,
                0.55D,
                1.25D,
                0.035D);

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.75D,
                this.getZ(),
                72,
                1.80D,
                0.80D,
                1.80D,
                0.075D);

        return true;
    }

    private void tickRainOfSwords(
            ServerLevel level) {

        if (rainOfSwordsTicks <= 0) {
            return;
        }

        if (isBladeSitting()) {

            finishRainOfSwords(level);

            return;
        }

        --rainOfSwordsTicks;
        --rainNextBarrageTicks;

        if (rainNextBarrageTicks <= 0) {

            spawnRainBarrage(level);

            rainNextBarrageTicks =
                    RAIN_BARRAGE_INTERVAL;
        }

        if (rainOfSwordsTicks % 20 == 0) {

            level.sendParticles(
                    ParticleTypes.CRIT,
                    this.getX(),
                    this.getY() + 0.55D,
                    this.getZ(),
                    10,
                    0.65D,
                    0.35D,
                    0.65D,
                    0.025D);
        }

        if (rainOfSwordsTicks <= 0) {

            finishRainOfSwords(level);
        }
    }

    private void spawnRainBarrage(
            ServerLevel level) {

        long active =
                trackedSwords.stream()
                        .filter(
                                state ->
                                        state.mode
                                                == SwordMode.RAIN_FALL)
                        .count();

        int budget =
                Math.max(
                        0,
                        RAIN_MAX_ACTIVE_SWORDS
                                - (int) active);

        if (budget <= 0) {
            return;
        }

        List<LivingEntity> targets =
                getNearbyBladeSpecialThreats(
                        level,
                        RAIN_OF_SWORDS_RANGE)
                        .stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        this::bladeThreatScore))
                        .toList();

        if (targets.isEmpty()) {
            return;
        }

        int count =
                Math.min(
                        RAIN_SWORDS_PER_BARRAGE,
                        budget);

        for (int i = 0;
             i < count;
             ++i) {

            LivingEntity target =
                    targets.get(
                            i % targets.size());

            spawnRainSword(
                    level,
                    target);
        }
    }

    private void spawnRainSword(
            ServerLevel level,
            LivingEntity target) {

        double spreadX =
                (this.random.nextDouble()
                        - 0.5D)
                        * 3.2D;

        double spreadZ =
                (this.random.nextDouble()
                        - 0.5D)
                        * 3.2D;

        Vec3 start =
                target.position()
                        .add(
                                spreadX,
                                target.getBbHeight()
                                        + 7.0D,
                                spreadZ);

        BladeRainSwordCarrier carrier =
                createRainSwordCarrier(
                        level,
                        start);

        /*
         * Keep a little rotational variety around the vertical axis.
         *
         * The local sword orientation itself is controlled by the ArmorStand
         * right-arm pose, so yaw cannot lay the sword flat.
         */
        float yaw =
                this.random.nextFloat()
                        * 360.0F;

        carrier.setYRot(yaw);
        carrier.setYBodyRot(yaw);
        carrier.setYHeadRot(yaw);

        trackedSwords.add(
                new TrackedSword(
                        carrier.getId(),
                        0,
                        SwordMode.RAIN_FALL,
                        target.getId()));

        level.sendParticles(
                ParticleTypes.CRIT,
                start.x,
                start.y,
                start.z,
                3,
                0.10D,
                0.10D,
                0.10D,
                0.01D);
    }

    private void finishRainOfSwords(
            ServerLevel level) {

        rainOfSwordsTicks = 0;
        rainNextBarrageTicks = 0;

        discardSwordModes(
                level,
                SwordMode.RAIN_FALL);
    }

    // =====================================================================
    // Sword manifestation controller
    // =====================================================================

    private Display.ItemDisplay createSwordDisplay(
            ServerLevel level,
            Vec3 pos) {

        Display.ItemDisplay display =
                new Display.ItemDisplay(
                        EntityType.ITEM_DISPLAY,
                        level);

        SlotAccess slot =
                display.getSlot(
                        Entity.CONTENTS_SLOT_INDEX);

        if (slot != null) {

            slot.set(
                    new ItemStack(
                            Items.IRON_SWORD));
        }

        display.setNoGravity(true);

        display.setPos(
                pos.x,
                pos.y,
                pos.z);

        display.addTag(
                BLADE_SWORD_TAG);

        display.addTag(
                swordOwnerTag());

        level.addFreshEntity(display);

        return display;
    }

    private BladeRainSwordCarrier createRainSwordCarrier(
            ServerLevel level,
            Vec3 pos) {

        BladeRainSwordCarrier carrier =
                new BladeRainSwordCarrier(level);

        /*
         * Vanilla invisible ArmorStand display technique:
         *
         * - the stand/body is invisible;
         * - the held vanilla iron sword remains visible;
         * - ShowArms enables the hand transform;
         * - the right-arm pose supplies the local roll/pitch ItemDisplay could
         *   not give us safely in 26.1;
         * - no custom item model/resource JSON is involved.
         */
        carrier.setInvisible(true);
        carrier.setShowArms(true);
        carrier.setNoBasePlate(true);

        carrier.setNoGravity(true);
        carrier.setInvulnerable(true);
        carrier.setSilent(true);

        carrier.noPhysics = true;

        carrier.setRightArmPose(
                RAIN_SWORD_ARM_POSE);

        carrier.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(
                        Items.IRON_SWORD));

        carrier.setPos(
                pos.x,
                pos.y,
                pos.z);

        carrier.addTag(
                BLADE_SWORD_TAG);

        carrier.addTag(
                swordOwnerTag());

        level.addFreshEntity(carrier);

        return carrier;
    }

    private void tickTrackedSwordManifestations(
            ServerLevel level) {

        Iterator<TrackedSword> iterator =
                trackedSwords.iterator();

        while (iterator.hasNext()) {

            TrackedSword state =
                    iterator.next();

            Entity entity =
                    level.getEntity(
                            state.entityId);

            if (entity == null
                    || !entity.isAlive()) {

                iterator.remove();

                continue;
            }

            ++state.age;

            switch (state.mode) {

                case DANCING_ORBIT,
                     DANCING_STRIKE,
                     DANCING_RETURN -> {

                    if (!(entity
                            instanceof Display.ItemDisplay display)) {

                        entity.discard();
                        iterator.remove();

                        continue;
                    }

                    switch (state.mode) {

                        case DANCING_ORBIT ->
                                tickDancingOrbit(
                                        level,
                                        display,
                                        state);

                        case DANCING_STRIKE ->
                                tickDancingStrike(
                                        level,
                                        display,
                                        state);

                        case DANCING_RETURN ->
                                tickDancingReturn(
                                        level,
                                        display,
                                        state);

                        default -> {
                        }
                    }
                }

                case RAIN_FALL -> {

                    if (!(entity
                            instanceof ArmorStand carrier)) {

                        entity.discard();
                        iterator.remove();

                        continue;
                    }

                    if (tickRainSword(
                            level,
                            carrier,
                            state)) {

                        iterator.remove();
                    }
                }
            }
        }
    }

    private void tickDancingOrbit(
            ServerLevel level,
            Display.ItemDisplay display,
            TrackedSword state) {

        if (dancingSwordsTicks <= 0) {
            return;
        }

        double angle =
                orbitAngle(
                        state.orbitIndex);

        Vec3 desired =
                currentOrbitPosition(
                        state.orbitIndex,
                        angle);

        display.setPos(
                desired.x,
                desired.y,
                desired.z);

        display.setXRot(-90.0F);

        display.setYRot(
                (float)
                        Math.toDegrees(angle));

        if ((this.tickCount
                + state.orbitIndex * 3)
                % 12 == 0) {

            level.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    desired.x,
                    desired.y,
                    desired.z,
                    1,
                    0.02D,
                    0.02D,
                    0.02D,
                    0.0D);
        }
    }

    private void tickDancingStrike(
            ServerLevel level,
            Display.ItemDisplay display,
            TrackedSword state) {

        LivingEntity target =
                getLivingEntityById(
                        level,
                        state.targetId);

        if (target == null
                || !target.isAlive()
                || !isValidBladeSpecialThreat(
                target)
                || this.distanceToSqr(target)
                > DANCING_TARGET_RANGE
                * DANCING_TARGET_RANGE
                * 1.50D) {

            state.mode =
                    SwordMode.DANCING_RETURN;

            state.targetId = -1;
            state.age = 0;

            return;
        }

        Vec3 targetPoint =
                target.position()
                        .add(
                                0.0D,
                                target.getBbHeight()
                                        * 0.50D,
                                0.0D);

        Vec3 delta =
                targetPoint.subtract(
                        display.position());

        double distance =
                delta.length();

        if (distance <= 0.70D) {

            target.hurtServer(
                    level,
                    this.damageSources()
                            .mobAttack(this),
                    DANCING_SWORD_DAMAGE);

            /*
             * Small spacing knockback helps the swords actually defend Blade's
             * immediate fighting space.
             */
            double dx =
                    this.getX()
                            - target.getX();

            double dz =
                    this.getZ()
                            - target.getZ();

            target.knockback(
                    DANCING_SWORD_KNOCKBACK,
                    dx,
                    dz);

            level.sendParticles(
                    ParticleTypes.SWEEP_ATTACK,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight()
                            * 0.50D,
                    target.getZ(),
                    1,
                    0.20D,
                    0.15D,
                    0.20D,
                    0.01D);

            level.sendParticles(
                    ParticleTypes.CRIT,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight()
                            * 0.50D,
                    target.getZ(),
                    9,
                    0.25D,
                    0.22D,
                    0.25D,
                    0.05D);

            state.mode =
                    SwordMode.DANCING_RETURN;

            state.targetId = -1;
            state.age = 0;

            return;
        }

        if (distance <= 0.001D) {

            state.mode =
                    SwordMode.DANCING_RETURN;

            state.targetId = -1;
            state.age = 0;

            return;
        }

        Vec3 step =
                delta.normalize()
                        .scale(
                                Math.min(
                                        DANCING_STRIKE_SPEED,
                                        distance));

        Vec3 next =
                display.position()
                        .add(step);

        if (!isSwordPointPassable(
                level,
                next)) {

            state.mode =
                    SwordMode.DANCING_RETURN;

            state.targetId = -1;
            state.age = 0;

            return;
        }

        display.setPos(
                next.x,
                next.y,
                next.z);

        orientSwordToward(
                display,
                step);
    }

    private void tickDancingReturn(
            ServerLevel level,
            Display.ItemDisplay display,
            TrackedSword state) {

        double angle =
                orbitAngle(
                        state.orbitIndex);

        Vec3 desired =
                currentOrbitPosition(
                        state.orbitIndex,
                        angle);

        Vec3 delta =
                desired.subtract(
                        display.position());

        double distance =
                delta.length();

        if (distance <= 0.18D
                || state.age > 26) {

            display.setPos(
                    desired.x,
                    desired.y,
                    desired.z);

            state.mode =
                    SwordMode.DANCING_ORBIT;

            state.age = 0;

            return;
        }

        Vec3 step =
                delta.normalize()
                        .scale(
                                Math.min(
                                        DANCING_RETURN_SPEED,
                                        distance));

        Vec3 next =
                display.position()
                        .add(step);

        display.setPos(
                next.x,
                next.y,
                next.z);

        orientSwordToward(
                display,
                step);
    }

    /**
     * @return true when the rain sword has been consumed/discarded.
     */
    private boolean tickRainSword(
            ServerLevel level,
            ArmorStand carrier,
            TrackedSword state) {

        LivingEntity target =
                getLivingEntityById(
                        level,
                        state.targetId);

        if (target == null
                || !target.isAlive()
                || !isValidBladeSpecialThreat(
                target)) {

            target =
                    findBestBladeSpecialTarget(
                            level,
                            RAIN_OF_SWORDS_RANGE);

            if (target == null) {

                carrier.discard();

                return true;
            }

            state.targetId =
                    target.getId();
        }

        if (state.age
                > RAIN_SWORD_MAX_AGE) {

            carrier.discard();

            return true;
        }

        double dx =
                target.getX()
                        - carrier.getX();

        double dz =
                target.getZ()
                        - carrier.getZ();

        double horizontalDistanceSqr =
                dx * dx
                        + dz * dz;

        /*
         * The carrier position is the hidden ArmorStand's feet, while the
         * visible sword is held above that anchor. A generous vertical window
         * makes impact line up naturally with the falling held item instead of
         * requiring the invisible stand's origin to enter the mob.
         */
        boolean inVerticalStrikeWindow =
                carrier.getY()
                        <= target.getY()
                        + target.getBbHeight()
                        + 0.70D
                        && carrier.getY()
                        >= target.getY()
                        - 0.80D;

        if (horizontalDistanceSqr
                <= RAIN_HIT_RADIUS
                * RAIN_HIT_RADIUS
                && inVerticalStrikeWindow) {

            target.hurtServer(
                    level,
                    this.damageSources()
                            .mobAttack(this),
                    RAIN_SWORD_DAMAGE);

            level.sendParticles(
                    ParticleTypes.SWEEP_ATTACK,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight()
                            * 0.35D,
                    target.getZ(),
                    3,
                    0.32D,
                    0.18D,
                    0.32D,
                    0.01D);

            level.sendParticles(
                    ParticleTypes.CRIT,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight()
                            * 0.35D,
                    target.getZ(),
                    9,
                    0.27D,
                    0.22D,
                    0.27D,
                    0.045D);

            carrier.discard();

            return true;
        }

        /*
         * If the sword has already passed well below the target without
         * connecting, clean it up instead of letting the hidden carrier drift.
         */
        if (carrier.getY()
                < target.getY()
                - 1.35D) {

            carrier.discard();

            return true;
        }

        double horizontalDistance =
                Math.sqrt(
                        horizontalDistanceSqr);

        double stepX = 0.0D;
        double stepZ = 0.0D;

        /*
         * Small tracking correction keeps moving targets hittable while the
         * dominant motion stays visibly DOWNWARD like actual sword rain.
         */
        if (horizontalDistance > 0.001D) {

            double correction =
                    Math.min(
                            RAIN_HORIZONTAL_TRACK_SPEED,
                            horizontalDistance);

            stepX =
                    dx
                            / horizontalDistance
                            * correction;

            stepZ =
                    dz
                            / horizontalDistance
                            * correction;
        }

        Vec3 step =
                new Vec3(
                        stepX,
                        -RAIN_FALL_SPEED,
                        stepZ);

        Vec3 next =
                carrier.position()
                        .add(step);

        /*
         * No phasing through roofs and absolutely no terrain griefing.
         */
        if (!isSwordPointPassable(
                level,
                next)) {

            carrier.discard();

            return true;
        }

        carrier.setPos(
                next.x,
                next.y,
                next.z);

        /*
         * Never rotate Rain from its movement vector.
         *
         * The right-arm pose permanently owns the hanging blade orientation.
         */
        if (state.age % 2 == 0) {

            level.sendParticles(
                    ParticleTypes.CRIT,
                    carrier.getX(),
                    carrier.getY()
                            + 1.15D,
                    carrier.getZ(),
                    1,
                    0.02D,
                    0.02D,
                    0.02D,
                    0.0D);
        }

        return false;
    }

    private double orbitAngle(
            int orbitIndex) {

        return this.tickCount
                * DANCING_ORBIT_SPEED
                + orbitIndex
                * (Math.PI * 2.0D
                / DANCING_SWORD_COUNT);
    }

    private Vec3 currentOrbitPosition(
            int orbitIndex,
            double angle) {

        double bob =
                Math.sin(
                        this.tickCount
                                * 0.16D
                                + orbitIndex)
                        * 0.08D;

        return this.position()
                .add(
                        Math.cos(angle)
                                * DANCING_ORBIT_RADIUS,
                        DANCING_ORBIT_HEIGHT
                                + bob,
                        Math.sin(angle)
                                * DANCING_ORBIT_RADIUS);
    }

    private void orientSwordToward(
            Display.ItemDisplay display,
            Vec3 movement) {

        double horizontal =
                Math.sqrt(
                        movement.x * movement.x
                                + movement.z * movement.z);

        display.setYRot(
                (float)
                        (Math.atan2(
                                movement.x,
                                movement.z)
                                * -90.0F
                                / Math.PI));

        display.setXRot(
                (float)
                        (-Math.atan2(
                                movement.y,
                                horizontal)
                                * -90.0F
                                / Math.PI));
    }

    private boolean isSwordPointPassable(
            ServerLevel level,
            Vec3 point) {

        var pos =
                net.minecraft.core.BlockPos.containing(
                        point.x,
                        point.y,
                        point.z);

        return level.getBlockState(pos)
                .getCollisionShape(
                        level,
                        pos)
                .isEmpty();
    }

    private void discardSwordModes(
            ServerLevel level,
            SwordMode... modes) {

        List<SwordMode> accepted =
                List.of(modes);

        Iterator<TrackedSword> iterator =
                trackedSwords.iterator();

        while (iterator.hasNext()) {

            TrackedSword state =
                    iterator.next();

            if (!accepted.contains(
                    state.mode)) {

                continue;
            }

            Entity entity =
                    level.getEntity(
                            state.entityId);

            if (entity != null) {
                entity.discard();
            }

            iterator.remove();
        }
    }

    private void cleanupOrphanSwordManifestations(
            ServerLevel level) {

        String ownerTag =
                swordOwnerTag();

        AABB cleanupArea =
                this.getBoundingBox()
                        .inflate(
                                64.0D,
                                32.0D,
                                64.0D);

        /*
         * Dancing Sword orphans.
         */
        List<Display.ItemDisplay> displayOrphans =
                level.getEntitiesOfClass(
                        Display.ItemDisplay.class,
                        cleanupArea,
                        display ->
                                hasBladeSwordOwnerTags(
                                        display,
                                        ownerTag));

        for (Display.ItemDisplay display
                : displayOrphans) {

            display.discard();
        }

        /*
         * Rain of Swords orphans.
         *
         * These may reload as ordinary vanilla ArmorStands because their saved
         * EntityType is minecraft:armor_stand, so cleanup deliberately checks
         * ArmorStand rather than only our runtime subclass.
         */
        List<ArmorStand> carrierOrphans =
                level.getEntitiesOfClass(
                        ArmorStand.class,
                        cleanupArea,
                        carrier ->
                                hasBladeSwordOwnerTags(
                                        carrier,
                                        ownerTag));

        for (ArmorStand carrier
                : carrierOrphans) {

            carrier.discard();
        }

        trackedSwords.clear();
    }

    private boolean hasBladeSwordOwnerTags(
            Entity entity,
            String ownerTag) {

        return entity.entityTags()
                .contains(
                        BLADE_SWORD_TAG)
                && entity.entityTags()
                .contains(
                        ownerTag);
    }

    private void discardAllSwordManifestations() {

        if (!(this.level()
                instanceof ServerLevel level)) {

            trackedSwords.clear();

            return;
        }

        for (TrackedSword state
                : trackedSwords) {

            Entity entity =
                    level.getEntity(
                            state.entityId);

            if (entity != null) {
                entity.discard();
            }
        }

        trackedSwords.clear();

        /*
         * Catch anything saved/reloaded or otherwise detached from the runtime
         * tracking list.
         */
        cleanupOrphanSwordManifestations(level);
    }

    private String swordOwnerTag() {

        return "wolfism_blade_owner_"
                + this.getUUID();
    }

    // =====================================================================
    // Threat intelligence
    // =====================================================================

    public double bladeThreatScore(
            LivingEntity target) {

        if (target == null) {
            return Double.MAX_VALUE;
        }

        /*
         * Lower = better target.
         */
        double score =
                Math.sqrt(
                        this.distanceToSqr(
                                target));

        if (target
                == this.getFamilyDefenseTarget()) {

            score -= 450.0D;
        }

        if (isThreateningBladeFamily(
                target)) {

            score -= 230.0D;
        }

        /*
         * Blade likes durable frontline targets because direct sustained melee
         * pressure has high battlefield value against them.
         */
        score -=
                Math.min(
                        28.0D,
                        target.getMaxHealth()
                                * 0.10D);

        score -=
                Math.min(
                        16.0D,
                        target.getArmorValue()
                                * 0.70D);

        if (target instanceof WitherBoss
                || target instanceof EnderDragon) {

            score -= 55.0D;
        }

        return score;
    }

    public boolean isThreateningBladeFamily(
            LivingEntity target) {

        if (!(target instanceof Mob mob)) {
            return false;
        }

        LivingEntity victim =
                mob.getTarget();

        return victim != null
                && isBladeFamilyMember(
                victim);
    }

    public boolean isValidBladeThreat(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target == this
                || isBladeFamilyMember(target)) {

            return false;
        }

        if (target
                == this.getFamilyDefenseTarget()
                || isThreateningBladeFamily(
                target)) {

            return true;
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

    /**
     * Blade's special gap-closing/sword manifestations deliberately exclude
     * Creepers so Wolfism's existing Creeper fuse/retreat coordinator remains
     * authoritative.
     */
    public boolean isValidBladeSpecialThreat(
            LivingEntity target) {

        return isValidBladeThreat(target)
                && !(target instanceof Creeper);
    }

    public List<LivingEntity>
    getNearbyBladeThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr =
                radius * radius;

        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox()
                                .inflate(radius),
                        this::isValidBladeThreat)
                .stream()
                .filter(
                        target ->
                                this.distanceToSqr(
                                        target)
                                        <= radiusSqr)
                .toList();
    }

    public List<LivingEntity>
    getNearbyBladeSpecialThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr =
                radius * radius;

        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox()
                                .inflate(radius),
                        this::isValidBladeSpecialThreat)
                .stream()
                .filter(
                        target ->
                                this.distanceToSqr(
                                        target)
                                        <= radiusSqr)
                .toList();
    }

    private LivingEntity findBestBladeSpecialTarget(
            ServerLevel level,
            double radius) {

        return getNearbyBladeSpecialThreats(
                level,
                radius)
                .stream()
                .min(
                        Comparator.comparingDouble(
                                this::bladeThreatScore))
                .orElse(null);
    }

    // =====================================================================
    // Family
    // =====================================================================

    public boolean isBladeFamilyMember(
            LivingEntity entity) {

        if (entity == null
                || !entity.isAlive()) {

            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {

            return entity
                    instanceof BladeWolf blade
                    && !blade.isTame();
        }

        if (entity == this.getOwner()) {
            return true;
        }

        /*
         * Wolfism family rule:
         * all tamed wolves are protected family.
         */
        return entity
                instanceof Wolf wolf
                && wolf.isTame();
    }

    private List<LivingEntity>
    getNearbyBladeFamily(
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

    // =====================================================================
    // Utility / cleanup
    // =====================================================================

    private LivingEntity getLivingEntityById(
            ServerLevel level,
            int id) {

        if (id < 0) {
            return null;
        }

        return level.getEntity(id)
                instanceof LivingEntity living
                ? living
                : null;
    }

    private void clearAdultBladeStates(
            ServerLevel level) {

        cancelBladeCharge();

        swordEnhancementTicks = 0;
        bladeCharmTicks = 0;

        dancingSwordsTicks = 0;
        dancingNextStrikeTicks = 0;

        rainOfSwordsTicks = 0;
        rainNextBarrageTicks = 0;

        discardAllSwordManifestations();
    }

    public boolean isBladeCombatVisualActive() {

        return swordEnhancementTicks > 0
                || bladeChargeTicks > 0
                || dancingSwordsTicks > 0
                || rainOfSwordsTicks > 0
                || !trackedSwords.isEmpty();
    }

    @Override
    public void remove(
            Entity.RemovalReason reason) {

        discardAllSwordManifestations();

        super.remove(reason);
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output) {

        super.addAdditionalSaveData(output);

        output.putInt(
                "BladeEnhancementCooldown",
                swordEnhancementCooldownTicks);

        output.putInt(
                "BladeCharmCooldown",
                bladeCharmCooldownTicks);

        output.putInt(
                "BladeChargeCooldown",
                bladeChargeCooldownTicks);

        output.putInt(
                "BladeDancingCooldown",
                dancingSwordsCooldownTicks);

        output.putInt(
                "BladeRainCooldown",
                rainOfSwordsCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {

        super.readAdditionalSaveData(input);

        swordEnhancementCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "BladeEnhancementCooldown",
                                0));

        bladeCharmCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "BladeCharmCooldown",
                                0));

        bladeChargeCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "BladeChargeCooldown",
                                0));

        dancingSwordsCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "BladeDancingCooldown",
                                0));

        rainOfSwordsCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "BladeRainCooldown",
                                0));

        /*
         * Cooldowns persist.
         * Temporary active states do not.
         */
        sharpSweepCooldownTicks = 0;

        swordEnhancementTicks = 0;
        bladeCharmTicks = 0;

        bladeChargeTicks = 0;
        bladeChargeTargetId = -1;

        dancingSwordsTicks = 0;
        dancingNextStrikeTicks = 0;
        dancingStrikeCursor = 0;

        rainOfSwordsTicks = 0;
        rainNextBarrageTicks = 0;

        trackedSwords.clear();

        orphanSwordCleanupDone = false;
    }

    // =====================================================================
    // Rain sword carrier
    // =====================================================================

    /**
     * Server-side invisible ArmorStand carrier used only by Rain of Swords.
     *
     * Its EntityType remains vanilla minecraft:armor_stand, so the vanilla
     * client renderer knows exactly how to render the held iron sword. The
     * subclass only exists server-side to make the carrier impossible to
     * attack or interact with while the ability is active.
     */
    private static final class BladeRainSwordCarrier
            extends ArmorStand {

        private BladeRainSwordCarrier(
                Level level) {

            super(
                    EntityType.ARMOR_STAND,
                    level);
        }

        @Override
        public boolean isPickable() {

            return false;
        }

        @Override
        public InteractionResult interact(
                Player player,
                InteractionHand hand,
                Vec3 location) {

            /*
             * Never expose the held sword to ArmorStand inventory swapping.
             */
            return InteractionResult.FAIL;
        }

        @Override
        public boolean hurtServer(
                ServerLevel level,
                DamageSource source,
                float damage) {

            /*
             * Ability carrier, not a combat entity.
             */
            return false;
        }
    }

    public static boolean checkBladeWolfSpawnRules(
            EntityType<BladeWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Spawn eggs, commands, breeding, etc. are never restricted by
         * natural-spawn rules.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {

            return true;
        }

        BlockPos below =
                pos.below();

        /*
         * Use the vanilla wolf-compatible terrain rule.
         */
        if (!level.getBlockState(below)
                .is(BlockTags.WOLVES_SPAWNABLE_ON)) {

            return false;
        }

        /*
         * Blade must have real physical space.
         */
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {

            return false;
        }

        AABB body =
                type.getSpawnAABB(
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D);

        if (!level.noCollision(body)) {
            return false;
        }

        /*
         * Blade Wolves are solitary natural spawns.
         *
         * Tamed Blade Wolves intentionally do not suppress natural spawning.
         */
        if (level instanceof ServerLevel serverLevel) {

            AABB territory =
                    new AABB(pos)
                            .inflate(
                                    64.0D,
                                    32.0D,
                                    64.0D);

            boolean anotherWildBlade =
                    !serverLevel.getEntitiesOfClass(
                                    BladeWolf.class,
                                    territory,
                                    blade ->
                                            blade.isAlive()
                                                    && !blade.isTame())
                            .isEmpty();

            if (anotherWildBlade) {
                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Sword runtime
    // =====================================================================

    private enum SwordMode {

        DANCING_ORBIT,
        DANCING_STRIKE,
        DANCING_RETURN,
        RAIN_FALL
    }

    private static final class TrackedSword {

        private final int entityId;
        private final int orbitIndex;

        private SwordMode mode;

        private int targetId;
        private int age;

        private TrackedSword(
                int entityId,
                int orbitIndex,
                SwordMode mode,
                int targetId) {

            this.entityId =
                    entityId;

            this.orbitIndex =
                    orbitIndex;

            this.mode =
                    mode;

            this.targetId =
                    targetId;
        }
    }
}
