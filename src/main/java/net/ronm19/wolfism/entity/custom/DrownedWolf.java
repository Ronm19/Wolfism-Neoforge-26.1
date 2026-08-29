package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWaterAnimal;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.DrownedWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #24 - Drowned Wolf (female), "The Abyssal Drifter".
 *
 * <p>The shared AbstractWolfismWaterAnimal foundation owns amphibious physiology
 * and 3D swimming. This class keeps only Drowned-specific priorities: wild
 * go-to-water / go-to-beach / swim-up behavior, underwater patrol decisions,
 * pack combat and the canonical Drowned abilities.</p>
 *
 * <p>Canonical Wolfism kit:
 * Water Walking (amphibious water mobility), Trident Toss, Ocean Vision,
 * and Drown Others.</p>
 */
public final class DrownedWolf extends AbstractWolfismWaterAnimal {
    public static final int TRIDENT_TOSS_COOLDOWN_TICKS = 20 * 5;
    public static final int DROWN_OTHERS_COOLDOWN_TICKS = 20 * 8;

    private static final double TRIDENT_MIN_RANGE = 5.0D;
    private static final double TRIDENT_MAX_RANGE = 18.0D;
    private static final double OCEAN_VISION_RANGE = 16.0D;
    private static final int OCEAN_VISION_DURATION = 20 * 6;

    private static final int DROWN_AIR_REMOVAL = 80;
    private static final int DROWN_SLOWNESS_TICKS = 20 * 4;

    // Drowned-specific steering priorities. The shared aquatic foundation owns
    // the actual movement physics; these values only tune pursuit intent.
    private static final double UNDERWATER_FOLLOW_START_DISTANCE_SQR = 12.25D; // 3.5 blocks
    private static final double UNDERWATER_FOLLOW_STOP_DISTANCE_SQR = 6.25D;   // 2.5 blocks
    private static final double UNDERWATER_COMBAT_STOP_DISTANCE_SQR = 2.25D;   // 1.5 blocks
    private static final double UNDERWATER_FOLLOW_SPEED = 1.10D;
    private static final double UNDERWATER_CATCHUP_SPEED = 1.40D;
    private static final double UNDERWATER_COMBAT_SPEED = 1.30D;
    private static final double UNDERWATER_PATROL_SPEED = 0.72D;

    private boolean searchingForLand;
    private Vec3 underwaterPatrolTarget;
    private int underwaterPatrolCooldown;

    private static final class BrainHolder {
        private static final Brain.Provider<DrownedWolf> PROVIDER = Brain.<DrownedWolf>provider(
                ImmutableList.of(ModSensorTypes.DROWNED_PACK.get()),
                wolf -> List.of());
    }

    public DrownedWolf(EntityType<? extends DrownedWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.DROWNED_WOLF.get();
    }

    @Override
    protected Brain<DrownedWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<DrownedWolf> getBrain() {
        return (Brain<DrownedWolf>) super.getBrain();
    }

    @Override
    protected boolean usesSharedUnderwaterOwnerFollowing() {
        // Drowned Wolf has a richer species-specific steering priority stack:
        // combat -> owner -> wild patrol/environmental transitions.
        return false;
    }

    @Override
    protected boolean hasSpeciesWaterMovementIntent() {
        return this.searchingForLand || this.underwaterPatrolTarget != null;
    }


    @Override
    protected void registerGoals() {
        super.registerGoals();

        // These mirror vanilla Drowned's environment transitions but are only
        // for wild Drowned Wolves. A tamed wolf prioritizes its owner/family.
        this.goalSelector.addGoal(5, new DrownedWolfGoToWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new DrownedWolfGoToBeachGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new DrownedWolfSwimUpGoal(this, 1.0D, this.level().getSeaLevel()));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof DrownedWolf drowned && this.isDrownedPackmate(drowned)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.DROWNED_TRIDENT_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.DROWNED_DROWN_OTHERS_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.canParticipateInWolfismCombat() && this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.DROWNED_PACK_THREAT.get())
                    .orElse(null);

            if (sharedThreat != null && this.isValidDrownedCombatTarget(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        super.customServerAiStep(level);

        // Species-specific priorities choose the destination; the shared aquatic
        // foundation now owns the actual underwater movement physics.
        this.tickDirectUnderwaterSteering(level);
    }

    @Override
    public void tick() {
        super.tick();


        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.applyOceanVision();
        this.tryTridentToss(level);
    }

    // ---------------------------------------------------------------------
    // Direct underwater steering
    // ---------------------------------------------------------------------

    /**
     * Chooses Drowned-specific underwater destinations while the shared aquatic
     * foundation performs the actual swimming physics. Combat wins over owner
     * following, owner following wins over wild patrol, and environmental
     * transition goals temporarily suppress this steering entirely.
     */
    private void tickDirectUnderwaterSteering(ServerLevel level) {
        if (!this.isInWater()) {
            this.underwaterPatrolTarget = null;
            this.underwaterPatrolCooldown = 0;
            return;
        }

        if (this.isOrderedToSit() && !this.hasFamilyDefenseEmergency()) {
            this.stopWaterAwareMovement();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        /*
         * Beach/swim-up goals temporarily own locomotion. Do not let the
         * Drowned-specific combat/owner/patrol steering fight those goals.
         */
        if (this.searchingForLand) {
            return;
        }

        // 1) Combat has highest normal underwater steering priority.
        LivingEntity target = this.getTarget();
        if (target != null
                && target.isAlive()
                && this.isValidDrownedCombatTarget(target)) {
            this.underwaterPatrolTarget = null;

            this.getLookControl().setLookAt(
                    target,
                    this.getMaxHeadYRot() + 20.0F,
                    this.getMaxHeadXRot() + 20.0F);

            if (this.distanceToSqr(target) > UNDERWATER_COMBAT_STOP_DISTANCE_SQR) {
                this.moveToWaterAware(target, UNDERWATER_COMBAT_SPEED);
            } else {
                this.stopWaterAwareMovement();
            }
            return;
        }

        // 2) Tamed Drowned Wolf follows her owner in real XYZ space.
        LivingEntity owner = this.getOwner();
        if (this.isTame() && owner != null && owner.isAlive()) {
            this.underwaterPatrolTarget = null;

            double distanceSqr = this.distanceToSqr(owner);
            if (distanceSqr > UNDERWATER_FOLLOW_START_DISTANCE_SQR) {
                double speed = distanceSqr > 100.0D
                        ? UNDERWATER_CATCHUP_SPEED
                        : UNDERWATER_FOLLOW_SPEED;

                this.getLookControl().setLookAt(
                        owner,
                        this.getMaxHeadYRot() + 20.0F,
                        this.getMaxHeadXRot() + 20.0F);

                this.moveToWaterAware(owner, speed);
            } else if (distanceSqr <= UNDERWATER_FOLLOW_STOP_DISTANCE_SQR) {
                this.stopWaterAwareMovement();
            }
            return;
        }

        // 3) Wild Drowned Wolves patrol slowly and deliberately underwater.
        if (--this.underwaterPatrolCooldown <= 0
                || this.underwaterPatrolTarget == null
                || this.position().distanceToSqr(this.underwaterPatrolTarget) < 2.25D
                || !level.getFluidState(
                        BlockPos.containing(this.underwaterPatrolTarget))
                .is(FluidTags.WATER)) {
            this.underwaterPatrolTarget = this.findUnderwaterPatrolTarget(level);
            this.underwaterPatrolCooldown = 50 + this.random.nextInt(70);
        }

        if (this.underwaterPatrolTarget != null) {
            Vec3 destination = this.underwaterPatrolTarget;

            this.getLookControl().setLookAt(
                    destination.x,
                    destination.y,
                    destination.z,
                    this.getMaxHeadYRot() + 10.0F,
                    this.getMaxHeadXRot() + 10.0F);

            this.moveToWaterAware(
                    destination.x,
                    destination.y,
                    destination.z,
                    UNDERWATER_PATROL_SPEED);
        }
    }

    private Vec3 findUnderwaterPatrolTarget(ServerLevel level) {
        BlockPos origin = this.blockPosition();

        for (int attempt = 0; attempt < 14; ++attempt) {
            BlockPos candidate = origin.offset(
                    this.random.nextInt(13) - 6,
                    this.random.nextInt(7) - 3,
                    this.random.nextInt(13) - 6);

            if (!level.getFluidState(candidate).is(FluidTags.WATER)) {
                continue;
            }

            if (!level.getBlockState(candidate)
                    .isPathfindable(PathComputationType.WATER)) {
                continue;
            }

            // Prefer destinations with enough room for a wolf-sized body.
            if (!level.getFluidState(candidate.above()).is(FluidTags.WATER)
                    && this.random.nextBoolean()) {
                continue;
            }

            return Vec3.atCenterOf(candidate);
        }

        return null;
    }

    // ---------------------------------------------------------------------
    // Trident Toss
    // ---------------------------------------------------------------------

    private void tryTridentToss(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.DROWNED_TRIDENT_COOLDOWN.get())) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (!this.isValidDrownedCombatTarget(target)
                || !this.getSensing().hasLineOfSight(target)) {
            return;
        }

        double distance = this.distanceTo(target);
        if (distance < TRIDENT_MIN_RANGE || distance > TRIDENT_MAX_RANGE) {
            return;
        }

        ItemStack tridentStack = new ItemStack(Items.TRIDENT);
        ThrownTrident trident = new ThrownTrident(
                this.level(),
                this,
                tridentStack);

        trident.pickup = AbstractArrow.Pickup.DISALLOWED;
        trident.setBaseDamage(this.isInWater() ? 6.0D : 5.0D);

        double xd = target.getX() - this.getX();
        double yd = target.getY(0.3333333333333333D) - trident.getY();
        double zd = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(xd * xd + zd * zd);

        Projectile.spawnProjectileUsingShoot(
                trident,
                level,
                tridentStack,
                xd,
                yd + horizontalDistance * 0.20D,
                zd,
                this.isInWater() ? 1.75F : 1.55F,
                8.0F);

        this.getBrain().setMemory(
                ModMemoryModuleTypes.DROWNED_TRIDENT_COOLDOWN.get(),
                TRIDENT_TOSS_COOLDOWN_TICKS);

        // Nearby packmates receive a short anti-volley lockout rather than
        // throwing every trident on the exact same tick.
        this.sharePackCooldown(
                ModMemoryModuleTypes.DROWNED_TRIDENT_COOLDOWN.get(),
                20);

        this.playSound(
                SoundEvents.DROWNED_SHOOT,
                1.0F,
                0.90F + this.random.nextFloat() * 0.20F);
    }

    // ---------------------------------------------------------------------
    // Ocean Vision
    // ---------------------------------------------------------------------

    private void applyOceanVision() {
        if (!this.isTame()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null
                || !owner.isAlive()
                || !owner.isInWater()
                || this.distanceToSqr(owner) > OCEAN_VISION_RANGE * OCEAN_VISION_RANGE) {
            return;
        }

        if (this.tickCount % 40 == 0) {
            owner.addEffect(
                    new MobEffectInstance(
                            MobEffects.NIGHT_VISION,
                            OCEAN_VISION_DURATION,
                            0),
                    this);
        }
    }

    // ---------------------------------------------------------------------
    // Drown Others
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(entity);
        if (!hurt
                || !this.canParticipateInWolfismCombat()
                || !(entity instanceof LivingEntity target)
                || this.isDrownedFamilyMember(target)) {
            return hurt;
        }

        if (target.isInWater()
                && !this.isCoolingDown(ModMemoryModuleTypes.DROWNED_DROWN_OTHERS_COOLDOWN.get())) {
            target.setAirSupply(
                    Math.max(
                            -20,
                            target.getAirSupply() - DROWN_AIR_REMOVAL));

            target.addEffect(
                    new MobEffectInstance(
                            MobEffects.SLOWNESS,
                            DROWN_SLOWNESS_TICKS,
                            0),
                    this);

            // A small downward drag makes the drowning pressure visible.
            target.setDeltaMovement(
                    target.getDeltaMovement().add(0.0D, -0.12D, 0.0D));
            target.hurtMarked = true;

            level.sendParticles(
                    ParticleTypes.BUBBLE,
                    target.getX(),
                    target.getY(0.55D),
                    target.getZ(),
                    16,
                    0.28D,
                    0.34D,
                    0.28D,
                    0.02D);

            this.getBrain().setMemory(
                    ModMemoryModuleTypes.DROWNED_DROWN_OTHERS_COOLDOWN.get(),
                    DROWN_OTHERS_COOLDOWN_TICKS);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    public boolean isDrownedPackmate(DrownedWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(
                this.getOwnerReference(),
                other.getOwnerReference());
    }

    public boolean isDrownedFamilyMember(LivingEntity entity) {
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

        return entity instanceof DrownedWolf drowned
                && this.isDrownedPackmate(drowned);
    }

    public boolean isValidDrownedCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.canParticipateInWolfismCombat()
                || !this.canAttack(target)
                || this.isAlliedTo(target)
                || this.isDrownedFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidDrownedCombatTarget(threat)) {
            return;
        }

        for (DrownedWolf mate : level.getEntitiesOfClass(
                DrownedWolf.class,
                this.getBoundingBox().inflate(DrownedWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive()
                        && (wolf == this || this.isDrownedPackmate(wolf)))) {
            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.DROWNED_PACK_THREAT.get(),
                    threat);

            if (mate.canParticipateInWolfismCombat()
                    && mate.isValidDrownedCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Shared cooldown helpers
    // ---------------------------------------------------------------------

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

    private void sharePackCooldown(
            MemoryModuleType<Integer> memory,
            int minimumTicks) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        for (DrownedWolf mate : level.getEntitiesOfClass(
                DrownedWolf.class,
                this.getBoundingBox().inflate(DrownedWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive()
                        && (wolf == this || this.isDrownedPackmate(wolf)))) {
            int current = mate.getBrain().getMemory(memory).orElse(0);
            if (current < minimumTicks) {
                mate.getBrain().setMemory(memory, minimumTicks);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    public static boolean checkDrownedWolfSpawnRules(
            EntityType<DrownedWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    // ---------------------------------------------------------------------
    // Vanilla-Drowned-inspired environmental goals
    // ---------------------------------------------------------------------

    public void setSearchingForLand(boolean searchingForLand) {
        this.searchingForLand = searchingForLand;
    }

    private static final class DrownedWolfGoToWaterGoal extends Goal {
        private final DrownedWolf wolf;
        private final double speedModifier;
        private double wantedX;
        private double wantedY;
        private double wantedZ;

        private DrownedWolfGoToWaterGoal(
                DrownedWolf wolf,
                double speedModifier) {
            this.wolf = wolf;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.wolf.isTame()
                    || this.wolf.getTarget() != null
                    || !this.wolf.level().isBrightOutside()
                    || this.wolf.isInWater()) {
                return false;
            }

            Vec3 pos = this.getWaterPos();
            if (pos == null) {
                return false;
            }

            this.wantedX = pos.x;
            this.wantedY = pos.y;
            this.wantedZ = pos.z;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.wolf.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.wolf.getNavigation().moveTo(
                    this.wantedX,
                    this.wantedY,
                    this.wantedZ,
                    this.speedModifier);
        }

        private Vec3 getWaterPos() {
            RandomSource random = this.wolf.getRandom();
            BlockPos pos = this.wolf.blockPosition();

            for (int i = 0; i < 10; ++i) {
                BlockPos randomPos = pos.offset(
                        random.nextInt(20) - 10,
                        2 - random.nextInt(8),
                        random.nextInt(20) - 10);

                if (this.wolf.level().getBlockState(randomPos).is(Blocks.WATER)) {
                    return Vec3.atBottomCenterOf(randomPos);
                }
            }

            return null;
        }
    }

    private static final class DrownedWolfGoToBeachGoal extends MoveToBlockGoal {
        private final DrownedWolf wolf;

        private DrownedWolfGoToBeachGoal(
                DrownedWolf wolf,
                double speedModifier) {
            super(wolf, speedModifier, 8, 2);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            return !this.wolf.isTame()
                    && this.wolf.getTarget() == null
                    && super.canUse()
                    && !this.wolf.level().isBrightOutside()
                    && this.wolf.isInWater()
                    && this.wolf.getY() >= this.wolf.level().getSeaLevel() - 3;
        }

        @Override
        protected boolean isValidTarget(
                LevelReader level,
                BlockPos pos) {
            BlockPos above = pos.above();
            return level.isEmptyBlock(above)
                    && level.isEmptyBlock(above.above())
                    && level.getBlockState(pos).entityCanStandOn(
                    level,
                    pos,
                    this.wolf);
        }

        @Override
        public void start() {
            /*
             * Beach movement is a LAND transition. Do not advertise aquatic
             * movement intent here or the shared base will switch back to the
             * water navigator while this goal is trying to leave the ocean.
             */
            this.wolf.setSearchingForLand(false);
            super.start();
        }
    }

    private static final class DrownedWolfSwimUpGoal extends Goal {
        private final DrownedWolf wolf;
        private final double speedModifier;
        private final int seaLevel;
        private boolean stuck;
        private Vec3 swimTarget;

        private DrownedWolfSwimUpGoal(
                DrownedWolf wolf,
                double speedModifier,
                int seaLevel) {
            this.wolf = wolf;
            this.speedModifier = speedModifier;
            this.seaLevel = seaLevel;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.wolf.isTame()
                    && this.wolf.getTarget() == null
                    && !this.wolf.level().isBrightOutside()
                    && this.wolf.isInWater()
                    && this.wolf.getY() < this.seaLevel - 2;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && !this.stuck;
        }

        @Override
        public void tick() {
            if (this.wolf.getY() >= this.seaLevel - 1) {
                return;
            }

            if (this.swimTarget == null
                    || this.wolf.position().distanceToSqr(this.swimTarget) < 4.0D
                    || !this.wolf.level()
                    .getFluidState(BlockPos.containing(this.swimTarget))
                    .is(FluidTags.WATER)) {
                Vec3 nextPos = DefaultRandomPos.getPosTowards(
                        this.wolf,
                        4,
                        8,
                        new Vec3(
                                this.wolf.getX(),
                                this.seaLevel - 1,
                                this.wolf.getZ()),
                        (float) (Math.PI / 2.0D));

                if (nextPos == null) {
                    this.stuck = true;
                    return;
                }

                this.swimTarget = nextPos;
            }

            this.wolf.moveToWaterAware(
                    this.swimTarget.x,
                    this.swimTarget.y,
                    this.swimTarget.z,
                    this.speedModifier);
        }

        @Override
        public void start() {
            this.wolf.setSearchingForLand(true);
            this.stuck = false;
            this.swimTarget = null;
        }

        @Override
        public void stop() {
            this.wolf.setSearchingForLand(false);
            this.swimTarget = null;
            this.wolf.stopWaterAwareMovement();
        }
    }


}