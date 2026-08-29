package net.ronm19.wolfism.entity;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
    public static final double FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE = 64.0D;

    /*
     * Shared portal-follow support.
     *
     * A wolf only arms this hand-off while it is actively following close to
     * its owner in the same dimension. Sitting/home wolves do not get dragged
     * through dimensions. If the owner changes dimensions during the grace
     * window, the wolf is moved to safe footing near the owner.
     */
    private static final double OWNER_PORTAL_FOLLOW_ARM_DISTANCE = 24.0D;
    private static final int OWNER_PORTAL_FOLLOW_GRACE_TICKS = 20 * 4;

    private int ownerPortalFollowGraceTicks;

    private LivingEntity familyDefenseTarget;
    private LivingEntity protectedFamilyMember;
    private int familyDefenseTicks;
    private boolean resumeOrderedSitAfterFamilyDefense;

    protected AbstractWolfismWolf( EntityType<? extends AbstractWolfismWolf> type, Level level ) {
        super(type, level);
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

        if (this.hasFamilyDefenseEmergency()) {
            this.releaseSittingForFamilyDefense();
        }

        if (!this.canParticipateInWolfismCombat()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }

            if (this.hasFamilyDefenseEmergency()) {
                this.clearFamilyDefense();
            }
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

    public final boolean canParticipateInWolfismCombat() {
        return !this.isBaby() || this.allowsBabyCombat();
    }

    protected final boolean canUseAdultWolfismAbility() {
        return !this.isBaby();
    }

    protected boolean canUseActiveWolfismAbility() {
        return this.canUseAdultWolfismAbility()
                && !this.isOrderedToSit()
                && !this.isInSittingPose();
    }

    @Override
    public void setTarget( LivingEntity target ) {
        if (target != null && !this.canParticipateInWolfismCombat()) {
            super.setTarget(null);
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

    public boolean beginFamilyDefense( LivingEntity attacker, LivingEntity protectedFamily ) {
        if (!this.canParticipateInWolfismCombat()
                || !this.isTame()
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

        this.releaseSittingForFamilyDefense();

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

    private void releaseSittingForFamilyDefense() {
        if (this.isOrderedToSit()) {
            this.resumeOrderedSitAfterFamilyDefense = true;
            this.setOrderedToSit(false);
        }

        // Important: ordered sitting and the rendered/physical sitting pose are
        // separate states. Emergency movement must clear both.
        if (this.isInSittingPose()) {
            this.setInSittingPose(false);
        }
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

        if (this.resumeOrderedSitAfterFamilyDefense
                && this.isTame()
                && this.isAlive()) {
            this.setOrderedToSit(true);
        }

        this.resumeOrderedSitAfterFamilyDefense = false;
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
