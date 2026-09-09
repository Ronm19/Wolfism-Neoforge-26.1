package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
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
import net.ronm19.wolfism.tag.ModStructureTags;

/**
 * Illager Wolf ♂
 *
 * Physical / Ranged-Control / Anti-Armor / Raider Warfare specialist.
 */
public final class IllagerWolf extends AbstractWolfismWolf {

    // =====================================================================
    // Baseline
    // =====================================================================

    private static final double BASE_MAX_HEALTH = 50.0D;

    private static final int DECISION_INTERVAL = 6;

    // =====================================================================
    // Illager Arrow
    // =====================================================================

    public static final int ILLAGER_ARROW_COOLDOWN_TICKS = 20 * 8;

    private static final double ILLAGER_ARROW_MIN_RANGE = 4.0D;
    private static final double ILLAGER_ARROW_MAX_RANGE = 14.0D;

    private static final int ILLAGER_ARROW_MAX_CHARGE_TICKS = 18;

    private static final double ILLAGER_ARROW_CHARGE_SPEED = 1.28D;
    private static final float ILLAGER_ARROW_IMPACT_DAMAGE = 9.5F;

    private static final Identifier ARMOR_PENETRATION_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "illager_wolf_arrow_penetration");

    // =====================================================================
    // Dancing Arrows
    // =====================================================================

    public static final int DANCING_ARROWS_COOLDOWN_TICKS = 20 * 22;
    public static final int DANCING_ARROWS_DURATION_TICKS = 20 * 10;

    private static final double DANCING_ARROWS_RANGE = 20.0D;

    private static final int DANCING_ARROW_COUNT = 6;
    private static final int DANCING_FIRST_RELEASE_DELAY = 15;
    private static final int DANCING_RELEASE_INTERVAL = 30;

    /*
     * V1.1:
     * Lower orbit so the arrows circle Illager Wolf rather than floating
     * over his head.
     */
    private static final double DANCING_ORBIT_HEIGHT = 0.58D;
    private static final double DANCING_ORBIT_RADIUS = 1.50D;

    private static final double DANCING_ORBIT_ANGULAR_SPEED = 0.22D;
    private static final double DANCING_ORBIT_TANGENT_SPEED = 0.22D;
    private static final double DANCING_ORBIT_CORRECTION = 0.38D;

    private static final double DANCING_PROJECTILE_SPEED = 1.25D;

    private static final float DANCING_ARROW_DAMAGE = 5.0F;
    private static final int DANCING_FLIGHT_LIFETIME = 45;

    // =====================================================================
    // Illager's Charm
    // =====================================================================

    private static final double CHARM_RADIUS = 12.0D;

    private static final int CHARM_DURATION_TICKS = 20 * 2;
    private static final int CHARM_RETRY_TICKS = 20 * 3;

    private static final int RAIDER_HOSTILITY_MEMORY_TICKS = 20 * 15;

    private static final float NORMAL_CHARM_CHANCE = 0.45F;
    private static final float RAVAGER_CHARM_CHANCE = 0.10F;

    private static final double RAIDER_COMMIT_DISTANCE = 3.25D;

    // =====================================================================
    // Rain of Arrows
    // =====================================================================

    public static final int RAIN_OF_ARROWS_COOLDOWN_TICKS = 20 * 40;
    public static final int RAIN_OF_ARROWS_DURATION_TICKS = 20 * 12;

    private static final double RAIN_OF_ARROWS_RANGE = 22.0D;

    private static final int RAIN_BARRAGE_INTERVAL = 15;
    private static final int RAIN_ARROWS_PER_BARRAGE = 4;
    private static final int RAIN_MAX_ACTIVE_ARROWS = 18;

    private static final double RAIN_PROJECTILE_SPEED = 1.40D;

    private static final float RAIN_ARROW_DAMAGE = 4.0F;
    private static final int RAIN_ARROW_LIFETIME = 42;

    // =====================================================================
    // Natural spawning
    // =====================================================================

    private static final int OUTPOST_SEARCH_RADIUS_CHUNKS = 6;
    private static final double OUTPOST_SPAWN_RADIUS = 96.0D;

    // =====================================================================
    // Runtime
    // =====================================================================

    private int illagerArrowCooldownTicks;
    private int illagerArrowChargeTicks;
    private int illagerArrowTargetId = -1;

    private int dancingArrowsCooldownTicks;
    private int dancingArrowsTicks;
    private int dancingNextReleaseTicks;
    private int dancingReleaseCursor;

    private int rainOfArrowsCooldownTicks;
    private int rainOfArrowsTicks;
    private int rainNextBarrageTicks;

    /**
     * Raider entity ID -> game tick at which hostility expires.
     */
    private final Map<Integer, Integer> hostileRaiders =
            new HashMap<>();

    /**
     * Raider entity ID -> game tick until which charm is active.
     */
    private final Map<Integer, Integer> charmedRaiders =
            new HashMap<>();

    /**
     * Raider entity ID -> next tick a charm roll is allowed.
     */
    private final Map<Integer, Integer> charmRetryTimes =
            new HashMap<>();

    private final List<TrackedArrow> trackedArrows =
            new ArrayList<>();

    // =====================================================================
    // Brain
    // =====================================================================

    private static final class BrainHolder {

        private static final Brain.Provider<IllagerWolf> PROVIDER =
                Brain.<IllagerWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.ILLAGER_TACTICAL.get()),
                        wolf -> List.of());
    }

    public IllagerWolf(
            EntityType<? extends IllagerWolf> type,
            Level level) {

        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf>
    wolfismEntityType() {

        return ModEntities.ILLAGER_WOLF.get();
    }

    // =====================================================================
    // Attributes
    // =====================================================================

    public static AttributeSupplier.Builder createAttributes() {

        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.18D)
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
    protected Brain<IllagerWolf> makeBrain(
            Brain.Packed packedBrain) {

        return BrainHolder.PROVIDER.makeBrain(
                this,
                packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<IllagerWolf> getBrain() {

        return (Brain<IllagerWolf>)
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

        tickTrackedArrows(level);
        tickIllagerArrowCharge(level);
        tickDancingArrows(level);
        tickRainOfArrows(level);

        if (this.tickCount % 10 == 0) {

            tickIllagersCharm(level);
            cleanRaiderMemories();
        }

        if (this.isBaby()) {

            cancelAdultStates(level);

            this.setTarget(null);

            return;
        }

        if (illagerArrowChargeTicks > 0) {
            return;
        }

        if (this.tickCount
                % DECISION_INTERVAL == 0) {

            tickIllagerDecisionBrain(level);
        }
    }

    private void tickCooldowns() {

        if (illagerArrowCooldownTicks > 0) {
            --illagerArrowCooldownTicks;
        }

        if (dancingArrowsCooldownTicks > 0) {
            --dancingArrowsCooldownTicks;
        }

        if (rainOfArrowsCooldownTicks > 0) {
            --rainOfArrowsCooldownTicks;
        }
    }

    // =====================================================================
    // Decision brain
    // =====================================================================

    private void tickIllagerDecisionBrain(
            ServerLevel level) {

        if (!this.canUseActiveWolfismAbility()) {
            return;
        }

        int hostileCount =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ILLAGER_HOSTILE_COUNT
                                        .get())
                        .orElse(0);

        /*
         * Ultimate first.
         */
        if (rainOfArrowsCooldownTicks <= 0
                && rainOfArrowsTicks <= 0
                && dancingArrowsTicks <= 0
                && hostileCount >= 4) {

            if (startRainOfArrows(level)) {
                return;
            }
        }

        /*
         * Controlled ranged pressure.
         */
        if (dancingArrowsCooldownTicks <= 0
                && dancingArrowsTicks <= 0
                && rainOfArrowsTicks <= 0
                && hostileCount >= 2) {

            if (startDancingArrows(level)) {
                return;
            }
        }

        LivingEntity armoredTarget =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ILLAGER_ARMORED_TARGET
                                        .get())
                        .orElse(null);

        if (tryStartIllagerArrow(
                level,
                armoredTarget)) {

            return;
        }

        LivingEntity raiderTarget =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ILLAGER_RAIDER_TARGET
                                        .get())
                        .orElse(null);

        if (tryStartIllagerArrow(
                level,
                raiderTarget)) {

            return;
        }

        LivingEntity primary =
                this.getBrain()
                        .getMemory(
                                ModMemoryModuleTypes
                                        .ILLAGER_PRIMARY_TARGET
                                        .get())
                        .orElse(null);

        if (tryStartIllagerArrow(
                level,
                primary)) {

            return;
        }

        /*
         * Shared Wolfism physical aggro rules remain authoritative.
         */
        if (primary != null
                && this
                .isWithinWolfismPhysicalAggroAcquireRange(
                        primary)) {

            this.setTarget(primary);
        }
    }

    // =====================================================================
    // Illager Arrow
    // =====================================================================

    private boolean tryStartIllagerArrow(
            ServerLevel level,
            LivingEntity target) {

        if (illagerArrowCooldownTicks > 0
                || illagerArrowChargeTicks > 0
                || rainOfArrowsTicks > 0
                || target == null
                || !target.isAlive()
                || !isValidIllagerThreat(target)
                || !this.hasLineOfSight(target)
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        double distanceSqr =
                this.distanceToSqr(target);

        if (distanceSqr
                < ILLAGER_ARROW_MIN_RANGE
                * ILLAGER_ARROW_MIN_RANGE
                || distanceSqr
                > ILLAGER_ARROW_MAX_RANGE
                * ILLAGER_ARROW_MAX_RANGE) {

            return false;
        }

        illagerArrowChargeTicks =
                ILLAGER_ARROW_MAX_CHARGE_TICKS;

        illagerArrowTargetId =
                target.getId();

        illagerArrowCooldownTicks =
                ILLAGER_ARROW_COOLDOWN_TICKS;

        this.getNavigation().stop();

        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                18,
                0.35D,
                0.25D,
                0.35D,
                0.08D);

        return true;
    }

    private void tickIllagerArrowCharge(
            ServerLevel level) {

        if (illagerArrowChargeTicks <= 0) {
            return;
        }

        --illagerArrowChargeTicks;

        LivingEntity target =
                getLivingEntityById(
                        level,
                        illagerArrowTargetId);

        if (target == null
                || !target.isAlive()
                || !isValidIllagerThreat(target)) {

            cancelIllagerArrow();

            return;
        }

        Vec3 targetPoint =
                target.position()
                        .add(
                                0.0D,
                                target.getBbHeight()
                                        * 0.45D,
                                0.0D);

        Vec3 delta =
                targetPoint.subtract(
                        this.position());

        double distance =
                delta.length();

        if (distance <= 1.20D) {

            applyIllagerArrowImpact(
                    level,
                    target);

            cancelIllagerArrow();

            return;
        }

        if (distance <= 0.001D) {

            cancelIllagerArrow();

            return;
        }

        Vec3 step =
                delta.normalize()
                        .scale(
                                Math.min(
                                        ILLAGER_ARROW_CHARGE_SPEED,
                                        distance));

        Vec3 next =
                this.position()
                        .add(step);

        /*
         * Never deliberately charge through family.
         */
        if (familyOccupiesChargePath(
                level,
                next)) {

            cancelIllagerArrow();

            return;
        }

        this.getNavigation().stop();

        this.setSprinting(true);

        Vec3 before =
                this.position();

        this.move(
                MoverType.SELF,
                step);

        this.setDeltaMovement(
                Vec3.ZERO);

        /*
         * Wall / terrain collision.
         * Illager Arrow does NOT break blocks.
         */
        if (this.position()
                .distanceToSqr(before)
                < 0.04D) {

            cancelIllagerArrow();

            return;
        }

        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.38D,
                this.getZ(),
                3,
                0.16D,
                0.12D,
                0.16D,
                0.01D);
    }

    private void applyIllagerArrowImpact(
            ServerLevel level,
            LivingEntity target) {

        if (isIllagerFamilyMember(target)) {
            return;
        }

        int armor =
                Math.max(
                        0,
                        target.getArmorValue());

        double penetration =
                Math.min(
                        11.0D,
                        armor * 0.55D);

        AttributeInstance armorAttribute =
                target.getAttribute(
                        Attributes.ARMOR);

        if (armorAttribute != null
                && penetration > 0.0D) {

            armorAttribute
                    .addOrUpdateTransientModifier(
                            new AttributeModifier(
                                    ARMOR_PENETRATION_MODIFIER,
                                    -penetration,
                                    AttributeModifier.Operation
                                            .ADD_VALUE));
        }

        try {

            target.hurtServer(
                    level,
                    this.damageSources()
                            .mobAttack(this),
                    ILLAGER_ARROW_IMPACT_DAMAGE);

        } finally {

            if (armorAttribute != null) {

                armorAttribute.removeModifier(
                        ARMOR_PENETRATION_MODIFIER);
            }
        }

        if (armor > 0) {

            damageArmorDurability(
                    level,
                    target,
                    armor);
        }

        double dx =
                this.getX()
                        - target.getX();

        double dz =
                this.getZ()
                        - target.getZ();

        target.knockback(
                1.05D,
                dx,
                dz);

        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY()
                        + target.getBbHeight()
                        * 0.50D,
                target.getZ(),
                24,
                0.48D,
                0.40D,
                0.48D,
                0.10D);

        level.sendParticles(
                ParticleTypes.POOF,
                target.getX(),
                target.getY()
                        + target.getBbHeight()
                        * 0.40D,
                target.getZ(),
                10,
                0.35D,
                0.30D,
                0.35D,
                0.04D);

        if (target
                instanceof Raider raider) {

            rememberRaiderHostility(raider);
        }
    }

    private void damageArmorDurability(
            ServerLevel level,
            LivingEntity target,
            int armorValue) {

        int durabilityDamage;

        if (armorValue >= 20) {

            durabilityDamage = 4;

        } else if (armorValue >= 15) {

            durabilityDamage = 3;

        } else if (armorValue >= 8) {

            durabilityDamage = 2;

        } else {

            durabilityDamage = 1;
        }

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        for (EquipmentSlot slot
                : armorSlots) {

            ItemStack stack =
                    target.getItemBySlot(slot);

            if (stack.isEmpty()
                    || !stack.isDamageableItem()) {

                continue;
            }

            stack.hurtAndBreak(
                    durabilityDamage,
                    level,
                    target,
                    brokenItem ->
                            target
                                    .onEquippedItemBroken(
                                            brokenItem,
                                            slot));
        }
    }

    private void cancelIllagerArrow() {

        illagerArrowChargeTicks = 0;
        illagerArrowTargetId = -1;

        this.setSprinting(false);

        this.setDeltaMovement(
                Vec3.ZERO);
    }

    private boolean familyOccupiesChargePath(
            ServerLevel level,
            Vec3 position) {

        AABB area =
                new AABB(
                        position.x - 0.75D,
                        position.y - 0.45D,
                        position.z - 0.75D,
                        position.x + 0.75D,
                        position.y + 1.25D,
                        position.z + 0.75D);

        return !level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        entity ->
                                entity != this
                                        && isIllagerFamilyMember(
                                        entity))
                .isEmpty();
    }

    // =====================================================================
    // Dancing Arrows
    // =====================================================================

    private boolean startDancingArrows(
            ServerLevel level) {

        if (dancingArrowsCooldownTicks > 0
                || dancingArrowsTicks > 0
                || rainOfArrowsTicks > 0
                || !this.canUseActiveWolfismAbility()
                || getNearbyIllagerThreats(
                level,
                DANCING_ARROWS_RANGE)
                .isEmpty()) {

            return false;
        }

        dancingArrowsTicks =
                DANCING_ARROWS_DURATION_TICKS;

        dancingArrowsCooldownTicks =
                DANCING_ARROWS_COOLDOWN_TICKS;

        dancingNextReleaseTicks =
                DANCING_FIRST_RELEASE_DELAY;

        dancingReleaseCursor = 0;

        for (int i = 0;
             i < DANCING_ARROW_COUNT;
             ++i) {

            spawnOrbitArrow(
                    level,
                    i);
        }

        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                20,
                0.70D,
                0.40D,
                0.70D,
                0.05D);

        return true;
    }

    private void spawnOrbitArrow(
            ServerLevel level,
            int orbitIndex) {

        Arrow arrow =
                createTemporaryArrow(level);

        double angle =
                (Math.PI * 2.0D
                        * orbitIndex)
                        / DANCING_ARROW_COUNT;

        Vec3 position =
                this.position()
                        .add(
                                Math.cos(angle)
                                        * DANCING_ORBIT_RADIUS,
                                DANCING_ORBIT_HEIGHT,
                                Math.sin(angle)
                                        * DANCING_ORBIT_RADIUS);

        arrow.setPos(
                position.x,
                position.y,
                position.z);

        /*
         * Requested visual:
         * arrow points upward while orbiting.
         */
        arrow.setXRot(-90.0F);

        arrow.setYRot(
                (float)
                        Math.toDegrees(angle));

        level.addFreshEntity(arrow);

        trackedArrows.add(
                new TrackedArrow(
                        arrow.getId(),
                        orbitIndex,
                        ArrowMode.DANCING_ORBIT,
                        -1));
    }

    private void tickDancingArrows(
            ServerLevel level) {

        if (dancingArrowsTicks <= 0) {
            return;
        }

        --dancingArrowsTicks;
        --dancingNextReleaseTicks;

        if (dancingNextReleaseTicks <= 0) {

            releaseNextDancingArrow(level);

            dancingNextReleaseTicks =
                    DANCING_RELEASE_INTERVAL;
        }

        if (dancingArrowsTicks <= 0) {

            discardTrackedMode(
                    level,
                    ArrowMode.DANCING_ORBIT);
        }
    }

    /**
     * The orbit does NOT lose one of its six display arrows every time
     * an attack is fired.
     *
     * Instead, a separate projectile launches from the position of
     * one of the six orbit arrows.
     */
    private void releaseNextDancingArrow(
            ServerLevel level) {

        LivingEntity target =
                findBestArrowTarget(
                        level,
                        DANCING_ARROWS_RANGE);

        if (target == null) {
            return;
        }

        List<TrackedArrow> orbiters =
                trackedArrows.stream()
                        .filter(
                                state ->
                                        state.mode
                                                == ArrowMode
                                                .DANCING_ORBIT)
                        .sorted(
                                Comparator
                                        .comparingInt(
                                                state ->
                                                        state.orbitIndex))
                        .toList();

        if (orbiters.isEmpty()) {
            return;
        }

        TrackedArrow launcher =
                orbiters.get(
                        dancingReleaseCursor
                                % orbiters.size());

        ++dancingReleaseCursor;

        Entity orbitEntity =
                level.getEntity(
                        launcher.arrowId);

        if (!(orbitEntity
                instanceof Arrow orbitArrow)
                || !orbitArrow.isAlive()) {

            return;
        }

        Arrow shot =
                createTemporaryArrow(level);

        shot.setPos(
                orbitArrow.getX(),
                orbitArrow.getY(),
                orbitArrow.getZ());

        Vec3 targetPoint =
                target.position()
                        .add(
                                0.0D,
                                target.getBbHeight()
                                        * 0.52D,
                                0.0D);

        Vec3 delta =
                targetPoint.subtract(
                        shot.position());

        if (delta.lengthSqr()
                <= 0.001D) {

            return;
        }

        Vec3 velocity =
                delta.normalize()
                        .scale(
                                DANCING_PROJECTILE_SPEED);

        shot.setDeltaMovement(
                velocity);

        rotateArrowToward(
                shot,
                velocity);

        level.addFreshEntity(shot);

        trackedArrows.add(
                new TrackedArrow(
                        shot.getId(),
                        launcher.orbitIndex,
                        ArrowMode.DANCING_FLIGHT,
                        target.getId()));

        level.sendParticles(
                ParticleTypes.CRIT,
                orbitArrow.getX(),
                orbitArrow.getY(),
                orbitArrow.getZ(),
                4,
                0.10D,
                0.10D,
                0.10D,
                0.02D);
    }

    // =====================================================================
    // Rain of Arrows
    // =====================================================================

    private boolean startRainOfArrows(
            ServerLevel level) {

        if (rainOfArrowsCooldownTicks > 0
                || rainOfArrowsTicks > 0
                || dancingArrowsTicks > 0
                || !this.canUseActiveWolfismAbility()) {

            return false;
        }

        List<LivingEntity> threats =
                getNearbyIllagerThreats(
                        level,
                        RAIN_OF_ARROWS_RANGE);

        if (threats.size() < 4) {
            return false;
        }

        rainOfArrowsTicks =
                RAIN_OF_ARROWS_DURATION_TICKS;

        rainOfArrowsCooldownTicks =
                RAIN_OF_ARROWS_COOLDOWN_TICKS;

        rainNextBarrageTicks = 0;

        /*
         * Much clearer ultimate startup visual.
         */
        level.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + 0.75D,
                this.getZ(),
                64,
                1.75D,
                0.80D,
                1.75D,
                0.10D);

        level.sendParticles(
                ParticleTypes.POOF,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                24,
                1.15D,
                0.35D,
                1.15D,
                0.045D);

        return true;
    }

    private void tickRainOfArrows(
            ServerLevel level) {

        if (rainOfArrowsTicks <= 0) {
            return;
        }

        --rainOfArrowsTicks;
        --rainNextBarrageTicks;

        if (rainNextBarrageTicks <= 0) {

            spawnRainBarrage(level);

            rainNextBarrageTicks =
                    RAIN_BARRAGE_INTERVAL;
        }

        if (this.tickCount % 20 == 0) {

            level.sendParticles(
                    ParticleTypes.CRIT,
                    this.getX(),
                    this.getY() + 0.75D,
                    this.getZ(),
                    9,
                    0.55D,
                    0.35D,
                    0.55D,
                    0.03D);
        }
    }

    private void spawnRainBarrage(
            ServerLevel level) {

        int currentlyActive =
                (int)
                        trackedArrows.stream()
                                .filter(
                                        state ->
                                                state.mode
                                                        == ArrowMode
                                                        .RAIN_FLIGHT)
                                .count();

        int budget =
                Math.max(
                        0,
                        RAIN_MAX_ACTIVE_ARROWS
                                - currentlyActive);

        if (budget <= 0) {
            return;
        }

        List<LivingEntity> targets =
                getNearbyIllagerThreats(
                        level,
                        RAIN_OF_ARROWS_RANGE)
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                this::
                                                        illagerThreatScore))
                        .toList();

        if (targets.isEmpty()) {
            return;
        }

        int arrows =
                Math.min(
                        RAIN_ARROWS_PER_BARRAGE,
                        budget);

        for (int i = 0;
             i < arrows;
             ++i) {

            LivingEntity target =
                    targets.get(
                            i % targets.size());

            spawnRainArrow(
                    level,
                    target);
        }
    }

    private void spawnRainArrow(
            ServerLevel level,
            LivingEntity target) {

        Arrow arrow =
                createTemporaryArrow(level);

        double spreadX =
                (this.random.nextDouble()
                        - 0.5D)
                        * 3.5D;

        double spreadZ =
                (this.random.nextDouble()
                        - 0.5D)
                        * 3.5D;

        /*
         * Previously ~6+ blocks overhead.
         * Five gives us a much easier-to-see rain effect.
         */
        Vec3 start =
                target.position()
                        .add(
                                spreadX,
                                target.getBbHeight()
                                        + 5.0D,
                                spreadZ);

        arrow.setPos(
                start.x,
                start.y,
                start.z);

        Vec3 targetPoint =
                target.position()
                        .add(
                                0.0D,
                                target.getBbHeight()
                                        * 0.45D,
                                0.0D);

        Vec3 delta =
                targetPoint.subtract(start);

        if (delta.lengthSqr() > 0.001D) {

            Vec3 velocity =
                    delta.normalize()
                            .scale(
                                    RAIN_PROJECTILE_SPEED);

            arrow.setDeltaMovement(
                    velocity);

            rotateArrowToward(
                    arrow,
                    velocity);
        }

        level.addFreshEntity(arrow);

        trackedArrows.add(
                new TrackedArrow(
                        arrow.getId(),
                        0,
                        ArrowMode.RAIN_FLIGHT,
                        target.getId()));

        /*
         * Spawn marker makes each barrage readable.
         */
        level.sendParticles(
                ParticleTypes.CRIT,
                start.x,
                start.y,
                start.z,
                3,
                0.12D,
                0.10D,
                0.12D,
                0.01D);
    }

    // =====================================================================
    // Temporary physical arrows
    // =====================================================================

    private Arrow createTemporaryArrow(
            ServerLevel level) {

        Arrow arrow =
                new Arrow(
                        EntityType.ARROW,
                        level);

        arrow.setOwner(this);

        arrow.pickup =
                AbstractArrow.Pickup.DISALLOWED;

        arrow.setNoPhysics(true);
        arrow.setNoGravity(true);

        /*
         * Actual damage is manually handled so owner/family can never
         * accidentally intercept the projectile.
         */
        arrow.setBaseDamage(0.0D);

        arrow.setCritArrow(false);

        arrow.setDeltaMovement(
                Vec3.ZERO);

        return arrow;
    }

    private void tickTrackedArrows(
            ServerLevel level) {

        Iterator<TrackedArrow> iterator =
                trackedArrows.iterator();

        while (iterator.hasNext()) {

            TrackedArrow state =
                    iterator.next();

            Entity entity =
                    level.getEntity(
                            state.arrowId);

            if (!(entity instanceof Arrow arrow)
                    || !arrow.isAlive()) {

                iterator.remove();

                continue;
            }

            ++state.age;

            if (state.mode
                    == ArrowMode.DANCING_ORBIT) {

                tickOrbitArrow(
                        level,
                        arrow,
                        state);

                continue;
            }

            LivingEntity target =
                    getLivingEntityById(
                            level,
                            state.targetId);

            if (target == null
                    || !target.isAlive()
                    || !isValidIllagerThreat(
                    target)) {

                target =
                        findBestArrowTarget(
                                level,
                                state.mode
                                        == ArrowMode.RAIN_FLIGHT
                                        ? RAIN_OF_ARROWS_RANGE
                                        : DANCING_ARROWS_RANGE);

                if (target == null) {

                    arrow.discard();

                    iterator.remove();

                    continue;
                }

                state.targetId =
                        target.getId();
            }

            int maxAge =
                    state.mode
                            == ArrowMode.RAIN_FLIGHT
                            ? RAIN_ARROW_LIFETIME
                            : DANCING_FLIGHT_LIFETIME;

            if (state.age > maxAge) {

                arrow.discard();

                iterator.remove();

                continue;
            }

            double speed =
                    state.mode
                            == ArrowMode.RAIN_FLIGHT
                            ? RAIN_PROJECTILE_SPEED
                            : DANCING_PROJECTILE_SPEED;

            Vec3 targetPoint =
                    target.position()
                            .add(
                                    0.0D,
                                    target.getBbHeight()
                                            * 0.52D,
                                    0.0D);

            Vec3 delta =
                    targetPoint.subtract(
                            arrow.position());

            double distance =
                    delta.length();

            if (distance <= 0.95D) {

                applyTrackedArrowImpact(
                        level,
                        arrow,
                        target,
                        state.mode);

                arrow.discard();

                iterator.remove();

                continue;
            }

            if (distance <= 0.001D) {

                arrow.discard();

                iterator.remove();

                continue;
            }

            Vec3 step =
                    delta.normalize()
                            .scale(
                                    Math.min(
                                            speed,
                                            distance));

            Vec3 next =
                    arrow.position()
                            .add(step);

            /*
             * Physical arrows stop when terrain blocks them.
             */
            if (!isArrowPointPassable(
                    level,
                    next)) {

                arrow.discard();

                iterator.remove();

                continue;
            }

            rotateArrowToward(
                    arrow,
                    step);

            /*
             * V1.1:
             *
             * Old:
             * setPos(next)
             * setDeltaMovement(Vec3.ZERO)
             *
             * That made arrows look like they were popping/teleporting.
             *
             * Now Minecraft has an actual motion vector to interpolate.
             */
            arrow.setDeltaMovement(step);

            arrow.setNoGravity(true);
            arrow.setNoPhysics(true);

            /*
             * Tiny trail only for Rain.
             * Helps the actual rain remain visible in combat.
             */
            if (state.mode
                    == ArrowMode.RAIN_FLIGHT
                    && state.age % 2 == 0) {

                level.sendParticles(
                        ParticleTypes.CRIT,
                        arrow.getX(),
                        arrow.getY(),
                        arrow.getZ(),
                        1,
                        0.02D,
                        0.02D,
                        0.02D,
                        0.0D);
            }
        }
    }

    // =====================================================================
    // Actual Dancing Arrow orbit
    // =====================================================================

    private void tickOrbitArrow(
            ServerLevel level,
            Arrow arrow,
            TrackedArrow state) {

        if (dancingArrowsTicks <= 0) {

            arrow.discard();

            return;
        }

        double angle =
                this.tickCount
                        * DANCING_ORBIT_ANGULAR_SPEED
                        + state.orbitIndex
                        * (Math.PI * 2.0D
                        / DANCING_ARROW_COUNT);

        double bob =
                Math.sin(
                        this.tickCount * 0.18D
                                + state.orbitIndex)
                        * 0.07D;

        Vec3 desired =
                this.position()
                        .add(
                                Math.cos(angle)
                                        * DANCING_ORBIT_RADIUS,
                                DANCING_ORBIT_HEIGHT
                                        + bob,
                                Math.sin(angle)
                                        * DANCING_ORBIT_RADIUS);

        Vec3 difference =
                desired.subtract(
                        arrow.position());

        /*
         * Teleport recovery only if the wolf suddenly gets very far away,
         * such as teleport-following its owner.
         *
         * Normal orbiting is real movement.
         */
        if (difference.lengthSqr() > 9.0D) {

            arrow.setPos(
                    desired.x,
                    desired.y,
                    desired.z);

            difference =
                    Vec3.ZERO;
        }

        /*
         * Tangential movement creates the circular orbit.
         */
        Vec3 tangent =
                new Vec3(
                        -Math.sin(angle),
                        0.0D,
                        Math.cos(angle))
                        .scale(
                                DANCING_ORBIT_TANGENT_SPEED);

        /*
         * Gentle inward correction prevents the arrow drifting away
         * while Illager Wolf moves around the battlefield.
         */
        Vec3 correction =
                difference.scale(
                        DANCING_ORBIT_CORRECTION);

        arrow.setDeltaMovement(
                tangent.add(correction));

        arrow.setNoGravity(true);
        arrow.setNoPhysics(true);

        /*
         * Requested orientation:
         * the arrows stand upright, points facing upward.
         */
        arrow.setXRot(-90.0F);

        arrow.setYRot(
                (float)
                        Math.toDegrees(angle));

        /*
         * Very subtle visual hint that the orbit is active.
         */
        if (this.tickCount % 8 == 0) {

            level.sendParticles(
                    ParticleTypes.CRIT,
                    arrow.getX(),
                    arrow.getY(),
                    arrow.getZ(),
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }
    }

    private void rotateArrowToward(
            Arrow arrow,
            Vec3 movement) {

        double horizontal =
                Math.sqrt(
                        movement.x
                                * movement.x
                                + movement.z
                                * movement.z);

        arrow.setYRot(
                (float)
                        (Math.atan2(
                                movement.x,
                                movement.z)
                                * 180.0D
                                / Math.PI));

        arrow.setXRot(
                (float)
                        (Math.atan2(
                                movement.y,
                                horizontal)
                                * 180.0D
                                / Math.PI));
    }

    private void applyTrackedArrowImpact(
            ServerLevel level,
            Arrow arrow,
            LivingEntity target,
            ArrowMode mode) {

        if (!isValidIllagerThreat(target)
                || isIllagerFamilyMember(
                target)) {

            return;
        }

        float damage =
                mode == ArrowMode.RAIN_FLIGHT
                        ? RAIN_ARROW_DAMAGE
                        : DANCING_ARROW_DAMAGE;

        target.hurtServer(
                level,
                this.damageSources()
                        .arrow(
                                arrow,
                                this),
                damage);

        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY()
                        + target.getBbHeight()
                        * 0.50D,
                target.getZ(),
                7,
                0.25D,
                0.24D,
                0.25D,
                0.05D);
    }

    private boolean isArrowPointPassable(
            ServerLevel level,
            Vec3 point) {

        BlockPos pos =
                BlockPos.containing(
                        point.x,
                        point.y,
                        point.z);

        return level.getBlockState(pos)
                .getCollisionShape(
                        level,
                        pos)
                .isEmpty();
    }

    private void discardTrackedMode(
            ServerLevel level,
            ArrowMode mode) {

        Iterator<TrackedArrow> iterator =
                trackedArrows.iterator();

        while (iterator.hasNext()) {

            TrackedArrow state =
                    iterator.next();

            if (state.mode != mode) {
                continue;
            }

            Entity entity =
                    level.getEntity(
                            state.arrowId);

            if (entity != null) {

                entity.discard();
            }

            iterator.remove();
        }
    }

    // =====================================================================
    // Raider Awareness
    // =====================================================================

    public double illagerThreatScore(
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

            score -= 500.0D;
        }

        if (isThreateningFamily(target)) {

            score -= 220.0D;
        }

        if (target instanceof Evoker) {

            score -= 95.0D;

        } else if (target instanceof Ravager) {

            score -= 82.0D;

        } else if (target instanceof Vindicator) {

            score -= 68.0D;

        } else if (target instanceof Pillager) {

            score -= 52.0D;

        } else if (target instanceof Raider) {

            score -= 40.0D;
        }

        /*
         * Anti-armor specialist.
         */
        score -= Math.min(
                36.0D,
                target.getArmorValue()
                        * 1.60D);

        if (target instanceof WitherBoss
                || target
                instanceof EnderDragon) {

            score -= 70.0D;
        }

        return score;
    }

    public boolean isThreateningFamily(
            LivingEntity target) {

        if (!(target instanceof Mob mob)) {
            return false;
        }

        LivingEntity victim =
                mob.getTarget();

        return victim != null
                && isIllagerFamilyMember(
                victim);
    }

    // =====================================================================
    // Illager's Charm
    // =====================================================================

    private void tickIllagersCharm(
            ServerLevel level) {

        if (!this.isTame()
                || this.isBaby()) {

            return;
        }

        List<Raider> raiders =
                level.getEntitiesOfClass(
                        Raider.class,
                        this.getBoundingBox()
                                .inflate(
                                        CHARM_RADIUS),
                        Raider::isAlive);

        for (Raider raider
                : raiders) {

            if (isRaiderConflict(
                    raider)) {

                charmedRaiders.remove(
                        raider.getId());

                continue;
            }

            LivingEntity target =
                    raider.getTarget();

            if (target == null
                    || !isIllagerFamilyMember(
                    target)) {

                continue;
            }

            double commitRange =
                    RAIDER_COMMIT_DISTANCE
                            + raider.getBbWidth();

            if (raider.distanceToSqr(
                    target)
                    <= commitRange
                    * commitRange) {

                rememberRaiderHostility(
                        raider);

                continue;
            }

            int charmedUntil =
                    charmedRaiders
                            .getOrDefault(
                                    raider.getId(),
                                    0);

            if (charmedUntil
                    > this.tickCount) {

                raider.setTarget(null);

                raider.getNavigation()
                        .stop();

                continue;
            }

            int retryAt =
                    charmRetryTimes
                            .getOrDefault(
                                    raider.getId(),
                                    0);

            if (retryAt > this.tickCount) {
                continue;
            }

            charmRetryTimes.put(
                    raider.getId(),
                    this.tickCount
                            + CHARM_RETRY_TICKS);

            float chance =
                    raider instanceof Ravager
                            ? RAVAGER_CHARM_CHANCE
                            : NORMAL_CHARM_CHANCE;

            if (this.random.nextFloat()
                    > chance) {

                continue;
            }

            raider.setTarget(null);

            raider.getNavigation()
                    .stop();

            charmedRaiders.put(
                    raider.getId(),
                    this.tickCount
                            + CHARM_DURATION_TICKS);

            level.sendParticles(
                    ParticleTypes.SMOKE,
                    raider.getX(),
                    raider.getY()
                            + raider.getBbHeight()
                            * 0.65D,
                    raider.getZ(),
                    5,
                    0.20D,
                    0.22D,
                    0.20D,
                    0.01D);
        }
    }

    private boolean isRaiderConflict(
            Raider raider) {

        if (raider == null
                || !raider.isAlive()) {

            return false;
        }

        if (hostileRaiders
                .getOrDefault(
                        raider.getId(),
                        0)
                > this.tickCount) {

            return true;
        }

        if (this.getLastHurtByMob()
                == raider) {

            return true;
        }

        LivingEntity owner =
                this.getOwner();

        if (owner != null
                && owner.getLastHurtByMob()
                == raider) {

            return true;
        }

        LivingEntity raiderAttacker =
                raider.getLastHurtByMob();

        return raiderAttacker != null
                && isIllagerFamilyMember(
                raiderAttacker);
    }

    private void rememberRaiderHostility(
            Raider raider) {

        hostileRaiders.put(
                raider.getId(),
                this.tickCount
                        + RAIDER_HOSTILITY_MEMORY_TICKS);

        charmedRaiders.remove(
                raider.getId());

        charmRetryTimes.remove(
                raider.getId());
    }

    private void cleanRaiderMemories() {

        hostileRaiders
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= this.tickCount);

        charmedRaiders
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= this.tickCount);

        charmRetryTimes
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        + CHARM_RETRY_TICKS
                                        <= this.tickCount);
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {

        super.onFamilyDefenseStarted(
                attacker,
                protectedFamily);

        if (attacker
                instanceof Raider raider) {

            rememberRaiderHostility(
                    raider);
        }
    }

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float amount) {

        boolean hurt =
                super.hurtServer(
                        level,
                        source,
                        amount);

        if (hurt
                && source.getEntity()
                instanceof Raider raider) {

            rememberRaiderHostility(
                    raider);
        }

        return hurt;
    }

    // =====================================================================
    // Combat / family
    // =====================================================================

    @Override
    public boolean canAttack(
            LivingEntity target) {

        if (this.isBaby()
                || target == null
                || isIllagerFamilyMember(
                target)) {

            return false;
        }

        /*
         * Raider familiarity:
         * existence alone does not make a Raider an enemy.
         */
        if (target
                instanceof Raider raider
                && !isRaiderConflict(
                raider)) {

            return false;
        }

        return super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(
            Entity other) {

        /*
         * Wild Illager Wolf recognizes Illagers as familiar.
         *
         * This does NOT make him an actual Raider.
         */
        if (!this.isTame()
                && other
                instanceof Raider) {

            return true;
        }

        return super
                .considersEntityAsAlly(
                        other);
    }

    public boolean isIllagerFamilyMember(
            LivingEntity entity) {

        if (entity == null
                || !entity.isAlive()) {

            return false;
        }

        if (entity == this) {
            return true;
        }

        if (this.isTame()) {

            LivingEntity owner =
                    this.getOwner();

            if (entity == owner) {
                return true;
            }

            if (entity instanceof Wolf wolf
                    && wolf.isTame()
                    && owner != null) {

                return wolf.isOwnedBy(owner);
            }

            return false;
        }

        return entity
                instanceof IllagerWolf wolf
                && !wolf.isTame();
    }

    public boolean isValidIllagerThreat(
            LivingEntity target) {

        if (target == null
                || !target.isAlive()
                || target == this
                || isIllagerFamilyMember(
                target)) {

            return false;
        }

        if (target
                instanceof Raider raider) {

            return isRaiderConflict(
                    raider);
        }

        if (target instanceof WitherBoss
                || target
                instanceof EnderDragon) {

            return true;
        }

        if (!(target instanceof Enemy)) {
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
    getNearbyIllagerThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr =
                radius * radius;

        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox()
                                .inflate(radius),
                        this::
                                isValidIllagerThreat)
                .stream()
                .filter(
                        target ->
                                this.distanceToSqr(
                                        target)
                                        <= radiusSqr)
                .toList();
    }

    private LivingEntity findBestArrowTarget(
            ServerLevel level,
            double radius) {

        return getNearbyIllagerThreats(
                level,
                radius)
                .stream()
                .min(
                        Comparator
                                .comparingDouble(
                                        this::
                                                illagerThreatScore))
                .orElse(null);
    }

    // =====================================================================
    // Utility
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

    private void cancelAdultStates(
            ServerLevel level) {

        cancelIllagerArrow();

        dancingArrowsTicks = 0;
        rainOfArrowsTicks = 0;

        for (TrackedArrow state
                : trackedArrows) {

            Entity arrow =
                    level.getEntity(
                            state.arrowId);

            if (arrow != null) {

                arrow.discard();
            }
        }

        trackedArrows.clear();
    }

    public boolean
    isIllagerCombatVisualActive() {

        return illagerArrowChargeTicks > 0
                || dancingArrowsTicks > 0
                || rainOfArrowsTicks > 0
                || !trackedArrows.isEmpty();
    }

    // =====================================================================
    // Natural spawning
    // =====================================================================

    public static boolean
    checkIllagerWolfSpawnRules(
            EntityType<IllagerWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason
                != EntitySpawnReason.CHUNK_GENERATION) {

            return true;
        }

        if (!level.getFluidState(pos)
                .isEmpty()
                || !level
                .getFluidState(
                        pos.above())
                .isEmpty()) {

            return false;
        }

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

        BlockPos floor =
                pos.below();

        if (!level
                .getBlockState(floor)
                .isFaceSturdy(
                        level,
                        floor,
                        Direction.UP)) {

            return false;
        }

        if (level
                instanceof ServerLevel serverLevel) {

            /*
             * Active Raid association.
             *
             * Illager Wolf is NOT inserted into the raid's Raider list.
             */
            Raid raid =
                    serverLevel.getRaidAt(
                            pos);

            boolean activeRaid =
                    raid != null
                            && raid.isActive()
                            && !raid.isOver();

            /*
             * Pillager Outpost association.
             */
            BlockPos outpost =
                    serverLevel
                            .findNearestMapStructure(
                                    ModStructureTags
                                            .ILLAGER_WOLF_OUTPOSTS,
                                    pos,
                                    OUTPOST_SEARCH_RADIUS_CHUNKS,
                                    false);

            boolean nearOutpost =
                    false;

            if (outpost != null) {

                double dx =
                        pos.getX()
                                - outpost.getX();

                double dz =
                        pos.getZ()
                                - outpost.getZ();

                nearOutpost =
                        dx * dx
                                + dz * dz
                                <= OUTPOST_SPAWN_RADIUS
                                * OUTPOST_SPAWN_RADIUS;
            }

            if (!activeRaid
                    && !nearOutpost) {

                return false;
            }

            boolean anotherWildIllagerWolf =
                    !serverLevel
                            .getEntitiesOfClass(
                                    IllagerWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf ->
                                            wolf.isAlive()
                                                    && !wolf.isTame())
                            .isEmpty();

            if (anotherWildIllagerWolf) {

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
                "IllagerArrowCooldown",
                illagerArrowCooldownTicks);

        output.putInt(
                "DancingArrowsCooldown",
                dancingArrowsCooldownTicks);

        output.putInt(
                "RainOfArrowsCooldown",
                rainOfArrowsCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {

        super.readAdditionalSaveData(
                input);

        illagerArrowCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "IllagerArrowCooldown",
                                0));

        dancingArrowsCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "DancingArrowsCooldown",
                                0));

        rainOfArrowsCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "RainOfArrowsCooldown",
                                0));
    }

    // =====================================================================
    // Arrow state
    // =====================================================================

    private enum ArrowMode {

        DANCING_ORBIT,
        DANCING_FLIGHT,
        RAIN_FLIGHT
    }

    private static final class
    TrackedArrow {

        private final int arrowId;
        private final int orbitIndex;

        private ArrowMode mode;

        private int targetId;
        private int age;

        private TrackedArrow(
                int arrowId,
                int orbitIndex,
                ArrowMode mode,
                int targetId) {

            this.arrowId =
                    arrowId;

            this.orbitIndex =
                    orbitIndex;

            this.mode =
                    mode;

            this.targetId =
                    targetId;
        }
    }
}