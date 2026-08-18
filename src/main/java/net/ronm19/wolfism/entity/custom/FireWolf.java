package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.FireFlameRushGoal;
import net.ronm19.wolfism.entity.ai.goal.FireHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.FirePackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.FirePackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.FirePupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.FirePupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.FirePupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.FireWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Fire Wolf: Wolfism's first elemental species and aggressive gap-closing striker.
 *
 * <p>Fire Wolves keep vanilla wolf fundamentals, but layer on fire immunity,
 * Burning Bite pressure, a steering Flame Rush, pack-aware rush reservations,
 * hot-biome awareness, and the same universal Wolfism family/Creeper rules.</p>
 */
public final class FireWolf extends AbstractWolfismWolf {
    public static final float FLAME_RUSH_DAMAGE_MULTIPLIER = 1.25F;
    public static final float BURNING_BITE_SECONDS = 3.0F;
    public static final float FLAME_RUSH_BURN_SECONDS = 6.0F;
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;
    public static final int FLAME_RUSH_COOLDOWN_TICKS = 20 * 22;
    public static final int HOT_FLAME_RUSH_COOLDOWN_TICKS = 20 * 20;

    private static final int FLAME_RUSH_DURATION_TICKS = 12;
    private static final double FLAME_RUSH_SPEED = 0.92D;
    private static final double FLAME_RUSH_IMPACT_DISTANCE_SQR = 2.15D * 2.15D;
    private static final double FLAME_RUSH_STEER_BLEND = 0.25D;

    private int flameRushTicks;
    private Vec3 flameRushDirection = Vec3.ZERO;

    private static final class BrainHolder {
        private static final Brain.Provider<FireWolf> PROVIDER = Brain.<FireWolf>provider(
                ImmutableList.of(ModSensorTypes.FIRE_PACK.get(), ModSensorTypes.FIRE_COMBAT.get()),
                wolf -> List.of());
    }

    public FireWolf(EntityType<? extends FireWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.FIRE_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new FirePupRetreatGoal(this));
        this.goalSelector.addGoal(2, new FireFlameRushGoal(this));
        this.goalSelector.addGoal(5, new FirePupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new FirePackCohesionGoal(this));
        this.goalSelector.addGoal(7, new FirePupPlayGoal(this));

        this.targetSelector.addGoal(3, new FirePackAssistGoal(this));
        this.targetSelector.addGoal(6, new FireHuntGoal(this));
    }

    @Override
    protected Brain<FireWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<FireWolf> getBrain() {
        return (Brain<FireWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
        this.tickFlameRushCooldown();
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            if (this.isFlameRushing()) {
                this.endFlameRush();
            }
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.flameRushTicks > 0) {
            this.tickFlameRush();
        } else if (!this.isBaby()
                && this.level() instanceof ServerLevel level
                && this.getRandom().nextInt(90) == 0) {
            level.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX() + (this.getRandom().nextDouble() - 0.5D) * 0.45D,
                    this.getY() + 0.30D + this.getRandom().nextDouble() * 0.45D,
                    this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 0.45D,
                    1,
                    0.01D,
                    0.02D,
                    0.01D,
                    0.0D);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isFlameRushing() {
        return this.flameRushTicks > 0;
    }

    public boolean isFlameRushCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get())
                .orElse(0) > 0;
    }

    public boolean canStartFlameRushAgainst(LivingEntity target) {
        if (target == null
                || target instanceof Creeper
                || !target.isAlive()
                || this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isFlameRushing()
                || this.isFlameRushCoolingDown()
                || !this.onGround()
                || !this.isValidFireCombatTarget(target)) {
            return false;
        }

        double distance = this.distanceToSqr(target);
        return distance >= FireFlameRushGoal.MIN_START_DISTANCE_SQR
                && distance <= FireFlameRushGoal.MAX_START_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target)
                && !this.isFlameRushReservedByPack(target);
    }

    public boolean beginFlameRush(LivingEntity target) {
        if (!this.canStartFlameRushAgainst(target)) {
            return false;
        }

        Vec3 direction = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (direction.lengthSqr() < 1.0E-5D) {
            return false;
        }

        this.flameRushDirection = direction.normalize();
        this.flameRushTicks = FLAME_RUSH_DURATION_TICKS;
        this.getBrain().setMemory(ModMemoryModuleTypes.FIRE_RUSH_TARGET.get(), target);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get(),
                this.isHotEnvironment() ? HOT_FLAME_RUSH_COOLDOWN_TICKS : FLAME_RUSH_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setSprinting(true);
        this.faceFlameRushDirection();

        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX(),
                    this.getY() + 0.35D,
                    this.getZ(),
                    7,
                    0.25D,
                    0.22D,
                    0.25D,
                    0.02D);
        }
        return true;
    }

    private void tickFlameRush() {
        LivingEntity target = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_RUSH_TARGET.get())
                .orElse(null);

        if (target == null
                || !target.isAlive()
                || target instanceof Creeper
                || !this.isValidFireCombatTarget(target)) {
            this.endFlameRush();
            return;
        }

        Vec3 desired = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (desired.lengthSqr() > 1.0E-5D) {
            desired = desired.normalize();
            Vec3 steered = this.flameRushDirection
                    .scale(1.0D - FLAME_RUSH_STEER_BLEND)
                    .add(desired.scale(FLAME_RUSH_STEER_BLEND));
            if (steered.lengthSqr() > 1.0E-5D) {
                this.flameRushDirection = steered.normalize();
            }
        }

        this.faceFlameRushDirection();

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX() - this.flameRushDirection.x * 0.45D,
                    this.getY() + 0.30D,
                    this.getZ() - this.flameRushDirection.z * 0.45D,
                    2,
                    0.14D,
                    0.12D,
                    0.14D,
                    0.01D);

            if (this.distanceToSqr(target) <= FLAME_RUSH_IMPACT_DISTANCE_SQR) {
                if (this.doHurtTarget(serverLevel, target)) {
                    double dx = this.getX() - target.getX();
                    double dz = this.getZ() - target.getZ();
                    target.knockback(1.0D, dx, dz);
                    serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            target.getX(),
                            target.getY() + target.getBbHeight() * 0.55D,
                            target.getZ(),
                            14,
                            0.35D,
                            0.35D,
                            0.35D,
                            0.035D);
                }
                this.endFlameRush();
                return;
            }
        }

        if (this.horizontalCollision) {
            this.endFlameRush();
            return;
        }

        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                this.flameRushDirection.x * FLAME_RUSH_SPEED,
                current.y,
                this.flameRushDirection.z * FLAME_RUSH_SPEED);
        this.hurtMarked = true;

        --this.flameRushTicks;
        if (this.flameRushTicks <= 0) {
            this.endFlameRush();
        }
    }

    public void endFlameRush() {
        this.flameRushTicks = 0;
        this.flameRushDirection = Vec3.ZERO;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_RUSH_TARGET.get());
        this.setSprinting(false);
    }

    private void faceFlameRushDirection() {
        if (this.flameRushDirection.lengthSqr() < 1.0E-5D) {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-this.flameRushDirection.x, this.flameRushDirection.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    private boolean isFlameRushReservedByPack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        List<FireWolf> packmates = serverLevel.getEntitiesOfClass(
                FireWolf.class,
                this.getBoundingBox().inflate(FireWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate != this && candidate.isAlive() && this.isFirePackmate(candidate));

        for (FireWolf packmate : packmates) {
            LivingEntity reserved = packmate.getBrain()
                    .getMemory(ModMemoryModuleTypes.FIRE_RUSH_TARGET.get())
                    .orElse(null);
            if (reserved == target && packmate.isFlameRushing()) {
                return true;
            }
        }
        return false;
    }

    private void tickFlameRushCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_RUSH_COOLDOWN.get());
        }
    }

    public boolean isFirePackmate(FireWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidFireCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
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

    public boolean hasEnoughHuntersFor(LivingEntity prey) {
        return !this.isBaby();
    }

    public boolean isHotEnvironment() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_HOT_ENVIRONMENT.get())
                .orElse(false);
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get());
        }
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_HUNT_TARGET.get());

        List<FireWolf> packmates = serverLevel.getEntitiesOfClass(
                FireWolf.class,
                this.getBoundingBox().inflate(FireWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isFirePackmate(candidate));
        for (FireWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.FIRE_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_HUNT_TARGET.get());
        }
    }


    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (this.isFlameRushing()) {
            this.endFlameRush();
        }
        this.getBrain().eraseMemory(ModMemoryModuleTypes.FIRE_HUNT_TARGET.get());
    }

    public void alertPackToThreat(LivingEntity threat, boolean pupEmergency) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || threat == null
                || !threat.isAlive()
                || this.isAlliedTo(threat)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && !this.wantsToAttack(threat, owner)) {
            return;
        }

        if (this.isFlameRushing()) {
            this.endFlameRush();
        }

        double radius = pupEmergency ? 34.0D : FireWolfPackSensor.PACK_SCAN_RADIUS;
        this.getBrain().setMemory(ModMemoryModuleTypes.FIRE_PACK_THREAT.get(), threat);

        List<FireWolf> packmates = serverLevel.getEntitiesOfClass(
                FireWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isFirePackmate(candidate));

        for (FireWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.FIRE_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidFireCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkFireWolfSpawnRules(
            EntityType<FireWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        var ground = level.getBlockState(pos.below());
        boolean validGround = ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                || ground.is(Blocks.RED_SAND);
        return validGround && isBrightEnoughToSpawn(level, pos);
    }
}
