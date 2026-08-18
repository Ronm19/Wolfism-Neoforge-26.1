package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.control.WaterWolfMoveControl;
import net.ronm19.wolfism.entity.ai.goal.WaterAquaticThreatGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterCurrentDashGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPressureWaveGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterRescueGoal;
import net.ronm19.wolfism.entity.ai.goal.WaterWolfWaterAffinityGoal;
import net.ronm19.wolfism.entity.ai.sensor.WaterWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Water Wolf: amphibious rescue, exploration and underwater support specialist.
 *
 * <p>Unlike the land-oriented elemental wolves, Water Wolf changes its behavior
 * when the pack enters water. It can keep nearby family members breathing,
 * actively rescue low-air allies, burst through water with Current Dash, and
 * deploy a temporary Bubble Shelter around itself in emergencies.</p>
 */
public final class WaterWolf extends AbstractWolfismWolf {
    public static final double WATER_BREATHING_AURA_RADIUS = 7.0D;
    public static final double RESCUE_SCAN_RADIUS = 24.0D;
    public static final double BUBBLE_SHELTER_RADIUS = 6.0D;
    public static final double PRESSURE_WAVE_RADIUS = 5.5D;


    public static final int CURRENT_DASH_COOLDOWN_TICKS = 20 * 12;
    public static final int BUBBLE_SHELTER_COOLDOWN_TICKS = 20 * 30;
    public static final int PRESSURE_WAVE_COOLDOWN_TICKS = 20 * 20;
    public static final int BUBBLE_SHELTER_DURATION_TICKS = 20 * 8;

    public static final float CURRENT_DASH_DAMAGE = 7.0F;
    public static final float PRESSURE_WAVE_DAMAGE = 4.0F;
    public static final double CURRENT_DASH_SPEED = 1.35D;

    private static final int AURA_REFRESH_INTERVAL = 10;
    private static final int AURA_EFFECT_TICKS = 50;
    private static final int BUBBLE_EFFECT_TICKS = 80;

    private int bubbleShelterTicks;
    private int pressureWavePulseTicks;
    private boolean currentDashImpactActive;

    private static final class BrainHolder {
        private static final Brain.Provider<WaterWolf> PROVIDER = Brain.<WaterWolf>provider(
                ImmutableList.of(ModSensorTypes.WATER_PACK.get(), ModSensorTypes.WATER_AQUATIC.get()),
                wolf -> List.of());
    }

    public WaterWolf(EntityType<? extends WaterWolf> type, Level level) {
        super(type, level);

        // A normal wolf treats water as undesirable terrain. Water Wolf does the
        // opposite: water is valid habitat, and its MoveControl becomes fully
        // three-dimensional only after the entity actually enters water.
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new WaterWolfMoveControl(this);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.WATER_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Family danger remains the highest priority through AbstractWolfismWolf.
        this.goalSelector.addGoal(1, new WaterPupRetreatGoal(this));
        this.goalSelector.addGoal(2, new WaterPressureWaveGoal(this));
        this.goalSelector.addGoal(3, new WaterRescueGoal(this));
        this.goalSelector.addGoal(4, new WaterCurrentDashGoal(this));
        this.goalSelector.addGoal(5, new WaterPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new WaterPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new WaterPupPlayGoal(this));
        this.goalSelector.addGoal(7, new WaterWolfWaterAffinityGoal(this));

        this.targetSelector.addGoal(3, new WaterPackAssistGoal(this));
        this.targetSelector.addGoal(4, new WaterAquaticThreatGoal(this));
    }

    @Override
    protected Brain<WaterWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<WaterWolf> getBrain() {
        return (Brain<WaterWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickCurrentDashCooldown();
        this.tickBubbleShelterCooldown();
        this.tickPressureWaveCooldown();
        if (this.pressureWavePulseTicks > 0) {
            --this.pressureWavePulseTicks;
        }
        this.tickBubbleShelter(level);
        this.tickWaterBreathingAura();

        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            this.clearCurrentDashReservation();
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        // Water Wolf never drowns. Keeping the vanilla air meter full also makes
        // transitions between swimming and land completely predictable.
        if (this.isInWater()) {
            this.setAirSupply(this.getMaxAirSupply());
        }

        if (this.level() instanceof ServerLevel level && this.isInWater()) {
            if (this.tickCount % 8 == 0) {
                level.sendParticles(
                        ParticleTypes.BUBBLE,
                        this.getX(),
                        this.getY() + 0.35D,
                        this.getZ(),
                        this.isBaby() ? 1 : 2,
                        0.22D,
                        0.18D,
                        0.22D,
                        0.01D);
            }
        }
    }

    /**
     * Guardian-style water travel. Land movement still comes entirely from the
     * vanilla wolf path, but in water the custom MoveControl can steer the wolf
     * through the full 3D volume instead of merely paddling toward dry ground.
     */
    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        if (this.isOrderedToSit()) {
            super.travelInWater(input, baseGravity, isFalling, oldY);
            return;
        }

        this.moveRelative(0.10F, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.90D));

        if (this.horizontalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.10D, 0.0D));
        }
    }

    /** Water is desirable habitat, not a pathfinding penalty. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return 10.0F + level.getPathfindingCostFromLightLevels(pos);
        }
        return super.getWalkTargetValue(pos, level);
    }

    /**
     * Sends movement through WaterWolfMoveControl while submerged and through
     * ordinary wolf navigation on land. Goals use this instead of hard-coding
     * one navigation system for both environments.
     */
    public void moveToWaterAware(double x, double y, double z, double speed) {
        if (this.isInWater()) {
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(x, y, z, speed);
            return;
        }
        this.getNavigation().moveTo(x, y, z, speed);
    }

    public void moveToWaterAware(LivingEntity target, double speed) {
        if (target == null) {
            return;
        }

        double y = target.getY();
        if (this.isInWater() && target.isInWater()) {
            y += target.getBbHeight() * 0.35D;
        }
        this.moveToWaterAware(target.getX(), y, target.getZ(), speed);
    }

    public void stopWaterAwareMovement() {
        this.getNavigation().stop();
        MoveControl control = this.getMoveControl();
        if (control instanceof WaterWolfMoveControl waterControl) {
            waterControl.stopAquaticMove();
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isCurrentDashImpactActive() {
        return this.currentDashImpactActive;
    }

    public boolean isBubbleShelterActive() {
        return this.bubbleShelterTicks > 0;
    }

    public boolean isCurrentDashCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_CURRENT_DASH_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean isBubbleShelterCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_BUBBLE_SHELTER_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean isPressureWaveCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_PRESSURE_WAVE_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean isPressureWavePulseActive() {
        return this.pressureWavePulseTicks > 0;
    }

    /**
     * Pressure Wave is defensive crowd control rather than a routine attack.
     * Two nearby threats are required during ordinary combat. A threatened
     * drowning family member is an emergency and may trigger it against one.
     */
    public boolean canStartPressureWave() {
        if (this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || !this.isInWater()
                || this.isPressureWaveCoolingDown()
                || this.isPressureWavePulseActive()
                || this.hasNearbyPressureWaveFromPack()) {
            return false;
        }

        List<LivingEntity> threats = this.findPressureWaveThreats();
        if (threats.isEmpty()) {
            return false;
        }

        LivingEntity rescueTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_RESCUE_TARGET.get())
                .orElse(null);
        if (rescueTarget != null
                && this.needsWaterRescue(rescueTarget)
                && this.distanceToSqr(rescueTarget) <= PRESSURE_WAVE_RADIUS * PRESSURE_WAVE_RADIUS) {
            double rescueDangerRadiusSqr = 3.5D * 3.5D;
            for (LivingEntity threat : threats) {
                if (rescueTarget.distanceToSqr(threat) <= rescueDangerRadiusSqr) {
                    return true;
                }
            }
        }

        LivingEntity protectedFamily = this.getProtectedFamilyMember();
        if (protectedFamily != null
                && protectedFamily.isAlive()
                && protectedFamily.isInWater()
                && this.distanceToSqr(protectedFamily) <= PRESSURE_WAVE_RADIUS * PRESSURE_WAVE_RADIUS) {
            double protectionRadiusSqr = 3.5D * 3.5D;
            for (LivingEntity threat : threats) {
                if (protectedFamily.distanceToSqr(threat) <= protectionRadiusSqr) {
                    return true;
                }
            }
        }

        return threats.size() >= 2;
    }

    /**
     * Releases a family-safe radial pulse. Damage is modest; the point of the
     * ability is to create underwater space by physically pushing threats away.
     */
    public boolean performPressureWave() {
        if (!this.canStartPressureWave() || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        List<LivingEntity> threats = this.findPressureWaveThreats();
        if (threats.isEmpty()) {
            return false;
        }

        LivingEntity rescueTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.WATER_RESCUE_TARGET.get())
                .orElse(null);
        boolean rescueEmergency = rescueTarget != null
                && this.needsWaterRescue(rescueTarget)
                && this.distanceToSqr(rescueTarget) <= PRESSURE_WAVE_RADIUS * PRESSURE_WAVE_RADIUS;

        this.pressureWavePulseTicks = 10;
        this.getBrain().setMemory(
                ModMemoryModuleTypes.WATER_PRESSURE_WAVE_COOLDOWN.get(),
                PRESSURE_WAVE_COOLDOWN_TICKS);

        Vec3 normalOrigin = this.position().add(0.0D, this.getBbHeight() * 0.45D, 0.0D);
        Vec3 rescueOrigin = rescueEmergency
                ? rescueTarget.position().add(0.0D, rescueTarget.getBbHeight() * 0.45D, 0.0D)
                : normalOrigin;

        int affected = 0;
        for (LivingEntity threat : threats) {
            if (!threat.isAlive() || !this.isPressureWaveThreat(threat)) {
                continue;
            }

            boolean hurt = threat.hurtServer(level, this.damageSources().mobAttack(this), PRESSURE_WAVE_DAMAGE);
            if (hurt && threat.isAlive()) {
                threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
            }

            // During a rescue, push away from the drowning ally rather than from
            // Water Wolf. This opens a clean lane for the rescue approach.
            Vec3 origin = rescueEmergency && rescueTarget.distanceToSqr(threat) <= 3.5D * 3.5D
                    ? rescueOrigin
                    : normalOrigin;
            Vec3 away = threat.position()
                    .add(0.0D, threat.getBbHeight() * 0.45D, 0.0D)
                    .subtract(origin);
            if (away.lengthSqr() < 1.0E-5D) {
                away = new Vec3(0.0D, 0.35D, 0.0D);
            } else {
                away = away.normalize();
            }

            // push() gives us real three-dimensional underwater displacement,
            // unlike ordinary horizontal knockback.
            threat.push(away.x * 1.15D, away.y * 0.55D + 0.12D, away.z * 1.15D);
            ++affected;
        }

        this.sendPressureWaveParticles(level);
        return affected > 0;
    }

    private List<LivingEntity> findPressureWaveThreats() {
        if (!(this.level() instanceof ServerLevel level)) {
            return List.of();
        }

        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(PRESSURE_WAVE_RADIUS),
                this::isPressureWaveThreat);
    }

    private boolean isPressureWaveThreat(LivingEntity candidate) {
        if (candidate == null
                || candidate == this
                || !candidate.isAlive()
                || !candidate.isInWater()
                || candidate instanceof Creeper
                || !this.isValidWaterCombatTarget(candidate)) {
            return false;
        }

        // Ordinary hostile aquatic mobs qualify automatically. A non-Enemy can
        // still qualify when it is the wolf's current/family-defense attacker.
        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || candidate == this.getFamilyDefenseTarget();
    }

    private boolean hasNearbyPressureWaveFromPack() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        return !level.getEntitiesOfClass(
                WaterWolf.class,
                this.getBoundingBox().inflate(PRESSURE_WAVE_RADIUS * 1.5D),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isWaterPackmate(candidate)
                        && candidate.isPressureWavePulseActive())
                .isEmpty();
    }

    private void sendPressureWaveParticles(ServerLevel level) {
        double centerY = this.getY() + this.getBbHeight() * 0.45D;
        int points = 20;
        double[] radii = {1.7D, 3.4D, PRESSURE_WAVE_RADIUS};

        for (int ring = 0; ring < radii.length; ++ring) {
            double radius = radii[ring];
            for (int i = 0; i < points; ++i) {
                double angle = (Math.PI * 2.0D * i) / points;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                level.sendParticles(
                        ring == radii.length - 1 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                        x,
                        centerY,
                        z,
                        1,
                        0.02D,
                        0.05D,
                        0.02D,
                        0.01D);
            }
        }

        level.sendParticles(
                ParticleTypes.BUBBLE,
                this.getX(),
                centerY,
                this.getZ(),
                24,
                0.65D,
                0.45D,
                0.65D,
                0.09D);
    }

    public boolean canStartCurrentDashAgainst(LivingEntity target) {
        if (target == null
                || target instanceof Creeper
                || !target.isAlive()
                || this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || !this.isInWater()
                || !target.isInWater()
                || this.isCurrentDashCoolingDown()
                || !this.isValidWaterCombatTarget(target)
                || this.isCurrentDashReservedByPack(target)) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= WaterCurrentDashGoal.MIN_START_DISTANCE_SQR
                && distance <= WaterCurrentDashGoal.MAX_START_DISTANCE_SQR;
    }

    public void reserveCurrentDashTarget(LivingEntity target) {
        if (target != null && target.isAlive()) {
            this.getBrain().setMemory(ModMemoryModuleTypes.WATER_CURRENT_DASH_TARGET.get(), target);
        }
    }

    public void clearCurrentDashReservation() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.WATER_CURRENT_DASH_TARGET.get());
    }

    /**
     * Drives the physical part of Current Dash. Damage is deliberately resolved
     * only once the wolf actually reaches the target; the ability is not a
     * ranged hit disguised as movement.
     */
    public boolean propelCurrentDashToward(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.isInWater()
                || !target.isInWater()
                || !this.isValidWaterCombatTarget(target)) {
            return false;
        }

        Vec3 direction = target.position()
                .add(0.0D, target.getBbHeight() * 0.35D, 0.0D)
                .subtract(this.position().add(0.0D, this.getBbHeight() * 0.35D, 0.0D));
        if (direction.lengthSqr() <= 1.0E-5D) {
            return true;
        }

        this.setDeltaMovement(direction.normalize().scale(CURRENT_DASH_SPEED));

        if (this.level() instanceof ServerLevel level && this.tickCount % 2 == 0) {
            level.sendParticles(
                    ParticleTypes.BUBBLE,
                    this.getX(),
                    this.getY() + this.getBbHeight() * 0.45D,
                    this.getZ(),
                    4,
                    0.22D,
                    0.18D,
                    0.22D,
                    0.045D);
        }
        return true;
    }

    /** Resolves Current Dash once the physical dash has closed the distance. */
    public boolean performCurrentDashImpact(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.isInWater()
                || !target.isInWater()
                || !this.isValidWaterCombatTarget(target)
                || this.distanceToSqr(target) > WaterCurrentDashGoal.IMPACT_DISTANCE_SQR
                || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        this.currentDashImpactActive = true;
        boolean hurt;
        try {
            hurt = target.hurtServer(level, this.damageSources().mobAttack(this), CURRENT_DASH_DAMAGE);
            if (hurt && target.isAlive()) {
                double dx = this.getX() - target.getX();
                double dz = this.getZ() - target.getZ();
                target.knockback(0.75D, dx, dz);
            }
        } finally {
            this.currentDashImpactActive = false;
        }

        level.sendParticles(
                ParticleTypes.SPLASH,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.45D,
                target.getZ(),
                18,
                0.35D,
                0.28D,
                0.35D,
                0.12D);
        level.sendParticles(
                ParticleTypes.BUBBLE,
                this.getX(),
                this.getY() + 0.30D,
                this.getZ(),
                16,
                0.28D,
                0.24D,
                0.28D,
                0.06D);
        return hurt;
    }

    public void finishCurrentDash(boolean started) {
        if (started) {
            this.getBrain().setMemory(
                    ModMemoryModuleTypes.WATER_CURRENT_DASH_COOLDOWN.get(),
                    CURRENT_DASH_COOLDOWN_TICKS);
        }
        this.clearCurrentDashReservation();
    }

    /** 3D rescue steering that does not depend on ground path nodes. */
    public boolean swimTowardRescueTarget(LivingEntity ally) {
        if (ally == null || !ally.isAlive() || !ally.isInWater()) {
            return false;
        }

        Vec3 from = this.position().add(0.0D, this.getBbHeight() * 0.42D, 0.0D);
        Vec3 to = ally.position().add(0.0D, ally.getBbHeight() * 0.42D, 0.0D);
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() <= 1.0E-5D) {
            return true;
        }

        Vec3 targetPoint = to;
        this.getNavigation().stop();
        this.getMoveControl().setWantedPosition(targetPoint.x, targetPoint.y, targetPoint.z, 1.35D);
        return true;
    }

    public void performRescueSupport(LivingEntity ally) {
        if (ally == null || !ally.isAlive() || !this.isWaterRescueAlly(ally)) {
            return;
        }

        ally.setAirSupply(ally.getMaxAirSupply());
        ally.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 12, 0));

        if (!this.isBubbleShelterCoolingDown()) {
            this.beginBubbleShelter();
        }
    }

    public boolean beginBubbleShelter() {
        if (this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || !this.isInWater()
                || this.isBubbleShelterActive()
                || this.isBubbleShelterCoolingDown()) {
            return false;
        }

        this.bubbleShelterTicks = BUBBLE_SHELTER_DURATION_TICKS;
        this.getBrain().setMemory(
                ModMemoryModuleTypes.WATER_BUBBLE_SHELTER_COOLDOWN.get(),
                BUBBLE_SHELTER_COOLDOWN_TICKS);

        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.BUBBLE,
                    this.getX(),
                    this.getY() + 0.35D,
                    this.getZ(),
                    34,
                    1.0D,
                    0.75D,
                    1.0D,
                    0.08D);
        }
        return true;
    }

    private void tickBubbleShelter(ServerLevel level) {
        if (this.bubbleShelterTicks <= 0) {
            return;
        }
        if (!this.isInWater()) {
            this.bubbleShelterTicks = 0;
            return;
        }

        --this.bubbleShelterTicks;

        if (this.tickCount % 5 == 0) {
            level.sendParticles(
                    ParticleTypes.BUBBLE,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    6,
                    BUBBLE_SHELTER_RADIUS * 0.42D,
                    0.70D,
                    BUBBLE_SHELTER_RADIUS * 0.42D,
                    0.02D);
        }

        if (this.tickCount % AURA_REFRESH_INTERVAL == 0) {
            for (LivingEntity ally : this.findWaterSupportAllies(BUBBLE_SHELTER_RADIUS)) {
                if (ally.isInWater()) {
                    ally.setAirSupply(ally.getMaxAirSupply());
                    ally.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, BUBBLE_EFFECT_TICKS, 0));
                }
            }
        }
    }

    private void tickWaterBreathingAura() {
        if (this.tickCount % AURA_REFRESH_INTERVAL != 0) {
            return;
        }

        for (LivingEntity ally : this.findWaterSupportAllies(WATER_BREATHING_AURA_RADIUS)) {
            if (!ally.isInWater()) {
                continue;
            }

            ally.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, AURA_EFFECT_TICKS, 0));
            if (ally.getAirSupply() < ally.getMaxAirSupply() / 2) {
                ally.setAirSupply(Math.min(ally.getMaxAirSupply(), ally.getAirSupply() + 80));
            }
        }
    }

    private List<LivingEntity> findWaterSupportAllies(double radius) {
        if (!(this.level() instanceof ServerLevel level)) {
            return List.of();
        }

        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isWaterRescueAlly(candidate));
    }

    public boolean isWaterRescueAlly(LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && candidate == owner) {
            return true;
        }

        // Wolfism family rule: every tamed wolf is family, regardless of species
        // or owner. Wild Water Wolves additionally rescue their own packmates.
        if (this.isTame() && candidate instanceof Wolf wolf && wolf.isTame()) {
            return true;
        }
        return candidate instanceof WaterWolf waterWolf && this.isWaterPackmate(waterWolf);
    }

    public boolean needsWaterRescue(LivingEntity ally) {
        if (ally == null || !ally.isAlive() || !ally.isInWater() || !this.isWaterRescueAlly(ally)) {
            return false;
        }

        int maxAir = Math.max(1, ally.getMaxAirSupply());
        return ally.getAirSupply() <= Math.max(40, maxAir / 3);
    }

    public boolean isWaterPackmate(WaterWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidWaterCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof WaterWolf waterWolf && this.isWaterPackmate(waterWolf)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public static boolean isAquaticThreat(LivingEntity target) {
        return target != null && target.isAlive() && target.isInWater() && target instanceof Enemy;
    }

    public LivingEntity findNearestAquaticThreat(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        candidate -> candidate != this
                                && isAquaticThreat(candidate)
                                && !(candidate instanceof Creeper)
                                && this.isValidWaterCombatTarget(candidate))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private boolean isCurrentDashReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        List<WaterWolf> packmates = level.getEntitiesOfClass(
                WaterWolf.class,
                this.getBoundingBox().inflate(WaterWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isWaterPackmate(candidate));

        for (WaterWolf packmate : packmates) {
            LivingEntity reserved = packmate.getBrain()
                    .getMemory(ModMemoryModuleTypes.WATER_CURRENT_DASH_TARGET.get())
                    .orElse(null);
            if (reserved == target) {
                return true;
            }
        }
        return false;
    }

    private void tickCurrentDashCooldown() {
        this.tickIntegerCooldown(ModMemoryModuleTypes.WATER_CURRENT_DASH_COOLDOWN.get());
    }

    private void tickBubbleShelterCooldown() {
        this.tickIntegerCooldown(ModMemoryModuleTypes.WATER_BUBBLE_SHELTER_COOLDOWN.get());
    }

    private void tickPressureWaveCooldown() {
        this.tickIntegerCooldown(ModMemoryModuleTypes.WATER_PRESSURE_WAVE_COOLDOWN.get());
    }

    private void tickIntegerCooldown(net.minecraft.world.entity.ai.memory.MemoryModuleType<Integer> memory) {
        int cooldown = this.getBrain().getMemory(memory).orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(memory, cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(memory);
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.clearCurrentDashReservation();
    }

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || this.isAlliedTo(threat)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && !this.wantsToAttack(threat, owner)) {
            return;
        }

        this.clearCurrentDashReservation();
        this.getBrain().setMemory(ModMemoryModuleTypes.WATER_PACK_THREAT.get(), threat);

        double radius = pupEmergency ? 34.0D : WaterWolfPackSensor.PACK_SCAN_RADIUS;
        List<WaterWolf> packmates = level.getEntitiesOfClass(
                WaterWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isWaterPackmate(candidate));

        for (WaterWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.WATER_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidWaterCombatTarget(threat)
                    && packmate.getTarget() == null) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkWaterWolfSpawnRules(
            EntityType<WaterWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);
        boolean validGround = level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);

        // Land spawning is intentionally allowed for the amphibious identity,
        // but only close to actual water. This prevents a river-biome boundary
        // from producing Water Wolves deep inland.
        return inWater || (validGround && hasNearbyWater(level, pos, 8, 3));
    }

    private static boolean hasNearbyWater(LevelAccessor level, BlockPos origin, int horizontalRadius, int verticalRadius) {
        for (int dx = -horizontalRadius; dx <= horizontalRadius; ++dx) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; ++dz) {
                for (int dy = -verticalRadius; dy <= verticalRadius; ++dy) {
                    if (level.getFluidState(origin.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
