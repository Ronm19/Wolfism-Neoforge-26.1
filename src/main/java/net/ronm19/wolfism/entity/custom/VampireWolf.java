package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.tags.BlockTags;
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
 * Wolfism - Vampire Wolf.
 *
 * <p>Role: Vampiric / Apex Sustained Predator / Disruptor / Flex Combatant.</p>
 *
 * <p>Vampire is intentionally NOT Blood Wolf 2.0. Blood is the executioner who
 * escalates around wounded prey. Vampire wins by reading the fight, controlling
 * dangerous prey, closing escape routes, attacking at range, draining vitality,
 * and turning crowded battles into feeding grounds.</p>
 */
public final class VampireWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Baseline / balance
    // ---------------------------------------------------------------------

    private static final double BASE_MAX_HEALTH = 52.0D;

    public static final int BLOODY_FANGS_COOLDOWN_TICKS = 20 * 7;
    private static final float BLOODY_FANGS_BONUS_DAMAGE = 11.0F;
    private static final float BLOODY_FANGS_HEAL_FRACTION = 0.80F;
    private static final float BLOODY_FANGS_HEAL_CAP = 9.0F;
    private static final int BLOODY_FANGS_RECOVERY_TICKS = 20 * 3;

    public static final int RAGE_DURATION_TICKS = 20 * 10;
    public static final int RAGE_COOLDOWN_TICKS = 20 * 28;
    private static final double RAGE_DAMAGE_BONUS = 0.30D;
    private static final double RAGE_SPEED_BONUS = 0.30D;
    private static final double RAGE_KNOCKBACK_BONUS = 0.18D;

    public static final int BLOOD_PROJECTILE_COOLDOWN_TICKS = 20 * 5;
    private static final float BLOOD_PROJECTILE_DAMAGE = 7.5F;
    private static final double BLOOD_PROJECTILE_RANGE = 32.0D;
    private static final double BLOOD_PROJECTILE_SPEED = 1.35D;
    private static final int BLOOD_PROJECTILE_MAX_LIFE = 32;
    private static final double BLOOD_PROJECTILE_HIT_DISTANCE_SQR = 1.45D * 1.45D;

    public static final int CHARM_COOLDOWN_TICKS = 20 * 25;
    private static final int CHARM_NORMAL_TICKS = 20 * 7;
    private static final int CHARM_ELITE_TICKS = 20 * 4;
    private static final int CHARM_BOSS_TICKS = 20;
    private static final float CHARM_FINAL_DRAIN_DAMAGE = 10.0F;
    private static final float CHARM_FINAL_DRAIN_HEAL = 5.5F;

    public static final int PROTECTION_COOLDOWN_TICKS = 20 * 18;
    private static final int PROTECTION_EFFECT_TICKS = 20 * 8;
    private static final double PROTECTION_RADIUS = 12.0D;

    public static final int CHARGE_COOLDOWN_TICKS = 20 * 14;
    private static final int CHARGE_MAX_TICKS = 12;
    private static final double CHARGE_MIN_DISTANCE = 6.0D;
    private static final double CHARGE_MAX_DISTANCE = 18.0D;
    private static final float CHARGE_IMPACT_DAMAGE = 6.0F;

    public static final int RAIN_OF_BLOOD_COOLDOWN_TICKS = 20 * 80;
    private static final int RAIN_OF_BLOOD_DURATION_TICKS = 20 * 10;
    private static final int RAIN_PULSE_INTERVAL = 20;
    private static final double RAIN_RADIUS = 11.0D;
    private static final float RAIN_PULSE_DAMAGE = 2.0F;
    private static final float RAIN_VAMPIRE_HEAL_PER_HIT = 0.55F;
    private static final float RAIN_FAMILY_HEAL_PER_HIT = 0.30F;
    private static final float RAIN_VAMPIRE_HEAL_CAP_PER_PULSE = 3.0F;
    private static final float RAIN_FAMILY_HEAL_CAP_PER_PULSE = 2.0F;
    private static final float RAIN_HEAL_RETENTION = 0.40F; // 60% healing reduction.

    private static final Identifier RAGE_DAMAGE_MODIFIER = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "vampire_rage_damage");
    private static final Identifier RAGE_SPEED_MODIFIER = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "vampire_rage_speed");
    private static final Identifier RAGE_KNOCKBACK_MODIFIER = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "vampire_rage_knockback");

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int bloodyFangsCooldownTicks;

    private int rageTicks;
    private int rageCooldownTicks;

    private int bloodProjectileCooldownTicks;
    private final List<BloodProjectile> bloodProjectiles = new ArrayList<>();

    private int charmCooldownTicks;
    private int charmedTargetId = -1;
    private int charmTicks;
    private int charmOriginalDuration;

    private int protectionCooldownTicks;

    private int chargeCooldownTicks;
    private int chargeTicks;
    private int chargeTargetId = -1;
    private boolean chargeImpacted;

    private int rainCooldownTicks;
    private int rainTicks;
    private Vec3 rainCenter;
    private final Map<Integer, Float> rainObservedHealth = new HashMap<>();

    private static final class BrainHolder {
        private static final Brain.Provider<VampireWolf> PROVIDER = Brain.<VampireWolf>provider(
                ImmutableList.of(ModSensorTypes.VAMPIRE_TACTICAL.get()),
                wolf -> List.of());
    }

    public VampireWolf(EntityType<? extends VampireWolf> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Registration / baseline
    // ---------------------------------------------------------------------

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.VAMPIRE_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 42.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.18D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float oldHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(oldHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected Brain<VampireWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<VampireWolf> getBrain() {
        return (Brain<VampireWolf>) super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Server Brain loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        if (this.isBaby()) {
            this.cancelAdultVampireStates();
            this.setTarget(null);
            return;
        }

        this.tickCooldowns();
        this.tickCharm(level);
        this.tickBloodProjectiles(level);
        this.tickVampireCharge(level);
        this.tickRainOfBlood(level);
        this.tickRageVisuals(level);

        if (this.tickCount % 4 == 0) {
            this.tickAdaptivePredatorBrain(level);
            this.refreshRageAttributes();
        }
    }

    private void tickCooldowns() {
        if (this.bloodyFangsCooldownTicks > 0) --this.bloodyFangsCooldownTicks;
        if (this.rageCooldownTicks > 0) --this.rageCooldownTicks;
        if (this.bloodProjectileCooldownTicks > 0) --this.bloodProjectileCooldownTicks;
        if (this.charmCooldownTicks > 0) --this.charmCooldownTicks;
        if (this.protectionCooldownTicks > 0) --this.protectionCooldownTicks;
        if (this.chargeCooldownTicks > 0) --this.chargeCooldownTicks;
        if (this.rainCooldownTicks > 0) --this.rainCooldownTicks;

        if (this.rageTicks > 0 && --this.rageTicks == 0) {
            this.rageCooldownTicks = RAGE_COOLDOWN_TICKS;
            this.refreshRageAttributes();
        }
    }

    /**
     * This is the important part of Vampire's identity: reassess, don't cycle.
     */
    private void tickAdaptivePredatorBrain(ServerLevel level) {
        if (!this.canUseVampireCombat()) return;

        LivingEntity current = this.getTarget();
        if (current != null && !this.isValidVampireCombatTarget(current)) {
            this.setTarget(null);
            current = null;
        }

        LivingEntity vulnerableFamily = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_VULNERABLE_FAMILY.get())
                .orElse(null);
        LivingEntity charmThreat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_CHARM_TARGET.get())
                .orElse(null);
        LivingEntity drainTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_DRAIN_TARGET.get())
                .orElse(null);
        LivingEntity flyingTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_FLYING_TARGET.get())
                .orElse(null);
        LivingEntity clusterTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_CLUSTER_TARGET.get())
                .orElse(null);
        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VAMPIRE_HOSTILE_COUNT.get())
                .orElse(0);

        // Family emergency remains the highest-level responsibility.
        if (this.hasFamilyDefenseEmergency()) {
            LivingEntity familyThreat = this.getFamilyDefenseTarget();
            if (this.isValidVampireCombatTarget(familyThreat)) {
                this.setTarget(familyThreat);
                current = familyThreat;
            }

            if (this.protectionCooldownTicks <= 0 && hostileCount >= 2) {
                this.castVampiresProtection(level);
                return;
            }

            if (this.charmCooldownTicks <= 0 && this.canCharm(charmThreat)) {
                this.startCharm(level, charmThreat);
                return;
            }
        }

        // Ultimate only when the battlefield is actually worth feeding on.
        if (this.rainCooldownTicks <= 0
                && this.rainTicks <= 0
                && hostileCount >= 4) {
            this.startRainOfBlood(level, clusterTarget);
            return;
        }

        // If family is being overwhelmed, reduce enemy output before chasing DPS.
        if (vulnerableFamily != null
                && this.protectionCooldownTicks <= 0
                && hostileCount >= 2) {
            this.castVampiresProtection(level);
            return;
        }

        // Low health -> acquire an accessible feeding target. Bloody Fangs fires
        // on the actual melee impact, so this is a target/movement decision.
        if (this.getHealth() / this.getMaxHealth() <= 0.62F
                && this.bloodyFangsCooldownTicks <= 0
                && this.isValidVampireCombatTarget(drainTarget)) {
            this.setTarget(drainTarget);
            this.getNavigation().moveTo(drainTarget, this.isRageActive() ? 1.55D : 1.30D);
            current = drainTarget;
        }

        // Disable a dangerous ordinary/elite enemy threatening family.
        if (this.charmCooldownTicks <= 0
                && this.canCharm(charmThreat)
                && (vulnerableFamily != null || hostileCount >= 3)) {
            this.startCharm(level, charmThreat);
            return;
        }

        // Airborne prey doesn't get to opt out of fighting Vampire.
        if (this.bloodProjectileCooldownTicks <= 0
                && this.isValidBloodProjectileTarget(flyingTarget)) {
            this.launchBloodProjectile(level, flyingTarget);
            return;
        }

        current = this.getTarget();
        if (this.isValidVampireCombatTarget(current)) {
            double distance = this.distanceTo(current);

            // Controlled predatory Rage, not Blood's injury-escalation frenzy.
            if (this.rageTicks <= 0
                    && this.rageCooldownTicks <= 0
                    && (distance >= 8.0D
                        || this.getHealth() / this.getMaxHealth() <= 0.66F
                        || current.getMaxHealth() >= 60.0F)) {
                this.startVampiresRage(level);
            }

            if (this.chargeTicks <= 0
                    && this.chargeCooldownTicks <= 0
                    && this.shouldCharge(current)) {
                this.startVampireCharge(level, current);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Bloody Fangs — strongest pure bite specialization
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(level, entity);

        if (!hurt
                || this.isBaby()
                || this.bloodyFangsCooldownTicks > 0
                || !(entity instanceof LivingEntity target)
                || !this.isValidVampireCombatTarget(target)) {
            return hurt;
        }

        DamageSource bite = this.damageSources().mobAttack(this);
        boolean drained = target.hurtServer(level, bite, BLOODY_FANGS_BONUS_DAMAGE);
        this.bloodyFangsCooldownTicks = BLOODY_FANGS_COOLDOWN_TICKS;

        if (drained) {
            float heal = Math.min(
                    BLOODY_FANGS_HEAL_CAP,
                    BLOODY_FANGS_BONUS_DAMAGE * BLOODY_FANGS_HEAL_FRACTION);
            this.heal(heal);

            // Feeding recovery is deliberately bite-gated: Vampire survives
            // heavy fights by successfully feeding, not by passive tank stats.
            this.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    BLOODY_FANGS_RECOVERY_TICKS,
                    0,
                    true,
                    false), this);
        }

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                target.getX(),
                target.getY(0.62D),
                target.getZ(),
                12,
                0.28D, 0.28D, 0.28D,
                0.08D);

        // The shared voice policy preserves this species recording's natural pitch.
        this.playSound(this.getWolfismGrowlSound(), 0.70F, 0.72F);
        return true;
    }

    // ---------------------------------------------------------------------
    // Vampire's Rage — deliberate pursuit state
    // ---------------------------------------------------------------------

    private void startVampiresRage(ServerLevel level) {
        if (this.rageTicks > 0 || this.rageCooldownTicks > 0) return;

        this.rageTicks = RAGE_DURATION_TICKS;
        this.refreshRageAttributes();

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.getX(), this.getY(0.55D), this.getZ(),
                30,
                0.70D, 0.45D, 0.70D,
                0.16D);
        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.CRIT,
                this.getX(), this.getY(0.45D), this.getZ(),
                18,
                0.50D, 0.35D, 0.50D,
                0.10D);
    }

    private void tickRageVisuals(ServerLevel level) {
        if (!this.isRageActive() || this.tickCount % 3 != 0) return;

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.getX(), this.getY(0.50D), this.getZ(),
                4,
                0.32D, 0.28D, 0.32D,
                0.025D);
    }

    public boolean isRageActive() {
        return this.rageTicks > 0;
    }

    private void refreshRageAttributes() {
        if (this.isBaby() || !this.isAlive() || !this.isRageActive()) {
            this.removeModifier(Attributes.ATTACK_DAMAGE, RAGE_DAMAGE_MODIFIER);
            this.removeModifier(Attributes.MOVEMENT_SPEED, RAGE_SPEED_MODIFIER);
            this.removeModifier(Attributes.KNOCKBACK_RESISTANCE, RAGE_KNOCKBACK_MODIFIER);
            return;
        }

        this.applyModifier(
                Attributes.ATTACK_DAMAGE,
                RAGE_DAMAGE_MODIFIER,
                RAGE_DAMAGE_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.applyModifier(
                Attributes.MOVEMENT_SPEED,
                RAGE_SPEED_MODIFIER,
                RAGE_SPEED_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.applyModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                RAGE_KNOCKBACK_MODIFIER,
                RAGE_KNOCKBACK_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private void applyModifier(
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private void removeModifier(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    // ---------------------------------------------------------------------
    // Blood Projectile — real moving anti-air / ranged pressure
    // ---------------------------------------------------------------------

    private void launchBloodProjectile(ServerLevel level, LivingEntity target) {
        if (!this.isValidBloodProjectileTarget(target)
                || this.bloodProjectileCooldownTicks > 0) return;

        Vec3 start = this.getEyePosition().add(0.0D, 0.12D, 0.0D);
        this.bloodProjectiles.add(new BloodProjectile(start, target.getId()));
        this.bloodProjectileCooldownTicks = BLOOD_PROJECTILE_COOLDOWN_TICKS;

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                start.x, start.y, start.z,
                8,
                0.12D, 0.12D, 0.12D,
                0.04D);
    }

    private void tickBloodProjectiles(ServerLevel level) {
        Iterator<BloodProjectile> iterator = this.bloodProjectiles.iterator();

        while (iterator.hasNext()) {
            BloodProjectile shot = iterator.next();
            Entity entity = level.getEntity(shot.targetId);

            if (!(entity instanceof LivingEntity target)
                    || !this.isValidBloodProjectileTarget(target)
                    || ++shot.life > BLOOD_PROJECTILE_MAX_LIFE) {
                iterator.remove();
                continue;
            }

            Vec3 targetPos = target.getEyePosition();
            Vec3 delta = targetPos.subtract(shot.position);

            if (delta.lengthSqr() <= BLOOD_PROJECTILE_HIT_DISTANCE_SQR) {
                DamageSource source = this.damageSources().source(DamageTypes.MOB_PROJECTILE, this);
                boolean damaged;

                if (target instanceof EnderDragon dragon) {
                    damaged = dragon.hurt(level, dragon.head, source, BLOOD_PROJECTILE_DAMAGE);
                } else {
                    damaged = target.hurtServer(level, source, BLOOD_PROJECTILE_DAMAGE);
                }

                if (damaged) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 2, 0), this);
                }

                WolfVfx.sendParticles("vampire_wolf", level,
                        ParticleTypes.DAMAGE_INDICATOR,
                        targetPos.x, targetPos.y, targetPos.z,
                        10,
                        0.24D, 0.24D, 0.24D,
                        0.08D);
                iterator.remove();
                continue;
            }

            Vec3 step = delta.normalize().scale(Math.min(BLOOD_PROJECTILE_SPEED, delta.length()));
            shot.position = shot.position.add(step);

            WolfVfx.sendParticles("vampire_wolf", level,
                    ParticleTypes.DAMAGE_INDICATOR,
                    shot.position.x, shot.position.y, shot.position.z,
                    2,
                    0.04D, 0.04D, 0.04D,
                    0.01D);
        }
    }

    private boolean isValidBloodProjectileTarget(LivingEntity target) {
        return this.isValidVampireThreat(target)
                && this.distanceToSqr(target) <= BLOOD_PROJECTILE_RANGE * BLOOD_PROJECTILE_RANGE;
    }

    // ---------------------------------------------------------------------
    // Vampire's Charm — control ordinary prey; bosses only stagger
    // ---------------------------------------------------------------------

    private void startCharm(ServerLevel level, LivingEntity target) {
        if (!this.canCharm(target) || this.charmCooldownTicks > 0 || this.charmTicks > 0) return;

        this.charmedTargetId = target.getId();
        this.charmOriginalDuration = this.getCharmDuration(target);
        this.charmTicks = this.charmOriginalDuration;
        this.charmCooldownTicks = CHARM_COOLDOWN_TICKS;

        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS,
                Math.min(this.charmTicks, 20 * 4),
                0,
                true,
                false), this);
        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                this.charmTicks,
                10,
                true,
                false), this);

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY(0.75D), target.getZ(),
                14,
                0.30D, 0.40D, 0.30D,
                0.05D);
    }

    private void tickCharm(ServerLevel level) {
        if (this.charmTicks <= 0 || this.charmedTargetId < 0) return;

        Entity entity = level.getEntity(this.charmedTargetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            this.clearCharm();
            return;
        }

        --this.charmTicks;

        // Bosses are only staggered. Ordinary/elite prey is truly locked down.
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                4,
                10,
                true,
                false), this);

        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, Math.min(0.0D, movement.y), 0.0D);
        target.hurtMarked = true;

        if (this.charmTicks % 5 == 0) {
            WolfVfx.sendParticles("vampire_wolf", level,
                    ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY(0.68D), target.getZ(),
                    3,
                    0.18D, 0.22D, 0.18D,
                    0.015D);
        }

        if (this.charmTicks <= 0) {
            boolean boss = this.isBossLike(target);
            boolean elite = this.isEliteCharmTarget(target);
            float damage = boss ? 3.0F : CHARM_FINAL_DRAIN_DAMAGE;
            boolean drained = target.hurtServer(level, this.damageSources().mobAttack(this), damage);

            if (drained && !boss) {
                this.heal(CHARM_FINAL_DRAIN_HEAL);
            }

            if (elite) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 3, 1, true, true), this);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 3, 1, true, true), this);
                WolfVfx.sendParticles("vampire_wolf", level,
                        ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY(0.70D), target.getZ(),
                        24,
                        0.45D, 0.45D, 0.45D,
                        0.10D);
            }

            this.clearCharm();
        }
    }

    private int getCharmDuration(LivingEntity target) {
        if (this.isBossLike(target)) return CHARM_BOSS_TICKS;
        if (this.isEliteCharmTarget(target)) return CHARM_ELITE_TICKS;
        return CHARM_NORMAL_TICKS;
    }

    private boolean isEliteCharmTarget(LivingEntity target) {
        return !this.isBossLike(target) && target.getMaxHealth() >= 40.0F;
    }

    private boolean canCharm(LivingEntity target) {
        return target instanceof Mob
                && this.isValidVampireThreat(target)
                && !(target instanceof Creeper);
    }

    private boolean isBossLike(LivingEntity target) {
        return target instanceof EnderDragon
                || target instanceof WitherBoss
                || target.getMaxHealth() >= 150.0F;
    }

    private void clearCharm() {
        this.charmTicks = 0;
        this.charmedTargetId = -1;
        this.charmOriginalDuration = 0;
    }

    // ---------------------------------------------------------------------
    // Vampire's Protection — protect by making the enemy worse
    // ---------------------------------------------------------------------

    private void castVampiresProtection(ServerLevel level) {
        if (this.protectionCooldownTicks > 0) return;

        this.protectionCooldownTicks = PROTECTION_COOLDOWN_TICKS;
        AABB area = this.getBoundingBox().inflate(PROTECTION_RADIUS);

        // Explicit owner application fixes the support gap observed in live
        // testing. Do not rely on a generic entity query to find the player.
        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.level() == level
                && this.distanceToSqr(owner) <= PROTECTION_RADIUS * PROTECTION_RADIUS) {
            this.applyVampireProtectionToFamily(level, owner);
        }

        this.applyVampireProtectionToFamily(level, this);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity != this && entity != owner && this.isVampireFamilyMember(entity)) {
                this.applyVampireProtectionToFamily(level, entity);
                continue;
            }

            if (this.isValidVampireThreat(entity)) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        PROTECTION_EFFECT_TICKS,
                        0,
                        true,
                        true), this);
                entity.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        PROTECTION_EFFECT_TICKS,
                        0,
                        true,
                        true), this);
            }
        }

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.getX(), this.getY(0.45D), this.getZ(),
                32,
                2.0D, 0.65D, 2.0D,
                0.05D);
    }

    private void applyVampireProtectionToFamily(ServerLevel level, LivingEntity family) {
        family.addEffect(new MobEffectInstance(
                MobEffects.RESISTANCE,
                PROTECTION_EFFECT_TICKS,
                0,
                true,
                true), this);

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.HEART,
                family.getX(),
                family.getY(0.75D),
                family.getZ(),
                2,
                0.20D, 0.20D, 0.20D,
                0.01D);
    }

    // ---------------------------------------------------------------------
    // Vampire Charge — gap closer, not Fire's Flame Rush
    // ---------------------------------------------------------------------

    private boolean shouldCharge(LivingEntity target) {
        if (!this.isValidVampireCombatTarget(target)) return false;
        double distance = this.distanceTo(target);
        if (distance < CHARGE_MIN_DISTANCE || distance > CHARGE_MAX_DISTANCE) return false;

        Vec3 fromVampire = target.position().subtract(this.position());
        Vec3 targetMotion = target.getDeltaMovement();
        double escaping = targetMotion.x * fromVampire.x + targetMotion.z * fromVampire.z;

        return escaping > 0.02D || distance >= 9.0D;
    }

    private void startVampireCharge(ServerLevel level, LivingEntity target) {
        if (!this.shouldCharge(target) || this.chargeCooldownTicks > 0) return;

        this.chargeTargetId = target.getId();
        this.chargeTicks = CHARGE_MAX_TICKS;
        this.chargeImpacted = false;
        this.chargeCooldownTicks = CHARGE_COOLDOWN_TICKS;
        this.getNavigation().stop();

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.getX(), this.getY(0.40D), this.getZ(),
                20,
                0.42D, 0.26D, 0.42D,
                0.08D);
    }

    private void tickVampireCharge(ServerLevel level) {
        if (this.chargeTicks <= 0 || this.chargeTargetId < 0) return;

        Entity entity = level.getEntity(this.chargeTargetId);
        if (!(entity instanceof LivingEntity target)
                || !this.isValidVampireCombatTarget(target)) {
            this.clearCharge();
            return;
        }

        if (this.horizontalCollision || --this.chargeTicks <= 0) {
            this.clearCharge();
            return;
        }

        Vec3 delta = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() <= 1.0E-5D) return;

        Vec3 dash = horizontal.normalize().scale(this.isRageActive() ? 1.10D : 0.92D);
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(dash.x, Math.max(current.y, 0.12D), dash.z);
        this.hurtMarked = true;
        this.getLookControl().setLookAt(target, 45.0F, 45.0F);

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.getX(), this.getY(0.42D), this.getZ(),
                3,
                0.18D, 0.12D, 0.18D,
                0.015D);

        if (!this.chargeImpacted && this.distanceToSqr(target) <= 2.10D * 2.10D) {
            this.chargeImpacted = true;
            target.hurtServer(level, this.damageSources().mobAttack(this), CHARGE_IMPACT_DAMAGE);

            double dx = this.getX() - target.getX();
            double dz = this.getZ() - target.getZ();
            target.knockback(0.75D, dx, dz);

            WolfVfx.sendParticles("vampire_wolf", level,
                    ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY(0.55D), target.getZ(),
                    8,
                    0.25D, 0.25D, 0.25D,
                    0.07D);
            this.clearCharge();
        }
    }

    private void clearCharge() {
        this.chargeTicks = 0;
        this.chargeTargetId = -1;
        this.chargeImpacted = false;
    }

    // ---------------------------------------------------------------------
    // Rain of Blood — battlefield becomes a feeding environment
    // ---------------------------------------------------------------------

    private void startRainOfBlood(ServerLevel level, LivingEntity clusterAnchor) {
        if (this.rainTicks > 0 || this.rainCooldownTicks > 0) return;

        if (this.isValidVampireThreat(clusterAnchor)
                && this.distanceToSqr(clusterAnchor) <= 16.0D * 16.0D) {
            this.rainCenter = clusterAnchor.position();
        } else {
            this.rainCenter = this.position();
        }

        this.rainTicks = RAIN_OF_BLOOD_DURATION_TICKS;
        this.rainCooldownTicks = RAIN_OF_BLOOD_COOLDOWN_TICKS;
        this.rainObservedHealth.clear();

        WolfVfx.sendParticles("vampire_wolf", level,
                ParticleTypes.DAMAGE_INDICATOR,
                this.rainCenter.x,
                this.rainCenter.y + 2.5D,
                this.rainCenter.z,
                45,
                RAIN_RADIUS * 0.55D,
                2.5D,
                RAIN_RADIUS * 0.55D,
                0.12D);
    }

    private void tickRainOfBlood(ServerLevel level) {
        if (this.rainTicks <= 0 || this.rainCenter == null) return;

        --this.rainTicks;

        // Constant visual rain curtain; no terrain griefing.
        if (this.rainTicks % 3 == 0) {
            WolfVfx.sendParticles("vampire_wolf", level,
                    ParticleTypes.DAMAGE_INDICATOR,
                    this.rainCenter.x,
                    this.rainCenter.y + 4.0D,
                    this.rainCenter.z,
                    14,
                    RAIN_RADIUS * 0.72D,
                    2.4D,
                    RAIN_RADIUS * 0.72D,
                    0.18D);
        }

        this.enforceRainRecoveryReduction(level);

        if (this.rainTicks % RAIN_PULSE_INTERVAL == 0) {
            this.pulseRainOfBlood(level);
        }

        if (this.rainTicks <= 0) {
            this.rainCenter = null;
            this.rainObservedHealth.clear();
        }
    }

    private void pulseRainOfBlood(ServerLevel level) {
        AABB area = new AABB(
                this.rainCenter.x - RAIN_RADIUS,
                this.rainCenter.y - 5.0D,
                this.rainCenter.z - RAIN_RADIUS,
                this.rainCenter.x + RAIN_RADIUS,
                this.rainCenter.y + 7.0D,
                this.rainCenter.z + RAIN_RADIUS);

        int drainedTargets = 0;
        DamageSource drainSource = this.damageSources().mobAttack(this);

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidVampireThreat)) {

            if (target.position().distanceToSqr(this.rainCenter) > RAIN_RADIUS * RAIN_RADIUS) {
                continue;
            }

            if (target.hurtServer(level, drainSource, RAIN_PULSE_DAMAGE)) {
                ++drainedTargets;
            }

            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 2, 0, true, true), this);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 2, 0, true, true), this);
            this.rainObservedHealth.put(target.getId(), target.getHealth());
        }

        if (drainedTargets <= 0) return;

        this.heal(Math.min(
                RAIN_VAMPIRE_HEAL_CAP_PER_PULSE,
                drainedTargets * RAIN_VAMPIRE_HEAL_PER_HIT));

        float familyHeal = Math.min(
                RAIN_FAMILY_HEAL_CAP_PER_PULSE,
                drainedTargets * RAIN_FAMILY_HEAL_PER_HIT);

        // Heal family around the Rain field itself. V1 searched around Vampire,
        // which failed whenever the field was centered on a cluster farther away.
        for (LivingEntity family : this.getVampireFamilyInRainField(level)) {
            if (family == this || family.getHealth() >= family.getMaxHealth()) continue;

            family.heal(familyHeal);
            WolfVfx.sendParticles("vampire_wolf", level,
                    ParticleTypes.HEART,
                    family.getX(), family.getY(0.75D), family.getZ(),
                    2,
                    0.18D, 0.18D, 0.18D,
                    0.01D);
        }
    }

    private List<LivingEntity> getVampireFamilyInRainField(ServerLevel level) {
        if (this.rainCenter == null) return List.of();

        AABB area = new AABB(
                this.rainCenter.x - RAIN_RADIUS,
                this.rainCenter.y - 5.0D,
                this.rainCenter.z - RAIN_RADIUS,
                this.rainCenter.x + RAIN_RADIUS,
                this.rainCenter.y + 7.0D,
                this.rainCenter.z + RAIN_RADIUS);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> this.isVampireFamilyMember(entity)
                        && entity.position().distanceToSqr(this.rainCenter) <= RAIN_RADIUS * RAIN_RADIUS);
    }

    /**
     * Approximate 60% healing reduction while a hostile remains in the field.
     * We do not permanently modify attributes or intercept healing globally.
     */
    private void enforceRainRecoveryReduction(ServerLevel level) {
        if (this.rainObservedHealth.isEmpty()) return;

        Iterator<Map.Entry<Integer, Float>> iterator = this.rainObservedHealth.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Float> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());

            if (!(entity instanceof LivingEntity target)
                    || !target.isAlive()
                    || !this.isValidVampireThreat(target)
                    || target.position().distanceToSqr(this.rainCenter) > RAIN_RADIUS * RAIN_RADIUS) {
                iterator.remove();
                continue;
            }

            float observed = entry.getValue();
            float current = target.getHealth();

            if (current > observed + 0.01F) {
                float gain = current - observed;
                float allowed = gain * RAIN_HEAL_RETENTION;
                target.setHealth(Math.min(target.getMaxHealth(), observed + allowed));
            }

            entry.setValue(target.getHealth());
        }
    }

    // ---------------------------------------------------------------------
    // Family / threat intelligence
    // ---------------------------------------------------------------------

    public boolean isVampireFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            if (entity == this.getOwner()) return true;
            // Keep the existing Wolfism universal tamed-wolf family rule.
            return entity instanceof Wolf wolf && wolf.isTame();
        }

        return entity instanceof VampireWolf other
                && !other.isTame();
    }

    public boolean isValidVampireThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || this.isAlliedTo(target)
                || this.isVampireFamilyMember(target)) {
            return false;
        }

        if (!(target instanceof Enemy)
                && target != this.getFamilyDefenseTarget()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public boolean isValidVampireCombatTarget(LivingEntity target) {
        return this.canUseVampireCombat()
                && target != null
                && !(target instanceof Creeper)
                && this.canAttack(target)
                && this.isValidVampireThreat(target);
    }

    public boolean canUseVampireCombat() {
        if (this.isBaby()
                || !this.isAlive()
                || !this.canParticipateInWolfismCombat()) {
            return false;
        }
        return !this.isOrderedToSit() || this.hasFamilyDefenseEmergency();
    }

    public List<LivingEntity> getNearbyVampireFamily(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isVampireFamilyMember);
    }

    public boolean isVulnerableFamilyMember(LivingEntity entity) {
        return this.isVampireFamilyMember(entity)
                && entity.getHealth() / Math.max(1.0F, entity.getMaxHealth()) <= 0.45F;
    }

    public double vampireThreatScore(LivingEntity target) {
        double score = this.distanceToSqr(target);

        if (target instanceof Mob mob
                && mob.getTarget() != null
                && this.isVampireFamilyMember(mob.getTarget())) {
            score -= 500.0D;
        }

        if (!target.onGround() || target.getY() > this.getY() + 2.0D) {
            score -= 40.0D;
        }

        return score;
    }

    // ---------------------------------------------------------------------
    // Damage reaction / sustain safety
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || this.isBaby()) return hurt;

        if (source.getEntity() instanceof LivingEntity attacker
                && this.isValidVampireThreat(attacker)
                && !this.hasFamilyDefenseEmergency()
                && !(this.getTarget() instanceof Creeper)) {
            this.setTarget(attacker);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Neutral natural spawning — shadowed woodland predator
    // ---------------------------------------------------------------------

    /**
     * The biome datagen limits Vampire to Dark Forest + Pale Garden. This
     * placement method keeps the encounter solitary and uses vanilla wolf
     * terrain rules. Tamed Vampires never suppress future wild spawns.
     */
    public static boolean checkVampireWolfSpawnRules(
            EntityType<VampireWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        if (!level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildVampire = !serverLevel.getEntitiesOfClass(
                            VampireWolf.class,
                            new AABB(pos).inflate(64.0D, 32.0D, 64.0D),
                            wolf -> wolf.isAlive() && !wolf.isTame())
                    .isEmpty();

            if (anotherWildVampire) return false;
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("VampireBloodyFangsCooldown", this.bloodyFangsCooldownTicks);
        output.putInt("VampireRageTicks", this.rageTicks);
        output.putInt("VampireRageCooldown", this.rageCooldownTicks);
        output.putInt("VampireProjectileCooldown", this.bloodProjectileCooldownTicks);
        output.putInt("VampireCharmCooldown", this.charmCooldownTicks);
        output.putInt("VampireProtectionCooldown", this.protectionCooldownTicks);
        output.putInt("VampireChargeCooldown", this.chargeCooldownTicks);
        output.putInt("VampireRainCooldown", this.rainCooldownTicks);
        output.putInt("VampireRainTicks", this.rainTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.bloodyFangsCooldownTicks = Math.max(0, input.getIntOr("VampireBloodyFangsCooldown", 0));
        this.rageTicks = Mth.clamp(input.getIntOr("VampireRageTicks", 0), 0, RAGE_DURATION_TICKS);
        this.rageCooldownTicks = Math.max(0, input.getIntOr("VampireRageCooldown", 0));
        this.bloodProjectileCooldownTicks = Math.max(0, input.getIntOr("VampireProjectileCooldown", 0));
        this.charmCooldownTicks = Math.max(0, input.getIntOr("VampireCharmCooldown", 0));
        this.protectionCooldownTicks = Math.max(0, input.getIntOr("VampireProtectionCooldown", 0));
        this.chargeCooldownTicks = Math.max(0, input.getIntOr("VampireChargeCooldown", 0));
        this.rainCooldownTicks = Math.max(0, input.getIntOr("VampireRainCooldown", 0));
        // Active Rain fields depend on a runtime world-space center and tracked
        // victims. Preserve the cooldown, but restart the active field cleanly.
        this.rainTicks = 0;

        // Active target-linked effects are runtime only and restart cleanly.
        this.clearCharm();
        this.clearCharge();
        this.bloodProjectiles.clear();
        this.rainCenter = null;
        this.rainObservedHealth.clear();

        if (this.isBaby()) {
            this.cancelAdultVampireStates();
        } else {
            this.refreshRageAttributes();
        }
    }

    private void cancelAdultVampireStates() {
        this.rageTicks = 0;
        this.clearCharm();
        this.clearCharge();
        this.bloodProjectiles.clear();
        this.rainTicks = 0;
        this.rainCenter = null;
        this.rainObservedHealth.clear();
        this.refreshRageAttributes();
    }

    // ---------------------------------------------------------------------
    // Client-facing state helpers
    // ---------------------------------------------------------------------

    public boolean isVampireCombatVisualActive() {
        return this.isRageActive()
                || this.charmTicks > 0
                || this.chargeTicks > 0
                || this.rainTicks > 0;
    }

    public boolean isRainOfBloodActive() {
        return this.rainTicks > 0;
    }

    private static final class BloodProjectile {
        private Vec3 position;
        private final int targetId;
        private int life;

        private BloodProjectile(Vec3 position, int targetId) {
            this.position = position;
            this.targetId = targetId;
        }
    }
}
