package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.StormPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.StormPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.StormPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.StormPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.StormPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.StormThunderStrikeGoal;
import net.ronm19.wolfism.entity.ai.goal.StormThunderstormGoal;
import net.ronm19.wolfism.entity.ai.sensor.StormWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Storm Wolf: burst-damage and electrical battlefield-disruption specialist.
 *
 * <p>Storm builds a real 0-100 electrical charge. Combat can build it under
 * clear skies, rain builds it faster, and thunder/localized Thunderstorm builds
 * it fastest. Full charge enables Thunder Strike. Thunderstorm is a separate
 * major ability that creates a localized electrical combat zone without
 * changing the world's weather or spawning vanilla lightning bolts.</p>
 */
public final class StormWolf extends AbstractWolfismWolf {
    public static final int MAX_THUNDER_CHARGE = 100;
    public static final int FULL_CHARGE_HOLD_TICKS = 20 * 20;

    public static final int THUNDER_STRIKE_COOLDOWN_TICKS = 20 * 16;
    public static final int THUNDERSTORM_COOLDOWN_TICKS = 20 * 75;
    public static final int THUNDERSTORM_DURATION_TICKS = 20 * 9;

    public static final float CLEAR_THUNDER_STRIKE_DAMAGE = 6.0F;
    public static final float RAIN_THUNDER_STRIKE_DAMAGE = 7.0F;
    public static final float STORM_THUNDER_STRIKE_DAMAGE = 8.0F;
    public static final float THUNDER_CHAIN_DAMAGE = 4.0F;
    public static final float THUNDERSTORM_PULSE_DAMAGE = 3.0F;
    public static final float THUNDERSTORM_MELEE_MULTIPLIER = 1.15F;

    public static final double THUNDERSTORM_RADIUS = 11.0D;
    private static final double THUNDER_CHAIN_RADIUS = 5.5D;
    private static final int THUNDERSTORM_MIN_CHARGE = 60;
    private static final int THUNDERSTORM_PULSE_INTERVAL = 24;

    private int fullChargeHoldTicks;
    private int thunderstormTicks;
    private int thunderstormPulseDelay;
    private boolean electricalImpactActive;

    private static final class BrainHolder {
        private static final Brain.Provider<StormWolf> PROVIDER = Brain.<StormWolf>provider(
                ImmutableList.of(ModSensorTypes.STORM_PACK.get(), ModSensorTypes.STORM_COMBAT.get()),
                wolf -> List.of());
    }

    public StormWolf(EntityType<? extends StormWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.STORM_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new StormPupRetreatGoal(this));
        this.goalSelector.addGoal(2, new StormThunderstormGoal(this));
        this.goalSelector.addGoal(3, new StormThunderStrikeGoal(this));
        this.goalSelector.addGoal(5, new StormPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new StormPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new StormPupPlayGoal(this));

        this.targetSelector.addGoal(3, new StormPackAssistGoal(this));
    }

    @Override
    protected Brain<StormWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<StormWolf> getBrain() {
        return (Brain<StormWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickThunderStrikeCooldown();
        this.tickThunderstormCooldown();
        this.tickThunderCharge(level);
        this.tickLocalizedThunderstorm(level);

        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            this.clearThunderStrikeReservation();
            this.endThunderstorm();
            this.setThunderCharge(0);
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        int charge = this.getThunderCharge();
        if (!this.isBaby() && charge > 0) {
            int interval = charge >= MAX_THUNDER_CHARGE ? 5 : Math.max(12, 34 - charge / 4);
            if (this.tickCount % interval == 0) {
                this.sendSparkParticles(level, this, charge >= MAX_THUNDER_CHARGE ? 3 : 1, 0.18D);
            }
        } else if (this.isBaby()
                && (this.isRainingHere() || this.isNaturalThunderstorm())
                && this.getRandom().nextInt(80) == 0) {
            // Harmless storm-pup spark: visual personality only.
            this.sendSparkParticles(level, this, 1, 0.08D);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isElectricalImpactActive() {
        return this.electricalImpactActive;
    }

    public int getThunderCharge() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_CHARGE.get())
                .orElse(0);
    }

    public boolean isFullyCharged() {
        return this.getThunderCharge() >= MAX_THUNDER_CHARGE;
    }

    public void addThunderCharge(int amount) {
        if (this.isBaby() || amount <= 0) {
            return;
        }
        this.setThunderCharge(Math.min(MAX_THUNDER_CHARGE, this.getThunderCharge() + amount));
    }

    private void setThunderCharge(int value) {
        int clamped = Math.max(0, Math.min(MAX_THUNDER_CHARGE, value));
        if (clamped > 0) {
            this.getBrain().setMemory(ModMemoryModuleTypes.STORM_CHARGE.get(), clamped);
        } else {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.STORM_CHARGE.get());
        }
        if (clamped < MAX_THUNDER_CHARGE) {
            this.fullChargeHoldTicks = 0;
        }
    }

    private void tickThunderCharge(ServerLevel level) {
        if (this.isBaby() || this.isOrderedToSit() || this.isInSittingPose()) {
            this.fullChargeHoldTicks = 0;
            return;
        }

        int current = this.getThunderCharge();
        if (current >= MAX_THUNDER_CHARGE) {
            if (!this.isThunderstormActive() && ++this.fullChargeHoldTicks > FULL_CHARGE_HOLD_TICKS) {
                this.setThunderCharge(0);
                this.sendSparkParticles(level, this, 5, 0.24D);
            }
            return;
        }

        boolean legitimateCombat = this.getTarget() != null
                && !(this.getTarget() instanceof Creeper)
                && this.isValidStormCombatTarget(this.getTarget());

        int gain = 0;
        if (this.isThunderstormActive()) {
            gain = 2;
        } else if (this.isNaturalThunderstorm()) {
            gain = 1;
        } else if (this.isRainingHere() && this.tickCount % 2 == 0) {
            gain = 1;
        } else if (legitimateCombat && this.tickCount % 4 == 0) {
            gain = 1;
        }

        if (gain > 0) {
            this.addThunderCharge(gain);
        }
    }

    public boolean isThunderStrikeCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean canStartThunderStrikeAgainst(LivingEntity target) {
        if (target == null
                || target instanceof Creeper
                || !target.isAlive()
                || this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || !this.isFullyCharged()
                || this.isThunderStrikeCoolingDown()
                || this.hasNearbyPrimingCreeper()
                || !this.isValidStormCombatTarget(target)) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= StormThunderStrikeGoal.MIN_START_DISTANCE_SQR
                && distance <= StormThunderStrikeGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target)
                && !this.isThunderStrikeReservedByPack(target);
    }

    public void reserveThunderStrikeTarget(LivingEntity target) {
        if (target != null && target.isAlive()) {
            this.getBrain().setMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_TARGET.get(), target);
        }
    }

    public void clearThunderStrikeReservation() {
        this.getBrain().eraseMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_TARGET.get());
    }

    public boolean performThunderStrike(LivingEntity target) {
        if (!this.canStartThunderStrikeAgainst(target) || !(this.level() instanceof ServerLevel level)) {
            this.clearThunderStrikeReservation();
            return false;
        }

        float damage = this.isNaturalThunderstorm()
                ? STORM_THUNDER_STRIKE_DAMAGE
                : (this.isRainingHere() ? RAIN_THUNDER_STRIKE_DAMAGE : CLEAR_THUNDER_STRIKE_DAMAGE);

        this.setThunderCharge(0);
        this.fullChargeHoldTicks = 0;
        this.electricalImpactActive = true;

        boolean hurt;
        try {
            hurt = target.hurtServer(level, this.damageSources().mobAttack(this), damage);
            if (hurt && target.isAlive()) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
                double dx = this.getX() - target.getX();
                double dz = this.getZ() - target.getZ();
                target.knockback(this.isNaturalThunderstorm() ? 1.0D : 0.75D, dx, dz);
                this.sendSparkParticles(level, target, 22, 0.42D);

                if (this.isNaturalThunderstorm()) {
                    this.performThunderChain(level, target);
                }
            }
        } finally {
            this.electricalImpactActive = false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get(),
                THUNDER_STRIKE_COOLDOWN_TICKS);
        this.clearThunderStrikeReservation();
        return hurt;
    }

    private void performThunderChain(ServerLevel level, LivingEntity primaryTarget) {
        LivingEntity chainTarget = level.getEntitiesOfClass(
                        LivingEntity.class,
                        primaryTarget.getBoundingBox().inflate(THUNDER_CHAIN_RADIUS),
                        candidate -> candidate != primaryTarget
                                && candidate != this
                                && candidate.isAlive()
                                && candidate instanceof Enemy
                                && !(candidate instanceof Creeper)
                                && this.isValidStormCombatTarget(candidate))
                .stream()
                .min(Comparator.comparingDouble(primaryTarget::distanceToSqr))
                .orElse(null);

        if (chainTarget == null) {
            return;
        }

        if (chainTarget.hurtServer(level, this.damageSources().mobAttack(this), THUNDER_CHAIN_DAMAGE)
                && chainTarget.isAlive()) {
            chainTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
            this.sendSparkParticles(level, chainTarget, 12, 0.30D);
        }
    }

    private boolean isThunderStrikeReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        List<StormWolf> packmates = level.getEntitiesOfClass(
                StormWolf.class,
                this.getBoundingBox().inflate(StormWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isStormPackmate(candidate));

        for (StormWolf packmate : packmates) {
            LivingEntity reserved = packmate.getBrain()
                    .getMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_TARGET.get())
                    .orElse(null);
            if (reserved == target) {
                return true;
            }
        }
        return false;
    }

    public boolean isThunderstormActive() {
        return this.thunderstormTicks > 0;
    }

    public boolean isThunderstormCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean canStartThunderstorm() {
        if (this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isThunderstormActive()
                || this.isThunderstormCoolingDown()
                || this.getThunderCharge() < THUNDERSTORM_MIN_CHARGE
                || this.hasNearbyPrimingCreeper()
                || this.isThunderstormActiveInPack()) {
            return false;
        }

        LivingEntity target = this.getTarget();
        if (target == null
                || target instanceof Creeper
                || !target.isAlive()
                || !this.isValidStormCombatTarget(target)
                || this.distanceToSqr(target) > THUNDERSTORM_RADIUS * THUNDERSTORM_RADIUS) {
            return false;
        }

        if (this.isNaturalThunderstorm() || this.getHealth() <= this.getMaxHealth() * 0.45F) {
            return true;
        }

        return this.countNearbyStormTargets() >= 2;
    }

    public boolean beginThunderstorm() {
        if (!this.canStartThunderstorm() || !(this.level() instanceof ServerLevel level)) {
            return false;
        }

        this.setThunderCharge(0);
        this.fullChargeHoldTicks = 0;
        this.thunderstormTicks = THUNDERSTORM_DURATION_TICKS;
        this.thunderstormPulseDelay = 8;
        this.getBrain().setMemory(
                ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get(),
                THUNDERSTORM_COOLDOWN_TICKS);

        WolfVfx.sendParticles("storm_wolf", level,
                ParticleTypes.ELECTRIC_SPARK,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                36,
                0.90D,
                0.60D,
                0.90D,
                0.09D);
        return true;
    }

    public void endThunderstorm() {
        this.thunderstormTicks = 0;
        this.thunderstormPulseDelay = 0;
    }

    private void tickLocalizedThunderstorm(ServerLevel level) {
        if (!this.isThunderstormActive()) {
            return;
        }

        if (this.isBaby() || this.isOrderedToSit() || this.isInSittingPose()) {
            this.endThunderstorm();
            return;
        }

        if (this.hasNearbyPrimingCreeper()) {
            --this.thunderstormTicks;
            if (this.thunderstormTicks <= 0) {
                this.endThunderstorm();
            }
            return;
        }

        if (this.tickCount % 4 == 0) {
            WolfVfx.sendParticles("storm_wolf", level,
                    ParticleTypes.ELECTRIC_SPARK,
                    this.getX(),
                    this.getY() + 0.40D,
                    this.getZ(),
                    4,
                    0.65D,
                    0.40D,
                    0.65D,
                    0.035D);
        }

        if (--this.thunderstormPulseDelay <= 0) {
            this.thunderstormPulseDelay = THUNDERSTORM_PULSE_INTERVAL;
            LivingEntity pulseTarget = this.findThunderstormPulseTarget(level);
            if (pulseTarget != null) {
                this.electricalImpactActive = true;
                try {
                    if (pulseTarget.hurtServer(
                                    level,
                                    this.damageSources().mobAttack(this),
                                    THUNDERSTORM_PULSE_DAMAGE)
                            && pulseTarget.isAlive()) {
                        pulseTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 0));
                        double dx = this.getX() - pulseTarget.getX();
                        double dz = this.getZ() - pulseTarget.getZ();
                        pulseTarget.knockback(0.35D, dx, dz);
                        this.sendSparkParticles(level, pulseTarget, 9, 0.28D);
                    }
                } finally {
                    this.electricalImpactActive = false;
                }
            }
        }

        --this.thunderstormTicks;
        if (this.thunderstormTicks <= 0) {
            this.endThunderstorm();
        }
    }

    private LivingEntity findThunderstormPulseTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current != null
                && !(current instanceof Creeper)
                && this.isValidStormCombatTarget(current)
                && this.distanceToSqr(current) <= THUNDERSTORM_RADIUS * THUNDERSTORM_RADIUS) {
            return current;
        }

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(THUNDERSTORM_RADIUS),
                        candidate -> candidate != this
                                && candidate.isAlive()
                                && candidate instanceof Enemy
                                && !(candidate instanceof Creeper)
                                && this.isValidStormCombatTarget(candidate))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private int countNearbyStormTargets() {
        if (!(this.level() instanceof ServerLevel level)) {
            return 0;
        }

        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THUNDERSTORM_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && this.isValidStormCombatTarget(candidate)).size();
    }

    private boolean isThunderstormActiveInPack() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        return !level.getEntitiesOfClass(
                StormWolf.class,
                this.getBoundingBox().inflate(StormWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && candidate.isThunderstormActive()
                        && this.isStormPackmate(candidate)).isEmpty();
    }

    private void tickThunderStrikeCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get())
                .orElse(0);
        if (cooldown <= 0) {
            return;
        }

        int reduction = this.isThunderstormActive() ? 3 : (this.isNaturalThunderstorm() ? 2 : 1);
        int next = cooldown - reduction;
        if (next > 0) {
            this.getBrain().setMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get(), next);
        } else {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.STORM_THUNDER_STRIKE_COOLDOWN.get());
        }
    }

    private void tickThunderstormCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get())
                .orElse(0);
        if (cooldown <= 0) {
            return;
        }

        int next = cooldown - (this.isNaturalThunderstorm() ? 2 : 1);
        if (next > 0) {
            this.getBrain().setMemory(ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get(), next);
        } else {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.STORM_THUNDERSTORM_COOLDOWN.get());
        }
    }

    public int getWeatherState() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.STORM_WEATHER_STATE.get())
                .orElseGet(() -> this.isNaturalThunderstorm() ? 2 : (this.isRainingHere() ? 1 : 0));
    }

    public boolean isRainingHere() {
        return this.level().isRainingAt(this.blockPosition());
    }

    public boolean isNaturalThunderstorm() {
        return this.level().isThundering() && this.level().isRainingAt(this.blockPosition());
    }

    public boolean isStormPackmate(StormWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidStormCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof StormWolf stormWolf && this.isStormPackmate(stormWolf)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public static boolean isPreferredPrey(LivingEntity target) {
        EntityType<?> type = target.getType();
        return type == EntityType.RABBIT
                || type == EntityType.CHICKEN
                || type == EntityType.SHEEP;
    }


    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.clearThunderStrikeReservation();
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

        this.clearThunderStrikeReservation();

        double radius = pupEmergency ? 34.0D : StormWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.STORM_PACK_THREAT.get(), threat);

        List<StormWolf> packmates = level.getEntitiesOfClass(
                StormWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isStormPackmate(candidate));

        for (StormWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.STORM_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidStormCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    private boolean hasNearbyPrimingCreeper() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        return !level.getEntitiesOfClass(
                Creeper.class,
                this.getBoundingBox().inflate(16.0D),
                creeper -> creeper.isAlive()
                        && (creeper.isIgnited()
                        || creeper.getSwellDir() > 0
                        || creeper.getSwelling(1.0F) > 0.0F)).isEmpty();
    }

    private void sendSparkParticles(ServerLevel level, LivingEntity target, int count, double spread) {
        WolfVfx.sendParticles("storm_wolf", level,
                ParticleTypes.ELECTRIC_SPARK,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.50D,
                target.getZ(),
                count,
                spread,
                spread,
                spread,
                0.025D);
    }

    public static boolean checkStormWolfSpawnRules(
            EntityType<StormWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
