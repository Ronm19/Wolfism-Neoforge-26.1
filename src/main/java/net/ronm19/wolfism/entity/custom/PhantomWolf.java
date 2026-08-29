package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.PhantomWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import org.jspecify.annotations.Nullable;

/**
 * Wolfism's Phantom Wolf.
 *
 * <p>The flight motor, body control, circling mathematics, flap cadence and
 * circle -> swoop combat rhythm are deliberately based on vanilla Phantom.
 * Wolfism-specific rules remain authoritative: this is still a tameable Wolf,
 * pups are non-combatants, tamed wolf family is protected, family-defense can
 * replace an ordinary target, and Creepers continue to use Wolfism's validated
 * Creeper tactics rather than the ordinary Phantom swoop.</p>
 */
public final class PhantomWolf extends AbstractWolfismWolf {
    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);

    private static final double OWNER_FOLLOW_START_DISTANCE_SQR = 6.0D * 6.0D;
    private static final double OWNER_FOLLOW_STOP_DISTANCE_SQR = 3.25D * 3.25D;
    private static final double OWNER_CIRCLE_MAX_DISTANCE_SQR = 14.0D * 14.0D;

    // Companion flight should feel like a flying wolf beside its owner, not a
    // Phantom permanently hovering several blocks over the player's head.
    private static final double OWNER_IDLE_ORBIT_MIN_RADIUS = 3.25D;
    private static final double OWNER_IDLE_ORBIT_MAX_RADIUS = 5.75D;

    // Combat posture is now deliberately aerial for EVERY legitimate hostile.
    // Once a hostile is either targeted or detected by Eye in the Sky, Phantom
    // maintains roughly 10-15 blocks of vertical advantage over that hostile.
    // The owner/family never trigger this altitude posture.
    // Keep the requested ~10-15 block aerial advantage, but do not force
    // Phantom to waste half the fight rebuilding excessive altitude.
    private static final double COMBAT_THREAT_CLEARANCE = 10.5D;
    private static final double COMBAT_MIN_SWOOP_START_CLEARANCE = 7.0D;
    private static final double COMBAT_RECOVERY_CLEARANCE = 10.5D;
    private static final double COMBAT_RECOVERY_HORIZONTAL_DISTANCE = 4.0D;
    private static final double COMBAT_ORBIT_MIN_RADIUS = 5.5D;
    private static final double COMBAT_ORBIT_MAX_RADIUS = 8.0D;

    // Combat rhythm: fast enough to feel like an elite aerial attacker without
    // becoming a no-counterplay blender.
    private static final int INITIAL_SWOOP_DELAY_TICKS = 6;
    private static final int NORMAL_SWOOP_DELAY_MIN_TICKS = 28;
    private static final int NORMAL_SWOOP_DELAY_RANDOM_TICKS = 17;
    private static final int SKY_HUNTER_SWOOP_DELAY_MIN_TICKS = 16;
    private static final int SKY_HUNTER_SWOOP_DELAY_RANDOM_TICKS = 11;
    private static final int MAX_SWOOP_COMMIT_TICKS = 48;

    // After combat ends, return near the owner's ordinary companion altitude
    // so the player can interact/sit the wolf without flying after her.
    private static final int POST_COMBAT_RETURN_TICKS = 20 * 4;

    // ---------------------------------------------------------------------
    // Aerial survivability
    // ---------------------------------------------------------------------
    // These are NOT general armor buffs. They only soften close retaliation
    // from the CURRENT combat target during the vulnerable attack/escape window.
    private static final float SWOOP_RETALIATION_DAMAGE_MULTIPLIER = 0.72F;
    private static final float RECOVERY_RETALIATION_DAMAGE_MULTIPLIER = 0.40F;
    private static final double CLOSE_RETALIATION_RADIUS_SQR = 5.0D * 5.0D;
    private static final float CRITICAL_HEALTH_FRACTION = 0.30F;

    // ---------------------------------------------------------------------
    // Apex Hunter — heavyweight prey damage
    // ---------------------------------------------------------------------
    // Phantom is one of Wolfism's elite aerial combat wolves. Ordinary mobs
    // should not receive absurd one-shot damage, but large/high-health prey
    // should not turn a solo Phantom fight into a minute-long chip-damage duel.
    //
    // Ravager: 100 max HP -> +45 damage per committed aerial impact.
    // Combined with ordinary attack + Dive/Phasing when ready, this targets
    // roughly a 2-swoop solo kill (occasionally 3 if a dive is weak) rather than requiring a whole Phantom pack.
    private static final float APEX_PREY_MIN_MAX_HEALTH = 30.0F;
    private static final float APEX_PREY_HEALTH_FRACTION = 0.45F;
    private static final float APEX_PREY_MAX_BONUS_DAMAGE = 45.0F;

    // ---------------------------------------------------------------------
    // Threat awareness / autonomous guard behavior
    // ---------------------------------------------------------------------
    // Eye in the Sky remains a non-aggressive 24-block reconnaissance system.
    // Inside these smaller zones, however, a hostile is close enough to count
    // as an immediate threat and Phantom may autonomously intercept it.
    private static final double THREAT_SCAN_RADIUS = 30.0D;
    private static final double SELF_INTERCEPT_RADIUS = 10.0D;
    private static final double OWNER_INTERCEPT_RADIUS = 12.0D;
    private static final double FAMILY_INTERCEPT_RADIUS = 8.0D;
    private static final int THREAT_SCAN_INTERVAL = 5;
    private static final int COMBAT_TARGET_LOCK_TICKS = 20 * 6;

    // Survival-friendly taming approach. A wild Phantom Wolf notices a player
    // holding either a bone or anything vanilla Wolf#isFood accepts, descends,
    // and waits on the ground close enough to interact with.
    private static final double FOOD_LURE_RANGE = 24.0D;
    private static final double FOOD_LURE_RANGE_SQR = FOOD_LURE_RANGE * FOOD_LURE_RANGE;
    private static final double FOOD_LURE_LANDING_OFFSET = 1.75D;
    private static final double FOOD_LURE_FINAL_DESCENT_HEIGHT = 0.85D;

    // ---------------------------------------------------------------------
    // Phantom Wolf ability kit
    // ---------------------------------------------------------------------
    public static final double EYE_IN_THE_SKY_RADIUS = 24.0D;
    public static final int PHANTOM_DIVE_COOLDOWN_TICKS = 20 * 18;
    public static final int PHASING_BITE_COOLDOWN_TICKS = 20 * 8;
    public static final int PHANTOM_SCREECH_COOLDOWN_TICKS = 20 * 35;
    public static final int PHANTOM_SWEEP_COOLDOWN_TICKS = 20 * 35;
    public static final int SKY_HUNTER_COOLDOWN_TICKS = 20 * 55;
    public static final int SKY_HUNTER_DURATION_TICKS = 20 * 8;

    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int RECON_SCAN_INTERVAL = 20;

    private static final float PHASING_BITE_BONUS_DAMAGE = 3.0F;
    private static final float PHANTOM_DIVE_MIN_BONUS_DAMAGE = 1.5F;
    private static final float PHANTOM_DIVE_MAX_BONUS_DAMAGE = 5.0F;
    private static final double PHANTOM_SCREECH_RADIUS = 8.0D;
    private static final double PHANTOM_SWEEP_RADIUS = 4.25D;

    private int abilityLockoutTicks;
    private int skyHunterTicks;

    // Prevent target thrashing after Phantom commits to a legitimate threat.
    // The lock refreshes while the enemy remains immediately dangerous.
    private int combatTargetLockTicks;
    private int postCombatReturnTicks;

    // Set only by PhantomWolfFoodLureGoal. The first flag reserves the MOVE
    // channel; the second means the wolf is close enough to release Phantom
    // hover physics and let gravity finish the landing.
    private boolean foodLureActive;
    private boolean foodLureFinalLanding;

    private Vec3 moveTargetPoint = Vec3.ZERO;
    private boolean hasFlightMoveTarget;
    private @Nullable BlockPos anchorPoint;
    private AttackPhase attackPhase = AttackPhase.CIRCLE;

    // Captured the instant CIRCLE -> SWOOP begins. Phantom Dive must measure
    // the height committed to the dive, not the almost-zero height difference
    // remaining after the wolf has already reached its victim.
    private double swoopStartY = Double.NaN;

    private static final class BrainHolder {
        private static final Brain.Provider<PhantomWolf> PROVIDER = Brain.<PhantomWolf>provider(
                ImmutableList.of(ModSensorTypes.PHANTOM_PACK.get()),
                wolf -> List.of());
    }

    public PhantomWolf(EntityType<? extends PhantomWolf> type, Level level) {
        super(type, level);
        this.moveControl = new PhantomWolfMoveControl(this);
        this.lookControl = new PhantomWolfLookControl(this);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.PHANTOM_WOLF.get();
    }

    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity previous = this.getTarget();
        super.setTarget(target);

        if (!this.level().isClientSide()
                && previous != null
                && target == null) {
            this.beginPostCombatReturn();
        }

        if (target != null) {
            this.postCombatReturnTicks = 0;
        }
    }

    @Override
    protected Brain<PhantomWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<PhantomWolf> getBrain() {
        return (Brain<PhantomWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof PhantomWolf phantom && this.isPhantomPackmate(phantom)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.PHANTOM_DIVE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.PHANTOM_PHASING_BITE_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.PHANTOM_SCREECH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.PHANTOM_SWEEP_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.PHANTOM_SKY_HUNTER_COOLDOWN.get());

        if (this.combatTargetLockTicks > 0) {
            --this.combatTargetLockTicks;
        }

        this.getBrain().tick(level, this);

        // Existing pack threat sharing remains useful, but it is no longer the
        // only way a Phantom Wolf acquires a combat target.
        if (!this.isBaby()
                && !this.isOrderedToSit()
                && this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.PHANTOM_PACK_THREAT.get())
                    .orElse(null);

            if (sharedThreat != null && this.isValidPhantomCombatTarget(sharedThreat)) {
                this.acquireCombatThreat(sharedThreat, false);
            }
        }

        super.customServerAiStep(level);

        // Run LAST so vanilla/shared target goals cannot erase a threat that
        // Phantom's own 360-degree awareness has just identified.
        if (this.tickCount % THREAT_SCAN_INTERVAL
                == Math.floorMod(this.getId(), THREAT_SCAN_INTERVAL)) {
            this.tickThreatAwareness(level);
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new PhantomWolfBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Survival taming accessibility: an untamed Phantom Wolf will interrupt
        // idle circling to descend toward a player visibly offering a bone or
        // normal wolf food. This does not auto-tame or consume anything.
        this.goalSelector.addGoal(0, new PhantomWolfFoodLureGoal());

        // Landing for an explicit owner sit command.
        this.goalSelector.addGoal(1, new PhantomWolfLandingGoal());

        // Vanilla-Phantom style combat: strategy manages CIRCLE/SWOOP while the
        // move goals own the actual 3D destination.
        this.goalSelector.addGoal(2, new PhantomWolfAttackStrategyGoal());
        this.goalSelector.addGoal(2, new PhantomWolfSweepAttackGoal());
        this.goalSelector.addGoal(2, new PhantomWolfRecoveryGoal());
        this.goalSelector.addGoal(2, new PhantomWolfPostCombatReturnGoal());

        // Tamed Phantom Wolves fly back to their owner in full XYZ space.
        this.goalSelector.addGoal(3, new PhantomWolfFollowOwnerGoal());

        // Wild wolves and nearby tamed wolves use the Phantom's anchor-circle
        // motion instead of ordinary ground wandering.
        this.goalSelector.addGoal(7, new PhantomWolfCircleAroundAnchorGoal());
    }

    @Override
    public void tick() {
        super.tick();

        boolean orderedSit = this.isOrderedToSit();

        // Ordered sitting is absolute, matching a normal tame wolf command.
        // Unlike an autonomous flight state, family-defense/combat must not steal
        // the MOVE channel while the owner has explicitly told this wolf to sit.
        if (!this.level().isClientSide()) {
            if (orderedSit) {
                this.attackPhase = AttackPhase.CIRCLE;

                if (this.getTarget() != null) {
                    this.setTarget(null);
                }

                if (this.onGround()) {
                    this.clearFlightMoveTarget();
                    this.getNavigation().stop();
                    this.setInSittingPose(true);
                    this.setNoGravity(false);
                    this.setXRot(Mth.approachDegrees(this.getXRot(), 0.0F, 8.0F));

                    // A seated Phantom Wolf is a statue just like a vanilla wolf.
                    // Do not leave residual horizontal Phantom velocity behind.
                    Vec3 motion = this.getDeltaMovement();
                    this.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
                    this.hurtMarked = true;
                } else {
                    // Descend in the normal standing/flying pose. The sitting pose
                    // begins only after the paws have actually touched the ground.
                    this.setInSittingPose(false);
                }
            } else if (this.isInSittingPose()) {
                this.setInSittingPose(false);
            }
        }

        // Keep no-gravity only for true Phantom flight. Ordered sitting restores
        // gravity immediately. A wild food lure keeps Phantom flight while
        // approaching, then releases gravity only for the final landing step.
        this.setNoGravity(!orderedSit && !this.foodLureFinalLanding);

        if (this.anchorPoint == null) {
            this.anchorPoint = this.blockPosition().above(5);
        }

        if (this.level().isClientSide() && this.isPhantomFlying()) {
            this.tickClientFlapEffects();
        }

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        if (this.postCombatReturnTicks > 0 && this.getTarget() == null) {
            --this.postCombatReturnTicks;
        } else if (this.getTarget() != null) {
            this.postCombatReturnTicks = 0;
        }

        if (this.skyHunterTicks > 0) {
            --this.skyHunterTicks;
            if (this.tickCount % 6 == 0) {
                level.sendParticles(
                        ParticleTypes.PORTAL,
                        this.getX(),
                        this.getY() + this.getBbHeight() * 0.70D,
                        this.getZ(),
                        4,
                        0.35D, 0.22D, 0.35D,
                        0.06D);
            }
        }

        if (this.isBaby() || this.isOrderedToSit() || this.isInSittingPose()) {
            this.skyHunterTicks = 0;
            this.getBrain().eraseMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get());
            return;
        }

        if (this.tickCount % RECON_SCAN_INTERVAL == Math.floorMod(this.getId(), RECON_SCAN_INTERVAL)) {
            this.tickEyeInTheSky(level);
        }

        if (!this.isSkyHunterActive()
                && this.tickCount % ABILITY_DECISION_INTERVAL == 0
                && this.abilityLockoutTicks <= 0) {
            if (!this.trySkyHunter(level)) {
                this.tryPhantomScreech(level);
            }
        }
    }

    private void tickClientFlapEffects() {
        float anim = Mth.cos(
                (this.getUniqueFlapTickOffset() + this.tickCount)
                        * FLAP_DEGREES_PER_TICK
                        * (float) (Math.PI / 180.0D)
                        + (float) Math.PI);
        float nextAnim = Mth.cos(
                (this.getUniqueFlapTickOffset() + this.tickCount + 1)
                        * FLAP_DEGREES_PER_TICK
                        * (float) (Math.PI / 180.0D)
                        + (float) Math.PI);

        if (anim > 0.0F && nextAnim <= 0.0F) {
            this.level().playLocalSound(
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.PHANTOM_FLAP,
                    this.getSoundSource(),
                    0.75F + this.random.nextFloat() * 0.08F,
                    0.98F + this.random.nextFloat() * 0.06F,
                    false);
        }

        // The previous pass used the wolf's 0.6-block collision width, so the
        // particles appeared under the torso. The visual wings are much wider.
        // Emit from actual wing-root / wing-tip offsets instead.
        float yaw = this.getYRot() * (float) (Math.PI / 180.0D);
        double rightX = Mth.cos(yaw);
        double rightZ = Mth.sin(yaw);
        double wingY = this.getY() + this.getBbHeight() * 0.76D + anim * 0.055D;

        this.spawnWingParticle(1.16D, wingY, rightX, rightZ, ParticleTypes.MYCELIUM);
        this.spawnWingParticle(-1.16D, wingY, rightX, rightZ, ParticleTypes.MYCELIUM);

        if ((this.tickCount & 1) == 0) {
            this.spawnWingParticle(0.58D, wingY - 0.02D, rightX, rightZ, ParticleTypes.PORTAL);
            this.spawnWingParticle(-0.58D, wingY - 0.02D, rightX, rightZ, ParticleTypes.PORTAL);
        }
    }

    private void spawnWingParticle(
            double lateralOffset,
            double y,
            double rightX,
            double rightZ,
            net.minecraft.core.particles.ParticleOptions particle) {
        this.level().addParticle(
                particle,
                this.getX() + rightX * lateralOffset,
                y,
                this.getZ() + rightZ * lateralOffset,
                0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean isFlapping() {
        return this.isPhantomFlying()
                && (this.getUniqueFlapTickOffset() + this.tickCount) % TICKS_PER_FLAP == 0;
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    /**
     * True while the model should use the Phantom flight pose/flap animation.
     */
    public boolean isPhantomFlying() {
        // A real sitting pose always wins over the autonomous Phantom flight
        // state. Do not make rendering depend on a perfect onGround() sample:
        // the adult model can be microscopically above the collision surface
        // while the Wolf sitting pose is already synchronized.
        if (this.isInSittingPose()) {
            return false;
        }

        if (this.onGround() && (this.isOrderedToSit() || this.foodLureActive)) {
            return false;
        }

        return !this.onGround()
                || !this.isOrderedToSit()
                || this.hasFamilyDefenseEmergency();
    }

    public boolean isSwooping() {
        return this.attackPhase == AttackPhase.SWOOP;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        // Wings make landing safe; this mirrors vanilla Phantom's no-fall rule.
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        // Real sit landing and the last fraction of a food-lure landing use
        // ordinary wolf/air travel so gravity and ground collision can complete.
        if (this.isOrderedToSit() || this.foodLureFinalLanding) {
            super.travel(input);
            return;
        }

        this.travelFlying(input, 0.2F);
    }


    /**
     * Very-rare nocturnal surface spawning for the Phantom Wolf.
     *
     * <p>The biome modifier controls rarity and habitat; this predicate keeps
     * natural spawns on legitimate wolf ground and in darkness. The entity
     * takes over with full 3D Phantom flight immediately after spawning.</p>
     */
    public static boolean checkPhantomWolfSpawnRules(
            EntityType<PhantomWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());

        // Phantom is configured for both forest and mountain habitats. Restricting
        // her exclusively to WOLVES_SPAWNABLE_ON made Stony/Jagged Peaks almost
        // sterile because exposed mountain surfaces are commonly stone/gravel.
        boolean validGround = ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                || ground.is(BlockTags.BASE_STONE_OVERWORLD)
                || ground.is(Blocks.GRAVEL)
                || ground.is(Blocks.SNOW)
                || ground.is(Blocks.SNOW_BLOCK);

        return validGround
                && level.getFluidState(pos).isEmpty()
                && level.getLevel().isDarkOutside();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData) {
        this.anchorPoint = this.blockPosition().above(5);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.anchorPoint = input.read("phantom_wolf_anchor", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("phantom_wolf_anchor", BlockPos.CODEC, this.anchorPoint);
    }


    // ---------------------------------------------------------------------
    // Phantom pack / Brain coordination
    // ---------------------------------------------------------------------

    public boolean isPhantomPackmate(PhantomWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isPhantomFamilyMember(LivingEntity entity) {
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

        return entity instanceof PhantomWolf phantom && this.isPhantomPackmate(phantom);
    }

    public boolean isValidPhantomCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || this.isBaby()
                || this.isOrderedToSit()
                || target instanceof Creeper
                || !this.canAttack(target)
                || this.isAlliedTo(target)
                || this.isPhantomFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidPhantomCombatTarget(threat)) {
            return;
        }

        for (PhantomWolf mate : level.getEntitiesOfClass(
                PhantomWolf.class,
                this.getBoundingBox().inflate(PhantomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isPhantomPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.PHANTOM_PACK_THREAT.get(), threat);

            if (!mate.isBaby()
                    && !mate.isOrderedToSit()
                    && mate.isValidPhantomCombatTarget(threat)) {
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

    private boolean canUsePhantomCombatFlight() {
        LivingEntity target = this.getTarget();
        return this.canUseActiveWolfismAbility()
                && this.isValidPhantomCombatTarget(target);
    }

    private boolean isCriticallyWounded() {
        return this.getHealth() <= this.getMaxHealth() * CRITICAL_HEALTH_FRACTION;
    }

    // ---------------------------------------------------------------------
    // THREAT AWARENESS
    // ---------------------------------------------------------------------

    /**
     * 360-degree threat awareness.
     *
     * <p>This deliberately has no facing-angle requirement. Phantom Wolf is an
     * aerial scout; a hostile behind her is still a hostile. Eye in the Sky can
     * mark enemies at long range without attacking them, while this closer
     * intercept layer decides when a threat is dangerous enough to engage.</p>
     */
    private void tickThreatAwareness(ServerLevel level) {
        if (this.isBaby()
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || !this.isTame()) {
            return;
        }

        // Family defense is already the highest-authority combat reason.
        if (this.hasFamilyDefenseEmergency()) {
            LivingEntity familyThreat = this.getFamilyDefenseTarget();
            if (familyThreat != null && this.isValidPhantomCombatTarget(familyThreat)) {
                this.acquireCombatThreat(familyThreat, true);
            }
            return;
        }

        LivingEntity current = this.getTarget();

        /*
         * Creepers belong to Wolfism's shared Creeper target/turn-taking/retreat
         * system. They are intentionally excluded from ordinary Phantom swoops.
         *
         * V9 accidentally treated that deliberate exclusion as "invalid target"
         * and cleared the Creeper target every awareness scan, sabotaging the
         * shared system before it could finish an attack turn.
         */
        if (current instanceof Creeper) {
            this.combatTargetLockTicks = 0;
            this.attackPhase = AttackPhase.CIRCLE;
            return;
        }

        if (current != null && !this.isValidPhantomCombatTarget(current)) {
            this.setTarget(null);
            current = null;
            this.combatTargetLockTicks = 0;
            if (this.attackPhase != AttackPhase.RECOVER) {
                this.attackPhase = AttackPhase.CIRCLE;
            }
        }

        LivingEntity immediate = this.findBestImmediateThreat(level);

        if (immediate != null) {
            double newScore = this.immediateThreatScore(level, immediate);
            double currentScore = current == null
                    ? Double.NEGATIVE_INFINITY
                    : this.immediateThreatScore(level, current);

            // Stay committed unless the new threat is substantially more urgent
            // (for example, something attacking the owner versus a nearby idle mob).
            if (current == null
                    || current == immediate
                    || this.combatTargetLockTicks <= 0
                    || newScore > currentScore + 1000.0D) {
                this.acquireCombatThreat(immediate, false);
            } else if (this.isImmediateThreat(level, current)) {
                this.combatTargetLockTicks = COMBAT_TARGET_LOCK_TICKS;
            }

            return;
        }

        // A previously acquired hostile may briefly move outside the immediate
        // guard zone. Keep the target for a few seconds so Phantom does not
        // constantly drop/reacquire a moving enemy.
        if (current != null
                && !this.hasFamilyDefenseEmergency()
                && this.combatTargetLockTicks <= 0
                && !this.isImmediateThreat(level, current)) {
            this.setTarget(null);
            if (this.attackPhase != AttackPhase.RECOVER) {
                this.attackPhase = AttackPhase.CIRCLE;
            }
        }
    }

    private @Nullable LivingEntity findBestImmediateThreat(ServerLevel level) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(
                                THREAT_SCAN_RADIUS,
                                THREAT_SCAN_RADIUS * 0.65D,
                                THREAT_SCAN_RADIUS),
                        candidate -> candidate instanceof Enemy
                                && this.isValidPhantomCombatTarget(candidate)
                                && this.isImmediateThreat(level, candidate))
                .stream()
                .max(Comparator.comparingDouble(
                        candidate -> this.immediateThreatScore(level, candidate)))
                .orElse(null);
    }

    /**
     * A hostile becomes combat-authorized when it is genuinely dangerous:
     *
     * <ul>
     *   <li>it is actively targeting Phantom, owner, or tamed wolf family;</li>
     *   <li>it comes within 10 blocks of Phantom;</li>
     *   <li>it comes within 12 blocks of the owner;</li>
     *   <li>it comes within 8 blocks of another tamed wolf family member.</li>
     * </ul>
     *
     * <p>This preserves Eye in the Sky's non-aggressive long-range scouting.
     * A Zombie 20 blocks away can glow without being attacked; if it approaches
     * the family, Phantom transitions from scout to aerial guard.</p>
     */
    private boolean isImmediateThreat(ServerLevel level, LivingEntity candidate) {
        if (!this.isValidPhantomCombatTarget(candidate)) {
            return false;
        }

        if (candidate instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            if (mobTarget == this
                    || (mobTarget != null && this.isPhantomFamilyMember(mobTarget))) {
                return true;
            }
        }

        if (this.distanceToSqr(candidate)
                <= SELF_INTERCEPT_RADIUS * SELF_INTERCEPT_RADIUS) {
            return true;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.distanceToSqr(candidate)
                <= OWNER_INTERCEPT_RADIUS * OWNER_INTERCEPT_RADIUS) {
            return true;
        }

        return !level.getEntitiesOfClass(
                        Wolf.class,
                        candidate.getBoundingBox().inflate(FAMILY_INTERCEPT_RADIUS),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && this.isPhantomFamilyMember(wolf))
                .isEmpty();
    }

    private double immediateThreatScore(ServerLevel level, LivingEntity candidate) {
        if (!this.isValidPhantomCombatTarget(candidate)) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.0D;

        if (candidate instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            if (mobTarget == this) {
                score += 10000.0D;
            } else if (mobTarget != null && this.isPhantomFamilyMember(mobTarget)) {
                score += 9000.0D;
            }
        }

        double selfDistance = Math.sqrt(this.distanceToSqr(candidate));
        if (selfDistance <= SELF_INTERCEPT_RADIUS) {
            score += 5000.0D - selfDistance * 80.0D;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            double ownerDistance = Math.sqrt(owner.distanceToSqr(candidate));
            if (ownerDistance <= OWNER_INTERCEPT_RADIUS) {
                score += 4500.0D - ownerDistance * 70.0D;
            }
        }

        boolean nearFamily = !level.getEntitiesOfClass(
                        Wolf.class,
                        candidate.getBoundingBox().inflate(FAMILY_INTERCEPT_RADIUS),
                        wolf -> wolf.isAlive()
                                && wolf.isTame()
                                && this.isPhantomFamilyMember(wolf))
                .isEmpty();

        if (nearFamily) {
            score += 4000.0D;
        }

        return score;
    }

    private void acquireCombatThreat(LivingEntity threat, boolean urgent) {
        if (!this.isValidPhantomCombatTarget(threat)) {
            return;
        }

        boolean changed = this.getTarget() != threat;
        this.setTarget(threat);
        this.combatTargetLockTicks = COMBAT_TARGET_LOCK_TICKS;
        this.setAnchorAboveTarget();

        if (changed) {
            this.alertPackToThreat(threat);
        }

        /*
         * Rear attacks are still detected immediately, but being touched once
         * no longer makes a healthy Phantom abandon a dive she already committed
         * to. That V9 behavior was one source of repeated fake-outs.
         *
         * Below 35% health she becomes appropriately defensive again.
         */
        boolean committedAndHealthy = this.attackPhase == AttackPhase.SWOOP
                && this.getTarget() == threat
                && this.getHealth() > this.getMaxHealth() * 0.35F;

        if (urgent
                && this.isPhantomFlying()
                && this.attackPhase != AttackPhase.RECOVER
                && !committedAndHealthy) {
            this.beginPostSwoopRecovery(threat);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity sourceEntity = source.getEntity();

        /*
         * Aerial momentum protection.
         *
         * Phantom is not gaining permanent armor. This applies only when the
         * CURRENT enemy catches her during the tiny close-range swoop/recovery
         * window. A Ravager landing one perfectly timed counter still matters,
         * but it should not deal the same full stationary hit while Phantom is
         * already moving through / away from the target at flight speed.
         */
        if (sourceEntity instanceof LivingEntity attacker
                && attacker == this.getTarget()
                && this.isPhantomFlying()
                && this.distanceToSqr(attacker) <= CLOSE_RETALIATION_RADIUS_SQR) {
            if (this.attackPhase == AttackPhase.RECOVER) {
                amount *= RECOVERY_RETALIATION_DAMAGE_MULTIPLIER;
            } else if (this.attackPhase == AttackPhase.SWOOP) {
                amount *= SWOOP_RETALIATION_DAMAGE_MULTIPLIER;
            }
        }

        boolean damaged = super.hurtServer(level, source, amount);

        if (!damaged || this.isBaby() || this.isOrderedToSit()) {
            return damaged;
        }

        if (sourceEntity instanceof LivingEntity attacker
                && attacker != this
                && this.isValidPhantomCombatTarget(attacker)) {
            // Damage is perfect 360-degree information: no look direction or
            // line-of-sight gate is allowed to make a rear attacker invisible.
            this.acquireCombatThreat(attacker, true);

            // Once truly wounded, survival wins. Keep the target so awareness,
            // Screech, and pack threat information remain valid, but force a
            // high evasive recovery instead of committing to another dive.
            if (this.isCriticallyWounded()
                    && this.isPhantomFlying()
                    && this.attackPhase != AttackPhase.RECOVER) {
                this.beginPostSwoopRecovery(attacker);
            }
        }

        return damaged;
    }

    // ---------------------------------------------------------------------
    // PHANTOM WOLF ABILITIES
    // ---------------------------------------------------------------------

    /**
     * Eye in the Sky + Aerial Reconnaissance.
     *
     * Tamed Phantom Wolves do not auto-aggro everything they see. Instead they
     * mark the nearest legitimate hostile with Glowing, remember it, and share
     * that recon target with nearby Phantom packmates. A real family-defense
     * event is still what authorizes combat.
     */
    private void tickEyeInTheSky(ServerLevel level) {
        LivingEntity owner = this.getOwner();
        if (!this.isTame() || !this.isPhantomFlying() || owner == null || !owner.isAlive()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get());
            return;
        }

        LivingEntity currentTarget = this.getTarget();
        LivingEntity recon = currentTarget != null && this.isValidPhantomCombatTarget(currentTarget)
                ? currentTarget
                : level.getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox().inflate(EYE_IN_THE_SKY_RADIUS, 12.0D, EYE_IN_THE_SKY_RADIUS),
                        candidate -> candidate instanceof Enemy
                                && candidate.isAlive()
                                && !(candidate instanceof Creeper)
                                && !this.isPhantomFamilyMember(candidate)
                                && this.canAttack(candidate))
                .stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);

        if (recon == null) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get());
            return;
        }

        this.getBrain().setMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get(), recon);
        recon.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, true, false), this);

        for (PhantomWolf mate : level.getEntitiesOfClass(
                PhantomWolf.class,
                this.getBoundingBox().inflate(PhantomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isPhantomPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get(), recon);
        }

        // Sky Signal: a restrained vertical marker that lets the owner read
        // exactly which threat the airborne scout has identified.
        if (this.tickCount % 40 == Math.floorMod(this.getId(), 40)) {
            for (int i = 0; i < 7; ++i) {
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        recon.getX(),
                        recon.getY() + recon.getBbHeight() + 0.25D + i * 0.28D,
                        recon.getZ(),
                        1,
                        0.025D, 0.025D, 0.025D,
                        0.0D);
            }
        }
    }

    /**
     * Fear at Night / Phantom Screech.
     *
     * This is intentionally an area-control ability, not another damage nuke.
     */
    private boolean tryPhantomScreech(ServerLevel level) {
        LivingEntity primaryTarget = this.getTarget();
        if (level.isBrightOutside()
                || primaryTarget == null
                || !this.isValidPhantomCombatTarget(primaryTarget)
                || !this.canUseActiveWolfismAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.PHANTOM_SCREECH_COOLDOWN.get())) {
            return false;
        }

        // Search around the fight, not around the airborne wolf. The Phantom
        // Wolf deliberately orbits several blocks above enemies, so using the
        // wolf as the center could make a valid ground group impossible to see.
        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(
                        PHANTOM_SCREECH_RADIUS,
                        4.5D,
                        PHANTOM_SCREECH_RADIUS),
                this::isValidPhantomCombatTarget);

        if (threats.size() < 2) {
            return false;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.PHANTOM_SCREECH_COOLDOWN.get(),
                PHANTOM_SCREECH_COOLDOWN_TICKS);
        this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 20);

        this.playSound(
                SoundEvents.PHANTOM_HURT,
                2.2F,
                0.65F + this.random.nextFloat() * 0.08F);

        // First ring shows the scream source; second ring marks the battlefield
        // actually being suppressed. This makes the ability visually testable.
        this.sendRing(level, this.position().add(0.0D, 0.55D, 0.0D), 3.0D, 28, ParticleTypes.PORTAL);
        this.sendRing(
                level,
                primaryTarget.position().add(0.0D, 0.45D, 0.0D),
                PHANTOM_SCREECH_RADIUS,
                48,
                ParticleTypes.MYCELIUM);

        for (LivingEntity threat : threats) {
            threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 6, 1), this);
            threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 0), this);

            // Give "Fear" a physical read: enemies recoil slightly away from the
            // Phantom Wolf instead of only receiving invisible stat penalties.
            Vec3 fearAway = threat.position().subtract(this.position());
            Vec3 fearHorizontal = new Vec3(fearAway.x, 0.0D, fearAway.z);
            if (fearHorizontal.lengthSqr() > 1.0E-5D) {
                fearHorizontal = fearHorizontal.normalize().scale(0.38D);
                threat.push(fearHorizontal.x, 0.12D, fearHorizontal.z);
                threat.hurtMarked = true;
            }

            level.sendParticles(
                    ParticleTypes.PORTAL,
                    threat.getX(),
                    threat.getY() + threat.getBbHeight() * 0.60D,
                    threat.getZ(),
                    16,
                    0.32D, 0.38D, 0.32D,
                    0.065D);
        }

        return true;
    }

    /**
     * Sky Hunter: the high-pressure aerial pursuit state.
     *
     * It triggers only for an airborne or heavyweight legitimate target, so it
     * is not permanently active in every tiny fight.
     */
    private boolean trySkyHunter(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null
                || !this.isValidPhantomCombatTarget(target)
                || this.skyHunterTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.PHANTOM_SKY_HUNTER_COOLDOWN.get())) {
            return false;
        }

        boolean worthyTarget = !target.onGround() || target.getMaxHealth() >= 30.0F;
        if (!worthyTarget) {
            return false;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.PHANTOM_SKY_HUNTER_COOLDOWN.get(),
                SKY_HUNTER_COOLDOWN_TICKS);
        this.skyHunterTicks = SKY_HUNTER_DURATION_TICKS;
        this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 12);

        this.playSound(
                SoundEvents.PHANTOM_AMBIENT,
                1.6F,
                0.72F + this.random.nextFloat() * 0.10F);

        level.sendParticles(
                ParticleTypes.PORTAL,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.55D,
                this.getZ(),
                32,
                0.70D, 0.45D, 0.70D,
                0.10D);
        return true;
    }

    public boolean isSkyHunterActive() {
        return this.skyHunterTicks > 0;
    }

    /**
     * Heavy-prey bonus for committed aerial impacts.
     *
     * <p>This is deliberately capped. It makes heavyweight targets such as a
     * Ravager legitimate solo prey without turning very high-health bosses into
     * percentage-damage victims. Normal 20-HP hostiles receive no bonus.</p>
     */
    private float calculateApexPreyBonus(LivingEntity target) {
        float maxHealth = target.getMaxHealth();
        if (maxHealth < APEX_PREY_MIN_MAX_HEALTH) {
            return 0.0F;
        }

        return Mth.clamp(
                maxHealth * APEX_PREY_HEALTH_FRACTION,
                0.0F,
                APEX_PREY_MAX_BONUS_DAMAGE);
    }

    /**
     * Resolves one physical swoop impact.
     *
     * <p>V6.x called normal doHurtTarget(), then immediately called hurtServer()
     * again for Dive and again for Phasing Bite. Those bonus hits can be hidden
     * by Minecraft's same-tick hurt/invulnerability handling. V7 calculates all
     * currently-ready primary-target damage first and submits ONE damage event,
     * then applies the distinct ability effects/VFX only after that hit succeeds.</p>
     */
    private boolean performSwoopImpact(ServerLevel level, LivingEntity target) {
        boolean diveReady = !this.isCoolingDown(ModMemoryModuleTypes.PHANTOM_DIVE_COOLDOWN.get());
        boolean phasingReady = !this.isCoolingDown(ModMemoryModuleTypes.PHANTOM_PHASING_BITE_COOLDOWN.get());

        float diveBonus = diveReady ? this.calculatePhantomDiveBonus(target) : 0.0F;
        float phasingBonus = phasingReady ? PHASING_BITE_BONUS_DAMAGE : 0.0F;
        float apexPreyBonus = this.calculateApexPreyBonus(target);
        float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float totalDamage = baseDamage + diveBonus + phasingBonus + apexPreyBonus;

        DamageSource source = this.damageSources().mobAttack(this);
        boolean hit = target.hurtServer(level, source, totalDamage);
        if (!hit) {
            return false;
        }

        EnchantmentHelper.doPostAttackEffects(level, target, source);

        if (diveReady) {
            this.activatePhantomDive(level, target, diveBonus);
        }

        if (phasingReady) {
            this.activatePhasingBite(level, target);
        }

        if (apexPreyBonus > 0.0F) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.72D,
                    target.getZ(),
                    18,
                    0.42D, 0.40D, 0.42D,
                    0.08D);
        }

        this.tryPhantomSweep(level, target);
        this.alertPackToThreat(target);
        return true;
    }

    private float calculatePhantomDiveBonus(LivingEntity target) {
        double committedStartY = Double.isNaN(this.swoopStartY)
                ? this.getY()
                : this.swoopStartY;

        double actualDrop = Math.max(0.0D, committedStartY - target.getY(0.5D));
        double descentSpeed = Math.max(0.0D, -this.getDeltaMovement().y);

        return (float) Mth.clamp(
                PHANTOM_DIVE_MIN_BONUS_DAMAGE
                        + actualDrop * 0.22D
                        + descentSpeed * 2.25D,
                PHANTOM_DIVE_MIN_BONUS_DAMAGE,
                PHANTOM_DIVE_MAX_BONUS_DAMAGE);
    }

    /**
     * Phantom Dive — altitude/momentum bonus attached to the real swoop.
     */
    private void activatePhantomDive(ServerLevel level, LivingEntity target, float bonusDamage) {
        this.getBrain().setMemory(
                ModMemoryModuleTypes.PHANTOM_DIVE_COOLDOWN.get(),
                PHANTOM_DIVE_COOLDOWN_TICKS);

        Vec3 away = target.position().subtract(this.position());
        if (away.lengthSqr() > 1.0E-4D) {
            Vec3 knock = away.normalize().scale(0.65D);
            target.push(knock.x, 0.25D, knock.z);
            target.hurtMarked = true;
        }

        level.sendParticles(
                ParticleTypes.POOF,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.45D,
                target.getZ(),
                16,
                0.32D, 0.24D, 0.32D,
                0.05D);
        level.sendParticles(
                ParticleTypes.PORTAL,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                20,
                0.28D, 0.30D, 0.28D,
                0.08D);

        // Higher real drops produce a slightly deeper impact pitch, making a
        // strong Dive easier to distinguish by ear without any debug UI.
        float normalized = Mth.clamp(
                bonusDamage / PHANTOM_DIVE_MAX_BONUS_DAMAGE,
                0.0F,
                1.0F);
        this.playSound(
                SoundEvents.PHANTOM_SWOOP,
                1.45F,
                0.78F + normalized * 0.18F);
    }

    /**
     * Phasing Bite — its +2 damage is already included in performSwoopImpact()
     * so it cannot be eaten by same-tick hurt immunity. This method applies the
     * supernatural debuff and its separate readable VFX.
     */
    private void activatePhasingBite(ServerLevel level, LivingEntity target) {
        this.getBrain().setMemory(
                ModMemoryModuleTypes.PHANTOM_PHASING_BITE_COOLDOWN.get(),
                PHASING_BITE_COOLDOWN_TICKS);

        // Phasing Bite should be readable as its own supernatural attack and
        // should meaningfully reduce retaliation after Phantom commits to melee.
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 6, 1), this);

        this.playSound(
                SoundEvents.PHANTOM_BITE,
                1.45F,
                0.72F + this.random.nextFloat() * 0.08F);

        Vec3 center = target.position().add(
                0.0D,
                target.getBbHeight() * 0.58D,
                0.0D);

        this.sendRing(level, center, 1.35D, 22, ParticleTypes.MYCELIUM);
        this.sendRing(level, center, 2.05D, 30, ParticleTypes.PORTAL);

        level.sendParticles(
                ParticleTypes.MYCELIUM,
                center.x,
                center.y,
                center.z,
                30,
                0.38D, 0.42D, 0.38D,
                0.055D);
        level.sendParticles(
                ParticleTypes.PORTAL,
                center.x,
                center.y,
                center.z,
                18,
                0.25D, 0.30D, 0.25D,
                0.055D);
    }

    /**
     * Phantom Sweep: after a successful swoop, knock nearby legitimate enemies
     * away with a visible wing-wave. The primary dive target is not hit twice.
     */
    private void tryPhantomSweep(ServerLevel level, LivingEntity primaryTarget) {
        if (this.isCoolingDown(ModMemoryModuleTypes.PHANTOM_SWEEP_COOLDOWN.get())) {
            return;
        }

        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(PHANTOM_SWEEP_RADIUS, 2.5D, PHANTOM_SWEEP_RADIUS),
                candidate -> candidate != primaryTarget && this.isValidPhantomCombatTarget(candidate));

        if (nearby.isEmpty()) {
            return;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.PHANTOM_SWEEP_COOLDOWN.get(),
                PHANTOM_SWEEP_COOLDOWN_TICKS);

        this.sendRing(
                level,
                primaryTarget.position().add(0.0D, 0.45D, 0.0D),
                PHANTOM_SWEEP_RADIUS,
                44,
                ParticleTypes.PORTAL);

        for (LivingEntity victim : nearby) {
            victim.hurtServer(level, this.damageSources().mobAttack(this), 3.0F);

            Vec3 away = victim.position().subtract(primaryTarget.position());
            if (away.lengthSqr() > 1.0E-4D) {
                Vec3 push = away.normalize().scale(0.75D);
                victim.push(push.x, 0.20D, push.z);
                victim.hurtMarked = true;
            }

            level.sendParticles(
                    ParticleTypes.MYCELIUM,
                    victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.45D,
                    victim.getZ(),
                    8,
                    0.22D, 0.28D, 0.22D,
                    0.03D);
        }
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

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }

        for (PhantomWolf mate : level.getEntitiesOfClass(
                PhantomWolf.class,
                this.getBoundingBox().inflate(PhantomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isPhantomPackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.015D, 0.02D, 0.015D,
                    0.0D);
        }
    }

    private void setFlightMoveTarget(Vec3 target) {
        this.moveTargetPoint = target;
        this.hasFlightMoveTarget = true;
    }

    private void clearFlightMoveTarget() {
        this.hasFlightMoveTarget = false;
        this.moveTargetPoint = this.position();
    }

    private void setAnchorAboveTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        // Every hostile gets the same aerial respect. A Zombie should not make
        // Phantom hover at melee height just because it is weaker than a Ravager.
        double anchorY = this.combatSafeOrbitY(target);

        this.anchorPoint = new BlockPos(
                Mth.floor(target.getX()),
                Mth.floor(anchorY),
                Mth.floor(target.getZ()));

        if (this.anchorPoint.getY() < this.level().getSeaLevel()) {
            this.anchorPoint = new BlockPos(
                    this.anchorPoint.getX(),
                    this.level().getSeaLevel() + 1,
                    this.anchorPoint.getZ());
        }
    }

    private double ownerCompanionHoverY(LivingEntity owner) {
        // Place the Phantom Wolf's body around the owner's chest/shoulder band.
        // For a normal player this is roughly 0.6-0.8 blocks above the feet,
        // dramatically lower than the previous ownerHeight + 2.25 target.
        double ownerBodyBand = owner.getY() + owner.getBbHeight() * 0.60D;
        return Math.max(
                owner.getY() + 0.35D,
                ownerBodyBand - this.getBbHeight() * 0.45D);
    }

    /**
     * Returns the hostile that should influence Phantom's safe flying altitude.
     *
     * <p>An actual combat target wins. Otherwise Eye in the Sky's reconnaissance
     * target is enough to make a tame Phantom climb defensively, but it still
     * does NOT authorize attacking that mob.</p>
     */
    private @Nullable LivingEntity getAltitudeThreat() {
        LivingEntity target = this.getTarget();
        if (target != null && this.isValidPhantomCombatTarget(target)) {
            return target;
        }

        LivingEntity recon = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get())
                .orElse(null);

        return recon != null && this.isValidPhantomCombatTarget(recon)
                ? recon
                : null;
    }

    private double combatSafeOrbitY(LivingEntity target) {
        return target.getY() + target.getBbHeight() + COMBAT_THREAT_CLEARANCE;
    }

    private double minimumSwoopStartY(LivingEntity target) {
        return target.getY()
                + target.getBbHeight()
                + COMBAT_MIN_SWOOP_START_CLEARANCE;
    }

    private void beginPostCombatReturn() {
        if (!this.isTame()
                || this.isOrderedToSit()
                || this.isInSittingPose()) {
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return;
        }

        this.postCombatReturnTicks = POST_COMBAT_RETURN_TICKS;
        this.attackPhase = AttackPhase.CIRCLE;
        this.swoopStartY = Double.NaN;
        this.combatTargetLockTicks = 0;
        this.anchorPoint = owner.blockPosition().above(1);

        // If the defeated target was also the current recon contact, do not let
        // stale recon altitude keep the companion hovering in the sky.
        LivingEntity recon = this.getBrain()
                .getMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get())
                .orElse(null);
        if (recon == null || !recon.isAlive()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.PHANTOM_RECON_TARGET.get());
        }

        double side = (this.getId() & 1) == 0 ? 2.0D : -2.0D;
        this.setFlightMoveTarget(new Vec3(
                owner.getX() + side,
                this.ownerCompanionHoverY(owner),
                owner.getZ()));
    }

    /**
     * Immediate disengage after a swoop.
     *
     * <p>The old flow changed back to CIRCLE but left Phantom physically beside
     * the victim until ordinary orbit steering recovered. Against Ravagers and
     * other hard-hitting mobs that was enough time to get punished repeatedly.
     * This gives the wolf a real aerial predator exit: UP + AWAY immediately,
     * then ordinary high orbit takes over.</p>
     */
    private void beginPostSwoopRecovery(LivingEntity target) {
        this.attackPhase = AttackPhase.RECOVER;

        Vec3 away = this.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);

        if (horizontal.lengthSqr() <= 1.0E-5D) {
            float yawRadians = this.getYRot() * (float) (Math.PI / 180.0D);
            horizontal = new Vec3(
                    -Mth.sin(yawRadians),
                    0.0D,
                    Mth.cos(yawRadians));
        } else {
            horizontal = horizontal.normalize();
        }

        double recoveryY = target.getY()
                + target.getBbHeight()
                + COMBAT_RECOVERY_CLEARANCE;

        this.setFlightMoveTarget(new Vec3(
                target.getX() + horizontal.x * COMBAT_RECOVERY_HORIZONTAL_DISTANCE,
                recoveryY,
                target.getZ() + horizontal.z * COMBAT_RECOVERY_HORIZONTAL_DISTANCE));

        // Immediate escape impulse so a heavy melee target does not get a free
        // second hit before MoveControl has had time to accelerate upward.
        Vec3 movement = this.getDeltaMovement();
        Vec3 escape = new Vec3(
                horizontal.x * 0.66D,
                0.92D,
                horizontal.z * 0.66D);

        this.setDeltaMovement(movement.scale(0.18D).add(escape));
    }

    private void maintainRecoveryTarget(LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);

        if (horizontal.lengthSqr() <= 1.0E-5D) {
            float yawRadians = this.getYRot() * (float) (Math.PI / 180.0D);
            horizontal = new Vec3(
                    -Mth.sin(yawRadians),
                    0.0D,
                    Mth.cos(yawRadians));
        } else {
            horizontal = horizontal.normalize();
        }

        this.setFlightMoveTarget(new Vec3(
                target.getX() + horizontal.x * COMBAT_RECOVERY_HORIZONTAL_DISTANCE,
                target.getY() + target.getBbHeight() + COMBAT_RECOVERY_CLEARANCE,
                target.getZ() + horizontal.z * COMBAT_RECOVERY_HORIZONTAL_DISTANCE));
    }

    private Vec3 predictSwoopAimPoint(LivingEntity target) {
        // Lead a moving target modestly instead of diving at where it used to be.
        // The cap avoids absurd prediction when a modded entity has huge velocity.
        double horizontalDistance = Math.sqrt(
                Mth.square(target.getX() - this.getX())
                        + Mth.square(target.getZ() - this.getZ()));

        double leadTicks = Mth.clamp(horizontalDistance / 6.0D, 0.6D, 2.25D);
        Vec3 lead = target.getDeltaMovement().scale(leadTicks);

        return new Vec3(
                target.getX() + lead.x,
                target.getY(0.55D),
                target.getZ() + lead.z);
    }

    private enum AttackPhase {
        CIRCLE,
        SWOOP,
        RECOVER
    }

    /**
     * Vanilla Phantom's special body control: head and torso point with the
     * flight yaw instead of behaving like a walking quadruped turning its head
     * independently from its body.
     */
    private static final class PhantomWolfBodyRotationControl extends BodyRotationControl {
        private final PhantomWolf wolf;

        private PhantomWolfBodyRotationControl(PhantomWolf wolf) {
            super(wolf);
            this.wolf = wolf;
        }

        @Override
        public void clientTick() {
            if (this.wolf.isPhantomFlying()) {
                this.wolf.yHeadRot = this.wolf.yBodyRot;
                this.wolf.yBodyRot = this.wolf.getYRot();
            } else {
                super.clientTick();
            }
        }
    }

    /**
     * Vanilla Phantom does not use ordinary look steering while flying. Keeping
     * the same behavior prevents Wolf head-look AI from fighting flight yaw.
     */
    private static final class PhantomWolfLookControl extends LookControl {
        private final PhantomWolf wolf;

        private PhantomWolfLookControl(PhantomWolf wolf) {
            super(wolf);
            this.wolf = wolf;
        }

        @Override
        public void tick() {
            if (!this.wolf.isPhantomFlying()) {
                super.tick();
            }
        }
    }

    /**
     * Direct adaptation of vanilla Phantom's flight motor.
     *
     * <p>The only Wolfism addition is setWantedPosition(): FlyingPathNavigation
     * and inherited Wolfism goals can feed a normal navigation destination into
     * the same Phantom motor. That lets family/Creeper safety remain compatible
     * with true 3D flight instead of maintaining two competing movement systems.</p>
     */
    private static final class PhantomWolfMoveControl extends MoveControl {
        private static final int OBSTACLE_CLIMB_MEMORY_TICKS = 8;
        private static final double OBSTACLE_CLIMB_MIN_Y = 2.25D;

        private final PhantomWolf wolf;
        private float speed = 0.1F;
        private int obstacleClimbTicks;

        private PhantomWolfMoveControl(PhantomWolf wolf) {
            super(wolf);
            this.wolf = wolf;
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speedModifier) {
            super.setWantedPosition(x, y, z, speedModifier);
            this.wolf.setFlightMoveTarget(new Vec3(x, y, z));
        }

        @Override
        public void tick() {
            if (this.wolf.isOrderedToSit()
                    && this.wolf.onGround()
                    && !this.wolf.hasFamilyDefenseEmergency()) {
                this.speed = 0.1F;
                this.wolf.setXRot(Mth.approachDegrees(this.wolf.getXRot(), 0.0F, 8.0F));
                return;
            }

            if (!this.wolf.hasFlightMoveTarget) {
                return;
            }

            /*
             * Flying obstacle avoidance.
             *
             * The vanilla-Phantom-style response used here previously turned the
             * wolf 180 degrees whenever horizontalCollision became true. That is
             * fine for a wild Phantom wandering in open sky, but awful for a
             * companion following an owner across fences/walls: it repeatedly
             * bounces off the obstacle even though it can simply gain altitude.
             *
             * Keep the REAL destination unchanged and temporarily bias only the
             * vertical steering component upward. Repeated collision naturally
             * refreshes the climb until the wolf is physically above the obstacle.
             */
            if (this.wolf.horizontalCollision
                    && this.wolf.isPhantomFlying()
                    && !this.wolf.isOrderedToSit()
                    && !this.wolf.foodLureFinalLanding) {
                this.obstacleClimbTicks = OBSTACLE_CLIMB_MEMORY_TICKS;
                this.speed = Math.max(this.speed, 0.75F);
            }

            double tdx = this.wolf.moveTargetPoint.x - this.wolf.getX();
            double tdy = this.wolf.moveTargetPoint.y - this.wolf.getY();
            double tdz = this.wolf.moveTargetPoint.z - this.wolf.getZ();

            /*
             * Threat altitude floor.
             *
             * Eye in the Sky may detect a hostile without making it a combat
             * target. That detection still tells an intelligent flying wolf not
             * to cruise at chest height beside the danger. The floor is ignored
             * only during a deliberate SWOOP so attacks can physically connect.
             */
            LivingEntity altitudeThreat = this.wolf.getAltitudeThreat();
            if (this.wolf.attackPhase != AttackPhase.SWOOP && altitudeThreat != null) {
                double safeY = this.wolf.combatSafeOrbitY(altitudeThreat);
                tdy = Math.max(tdy, safeY - this.wolf.getY());
            }

            if (this.obstacleClimbTicks > 0) {
                --this.obstacleClimbTicks;

                // Because this is relative to CURRENT Y every tick, a taller wall
                // simply keeps refreshing the climb while collision persists.
                tdy = Math.max(tdy, OBSTACLE_CLIMB_MIN_Y);
            }
            double sd = Math.sqrt(tdx * tdx + tdz * tdz);

            // Vanilla Phantom assumes there is always some horizontal distance.
            // A tame owner/landing target can be almost directly above or below
            // a wolf, so keep the same smoothing but permit this one edge case.
            if (Math.abs(sd) <= 1.0E-5D) {
                if (Math.abs(tdy) > 0.05D) {
                    Vec3 movement = this.wolf.getDeltaMovement();
                    double wantedY = Mth.clamp(tdy * 0.08D, -0.22D, 0.22D);
                    this.wolf.setDeltaMovement(
                            movement.add(new Vec3(0.0D, wantedY, 0.0D).subtract(movement).scale(0.2D)));
                    this.wolf.setXRot(tdy > 0.0D ? -75.0F : 75.0F);
                }
                return;
            }

            /*
             * Never allow a steep dive to reverse the horizontal steering vector.
             *
             * With V8/V9's high combat altitude, the old vanilla-derived formula
             * could become NEGATIVE when vertical distance greatly exceeded
             * horizontal distance. That literally made a committed dive steer
             * backward for part of its descent -- the visual "psych!" behavior.
             */
            double minimumHorizontalScale =
                    this.wolf.attackPhase == AttackPhase.SWOOP ? 0.48D : 0.18D;
            double yRelativeScale = Mth.clamp(
                    1.0D - Math.abs(tdy * 0.7D) / Math.max(sd, 1.0E-5D),
                    minimumHorizontalScale,
                    1.0D);
            tdx *= yRelativeScale;
            tdz *= yRelativeScale;
            sd = Math.sqrt(tdx * tdx + tdz * tdz);
            double sd2 = Math.sqrt(tdx * tdx + tdz * tdz + tdy * tdy);

            if (sd2 <= 1.0E-5D) {
                return;
            }

            float previousYaw = this.wolf.getYRot();
            float angle = (float) Mth.atan2(tdz, tdx);
            float current = Mth.wrapDegrees(this.wolf.getYRot() + 90.0F);
            float wanted = Mth.wrapDegrees(angle * (180.0F / (float) Math.PI));

            boolean committingSwoop = this.wolf.attackPhase == AttackPhase.SWOOP;
            float turnRate = committingSwoop ? 12.0F : 4.0F;
            this.wolf.setYRot(Mth.approachDegrees(current, wanted, turnRate) - 90.0F);
            this.wolf.yBodyRot = this.wolf.getYRot();

            // A committed attack gets real chase speed and faster acceleration.
            // Ordinary companion/idle flight keeps the already-approved values.
            float topSpeed;
            if (committingSwoop) {
                topSpeed = this.wolf.isSkyHunterActive() ? 2.80F : 2.45F;
            } else {
                topSpeed = this.wolf.isSkyHunterActive()
                        ? 2.25F
                        : (!this.wolf.level().isBrightOutside() ? 1.98F : 1.80F);
            }

            float headingDifference =
                    Mth.degreesDifferenceAbs(previousYaw, this.wolf.getYRot());

            if (headingDifference < (committingSwoop ? 9.0F : 3.0F)) {
                float acceleration = committingSwoop
                        ? 0.085F
                        : 0.005F * (topSpeed / Math.max(this.speed, 0.05F));
                this.speed = Mth.approach(this.speed, topSpeed, acceleration);
            } else {
                this.speed = Mth.approach(
                        this.speed,
                        committingSwoop ? 0.85F : 0.2F,
                        committingSwoop ? 0.060F : 0.025F);
            }

            float wantedPitch = (float) (-(Mth.atan2(-tdy, sd) * 180.0F / (float) Math.PI));
            this.wolf.setXRot(wantedPitch);

            float moveAngle = this.wolf.getYRot() + 90.0F;
            double txd = this.speed
                    * Mth.cos(moveAngle * (float) (Math.PI / 180.0D))
                    * Math.abs(tdx / sd2);
            double tzd = this.speed
                    * Mth.sin(moveAngle * (float) (Math.PI / 180.0D))
                    * Math.abs(tdz / sd2);
            double tyd = this.speed
                    * Mth.sin(wantedPitch * (float) (Math.PI / 180.0D))
                    * Math.abs(tdy / sd2);

            Vec3 movement = this.wolf.getDeltaMovement();
            this.wolf.setDeltaMovement(
                    movement.add(new Vec3(txd, tyd, tzd).subtract(movement).scale(0.2D)));
        }
    }

    private abstract class PhantomWolfMoveTargetGoal extends Goal {
        private PhantomWolfMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return !PhantomWolf.this.hasFlightMoveTarget
                    || PhantomWolf.this.moveTargetPoint.distanceToSqr(
                    PhantomWolf.this.getX(),
                    PhantomWolf.this.getY(),
                    PhantomWolf.this.getZ()) < 4.0D;
        }
    }

    private boolean isWolfLureItem(ItemStack stack) {
        return stack.is(Items.BONE) || this.isFood(stack);
    }

    private boolean playerOffersWolfLure(Player player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && (this.isWolfLureItem(player.getMainHandItem())
                || this.isWolfLureItem(player.getOffhandItem()));
    }

    private @Nullable Player findNearestWolfLurePlayer() {
        return this.level().getEntitiesOfClass(
                        Player.class,
                        this.getBoundingBox().inflate(FOOD_LURE_RANGE, FOOD_LURE_RANGE, FOOD_LURE_RANGE),
                        this::playerOffersWolfLure)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /**
     * Survival-friendly taming approach.
     *
     * <p>An untamed Phantom Wolf that sees a nearby player holding a bone or
     * normal wolf food flies down beside that player, lands, and stays there
     * while the lure remains visible. It never consumes the item and never
     * bypasses vanilla taming: the player still has to right-click with a bone.</p>
     */
    private final class PhantomWolfFoodLureGoal extends PhantomWolfMoveTargetGoal {
        private @Nullable Player temptingPlayer;
        private int repathTick;

        @Override
        public boolean canUse() {
            if (PhantomWolf.this.isTame()
                    || PhantomWolf.this.isOrderedToSit()
                    || PhantomWolf.this.isPassenger()
                    || PhantomWolf.this.getTarget() != null) {
                return false;
            }

            Player player = PhantomWolf.this.findNearestWolfLurePlayer();
            if (player == null || PhantomWolf.this.distanceToSqr(player) > FOOD_LURE_RANGE_SQR) {
                return false;
            }

            this.temptingPlayer = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.temptingPlayer != null
                    && !PhantomWolf.this.isTame()
                    && !PhantomWolf.this.isOrderedToSit()
                    && !PhantomWolf.this.isPassenger()
                    && PhantomWolf.this.getTarget() == null
                    && PhantomWolf.this.playerOffersWolfLure(this.temptingPlayer)
                    && PhantomWolf.this.distanceToSqr(this.temptingPlayer) <= FOOD_LURE_RANGE_SQR;
        }

        @Override
        public void start() {
            PhantomWolf.this.foodLureActive = true;
            PhantomWolf.this.foodLureFinalLanding = false;
            PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
            PhantomWolf.this.getNavigation().stop();
            this.repathTick = 0;
        }

        @Override
        public void stop() {
            PhantomWolf.this.clearFlightMoveTarget();
            PhantomWolf.this.getNavigation().stop();
            PhantomWolf.this.foodLureActive = false;
            PhantomWolf.this.foodLureFinalLanding = false;
            this.temptingPlayer = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player player = this.temptingPlayer;
            if (player == null) {
                return;
            }

            // Land beside the player instead of inside their hitbox. The side is
            // deterministic per wolf so several wild Phantom Wolves do not all
            // stack on precisely the same block.
            double yaw = player.getYRot() * (Math.PI / 180.0D);
            double side = (PhantomWolf.this.getId() & 1) == 0
                    ? FOOD_LURE_LANDING_OFFSET
                    : -FOOD_LURE_LANDING_OFFSET;

            double landingX = player.getX() + Math.cos(yaw) * side;
            double landingZ = player.getZ() + Math.sin(yaw) * side;

            BlockPos landingColumn = BlockPos.containing(
                    landingX,
                    player.getY(),
                    landingZ);
            BlockPos ground = PhantomWolf.this.level().getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    landingColumn);

            double horizontalDx = PhantomWolf.this.getX() - landingX;
            double horizontalDz = PhantomWolf.this.getZ() - landingZ;
            double horizontalSqr = horizontalDx * horizontalDx + horizontalDz * horizontalDz;

            if (PhantomWolf.this.onGround() && horizontalSqr <= 3.25D * 3.25D) {
                PhantomWolf.this.foodLureFinalLanding = true;
                PhantomWolf.this.clearFlightMoveTarget();
                PhantomWolf.this.getNavigation().stop();
                PhantomWolf.this.setNoGravity(false);
                PhantomWolf.this.setDeltaMovement(Vec3.ZERO);
                PhantomWolf.this.hurtMarked = true;
                return;
            }

            double heightAboveLanding = PhantomWolf.this.getY() - ground.getY();
            if (horizontalSqr <= 2.75D * 2.75D
                    && heightAboveLanding <= FOOD_LURE_FINAL_DESCENT_HEIGHT) {
                PhantomWolf.this.foodLureFinalLanding = true;
                PhantomWolf.this.clearFlightMoveTarget();
                PhantomWolf.this.getNavigation().stop();
                PhantomWolf.this.setNoGravity(false);

                Vec3 motion = PhantomWolf.this.getDeltaMovement();
                PhantomWolf.this.setDeltaMovement(
                        motion.x * 0.15D,
                        Math.min(motion.y, -0.08D),
                        motion.z * 0.15D);
                PhantomWolf.this.hurtMarked = true;
                return;
            }

            PhantomWolf.this.foodLureFinalLanding = false;
            PhantomWolf.this.setNoGravity(true);

            if (--this.repathTick <= 0 || this.touchingTarget()) {
                this.repathTick = 5;

                // Approach low. The Phantom motor remains responsible for the
                // long descent, but the destination itself sits just above the
                // landing surface instead of around the player's head.
                PhantomWolf.this.setFlightMoveTarget(new Vec3(
                        landingX,
                        ground.getY() + 0.55D,
                        landingZ));
            }
        }
    }

    /**
     * Ordered sitting means land first. Once on the ground vanilla Wolf sitting
     * takes over normally.
     */
    private final class PhantomWolfLandingGoal extends PhantomWolfMoveTargetGoal {
        private int repathTick;

        @Override
        public boolean canUse() {
            return PhantomWolf.this.isOrderedToSit()
                    && !PhantomWolf.this.onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.repathTick = 0;
            PhantomWolf.this.getNavigation().stop();
            this.selectLandingPoint();
        }

        @Override
        public void tick() {
            BlockPos ground = PhantomWolf.this.level().getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    PhantomWolf.this.blockPosition());

            // Once close enough to the landing surface, stop asking the Phantom
            // motor to hover at a destination and let restored gravity finish
            // the final fraction of a block.
            if (PhantomWolf.this.getY() <= ground.getY() + 0.70D) {
                PhantomWolf.this.clearFlightMoveTarget();
                PhantomWolf.this.getNavigation().stop();
                PhantomWolf.this.setNoGravity(false);

                Vec3 motion = PhantomWolf.this.getDeltaMovement();
                PhantomWolf.this.setDeltaMovement(
                        motion.x * 0.15D,
                        Math.min(motion.y, -0.08D),
                        motion.z * 0.15D);
                PhantomWolf.this.hurtMarked = true;
                return;
            }

            if (--this.repathTick <= 0 || this.touchingTarget()) {
                this.repathTick = 8;
                this.selectLandingPoint();
            }
        }

        @Override
        public void stop() {
            PhantomWolf.this.clearFlightMoveTarget();
            PhantomWolf.this.getNavigation().stop();

            if (PhantomWolf.this.onGround() && PhantomWolf.this.isOrderedToSit()) {
                PhantomWolf.this.setNoGravity(false);
                PhantomWolf.this.setInSittingPose(true);
                PhantomWolf.this.setXRot(0.0F);
                PhantomWolf.this.setDeltaMovement(Vec3.ZERO);
                PhantomWolf.this.hurtMarked = true;
            }
        }

        private void selectLandingPoint() {
            BlockPos ground = PhantomWolf.this.level().getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    PhantomWolf.this.blockPosition());

            // Heightmap position is already the standing surface position. The
            // old +1.0 target was literally telling her to stop a block above it.
            double xOffset = ((PhantomWolf.this.getId() & 1) == 0 ? 0.20D : -0.20D);
            PhantomWolf.this.setFlightMoveTarget(new Vec3(
                    ground.getX() + 0.5D + xOffset,
                    ground.getY() + 0.05D,
                    ground.getZ() + 0.5D));
        }
    }

    /**
     * Wolfism combat commitment timer: establish aerial position, seize a good
     * opening quickly, then repeat after a short recovery/circle rhythm.
     */
    private final class PhantomWolfAttackStrategyGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            return PhantomWolf.this.canUsePhantomCombatFlight();
        }

        @Override
        public boolean canContinueToUse() {
            return PhantomWolf.this.canUsePhantomCombatFlight();
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(INITIAL_SWOOP_DELAY_TICKS);
            PhantomWolf.this.swoopStartY = Double.NaN;

            if (PhantomWolf.this.attackPhase != AttackPhase.RECOVER) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
                PhantomWolf.this.setAnchorAboveTarget();
            }
        }

        @Override
        public void stop() {
            if (PhantomWolf.this.attackPhase != AttackPhase.RECOVER) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
            }

            PhantomWolf.this.swoopStartY = Double.NaN;

            LivingEntity owner = PhantomWolf.this.getOwner();
            if (PhantomWolf.this.isTame() && owner != null && owner.isAlive()) {
                PhantomWolf.this.anchorPoint = owner.blockPosition().above(2);
            } else {
                PhantomWolf.this.anchorPoint = PhantomWolf.this.blockPosition().above(5);
            }
        }

        @Override
        public void tick() {
            if (PhantomWolf.this.attackPhase != AttackPhase.CIRCLE) {
                return;
            }

            LivingEntity target = PhantomWolf.this.getTarget();
            if (target == null) {
                return;
            }

            if (PhantomWolf.this.isCriticallyWounded()) {
                // Stay combat-aware but do not suicide-dive at critical health.
                PhantomWolf.this.setFlightMoveTarget(new Vec3(
                        PhantomWolf.this.getX(),
                        target.getY() + target.getBbHeight() + COMBAT_RECOVERY_CLEARANCE,
                        PhantomWolf.this.getZ()));
                this.nextSweepTick = Math.max(this.nextSweepTick, 8);
                return;
            }

            --this.nextSweepTick;

            double horizontalSqr =
                    Mth.square(target.getX() - PhantomWolf.this.getX())
                            + Mth.square(target.getZ() - PhantomWolf.this.getZ());

            /*
             * Opportunity attack:
             * once Phantom has a good high position and is already reasonably
             * close to the target, do not keep orbiting just because an arbitrary
             * timer still has two seconds left.
             */
            boolean goodOpening = PhantomWolf.this.getY()
                    >= PhantomWolf.this.minimumSwoopStartY(target)
                    && horizontalSqr <= 9.0D * 9.0D;

            if (goodOpening && this.nextSweepTick > 4) {
                this.nextSweepTick = Math.min(this.nextSweepTick, 4);
            }

            if (this.nextSweepTick > 0) {
                return;
            }

            // Reach genuine aerial altitude first, but only postpone briefly.
            if (PhantomWolf.this.getY()
                    < PhantomWolf.this.minimumSwoopStartY(target)) {
                PhantomWolf.this.setFlightMoveTarget(new Vec3(
                        PhantomWolf.this.getX(),
                        PhantomWolf.this.combatSafeOrbitY(target),
                        PhantomWolf.this.getZ()));
                this.nextSweepTick = this.adjustedTickDelay(4);
                return;
            }

            PhantomWolf.this.swoopStartY = PhantomWolf.this.getY();
            PhantomWolf.this.attackPhase = AttackPhase.SWOOP;
            PhantomWolf.this.setAnchorAboveTarget();

            if (PhantomWolf.this.isSkyHunterActive()) {
                this.nextSweepTick = this.adjustedTickDelay(
                        SKY_HUNTER_SWOOP_DELAY_MIN_TICKS
                                + PhantomWolf.this.random.nextInt(
                                SKY_HUNTER_SWOOP_DELAY_RANDOM_TICKS));
            } else {
                this.nextSweepTick = this.adjustedTickDelay(
                        NORMAL_SWOOP_DELAY_MIN_TICKS
                                + PhantomWolf.this.random.nextInt(
                                NORMAL_SWOOP_DELAY_RANDOM_TICKS));
            }

            PhantomWolf.this.playSound(
                    SoundEvents.PHANTOM_SWOOP,
                    4.0F,
                    0.95F + PhantomWolf.this.random.nextFloat() * 0.1F);
        }
    }

    /**
     * Vanilla Phantom's physical sweep, but it keeps Wolfism's target after a
     * hit so family/owner combat does not get silently cancelled.
     */
    private final class PhantomWolfSweepAttackGoal extends PhantomWolfMoveTargetGoal {
        private int swoopTicks;

        @Override
        public boolean canUse() {
            return PhantomWolf.this.canUsePhantomCombatFlight()
                    && PhantomWolf.this.attackPhase == AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.swoopTicks = 0;
            PhantomWolf.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            // If tick() intentionally entered RECOVER, do not immediately
            // destroy that phase when this SWOOP goal stops.
            if (PhantomWolf.this.attackPhase == AttackPhase.SWOOP) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
            }

            PhantomWolf.this.swoopStartY = Double.NaN;
        }

        @Override
        public void tick() {
            LivingEntity target = PhantomWolf.this.getTarget();
            if (target == null) {
                return;
            }

            ++this.swoopTicks;

            Vec3 aim = PhantomWolf.this.predictSwoopAimPoint(target);
            PhantomWolf.this.setFlightMoveTarget(aim);

            double horizontalSqr = Mth.square(target.getX() - PhantomWolf.this.getX())
                    + Mth.square(target.getZ() - PhantomWolf.this.getZ());

            /*
             * COMMITMENT RULE:
             * Once the dive begins, chase the opening. Do not abort merely
             * because the victim moved 4-5 blocks or landed one hit.
             *
             * Only abandon a low attack lane when the target has genuinely
             * escaped far away, or if the entire swoop has timed out.
             */
            boolean targetEscapedAttackLane =
                    PhantomWolf.this.getY()
                            < target.getY() + target.getBbHeight() + 0.90D
                            && horizontalSqr > 8.0D * 8.0D;

            if (targetEscapedAttackLane
                    || this.swoopTicks >= MAX_SWOOP_COMMIT_TICKS) {
                PhantomWolf.this.swoopStartY = Double.NaN;
                PhantomWolf.this.beginPostSwoopRecovery(target);
                return;
            }

            if (PhantomWolf.this.getBoundingBox().inflate(0.35F).intersects(target.getBoundingBox())) {
                if (PhantomWolf.this.level() instanceof ServerLevel serverLevel) {
                    PhantomWolf.this.performSwoopImpact(serverLevel, target);
                }

                PhantomWolf.this.swoopStartY = Double.NaN;
                PhantomWolf.this.beginPostSwoopRecovery(target);

                if (!PhantomWolf.this.isSilent()) {
                    PhantomWolf.this.level().levelEvent(1039, PhantomWolf.this.blockPosition(), 0);
                }
            } else if (PhantomWolf.this.horizontalCollision) {
                // Physical obstruction is a legitimate reason to disengage.
                // Ordinary retaliation damage is NOT; a healthy committed
                // Phantom now finishes the dive instead of instantly feinting.
                PhantomWolf.this.swoopStartY = Double.NaN;
                PhantomWolf.this.beginPostSwoopRecovery(target);
            }
        }
    }

    /**
     * Real post-attack recovery phase.
     *
     * <p>V8 only applied one escape impulse. V9 keeps ownership of the MOVE
     * channel until Phantom has genuinely rebuilt altitude and separation,
     * preventing the ordinary circle goal from immediately overwriting the
     * escape path.</p>
     */
    private final class PhantomWolfRecoveryGoal extends PhantomWolfMoveTargetGoal {
        private int recoveryTicks;

        @Override
        public boolean canUse() {
            return PhantomWolf.this.canUsePhantomCombatFlight()
                    && PhantomWolf.this.attackPhase == AttackPhase.RECOVER;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.canUse()) {
                return false;
            }

            // Critical Phantom remains evasive until healed above 30% or the
            // combat target disappears. Healthy recovery remains short.
            return PhantomWolf.this.isCriticallyWounded()
                    || this.recoveryTicks < 24;
        }

        @Override
        public void start() {
            this.recoveryTicks = 0;
            PhantomWolf.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            if (PhantomWolf.this.attackPhase == AttackPhase.RECOVER) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
                PhantomWolf.this.setAnchorAboveTarget();
            }
        }

        @Override
        public void tick() {
            LivingEntity target = PhantomWolf.this.getTarget();
            if (target == null) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
                return;
            }

            ++this.recoveryTicks;
            PhantomWolf.this.maintainRecoveryTarget(target);

            double safeY = target.getY()
                    + target.getBbHeight()
                    + COMBAT_THREAT_CLEARANCE;

            double dx = PhantomWolf.this.getX() - target.getX();
            double dz = PhantomWolf.this.getZ() - target.getZ();
            double horizontalSqr = dx * dx + dz * dz;

            if (!PhantomWolf.this.isCriticallyWounded()
                    && this.recoveryTicks >= 7
                    && PhantomWolf.this.getY() >= safeY
                    && horizontalSqr >= 5.0D * 5.0D) {
                PhantomWolf.this.attackPhase = AttackPhase.CIRCLE;
                PhantomWolf.this.setAnchorAboveTarget();
            }
        }
    }

    /**
     * After the final enemy is gone, actively come back down to companion
     * height instead of leaving the wolf stranded at combat altitude.
     */
    private final class PhantomWolfPostCombatReturnGoal extends PhantomWolfMoveTargetGoal {
        private @Nullable LivingEntity owner;

        @Override
        public boolean canUse() {
            if (PhantomWolf.this.postCombatReturnTicks <= 0
                    || PhantomWolf.this.getTarget() != null
                    || !PhantomWolf.this.isTame()
                    || PhantomWolf.this.isOrderedToSit()) {
                return false;
            }

            LivingEntity candidate = PhantomWolf.this.getOwner();
            if (candidate == null || !candidate.isAlive()) {
                return false;
            }

            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null
                    && this.owner.isAlive()
                    && PhantomWolf.this.postCombatReturnTicks > 0
                    && PhantomWolf.this.getTarget() == null
                    && !PhantomWolf.this.isOrderedToSit();
        }

        @Override
        public void start() {
            PhantomWolf.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            PhantomWolf.this.postCombatReturnTicks = 0;
            if (this.owner != null && this.owner.isAlive()) {
                PhantomWolf.this.anchorPoint = this.owner.blockPosition().above(1);
            }
            this.owner = null;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }

            double side = (PhantomWolf.this.getId() & 1) == 0 ? 2.0D : -2.0D;
            double hoverY = PhantomWolf.this.ownerCompanionHoverY(this.owner);

            PhantomWolf.this.setFlightMoveTarget(new Vec3(
                    this.owner.getX() + side,
                    hoverY,
                    this.owner.getZ()));

            double horizontalSqr =
                    Mth.square(PhantomWolf.this.getX() - (this.owner.getX() + side))
                            + Mth.square(PhantomWolf.this.getZ() - this.owner.getZ());

            if (horizontalSqr <= 3.0D * 3.0D
                    && Math.abs(PhantomWolf.this.getY() - hoverY) <= 1.25D) {
                PhantomWolf.this.postCombatReturnTicks = 0;
            }
        }
    }

    /**
     * Tamed flight follow. Vanilla FollowOwnerGoal remains available as a
     * fallback, but this higher-priority goal supplies a true XYZ destination
     * and keeps the wolf a few blocks above the owner's position.
     */
    private final class PhantomWolfFollowOwnerGoal extends PhantomWolfMoveTargetGoal {
        private @Nullable LivingEntity owner;

        @Override
        public boolean canUse() {
            if (!PhantomWolf.this.isTame()
                    || PhantomWolf.this.isOrderedToSit()
                    || PhantomWolf.this.getTarget() != null) {
                return false;
            }

            LivingEntity candidate = PhantomWolf.this.getOwner();
            if (candidate == null || !candidate.isAlive()) {
                return false;
            }

            if (PhantomWolf.this.distanceToSqr(candidate) <= OWNER_FOLLOW_START_DISTANCE_SQR) {
                return false;
            }

            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null
                    && this.owner.isAlive()
                    && !PhantomWolf.this.isOrderedToSit()
                    && PhantomWolf.this.getTarget() == null
                    && PhantomWolf.this.distanceToSqr(this.owner) > OWNER_FOLLOW_STOP_DISTANCE_SQR;
        }

        @Override
        public void start() {
            PhantomWolf.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            if (this.owner != null && this.owner.isAlive()) {
                PhantomWolf.this.anchorPoint = this.owner.blockPosition().above(1);
            }
            this.owner = null;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }

            double hoverY = PhantomWolf.this.ownerCompanionHoverY(this.owner);
            PhantomWolf.this.setFlightMoveTarget(
                    new Vec3(this.owner.getX(), hoverY, this.owner.getZ()));
        }
    }

    /**
     * Vanilla Phantom's anchor circle, with one Wolfism adaptation: a nearby
     * owner becomes the anchor for a tame wolf, so it naturally orbits its
     * family instead of wandering away from its spawn location.
     */
    private final class PhantomWolfCircleAroundAnchorGoal extends PhantomWolfMoveTargetGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() {
            return !PhantomWolf.this.isOrderedToSit()
                    && !PhantomWolf.this.foodLureActive
                    && (PhantomWolf.this.getTarget() == null
                    || PhantomWolf.this.attackPhase == AttackPhase.CIRCLE);
        }

        @Override
        public void start() {
            LivingEntity target = PhantomWolf.this.getTarget();
            LivingEntity owner = PhantomWolf.this.getOwner();

            if (target != null) {
                this.distance = (float) (COMBAT_ORBIT_MIN_RADIUS
                        + PhantomWolf.this.random.nextDouble()
                        * (COMBAT_ORBIT_MAX_RADIUS - COMBAT_ORBIT_MIN_RADIUS));
                this.height = -0.45F + PhantomWolf.this.random.nextFloat() * 0.90F;
            } else if (PhantomWolf.this.isTame() && owner != null && owner.isAlive()) {
                this.distance = (float) (OWNER_IDLE_ORBIT_MIN_RADIUS
                        + PhantomWolf.this.random.nextDouble()
                        * (OWNER_IDLE_ORBIT_MAX_RADIUS - OWNER_IDLE_ORBIT_MIN_RADIUS));
                this.height = -0.22F + PhantomWolf.this.random.nextFloat() * 0.44F;
            } else {
                this.distance = 5.0F + PhantomWolf.this.random.nextFloat() * 10.0F;
                this.height = -4.0F + PhantomWolf.this.random.nextFloat() * 9.0F;
            }

            this.clockwise = PhantomWolf.this.random.nextBoolean() ? 1.0F : -1.0F;
            PhantomWolf.this.getNavigation().stop();
            this.updateAnchorFromOwner();
            this.selectNext();
        }

        @Override
        public void tick() {
            this.updateAnchorFromOwner();

            if (PhantomWolf.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                LivingEntity target = PhantomWolf.this.getTarget();
                LivingEntity owner = PhantomWolf.this.getOwner();

                if (target != null) {
                    this.height = -0.45F + PhantomWolf.this.random.nextFloat() * 0.90F;
                } else if (PhantomWolf.this.isTame() && owner != null && owner.isAlive()) {
                    this.height = -0.22F + PhantomWolf.this.random.nextFloat() * 0.44F;
                } else {
                    this.height = -4.0F + PhantomWolf.this.random.nextFloat() * 9.0F;
                }
            }

            if (PhantomWolf.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                LivingEntity target = PhantomWolf.this.getTarget();
                LivingEntity owner = PhantomWolf.this.getOwner();

                if (target != null) {
                    this.distance += 0.5F;
                    if (this.distance > COMBAT_ORBIT_MAX_RADIUS) {
                        this.distance = (float) COMBAT_ORBIT_MIN_RADIUS;
                        this.clockwise = -this.clockwise;
                    }
                } else if (PhantomWolf.this.isTame() && owner != null && owner.isAlive()) {
                    this.distance += 0.35F;
                    if (this.distance > OWNER_IDLE_ORBIT_MAX_RADIUS) {
                        this.distance = (float) OWNER_IDLE_ORBIT_MIN_RADIUS;
                        this.clockwise = -this.clockwise;
                    }
                } else {
                    ++this.distance;
                    if (this.distance > 15.0F) {
                        this.distance = 5.0F;
                        this.clockwise = -this.clockwise;
                    }
                }
            }

            if (PhantomWolf.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = PhantomWolf.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (PhantomWolf.this.moveTargetPoint.y < PhantomWolf.this.getY()
                    && !PhantomWolf.this.level().isEmptyBlock(PhantomWolf.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (PhantomWolf.this.moveTargetPoint.y > PhantomWolf.this.getY()
                    && !PhantomWolf.this.level().isEmptyBlock(PhantomWolf.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }
        }

        private void updateAnchorFromOwner() {
            LivingEntity owner = PhantomWolf.this.getOwner();
            if (PhantomWolf.this.isTame()
                    && owner != null
                    && owner.isAlive()
                    && PhantomWolf.this.distanceToSqr(owner) <= OWNER_CIRCLE_MAX_DISTANCE_SQR
                    && PhantomWolf.this.getTarget() == null) {
                PhantomWolf.this.anchorPoint = owner.blockPosition().above(1);
            }
        }

        private void selectNext() {
            this.angle += this.clockwise * 15.0F * (float) (Math.PI / 180.0D);

            LivingEntity target = PhantomWolf.this.getTarget();
            if (target != null && PhantomWolf.this.attackPhase == AttackPhase.CIRCLE) {
                // Intelligent combat orbit: remain above melee reach and keep
                // moving laterally. Ground enemies get more clearance than
                // already-airborne targets.
                double safeY = PhantomWolf.this.combatSafeOrbitY(target) + this.height;
                PhantomWolf.this.setFlightMoveTarget(new Vec3(
                        target.getX() + this.distance * Mth.cos(this.angle),
                        safeY,
                        target.getZ() + this.distance * Mth.sin(this.angle)));
                return;
            }

            LivingEntity owner = PhantomWolf.this.getOwner();
            if (PhantomWolf.this.isTame()
                    && owner != null
                    && owner.isAlive()
                    && PhantomWolf.this.distanceToSqr(owner) <= OWNER_CIRCLE_MAX_DISTANCE_SQR) {
                // Companion orbit: stay around chest height and much closer to
                // the owner instead of circling five-plus blocks overhead.
                double companionY = PhantomWolf.this.ownerCompanionHoverY(owner) + this.height;
                PhantomWolf.this.setFlightMoveTarget(new Vec3(
                        owner.getX() + this.distance * Mth.cos(this.angle),
                        companionY,
                        owner.getZ() + this.distance * Mth.sin(this.angle)));
                return;
            }

            if (PhantomWolf.this.anchorPoint == null) {
                PhantomWolf.this.anchorPoint = PhantomWolf.this.blockPosition().above(5);
            }

            PhantomWolf.this.setFlightMoveTarget(
                    Vec3.atLowerCornerOf(PhantomWolf.this.anchorPoint)
                            .add(
                                    this.distance * Mth.cos(this.angle),
                                    -4.0F + this.height,
                                    this.distance * Mth.sin(this.angle)));
        }
    }
}
