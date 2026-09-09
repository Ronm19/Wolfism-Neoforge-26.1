package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Command Wolf (#50) — Command / Administration / Tactical AI Control.
 *
 * <p>Wolf King owns strategy. War owns battle tactics. Command owns the
 * immediate execution layer: interrupt this action, push that threat back,
 * focus this enemy, regroup that overextended family member.</p>
 */
public final class CommandWolf extends AbstractWolfismWolf {

    private static final EntityDataAccessor<Boolean> DATA_COMMAND_VISUAL_ACTIVE =
            SynchedEntityData.defineId(
                    CommandWolf.class,
                    EntityDataSerializers.BOOLEAN);

    private static final double BASE_MAX_HEALTH = 44.0D;
    private static final double BASE_ATTACK_DAMAGE = 7.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.33D;
    private static final double BASE_ARMOR = 6.0D;
    private static final double BASE_FOLLOW_RANGE = 48.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 0.12D;

    public static final double COMMAND_AWARENESS_RADIUS = 32.0D;
    /** REGROUP can recover badly overextended family, including ~70-block chases. */
    public static final double COMMAND_FAMILY_RADIUS = 80.0D;
    public static final double COMMAND_LOCAL_FAMILY_RADIUS = 30.0D;
    private static final double COMMAND_EFFECT_RADIUS = 18.0D;
    private static final double COMMAND_EFFECT_RADIUS_SQR =
            COMMAND_EFFECT_RADIUS * COMMAND_EFFECT_RADIUS;
    private static final double FOCUS_FAMILY_RADIUS = 24.0D;
    private static final double REGROUP_DISTANCE = 28.0D;
    private static final double REGROUP_DISTANCE_SQR =
            REGROUP_DISTANCE * REGROUP_DISTANCE;
    private static final double ISOLATED_DISTANCE = 24.0D;
    private static final double ISOLATED_DISTANCE_SQR =
            ISOLATED_DISTANCE * ISOLATED_DISTANCE;

    private static final int NORMAL_DECISION_INTERVAL = 8;
    private static final int COMMAND_CHAIN_DECISION_INTERVAL = 14;
    private static final int EXECUTE_MODE_DECISION_INTERVAL = 8;

    public static final int COMMANDING_BITE_COOLDOWN_TICKS = 20 * 5;
    private static final float COMMANDING_BITE_BONUS_DAMAGE = 2.25F;
    private static final int COMMANDING_BITE_DISRUPTION_TICKS = 22;

    public static final int HALT_COOLDOWN_TICKS = 20 * 8;
    public static final int BACK_COOLDOWN_TICKS = 20 * 11;
    public static final int DROP_COOLDOWN_TICKS = 20 * 14;
    public static final int FOCUS_COOLDOWN_TICKS = 20 * 12;
    public static final int REGROUP_COOLDOWN_TICKS = 20 * 16;
    public static final int OVERRIDE_COOLDOWN_TICKS = 20 * 24;
    public static final int EXECUTE_ORDER_COOLDOWN_TICKS = 20 * 34;

    private static final int HALT_BASE_TICKS = 36;
    private static final int DROP_BASE_TICKS = 20;
    private static final int OVERRIDE_BASE_TICKS = 50;
    private static final int FOCUS_DURATION_TICKS = 20 * 8;
    private static final int EXECUTE_FOCUS_TICKS = 20 * 5;
    private static final int MAX_EXECUTE_RESPONDERS = 4;

    public static final int COMMAND_CHAIN_DURATION_TICKS = 20 * 10;
    public static final int COMMAND_CHAIN_COOLDOWN_TICKS = 20 * 45;
    public static final int EXECUTE_MODE_DURATION_TICKS = 20 * 12;
    public static final int EXECUTE_MODE_COOLDOWN_TICKS = 20 * 60;

    private int commandingBiteCooldownTicks;
    private int haltCooldownTicks;
    private int backCooldownTicks;
    private int dropCooldownTicks;
    private int focusCooldownTicks;
    private int regroupCooldownTicks;
    private int overrideCooldownTicks;
    private int executeOrderCooldownTicks;
    private int commandChainCooldownTicks;
    private int executeModeCooldownTicks;

    private int commandChainTicks;
    private int executeModeTicks;
    private int focusTargetId = -1;
    private int focusTicks;

    private final Map<Integer, Integer> targetDisruptionTicks = new HashMap<>();
    private final Map<Integer, Integer> itemInterruptionTicks = new HashMap<>();

    private static final class BrainHolder {
        private static final Brain.Provider<CommandWolf> PROVIDER =
                Brain.<CommandWolf>provider(
                        ImmutableList.of(ModSensorTypes.COMMAND_AWARENESS.get()),
                        wolf -> List.of());
    }

    public CommandWolf(EntityType<? extends CommandWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COMMAND_VISUAL_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.COMMAND_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ARMOR, BASE_ARMOR)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected Brain<CommandWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<CommandWolf> getBrain() {
        return (Brain<CommandWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;

        tickCooldowns();

        if (this.isBaby()) {
            cancelAllCommandActivity();
            this.setTarget(null);
            return;
        }

        // Owner sit/stay is absolute, including already-issued transient control.
        if (this.isOrderedToSit() || this.isInSittingPose()) {
            cancelAllCommandActivity();
            this.setTarget(null);
            return;
        }

        tickTimedCommandStates(level);
        tickTargetDisruptions(level);
        tickItemInterruptions(level);

        if (!this.canUseActiveWolfismAbility()) return;

        if (executeModeTicks > 0) {
            if (this.tickCount % EXECUTE_MODE_DECISION_INTERVAL == 0) {
                issueContextualCommand(level, true);
            }
            return;
        }

        if (commandChainTicks > 0) {
            if (this.tickCount % COMMAND_CHAIN_DECISION_INTERVAL == 0) {
                issueContextualCommand(level, false);
            }
            return;
        }

        if (this.tickCount % NORMAL_DECISION_INTERVAL == 0) {
            tickCommandDecisionBrain(level);
        }
    }

    private void tickCooldowns() {
        if (commandingBiteCooldownTicks > 0) --commandingBiteCooldownTicks;
        if (haltCooldownTicks > 0) --haltCooldownTicks;
        if (backCooldownTicks > 0) --backCooldownTicks;
        if (dropCooldownTicks > 0) --dropCooldownTicks;
        if (focusCooldownTicks > 0) --focusCooldownTicks;
        if (regroupCooldownTicks > 0) --regroupCooldownTicks;
        if (overrideCooldownTicks > 0) --overrideCooldownTicks;
        if (executeOrderCooldownTicks > 0) --executeOrderCooldownTicks;
        if (commandChainCooldownTicks > 0) --commandChainCooldownTicks;
        if (executeModeCooldownTicks > 0) --executeModeCooldownTicks;

        // /EXECUTE runs the admin loop hotter, without deleting resistance rules.
        if (executeModeTicks > 0) {
            if (commandingBiteCooldownTicks > 0) --commandingBiteCooldownTicks;
            if (haltCooldownTicks > 0) --haltCooldownTicks;
            if (backCooldownTicks > 0) --backCooldownTicks;
            if (dropCooldownTicks > 0) --dropCooldownTicks;
            if (focusCooldownTicks > 0) --focusCooldownTicks;
            if (regroupCooldownTicks > 0) --regroupCooldownTicks;
            if (overrideCooldownTicks > 0) --overrideCooldownTicks;
        }
    }

    private void tickTimedCommandStates(ServerLevel level) {
        if (focusTicks > 0) {
            --focusTicks;
            LivingEntity focus = getLivingEntityById(level, focusTargetId);

            if (focus == null || !isValidCommandThreat(focus)) {
                clearFocus();
            } else if (this.tickCount % 10 == 0) {
                coordinateFocus(level, focus, false);
            }
        }

        if (commandChainTicks > 0) {
            --commandChainTicks;
            if (commandChainTicks == 0) {
                commandBurst(level, this, 8, ParticleTypes.POOF);
            }
        }

        if (executeModeTicks > 0) {
            --executeModeTicks;

            if (this.tickCount % 10 == 0) {
                this.addEffect(new MobEffectInstance(
                        MobEffects.SPEED, 18, 0, true, false), this);
                this.addEffect(new MobEffectInstance(
                        MobEffects.RESISTANCE, 18, 0, true, false), this);
            }

            if (this.tickCount % 10 == 0) {
                sendExecutePulse(level, false);
            }

            if (executeModeTicks == 0) {
                commandBurst(
                        level,
                        this,
                        14,
                        ParticleTypes.SMOKE);

                commandBurst(
                        level,
                        this,
                        10,
                        ParticleTypes.POOF);
            }
        }

        syncCommandVisualState();
    }

    // ---------------------------------------------------------------------
    // Commanding Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        LivingEntity livingTarget =
                target instanceof LivingEntity living ? living : null;

        boolean ready = livingTarget != null
                && !this.isBaby()
                && commandingBiteCooldownTicks <= 0
                && isValidCommandThreat(livingTarget);

        AttributeInstance attackDamage =
                this.getAttribute(Attributes.ATTACK_DAMAGE);
        double oldBaseAttack =
                attackDamage == null ? 0.0D : attackDamage.getBaseValue();

        if (ready && attackDamage != null) {
            attackDamage.setBaseValue(
                    oldBaseAttack + COMMANDING_BITE_BONUS_DAMAGE);
        }

        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (ready && attackDamage != null) {
                attackDamage.setBaseValue(oldBaseAttack);
            }
        }

        if (!hit || !ready || livingTarget == null) return hit;

        commandingBiteCooldownTicks = COMMANDING_BITE_COOLDOWN_TICKS;
        int disruption = scaledControlDuration(
                livingTarget,
                COMMANDING_BITE_DISRUPTION_TICKS);

        queueTargetDisruption(livingTarget, disruption);
        livingTarget.stopUsingItem();

        double push = isBossCommandTarget(livingTarget)
                ? 0.12D
                : isEliteCommandTarget(livingTarget) ? 0.28D : 0.48D;

        double dx = this.getX() - livingTarget.getX();
        double dz = this.getZ() - livingTarget.getZ();
        livingTarget.knockback(push, dx, dz);

        commandBurst(level, livingTarget, 10, ParticleTypes.CRIT);
        commandBurst(level, livingTarget, 6, ParticleTypes.ENCHANTED_HIT);
        return true;
    }


    // ---------------------------------------------------------------------
    // High-level command decision loop
    // ---------------------------------------------------------------------

    private void tickCommandDecisionBrain(ServerLevel level) {
        LivingEntity priority = getPriorityThreat();
        LivingEntity dangerous = getDangerousThreat();
        LivingEntity vulnerable = getVulnerableFamily();
        LivingEntity isolated = getIsolatedFamily();
        int hostileCount = getHostileCount();
        int familyCount = getFamilyCount();

        if (executeModeCooldownTicks <= 0
                && shouldStartExecuteMode(
                priority, vulnerable, hostileCount, familyCount)
                && startExecuteMode(level)) {
            return;
        }

        if (commandChainCooldownTicks <= 0
                && shouldStartCommandChain(hostileCount, familyCount)
                && startCommandChain(level)) {
            return;
        }

        if (dangerous != null
                && haltCooldownTicks <= 0
                && tryHalt(level, dangerous)) {
            return;
        }

        if (priority != null
                && vulnerable != null
                && backCooldownTicks <= 0
                && tryBack(level, priority, vulnerable)) {
            return;
        }

        if (dangerous != null
                && dropCooldownTicks <= 0
                && tryDrop(level, dangerous)) {
            return;
        }

        if (priority != null
                && overrideCooldownTicks <= 0
                && isHighValueOverrideTarget(priority)
                && tryOverride(level, priority)) {
            return;
        }

        if (isolated != null
                && regroupCooldownTicks <= 0
                && tryRegroup(level)) {
            return;
        }

        if (priority != null
                && hostileCount >= 2
                && focusCooldownTicks <= 0
                && tryFocus(level, priority)) {
            return;
        }

        if (priority != null
                && hostileCount >= 3
                && familyCount >= 2
                && executeOrderCooldownTicks <= 0) {
            tryExecuteOrder(level, priority);
        }
    }

    private void issueContextualCommand(
            ServerLevel level,
            boolean ultimate) {

        if (!this.canUseActiveWolfismAbility()) return;

        LivingEntity priority = getPriorityThreat();
        LivingEntity dangerous = getDangerousThreat();
        LivingEntity vulnerable = getVulnerableFamily();
        LivingEntity isolated = getIsolatedFamily();
        int hostileCount = getHostileCount();
        int familyCount = getFamilyCount();

        // This is intentionally contextual, never a fixed command sequence.
        if (dangerous != null
                && haltCooldownTicks <= 0
                && tryHalt(level, dangerous)) {
            return;
        }

        if (priority != null
                && vulnerable != null
                && backCooldownTicks <= 0
                && tryBack(level, priority, vulnerable)) {
            return;
        }

        if (dangerous != null
                && dropCooldownTicks <= 0
                && tryDrop(level, dangerous)) {
            return;
        }

        if (priority != null
                && overrideCooldownTicks <= 0
                && (ultimate || isHighValueOverrideTarget(priority))
                && tryOverride(level, priority)) {
            return;
        }

        if (isolated != null
                && regroupCooldownTicks <= 0
                && tryRegroup(level)) {
            return;
        }

        if (priority != null
                && focusCooldownTicks <= 0
                && (hostileCount >= 2 || ultimate)
                && tryFocus(level, priority)) {
            return;
        }

        if (priority != null
                && familyCount >= 2
                && hostileCount >= (ultimate ? 2 : 3)
                && executeOrderCooldownTicks <= 0) {
            tryExecuteOrder(level, priority);
        }
    }

    // ---------------------------------------------------------------------
    // HALT
    // ---------------------------------------------------------------------

    private boolean tryHalt(ServerLevel level, LivingEntity target) {
        if (haltCooldownTicks > 0
                || !canCommandTarget(target)
                || !isDangerousCommandThreat(target)) {
            return false;
        }

        int ticks = scaledControlDuration(target, HALT_BASE_TICKS);
        queueTargetDisruption(target, ticks);
        target.stopUsingItem();

        if (target instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                Math.max(4, ticks),
                isBossCommandTarget(target) ? 0 : 1,
                true,
                true), this);

        haltCooldownTicks = HALT_COOLDOWN_TICKS;
        commandBurst(level, target, 14, ParticleTypes.POOF);
        return true;
    }

    // ---------------------------------------------------------------------
    // BACK
    // ---------------------------------------------------------------------

    private boolean tryBack(
            ServerLevel level,
            LivingEntity target,
            LivingEntity protectedMember) {

        if (backCooldownTicks > 0
                || !canCommandTarget(target)
                || protectedMember == null
                || !protectedMember.isAlive()) {
            return false;
        }

        if (target.distanceToSqr(protectedMember) > 7.5D * 7.5D) {
            return false;
        }

        /*
         * BACK must be visually obvious.
         *
         * Vanilla knockback alone is easy to miss on resistant mobs, so
         * Command combines knockback with a small directional velocity assist.
         */
        boolean boss = isBossCommandTarget(target);
        boolean elite = isEliteCommandTarget(target);

        double strength = boss
                ? 0.30D
                : elite ? 0.90D : 1.65D;

        double dx = protectedMember.getX() - target.getX();
        double dz = protectedMember.getZ() - target.getZ();

        target.knockback(strength, dx, dz);

        Vec3 away =
                target.position()
                        .subtract(protectedMember.position());

        if (away.lengthSqr() > 1.0E-5D) {
            away = away.normalize();

            double shove = boss
                    ? 0.05D
                    : elite ? 0.14D : 0.25D;

            double lift = boss
                    ? 0.01D
                    : elite ? 0.04D : 0.08D;

            Vec3 motion = target.getDeltaMovement();

            target.setDeltaMovement(
                    motion.x + away.x * shove,
                    Math.max(motion.y, lift),
                    motion.z + away.z * shove);
        }

        target.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        boss
                                ? 6
                                : elite ? 12 : 24,
                        0,
                        true,
                        true),
                this);

        backCooldownTicks = BACK_COOLDOWN_TICKS;

        commandBurst(
                level,
                target,
                18,
                ParticleTypes.POOF);

        commandBurst(
                level,
                target,
                8,
                ParticleTypes.ENCHANTED_HIT);

        return true;
    }

    // ---------------------------------------------------------------------
    // DROP — action interruption, never equipment-loot generation
    // ---------------------------------------------------------------------

    private boolean tryDrop(ServerLevel level, LivingEntity target) {
        if (dropCooldownTicks > 0
                || !canCommandTarget(target)
                || !isDropEligible(target)) {
            return false;
        }

        int ticks = scaledControlDuration(target, DROP_BASE_TICKS);
        itemInterruptionTicks.merge(
                target.getId(),
                ticks,
                Math::max);

        target.stopUsingItem();

        if (target instanceof Pillager pillager) {
            pillager.setChargingCrossbow(false);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                Math.max(4, ticks),
                0,
                true,
                true), this);

        dropCooldownTicks = DROP_COOLDOWN_TICKS;
        commandBurst(level, target, 10, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    private boolean isDropEligible(LivingEntity target) {
        return target.isUsingItem()
                || target instanceof AbstractSkeleton
                || target instanceof Pillager;
    }

    // ---------------------------------------------------------------------
    // FOCUS
    // ---------------------------------------------------------------------

    private boolean tryFocus(ServerLevel level, LivingEntity target) {
        if (focusCooldownTicks > 0
                || !canCommandTarget(target)
                || !this.isTame()) {
            return false;
        }

        focusTargetId = target.getId();
        focusTicks = FOCUS_DURATION_TICKS;
        focusCooldownTicks = FOCUS_COOLDOWN_TICKS;
        syncCommandVisualState();

        coordinateFocus(level, target, false);
        commandBurst(level, target, 14, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    /**
     * FOCUS uses each receiving wolf's normal target slot instead of replacing
     * species combat logic. AbstractWolfismWolf may still reject targets beyond
     * that species' legal physical aggro envelope.
     */
    private int coordinateFocus(
            ServerLevel level,
            LivingEntity target,
            boolean executeOrder) {

        if (!this.isTame() || !isValidCommandThreat(target)) return 0;

        List<AbstractWolfismWolf> family =
                getOperationalFamily(level, FOCUS_FAMILY_RADIUS)
                        .stream()
                        .filter(wolf -> wolf != this)
                        .filter(this::canReceiveCommandOrder)
                        .sorted(Comparator.comparingDouble(
                                wolf -> wolf.distanceToSqr(target)))
                        .toList();

        int responders = 0;
        int max = executeOrder
                ? MAX_EXECUTE_RESPONDERS
                : Integer.MAX_VALUE;

        for (AbstractWolfismWolf wolf : family) {
            if (!wolf.isWithinWolfismPhysicalAggroAcquireRange(target)) {
                continue;
            }

            LivingEntity existing = wolf.getTarget();
            boolean targetThreatensFamily =
                    isThreateningCommandFamily(target);

            boolean betterPriority = existing == null
                    || !existing.isAlive()
                    || targetThreatensFamily
                    || wolf.distanceToSqr(target) + 9.0D
                    < wolf.distanceToSqr(existing);

            if (!betterPriority) continue;

            wolf.setTarget(target);

            if (wolf.getTarget() == target) {
                ++responders;

                if (executeOrder) {
                    commandBurst(
                            level,
                            wolf,
                            3,
                            ParticleTypes.CRIT);
                }
            }

            if (responders >= max) break;
        }

        return responders;
    }

    // ---------------------------------------------------------------------
    // REGROUP
    // ---------------------------------------------------------------------

    private boolean tryRegroup(ServerLevel level) {
        if (regroupCooldownTicks > 0 || !this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) return false;

        int recalled = 0;

        for (AbstractWolfismWolf wolf :
                getOperationalFamily(level, COMMAND_FAMILY_RADIUS)) {

            if (wolf == this || !canReceiveCommandOrder(wolf)) {
                continue;
            }

            boolean farFromOwner =
                    wolf.distanceToSqr(owner) > REGROUP_DISTANCE_SQR;

            LivingEntity current = wolf.getTarget();

            boolean pointlessPursuit = current != null
                    && current.isAlive()
                    && current.distanceToSqr(owner) > REGROUP_DISTANCE_SQR
                    && !isThreateningCommandFamily(current);

            if (!farFromOwner && !pointlessPursuit) continue;

            if (current != null
                    && !isThreateningCommandFamily(current)) {
                wolf.setTarget(null);
            }

            /*
             * Do not generic-move every species. Stopping the old commitment lets
             * that species' own follow-owner system (ground, flight, teleport...)
             * regain control.
             */
            wolf.getNavigation().stop();
            wolf.setSprinting(false);
            ++recalled;

            commandBurst(level, wolf, 4, ParticleTypes.POOF);
        }

        if (recalled <= 0) return false;

        regroupCooldownTicks = REGROUP_COOLDOWN_TICKS;
        commandBurst(level, this, 10, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    // ---------------------------------------------------------------------
    // Override
    // ---------------------------------------------------------------------

    private boolean tryOverride(ServerLevel level, LivingEntity target) {
        if (overrideCooldownTicks > 0
                || !canCommandTarget(target)) {
            return false;
        }

        int ticks = scaledControlDuration(target, OVERRIDE_BASE_TICKS);
        queueTargetDisruption(target, ticks);
        itemInterruptionTicks.merge(
                target.getId(),
                Math.max(4, ticks / 2),
                Math::max);

        target.stopUsingItem();

        if (target instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                Math.max(4, ticks),
                0,
                true,
                true), this);

        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                Math.max(4, ticks),
                isBossCommandTarget(target) ? 0 : 1,
                true,
                true), this);

        overrideCooldownTicks = OVERRIDE_COOLDOWN_TICKS;
        commandBurst(level, target, 18, ParticleTypes.ENCHANTED_HIT);
        return true;
    }

    // ---------------------------------------------------------------------
    // Execute order — “execute the order”, NOT /kill
    // ---------------------------------------------------------------------

    private boolean tryExecuteOrder(
            ServerLevel level,
            LivingEntity target) {

        if (executeOrderCooldownTicks > 0
                || !this.isTame()
                || !canCommandTarget(target)) {
            return false;
        }

        int responders = coordinateFocus(level, target, true);
        if (responders <= 0) return false;

        focusTargetId = target.getId();
        focusTicks = Math.max(focusTicks, EXECUTE_FOCUS_TICKS);
        executeOrderCooldownTicks = EXECUTE_ORDER_COOLDOWN_TICKS;
        syncCommandVisualState();

        commandBurst(level, this, 16, ParticleTypes.CRIT);
        commandBurst(level, target, 12, ParticleTypes.ENCHANTED_HIT);
        return true;
    }


    // ---------------------------------------------------------------------
    // Command Chain
    // ---------------------------------------------------------------------

    private boolean shouldStartCommandChain(
            int hostileCount,
            int familyCount) {

        if (!this.isTame()) return false;

        return (hostileCount >= 3 && familyCount >= 2)
                || (this.hasFamilyDefenseEmergency()
                && hostileCount >= 2);
    }

    private boolean startCommandChain(ServerLevel level) {
        if (commandChainCooldownTicks > 0
                || commandChainTicks > 0
                || executeModeTicks > 0
                || !this.canUseActiveWolfismAbility()) {
            return false;
        }

        commandChainTicks = COMMAND_CHAIN_DURATION_TICKS;
        commandChainCooldownTicks = COMMAND_CHAIN_COOLDOWN_TICKS;
        syncCommandVisualState();

        commandBurst(
                level,
                this,
                22,
                ParticleTypes.ENCHANTED_HIT);

        issueContextualCommand(level, false);
        return true;
    }

    // ---------------------------------------------------------------------
    // /EXECUTE ultimate
    // ---------------------------------------------------------------------

    private boolean shouldStartExecuteMode(
            LivingEntity priority,
            LivingEntity vulnerable,
            int hostileCount,
            int familyCount) {

        if (!this.isTame() || priority == null) return false;

        if (hostileCount >= 5 && familyCount >= 2) {
            return true;
        }

        if (this.hasFamilyDefenseEmergency()
                && vulnerable != null
                && hostileCount >= 3) {
            return true;
        }

        return isBossCommandTarget(priority)
                && hostileCount >= 2
                && familyCount >= 3;
    }

    private boolean startExecuteMode(ServerLevel level) {
        if (executeModeCooldownTicks > 0
                || executeModeTicks > 0
                || !this.canUseActiveWolfismAbility()) {
            return false;
        }

        executeModeTicks = EXECUTE_MODE_DURATION_TICKS;
        executeModeCooldownTicks = EXECUTE_MODE_COOLDOWN_TICKS;

        // /EXECUTE supersedes the lower Command Chain loop.
        commandChainTicks = 0;

        // Bring tools closer to ready without erasing cooldown balance.
        haltCooldownTicks = Math.min(haltCooldownTicks, 20);
        backCooldownTicks = Math.min(backCooldownTicks, 30);
        dropCooldownTicks = Math.min(dropCooldownTicks, 30);
        focusCooldownTicks = Math.min(focusCooldownTicks, 20);
        regroupCooldownTicks = Math.min(regroupCooldownTicks, 30);
        overrideCooldownTicks = Math.min(overrideCooldownTicks, 50);
        executeOrderCooldownTicks = Math.min(
                executeOrderCooldownTicks,
                60);
        syncCommandVisualState();

        sendExecutePulse(level, true);

        issueContextualCommand(level, true);
        return true;
    }

    // ---------------------------------------------------------------------
    // Transient enemy interruption processors
    // ---------------------------------------------------------------------

    private void queueTargetDisruption(
            LivingEntity target,
            int ticks) {

        if (target == null || ticks <= 0) return;

        targetDisruptionTicks.merge(
                target.getId(),
                ticks,
                Math::max);
    }

    private void tickTargetDisruptions(ServerLevel level) {
        if (targetDisruptionTicks.isEmpty()) return;

        Iterator<Map.Entry<Integer, Integer>> iterator =
                targetDisruptionTicks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            int remaining = entry.getValue();

            LivingEntity target =
                    getLivingEntityById(level, entry.getKey());

            if (remaining <= 0
                    || target == null
                    || !target.isAlive()
                    || target.distanceToSqr(this)
                    > COMMAND_AWARENESS_RADIUS
                    * COMMAND_AWARENESS_RADIUS) {

                iterator.remove();
                continue;
            }

            target.stopUsingItem();

            if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }

            if (target instanceof Creeper creeper
                    && !creeper.isIgnited()) {
                creeper.setSwellDir(-1);
            }

            entry.setValue(remaining - 1);
        }
    }

    private void tickItemInterruptions(ServerLevel level) {
        if (itemInterruptionTicks.isEmpty()) return;

        Iterator<Map.Entry<Integer, Integer>> iterator =
                itemInterruptionTicks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            int remaining = entry.getValue();

            LivingEntity target =
                    getLivingEntityById(level, entry.getKey());

            if (remaining <= 0
                    || target == null
                    || !target.isAlive()
                    || target.distanceToSqr(this)
                    > COMMAND_AWARENESS_RADIUS
                    * COMMAND_AWARENESS_RADIUS) {

                iterator.remove();
                continue;
            }

            target.stopUsingItem();

            if (target instanceof Pillager pillager) {
                pillager.setChargingCrossbow(false);
            }

            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }

            entry.setValue(remaining - 1);
        }
    }

    private int scaledControlDuration(
            LivingEntity target,
            int normalTicks) {

        if (isBossCommandTarget(target)) {
            return Math.max(2, normalTicks / 9);
        }

        if (isEliteCommandTarget(target)) {
            return Math.max(6, normalTicks / 3);
        }

        return normalTicks;
    }

    // ---------------------------------------------------------------------
    // Awareness helpers — public because the Sensor uses them
    // ---------------------------------------------------------------------

    public boolean isValidCommandThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || isCommandFamilyMember(target)
                || this.isAlliedTo(target)
                || (!(target instanceof Enemy)
                && !isBossCommandTarget(target))) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public boolean isCommandFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            LivingEntity owner = this.getOwner();

            if (entity == owner) return true;

            if (entity instanceof Wolf wolf
                    && wolf.isTame()
                    && owner != null) {
                return wolf.isOwnedBy(owner);
            }

            return false;
        }

        return entity instanceof CommandWolf command
                && !command.isTame();
    }

    public boolean isVulnerableCommandFamilyMember(
            LivingEntity member) {

        if (!isCommandFamilyMember(member) || member == this) {
            return false;
        }

        return member.getHealth()
                <= member.getMaxHealth() * 0.42F;
    }

    public boolean isIsolatedCommandFamilyMember(
            LivingEntity member) {

        if (!(member instanceof AbstractWolfismWolf wolf)
                || !isCommandFamilyMember(wolf)
                || wolf == this
                || wolf.isBaby()
                || wolf.isOrderedToSit()
                || wolf.isInSittingPose()
                || wolf.hasFamilyDefenseEmergency()) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return owner != null
                && wolf.distanceToSqr(owner)
                > ISOLATED_DISTANCE_SQR;
    }

    public boolean isDangerousCommandThreat(LivingEntity target) {
        if (!isValidCommandThreat(target)) return false;

        if (target instanceof Creeper creeper) {
            return creeper.isIgnited()
                    || creeper.getSwellDir() > 0
                    || creeper.getSwelling(1.0F) > 0.0F;
        }

        if (target.isUsingItem()) return true;

        if (target instanceof Pillager pillager
                && pillager.isChargingCrossbow()) {
            return true;
        }

        if (target instanceof Mob mob) {
            LivingEntity victim = mob.getTarget();
            return victim != null
                    && isCommandFamilyMember(victim);
        }

        return false;
    }

    public double commandThreatScore(LivingEntity target) {
        if (!isValidCommandThreat(target)) {
            return Double.MAX_VALUE;
        }

        double score =
                Math.sqrt(this.distanceToSqr(target));

        if (target == this.getFamilyDefenseTarget()) {
            score -= 1000.0D;
        }

        if (isDangerousCommandThreat(target)) {
            score -= 160.0D;
        }

        if (isThreateningCommandFamily(target)) {
            score -= 120.0D;
        }

        if (isBossCommandTarget(target)) {
            score -= 30.0D;
        } else if (isEliteCommandTarget(target)) {
            score -= 18.0D;
        }

        return score;
    }

    private boolean isThreateningCommandFamily(
            LivingEntity target) {

        if (!(target instanceof Mob mob)) return false;

        LivingEntity victim = mob.getTarget();

        return victim != null
                && isCommandFamilyMember(victim);
    }

    private boolean isHighValueOverrideTarget(
            LivingEntity target) {

        return isDangerousCommandThreat(target)
                || isThreateningCommandFamily(target)
                || target == this.getFamilyDefenseTarget()
                || isEliteCommandTarget(target)
                || isBossCommandTarget(target);
    }

    private boolean isBossCommandTarget(LivingEntity target) {
        return target instanceof WitherBoss
                || target instanceof EnderDragon;
    }

    private boolean isEliteCommandTarget(LivingEntity target) {
        return target instanceof Warden
                || target instanceof Ravager
                || target instanceof Evoker
                || target.getType() == EntityType.PIGLIN_BRUTE;
    }

    private boolean canCommandTarget(LivingEntity target) {
        return target != null
                && isValidCommandThreat(target)
                && this.distanceToSqr(target)
                <= COMMAND_EFFECT_RADIUS_SQR
                && this.getSensing().hasLineOfSight(target);
    }

    // ---------------------------------------------------------------------
    // Family helpers
    // ---------------------------------------------------------------------

    public List<AbstractWolfismWolf> getOperationalFamily(
            ServerLevel level,
            double radius) {

        if (!this.isTame()) return List.of();

        LivingEntity owner = this.getOwner();
        if (owner == null) return List.of();

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        AbstractWolfismWolf.class,
                        this.getBoundingBox().inflate(radius),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && wolf.isOwnedBy(owner))
                .stream()
                .filter(wolf ->
                        this.distanceToSqr(wolf) <= radiusSqr)
                .toList();
    }

    private boolean canReceiveCommandOrder(
            AbstractWolfismWolf wolf) {

        return wolf != null
                && wolf.isAlive()
                && !wolf.isBaby()
                && !wolf.isOrderedToSit()
                && !wolf.isInSittingPose()
                && !wolf.hasFamilyDefenseEmergency();
    }

    // ---------------------------------------------------------------------
    // Brain-memory getters
    // ---------------------------------------------------------------------

    private LivingEntity getPriorityThreat() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_PRIORITY_THREAT
                                .get())
                .orElse(null);
    }

    private LivingEntity getDangerousThreat() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_DANGEROUS_THREAT
                                .get())
                .orElse(null);
    }

    private LivingEntity getVulnerableFamily() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_VULNERABLE_FAMILY
                                .get())
                .orElse(null);
    }

    private LivingEntity getIsolatedFamily() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_ISOLATED_FAMILY
                                .get())
                .orElse(null);
    }

    private int getHostileCount() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_HOSTILE_COUNT
                                .get())
                .orElse(0);
    }

    private int getFamilyCount() {
        return this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes
                                .COMMAND_FAMILY_COUNT
                                .get())
                .orElse(0);
    }

    // ---------------------------------------------------------------------
    // Public state / visuals
    // ---------------------------------------------------------------------

    public boolean isCommandChainActive() {
        return commandChainTicks > 0;
    }

    public boolean isExecuteModeActive() {
        return executeModeTicks > 0;
    }

    public boolean isCommandCombatVisualActive() {
        return this.level().isClientSide()
                ? this.entityData.get(DATA_COMMAND_VISUAL_ACTIVE)
                : isCommandChainActive()
                || isExecuteModeActive()
                || focusTicks > 0;
    }

    private void syncCommandVisualState() {
        if (!this.level().isClientSide()) {
            this.entityData.set(
                    DATA_COMMAND_VISUAL_ACTIVE,
                    commandChainTicks > 0
                            || executeModeTicks > 0
                            || focusTicks > 0);
        }
    }

    public int getCommandingBiteCooldownTicks() {
        return commandingBiteCooldownTicks;
    }

    private void sendExecutePulse(
            ServerLevel level,
            boolean activation) {

        int ringPoints =
                activation
                        ? 16
                        : 10;

        double radius =
                activation
                        ? 1.35D
                        : 0.95D;

        double y =
                this.getY()
                        + (activation
                        ? 0.48D
                        : 0.38D);

        for (int i = 0;
             i < ringPoints;
             ++i) {

            double angle =
                    (Math.PI * 2.0D * i)
                            / ringPoints
                            + this.tickCount
                            * 0.11D;

            double x =
                    this.getX()
                            + Math.cos(angle)
                            * radius;

            double z =
                    this.getZ()
                            + Math.sin(angle)
                            * radius;

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    x,
                    y,
                    z,
                    1,
                    0.01D,
                    0.03D,
                    0.01D,
                    0.005D);
        }

        commandBurst(
                level,
                this,
                activation
                        ? 38
                        : 10,
                ParticleTypes.ENCHANTED_HIT);

        if (activation) {
            commandBurst(
                    level,
                    this,
                    28,
                    ParticleTypes.POOF);

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.75D,
                    this.getZ(),
                    12,
                    0.40D,
                    0.40D,
                    0.40D,
                    0.035D);
        }
    }

    private void commandBurst(
            ServerLevel level,
            LivingEntity center,
            int count,
            ParticleOptions particle) {

        level.sendParticles(
                particle,
                center.getX(),
                center.getY()
                        + center.getBbHeight() * 0.60D,
                center.getZ(),
                count,
                0.35D,
                0.26D,
                0.35D,
                0.03D);
    }

    private void clearFocus() {
        focusTargetId = -1;
        focusTicks = 0;
        syncCommandVisualState();
    }

    private LivingEntity getLivingEntityById(
            ServerLevel level,
            int id) {

        if (id < 0) return null;

        return level.getEntity(id)
                instanceof LivingEntity living
                ? living
                : null;
    }

    private void cancelAllCommandActivity() {
        commandChainTicks = 0;
        executeModeTicks = 0;
        clearFocus();
        targetDisruptionTicks.clear();
        itemInterruptionTicks.clear();
        syncCommandVisualState();
    }

    // =====================================================================
// Natural spawning
// =====================================================================

    /**
     * Command Wolf is a rare solitary open-country overseer.
     *
     * The biome modifier controls the allowed habitats. This predicate keeps
     * natural Command Wolves on exposed, dry wolf-valid terrain and prevents
     * several wild Commands from accumulating in the same operational area.
     */
    public static boolean checkCommandWolfSpawnRules(
            EntityType<CommandWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Habitat restrictions apply only to actual world spawning.
         * Spawn eggs, /summon, breeding, etc. remain unrestricted.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        /*
         * Command's identity is observation and battlefield awareness.
         * He belongs on the surface rather than randomly appearing underground.
         */
        if (!level.canSeeSky(pos)) {
            return false;
        }

        // Dry feet/head.
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        // Clear body space.
        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(pos.above())
                .getCollisionShape(level, pos.above())
                .isEmpty()) {

            return false;
        }

        // Normal wolf-compatible surface.
        if (!level.getBlockState(pos.below())
                .is(BlockTags.WOLVES_SPAWNABLE_ON)) {

            return false;
        }

        /*
         * Command Wolves are solitary administrators, not packs of twelve
         * middle managers holding the same meeting.
         */
        if (level instanceof ServerLevel serverLevel) {

            boolean anotherWildCommand =
                    !serverLevel.getEntitiesOfClass(
                                    CommandWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf ->
                                            wolf.isAlive()
                                                    && !wolf.isTame())
                            .isEmpty();

            if (anotherWildCommand) {
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

        output.putInt("CommandBiteCooldown",
                commandingBiteCooldownTicks);
        output.putInt("CommandHaltCooldown",
                haltCooldownTicks);
        output.putInt("CommandBackCooldown",
                backCooldownTicks);
        output.putInt("CommandDropCooldown",
                dropCooldownTicks);
        output.putInt("CommandFocusCooldown",
                focusCooldownTicks);
        output.putInt("CommandRegroupCooldown",
                regroupCooldownTicks);
        output.putInt("CommandOverrideCooldown",
                overrideCooldownTicks);
        output.putInt("CommandExecuteOrderCooldown",
                executeOrderCooldownTicks);
        output.putInt("CommandChainCooldown",
                commandChainCooldownTicks);
        output.putInt("CommandExecuteModeCooldown",
                executeModeCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        commandingBiteCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandBiteCooldown", 0));
        haltCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandHaltCooldown", 0));
        backCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandBackCooldown", 0));
        dropCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandDropCooldown", 0));
        focusCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandFocusCooldown", 0));
        regroupCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandRegroupCooldown", 0));
        overrideCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandOverrideCooldown", 0));
        executeOrderCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "CommandExecuteOrderCooldown",
                        0));
        commandChainCooldownTicks = Math.max(
                0,
                input.getIntOr("CommandChainCooldown", 0));
        executeModeCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "CommandExecuteModeCooldown",
                        0));

        // Runtime orders are rebuilt from awareness after reload.
        cancelAllCommandActivity();
    }
}
