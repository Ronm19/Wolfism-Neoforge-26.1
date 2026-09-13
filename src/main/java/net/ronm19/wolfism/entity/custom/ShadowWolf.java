package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.ShadowWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Shadow Wolf: supernatural ambush/control specialist.
 *
 * <p>Black Wolf remains Wolfism's natural nocturnal hunter. Shadow Wolf is deliberately
 * different: it bends darkness into teleport attacks, orbiting blades, a vision-suppressing
 * battlefield veil, and a short multi-target assassin state.</p>
 */
public final class ShadowWolf extends AbstractWolfismWolf {
    public static final int VOID_DASH_COOLDOWN_TICKS = 20 * 12;
    public static final int SHADOW_BLADES_COOLDOWN_TICKS = 20 * 16;
    public static final int DUSK_VEIL_COOLDOWN_TICKS = 20 * 18;
    public static final int SHADOW_ASSASSIN_COOLDOWN_TICKS = 20 * 60;

    public static final int SHADOW_BLADES_DURATION_TICKS = 20 * 7;
    public static final int DUSK_VEIL_DURATION_TICKS = 20 * 8;
    public static final int SHADOW_ASSASSIN_DURATION_TICKS = 20 * 6;

    public static final double VOID_DASH_MIN_RANGE = 4.0D;
    public static final double VOID_DASH_MAX_RANGE = 12.0D;
    public static final double SHADOW_BLADES_RADIUS = 4.75D;
    public static final double DUSK_VEIL_RADIUS = 12.0D;
    public static final double ASSASSIN_SEARCH_RADIUS = 14.0D;

    public static final float VOID_DASH_DAMAGE = 7.0F;
    public static final float SHADOW_BLADES_PULSE_DAMAGE = 2.0F;
    public static final float ASSASSIN_STRIKE_DAMAGE = 5.5F;

    private static final int DASH_VISUAL_TICKS = 10;
    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int BLADES_DAMAGE_INTERVAL = 20;
    private static final int VEIL_EFFECT_INTERVAL = 20;
    private static final int ASSASSIN_STRIKE_INTERVAL = 18;

    private static final EntityDataAccessor<Boolean> DATA_DASH_ACTIVE =
            SynchedEntityData.defineId(ShadowWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BLADES_ACTIVE =
            SynchedEntityData.defineId(ShadowWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_VEIL_ACTIVE =
            SynchedEntityData.defineId(ShadowWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ASSASSIN_ACTIVE =
            SynchedEntityData.defineId(ShadowWolf.class, EntityDataSerializers.BOOLEAN);

    private int dashVisualTicks;
    private int shadowBladesTicks;
    private int duskVeilTicks;
    private int shadowAssassinTicks;
    private int abilityLockoutTicks;
    private int lastAssassinTargetId = -1;
    private Vec3 duskVeilCenter;

    private static final class BrainHolder {
        private static final Brain.Provider<ShadowWolf> PROVIDER = Brain.<ShadowWolf>provider(
                ImmutableList.of(ModSensorTypes.SHADOW_PACK.get()),
                wolf -> List.of());
    }

    public ShadowWolf(EntityType<? extends ShadowWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_DASH_ACTIVE, false);
        data.define(DATA_BLADES_ACTIVE, false);
        data.define(DATA_VEIL_ACTIVE, false);
        data.define(DATA_ASSASSIN_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SHADOW_WOLF.get();
    }

    @Override
    protected Brain<ShadowWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ShadowWolf> getBrain() {
        return (Brain<ShadowWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof ShadowWolf shadow && this.isShadowPackmate(shadow)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.SHADOW_VOID_DASH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SHADOW_BLADES_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SHADOW_DUSK_VEIL_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SHADOW_ASSASSIN_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelActiveShadowAbilities();
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.SHADOW_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidShadowCombatTarget(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.tickVisualTimers();
        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        if (this.isBaby()) {
            return;
        }

        this.tickShadowStep(level);

        // Long-form abilities own the wolf's supernatural state. They do not stack
        // with one another, which keeps each ability visually/readably distinct.
        if (this.shadowAssassinTicks > 0) {
            this.tickShadowAssassin(level);
            return;
        }
        if (this.duskVeilTicks > 0) {
            this.tickDuskVeil(level);
            return;
        }
        if (this.shadowBladesTicks > 0) {
            this.tickShadowBlades(level);
            return;
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            if (this.tryStartShadowAssassin(level)) {
                return;
            }
            if (this.tryStartDuskVeil(level)) {
                return;
            }
            if (this.tryStartShadowBlades(level)) {
                return;
            }
            this.tryVoidDash(level);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof ShadowWolf shadow && this.isShadowPackmate(shadow)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isShadowPackmate(ShadowWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isShadowFamilyMember(LivingEntity entity) {
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
        return entity instanceof ShadowWolf shadow && this.isShadowPackmate(shadow);
    }

    public boolean isValidShadowCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof ShadowWolf shadow && this.isShadowPackmate(shadow)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    /**
     * Active Shadow abilities never use "anything attackable" as an AoE filter.
     * They only hit actual combat threats, preventing collateral damage to animals
     * or Wolfism family while still allowing explicit family-defense targets.
     */
    private boolean isShadowAbilityThreat(LivingEntity candidate, LivingEntity primary) {
        if (candidate instanceof Creeper || !this.isValidShadowCombatTarget(candidate)) {
            return false;
        }
        if (candidate == primary || candidate == this.getTarget() || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SHADOW_PACK_THREAT.get())
                .orElse(null);
        if (candidate == shared) {
            return true;
        }
        if (candidate instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            if (mobTarget != null && this.isShadowFamilyMember(mobTarget)) {
                return true;
            }
        }
        return candidate instanceof Enemy
                && (primary != null || this.hasFamilyDefenseEmergency() || shared != null);
    }

    public boolean isShadowEmpowered() {
        return this.level().isDarkOutside() || this.level().getRawBrightness(this.blockPosition(), 0) <= 6;
    }

    public float getShadowPowerMultiplier() {
        return this.isShadowEmpowered() ? 1.15F : 0.90F;
    }

    // ---------------------------------------------------------------------
    // 1) Shadow Step — passive darkness movement / identity
    // ---------------------------------------------------------------------
    private void tickShadowStep(ServerLevel level) {
        if (!this.isShadowEmpowered()) {
            return;
        }

        if (this.isWolfismWorkTick(20)) {
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, true, false));
            this.removeEffect(MobEffects.DARKNESS);
        }

        Vec3 velocity = this.getDeltaMovement();
        double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeedSqr > 0.0025D && this.tickCount % 4 == 0) {
            WolfVfx.sendParticles("shadow_wolf", level,
                    ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY() + 0.30D, this.getZ(),
                    2, 0.18D, 0.12D, 0.18D, 0.01D);
            WolfVfx.sendParticles("shadow_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.18D, this.getZ(),
                    1, 0.12D, 0.05D, 0.12D, 0.005D);
        }
    }

    // ---------------------------------------------------------------------
    // 2) Void Dash — instant supernatural interception
    // ---------------------------------------------------------------------
    private boolean tryVoidDash(ServerLevel level) {
        LivingEntity target = this.getPrimaryShadowTarget();
        if (!this.canUseActiveShadowAbility()
                || target == null
                || target instanceof Creeper
                || !this.isValidShadowCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.SHADOW_VOID_DASH_COOLDOWN.get())
                || this.isDashTargetReservedByPack(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < VOID_DASH_MIN_RANGE * VOID_DASH_MIN_RANGE
                || distanceSqr > VOID_DASH_MAX_RANGE * VOID_DASH_MAX_RANGE) {
            return false;
        }

        Vec3 origin = this.position();
        Vec3 targetLook = target.getLookAngle();
        Vec3 flatLook = new Vec3(targetLook.x, 0.0D, targetLook.z);
        if (flatLook.lengthSqr() < 1.0E-5D) {
            flatLook = target.position().subtract(origin);
            flatLook = new Vec3(flatLook.x, 0.0D, flatLook.z);
        }
        if (flatLook.lengthSqr() < 1.0E-5D) {
            flatLook = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 desired = target.position().subtract(flatLook.normalize().scale(2.2D));
        Vec3 safe = this.findSafeShadowPosition(level, desired, target.getY());
        if (safe == null) {
            return false;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_VOID_DASH_TARGET.get(), target);
        this.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_VOID_DASH_COOLDOWN.get(), VOID_DASH_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setPos(safe.x, safe.y, safe.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.getLookControl().setLookAt(target, 90.0F, 90.0F);

        float damage = VOID_DASH_DAMAGE * this.getShadowPowerMultiplier();
        if (target.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 0));
        }

        this.sendDashTrail(level, origin, safe);
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 0.45D, origin.z,
                20, 0.30D, 0.35D, 0.30D, 0.05D);
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.REVERSE_PORTAL, safe.x, safe.y + 0.45D, safe.z,
                24, 0.30D, 0.35D, 0.30D, 0.06D);
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(), 2, 0.20D, 0.20D, 0.20D, 0.0D);

        this.dashVisualTicks = DASH_VISUAL_TICKS;
        this.entityData.set(DATA_DASH_ACTIVE, true);
        this.abilityLockoutTicks = 20;
        return true;
    }

    // ---------------------------------------------------------------------
    // 3) Shadow Blades — orbiting close-range damage state
    // ---------------------------------------------------------------------
    private boolean tryStartShadowBlades(ServerLevel level) {
        if (!this.canUseActiveShadowAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.SHADOW_BLADES_COOLDOWN.get())) {
            return false;
        }

        LivingEntity primary = this.getPrimaryShadowTarget();
        List<LivingEntity> threats = this.findShadowThreats(this.position(), 6.0D, primary);
        boolean closePrimary = primary != null && this.distanceToSqr(primary) <= 6.0D * 6.0D;
        if (!closePrimary && threats.size() < 2) {
            return false;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_BLADES_COOLDOWN.get(), SHADOW_BLADES_COOLDOWN_TICKS);
        this.shadowBladesTicks = SHADOW_BLADES_DURATION_TICKS;
        this.entityData.set(DATA_BLADES_ACTIVE, true);
        this.abilityLockoutTicks = 20;

        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.WITCH, this.getX(), this.getY() + 0.55D, this.getZ(),
                24, 0.55D, 0.40D, 0.55D, 0.03D);
        return true;
    }

    private void tickShadowBlades(ServerLevel level) {
        --this.shadowBladesTicks;
        this.sendShadowBladeOrbit(level);

        if (this.shadowBladesTicks % BLADES_DAMAGE_INTERVAL == 0) {
            LivingEntity primary = this.getPrimaryShadowTarget();
            this.findShadowThreats(this.position(), SHADOW_BLADES_RADIUS, primary).stream()
                    .sorted(Comparator.comparingDouble(this::distanceToSqr))
                    .limit(3)
                    .forEach(target -> {
                        if (target.hurtServer(
                                level,
                                this.damageSources().mobAttack(this),
                                SHADOW_BLADES_PULSE_DAMAGE * this.getShadowPowerMultiplier())) {
                            WolfVfx.sendParticles("shadow_wolf", level,
                                    ParticleTypes.SWEEP_ATTACK,
                                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                                    2, 0.18D, 0.18D, 0.18D, 0.0D);
                        }
                    });
        }

        if (this.shadowBladesTicks <= 0) {
            this.shadowBladesTicks = 0;
            this.entityData.set(DATA_BLADES_ACTIVE, false);
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 18);
        }
    }

    // ---------------------------------------------------------------------
    // 4) Dusk Veil — fixed battlefield-control field
    // ---------------------------------------------------------------------
    private boolean tryStartDuskVeil(ServerLevel level) {
        if (!this.canUseActiveShadowAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.SHADOW_DUSK_VEIL_COOLDOWN.get())
                || this.hasPackActiveDuskVeil()) {
            return false;
        }

        LivingEntity primary = this.getPrimaryShadowTarget();
        List<LivingEntity> threats = this.findShadowThreats(this.position(), 10.0D, primary);
        boolean familyEmergency = this.hasFamilyDefenseEmergency() && !threats.isEmpty();
        if (threats.size() < 2 && !familyEmergency) {
            return false;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_DUSK_VEIL_COOLDOWN.get(), DUSK_VEIL_COOLDOWN_TICKS);
        this.duskVeilCenter = this.position();
        this.duskVeilTicks = DUSK_VEIL_DURATION_TICKS;
        this.entityData.set(DATA_VEIL_ACTIVE, true);
        this.applyPackAbilityLockout(DUSK_VEIL_DURATION_TICKS);

        this.sendShadowRing(level, this.duskVeilCenter, 4.0D, 28, ParticleTypes.REVERSE_PORTAL);
        this.sendShadowRing(level, this.duskVeilCenter, 8.0D, 40, ParticleTypes.SMOKE);
        this.sendShadowRing(level, this.duskVeilCenter, DUSK_VEIL_RADIUS, 54, ParticleTypes.REVERSE_PORTAL);
        return true;
    }

    private void tickDuskVeil(ServerLevel level) {
        --this.duskVeilTicks;
        if (this.duskVeilCenter == null) {
            this.duskVeilCenter = this.position();
        }

        if (this.duskVeilTicks % 5 == 0) {
            double pulseRadius = 3.0D + ((DUSK_VEIL_DURATION_TICKS - this.duskVeilTicks) % 80) / 80.0D * 8.5D;
            this.sendShadowRing(level, this.duskVeilCenter, pulseRadius, 24, ParticleTypes.SMOKE);
        }

        if (this.duskVeilTicks % VEIL_EFFECT_INTERVAL == 0) {
            LivingEntity primary = this.getPrimaryShadowTarget();
            for (LivingEntity target : this.findShadowThreats(this.duskVeilCenter, DUSK_VEIL_RADIUS, primary)) {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 0));
            }
        }

        if (this.duskVeilTicks <= 0) {
            this.duskVeilTicks = 0;
            this.duskVeilCenter = null;
            this.entityData.set(DATA_VEIL_ACTIVE, false);
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 20);
        }
    }

    // ---------------------------------------------------------------------
    // 5) Shadow Assassin — multi-strike ultimate
    // ---------------------------------------------------------------------
    private boolean tryStartShadowAssassin(ServerLevel level) {
        if (!this.canUseActiveShadowAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.SHADOW_ASSASSIN_COOLDOWN.get())
                || this.hasPackActiveShadowAssassin()) {
            return false;
        }

        LivingEntity primary = this.getPrimaryShadowTarget();
        if (primary == null || primary instanceof Creeper || !this.isValidShadowCombatTarget(primary)) {
            return false;
        }

        List<LivingEntity> threats = this.findShadowThreats(this.position(), 12.0D, primary);
        boolean heavyweight = primary.getMaxHealth() >= 60.0F;
        if (threats.size() < 3 && !heavyweight) {
            return false;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_ASSASSIN_COOLDOWN.get(), SHADOW_ASSASSIN_COOLDOWN_TICKS);
        this.shadowAssassinTicks = SHADOW_ASSASSIN_DURATION_TICKS;
        this.lastAssassinTargetId = -1;
        this.entityData.set(DATA_ASSASSIN_ACTIVE, true);
        this.applyPackAbilityLockout(SHADOW_ASSASSIN_DURATION_TICKS);

        this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SHADOW_ASSASSIN_DURATION_TICKS + 5, 0, true, false));
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, SHADOW_ASSASSIN_DURATION_TICKS + 5, 1, true, false));
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.55D, this.getZ(),
                45, 0.75D, 0.65D, 0.75D, 0.08D);
        this.sendShadowRing(level, this.position(), 3.5D, 32, ParticleTypes.WITCH);
        return true;
    }

    private void tickShadowAssassin(ServerLevel level) {
        --this.shadowAssassinTicks;

        if (this.shadowAssassinTicks % 3 == 0) {
            WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.SMOKE, this.getX(), this.getY() + 0.35D, this.getZ(),
                    2, 0.20D, 0.20D, 0.20D, 0.01D);
        }

        if (this.shadowAssassinTicks > 0 && this.shadowAssassinTicks % ASSASSIN_STRIKE_INTERVAL == 0) {
            this.performAssassinStrike(level);
        }

        if (this.shadowAssassinTicks <= 0) {
            this.shadowAssassinTicks = 0;
            this.entityData.set(DATA_ASSASSIN_ACTIVE, false);
            this.lastAssassinTargetId = -1;
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 24);
            WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.50D, this.getZ(),
                    24, 0.45D, 0.45D, 0.45D, 0.05D);
        }
    }

    private void performAssassinStrike(ServerLevel level) {
        LivingEntity primary = this.getPrimaryShadowTarget();
        List<LivingEntity> threats = this.findShadowThreats(this.position(), ASSASSIN_SEARCH_RADIUS, primary).stream()
                .sorted(Comparator.comparingDouble(this::distanceToSqr))
                .toList();
        if (threats.isEmpty()) {
            return;
        }

        LivingEntity target = threats.stream()
                .filter(candidate -> candidate.getId() != this.lastAssassinTargetId)
                .findFirst()
                .orElse(threats.getFirst());

        Vec3 origin = this.position();
        Vec3 around = target.position().subtract(origin);
        Vec3 horizontal = new Vec3(around.x, 0.0D, around.z);
        if (horizontal.lengthSqr() < 1.0E-5D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 desired = target.position().subtract(horizontal.normalize().scale(1.8D));
        Vec3 safe = this.findSafeShadowPosition(level, desired, target.getY());
        if (safe == null) {
            safe = this.findSafeShadowPosition(level, target.position(), target.getY());
        }
        if (safe == null) {
            return;
        }

        this.getNavigation().stop();
        this.setPos(safe.x, safe.y, safe.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.getLookControl().setLookAt(target, 90.0F, 90.0F);
        this.lastAssassinTargetId = target.getId();

        if (target.hurtServer(
                level,
                this.damageSources().mobAttack(this),
                ASSASSIN_STRIKE_DAMAGE * this.getShadowPowerMultiplier())) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 35, 0));
        }

        this.sendDashTrail(level, origin, safe);
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(), 3, 0.24D, 0.24D, 0.24D, 0.0D);
        WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.REVERSE_PORTAL, safe.x, safe.y + 0.40D, safe.z,
                16, 0.28D, 0.28D, 0.28D, 0.05D);
    }

    // ---------------------------------------------------------------------
    // Family / pack / cooldown helpers
    // ---------------------------------------------------------------------
    private LivingEntity getPrimaryShadowTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.isValidShadowCombatTarget(target)) {
            return target;
        }
        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && familyTarget.isAlive() && this.isValidShadowCombatTarget(familyTarget)) {
            return familyTarget;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SHADOW_PACK_THREAT.get())
                .orElse(null);
        return shared != null && shared.isAlive() && this.isValidShadowCombatTarget(shared) ? shared : null;
    }

    private List<LivingEntity> findShadowThreats(Vec3 center, double radius, LivingEntity primary) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != this && candidate.isAlive() && this.isShadowAbilityThreat(candidate, primary));
    }

    private boolean canUseActiveShadowAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.shadowBladesTicks <= 0
                && this.duskVeilTicks <= 0
                && this.shadowAssassinTicks <= 0;
    }

    private boolean isCoolingDown(MemoryModuleType<Integer> memory) {
        return this.getBrain().getMemory(memory).orElse(0) > 0;
    }

    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int cooldown = this.getBrain().getMemory(memory).orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(memory, cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(memory);
        }
    }

    private boolean isDashTargetReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        ShadowWolf.class,
                        this.getBoundingBox().inflate(ShadowWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isShadowPackmate(mate))
                .stream()
                .anyMatch(mate -> mate.getBrain()
                        .getMemory(ModMemoryModuleTypes.SHADOW_VOID_DASH_TARGET.get())
                        .orElse(null) == target);
    }

    private boolean hasPackActiveDuskVeil() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        ShadowWolf.class,
                        this.getBoundingBox().inflate(ShadowWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isShadowPackmate(mate))
                .stream()
                .anyMatch(ShadowWolf::isDuskVeilActive);
    }

    private boolean hasPackActiveShadowAssassin() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        ShadowWolf.class,
                        this.getBoundingBox().inflate(ShadowWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isShadowPackmate(mate))
                .stream()
                .anyMatch(ShadowWolf::isShadowAssassinActive);
    }

    private void applyPackAbilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, ticks);
            return;
        }
        for (ShadowWolf mate : level.getEntitiesOfClass(
                ShadowWolf.class,
                this.getBoundingBox().inflate(ShadowWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isShadowPackmate(wolf)))) {
            mate.abilityLockoutTicks = Math.max(mate.abilityLockoutTicks, ticks);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidShadowCombatTarget(threat)) {
            return;
        }
        for (ShadowWolf mate : level.getEntitiesOfClass(
                ShadowWolf.class,
                this.getBoundingBox().inflate(ShadowWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isShadowPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.SHADOW_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidShadowCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    private void tickVisualTimers() {
        if (this.dashVisualTicks > 0 && --this.dashVisualTicks <= 0) {
            this.dashVisualTicks = 0;
            this.entityData.set(DATA_DASH_ACTIVE, false);
            this.getBrain().eraseMemory(ModMemoryModuleTypes.SHADOW_VOID_DASH_TARGET.get());
        }
    }

    private void cancelActiveShadowAbilities() {
        this.shadowBladesTicks = 0;
        this.duskVeilTicks = 0;
        this.shadowAssassinTicks = 0;
        this.duskVeilCenter = null;
        this.entityData.set(DATA_BLADES_ACTIVE, false);
        this.entityData.set(DATA_VEIL_ACTIVE, false);
        this.entityData.set(DATA_ASSASSIN_ACTIVE, false);
    }

    // ---------------------------------------------------------------------
    // Teleport / particle helpers
    // ---------------------------------------------------------------------
    private Vec3 findSafeShadowPosition(ServerLevel level, Vec3 desired, double targetY) {
        double[][] offsets = {
                {0.0D, 0.0D}, {1.5D, 0.0D}, {-1.5D, 0.0D}, {0.0D, 1.5D}, {0.0D, -1.5D},
                {2.0D, 2.0D}, {-2.0D, 2.0D}, {2.0D, -2.0D}, {-2.0D, -2.0D}
        };
        for (int dy = 1; dy >= -1; --dy) {
            for (double[] offset : offsets) {
                BlockPos pos = BlockPos.containing(desired.x + offset[0], targetY + dy, desired.z + offset[1]);
                if (level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir()
                        && !level.getBlockState(pos.below()).isAir()) {
                    return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                }
            }
        }
        return null;
    }

    private void sendDashTrail(ServerLevel level, Vec3 start, Vec3 end) {
        for (int i = 1; i <= 10; ++i) {
            double t = i / 11.0D;
            Vec3 point = start.lerp(end, t);
            WolfVfx.sendParticles("shadow_wolf", level,
                    i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SMOKE,
                    point.x, point.y + 0.38D, point.z,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private void sendShadowBladeOrbit(ServerLevel level) {
        if (this.tickCount % 2 != 0) {
            return;
        }
        double phase = this.tickCount * 0.22D;
        for (int i = 0; i < 4; ++i) {
            double angle = phase + Math.PI * 0.5D * i;
            double radius = 1.35D;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + 0.55D + Math.sin(angle * 2.0D) * 0.18D;
            WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.WITCH, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            if (this.tickCount % 8 == 0) {
                WolfVfx.sendParticles("shadow_wolf", level, ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void sendShadowRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("shadow_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.16D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    public boolean isVoidDashActive() {
        return this.entityData.get(DATA_DASH_ACTIVE);
    }

    public boolean isShadowBladesActive() {
        return this.entityData.get(DATA_BLADES_ACTIVE);
    }

    public boolean isDuskVeilActive() {
        return this.entityData.get(DATA_VEIL_ACTIVE);
    }

    public boolean isShadowAssassinActive() {
        return this.entityData.get(DATA_ASSASSIN_ACTIVE);
    }

    public static boolean checkShadowWolfSpawnRules(
            EntityType<ShadowWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        boolean night = level instanceof Level actual && actual.isDarkOutside();
        return night && level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON);
    }
}
