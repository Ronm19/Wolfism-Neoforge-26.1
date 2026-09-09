package net.ronm19.wolfism.entity.custom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.registry.ModSounds;

/**
 * Wolfism #39 — Primordial Wolf ♀.
 *
 * <p>The primal hunter / pack-survival specialist. Primordial wins through
 * awareness, target evaluation, pursuit, interception and pack instinct rather
 * than supernatural artillery. Her Brain sensors are intentionally the heart
 * of the implementation.</p>
 */
public final class PrimordialWolf extends AbstractWolfismWolf {

    // Raw power stays below Wolf King; her five-star strength is intelligence.
    private static final double MAX_HEALTH = 56.0D;
    private static final double ATTACK_DAMAGE = 8.0D;
    private static final double MOVE_SPEED = 0.33D;
    private static final double ARMOR = 6.0D;
    private static final double FOLLOW_RANGE = 56.0D;
    private static final double KNOCKBACK_RESISTANCE = 0.30D;

    public static final double AWARENESS_RADIUS = 42.0D;
    public static final double PRIMAL_STATE_AWARENESS_RADIUS = 52.0D;
    public static final double PACK_RADIUS = 22.0D;
    public static final double SURVIVAL_INTERCEPT_RADIUS = 26.0D;

    public static final int PRIMAL_STATE_DURATION_TICKS = 20 * 12;
    public static final int PRIMAL_STATE_COOLDOWN_TICKS = 20 * 35;
    public static final int FIRST_PACK_DURATION_TICKS = 20 * 15;
    public static final int FIRST_PACK_COOLDOWN_TICKS = 20 * 60;
    public static final double FIRST_PACK_RADIUS = 16.0D;

    private static final double PRIMAL_STATE_SPEED_BONUS = 0.18D;
    private static final double PRIMAL_STATE_DAMAGE_BONUS = 0.10D;
    private static final double PRIMAL_STATE_KB_BONUS = 0.28D;
    private static final double PRIMAL_STATE_ARMOR_BONUS = 3.0D;
    private static final double PRIMAL_PURSUIT_SPEED = 1.34D;
    private static final double FIRST_PACK_RALLY_SPEED = 1.30D;
    private static final double PACK_EMERGENCY_PURSUIT_SPEED = 1.27D;
    private static final double FINISHER_HEALTH_RATIO = 0.28D;
    private static final float PRIMAL_BITE_FINISHER_DAMAGE = 2.0F;
    private static final int NORMAL_DECISION_INTERVAL = 3;
    private static final int PRIMAL_DECISION_INTERVAL = 2;
    private static final int TARGET_COMMITMENT_TICKS = 20 * 4;
    private static final double NORMAL_PURSUIT_SPEED = 1.24D;
    private static final double EMERGENCY_SWITCH_MARGIN = 95.0D;

    private static final Identifier PRIMAL_STATE_SPEED =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "primordial_state_speed");
    private static final Identifier PRIMAL_STATE_DAMAGE =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "primordial_state_damage");
    private static final Identifier PRIMAL_STATE_KB =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "primordial_state_knockback");
    private static final Identifier PRIMAL_STATE_ARMOR =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "primordial_state_armor");

    private int primalStateTicks;
    private int primalStateCooldownTicks;
    private int firstPackTicks;
    private int firstPackCooldownTicks;
    private LivingEntity committedTarget;
    private int targetCommitmentTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<PrimordialWolf> PROVIDER =
                Brain.<PrimordialWolf>provider(
                        List.of(
                                ModSensorTypes.PRIMORDIAL_PACK.get(),
                                ModSensorTypes.PRIMORDIAL_AWARENESS.get()),
                        wolf -> List.of());
    }

    public PrimordialWolf(EntityType<? extends PrimordialWolf> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.PRIMORDIAL_WOLF.get();
    }

    @Override
    protected Brain<PrimordialWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<PrimordialWolf> getBrain() {
        return (Brain<PrimordialWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        setBase(Attributes.MAX_HEALTH, MAX_HEALTH);
        setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
        setBase(Attributes.MOVEMENT_SPEED, MOVE_SPEED);
        setBase(Attributes.ARMOR, ARMOR);
        setBase(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
        setBase(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
        if (this.isTame()) {
            this.setHealth((float) MAX_HEALTH);
        }
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isPrimordialFamilyMember(target)
                && super.canAttack(target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
        tickTimers();

        if (this.isBaby()) {
            clearAdultState();
            return;
        }

        refreshPrimalStateAttributes();

        if (this.isOrderedToSit() || this.isInSittingPose()) return;

        LivingEntity priority = resolveCommittedThreat(getPriorityThreat());
        if (isPotentialPrimordialThreat(priority)) {
            adoptPriorityThreat(priority);
            sharePackInstinct(level, priority, this.firstPackTicks > 0);

            // "Careful" must mean deliberate, not hesitant. Once a threat is chosen,
            // keep pressure on it instead of waiting for another sensor/goal refresh.
            if (this.primalStateTicks > 0) {
                pursuePrimalTarget(priority);
            } else if (this.distanceToSqr(priority) > 2.6D * 2.6D) {
                this.getNavigation().moveTo(priority, NORMAL_PURSUIT_SPEED);
                this.setSprinting(true);
            }
        }

        int interval = this.primalStateTicks > 0
                ? PRIMAL_DECISION_INTERVAL
                : NORMAL_DECISION_INTERVAL;
        if (this.tickCount % interval != 0) return;

        if (tryCallOfTheFirstPack(level, priority)) return;
        if (trySurvivalInstinct(level)) return;
        tryPrimalState(level, priority);
    }

    private void tickTimers() {
        if (primalStateTicks > 0) --primalStateTicks;
        if (primalStateCooldownTicks > 0) --primalStateCooldownTicks;
        if (firstPackTicks > 0) --firstPackTicks;
        if (firstPackCooldownTicks > 0) --firstPackCooldownTicks;
        if (targetCommitmentTicks > 0) --targetCommitmentTicks;
        if (targetCommitmentTicks <= 0
                || committedTarget == null
                || !isPotentialPrimordialThreat(committedTarget)) {
            committedTarget = null;
            targetCommitmentTicks = 0;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_PRIMAL_STATE_COOLDOWN.get(), primalStateCooldownTicks);
        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_COOLDOWN.get(), firstPackCooldownTicks);
        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_PRIMAL_STATE_ACTIVE.get(), primalStateTicks > 0);
        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_ACTIVE.get(), firstPackTicks > 0);
    }

    private void clearAdultState() {
        primalStateTicks = 0;
        firstPackTicks = 0;
        clearPrimalStateAttributes();
        committedTarget = null;
        targetCommitmentTicks = 0;
        this.setTarget(null);
    }

    // ------------------------------------------------------------------
    // Primordial Awareness / Primal Senses
    // ------------------------------------------------------------------

    public double getCurrentAwarenessRadius() {
        return this.primalStateTicks > 0
                ? PRIMAL_STATE_AWARENESS_RADIUS
                : AWARENESS_RADIUS;
    }

    public boolean isPrimalStateActive() {
        return this.primalStateTicks > 0;
    }

    public boolean isPotentialPrimordialThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || candidate instanceof Creeper
                || this.isPrimordialFamilyMember(candidate)
                || !super.canAttack(candidate)) {
            return false;
        }
        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || this.isTargetingPrimordialFamily(candidate)
                || candidate == this.getLastHurtByMob();
    }

    public double scorePrimordialThreat(LivingEntity candidate) {
        if (!isPotentialPrimordialThreat(candidate)) return -10000.0D;

        double score = 0.0D;
        LivingEntity owner = this.getOwner();
        if (candidate == this.getLastHurtByMob()) score += 85.0D;

        if (candidate instanceof Mob mob) {
            LivingEntity victim = mob.getTarget();
            if (victim != null && isPrimordialFamilyMember(victim)) {
                score += 120.0D;
                if (victim == owner) score += 80.0D;
                double ratio = victim.getHealth() / Math.max(1.0F, victim.getMaxHealth());
                score += (1.0D - ratio) * 70.0D;
            }
        }

        score += Math.min(70.0D, candidate.getMaxHealth() * 0.75D);
        score += Math.min(28.0D, candidate.getAttributeValue(Attributes.MOVEMENT_SPEED) * 65.0D);

        double distance = Math.sqrt(this.distanceToSqr(candidate));
        score += Math.max(0.0D, 38.0D - distance);

        double targetRatio = candidate.getHealth() / Math.max(1.0F, candidate.getMaxHealth());
        if (targetRatio <= FINISHER_HEALTH_RATIO) score += 18.0D;
        if (Math.abs(candidate.getY() - this.getY()) > 5.0D) score -= 12.0D;
        if (this.hasLineOfSight(candidate)) score += 10.0D;
        return score;
    }

    private LivingEntity getPriorityThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_PRIORITY_THREAT.get())
                .filter(this::isPotentialPrimordialThreat)
                .orElse(null);
    }

    private LivingEntity resolveCommittedThreat(LivingEntity sensedPriority) {
        if (!isPotentialPrimordialThreat(committedTarget) || targetCommitmentTicks <= 0) {
            return sensedPriority;
        }
        if (!isPotentialPrimordialThreat(sensedPriority) || sensedPriority == committedTarget) {
            return committedTarget;
        }

        // A true family emergency may override instantly. Otherwise require a
        // clearly superior threat before abandoning an already-correct decision.
        if (isTargetingPrimordialFamily(sensedPriority)
                || scorePrimordialThreat(sensedPriority)
                > scorePrimordialThreat(committedTarget) + EMERGENCY_SWITCH_MARGIN) {
            return sensedPriority;
        }
        return committedTarget;
    }

    private void adoptPriorityThreat(LivingEntity priority) {
        if (!isPotentialPrimordialThreat(priority)) return;

        LivingEntity current = this.getTarget();
        boolean familyEmergency = isTargetingPrimordialFamily(priority);
        boolean maySwitch = current == priority
                || !isPotentialPrimordialThreat(current)
                || familyEmergency
                || scorePrimordialThreat(priority) > scorePrimordialThreat(current) + 70.0D;

        if (maySwitch) {
            if (current != priority) this.setTarget(priority);
            if (committedTarget != priority || familyEmergency) {
                committedTarget = priority;
                targetCommitmentTicks = TARGET_COMMITMENT_TICKS;
            }
        }
    }

    // ------------------------------------------------------------------
    // Primal Bite — adaptive physical attack
    // ------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hit = super.doHurtTarget(level, entity);
        if (!hit || this.isBaby() || !(entity instanceof LivingEntity target)) return hit;

        double ratio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
        if (target.isAlive() && ratio <= FINISHER_HEALTH_RATIO) {
            target.hurtServer(level, this.damageSources().mobAttack(this), PRIMAL_BITE_FINISHER_DAMAGE);
        }

        if (target.isAlive()) {
            // Fast quarry is not allowed to casually break pursuit after contact.
            double targetSpeed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if (targetSpeed >= 0.28D || this.primalStateTicks > 0) {
                this.getNavigation().moveTo(target, PRIMAL_PURSUIT_SPEED);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Pack Instinct
    // ------------------------------------------------------------------

    private void sharePackInstinct(ServerLevel level, LivingEntity priority, boolean firstPackAuthority) {
        if (!isPotentialPrimordialThreat(priority)) return;

        boolean emergency = isTargetingPrimordialFamily(priority);
        LivingEntity protectedMember = emergency && priority instanceof Mob mob
                ? mob.getTarget()
                : null;

        for (Wolf wolf : findOperationalPackWolves(level, PACK_RADIUS)) {
            if (wolf.isBaby() || wolf.isOrderedToSit() || wolf.isInSittingPose() || !wolf.canAttack(priority)) continue;

            LivingEntity current = wolf.getTarget();
            boolean mayRedirect = firstPackAuthority
                    || emergency
                    || current == null
                    || !current.isAlive();

            if (!mayRedirect) continue;

            if (wolf instanceof AbstractWolfismWolf wolfismWolf
                    && emergency
                    && protectedMember != null) {
                wolfismWolf.beginFamilyDefense(priority, protectedMember);
            }

            wolf.setTarget(priority);

            // Shared instinct is supposed to remove hesitation, not spread it.
            // During a family emergency / First Pack rally, give an immediate
            // pursuit order as well as the target so individual AI cannot just
            // stand cautiously while a packmate is being mauled.
            if ((emergency || firstPackAuthority)
                    && wolf.distanceToSqr(priority) > 2.4D * 2.4D) {
                wolf.getNavigation().moveTo(priority, PACK_EMERGENCY_PURSUIT_SPEED);
                wolf.setSprinting(true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Survival Instinct
    // ------------------------------------------------------------------

    private boolean trySurvivalInstinct(ServerLevel level) {
        LivingEntity endangered = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_FAMILY_IN_DANGER.get())
                .filter(LivingEntity::isAlive)
                .orElse(null);
        LivingEntity attacker = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_FAMILY_ATTACKER.get())
                .filter(this::isPotentialPrimordialThreat)
                .orElse(null);

        if (endangered == null) return false;

        if (attacker != null) {
            this.beginFamilyDefense(attacker, endangered);
            this.setTarget(attacker);
            committedTarget = attacker;
            targetCommitmentTicks = TARGET_COMMITMENT_TICKS;
            sharePackInstinct(level, attacker, true);

            // Do not merely "notice" the emergency: physically cut the attacker off.
            if (this.distanceToSqr(attacker) > 2.2D * 2.2D) {
                this.getNavigation().moveTo(attacker, 1.30D);
                this.setSprinting(true);
            }
            return true;
        }

        // Vulnerable but not actively attacked: close the gap and stabilize pack geometry.
        if (this.distanceToSqr(endangered) > 6.0D * 6.0D) {
            this.getNavigation().moveTo(endangered, 1.25D);
            this.setSprinting(true);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Primal State
    // ------------------------------------------------------------------

    private boolean tryPrimalState(ServerLevel level, LivingEntity priority) {
        if (primalStateTicks > 0 || primalStateCooldownTicks > 0 || !isPotentialPrimordialThreat(priority)) return false;

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_HOSTILE_COUNT.get())
                .orElse(0);
        boolean outnumbered = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PRIMORDIAL_PACK_OUTNUMBERED.get())
                .orElse(false);
        boolean familyDanger = this.getBrain().hasMemoryValue(ModMemoryModuleTypes.PRIMORDIAL_FAMILY_IN_DANGER.get());
        boolean hardTarget = priority.getMaxHealth() >= 36.0F;
        boolean fastTarget = priority.getAttributeValue(Attributes.MOVEMENT_SPEED) >= 0.30D;

        if (!familyDanger && !outnumbered && hostileCount < 3 && !hardTarget && !fastTarget) return false;

        primalStateTicks = PRIMAL_STATE_DURATION_TICKS;
        primalStateCooldownTicks = PRIMAL_STATE_COOLDOWN_TICKS;
        refreshPrimalStateAttributes();
        this.setSprinting(true);

        level.sendParticles(ParticleTypes.CRIT,
                this.getX(), this.getY(0.55D), this.getZ(),
                9, 0.38D, 0.22D, 0.38D, 0.03D);
        return true;
    }

    private void pursuePrimalTarget(LivingEntity target) {
        if (!isPotentialPrimordialThreat(target)) return;
        if (this.distanceToSqr(target) > 2.3D * 2.3D) {
            this.getNavigation().moveTo(target, PRIMAL_PURSUIT_SPEED);
            this.setSprinting(true);
        }
    }

    private void refreshPrimalStateAttributes() {
        if (primalStateTicks <= 0) {
            clearPrimalStateAttributes();
            return;
        }
        applyTransient(Attributes.MOVEMENT_SPEED, PRIMAL_STATE_SPEED, PRIMAL_STATE_SPEED_BONUS);
        applyTransient(Attributes.ATTACK_DAMAGE, PRIMAL_STATE_DAMAGE, PRIMAL_STATE_DAMAGE_BONUS);
        applyTransient(Attributes.KNOCKBACK_RESISTANCE, PRIMAL_STATE_KB, PRIMAL_STATE_KB_BONUS);
        applyTransientValue(Attributes.ARMOR, PRIMAL_STATE_ARMOR, PRIMAL_STATE_ARMOR_BONUS);
    }

    private void applyTransient(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                Identifier id, double amount) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void applyTransientValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                     Identifier id, double amount) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private void clearPrimalStateAttributes() {
        removeModifier(Attributes.MOVEMENT_SPEED, PRIMAL_STATE_SPEED);
        removeModifier(Attributes.ATTACK_DAMAGE, PRIMAL_STATE_DAMAGE);
        removeModifier(Attributes.KNOCKBACK_RESISTANCE, PRIMAL_STATE_KB);
        removeModifier(Attributes.ARMOR, PRIMAL_STATE_ARMOR);
        if (this.getTarget() == null) this.setSprinting(false);
    }

    private void removeModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                Identifier id) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    // ------------------------------------------------------------------
    // Call of the First Pack — ultimate
    // ------------------------------------------------------------------

    private boolean tryCallOfTheFirstPack(ServerLevel level, LivingEntity priority) {
        if (firstPackTicks > 0 || firstPackCooldownTicks > 0 || !this.isTame()) return false;

        int hostileCount = this.getBrain().getMemory(ModMemoryModuleTypes.PRIMORDIAL_HOSTILE_COUNT.get()).orElse(0);
        int packCount = this.getBrain().getMemory(ModMemoryModuleTypes.PRIMORDIAL_PACK_COUNT.get()).orElse(1);
        boolean outnumbered = this.getBrain().getMemory(ModMemoryModuleTypes.PRIMORDIAL_PACK_OUTNUMBERED.get()).orElse(false);
        boolean familyDanger = this.getBrain().hasMemoryValue(ModMemoryModuleTypes.PRIMORDIAL_FAMILY_IN_DANGER.get());

        boolean heavyweightThreat = isPotentialPrimordialThreat(priority)
                && priority.getMaxHealth() >= 50.0F;
        boolean packBattle = packCount >= 2
                && (familyDanger || outnumbered || hostileCount >= 3 || heavyweightThreat);

        if (!packBattle) return false;

        firstPackTicks = FIRST_PACK_DURATION_TICKS;
        firstPackCooldownTicks = FIRST_PACK_COOLDOWN_TICKS;
        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_ACTIVE.get(), true);
        this.getBrain().setMemory(ModMemoryModuleTypes.PRIMORDIAL_FIRST_PACK_COOLDOWN.get(), firstPackCooldownTicks);

        for (LivingEntity member : findFamily(level, FIRST_PACK_RADIUS)) {
            member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FIRST_PACK_DURATION_TICKS, 0, false, true), this);
            member.addEffect(new MobEffectInstance(MobEffects.SPEED, FIRST_PACK_DURATION_TICKS, 0, false, true), this);
            member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, FIRST_PACK_DURATION_TICKS, 0, false, true), this);

            // Every affected family member visibly answers the call.
            level.sendParticles(ParticleTypes.CRIT,
                    member.getX(), member.getY(0.55D), member.getZ(),
                    8, 0.30D, 0.22D, 0.30D, 0.035D);
        }

        if (isPotentialPrimordialThreat(priority)) {
            sharePackInstinct(level, priority, true);
            rallyFirstPack(level, priority);
        }

        /*
         * One registered SoundEvent owns all Primordial howl variants.
         * Gameplay NEVER selects howl_1/howl_2/howl_3 directly; sounds.json/datagen
         * owns that random variation exactly like Wolf King's howl pipeline.
         */
        this.playSound(
                ModSounds.PRIMORDIAL_WOLF_HOWL.value(),
                2.0F,
                1.0F);

        // Clear gameplay feedback: this is intentionally obvious so the player
        // can verify that Call of the First Pack actually fired.
        level.sendParticles(ParticleTypes.POOF,
                this.getX(), this.getY(0.55D), this.getZ(),
                34, 1.25D, 0.35D, 1.25D, 0.055D);
        level.sendParticles(ParticleTypes.CRIT,
                this.getX(), this.getY(0.75D), this.getZ(),
                24, 0.90D, 0.45D, 0.90D, 0.075D);
        return true;
    }

    private void rallyFirstPack(ServerLevel level, LivingEntity priority) {
        for (Wolf wolf : findOperationalPackWolves(level, FIRST_PACK_RADIUS)) {
            if (wolf.isBaby() || wolf.isOrderedToSit() || wolf.isInSittingPose()) continue;

            if (priority != null && wolf.canAttack(priority)) {
                wolf.setTarget(priority);
                if (wolf.distanceToSqr(priority) > 2.4D * 2.4D) {
                    wolf.getNavigation().moveTo(priority, FIRST_PACK_RALLY_SPEED);
                    wolf.setSprinting(true);
                }
            } else if (wolf.getTarget() == null && this.distanceToSqr(wolf) > 9.0D * 9.0D) {
                wolf.getNavigation().moveTo(this, FIRST_PACK_RALLY_SPEED);
            }
        }
    }

    // ------------------------------------------------------------------
    // Family / pack rules
    // ------------------------------------------------------------------

    public boolean isPrimordialFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (!this.isTame()) return false;

        LivingEntity owner = this.getOwner();
        if (entity == owner) return true;

        if (entity instanceof Wolf wolf && wolf.isTame()) {
            return Objects.equals(this.getOwnerReference(), wolf.getOwnerReference());
        }
        return false;
    }

    public boolean isOperationalPackWolf(Wolf wolf) {
        if (wolf == null || wolf == this || !wolf.isAlive()) return false;
        if (this.isTame()) {
            return wolf.isTame()
                    && Objects.equals(this.getOwnerReference(), wolf.getOwnerReference());
        }
        return !wolf.isTame();
    }

    public boolean isTargetingPrimordialFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) return false;
        LivingEntity target = mob.getTarget();
        return target != null && isPrimordialFamilyMember(target);
    }

    public List<Wolf> findOperationalPackWolves(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(radius),
                this::isOperationalPackWolf);
    }

    public List<LivingEntity> findFamily(ServerLevel level, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        result.add(this);
        if (!this.isTame()) return result;

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.distanceToSqr(owner) <= radius * radius) result.add(owner);
        result.addAll(findOperationalPackWolves(level, radius));
        return result;
    }
    // ------------------------------------------------------------------
    // Natural spawning — neutral ancestral wilderness encounter
    // ------------------------------------------------------------------

    /**
     * Primordial is neutral toward players unless provoked. MobCategory.AMBIENT
     * remains only a spawn-budget choice; it does not change her Wolf temperament.
     * Habitat is supplied by the Primordial biome tag.
     */
    public static boolean checkPrimordialWolfSpawnRules(
            EntityType<PrimordialWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        var ground = level.getBlockState(pos.below());
        if (!ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                && !ground.is(Blocks.SNOW_BLOCK)) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        // Solitary ancestral encounter: prevent wild Primordials from piling up
        // in the same local hunting range. Tamed Primordials never suppress spawns.
        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildPrimordial = !serverLevel.getEntitiesOfClass(
                            PrimordialWolf.class,
                            new AABB(pos).inflate(80.0D, 40.0D, 80.0D),
                            wolf -> wolf.isAlive() && !wolf.isTame())
                    .isEmpty();

            if (anotherWildPrimordial) {
                return false;
            }
        }

        return true;
    }

}
