package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
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
import net.ronm19.wolfism.entity.ai.goal.LunarBeamGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarDreamstepGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarMoonShieldGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarMoonlitHowlGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarNightWatchTargetGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.LunarPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.LunarWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Lunar Wolf: a precision night-watch guardian and Solar Wolf's thematic opposite.
 * Moonlight improves awareness and support; her active kit focuses on controlled
 * repositioning, protection, weakening enemies, and a precise piercing beam.
 */
public final class LunarWolf extends AbstractWolfismWolf {
    public static final int LUNAR_BEAM_COOLDOWN_TICKS = 20 * 14;
    public static final int MOON_SHIELD_COOLDOWN_TICKS = 20 * 28;
    public static final int DREAMSTEP_COOLDOWN_TICKS = 20 * 12;
    public static final int MOONLIT_HOWL_COOLDOWN_TICKS = 20 * 85;

    public static final float LUNAR_BEAM_DAMAGE = 6.5F;
    public static final double MOON_SHIELD_RADIUS = 7.5D;
    public static final double LUNAR_AURA_RADIUS = 8.0D;
    public static final double MOONLIT_HOWL_RADIUS = 12.0D;

    private static final int LUNAR_AURA_INTERVAL = 40;
    private static final int BEAM_VISUAL_TICKS = 10;
    private static final int SHIELD_VISUAL_TICKS = 24;
    private static final int DREAMSTEP_VISUAL_TICKS = 10;
    private static final int HOWL_VISUAL_TICKS = 28;

    private static final EntityDataAccessor<Boolean> DATA_BEAM_ACTIVE =
            SynchedEntityData.defineId(LunarWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET_ID =
            SynchedEntityData.defineId(LunarWolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHIELD_ACTIVE =
            SynchedEntityData.defineId(LunarWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DREAMSTEP_ACTIVE =
            SynchedEntityData.defineId(LunarWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOWL_ACTIVE =
            SynchedEntityData.defineId(LunarWolf.class, EntityDataSerializers.BOOLEAN);

    private int beamVisualTicks;
    private int shieldVisualTicks;
    private int dreamstepVisualTicks;
    private int howlVisualTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<LunarWolf> PROVIDER = Brain.<LunarWolf>provider(
                ImmutableList.of(ModSensorTypes.LUNAR_PACK.get(), ModSensorTypes.LUNAR_NIGHT_WATCH.get()),
                wolf -> List.of());
    }

    public LunarWolf(EntityType<? extends LunarWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_BEAM_ACTIVE, false);
        data.define(DATA_BEAM_TARGET_ID, 0);
        data.define(DATA_SHIELD_ACTIVE, false);
        data.define(DATA_DREAMSTEP_ACTIVE, false);
        data.define(DATA_HOWL_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.LUNAR_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new LunarPupRetreatGoal(this));
        this.goalSelector.addGoal(1, new LunarMoonlitHowlGoal(this));
        this.goalSelector.addGoal(2, new LunarMoonShieldGoal(this));
        this.goalSelector.addGoal(3, new LunarBeamGoal(this));
        this.goalSelector.addGoal(4, new LunarDreamstepGoal(this));
        this.goalSelector.addGoal(7, new LunarPupFollowAdultGoal(this));
        this.goalSelector.addGoal(8, new LunarPackCohesionGoal(this));
        this.goalSelector.addGoal(9, new LunarPupPlayGoal(this));

        this.targetSelector.addGoal(2, new LunarNightWatchTargetGoal(this));
        this.targetSelector.addGoal(3, new LunarPackAssistGoal(this));
    }

    @Override
    protected Brain<LunarWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<LunarWolf> getBrain() {
        return (Brain<LunarWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof LunarWolf lunar && this.isLunarPackmate(lunar)) this.setTarget(null);

        this.tickCooldown(ModMemoryModuleTypes.LUNAR_BEAM_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.LUNAR_SHIELD_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.LUNAR_DREAMSTEP_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.LUNAR_HOWL_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.clearLunarBeamVisual();
        }
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;

        if (this.beamVisualTicks > 0 && --this.beamVisualTicks <= 0) this.clearLunarBeamVisual();
        if (this.shieldVisualTicks > 0 && --this.shieldVisualTicks <= 0) {
            this.entityData.set(DATA_SHIELD_ACTIVE, false);
        }
        if (this.dreamstepVisualTicks > 0 && --this.dreamstepVisualTicks <= 0) {
            this.entityData.set(DATA_DREAMSTEP_ACTIVE, false);
        }
        if (this.howlVisualTicks > 0 && --this.howlVisualTicks <= 0) {
            this.entityData.set(DATA_HOWL_ACTIVE, false);
        }

        if (!this.isBaby() && this.tickCount % LUNAR_AURA_INTERVAL == 0) this.tickLunarAura(level);
        if (this.isNightEmpowered() && this.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.55D, this.getZ(), 1, 0.20D, 0.18D, 0.20D, 0.002D);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) return false;
        if (target instanceof LunarWolf lunar && this.isLunarPackmate(lunar)) return false;
        return super.canAttack(target);
    }

    public boolean isNightEmpowered() {
        return this.level().isDarkOutside();
    }

    public boolean isInDirectMoonlight() {
        return this.isNightEmpowered() && this.level().canSeeSky(this.blockPosition());
    }

    public float getLunarPowerMultiplier() {
        if (this.isInDirectMoonlight()) return 1.25F;
        if (this.isNightEmpowered()) return 1.10F;
        return 0.85F;
    }

    public boolean isLunarPackmate(LunarWolf other) {
        if (other == this || other.isTame() != this.isTame()) return false;
        if (!this.isTame()) return true;
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidLunarCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) return false;
        if (target instanceof LunarWolf lunar && this.isLunarPackmate(lunar)) return false;
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private boolean isLunarAbilityThreat(LivingEntity candidate, LivingEntity primary) {
        if (!this.isValidLunarCombatTarget(candidate)) return false;
        if (candidate == primary || candidate == this.getTarget() || candidate == this.getFamilyDefenseTarget()) return true;
        LivingEntity shared = this.getBrain().getMemory(ModMemoryModuleTypes.LUNAR_PACK_THREAT.get()).orElse(null);
        LivingEntity watch = this.getBrain().getMemory(ModMemoryModuleTypes.LUNAR_WATCH_THREAT.get()).orElse(null);
        return candidate == shared || candidate == watch || candidate instanceof Enemy;
    }

    public boolean isLunarFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (this.isTame()) {
            if (entity == this.getOwner()) return true;
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof LunarWolf lunar && this.isLunarPackmate(lunar);
    }

    // ------------------------------------------------------------------
    // Moonlit Sight + Lunar Aura
    // ------------------------------------------------------------------
    private void tickLunarAura(ServerLevel level) {
        if (!this.isNightEmpowered()) return;
        AABB area = this.getBoundingBox().inflate(LUNAR_AURA_RADIUS);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
        for (LivingEntity entity : entities) {
            if (this.isLunarFamilyMember(entity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
                entity.removeEffect(MobEffects.DARKNESS);
                continue;
            }
            if (entity instanceof Enemy && this.isValidLunarCombatTarget(entity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
                continue;
            }
            // Calm neutral/non-hostile mobs that are actively aggroed onto Lunar family.
            if (entity instanceof Mob mob && !(entity instanceof Enemy) && this.isLunarFamilyMember(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        if (this.tickCount % 80 == 0) this.sendLunarRing(level, this.position().add(0.0D, 0.12D, 0.0D), 3.1D, 20);
    }

    // ------------------------------------------------------------------
    // Lunar Beam
    // ------------------------------------------------------------------
    public boolean canStartLunarBeamAgainst(LivingEntity target) {
        if (!this.canUseActiveLunarAbility() || target == null || target instanceof Creeper
                || !this.isValidLunarCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.LUNAR_BEAM_COOLDOWN.get())
                || this.isAbilityTargetReservedByPack(target, ModMemoryModuleTypes.LUNAR_BEAM_TARGET.get())) return false;
        double d = this.distanceToSqr(target);
        return d >= LunarBeamGoal.MIN_START_DISTANCE_SQR
                && d <= LunarBeamGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target);
    }

    public void reserveLunarBeamTarget(LivingEntity target) {
        if (target != null && target.isAlive()) this.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_BEAM_TARGET.get(), target);
    }

    public void clearLunarBeamReservation() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.LUNAR_BEAM_TARGET.get());
    }

    public boolean performLunarBeam(LivingEntity target) {
        if (!this.canStartLunarBeamAgainst(target) || !(this.level() instanceof ServerLevel level)) return false;
        Vec3 start = this.position().add(0.0D, 0.62D, 0.0D);
        Vec3 raw = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D).subtract(start);
        double length = Math.min(20.0D, raw.length());
        if (length < 0.01D) return false;
        Vec3 dir = raw.normalize();
        Vec3 end = start.add(dir.scale(length));

        AABB beamBox = new AABB(start, end).inflate(1.10D);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(
                LivingEntity.class, beamBox,
                entity -> entity != this && !(entity instanceof Creeper) && this.isLunarAbilityThreat(entity, target)));
        candidates.sort(Comparator.comparingDouble(entity -> projectionAlongBeam(start, dir, entity.position())));

        int hits = 0;
        float damage = LUNAR_BEAM_DAMAGE * this.getLunarPowerMultiplier();
        for (LivingEntity candidate : candidates) {
            double projection = projectionAlongBeam(start, dir, candidate.position());
            if (projection < 0.0D || projection > length) continue;
            Vec3 closest = start.add(dir.scale(projection));
            Vec3 center = candidate.position().add(0.0D, candidate.getBbHeight() * 0.5D, 0.0D);
            if (center.distanceToSqr(closest) > 1.25D * 1.25D) continue;
            if (candidate.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
                candidate.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                candidate.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }
            level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 12, 0.16D, 0.22D, 0.16D, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 6, 0.12D, 0.18D, 0.12D, 0.015D);
            if (++hits >= 3) break;
        }

        for (double d = 0.6D; d <= length; d += 1.5D) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, 0.035D, 0.035D, 0.035D, 0.0D);
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_BEAM_COOLDOWN.get(), LUNAR_BEAM_COOLDOWN_TICKS);
        this.clearLunarBeamReservation();
        this.beamVisualTicks = BEAM_VISUAL_TICKS;
        this.entityData.set(DATA_BEAM_TARGET_ID, target.getId());
        this.entityData.set(DATA_BEAM_ACTIVE, true);
        return hits > 0;
    }

    private static double projectionAlongBeam(Vec3 start, Vec3 dir, Vec3 point) {
        return point.subtract(start).dot(dir);
    }

    private void clearLunarBeamVisual() {
        this.beamVisualTicks = 0;
        this.entityData.set(DATA_BEAM_ACTIVE, false);
        this.entityData.set(DATA_BEAM_TARGET_ID, 0);
    }

    // ------------------------------------------------------------------
    // Moon Shield
    // ------------------------------------------------------------------
    public boolean canStartMoonShield() {
        if (!this.canUseActiveLunarAbility() || this.isCoolingDown(ModMemoryModuleTypes.LUNAR_SHIELD_COOLDOWN.get())) return false;
        List<LivingEntity> family = this.findFamilyAround(MOON_SHIELD_RADIUS);
        boolean endangered = family.stream().anyMatch(entity -> entity.getHealth() <= entity.getMaxHealth() * 0.60F);
        return endangered || this.hasFamilyDefenseEmergency() || this.findLunarThreatsAround(this, 6.5D).size() >= 2;
    }

    public boolean performMoonShield() {
        if (!this.canStartMoonShield() || !(this.level() instanceof ServerLevel level)) return false;
        List<LivingEntity> family = this.findFamilyAround(MOON_SHIELD_RADIUS);
        if (family.isEmpty()) return false;
        for (LivingEntity member : family) {
            member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 8, 0));
            member.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 8, 1));
            member.removeEffect(MobEffects.DARKNESS);
            level.sendParticles(ParticleTypes.END_ROD, member.getX(), member.getY() + member.getBbHeight() * 0.55D, member.getZ(), 10, 0.35D, 0.45D, 0.35D, 0.01D);
            level.sendParticles(ParticleTypes.WITCH, member.getX(), member.getY() + 0.4D, member.getZ(), 7, 0.35D, 0.25D, 0.35D, 0.01D);
        }
        this.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_SHIELD_COOLDOWN.get(), MOON_SHIELD_COOLDOWN_TICKS);
        this.shieldVisualTicks = SHIELD_VISUAL_TICKS;
        this.entityData.set(DATA_SHIELD_ACTIVE, true);
        this.sendLunarRing(level, this.position().add(0.0D, 0.15D, 0.0D), MOON_SHIELD_RADIUS * 0.72D, 32);
        return true;
    }

    // ------------------------------------------------------------------
    // Dreamstep
    // ------------------------------------------------------------------
    public boolean canStartDreamstepAgainst(LivingEntity target) {
        if (!this.canUseActiveLunarAbility() || target == null || !this.isValidLunarCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.LUNAR_DREAMSTEP_COOLDOWN.get())) return false;
        double d = this.distanceToSqr(target);
        return d >= LunarDreamstepGoal.MIN_START_DISTANCE_SQR && d <= LunarDreamstepGoal.MAX_START_DISTANCE_SQR;
    }

    public boolean performDreamstep(LivingEntity target) {
        if (!this.canStartDreamstepAgainst(target) || !(this.level() instanceof ServerLevel level)) return false;
        Vec3 origin = this.position();
        boolean defensive = this.getHealth() <= this.getMaxHealth() * 0.35F;
        Vec3 fromTarget = this.position().subtract(target.position());
        Vec3 horizontal = new Vec3(fromTarget.x, 0.0D, fromTarget.z);
        if (horizontal.lengthSqr() < 1.0E-5D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        horizontal = horizontal.normalize();

        Vec3 desired;
        if (defensive) {
            desired = target.position().add(horizontal.scale(6.0D));
        } else {
            Vec3 look = target.getLookAngle();
            Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
            if (flatLook.lengthSqr() < 1.0E-5D) flatLook = horizontal.scale(-1.0D);
            desired = target.position().subtract(flatLook.normalize().scale(2.6D));
        }

        Vec3 safe = this.findSafeDreamstepPosition(level, desired, target);
        if (safe == null) return false;
        level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 0.45D, origin.z, 28, 0.28D, 0.40D, 0.28D, 0.08D);
        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 0.45D, origin.z, 8, 0.18D, 0.25D, 0.18D, 0.02D);
        this.getNavigation().stop();
        this.setPos(safe.x, safe.y, safe.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.getLookControl().setLookAt(target, 90.0F, 90.0F);
        level.sendParticles(ParticleTypes.PORTAL, safe.x, safe.y + 0.45D, safe.z, 28, 0.28D, 0.40D, 0.28D, 0.08D);
        level.sendParticles(ParticleTypes.END_ROD, safe.x, safe.y + 0.45D, safe.z, 8, 0.18D, 0.25D, 0.18D, 0.02D);
        this.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_DREAMSTEP_COOLDOWN.get(), DREAMSTEP_COOLDOWN_TICKS);
        this.dreamstepVisualTicks = DREAMSTEP_VISUAL_TICKS;
        this.entityData.set(DATA_DREAMSTEP_ACTIVE, true);
        return true;
    }

    private Vec3 findSafeDreamstepPosition(ServerLevel level, Vec3 desired, LivingEntity target) {
        double[][] offsets = {{0,0},{1.5,0},{-1.5,0},{0,1.5},{0,-1.5},{2,2},{-2,2},{2,-2},{-2,-2}};
        for (int dy = 1; dy >= -1; --dy) {
            for (double[] off : offsets) {
                BlockPos pos = BlockPos.containing(desired.x + off[0], target.getY() + dy, desired.z + off[1]);
                if (level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir()
                        && !level.getBlockState(pos.below()).isAir()) {
                    return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Moonlit Howl ultimate
    // ------------------------------------------------------------------
    public boolean canStartMoonlitHowl() {
        if (!this.canUseActiveLunarAbility() || this.isCoolingDown(ModMemoryModuleTypes.LUNAR_HOWL_COOLDOWN.get())) return false;
        List<LivingEntity> family = this.findFamilyAround(MOONLIT_HOWL_RADIUS);
        boolean criticalFamily = family.stream().anyMatch(entity -> entity.getHealth() <= entity.getMaxHealth() * 0.45F);
        int threats = this.findLunarThreatsAround(this, MOONLIT_HOWL_RADIUS).size();
        return (criticalFamily && threats >= 1) || threats >= 3;
    }

    public boolean performMoonlitHowl() {
        if (!this.canStartMoonlitHowl() || !(this.level() instanceof ServerLevel level)) return false;
        List<LivingEntity> family = this.findFamilyAround(MOONLIT_HOWL_RADIUS);
        for (LivingEntity member : family) {
            member.heal(5.0F);
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
            member.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 10, 0));
            member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 10, 0));
            member.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 15, 0, true, false));
            member.removeEffect(MobEffects.DARKNESS);
        }
        for (LivingEntity threat : this.findLunarThreatsAround(this, MOONLIT_HOWL_RADIUS)) {
            threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 9, 1));
            threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 7, 1));
            threat.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0));
        }
        this.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_HOWL_COOLDOWN.get(), MOONLIT_HOWL_COOLDOWN_TICKS);
        this.howlVisualTicks = HOWL_VISUAL_TICKS;
        this.entityData.set(DATA_HOWL_ACTIVE, true);
        this.sendLunarRing(level, this.position().add(0.0D, 0.12D, 0.0D), 4.0D, 28);
        this.sendLunarRing(level, this.position().add(0.0D, 0.18D, 0.0D), 8.0D, 42);
        this.sendLunarRing(level, this.position().add(0.0D, 0.22D, 0.0D), 11.5D, 56);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0D, this.getZ(), 32, 0.9D, 1.0D, 0.9D, 0.04D);
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 0.7D, this.getZ(), 24, 1.1D, 0.6D, 1.1D, 0.03D);
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers / family / pack
    // ------------------------------------------------------------------
    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel level) || threat == null || !threat.isAlive()) return;
        List<LunarWolf> pack = level.getEntitiesOfClass(
                LunarWolf.class,
                this.getBoundingBox().inflate(LunarWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && (candidate == this || this.isLunarPackmate(candidate)));
        for (LunarWolf mate : pack) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.LUNAR_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidLunarCombatTarget(threat)) {
                LivingEntity current = mate.getTarget();
                if (current == null || !current.isAlive()) mate.setTarget(threat);
            } else if (mate.isBaby()) mate.setTarget(null);
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.clearLunarBeamReservation();
    }

    private boolean canUseActiveLunarAbility() {
        return !this.isBaby() && !this.isOrderedToSit() && !this.isInSittingPose();
    }

    private List<LivingEntity> findFamilyAround(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isLunarFamilyMember);
    }

    private List<LivingEntity> findLunarThreatsAround(LivingEntity anchor, double radius) {
        LivingEntity primary = anchor == this ? this.getTarget() : anchor;
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                anchor.getBoundingBox().inflate(radius),
                candidate -> candidate != this && this.isLunarAbilityThreat(candidate, primary));
    }

    private boolean isCoolingDown(MemoryModuleType<Integer> memory) {
        return this.getBrain().getMemory(memory).orElse(0) > 0;
    }

    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int cooldown = this.getBrain().getMemory(memory).orElse(0);
        if (cooldown > 1) this.getBrain().setMemory(memory, cooldown - 1);
        else if (cooldown == 1) this.getBrain().eraseMemory(memory);
    }

    private boolean isAbilityTargetReservedByPack(LivingEntity target, MemoryModuleType<LivingEntity> memory) {
        if (!(this.level() instanceof ServerLevel level) || target == null) return false;
        List<LunarWolf> mates = level.getEntitiesOfClass(
                LunarWolf.class,
                this.getBoundingBox().inflate(LunarWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isLunarPackmate(candidate));
        for (LunarWolf mate : mates) if (mate.getBrain().getMemory(memory).orElse(null) == target) return true;
        return false;
    }

    private void sendLunarRing(ServerLevel level, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.PORTAL, x, center.y, z, 1, 0.015D, 0.025D, 0.015D, 0.0D);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, x, center.y + 0.03D, z, 1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public boolean isLunarBeamActive() { return this.entityData.get(DATA_BEAM_ACTIVE); }
    public int getLunarBeamVisualTargetId() { return this.entityData.get(DATA_BEAM_TARGET_ID); }
    public boolean isMoonShieldActive() { return this.entityData.get(DATA_SHIELD_ACTIVE); }
    public boolean isDreamstepActive() { return this.entityData.get(DATA_DREAMSTEP_ACTIVE); }
    public boolean isMoonlitHowlActive() { return this.entityData.get(DATA_HOWL_ACTIVE); }

    public static boolean checkLunarWolfSpawnRules(
            EntityType<LunarWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        boolean night = level instanceof Level actual && actual.isDarkOutside();
        return night
                && level.canSeeSky(pos)
                && level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON);
    }
}
