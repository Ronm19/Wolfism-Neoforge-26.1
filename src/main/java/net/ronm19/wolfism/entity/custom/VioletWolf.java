package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
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
import net.ronm19.wolfism.entity.ai.sensor.VioletWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Violet Wolf: a precision combat-support specialist.
 *
 * <p>Violet is deliberately more aggressive than Cherry or Spirit. Her personal
 * attacks open enemies up, while Graceful Leap, Violet Aura, Shattering Howl,
 * and Twilight Bloom turn a coordinated family into a much stronger fighting
 * unit. Major abilities are mutually gated so their visuals and purposes remain
 * readable in combat.</p>
 */
public final class VioletWolf extends AbstractWolfismWolf {
    public static final int GRACEFUL_LEAP_COOLDOWN_TICKS = 20 * 12;
    public static final int VIOLET_AURA_COOLDOWN_TICKS = 20 * 18;
    public static final int SHATTERING_HOWL_COOLDOWN_TICKS = 20 * 22;
    public static final int TWILIGHT_BLOOM_COOLDOWN_TICKS = 20 * 90;

    public static final int VIOLET_AURA_DURATION_TICKS = 20 * 8;
    public static final int TWILIGHT_BLOOM_DURATION_TICKS = 20 * 12;

    public static final double GRACEFUL_LEAP_MIN_RANGE = 4.0D;
    public static final double GRACEFUL_LEAP_MAX_RANGE = 12.0D;
    public static final double VIOLET_AURA_RADIUS = 10.0D;
    public static final double SHATTERING_HOWL_RADIUS = 12.0D;
    public static final double TWILIGHT_BLOOM_RADIUS = 12.0D;

    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int AURA_PULSE_INTERVAL = 20;
    private static final int BLOOM_PULSE_INTERVAL = 20;
    private static final int LEAP_MAX_TICKS = 16;
    private static final float GRACEFUL_LEAP_DAMAGE = 6.0F;
    private static final float SHATTERING_HOWL_DAMAGE = 4.0F;
    private static final float TWILIGHT_BLOOM_DAMAGE = 2.0F;

    private int violetAuraTicks;
    private int twilightBloomTicks;
    private int gracefulLeapTicks;
    private int abilityLockoutTicks;
    private LivingEntity gracefulLeapTarget;
    private Vec3 twilightBloomCenter;

    private static final class BrainHolder {
        private static final Brain.Provider<VioletWolf> PROVIDER = Brain.<VioletWolf>provider(
                ImmutableList.of(ModSensorTypes.VIOLET_PACK.get()),
                wolf -> List.of());
    }

    public VioletWolf(EntityType<? extends VioletWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.VIOLET_WOLF.get();
    }

    @Override
    protected Brain<VioletWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<VioletWolf> getBrain() {
        return (Brain<VioletWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof VioletWolf violet && this.isVioletPackmate(violet)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.VIOLET_LEAP_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.VIOLET_AURA_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.VIOLET_HOWL_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.VIOLET_BLOOM_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelVioletAbilities();
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.VIOLET_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidVioletCombatTarget(sharedThreat)) {
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

        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        if (this.isBaby()) {
            return;
        }

        if (this.gracefulLeapTicks > 0) {
            this.tickGracefulLeap(level);
            return;
        }

        if (this.twilightBloomTicks > 0) {
            this.tickTwilightBloom(level);
            return;
        }

        if (this.violetAuraTicks > 0) {
            this.tickVioletAura(level);
            // Aura is a support stance; ordinary melee remains available, but
            // other named abilities wait until the aura has had its own moment.
            return;
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            if (this.tryStartTwilightBloom(level)) {
                return;
            }
            if (this.tryShatteringHowl(level)) {
                return;
            }
            if (this.tryStartVioletAura(level)) {
                return;
            }
            this.tryStartGracefulLeap(level);
        }
    }

    /**
     * Violet Strike: 30% of successful ordinary melee attacks briefly weaken the
     * victim. It remains passive and therefore never competes for an active slot.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(entity);
        if (hurt && !this.isBaby() && entity instanceof LivingEntity target && this.random.nextFloat() < 0.30F) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 4, 0), this);
            WolfVfx.sendParticles("violet_wolf", level,
                    ParticleTypes.WITCH,
                    target.getX(), target.getY(0.62D), target.getZ(),
                    14, 0.28D, 0.34D, 0.28D, 0.05D);
            WolfVfx.sendParticles("violet_wolf", level,
                    ParticleTypes.ENCHANTED_HIT,
                    target.getX(), target.getY(0.55D), target.getZ(),
                    9, 0.25D, 0.25D, 0.25D, 0.08D);
        }
        return hurt;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof VioletWolf violet && this.isVioletPackmate(violet)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isVioletPackmate(VioletWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isVioletFamilyMember(LivingEntity entity) {
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
        return entity instanceof VioletWolf violet && this.isVioletPackmate(violet);
    }

    public boolean isValidVioletCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof VioletWolf violet && this.isVioletPackmate(violet)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private boolean isVioletAbilityThreat(LivingEntity candidate, LivingEntity primary) {
        if (candidate instanceof Creeper || !this.isValidVioletCombatTarget(candidate)) {
            return false;
        }
        if (candidate == primary
                || candidate == this.getTarget()
                || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VIOLET_PACK_THREAT.get())
                .orElse(null);
        if (candidate == shared) {
            return true;
        }
        // Broad AoE only accepts naturally hostile mobs; innocent animals and
        // unrelated wolves never become collateral just because they are nearby.
        return candidate instanceof Enemy;
    }

    // ---------------------------------------------------------------------
    // 2. Graceful Leap
    // ---------------------------------------------------------------------
    private boolean tryStartGracefulLeap(ServerLevel level) {
        if (!this.canUseActiveVioletAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.VIOLET_LEAP_COOLDOWN.get())) {
            return false;
        }

        LivingEntity target = this.getPrimaryVioletTarget();
        if (target == null || target instanceof Creeper) {
            return false;
        }

        double distance = this.distanceTo(target);
        if (distance < GRACEFUL_LEAP_MIN_RANGE || distance > GRACEFUL_LEAP_MAX_RANGE) {
            return false;
        }

        this.gracefulLeapTarget = target;
        this.gracefulLeapTicks = LEAP_MAX_TICKS;
        this.getBrain().setMemory(ModMemoryModuleTypes.VIOLET_LEAP_TARGET.get(), target);
        this.setPackCooldown(ModMemoryModuleTypes.VIOLET_LEAP_COOLDOWN.get(), GRACEFUL_LEAP_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(16);
        this.getNavigation().stop();

        Vec3 toward = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() > 1.0E-6D) {
            horizontal = horizontal.normalize().scale(1.15D);
        }
        this.setDeltaMovement(horizontal.x, 0.44D + Math.min(0.18D, Math.max(0.0D, toward.y * 0.08D)), horizontal.z);
        this.hurtMarked = true;

        WolfVfx.sendParticles("violet_wolf", level,
                ParticleTypes.WITCH,
                this.getX(), this.getY(0.45D), this.getZ(),
                24, 0.32D, 0.22D, 0.32D, 0.08D);
        return true;
    }

    private void tickGracefulLeap(ServerLevel level) {
        LivingEntity target = this.gracefulLeapTarget;
        if (target == null || !target.isAlive() || !this.isValidVioletCombatTarget(target)) {
            this.finishGracefulLeap();
            return;
        }

        --this.gracefulLeapTicks;
        WolfVfx.sendParticles("violet_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY(0.45D), this.getZ(),
                4, 0.18D, 0.16D, 0.18D, 0.01D);

        Vec3 toward = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() > 1.0E-6D) {
            Vec3 steer = horizontal.normalize().scale(0.18D);
            Vec3 current = this.getDeltaMovement();
            this.setDeltaMovement(current.x * 0.86D + steer.x, current.y, current.z * 0.86D + steer.z);
            this.hurtMarked = true;
        }

        if (this.distanceToSqr(target) <= 3.4D) {
            if (target.hurtServer(level, this.damageSources().mobAttack(this), GRACEFUL_LEAP_DAMAGE)) {
                // "Stun" without introducing a custom effect: very strong short
                // Slowness plus Weakness prevents meaningful retaliation for 1.5s.
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5), this);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1), this);
                Vec3 away = target.position().subtract(this.position());
                if (away.lengthSqr() > 1.0E-6D) {
                    Vec3 push = away.normalize().scale(0.35D);
                    target.push(push.x, 0.15D, push.z);
                }
                WolfVfx.sendParticles("violet_wolf", level,
                        ParticleTypes.WITCH,
                        target.getX(), target.getY(0.55D), target.getZ(),
                        30, 0.35D, 0.42D, 0.35D, 0.04D);
            }
            this.finishGracefulLeap();
            return;
        }

        if (this.gracefulLeapTicks <= 0) {
            this.finishGracefulLeap();
        }
    }

    private void finishGracefulLeap() {
        this.gracefulLeapTicks = 0;
        this.gracefulLeapTarget = null;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.VIOLET_LEAP_TARGET.get());
    }

    // ---------------------------------------------------------------------
    // 3. Violet Aura
    // ---------------------------------------------------------------------
    private boolean tryStartVioletAura(ServerLevel level) {
        if (!this.canUseActiveVioletAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.VIOLET_AURA_COOLDOWN.get())
                || this.hasPackActiveAura()) {
            return false;
        }

        LivingEntity target = this.getPrimaryVioletTarget();
        if (target == null) {
            return false;
        }

        long nearbyFamily = this.findFamily(VIOLET_AURA_RADIUS).stream()
                .filter(member -> member != this)
                .count();
        if (nearbyFamily < 1) {
            return false;
        }

        this.violetAuraTicks = VIOLET_AURA_DURATION_TICKS;
        this.setPackCooldown(ModMemoryModuleTypes.VIOLET_AURA_COOLDOWN.get(), VIOLET_AURA_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(20);
        this.sendVioletRing(level, this.position(), 2.2D, 28, ParticleTypes.WITCH);
        return true;
    }

    private void tickVioletAura(ServerLevel level) {
        --this.violetAuraTicks;

        if (this.tickCount % 5 == 0) {
            this.sendVioletRing(level, this.position(), VIOLET_AURA_RADIUS * 0.55D, 24, ParticleTypes.WITCH);
        }
        if (this.tickCount % AURA_PULSE_INTERVAL == 0) {
            for (LivingEntity family : this.findFamily(VIOLET_AURA_RADIUS)) {
                family.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, true, true), this);
                WolfVfx.sendParticles("violet_wolf", level,
                        ParticleTypes.ENCHANTED_HIT,
                        family.getX(), family.getY(0.55D), family.getZ(),
                        6, 0.22D, 0.28D, 0.22D, 0.04D);
            }
        }

        if (this.violetAuraTicks <= 0) {
            this.violetAuraTicks = 0;
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 10);
        }
    }

    // ---------------------------------------------------------------------
    // 4. Shattering Howl
    // ---------------------------------------------------------------------
    private boolean tryShatteringHowl(ServerLevel level) {
        if (!this.canUseActiveVioletAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.VIOLET_HOWL_COOLDOWN.get())) {
            return false;
        }

        LivingEntity primary = this.getPrimaryVioletTarget();
        List<LivingEntity> threats = this.findVioletThreats(this.position(), SHATTERING_HOWL_RADIUS, primary);
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VIOLET_INJURED_FAMILY.get())
                .orElse(null);
        boolean familyEmergency = injured != null
                && injured.getHealth() / injured.getMaxHealth() <= 0.45F
                && !threats.isEmpty();
        if (threats.size() < 2 && !familyEmergency) {
            return false;
        }

        this.setPackCooldown(ModMemoryModuleTypes.VIOLET_HOWL_COOLDOWN.get(), SHATTERING_HOWL_COOLDOWN_TICKS);
        this.playSound(this.getWolfismHowlSound(), 1.25F, 1.0F);
        this.applyPackAbilityLockout(24);

        this.sendVioletRing(level, this.position(), 3.0D, 32, ParticleTypes.REVERSE_PORTAL);
        this.sendVioletRing(level, this.position(), 6.0D, 40, ParticleTypes.WITCH);
        this.sendVioletRing(level, this.position(), SHATTERING_HOWL_RADIUS, 52, ParticleTypes.ENCHANTED_HIT);

        for (LivingEntity threat : threats) {
            if (threat.hurtServer(level, this.damageSources().mobAttack(this), SHATTERING_HOWL_DAMAGE)) {
                threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
                Vec3 away = threat.position().subtract(this.position());
                if (away.lengthSqr() > 1.0E-6D) {
                    Vec3 push = away.normalize().scale(0.95D);
                    threat.push(push.x, 0.24D, push.z);
                }
            }
        }

        for (LivingEntity family : this.findFamily(SHATTERING_HOWL_RADIUS)) {
            family.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1, true, true), this);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // 5. Twilight Bloom (ultimate)
    // ---------------------------------------------------------------------
    private boolean tryStartTwilightBloom(ServerLevel level) {
        if (!this.canUseActiveVioletAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.VIOLET_BLOOM_COOLDOWN.get())
                || this.hasPackActiveBloom()) {
            return false;
        }

        LivingEntity primary = this.getPrimaryVioletTarget();
        List<LivingEntity> threats = this.findVioletThreats(this.position(), TWILIGHT_BLOOM_RADIUS, primary);
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VIOLET_INJURED_FAMILY.get())
                .orElse(null);
        boolean criticalFamily = injured != null
                && injured.getHealth() / injured.getMaxHealth() <= 0.35F
                && !threats.isEmpty();
        boolean heavyweight = primary != null && primary.getMaxHealth() >= 60.0F;
        if (threats.size() < 3 && !criticalFamily && !heavyweight) {
            return false;
        }

        this.twilightBloomCenter = criticalFamily ? injured.position() : this.position();
        this.twilightBloomTicks = TWILIGHT_BLOOM_DURATION_TICKS;
        this.setPackCooldown(ModMemoryModuleTypes.VIOLET_BLOOM_COOLDOWN.get(), TWILIGHT_BLOOM_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(50);

        this.sendVioletRing(level, this.twilightBloomCenter, 3.0D, 32, ParticleTypes.WITCH);
        this.sendVioletRing(level, this.twilightBloomCenter, 7.0D, 44, ParticleTypes.WITCH);
        this.sendVioletRing(level, this.twilightBloomCenter, TWILIGHT_BLOOM_RADIUS, 64, ParticleTypes.REVERSE_PORTAL);
        return true;
    }

    private void tickTwilightBloom(ServerLevel level) {
        if (this.twilightBloomCenter == null) {
            this.twilightBloomTicks = 0;
            return;
        }

        --this.twilightBloomTicks;
        if (this.tickCount % 4 == 0) {
            double radius = 4.0D + 4.0D * (0.5D + 0.5D * Math.sin(this.tickCount * 0.23D));
            this.sendVioletRing(level, this.twilightBloomCenter, radius, 34, ParticleTypes.WITCH);
        }

        if (this.tickCount % BLOOM_PULSE_INTERVAL == 0) {
            AABB zone = new AABB(
                    this.twilightBloomCenter.x - TWILIGHT_BLOOM_RADIUS,
                    this.twilightBloomCenter.y - TWILIGHT_BLOOM_RADIUS,
                    this.twilightBloomCenter.z - TWILIGHT_BLOOM_RADIUS,
                    this.twilightBloomCenter.x + TWILIGHT_BLOOM_RADIUS,
                    this.twilightBloomCenter.y + TWILIGHT_BLOOM_RADIUS,
                    this.twilightBloomCenter.z + TWILIGHT_BLOOM_RADIUS);

            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, zone, LivingEntity::isAlive)) {
                if (this.isVioletFamilyMember(candidate)) {
                    candidate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, 1, true, true), this);
                    candidate.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 35, 0, true, true), this);
                    candidate.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 45, 0, true, true), this);
                    WolfVfx.sendParticles("violet_wolf", level,
                            ParticleTypes.ENCHANTED_HIT,
                            candidate.getX(), candidate.getY(0.55D), candidate.getZ(),
                            7, 0.24D, 0.30D, 0.24D, 0.04D);
                } else if (this.isVioletAbilityThreat(candidate, this.getPrimaryVioletTarget())) {
                    if (candidate.hurtServer(level, this.damageSources().mobAttack(this), TWILIGHT_BLOOM_DAMAGE)) {
                        candidate.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 1), this);
                        candidate.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, 1), this);
                    }
                }
            }
        }

        if (this.twilightBloomTicks <= 0) {
            this.twilightBloomTicks = 0;
            this.twilightBloomCenter = null;
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 20);
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers / family coordination
    // ---------------------------------------------------------------------
    private LivingEntity getPrimaryVioletTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.isValidVioletCombatTarget(target)) {
            return target;
        }
        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && familyTarget.isAlive() && this.isValidVioletCombatTarget(familyTarget)) {
            return familyTarget;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VIOLET_PACK_THREAT.get())
                .orElse(null);
        return shared != null && shared.isAlive() && this.isValidVioletCombatTarget(shared) ? shared : null;
    }

    private List<LivingEntity> findFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isVioletFamilyMember);
    }

    private List<LivingEntity> findVioletThreats(Vec3 center, double radius, LivingEntity primary) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isVioletAbilityThreat(candidate, primary));
    }

    private boolean canUseActiveVioletAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.gracefulLeapTicks <= 0
                && this.violetAuraTicks <= 0
                && this.twilightBloomTicks <= 0;
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

    private boolean hasPackActiveAura() {
        return this.hasPackAbility(wolf -> wolf.violetAuraTicks > 0);
    }

    private boolean hasPackActiveBloom() {
        return this.hasPackAbility(wolf -> wolf.twilightBloomTicks > 0);
    }

    private boolean hasPackAbility(java.util.function.Predicate<VioletWolf> predicate) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        VioletWolf.class,
                        this.getBoundingBox().inflate(VioletWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isVioletPackmate(mate))
                .stream()
                .anyMatch(predicate);
    }

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }
        for (VioletWolf mate : level.getEntitiesOfClass(
                VioletWolf.class,
                this.getBoundingBox().inflate(VioletWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isVioletPackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void applyPackAbilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, ticks);
            return;
        }
        for (VioletWolf mate : level.getEntitiesOfClass(
                VioletWolf.class,
                this.getBoundingBox().inflate(VioletWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isVioletPackmate(wolf)))) {
            mate.abilityLockoutTicks = Math.max(mate.abilityLockoutTicks, ticks);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidVioletCombatTarget(threat)) {
            return;
        }
        for (VioletWolf mate : level.getEntitiesOfClass(
                VioletWolf.class,
                this.getBoundingBox().inflate(VioletWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isVioletPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.VIOLET_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidVioletCombatTarget(threat)) {
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

    private void cancelVioletAbilities() {
        this.gracefulLeapTicks = 0;
        this.gracefulLeapTarget = null;
        this.violetAuraTicks = 0;
        this.twilightBloomTicks = 0;
        this.twilightBloomCenter = null;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.VIOLET_LEAP_TARGET.get());
    }

    private void sendVioletRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("violet_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.16D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public static boolean checkVioletWolfSpawnRules(
            EntityType<VioletWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
