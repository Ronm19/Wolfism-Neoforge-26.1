package net.ronm19.wolfism.entity;

import net.ronm19.wolfism.creator.CreatorProgress;
import java.util.Set;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperPackAttackGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismFamilyDefenseGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismFamilyPupEmergencyGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperTargetGoal;

/**
 * Common foundation for Wolfism's normal wolf-shaped species.
 *
 * <p>The class deliberately builds on vanilla {@link Wolf} so every species gets
 * vanilla wolf movement, taming, sitting, following, wolf armor, wet/shake
 * behavior, sounds and combat behavior as a stable baseline. Species-specific
 * rendering belongs in each species' client renderer instead of this common
 * entity class.</p>
 *
 * <p>Wolfism pups are non-combatants by default. They may still flee from danger,
 * follow family, and participate in dedicated pup-safety behavior, but they do
 * not acquire combat targets, retaliate for family, or deal melee damage unless
 * a species deliberately opts into baby combat.</p>
 */
public abstract class AbstractWolfismWolf extends Wolf {
    public static final int FAMILY_DEFENSE_DURATION_TICKS = 20 * 12;

    public static final double DEFAULT_PHYSICAL_AGGRO_ACQUIRE_RADIUS = 12.0D;
    public static final double DEFAULT_PHYSICAL_AGGRO_RELEASE_RADIUS = 18.0D;

    public static final double FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE =
            DEFAULT_PHYSICAL_AGGRO_RELEASE_RADIUS;

    private static final double OWNER_PORTAL_FOLLOW_ARM_DISTANCE = 24.0D;
    private static final int OWNER_PORTAL_FOLLOW_GRACE_TICKS = 20 * 4;

    private int ownerPortalFollowGraceTicks;

    // ---------------------------------------------------------------------
    // Wolf Staff shared command state
    // ---------------------------------------------------------------------
    private boolean wolfStaffCommanded;
    private WolfStaffCommandMode wolfStaffCommandMode = WolfStaffCommandMode.FOLLOW;
    private LivingEntity wolfStaffFocusedTarget;
    private int wolfStaffFocusedTargetTicks;
    private int wolfStaffRecallTicks;
    private int wolfStaffCommandScanCooldown;

    private LivingEntity familyDefenseTarget;
    private LivingEntity protectedFamilyMember;
    private int familyDefenseTicks;

    protected AbstractWolfismWolf( EntityType<? extends AbstractWolfismWolf> type, Level level ) {
        super(type, level);
    }

    /**
     * Global Wolfism family welcome.
     *
     * <p>Every Wolfism wolf that inherits this base gets the same successful
     * player-taming welcome automatically. Species classes do not need to
     * duplicate this message. The pre-tame guard also prevents repeated
     * welcomes if tame(...) is ever invoked on an already-tamed wolf.</p>
     */
    @Override
    public void tame(Player player) {
        boolean wasAlreadyTame = this.isTame();
        super.tame(player);

        if (wasAlreadyTame || !this.isTame() || !(this.level() instanceof ServerLevel level)) {
            return;
        }

        level.sendParticles(
                ParticleTypes.HEART,
                this.getX(), this.getY(0.72D), this.getZ(),
                10, 0.55D, 0.45D, 0.55D, 0.035D);
        level.sendParticles(
                ParticleTypes.POOF,
                this.getX(), this.getY(0.45D), this.getZ(),
                12, 0.65D, 0.18D, 0.65D, 0.025D);

        player.sendOverlayMessage(
                Component.translatable(
                        "message.wolfism.joined_family",
                        this.getDisplayName()));

        if (player instanceof ServerPlayer serverPlayer) {
            CreatorProgress.onWolfTamed(serverPlayer, this);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(0, new WolfismCreeperRetreatGoal(this));
        this.goalSelector.addGoal(1, new WolfismFamilyPupEmergencyGoal(this));
        this.goalSelector.addGoal(1, new WolfismCreeperPackAttackGoal(this));
        this.goalSelector.removeAllGoals(goal -> goal instanceof BreedGoal);
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0D, AbstractWolfismWolf.class));

        this.targetSelector.addGoal(0, new WolfismFamilyDefenseGoal(this));
        this.targetSelector.addGoal(4, new WolfismCreeperTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        /*
         * Do this before species AI. Cross-dimension teleport creates the new
         * entity in the destination level and removes this old instance, so no
         * more AI should run on the old instance that tick.
         */
        if (this.tickSharedOwnerPortalFollow()) {
            return;
        }

        this.tickWolfStaffCommand();

        /*
         * OWNER SIT COMMAND IS ABSOLUTE.
         *
         * Earlier revisions temporarily cleared ordered sitting during family
         * defense and later restored it. That created a delayed command bug:
         * after the player had resumed control, clearFamilyDefense() could
         * unexpectedly setOrderedToSit(true) again.
         *
         * Sitting wolves now remain seated and are not recruited into family
         * emergency movement/combat. If the owner sits a wolf while a family
         * defense is already active, that defense is cancelled immediately.
         */
        if ((this.isOrderedToSit() || this.isInSittingPose())
                && this.hasFamilyDefenseEmergency()) {
            this.clearFamilyDefense();
        }

        if (!this.canParticipateInWolfismCombat()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }

            if (this.hasFamilyDefenseEmergency()) {
                this.clearFamilyDefense();
            }
        } else {
            this.enforceSharedPhysicalAggroDiscipline();
        }

        if (this.level() instanceof ServerLevel && this.familyDefenseTicks > 0) {
            --this.familyDefenseTicks;

            LivingEntity threat = this.familyDefenseTarget;
            if (threat == null
                    || !threat.isAlive()
                    || this.isAlliedTo(threat)
                    || this.distanceToSqr(threat)
                    > FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE * FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE
                    || this.familyDefenseTicks <= 0) {
                this.clearFamilyDefense();
            }
        }
    }


    /**
     * Shared Wolfism portal hand-off.
     *
     * <p>This is deliberately proximity-gated. A wolf sitting at home should
     * not teleport across dimensions just because its owner used a portal on
     * the other side of the world. A non-sitting tamed Wolfism wolf that was
     * within 24 blocks shortly before the owner's dimension change will follow
     * automatically.</p>
     *
     * @return true when this old entity instance successfully teleported out
     * of its current dimension and should stop ticking.
     */
    private boolean tickSharedOwnerPortalFollow() {
        if (!(this.level() instanceof ServerLevel)
                || !this.isTame()
                || !this.isAlive()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isPassenger()) {
            this.ownerPortalFollowGraceTicks = 0;
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)
                || !serverPlayer.isAlive()) {
            if (this.ownerPortalFollowGraceTicks > 0) {
                --this.ownerPortalFollowGraceTicks;
            }
            return false;
        }

        if (owner.level() == this.level()) {
            if (this.distanceToSqr(owner)
                    <= OWNER_PORTAL_FOLLOW_ARM_DISTANCE
                    * OWNER_PORTAL_FOLLOW_ARM_DISTANCE) {
                this.ownerPortalFollowGraceTicks =
                        OWNER_PORTAL_FOLLOW_GRACE_TICKS;
            } else {
                this.ownerPortalFollowGraceTicks = 0;
            }

            return false;
        }

        if (this.ownerPortalFollowGraceTicks <= 0
                || !(owner.level() instanceof ServerLevel destinationLevel)) {
            return false;
        }

        BlockPos destination =
                this.findSharedPortalFollowDestination(
                        destinationLevel,
                        serverPlayer.blockPosition());

        if (destination == null) {
            --this.ownerPortalFollowGraceTicks;
            return false;
        }

        this.setTarget(null);
        this.getNavigation().stop();

        boolean teleported = this.teleportTo(
                destinationLevel,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                Set.<Relative>of(),
                this.getYRot(),
                this.getXRot(),
                false);

        this.ownerPortalFollowGraceTicks = 0;
        return teleported;
    }

    private BlockPos findSharedPortalFollowDestination(
            ServerLevel level,
            BlockPos ownerPos ) {

        /*
         * Random attempts first so an entire Wolfism army does not materialize
         * on the exact same portal-exit block.
         */
        for (int attempt = 0; attempt < 24; ++attempt) {
            int dx = this.random.nextIntBetweenInclusive(-6, 6);
            int dz = this.random.nextIntBetweenInclusive(-6, 6);
            int dy = this.random.nextIntBetweenInclusive(-1, 2);

            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }

            BlockPos feet = ownerPos.offset(dx, dy, dz);
            if (this.isSafeSharedPortalFollowPosition(level, feet)) {
                return feet.immutable();
            }
        }

        // Deterministic fallback if the portal exit is cramped.
        for (int radius = 1; radius <= 5; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -1; dy <= 2; ++dy) {
                        BlockPos feet = ownerPos.offset(dx, dy, dz);

                        if (this.isSafeSharedPortalFollowPosition(
                                level,
                                feet)) {
                            return feet.immutable();
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean isSafeSharedPortalFollowPosition(
            ServerLevel level,
            BlockPos feet ) {

        BlockPos head = feet.above();
        BlockPos groundPos = feet.below();

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState groundState = level.getBlockState(groundPos);

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }

        return groundState.isFaceSturdy(
                level,
                groundPos,
                Direction.UP);
    }

    protected boolean allowsBabyCombat() {
        return false;
    }

    /**
     * Physical aggro acquisition radius for this species.
     *
     * <p>Override only when a species genuinely needs a different LOCAL combat
     * envelope. This value should not be inflated merely because the species
     * has long-range awareness.</p>
     */
    public double getWolfismPhysicalAggroAcquireRadius() {
        return DEFAULT_PHYSICAL_AGGRO_ACQUIRE_RADIUS;
    }

    /**
     * Hysteresis radius that lets a wolf finish a nearby engagement without
     * chasing the target indefinitely.
     */
    public double getWolfismPhysicalAggroReleaseRadius() {
        return DEFAULT_PHYSICAL_AGGRO_RELEASE_RADIUS;
    }

    /**
     * RANGED-SPECIALIST HOOK.
     *
     * <p>Default false. A species whose ranged ability genuinely needs
     * {@code getTarget()} at long distance may override this and return true
     * for an appropriate target. The shared base will retain the target but
     * STOP physical navigation while it remains outside local aggro range.</p>
     *
     * <p>Prefer ability-specific Brain memories/candidates when possible. They
     * are cleaner than using the mob's physical target slot for ranged-only
     * awareness.</p>
     */
    protected boolean canRetainDistantWolfismTargetForRangedAbility(
            LivingEntity target ) {
        return false;
    }

    public final boolean isWithinWolfismPhysicalAggroAcquireRange(
            LivingEntity target ) {
        if (target == null) return false;

        double radius = this.getWolfismPhysicalAggroAcquireRadius();
        return this.distanceToSqr(target) <= radius * radius;
    }

    public final boolean isWithinWolfismPhysicalAggroReleaseRange(
            LivingEntity target ) {
        if (target == null) return false;

        double radius = Math.max(
                this.getWolfismPhysicalAggroAcquireRadius(),
                this.getWolfismPhysicalAggroReleaseRadius());

        return this.distanceToSqr(target) <= radius * radius;
    }

    private boolean isAllowedDistantRangedTarget( LivingEntity target ) {
        return target != null
                && this.canParticipateInWolfismCombat()
                && this.canRetainDistantWolfismTargetForRangedAbility(target);
    }

    /**
     * Shared "awareness != aggression" enforcement for all Wolfism wolves.
     */
    private void enforceSharedPhysicalAggroDiscipline() {
        LivingEntity target = this.getTarget();
        if (target == null) return;

        if (this.isWolfStaffAttackTarget(target)
                || this.isWolfStaffGuardThreat(target)) {
            return;
        }

        if (this.isWithinWolfismPhysicalAggroReleaseRange(target)) {
            return;
        }

        if (this.isAllowedDistantRangedTarget(target)) {
            // Keep the ranged ability's target lock, but do not let ordinary
            // melee/navigation goals turn it into a cross-biome chase.
            this.getNavigation().stop();
            this.setSprinting(false);
            return;
        }

        super.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);
    }

    public final boolean canParticipateInWolfismCombat() {
        return !this.isBaby() || this.allowsBabyCombat();
    }

    protected final boolean canUseAdultWolfismAbility() {
        return !this.isBaby();
    }

    protected boolean canUseActiveWolfismAbility() {
        if (!this.canUseAdultWolfismAbility()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.wolfStaffRecallTicks > 0) {
            return false;
        }

        // An explicit Staff FOLLOW command means exactly that: disengage and
        // travel with the owner instead of independently starting new fights.
        return !this.wolfStaffCommanded
                || this.wolfStaffCommandMode != WolfStaffCommandMode.FOLLOW;
    }

    @Override
    public void setTarget( LivingEntity target ) {
        if (target != null && this.wolfStaffCommanded) {
            if (this.wolfStaffRecallTicks > 0
                    || this.wolfStaffCommandMode == WolfStaffCommandMode.FOLLOW
                    || this.wolfStaffCommandMode == WolfStaffCommandMode.SIT) {
                super.setTarget(null);
                return;
            }

            if (this.wolfStaffFocusedTarget != null
                    && this.wolfStaffFocusedTarget.isAlive()
                    && target != this.wolfStaffFocusedTarget) {
                return;
            }

            if (this.wolfStaffCommandMode == WolfStaffCommandMode.GUARD
                    && !this.isWolfStaffGuardThreat(target)) {
                super.setTarget(null);
                return;
            }
        }

        if (target != null && !this.canParticipateInWolfismCombat()) {
            super.setTarget(null);
            return;
        }

        if (target != null
                && !this.isWithinWolfismPhysicalAggroAcquireRange(target)
                && !this.isAllowedDistantRangedTarget(target)
                && !this.isWolfStaffAttackTarget(target)
                && !this.isWolfStaffGuardThreat(target)) {
            /*
             * Sensors/Brain code may still know about this entity. We are only
             * refusing to promote a distant observation into the mob's normal
             * physical combat target slot.
             */
            super.setTarget(null);
            this.getNavigation().stop();
            return;
        }

        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget( ServerLevel level, Entity target ) {
        if (!this.canParticipateInWolfismCombat()) {
            this.setTarget(null);
            return false;
        }

        /*
         * Compatibility dispatch.
         *
         * Minecraft 26.1 calls doHurtTarget(ServerLevel, Entity),
         * but several existing Wolfism wolves still override the older
         * doHurtTarget(Entity) hook.
         *
         * Calling through here preserves those existing wolf implementations
         * without forcing all of them to be rewritten immediately.
         */
        return this.doHurtTarget(target);
    }

    /**
     * Wolfism compatibility melee hook.
     * <p>
     * Existing wolf classes that override doHurtTarget(Entity) can remain intact.
     * New 26.1 implementations may instead override
     * doHurtTarget(ServerLevel, Entity).
     */
    public boolean doHurtTarget( Entity target ) {
        if (!this.canParticipateInWolfismCombat()) {
            this.setTarget(null);
            return false;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        return super.doHurtTarget(serverLevel, target);
    }



    // =====================================================================
    // Wolf Staff universal command hook
    // =====================================================================

    public final boolean hasWolfStaffCommand() {
        return this.wolfStaffCommanded;
    }

    public final WolfStaffCommandMode getWolfStaffCommandMode() {
        return this.wolfStaffCommandMode;
    }

    public final boolean isWolfStaffRecallActive() {
        return this.wolfStaffRecallTicks > 0;
    }

    public final WolfStaffCombatRole getWolfStaffCombatRole() {
        return WolfStaffCombatRole.classify(this);
    }

    public final void applyWolfStaffCommand(WolfStaffCommandMode mode) {
        if (!this.isTame() || mode == null) {
            return;
        }

        this.wolfStaffCommanded = true;
        this.wolfStaffCommandMode = mode;
        this.wolfStaffRecallTicks = 0;
        this.wolfStaffFocusedTarget = null;
        this.wolfStaffFocusedTargetTicks = 0;
        this.wolfStaffCommandScanCooldown = 0;

        switch (mode) {
            case SIT -> {
                this.setOrderedToSit(true);
                super.setTarget(null);
                this.getNavigation().stop();
                if (this.hasFamilyDefenseEmergency()) {
                    this.clearFamilyDefense();
                }
            }
            case FOLLOW -> {
                this.setOrderedToSit(false);
                super.setTarget(null);
                this.getNavigation().stop();
                if (this.hasFamilyDefenseEmergency()) {
                    this.clearFamilyDefense();
                }
            }
            case GUARD, ATTACK -> {
                this.setOrderedToSit(false);
                super.setTarget(null);
                this.getNavigation().stop();
            }
        }
    }

    /**
     * Focused Wolf Staff strike command. The target lock intentionally bypasses
     * the ordinary local-aggro envelope so flying/ranged responders can accept
     * a 20-30 block command without the shared base immediately deleting it.
     */
    public final void focusWolfStaffTarget(LivingEntity target, int durationTicks) {
        if (!this.isTame()
                || !this.canParticipateInWolfismCombat()
                || target == null
                || !target.isAlive()
                || this.isAlliedTo(target)) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && target == owner) {
            return;
        }

        this.wolfStaffCommanded = true;
        this.wolfStaffCommandMode = WolfStaffCommandMode.ATTACK;
        this.wolfStaffRecallTicks = 0;
        this.wolfStaffFocusedTarget = target;
        this.wolfStaffFocusedTargetTicks = Math.max(20, durationTicks);
        this.setOrderedToSit(false);
        this.getNavigation().stop();
        this.setTarget(target);
    }

    /**
     * Emergency Recall is deliberately stronger than FOLLOW: it clears current
     * combat, extinguishes fire, removes all temporary effects, and aggressively
     * returns the wolf to its owner. It also cancels species active abilities via
     * canUseActiveWolfismAbility() for the duration of the recall window.
     */
    public final void beginWolfStaffRecall() {
        if (!this.isTame() || !this.isAlive()) {
            return;
        }

        this.wolfStaffCommanded = true;
        this.wolfStaffCommandMode = WolfStaffCommandMode.FOLLOW;
        this.wolfStaffRecallTicks = 20 * 10;
        this.wolfStaffFocusedTarget = null;
        this.wolfStaffFocusedTargetTicks = 0;
        this.wolfStaffCommandScanCooldown = 0;

        this.setOrderedToSit(false);
        super.setTarget(null);
        this.getNavigation().stop();
        this.clearFire();
        this.removeAllEffects();

        if (this.hasFamilyDefenseEmergency()) {
            this.clearFamilyDefense();
        }
    }

    private void tickWolfStaffCommand() {
        if (!(this.level() instanceof ServerLevel level)
                || !this.wolfStaffCommanded
                || !this.isTame()
                || !this.isAlive()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            return;
        }

        if (this.wolfStaffRecallTicks > 0) {
            --this.wolfStaffRecallTicks;
            this.setOrderedToSit(false);
            super.setTarget(null);
            this.clearFire();

            // Recall is a reset command, not just a movement instruction.
            if (!this.getActiveEffects().isEmpty()) {
                this.removeAllEffects();
            }

            double distanceSqr = this.distanceToSqr(owner);
            if (distanceSqr > 28.0D * 28.0D) {
                BlockPos destination = this.findSharedPortalFollowDestination(
                        level,
                        owner.blockPosition());

                if (destination != null) {
                    this.teleportTo(
                            level,
                            destination.getX() + 0.5D,
                            destination.getY(),
                            destination.getZ() + 0.5D,
                            Set.<Relative>of(),
                            this.getYRot(),
                            this.getXRot(),
                            false);
                }
            } else if (distanceSqr > 3.0D * 3.0D) {
                this.getNavigation().moveTo(owner, 1.35D);
            } else {
                this.getNavigation().stop();
                this.wolfStaffRecallTicks = 0;
            }

            return;
        }

        if (this.wolfStaffFocusedTargetTicks > 0) {
            --this.wolfStaffFocusedTargetTicks;
        }

        if (this.wolfStaffFocusedTarget != null) {
            LivingEntity focused = this.wolfStaffFocusedTarget;
            boolean invalid = this.wolfStaffFocusedTargetTicks <= 0
                    || !focused.isAlive()
                    || focused.level() != this.level()
                    || this.isAlliedTo(focused)
                    || this.distanceToSqr(focused) > 48.0D * 48.0D;

            if (invalid) {
                if (this.getTarget() == focused) {
                    super.setTarget(null);
                }
                this.wolfStaffFocusedTarget = null;
                this.wolfStaffFocusedTargetTicks = 0;
            } else {
                this.setOrderedToSit(false);
                if (this.getTarget() != focused) {
                    this.setTarget(focused);
                }
                return;
            }
        }

        if (this.wolfStaffCommandScanCooldown > 0) {
            --this.wolfStaffCommandScanCooldown;
        }

        switch (this.wolfStaffCommandMode) {
            case SIT -> {
                this.setOrderedToSit(true);
                super.setTarget(null);
                this.getNavigation().stop();
            }
            case FOLLOW -> {
                this.setOrderedToSit(false);
                super.setTarget(null);

                if (this.distanceToSqr(owner) > 10.0D * 10.0D) {
                    this.getNavigation().moveTo(owner, 1.1D);
                }
            }
            case GUARD -> this.tickWolfStaffGuard(owner);
            case ATTACK -> this.tickWolfStaffFreeAttack(owner);
        }
    }

    private void tickWolfStaffGuard(LivingEntity owner) {
        this.setOrderedToSit(false);

        LivingEntity current = this.getTarget();
        if (current != null && !this.isWolfStaffGuardThreat(current)) {
            super.setTarget(null);
            current = null;
        }

        if (current == null && this.wolfStaffCommandScanCooldown <= 0) {
            this.wolfStaffCommandScanCooldown = 10;
            LivingEntity threat = this.findWolfStaffHostileAroundOwner(owner, 14.0D);
            if (threat != null) {
                this.setTarget(threat);
                current = threat;
            }
        }

        if (current == null && this.distanceToSqr(owner) > 8.0D * 8.0D) {
            this.getNavigation().moveTo(owner, 1.15D);
        }
    }

    private void tickWolfStaffFreeAttack(LivingEntity owner) {
        this.setOrderedToSit(false);

        LivingEntity current = this.getTarget();
        if (current != null
                && (!current.isAlive()
                || current.level() != this.level()
                || owner.distanceToSqr(current) > 28.0D * 28.0D
                || this.isAlliedTo(current))) {
            super.setTarget(null);
            current = null;
        }

        if (current == null && this.wolfStaffCommandScanCooldown <= 0) {
            this.wolfStaffCommandScanCooldown = 8;
            LivingEntity target = this.findWolfStaffHostileAroundOwner(owner, 24.0D);
            if (target != null) {
                this.setTarget(target);
            }
        }
    }

    private LivingEntity findWolfStaffHostileAroundOwner(LivingEntity owner, double radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : this.level().getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(radius),
                entity -> entity instanceof Enemy
                        && entity.isAlive()
                        && entity != owner
                        && entity != this
                        && !this.isAlliedTo(entity))) {

            double distance = this.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }

    private boolean isWolfStaffGuardThreat(LivingEntity target) {
        if (!this.wolfStaffCommanded
                || this.wolfStaffCommandMode != WolfStaffCommandMode.GUARD
                || target == null
                || !target.isAlive()
                || !(target instanceof Enemy)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return owner != null
                && owner.level() == this.level()
                && owner.distanceToSqr(target) <= 16.0D * 16.0D
                && !this.isAlliedTo(target);
    }

    private boolean isWolfStaffAttackTarget(LivingEntity target) {
        if (!this.wolfStaffCommanded
                || this.wolfStaffCommandMode != WolfStaffCommandMode.ATTACK
                || target == null
                || !target.isAlive()) {
            return false;
        }

        if (this.wolfStaffFocusedTarget != null) {
            return target == this.wolfStaffFocusedTarget;
        }

        LivingEntity owner = this.getOwner();
        return owner != null
                && owner.level() == this.level()
                && owner.distanceToSqr(target) <= 28.0D * 28.0D
                && !this.isAlliedTo(target);
    }



    public boolean beginFamilyDefense( LivingEntity attacker, LivingEntity protectedFamily ) {
        if (!this.canParticipateInWolfismCombat()
                || !this.isTame()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || attacker == null
                || protectedFamily == null
                || !attacker.isAlive()
                || attacker == this
                || attacker == protectedFamily
                || this.isAlliedTo(attacker)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null) {
            if (attacker == owner || !this.wantsToAttack(attacker, owner)) {
                return false;
            }
        }

        this.familyDefenseTarget = attacker;
        this.protectedFamilyMember = protectedFamily;
        this.familyDefenseTicks = FAMILY_DEFENSE_DURATION_TICKS;
        this.getNavigation().stop();

        this.onFamilyDefenseStarted(attacker, protectedFamily);
        this.setTarget(attacker);
        return true;
    }

    public boolean hasFamilyDefenseEmergency() {
        return this.familyDefenseTicks > 0
                && this.familyDefenseTarget != null
                && this.familyDefenseTarget.isAlive();
    }

    public LivingEntity getFamilyDefenseTarget() {
        return this.familyDefenseTarget;
    }

    public LivingEntity getProtectedFamilyMember() {
        return this.protectedFamilyMember;
    }

    public void clearFamilyDefense() {
        LivingEntity oldTarget = this.familyDefenseTarget;

        this.familyDefenseTarget = null;
        this.protectedFamilyMember = null;
        this.familyDefenseTicks = 0;

        if (this.getTarget() == oldTarget) {
            this.setTarget(null);
        }

        /*
         * Never manufacture an owner sit command here.
         *
         * Sitting state belongs exclusively to the player's actual command.
         * Family defense may end combat, but it must not resurrect an older
         * sit instruction several ticks later.
         */
    }

    protected void onFamilyDefenseStarted( LivingEntity attacker, LivingEntity protectedFamily ) {
    }

    protected abstract EntityType<? extends AbstractWolfismWolf> wolfismEntityType();

    @Override
    public boolean wantsToAttack( LivingEntity target, LivingEntity owner ) {
        if (!this.canParticipateInWolfismCombat()) {
            return false;
        }

        if (target instanceof Creeper) {
            return true;
        }

        if (this.isTame() && target instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }

        return super.wantsToAttack(target, owner);
    }

    @Override
    public boolean canAttack( LivingEntity target ) {
        if (!this.canParticipateInWolfismCombat()) {
            return false;
        }

        if (this.isTame() && target instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }

        return super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly( Entity other ) {
        if (this.isTame() && other instanceof Wolf wolf && wolf.isTame()) {
            return true;
        }

        return super.considersEntityAsAlly(other);
    }

    // =====================================================================
    // Shared command persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("WolfStaffCommanded", this.wolfStaffCommanded);
        output.putString("WolfStaffCommandMode", this.wolfStaffCommandMode.serializedName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.wolfStaffCommanded = input.getBooleanOr("WolfStaffCommanded", false);
        this.wolfStaffCommandMode = WolfStaffCommandMode.byName(
                input.getStringOr("WolfStaffCommandMode", WolfStaffCommandMode.FOLLOW.serializedName()));
        this.wolfStaffFocusedTarget = null;
        this.wolfStaffFocusedTargetTicks = 0;
        this.wolfStaffRecallTicks = 0;
        this.wolfStaffCommandScanCooldown = 0;
    }

    /**
     * Wolfism cross-species breeding rule.
     *
     * <p>Any two adult/tamed Wolfism wolves may breed with one another. The baby
     * is always one of the two parent species; hybrids are not created here.</p>
     *
     * <p>Examples:
     * Black + Omen -> 50% Black pup / 50% Omen pup
     * Angel + Demon -> 50% Angel pup / 50% Demon pup
     * Black + Black -> Black pup
     * </p>
     */
    @Override
    public boolean canMate( Animal partner ) {
        if (partner == this || !(partner instanceof AbstractWolfismWolf wolf)) {
            return false;
        }

        // Make the Wolfism contract explicit instead of filtering by EntityType.
        // This intentionally allows different Wolfism species to pair.
        return this.isTame()
                && wolf.isTame()
                && !this.isBaby()
                && !wolf.isBaby()
                && !this.isInSittingPose()
                && !wolf.isInSittingPose()
                && this.isInLove()
                && wolf.isInLove();
    }

    /**
     * Chooses which parent's species the pup inherits.
     *
     * <p>Kept as a separate protected hook so a future special wolf can override
     * inheritance without rewriting the entire breeding implementation.</p>
     */
    protected EntityType<? extends AbstractWolfismWolf> chooseWolfismOffspringType(
            AbstractWolfismWolf partner ) {
        if (partner.getType() == this.getType()) {
            return this.wolfismEntityType();
        }

        return this.random.nextBoolean()
                ? this.wolfismEntityType()
                : partner.wolfismEntityType();
    }

    @Override
    public AbstractWolfismWolf getBreedOffspring( ServerLevel level, AgeableMob partner ) {
        if (!(partner instanceof AbstractWolfismWolf wolf)) {
            return null;
        }

        EntityType<? extends AbstractWolfismWolf> offspringType =
                this.chooseWolfismOffspringType(wolf);

        AbstractWolfismWolf baby =
                offspringType.create(level, EntitySpawnReason.BREEDING);

        if (baby != null && this.isTame()) {
            // Preserve the existing Wolfism ownership rule.
            baby.setOwnerReference(this.getOwnerReference());
            baby.setTame(true, true);

            // Keep vanilla-style mixed collar inheritance even across species.
            baby.setComponent(
                    DataComponents.WOLF_COLLAR,
                    DyeColor.getMixedColor(
                            level,
                            this.getCollarColor(),
                            wolf.getCollarColor()));

            baby.setComponent(
                    DataComponents.WOLF_SOUND_VARIANT,
                    WolfSoundVariants.pickRandomSoundVariant(
                            this.registryAccess(),
                            this.random));
        }

        return baby;
    }

}
