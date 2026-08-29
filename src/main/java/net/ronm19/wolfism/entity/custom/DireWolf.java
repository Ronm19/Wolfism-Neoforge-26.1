package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.DireChargeAttackGoal;
import net.ronm19.wolfism.entity.ai.goal.DireDemolitionGoal;
import net.ronm19.wolfism.entity.ai.goal.DireHuntGoal;
import net.ronm19.wolfism.entity.ai.goal.DirePackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.DirePackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.DirePupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.DirePupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.DirePupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.DireWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBlockTags;

/**
 * Dire Wolf: Wolfism's massive natural frontline wolf and living battering ram.
 *
 * <p>The Dire Wolf is intentionally physical rather than magical: high mass,
 * strong bites, a committed combat charge, and an owner-commanded demolition
 * charge for blocks explicitly opted into wolfism:dire_charge_breakable. Dire is
 * intentionally not rideable; its identity stays focused on size, pressure and
 * controlled physical power while Wolfism's shared Creeper safety remains higher
 * priority.</p>
 */
public final class DireWolf extends AbstractWolfismWolf {
    public static final int HUNT_COOLDOWN_TICKS = 20 * 10;
    public static final float CHARGE_DAMAGE_MULTIPLIER = 1.35F;

    private static final int CHARGE_DURATION_TICKS = 16;
    private static final int CHARGE_COOLDOWN_TICKS = 20 * 4;
    private static final double CHARGE_SPEED = 0.88D;
    private static final double CHARGE_HIT_INFLATE = 0.55D;
    public static final double DEMOLITION_COMMAND_SEARCH_RADIUS = 24.0D;
    public static final double DEMOLITION_MAX_TARGET_DISTANCE = 32.0D;
    private static final int MAX_DEMOLITION_ATTEMPTS = 3;

    private int chargeTicks;
    private int chargeCooldown;
    private Vec3 chargeDirection = Vec3.ZERO;
    private final Set<Integer> chargeHitIds = new HashSet<>();
    private BlockPos demolitionTarget;
    private boolean demolitionCharge;
    private int demolitionAttempts;

    private static final class BrainHolder {
        private static final Brain.Provider<DireWolf> PROVIDER = Brain.<DireWolf>provider(
                ImmutableList.of(ModSensorTypes.DIRE_PACK.get(), ModSensorTypes.DIRE_PREY.get()),
                wolf -> List.of());
    }

    public DireWolf(EntityType<? extends DireWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.DIRE_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Shared Creeper retreat remains priority 0. Dire pups retreat before
        // ordinary movement; adults may use their species charge below Creeper attack priority.
        this.goalSelector.addGoal(1, new DirePupRetreatGoal(this));
        this.goalSelector.addGoal(1, new DireDemolitionGoal(this));
        this.goalSelector.addGoal(2, new DireChargeAttackGoal(this));
        this.goalSelector.addGoal(5, new DirePupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new DirePackCohesionGoal(this));
        this.goalSelector.addGoal(7, new DirePupPlayGoal(this));

        this.targetSelector.addGoal(3, new DirePackAssistGoal(this));
        this.targetSelector.addGoal(6, new DireHuntGoal(this));
    }

    @Override
    protected Brain<DireWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<DireWolf> getBrain() {
        return (Brain<DireWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.tickHuntCooldown();
        this.getBrain().tick(level, this);

        if (this.hasDemolitionTarget()) {
            // Keep an accepted owner order from being silently stolen one tick
            // later by ordinary prey/pack target selection. Taking actual damage
            // still calls alertPackToThreat(), which cancels demolition first.
            this.setTarget(null);
            this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_PACK_THREAT.get());
            this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_HUNT_TARGET.get());
        } else if (this.isBaby() && this.getTarget() != null) {
            this.setTarget(null);
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.chargeCooldown > 0) {
            --this.chargeCooldown;
        }
        if (this.chargeTicks > 0) {
            this.tickCharge();
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        return super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Charge / owner-commanded demolition
    // ---------------------------------------------------------------------


    public boolean canStartCharge() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && this.onGround()
                && this.chargeCooldown <= 0
                && !this.isCharging();
    }

    public boolean isCharging() {
        return this.chargeTicks > 0;
    }

    public void beginAiCharge(LivingEntity target) {
        if (target == null || !target.isAlive() || target instanceof Creeper || !this.canStartCharge()) {
            return;
        }

        Vec3 direction = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        this.beginCharge(direction);
    }

    public boolean beginDemolitionCharge(BlockPos target) {
        if (!this.isDemolitionTargetValid(target)) {
            this.cancelDemolitionCommand();
            return false;
        }

        Vec3 center = Vec3.atCenterOf(target);
        Vec3 direction = new Vec3(center.x - this.getX(), 0.0D, center.z - this.getZ());
        if (!this.beginCharge(direction)) {
            return false;
        }

        this.demolitionCharge = true;
        return true;
    }

    private boolean beginCharge(Vec3 direction) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-5D || !this.canStartCharge()) {
            return false;
        }

        this.chargeDirection = flat.normalize();
        this.chargeTicks = CHARGE_DURATION_TICKS;
        this.chargeCooldown = CHARGE_COOLDOWN_TICKS;
        this.chargeHitIds.clear();
        this.getNavigation().stop();
        this.setSprinting(true);
        this.faceChargeDirection();
        return true;
    }

    private void tickCharge() {
        this.faceChargeDirection();

        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.demolitionCharge && this.tryBreakCommandedBlock(serverLevel)) {
                this.completeDemolitionCommand();
                this.endCharge();
                return;
            }
            this.hitChargeTargets(serverLevel);
        }

        if (this.horizontalCollision) {
            if (this.demolitionCharge) {
                this.recordDemolitionFailure();
            }
            this.endCharge();
            return;
        }

        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                this.chargeDirection.x * CHARGE_SPEED,
                current.y,
                this.chargeDirection.z * CHARGE_SPEED);
        this.hurtMarked = true;

        --this.chargeTicks;
        if (this.chargeTicks <= 0) {
            if (this.demolitionCharge) {
                this.recordDemolitionFailure();
            }
            this.endCharge();
        }
    }

    private void endCharge() {
        this.chargeTicks = 0;
        this.chargeDirection = Vec3.ZERO;
        this.chargeHitIds.clear();
        this.demolitionCharge = false;
        this.setSprinting(false);
    }

    private void faceChargeDirection() {
        if (this.chargeDirection.lengthSqr() < 1.0E-5D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-this.chargeDirection.x, this.chargeDirection.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    public boolean canAcceptDemolitionCommand(Player owner, BlockPos target) {
        if (owner == null
                || !this.isTame()
                || this.isBaby()
                || !this.isOwnedBy(owner)
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isCharging()
                || this.distanceToSqr(
                                target.getX() + 0.5D,
                                target.getY() + 0.5D,
                                target.getZ() + 0.5D)
                        > DEMOLITION_MAX_TARGET_DISTANCE * DEMOLITION_MAX_TARGET_DISTANCE) {
            return false;
        }

        return this.isDemolitionTargetValid(target);
    }

    public boolean commandDemolition(Player owner, BlockPos target) {
        if (!this.canAcceptDemolitionCommand(owner, target)) {
            return false;
        }

        // An explicit owner order overrides ordinary prey/hunt state. A real
        // danger will cancel demolition again through the normal pack hooks.
        this.setTarget(null);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_PACK_THREAT.get());
        this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_HUNT_TARGET.get());

        this.demolitionTarget = target.immutable();
        this.demolitionAttempts = 0;
        this.getNavigation().stop();
        return true;
    }

    public boolean hasDemolitionTarget() {
        return this.demolitionTarget != null;
    }

    public BlockPos getDemolitionTarget() {
        return this.demolitionTarget;
    }

    public boolean isDemolitionTargetValid(BlockPos target) {
        if (target == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }

        return isDemolitionBreakableState(serverLevel.getBlockState(target));
    }

    /**
     * The Wolfism tag remains the extension point for modded blocks. Vanilla
     * weak obstacles also have a hard fallback so a stale generated tag cannot
     * silently make the command unusable during development.
     */
    public static boolean isDemolitionBreakableState(BlockState state) {
        return !state.isAir()
                && (state.is(ModBlockTags.DIRE_CHARGE_BREAKABLE)
                        || state.is(BlockTags.LEAVES)
                        || state.is(BlockTags.DOORS)
                        || state.is(BlockTags.TRAPDOORS)
                        || state.is(BlockTags.FENCES)
                        || state.is(Blocks.COBWEB)
                        || state.is(Blocks.GLASS)
                        || state.is(Blocks.GLASS_PANE)
                        || state.is(Blocks.WHITE_STAINED_GLASS)
                        || state.is(Blocks.ORANGE_STAINED_GLASS)
                        || state.is(Blocks.MAGENTA_STAINED_GLASS)
                        || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                        || state.is(Blocks.YELLOW_STAINED_GLASS)
                        || state.is(Blocks.LIME_STAINED_GLASS)
                        || state.is(Blocks.PINK_STAINED_GLASS)
                        || state.is(Blocks.GRAY_STAINED_GLASS)
                        || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS)
                        || state.is(Blocks.CYAN_STAINED_GLASS)
                        || state.is(Blocks.PURPLE_STAINED_GLASS)
                        || state.is(Blocks.BLUE_STAINED_GLASS)
                        || state.is(Blocks.BROWN_STAINED_GLASS)
                        || state.is(Blocks.GREEN_STAINED_GLASS)
                        || state.is(Blocks.RED_STAINED_GLASS)
                        || state.is(Blocks.BLACK_STAINED_GLASS)
                        || state.is(Blocks.WHITE_STAINED_GLASS_PANE)
                        || state.is(Blocks.ORANGE_STAINED_GLASS_PANE)
                        || state.is(Blocks.MAGENTA_STAINED_GLASS_PANE)
                        || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)
                        || state.is(Blocks.YELLOW_STAINED_GLASS_PANE)
                        || state.is(Blocks.LIME_STAINED_GLASS_PANE)
                        || state.is(Blocks.PINK_STAINED_GLASS_PANE)
                        || state.is(Blocks.GRAY_STAINED_GLASS_PANE)
                        || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE)
                        || state.is(Blocks.CYAN_STAINED_GLASS_PANE)
                        || state.is(Blocks.PURPLE_STAINED_GLASS_PANE)
                        || state.is(Blocks.BLUE_STAINED_GLASS_PANE)
                        || state.is(Blocks.BROWN_STAINED_GLASS_PANE)
                        || state.is(Blocks.GREEN_STAINED_GLASS_PANE)
                        || state.is(Blocks.RED_STAINED_GLASS_PANE)
                        || state.is(Blocks.BLACK_STAINED_GLASS_PANE));
    }

    public void cancelDemolitionCommand() {
        this.demolitionTarget = null;
        this.demolitionAttempts = 0;
        this.demolitionCharge = false;
    }

    private void completeDemolitionCommand() {
        this.demolitionTarget = null;
        this.demolitionAttempts = 0;
    }

    private void recordDemolitionFailure() {
        ++this.demolitionAttempts;
        if (this.demolitionAttempts >= MAX_DEMOLITION_ATTEMPTS || !this.isDemolitionTargetValid(this.demolitionTarget)) {
            this.cancelDemolitionCommand();
        } else {
            // A failed line-up should retry quickly instead of waiting the full
            // combat charge cooldown. The goal will reposition before trying again.
            this.chargeCooldown = Math.min(this.chargeCooldown, 30);
        }
    }

    private boolean tryBreakCommandedBlock(ServerLevel level) {
        if (!this.isDemolitionTargetValid(this.demolitionTarget)) {
            this.cancelDemolitionCommand();
            return false;
        }

        BlockPos target = this.demolitionTarget;
        Vec3 center = Vec3.atCenterOf(target);

        // Do not depend on a one-tick AABB intersection. The Dire has reached
        // impact range once its large body is close to the exact commanded block.
        // This keeps the command precise while making high-speed charges reliable.
        double dx = center.x - this.getX();
        double dz = center.z - this.getZ();
        double horizontalDistanceSqr = dx * dx + dz * dz;
        double verticalDelta = Math.abs(center.y - (this.getY() + this.getBbHeight() * 0.5D));
        if (horizontalDistanceSqr > 2.35D * 2.35D || verticalDelta > 1.75D) {
            return false;
        }

        if (level.destroyBlock(target, false, this)) {
            return true;
        }

        // No-drop fallback for a valid weak target. This is intentionally only
        // reached after destroyBlock reports failure.
        BlockState remaining = level.getBlockState(target);
        return isDemolitionBreakableState(remaining) && level.removeBlock(target, false);
    }

    private void hitChargeTargets(ServerLevel level) {
        AABB hitBox = this.getBoundingBox().inflate(CHARGE_HIT_INFLATE, 0.25D, CHARGE_HIT_INFLATE);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                hitBox,
                candidate -> candidate != this && candidate.isAlive() && this.canChargeHit(candidate))) {
            if (!this.chargeHitIds.add(target.getId())) {
                continue;
            }

            if (this.doHurtTarget(target)) {
                double dx = this.getX() - target.getX();
                double dz = this.getZ() - target.getZ();
                target.knockback(1.65D, dx, dz);
            }
        }
    }

    private boolean canChargeHit(LivingEntity candidate) {
        // Creepers always use Wolfism's validated bait/retreat/turn-switching
        // protocol. A Dire must never bypass that safety layer with a body charge.
        if (candidate instanceof Creeper
                || this.isAlliedTo(candidate)
                || !this.canAttack(candidate)) {
            return false;
        }

        LivingEntity currentTarget = this.getTarget();
        if (currentTarget == candidate) {
            return true;
        }

        // A physical charge may clip another hostile mob in the same lane, but
        // it does not become a livestock/villager bulldozer.
        return candidate instanceof Enemy;
    }

    // ---------------------------------------------------------------------
    // Pack / prey intelligence
    // ---------------------------------------------------------------------

    public boolean isDirePackmate(DireWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isValidDireCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public static boolean isPreferredPrey(LivingEntity target) {
        EntityType<?> type = target.getType();
        return type == EntityType.SHEEP
                || type == EntityType.PIG
                || type == EntityType.COW
                || type == EntityType.GOAT;
    }

    public boolean hasEnoughHuntersFor(LivingEntity prey) {
        // A Dire is individually capable of tackling the large natural prey in
        // its roster; nearby packmates still converge on the same hunt target.
        return !this.isBaby();
    }

    public boolean isHuntCoolingDown() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get())
                .orElse(0) > 0;
    }

    private void tickHuntCooldown() {
        int cooldown = this.getBrain()
                .getMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get())
                .orElse(0);
        if (cooldown > 1) {
            this.getBrain().setMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get(), cooldown - 1);
        } else if (cooldown == 1) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get());
        }
    }

    public void beginPackHuntCooldown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
        this.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_HUNT_TARGET.get());

        List<DireWolf> packmates = serverLevel.getEntitiesOfClass(
                DireWolf.class,
                this.getBoundingBox().inflate(DireWolfPackSensor.PACK_SCAN_RADIUS),
                candidate -> candidate.isAlive() && this.isDirePackmate(candidate));
        for (DireWolf packmate : packmates) {
            packmate.getBrain().setMemory(ModMemoryModuleTypes.DIRE_HUNT_COOLDOWN.get(), HUNT_COOLDOWN_TICKS);
            packmate.getBrain().eraseMemory(ModMemoryModuleTypes.DIRE_HUNT_TARGET.get());
        }
    }


    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        this.cancelDemolitionCommand();
        if (this.isCharging()) {
            this.endCharge();
        }
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

        double radius = pupEmergency ? 36.0D : DireWolfPackSensor.PACK_SCAN_RADIUS;
        this.cancelDemolitionCommand();
        this.getBrain().setMemory(ModMemoryModuleTypes.DIRE_PACK_THREAT.get(), threat);

        List<DireWolf> packmates = serverLevel.getEntitiesOfClass(
                DireWolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive() && this.isDirePackmate(candidate));

        for (DireWolf packmate : packmates) {
            packmate.cancelDemolitionCommand();
            packmate.getBrain().setMemory(ModMemoryModuleTypes.DIRE_PACK_THREAT.get(), threat);
            if (!packmate.isBaby()
                    && !packmate.isOrderedToSit()
                    && packmate.isValidDireCombatTarget(threat)
                    && (packmate.getTarget() == null || isPreferredPrey(packmate.getTarget()))) {
                packmate.setTarget(threat);
            }
        }
    }

    public static boolean checkDireWolfSpawnRules(
            EntityType<DireWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}

