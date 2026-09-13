package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.ronm19.wolfism.entity.ai.goal.SolarCelestialDashGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarFlareGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarAscensionGoal;
import net.ronm19.wolfism.entity.ai.goal.SolarSunbeamGoal;
import net.ronm19.wolfism.entity.ai.sensor.SolarWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Solar Wolf: Wolfism's first celestial specialist.
 *
 * <p>Solar Wolf is intentionally more than a bright Fire Wolf. Daybreak Aura
 * turns daylight into family sustain and anti-hostile pressure, Sunbeam is a
 * piercing line attack, Solar Flare is defensive area control, Celestial Dash
 * is a radiant interception move, and Solar Ascension is the rare battlefield
 * ultimate. All active damage is family-safe.</p>
 */
public final class SolarWolf extends AbstractWolfismWolf {
    public static final int SOLAR_FLARE_COOLDOWN_TICKS = 20 * 18;
    public static final int SUNBEAM_COOLDOWN_TICKS = 20 * 14;
    public static final int CELESTIAL_DASH_COOLDOWN_TICKS = 20 * 16;
    public static final int SOLAR_ASCENSION_COOLDOWN_TICKS = 20 * 90;

    public static final float SOLAR_FLARE_DAMAGE = 4.5F;
    public static final float SUNBEAM_DAMAGE = 7.0F;
    public static final float CELESTIAL_DASH_DAMAGE = 7.5F;
    public static final float ASCENSION_PULSE_DAMAGE = 3.0F;
    public static final float ASCENSION_FINAL_DAMAGE = 11.0F;

    public static final double SOLAR_FLARE_RADIUS = 6.0D;
    public static final double DAYBREAK_AURA_RADIUS = 8.0D;
    public static final double ASCENSION_RADIUS = 5.5D;

    private static final int FLARE_VISUAL_TICKS = 12;
    private static final int SUNBEAM_VISUAL_TICKS = 10;
    private static final int DASH_DURATION_TICKS = 13;
    private static final double DASH_SPEED = 1.05D;
    private static final double DASH_IMPACT_RADIUS = 1.55D;
    private static final int ASCENSION_DURATION_TICKS = 36;
    private static final int ASCENSION_PULSE_INTERVAL = 6;
    private static final int DAYBREAK_AURA_INTERVAL = 40;

    /** Client-visible state for the short-lived rendered Sunbeam. */
    private static final EntityDataAccessor<Boolean> DATA_SUNBEAM_ACTIVE =
            SynchedEntityData.defineId(SolarWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SUNBEAM_TARGET_ID =
            SynchedEntityData.defineId(SolarWolf.class, EntityDataSerializers.INT);

    private int solarFlareVisualTicks;
    private int sunbeamVisualTicks;
    private int celestialDashTicks;
    private Vec3 celestialDashDirection = Vec3.ZERO;
    private final Set<Integer> dashHitIds = new HashSet<>();
    private int solarAscensionTicks;
    private Vec3 solarAscensionCenter = Vec3.ZERO;
    private LivingEntity solarAscensionTarget;

    private static final class BrainHolder {
        private static final Brain.Provider<SolarWolf> PROVIDER = Brain.<SolarWolf>provider(
                ImmutableList.of(ModSensorTypes.SOLAR_PACK.get()),
                wolf -> List.of());
    }

    public SolarWolf(EntityType<? extends SolarWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SUNBEAM_ACTIVE, false);
        entityData.define(DATA_SUNBEAM_TARGET_ID, 0);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SOLAR_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new SolarPupRetreatGoal(this));
        this.goalSelector.addGoal(1, new SolarAscensionGoal(this));
        this.goalSelector.addGoal(2, new SolarFlareGoal(this));
        this.goalSelector.addGoal(3, new SolarSunbeamGoal(this));
        this.goalSelector.addGoal(4, new SolarCelestialDashGoal(this));
        this.goalSelector.addGoal(7, new SolarPupFollowAdultGoal(this));
        this.goalSelector.addGoal(8, new SolarPackCohesionGoal(this));
        this.goalSelector.addGoal(9, new SolarPupPlayGoal(this));
        this.targetSelector.addGoal(3, new SolarPackAssistGoal(this));
    }

    @Override
    protected Brain<SolarWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SolarWolf> getBrain() {
        return (Brain<SolarWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget instanceof SolarWolf solar && this.isSolarPackmate(solar)) {
            // Vanilla retaliation may briefly assign a packmate after incidental
            // contact. Never allow that stale target to become a Solar civil war.
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.SOLAR_FLARE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SOLAR_SUNBEAM_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SOLAR_DASH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.SOLAR_ASCENSION_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelCelestialDash();
            this.cancelSolarAscension();
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel level) {
            if (this.solarFlareVisualTicks > 0) --this.solarFlareVisualTicks;
            if (this.sunbeamVisualTicks > 0 && --this.sunbeamVisualTicks <= 0) {
                this.clearSunbeamVisual();
            }
            if (this.celestialDashTicks > 0) this.tickCelestialDash(level);
            if (this.solarAscensionTicks > 0) this.tickSolarAscension(level);

            if (!this.isBaby() && this.tickCount % DAYBREAK_AURA_INTERVAL == 0) {
                this.tickDaybreakAura(level);
            }

            if (this.isInDirectSunlight() && this.isWolfismWorkTick(80) && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }

            if (this.isInDirectSunlight() && this.tickCount % 18 == 0) {
                WolfVfx.sendParticles("solar_wolf", level,
                        ParticleTypes.END_ROD,
                        this.getX(), this.getY() + 0.55D, this.getZ(),
                        2, 0.24D, 0.28D, 0.24D, 0.008D);
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        // Wild Solar Wolves form a natural pack. They must never enter the
        // vanilla retaliation loop against one another, even before taming.
        if (target instanceof SolarWolf solar && this.isSolarPackmate(solar)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isInDirectSunlight() {
        return !this.level().isDarkOutside()
                && this.level().canSeeSky(this.blockPosition())
                && !this.level().isRainingAt(this.blockPosition());
    }

    public float getSolarPowerMultiplier() {
        return this.isInDirectSunlight() ? 1.20F : 0.90F;
    }

    public boolean isSolarPackmate(SolarWolf other) {
        if (other == this || other.isTame() != this.isTame()) return false;
        if (!this.isTame()) return true;
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidSolarCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof SolarWolf solar && this.isSolarPackmate(solar)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    /**
     * Solar abilities are deliberately stricter than ordinary Mob#canAttack.
     * A living entity is not collateral just because Minecraft technically lets
     * this wolf attack it. Only the explicit combat target / family-defense
     * threat / shared pack threat, or a genuinely hostile Enemy, may be struck.
     */
    private boolean isSolarAbilityThreat(LivingEntity candidate, LivingEntity primaryTarget) {
        if (!this.isValidSolarCombatTarget(candidate)) {
            return false;
        }
        if (candidate == primaryTarget || candidate == this.getTarget() || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }
        LivingEntity sharedThreat = this.getBrain().getMemory(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get()).orElse(null);
        if (candidate == sharedThreat) {
            return true;
        }
        return candidate instanceof Enemy;
    }

    public boolean isSolarFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (this.isTame()) {
            if (entity == this.getOwner()) return true;
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof SolarWolf solar && this.isSolarPackmate(solar);
    }

    // ---------------------------------------------------------------------
    // Daybreak Aura
    // ---------------------------------------------------------------------

    private void tickDaybreakAura(ServerLevel level) {
        if (!this.isInDirectSunlight()) return;

        AABB area = this.getBoundingBox().inflate(DAYBREAK_AURA_RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
        for (LivingEntity entity : nearby) {
            if (this.isSolarFamilyMember(entity)) {
                if (entity.getHealth() < entity.getMaxHealth()) entity.heal(1.0F);
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
                entity.removeEffect(MobEffects.DARKNESS);
                continue;
            }

            if (entity instanceof Enemy && this.isValidSolarCombatTarget(entity)) {
                entity.igniteForSeconds(2.5F);
                entity.hurtServer(level, this.damageSources().mobAttack(this), 1.0F);
            }
        }

        WolfVfx.sendParticles("solar_wolf", level,
                ParticleTypes.END_ROD,
                this.getX(), this.getY() + 0.45D, this.getZ(),
                8, 1.25D, 0.18D, 1.25D, 0.012D);
    }

    // ---------------------------------------------------------------------
    // Solar Flare
    // ---------------------------------------------------------------------

    public boolean canStartSolarFlare() {
        if (!this.canUseActiveSolarAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.SOLAR_FLARE_COOLDOWN.get())
                || this.celestialDashTicks > 0
                || this.solarAscensionTicks > 0) return false;

        int threats = this.findSolarThreatsAround(this, SOLAR_FLARE_RADIUS).size();
        if (this.hasFamilyDefenseEmergency()) return threats >= 1;
        return threats >= 2;
    }

    public boolean performSolarFlare() {
        if (!this.canStartSolarFlare() || !(this.level() instanceof ServerLevel level)) return false;

        List<LivingEntity> threats = this.findSolarThreatsAround(this, SOLAR_FLARE_RADIUS);
        if (threats.isEmpty()) return false;

        float damage = SOLAR_FLARE_DAMAGE * this.getSolarPowerMultiplier();
        for (LivingEntity threat : threats) {
            if (threat.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
                threat.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                threat.igniteForSeconds(2.0F);
                Vec3 push = threat.position().subtract(this.position());
                if (push.lengthSqr() > 1.0E-5D) {
                    push = push.normalize();
                    threat.push(push.x * 0.55D, 0.18D, push.z * 0.55D);
                }
            }
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_FLARE_COOLDOWN.get(), SOLAR_FLARE_COOLDOWN_TICKS);
        this.solarFlareVisualTicks = FLARE_VISUAL_TICKS;
        this.sendSolarRing(level, this.position().add(0.0D, 0.35D, 0.0D), SOLAR_FLARE_RADIUS, 44);
        return true;
    }

    // ---------------------------------------------------------------------
    // Sunbeam
    // ---------------------------------------------------------------------

    public boolean canStartSunbeamAgainst(LivingEntity target) {
        if (!this.canUseActiveSolarAbility()
                || target == null
                || target instanceof Creeper
                || !this.isValidSolarCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.SOLAR_SUNBEAM_COOLDOWN.get())
                || this.isAbilityTargetReservedByPack(target, ModMemoryModuleTypes.SOLAR_SUNBEAM_TARGET.get())
                || this.celestialDashTicks > 0
                || this.solarAscensionTicks > 0) return false;

        double distance = this.distanceToSqr(target);
        return distance >= SolarSunbeamGoal.MIN_START_DISTANCE_SQR
                && distance <= SolarSunbeamGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target);
    }

    public void reserveSunbeamTarget(LivingEntity target) {
        if (target != null && target.isAlive()) this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_SUNBEAM_TARGET.get(), target);
    }

    public void clearSunbeamReservation() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.SOLAR_SUNBEAM_TARGET.get());
    }

    public boolean performSunbeam(LivingEntity target) {
        if (!this.canStartSunbeamAgainst(target) || !(this.level() instanceof ServerLevel level)) return false;

        Vec3 start = this.position().add(0.0D, 0.62D, 0.0D);
        Vec3 raw = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D).subtract(start);
        double length = Math.min(18.0D, raw.length());
        if (length < 0.01D) return false;
        Vec3 dir = raw.normalize();
        Vec3 end = start.add(dir.scale(length));

        AABB beamBox = new AABB(start, end).inflate(1.15D);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(
                LivingEntity.class,
                beamBox,
                entity -> entity != this
                        && !(entity instanceof Creeper)
                        && this.isSolarAbilityThreat(entity, target)));

        candidates.sort(Comparator.comparingDouble(entity -> projectionAlongBeam(start, dir, entity.position())));
        int hits = 0;
        float damage = SUNBEAM_DAMAGE * this.getSolarPowerMultiplier();
        for (LivingEntity candidate : candidates) {
            double projection = projectionAlongBeam(start, dir, candidate.position());
            if (projection < 0.0D || projection > length) continue;
            Vec3 closest = start.add(dir.scale(projection));
            Vec3 center = candidate.position().add(0.0D, candidate.getBbHeight() * 0.5D, 0.0D);
            if (center.distanceToSqr(closest) > 1.35D * 1.35D) continue;

            if (candidate.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
                candidate.igniteForSeconds(4.0F);
            }
            Vec3 impact = candidate.position().add(0.0D, candidate.getBbHeight() * 0.5D, 0.0D);
            WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 9, 0.20D, 0.24D, 0.20D, 0.035D);
            WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.FLAME, impact.x, impact.y, impact.z, 5, 0.15D, 0.18D, 0.15D, 0.020D);
            if (++hits >= 4) break;
        }

        // The actual line is rendered client-side with vanilla BeaconRenderer.
        // Particles now act as edge sparkle / impact polish rather than faking the beam.
        for (double d = 0.45D; d <= length; d += 1.35D) {
            Vec3 p = start.add(dir.scale(d));
            WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.045D, 0.045D, 0.045D, 0.0D);
            if (((int) Math.floor(d * 2.0D)) % 5 == 0) {
                WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.025D, 0.025D, 0.025D, 0.0D);
            }
        }

        // A brighter muzzle flash makes the transition from charge particles to beam obvious.
        WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.END_ROD, start.x, start.y, start.z, 8, 0.12D, 0.10D, 0.12D, 0.025D);
        WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.FLAME, start.x, start.y, start.z, 5, 0.09D, 0.08D, 0.09D, 0.015D);

        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_SUNBEAM_COOLDOWN.get(), SUNBEAM_COOLDOWN_TICKS);
        this.clearSunbeamReservation();
        this.sunbeamVisualTicks = SUNBEAM_VISUAL_TICKS;
        this.entityData.set(DATA_SUNBEAM_TARGET_ID, target.getId());
        this.entityData.set(DATA_SUNBEAM_ACTIVE, true);
        return hits > 0;
    }

    private void clearSunbeamVisual() {
        this.sunbeamVisualTicks = 0;
        this.entityData.set(DATA_SUNBEAM_ACTIVE, false);
        this.entityData.set(DATA_SUNBEAM_TARGET_ID, 0);
    }

    public int getSunbeamVisualTargetId() {
        return this.entityData.get(DATA_SUNBEAM_TARGET_ID);
    }

    private static double projectionAlongBeam(Vec3 start, Vec3 dir, Vec3 point) {
        return point.subtract(start).dot(dir);
    }

    // ---------------------------------------------------------------------
    // Celestial Dash
    // ---------------------------------------------------------------------

    public boolean canStartCelestialDashAgainst(LivingEntity target) {
        if (!this.canUseActiveSolarAbility()
                || target == null
                || target instanceof Creeper
                || !this.isValidSolarCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.SOLAR_DASH_COOLDOWN.get())
                || this.isAbilityTargetReservedByPack(target, ModMemoryModuleTypes.SOLAR_DASH_TARGET.get())
                || this.celestialDashTicks > 0
                || this.solarAscensionTicks > 0) return false;

        double distance = this.distanceToSqr(target);
        return distance >= SolarCelestialDashGoal.MIN_START_DISTANCE_SQR
                && distance <= SolarCelestialDashGoal.MAX_START_DISTANCE_SQR;
    }

    public boolean beginCelestialDash(LivingEntity target) {
        if (!this.canStartCelestialDashAgainst(target)) return false;
        Vec3 flat = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-5D) return false;

        this.celestialDashDirection = flat.normalize();
        this.celestialDashTicks = DASH_DURATION_TICKS;
        this.dashHitIds.clear();
        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_DASH_TARGET.get(), target);
        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_DASH_COOLDOWN.get(), CELESTIAL_DASH_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setSprinting(true);
        return true;
    }

    private void tickCelestialDash(ServerLevel level) {
        LivingEntity target = this.getBrain().getMemory(ModMemoryModuleTypes.SOLAR_DASH_TARGET.get()).orElse(null);
        if (target == null || !target.isAlive() || !this.isValidSolarCombatTarget(target)) {
            this.cancelCelestialDash();
            return;
        }

        Vec3 desired = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (desired.lengthSqr() > 1.0E-5D) {
            Vec3 steered = this.celestialDashDirection.scale(0.72D).add(desired.normalize().scale(0.28D));
            if (steered.lengthSqr() > 1.0E-5D) this.celestialDashDirection = steered.normalize();
        }

        this.faceDirection(this.celestialDashDirection);
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                this.celestialDashDirection.x * DASH_SPEED,
                current.y,
                this.celestialDashDirection.z * DASH_SPEED);
        this.hurtMarked = true;

        WolfVfx.sendParticles("solar_wolf", level,
                ParticleTypes.FLAME,
                this.getX() - this.celestialDashDirection.x * 0.55D,
                this.getY() + 0.28D,
                this.getZ() - this.celestialDashDirection.z * 0.55D,
                4, 0.16D, 0.12D, 0.16D, 0.01D);
        WolfVfx.sendParticles("solar_wolf", level,
                ParticleTypes.END_ROD,
                this.getX(), this.getY() + 0.45D, this.getZ(),
                2, 0.18D, 0.14D, 0.18D, 0.01D);

        List<LivingEntity> impacts = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(DASH_IMPACT_RADIUS),
                candidate -> candidate != this
                        && !this.dashHitIds.contains(candidate.getId())
                        && !(candidate instanceof Creeper)
                        && this.isSolarAbilityThreat(candidate, target));

        for (LivingEntity impact : impacts) {
            this.dashHitIds.add(impact.getId());
            if (impact.hurtServer(level, this.damageSources().mobAttack(this), CELESTIAL_DASH_DAMAGE * this.getSolarPowerMultiplier())) {
                impact.igniteForSeconds(4.0F);
                Vec3 push = impact.position().subtract(this.position());
                if (push.lengthSqr() > 1.0E-5D) {
                    push = push.normalize();
                    impact.push(push.x * 1.2D, 0.28D, push.z * 1.2D);
                }
            }
        }

        if (this.horizontalCollision || --this.celestialDashTicks <= 0) this.cancelCelestialDash();
    }

    public void cancelCelestialDash() {
        this.celestialDashTicks = 0;
        this.celestialDashDirection = Vec3.ZERO;
        this.dashHitIds.clear();
        this.getBrain().eraseMemory(ModMemoryModuleTypes.SOLAR_DASH_TARGET.get());
        this.setSprinting(false);
    }

    // ---------------------------------------------------------------------
    // Solar Ascension
    // ---------------------------------------------------------------------

    public boolean canStartSolarAscensionAgainst(LivingEntity target) {
        if (!this.canUseActiveSolarAbility()
                || target == null
                || !this.isValidSolarCombatTarget(target)
                || this.isCoolingDown(ModMemoryModuleTypes.SOLAR_ASCENSION_COOLDOWN.get())
                || this.solarAscensionTicks > 0
                || this.celestialDashTicks > 0) return false;

        int nearbyThreats = this.findSolarThreatsAround(target, 6.5D).size();
        return nearbyThreats >= 3 || target.getMaxHealth() >= 60.0F;
    }

    public boolean beginSolarAscension(LivingEntity target) {
        if (!this.canStartSolarAscensionAgainst(target)) return false;
        this.solarAscensionTicks = ASCENSION_DURATION_TICKS;
        this.solarAscensionCenter = target.position();
        this.solarAscensionTarget = target;
        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_ASCENSION_TARGET.get(), target);
        this.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_ASCENSION_COOLDOWN.get(), SOLAR_ASCENSION_COOLDOWN_TICKS);
        this.getNavigation().stop();
        return true;
    }

    private void tickSolarAscension(ServerLevel level) {
        if (this.solarAscensionTicks <= 0) return;

        if (this.solarAscensionTarget != null && this.solarAscensionTarget.isAlive()) {
            Vec3 targetPos = this.solarAscensionTarget.position();
            this.solarAscensionCenter = this.solarAscensionCenter.lerp(targetPos, 0.18D);
            this.getLookControl().setLookAt(this.solarAscensionTarget, 45.0F, 45.0F);
        }

        int elapsed = ASCENSION_DURATION_TICKS - this.solarAscensionTicks;
        this.sendAscensionColumn(level, this.solarAscensionCenter, elapsed);

        if (elapsed % ASCENSION_PULSE_INTERVAL == 0 && elapsed > 0) {
            boolean finalPulse = this.solarAscensionTicks <= ASCENSION_PULSE_INTERVAL;
            float damage = (finalPulse ? ASCENSION_FINAL_DAMAGE : ASCENSION_PULSE_DAMAGE) * this.getSolarPowerMultiplier();
            AABB area = new AABB(
                    this.solarAscensionCenter.x - ASCENSION_RADIUS,
                    this.solarAscensionCenter.y - 2.0D,
                    this.solarAscensionCenter.z - ASCENSION_RADIUS,
                    this.solarAscensionCenter.x + ASCENSION_RADIUS,
                    this.solarAscensionCenter.y + 7.0D,
                    this.solarAscensionCenter.z + ASCENSION_RADIUS);

            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
            for (LivingEntity entity : entities) {
                if (this.isSolarFamilyMember(entity)) {
                    entity.removeEffect(MobEffects.DARKNESS);
                    if (finalPulse) entity.heal(3.0F);
                    continue;
                }
                if (!this.isSolarAbilityThreat(entity, this.solarAscensionTarget)) continue;
                entity.hurtServer(level, this.damageSources().mobAttack(this), damage);
                entity.igniteForSeconds(finalPulse ? 6.0F : 3.0F);
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, finalPulse ? 80 : 30, 0));
            }
        }

        if (--this.solarAscensionTicks <= 0) this.cancelSolarAscension();
    }

    private void sendAscensionColumn(ServerLevel level, Vec3 center, int elapsed) {
        for (int i = 0; i < 9; ++i) {
            double y = center.y + 0.2D + i * 0.75D;
            double phase = elapsed * 0.32D + i * 0.7D;
            double radius = 0.55D + (i % 3) * 0.08D;
            double x = center.x + Math.cos(phase) * radius;
            double z = center.z + Math.sin(phase) * radius;
            WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.END_ROD, x, y, z, 2, 0.10D, 0.14D, 0.10D, 0.004D);
        }
        if (elapsed % 4 == 0) {
            this.sendSolarRing(level, center.add(0.0D, 0.15D, 0.0D), ASCENSION_RADIUS * 0.72D, 28);
        }
    }

    public void cancelSolarAscension() {
        this.solarAscensionTicks = 0;
        this.solarAscensionCenter = Vec3.ZERO;
        this.solarAscensionTarget = null;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.SOLAR_ASCENSION_TARGET.get());
    }

    // ---------------------------------------------------------------------
    // Shared helpers / pack intelligence
    // ---------------------------------------------------------------------

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel level) || threat == null || !threat.isAlive()) return;

        List<SolarWolf> pack = level.getEntitiesOfClass(
                SolarWolf.class,
                this.getBoundingBox().inflate(SolarWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && (candidate == this || this.isSolarPackmate(candidate)));

        for (SolarWolf packmate : pack) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.SOLAR_PACK_THREAT.get(), threat);
            if (!packmate.isBaby() && packmate.isValidSolarCombatTarget(threat)) {
                LivingEntity current = packmate.getTarget();
                if (current == null || !current.isAlive()) packmate.setTarget(threat);
            } else if (packmate.isBaby()) {
                packmate.setTarget(null);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.clearSunbeamReservation();
        this.cancelCelestialDash();
        if (this.solarAscensionTicks > 0 && attacker != this.solarAscensionTarget) this.cancelSolarAscension();
    }

    private boolean canUseActiveSolarAbility() {
        return !this.isBaby() && !this.isOrderedToSit() && !this.isInSittingPose();
    }

    private List<LivingEntity> findSolarThreatsAround(LivingEntity anchor, double radius) {
        LivingEntity primary = anchor == this ? this.getTarget() : anchor;
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                anchor.getBoundingBox().inflate(radius),
                candidate -> candidate != this && this.isSolarAbilityThreat(candidate, primary));
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
        List<SolarWolf> packmates = level.getEntitiesOfClass(
                SolarWolf.class,
                this.getBoundingBox().inflate(SolarWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isSolarPackmate(candidate));
        for (SolarWolf packmate : packmates) {
            if (packmate.getBrain().getMemory(memory).orElse(null) == target) return true;
        }
        return false;
    }

    private void faceDirection(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-5D) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    private void sendSolarRing(ServerLevel level, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.END_ROD, x, center.y, z, 1, 0.02D, 0.04D, 0.02D, 0.0D);
            if (i % 3 == 0) WolfVfx.sendParticles("solar_wolf", level, ParticleTypes.FLAME, x, center.y + 0.04D, z, 1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public boolean isSolarFlareActive() { return this.solarFlareVisualTicks > 0; }
    public boolean isSunbeamActive() { return this.entityData.get(DATA_SUNBEAM_ACTIVE); }
    public boolean isCelestialDashing() { return this.celestialDashTicks > 0; }
    public boolean isSolarAscensionActive() { return this.solarAscensionTicks > 0; }

    public static boolean checkSolarWolfSpawnRules(
            EntityType<SolarWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && level.canSeeSky(pos)
                && isBrightEnoughToSpawn(level, pos);
    }
}
