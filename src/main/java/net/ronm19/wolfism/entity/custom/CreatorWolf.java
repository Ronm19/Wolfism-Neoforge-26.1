package net.ronm19.wolfism.entity.custom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.creator.CreatorProgress;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/**
 * Secret Wolfism #53 — Creator Wolf ♂, six-star demi-god / adaptive all-rounder.
 *
 * <p>Creator is intentionally not the best specialist at every niche. His power
 * is broad awareness, reaction, adaptation, intervention and making the rest of
 * the family perform closer to their own ceiling.</p>
 *
 * <p>This V1 is the gameplay foundation. The four-wolf survival progression
 * gate / predictable base arrival is deliberately a separate integration pass.
 * Creative spawn egg and /summon remain available for testing.</p>
 */
public final class CreatorWolf extends AbstractWolfismWolf {

    /*
     * SECRET IDENTITY
     *
     * Before the first completed Bone -> Eye bond, the player should not be
     * handed the words "Creator Wolf" by UI text. The reveal happens when the
     * Eye of Ender completes the bond.
     */
    private static final String UNDISCOVERED_IDENTITY_TEXT = "K'RÆ-ØV";

    private static Component undiscoveredIdentityName() {
        return Component.literal(UNDISCOVERED_IDENTITY_TEXT)
                .withStyle(ChatFormatting.BLACK);
    }

    private static Component revealedCreatorName() {
        return Component.translatable("entity.wolfism.creator_wolf")
                .withStyle(ChatFormatting.BLACK);
    }

    // ---------------------------------------------------------------------
    // Six-star baseline — high, but not "largest number wins" design.
    // ---------------------------------------------------------------------

    private static final double MAX_HEALTH = 80.0D;
    private static final double ATTACK_DAMAGE = 10.0D;
    private static final double MOVE_SPEED = 0.36D;
    private static final double ARMOR = 8.0D;
    private static final double FOLLOW_RANGE = 64.0D;
    private static final double KNOCKBACK_RESISTANCE = 0.45D;

    public static final double AWARENESS_RADIUS = 56.0D;
    public static final double WILL_AWARENESS_RADIUS = 72.0D;
    public static final double FAMILY_RADIUS = 26.0D;

    /*
     * Physical aggro range is now enforced globally by AbstractWolfismWolf.
     * Creator keeps only his family-sharing radius here; Creator Beam uses its
     * own ranged candidate and never needs to turn distant awareness into a
     * physical chase target.
     */
    private static final double PRESENCE_SHARE_RADIUS = 12.0D;

    private static final int DECISION_INTERVAL = 2;

    // Creator Beam — intentionally frequent enough to pressure ranged/flying threats.
    private static final int BEAM_COOLDOWN_TICKS = 20 * 6;
    private static final int BEAM_HIGH_THREAT_COOLDOWN_TICKS = 20 * 3 + 10;
    private static final int BEAM_CREEPER_COOLDOWN_TICKS = 20 * 3;
    private static final int BEAM_EMPOWERED_COOLDOWN_TICKS = 20 * 2 + 10;
    private static final int BEAM_VISUAL_TICKS = 10;
    private static final double BEAM_MIN_RANGE = 3.0D;
    private static final double BEAM_MAX_RANGE = 48.0D;
    private static final float BEAM_BASE_DAMAGE = 22.0F;
    private static final float CREEPER_BEAM_BONUS_DAMAGE = 12.0F;
    private static final float CREEPER_BITE_BONUS_DAMAGE = 16.0F;

    // Creator's Touch
    private static final int TOUCH_COOLDOWN_TICKS = 20 * 22;
    private static final int TOUCH_DURATION_TICKS = 20 * 12;
    private static final double TOUCH_RADIUS = 18.0D;

    // Creator's Intervention
    private static final int INTERVENTION_COOLDOWN_TICKS = 20 * 18;
    private static final double INTERVENTION_RADIUS = 28.0D;

    // Perfect Instinct
    private static final int PERFECT_INSTINCT_DURATION_TICKS = 20 * 10;
    private static final int PERFECT_INSTINCT_COOLDOWN_TICKS = 20 * 45;

    // Creator's Will
    private static final int CREATOR_WILL_DURATION_TICKS = 20 * 15;
    private static final int CREATOR_WILL_COOLDOWN_TICKS = 20 * 90;
    private static final double CREATOR_WILL_RADIUS = 22.0D;

    // Recovery State — Creator never permanently dies through combat damage.
    private static final int RECOVERY_DURATION_TICKS = 20 * 5;
    private static final float RECOVERY_RETURN_HEALTH_RATIO = 0.65F;

    // Adaptive Mastery
    private static final int ADAPTATION_DECAY_TICKS = 20 * 12;
    private static final int MAX_ADAPTATION_REPEATS = 4;

    // Mastered Instinct terrain escape — only used when combat movement is genuinely boxed in.
    private static final int INSTINCT_ESCAPE_COOLDOWN_TICKS = 20 * 3;
    private static final int INSTINCT_ESCAPE_SEARCH_RADIUS = 6;

    private static final Identifier PERFECT_SPEED =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_perfect_speed");
    private static final Identifier PERFECT_ARMOR =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_perfect_armor");
    private static final Identifier PERFECT_KB =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_perfect_knockback");
    private static final Identifier WILL_SPEED =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_will_speed");
    private static final Identifier WILL_DAMAGE =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_will_damage");
    private static final Identifier WILL_ARMOR =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "creator_will_armor");

    /** Client-visible state for Creator's short-lived true beacon beam. */
    private static final EntityDataAccessor<Boolean> DATA_CREATOR_BEAM_ACTIVE =
            SynchedEntityData.defineId(CreatorWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CREATOR_BEAM_TARGET_ID =
            SynchedEntityData.defineId(CreatorWolf.class, EntityDataSerializers.INT);

    private int beamCooldownTicks;
    private int beamVisualTicks;
    private int touchCooldownTicks;
    private int interventionCooldownTicks;
    private int instinctiveCounterCooldownTicks;
    private int perfectInstinctTicks;
    private int perfectInstinctCooldownTicks;
    private int creatorWillTicks;
    private int creatorWillCooldownTicks;
    private int recoveryTicks;
    private int creatorGrowlCooldownTicks;
    private int instinctEscapeCooldownTicks;

    private boolean waitingForBond = true;
    private boolean boneAccepted;
    private boolean identityRevealed;
    private boolean reacquisitionEncounter;
    private UUID unlockPlayerUuid;

    private UUID adaptiveAttacker;
    private int adaptivePattern;
    private int adaptationRepeats;
    private int adaptationDecayTicks;

    private LivingEntity committedTarget;
    private int targetCommitmentTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<CreatorWolf> PROVIDER =
                Brain.<CreatorWolf>provider(
                        List.of(
                                ModSensorTypes.CREATOR_AWARENESS.get(),
                                ModSensorTypes.CREATOR_FAMILY.get()),
                        wolf -> List.of());
    }

    public CreatorWolf(EntityType<? extends CreatorWolf> type, Level level) {
        super(type, level);
        this.setCustomName(undiscoveredIdentityName());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CREATOR_BEAM_ACTIVE, false);
        entityData.define(DATA_CREATOR_BEAM_TARGET_ID, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.CREATOR_WOLF.get();
    }

    @Override
    protected Brain<CreatorWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<CreatorWolf> getBrain() {
        return (Brain<CreatorWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        setBase(Attributes.MAX_HEALTH, MAX_HEALTH);
        setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
        setBase(Attributes.MOVEMENT_SPEED, MOVE_SPEED);
        setBase(Attributes.ARMOR, ARMOR);
        setBase(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
        setBase(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);

        if (this.isTame()) {
            this.waitingForBond = false;
            this.boneAccepted = true;
            this.setHealth((float) MAX_HEALTH);
        }
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    // ---------------------------------------------------------------------
    // Two-stage Creator bond — Bone -> Eye of Ender.
    // ---------------------------------------------------------------------

    private String creatorIdentityMessageKey(String unknownKey, String revealedKey) {
        return this.identityRevealed ? revealedKey : unknownKey;
    }

    private void revealCreatorIdentity() {
        this.identityRevealed = true;
        this.setCustomName(revealedCreatorName());
    }

    private void applyUndiscoveredIdentity() {
        this.identityRevealed = false;
        this.setCustomName(undiscoveredIdentityName());
    }

    public boolean isCreatorIdentityRevealed() {
        return this.identityRevealed;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && !this.isBaby()) {
            if (this.unlockPlayerUuid != null
                    && !this.unlockPlayerUuid.equals(player.getUUID())) {
                if (!this.level().isClientSide()) {
                    player.sendOverlayMessage(
                            Component.translatable(creatorIdentityMessageKey(
                                    "message.wolfism.creator_rejects_other_unknown",
                                    "message.wolfism.creator_rejects_other")));
                }
                return InteractionResult.SUCCESS;
            }

            if (stack.is(Items.BONE)) {
                if (!this.level().isClientSide()) {
                    if (!this.boneAccepted) {
                        this.boneAccepted = true;
                        if (!player.getAbilities().instabuild) stack.shrink(1);

                        if (this.level() instanceof ServerLevel level) {
                            level.sendParticles(
                                    ParticleTypes.ENCHANT,
                                    this.getX(), this.getY(0.65D), this.getZ(),
                                    20, 0.55D, 0.42D, 0.55D, 0.04D);
                        }
                    }

                    player.sendOverlayMessage(
                            Component.translatable(creatorIdentityMessageKey(
                                    "message.wolfism.creator_needs_eye_unknown",
                                    "message.wolfism.creator_needs_eye")));
                }
                return InteractionResult.SUCCESS;
            }

            if (stack.is(Items.ENDER_EYE)) {
                if (!this.level().isClientSide()) {
                    if (!this.boneAccepted) {
                        player.sendOverlayMessage(
                                Component.translatable(creatorIdentityMessageKey(
                                        "message.wolfism.creator_needs_bone_unknown",
                                        "message.wolfism.creator_needs_bone")));
                    } else {
                        if (!player.getAbilities().instabuild) stack.shrink(1);
                        this.waitingForBond = false;
                        this.revealCreatorIdentity();
                        this.tame(player); // reveal -> global family welcome says "Creator Wolf".
                        this.setHealth(this.getMaxHealth());

                        if (this.level() instanceof ServerLevel level) {
                            level.sendParticles(
                                    ParticleTypes.END_ROD,
                                    this.getX(), this.getY(0.70D), this.getZ(),
                                    34, 0.75D, 0.55D, 0.75D, 0.055D);
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return super.mobInteract(player, hand);
    }

    public boolean isWaitingForBond() {
        return this.waitingForBond && !this.isTame();
    }

    public boolean hasAcceptedBone() {
        return this.boneAccepted;
    }

    /**
     * Marks a legitimate survival encounter. Creator stays stationary and
     * invulnerable until the bound player performs Bone -> Eye of Ender.
     */
    public void prepareUnlockEncounter(ServerPlayer player, boolean reacquisition) {
        this.unlockPlayerUuid = player.getUUID();
        this.reacquisitionEncounter = reacquisition;
        this.waitingForBond = true;
        this.boneAccepted = false;

        if (CreatorProgress.get(player, CreatorProgress.DISCOVERED)) {
            this.revealCreatorIdentity();
        } else {
            this.applyUndiscoveredIdentity();
        }

        this.setTarget(null);
        this.getNavigation().stop();
        this.setHealth(this.getMaxHealth());
    }

    /** Developer auto-grant bypasses the survival ritual but remains owned. */
    public void prepareDeveloperGift(ServerPlayer player) {
        this.unlockPlayerUuid = player.getUUID();
        this.reacquisitionEncounter = false;
        this.waitingForBond = false;
        this.boneAccepted = true;
        this.revealCreatorIdentity();
    }

    public boolean isReacquisitionEncounter() {
        return this.reacquisitionEncounter;
    }

    public void playCreatorArrivalHowl() {
        this.playSound(
                ModSounds.CREATOR_WOLF_HOWL.value(),
                2.35F,
                0.98F + this.random.nextFloat() * 0.05F);
    }

    private void playCreatorAggressionGrowl() {
        if (this.creatorGrowlCooldownTicks > 0 || this.isWaitingForBond()) return;
        this.creatorGrowlCooldownTicks = 20 * 4;
        this.playSound(
                ModSounds.CREATOR_WOLF_GROWL.value(),
                1.85F,
                0.92F + this.random.nextFloat() * 0.10F);
    }

    /**
     * Creator is not killed; his owner deliberately dismisses him from this
     * manifestation. Player progression survives and the event layer begins
     * the structure reacquisition hunt.
     */
    public void vanishByOwner(ServerPlayer owner) {
        if (!this.isTame() || !this.isOwnedBy(owner)) return;

        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    this.getX(), this.getY(0.65D), this.getZ(),
                    38, 0.75D, 0.55D, 0.75D, 0.16D);
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(0.65D), this.getZ(),
                    18, 0.50D, 0.40D, 0.50D, 0.08D);
        }

        this.discard();
    }

    // ---------------------------------------------------------------------
    // Core server Brain loop.
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
        tickTimers();
        tickAdaptationDecay();

        if (this.isTame()) this.waitingForBond = false;

        if (this.recoveryTicks > 0) {
            tickRecovery(level);
            syncDebugMemories();
            return;
        }

        if (this.isWaitingForBond()) {
            tickWaitingState();
            syncDebugMemories();
            return;
        }

        if (this.isBaby()) {
            clearAdultCombatState();
            syncDebugMemories();
            return;
        }

        refreshStateAttributes();

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            syncDebugMemories();
            return;
        }

        LivingEntity priority = resolveCommittedThreat(getPriorityThreat());

        // Broad awareness is NOT broad aggression. Creator only physically
        // commits/chases when a threat enters close-combat range.
        if (isPotentialCreatorThreat(priority)
                && this.isWithinWolfismPhysicalAggroAcquireRange(priority)) {
            commitToThreat(priority);
            runCreationsPresence(level, priority);
        } else if (isPotentialCreatorThreat(priority)
                && isTargetingCreatorFamily(priority)) {
            // Distant family emergencies remain knowledge/ranged-intervention
            // candidates without turning into a physical chase.
            runCreationsPresence(level, priority);
        }

        if (this.creatorWillTicks > 0 && isPotentialCreatorThreat(priority)) {
            maintainCreatorWillPressure(level, priority);
        }

        if (this.tickCount % DECISION_INTERVAL == 0) {
            if (tryCreatorsIntervention(level)) {
                syncDebugMemories();
                return;
            }
            if (tryCreatorsWill(level, priority)) {
                syncDebugMemories();
                return;
            }
            if (tryPerfectInstinct(level, priority)) {
                syncDebugMemories();
                return;
            }
            if (tryCreatorsTouch(level)) {
                syncDebugMemories();
                return;
            }
            tryCreatorBeam(level, priority);
        }

        syncDebugMemories();
    }

    private void tickTimers() {
        if (beamCooldownTicks > 0) --beamCooldownTicks;
        if (beamVisualTicks > 0 && --beamVisualTicks <= 0) clearCreatorBeamVisual();
        if (touchCooldownTicks > 0) --touchCooldownTicks;
        if (interventionCooldownTicks > 0) --interventionCooldownTicks;
        if (instinctiveCounterCooldownTicks > 0) --instinctiveCounterCooldownTicks;
        if (creatorGrowlCooldownTicks > 0) --creatorGrowlCooldownTicks;
        if (instinctEscapeCooldownTicks > 0) --instinctEscapeCooldownTicks;
        if (perfectInstinctTicks > 0) --perfectInstinctTicks;
        if (perfectInstinctCooldownTicks > 0) --perfectInstinctCooldownTicks;
        if (creatorWillTicks > 0) --creatorWillTicks;
        if (creatorWillCooldownTicks > 0) --creatorWillCooldownTicks;
        if (targetCommitmentTicks > 0) --targetCommitmentTicks;

        if (targetCommitmentTicks <= 0
                || committedTarget == null
                || !isPotentialCreatorThreat(committedTarget)
                || !this.isWithinWolfismPhysicalAggroReleaseRange(committedTarget)) {
            committedTarget = null;
            targetCommitmentTicks = 0;
        }
    }

    private void syncDebugMemories() {
        Brain<CreatorWolf> brain = this.getBrain();
        brain.setMemory(ModMemoryModuleTypes.CREATOR_BEAM_COOLDOWN.get(), beamCooldownTicks);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_TOUCH_COOLDOWN.get(), touchCooldownTicks);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_INTERVENTION_COOLDOWN.get(), interventionCooldownTicks);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_PERFECT_INSTINCT_ACTIVE.get(), perfectInstinctTicks > 0);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_PERFECT_INSTINCT_COOLDOWN.get(), perfectInstinctCooldownTicks);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_WILL_ACTIVE.get(), creatorWillTicks > 0);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_WILL_COOLDOWN.get(), creatorWillCooldownTicks);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_RECOVERY_ACTIVE.get(), recoveryTicks > 0);
        brain.setMemory(ModMemoryModuleTypes.CREATOR_ADAPTATION_LEVEL.get(), adaptationRepeats);
    }

    private void tickWaitingState() {
        clearCreatorBeamVisual();
        this.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, motion.y, 0.0D);
    }

    private void clearAdultCombatState() {
        clearCreatorBeamVisual();
        this.setTarget(null);
        this.committedTarget = null;
        this.targetCommitmentTicks = 0;
        this.perfectInstinctTicks = 0;
        this.creatorWillTicks = 0;
        clearStateAttributes();
    }

    // ---------------------------------------------------------------------
    // Creator Awareness — broad whole-battlefield comprehension.
    // ---------------------------------------------------------------------

    public double getCurrentAwarenessRadius() {
        return this.creatorWillTicks > 0 ? WILL_AWARENESS_RADIUS : AWARENESS_RADIUS;
    }

    public boolean isPotentialCreatorThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || this.isCreatorFamilyMember(candidate)
                || !super.canAttack(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || candidate == this.getLastHurtByMob()
                || isTargetingCreatorFamily(candidate);
    }

    public double scoreCreatorThreat(LivingEntity candidate) {
        if (!isPotentialCreatorThreat(candidate)) return -10000.0D;

        double score = 0.0D;
        LivingEntity owner = this.getOwner();

        if (candidate == this.getLastHurtByMob()) score += 90.0D;
        if (candidate == committedTarget) score += 40.0D;
        if (candidate instanceof Enemy) score += 35.0D;

        if (candidate instanceof Mob mob) {
            LivingEntity victim = mob.getTarget();
            if (victim != null && isCreatorFamilyMember(victim)) {
                score += 175.0D;
                if (victim == owner) score += 100.0D;

                double healthRatio = victim.getHealth() / Math.max(1.0F, victim.getMaxHealth());
                score += (1.0D - healthRatio) * 100.0D;
            }
        }

        if (candidate instanceof Creeper) {
            double creeperDistance = Math.sqrt(this.distanceToSqr(candidate));
            // Creator should be one of Wolfism's best Creeper annihilators.
            score += creeperDistance <= 24.0D ? 245.0D : 105.0D;
        }

        score += Math.min(75.0D, candidate.getMaxHealth() * 0.45D);
        score += Math.min(32.0D, candidate.getAttributeValue(Attributes.ARMOR) * 2.0D);
        score += Math.max(0.0D, 45.0D - Math.sqrt(this.distanceToSqr(candidate)));

        if (adaptiveAttacker != null
                && adaptiveAttacker.equals(candidate.getUUID())
                && adaptationRepeats > 0) {
            score += adaptationRepeats * 18.0D;
        }

        return score;
    }

    private LivingEntity getPriorityThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_PRIORITY_THREAT.get())
                .filter(this::isPotentialCreatorThreat)
                .orElseGet(() -> isPotentialCreatorThreat(this.getTarget()) ? this.getTarget() : null);
    }

    private LivingEntity resolveCommittedThreat(LivingEntity sensed) {
        if (!isPotentialCreatorThreat(committedTarget)
                || !this.isWithinWolfismPhysicalAggroReleaseRange(committedTarget)
                || targetCommitmentTicks <= 0) {
            return sensed;
        }
        if (!isPotentialCreatorThreat(sensed) || sensed == committedTarget) return committedTarget;

        if (isTargetingCreatorFamily(sensed)
                || scoreCreatorThreat(sensed) > scoreCreatorThreat(committedTarget) + 85.0D) {
            return sensed;
        }
        return committedTarget;
    }

    private void commitToThreat(LivingEntity target) {
        if (!isPotentialCreatorThreat(target)
                || !this.isWithinWolfismPhysicalAggroAcquireRange(target)) return;

        LivingEntity current = this.getTarget();
        boolean changingThreat = current != target;
        if (!isPotentialCreatorThreat(current)
                || current == target
                || isTargetingCreatorFamily(target)
                || scoreCreatorThreat(target) > scoreCreatorThreat(current) + 60.0D) {
            this.setTarget(target);
            if (changingThreat) playCreatorAggressionGrowl();
            this.committedTarget = target;
            this.targetCommitmentTicks = 20 * 5;

            if (this.distanceToSqr(target) > 3.0D * 3.0D) {
                this.getNavigation().moveTo(target, perfectInstinctTicks > 0 ? 1.38D : 1.26D);
                this.setSprinting(true);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Mastered Instinct + Adaptive Mastery + Instinctive Counter.
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isWaitingForBond() || this.recoveryTicks > 0) return false;
        if (this.isInvulnerableTo(level, source)) return false;

        Entity sourceEntity = source.getEntity();
        LivingEntity attacker = sourceEntity instanceof LivingEntity living ? living : null;

        int learnedRepeats = registerPatternAttempt(source, attacker);
        if (shouldMasteredInstinctDodge(source, attacker, learnedRepeats)) {
            performInstinctiveDodge(level, attacker);
            return false;
        }

        if (amount >= this.getHealth()) {
            enterRecoveryState(level);
            return true;
        }

        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && attacker != null && this.canAttack(attacker)) {
            commitToThreat(attacker);
        }
        return hurt;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (this.isWaitingForBond() || this.recoveryTicks > 0) return true;
        return super.isInvulnerableTo(level, source);
    }

    private int registerPatternAttempt(DamageSource source, @Nullable LivingEntity attacker) {
        int pattern = classifyDamagePattern(source);
        UUID attackerId = attacker != null ? attacker.getUUID() : null;

        if (Objects.equals(this.adaptiveAttacker, attackerId)
                && this.adaptivePattern == pattern
                && this.adaptationDecayTicks > 0) {
            this.adaptationRepeats = Math.min(MAX_ADAPTATION_REPEATS, this.adaptationRepeats + 1);
        } else {
            this.adaptiveAttacker = attackerId;
            this.adaptivePattern = pattern;
            this.adaptationRepeats = 1;
        }

        this.adaptationDecayTicks = ADAPTATION_DECAY_TICKS;
        return this.adaptationRepeats;
    }

    private int classifyDamagePattern(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return 1;
        if (source.is(DamageTypeTags.IS_FIRE)) return 2;
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return 3;
        if (source.getDirectEntity() != null) return 4;
        return 5;
    }

    private void tickAdaptationDecay() {
        if (adaptationDecayTicks > 0) {
            --adaptationDecayTicks;
            return;
        }

        adaptiveAttacker = null;
        adaptivePattern = 0;
        adaptationRepeats = 0;
    }

    private boolean shouldMasteredInstinctDodge(
            DamageSource source,
            @Nullable LivingEntity attacker,
            int learnedRepeats) {

        // Environmental hazards and true armor-bypass effects remain meaningful.
        if (attacker == null && !source.is(DamageTypeTags.IS_PROJECTILE)) return false;

        double chance;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) chance = 0.88D;
        else if (source.is(DamageTypeTags.BYPASSES_ARMOR)) chance = 0.70D;
        else chance = 0.80D;

        // Adaptive Mastery should make a repeated attack pattern increasingly
        // miserable to land, especially during a prolonged boss fight.
        chance += Math.max(0, learnedRepeats - 1) * 0.05D;
        if (perfectInstinctTicks > 0) chance += 0.08D;
        if (creatorWillTicks > 0) chance += 0.04D;

        // Creator is extremely difficult to hit, not mathematically untouchable.
        chance = Math.min(0.97D, chance);
        return this.random.nextDouble() < chance;
    }

    private void performInstinctiveDodge(ServerLevel level, @Nullable LivingEntity attacker) {
        /*
         * A shove is useless if Creator is boxed into a crater. In that case
         * Mastered Instinct performs a very short emergency reposition to a
         * nearby safe standing point. This is terrain recovery, not Rift-style
         * free teleportation, and has its own cooldown.
         */
        if (instinctEscapeCooldownTicks <= 0
                && isCreatorTerrainConstrained(level)
                && tryCreatorInstinctEscape(level, attacker)) {
            instinctEscapeCooldownTicks = INSTINCT_ESCAPE_COOLDOWN_TICKS;
        } else {
            double side = this.random.nextBoolean() ? 1.0D : -1.0D;

            Vec3 reference;
            if (attacker != null) {
                reference = this.position().subtract(attacker.position());
                reference = new Vec3(reference.x, 0.0D, reference.z);
            } else {
                Vec3 look = this.getLookAngle();
                reference = new Vec3(-look.x, 0.0D, -look.z);
            }
            if (reference.lengthSqr() < 1.0E-4D) reference = new Vec3(0.0D, 0.0D, 1.0D);
            reference = reference.normalize();

            Vec3 lateral = new Vec3(-reference.z, 0.0D, reference.x).scale(0.86D * side);
            Vec3 evasive = lateral.add(reference.scale(0.34D));
            this.push(evasive.x, 0.14D, evasive.z);

            level.sendParticles(
                    ParticleTypes.CLOUD,
                    this.getX(), this.getY(0.45D), this.getZ(),
                    4, 0.20D, 0.10D, 0.20D, 0.025D);
        }

        if (attacker == null || !this.canAttack(attacker)) return;

        // A ranged attacker does not need to become a marathon melee target.
        // If Beam is ready, Mastered Instinct can answer the attack immediately.
        if (!this.isWithinWolfismPhysicalAggroAcquireRange(attacker)) {
            tryCreatorBeam(level, attacker);
            return;
        }

        commitToThreat(attacker);
        if (instinctiveCounterCooldownTicks <= 0
                && this.distanceToSqr(attacker) <= 4.0D * 4.0D) {
            instinctiveCounterCooldownTicks = 16;
            attacker.hurtServer(level, this.damageSources().mobAttack(this), 5.0F);
            level.sendParticles(
                    ParticleTypes.CRIT,
                    attacker.getX(), attacker.getY(0.55D), attacker.getZ(),
                    4, 0.20D, 0.16D, 0.20D, 0.04D);
        }
    }

    private boolean isCreatorTerrainConstrained(ServerLevel level) {
        if (this.horizontalCollision) return true;

        BlockPos feet = this.blockPosition();
        int blockedSides = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos sideFeet = feet.relative(direction);
            BlockPos sideHead = sideFeet.above();

            if (!level.getBlockState(sideFeet).getCollisionShape(level, sideFeet).isEmpty()
                    || !level.getBlockState(sideHead).getCollisionShape(level, sideHead).isEmpty()) {
                ++blockedSides;
            }
        }

        // Two or more blocked sides is enough to treat a combat crater/corner as
        // dangerous when a successful dodge is trying to happen.
        return blockedSides >= 2;
    }

    private boolean tryCreatorInstinctEscape(
            ServerLevel level,
            @Nullable LivingEntity attacker) {

        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int dx = -INSTINCT_ESCAPE_SEARCH_RADIUS; dx <= INSTINCT_ESCAPE_SEARCH_RADIUS; ++dx) {
            for (int dz = -INSTINCT_ESCAPE_SEARCH_RADIUS; dz <= INSTINCT_ESCAPE_SEARCH_RADIUS; ++dz) {
                int horizontalDistanceSqr = dx * dx + dz * dz;
                if (horizontalDistanceSqr < 4
                        || horizontalDistanceSqr > INSTINCT_ESCAPE_SEARCH_RADIUS * INSTINCT_ESCAPE_SEARCH_RADIUS) {
                    continue;
                }

                for (int dy = -1; dy <= 4; ++dy) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isSafeCreatorEscapePosition(level, candidate)) continue;

                    double score = horizontalDistanceSqr * 0.10D + dy * 2.25D;

                    if (attacker != null) {
                        double ax = candidate.getX() + 0.5D - attacker.getX();
                        double az = candidate.getZ() + 0.5D - attacker.getZ();
                        score += Math.sqrt(ax * ax + az * az) * 0.75D;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        if (best == null) return false;

        level.sendParticles(
                ParticleTypes.CLOUD,
                this.getX(), this.getY(0.45D), this.getZ(),
                7, 0.30D, 0.18D, 0.30D, 0.035D);

        this.getNavigation().stop();
        this.teleportTo(
                best.getX() + 0.5D,
                best.getY(),
                best.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = true;

        level.sendParticles(
                ParticleTypes.CLOUD,
                this.getX(), this.getY(0.45D), this.getZ(),
                7, 0.30D, 0.18D, 0.30D, 0.035D);
        return true;
    }

    private boolean isSafeCreatorEscapePosition(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState groundState = level.getBlockState(ground);

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }

        return groundState.isFaceSturdy(level, ground, Direction.UP);
    }

    // Creator's Bite: strong baseline; adaptation adds only a modest execution bonus.
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            if (living instanceof Creeper) {
                // If a Creeper gets close enough to avoid the precision beam,
                // Creator's melee answer should still remove it immediately.
                living.hurtServer(
                        level,
                        this.damageSources().mobAttack(this),
                        CREEPER_BITE_BONUS_DAMAGE);
            }

            if (adaptiveAttacker != null
                    && adaptiveAttacker.equals(living.getUUID())
                    && adaptationRepeats >= 2) {
                float bonus = Math.min(3.0F, 0.65F * adaptationRepeats);
                living.hurtServer(level, this.damageSources().mobAttack(this), bonus);
            }
        }
        return hit;
    }

    // ---------------------------------------------------------------------
    // Creator Beam — precision ranged removal, not artillery spam.
    // ---------------------------------------------------------------------

    private boolean tryCreatorBeam(ServerLevel level, @Nullable LivingEntity target) {
        if (beamCooldownTicks > 0 || !isPotentialCreatorThreat(target)) return false;

        double distance = Math.sqrt(this.distanceToSqr(target));
        if (distance < BEAM_MIN_RANGE || distance > BEAM_MAX_RANGE || !this.hasLineOfSight(target)) {
            return false;
        }

        boolean creeperEmergency = target instanceof Creeper;
        // Use it for genuinely meaningful threats rather than every nearby zombie,
        // with Creepers deliberately exempt because Creator is an elite anti-Creeper wolf.
        if (!creeperEmergency
                && scoreCreatorThreat(target) < 95.0D
                && target.getMaxHealth() < 40.0F) {
            return false;
        }

        beamCooldownTicks = calculateCreatorBeamCooldown(target, creeperEmergency);

        double armor = target.getAttributeValue(Attributes.ARMOR);
        float armorCompensation = (float) Math.min(7.0D, armor * 0.40D);
        float adaptationBonus = adaptiveAttacker != null
                && adaptiveAttacker.equals(target.getUUID())
                ? Math.min(3.5F, adaptationRepeats * 0.70F)
                : 0.0F;
        float damage = BEAM_BASE_DAMAGE
                + armorCompensation
                + adaptationBonus
                + (creeperEmergency ? CREEPER_BEAM_BONUS_DAMAGE : 0.0F);

        target.hurtServer(level, this.damageSources().mobAttack(this), damage);

        // The actual line is a client-rendered vanilla BeaconRenderer beam.
        // Server particles are intentionally limited to muzzle/impact polish.
        this.beamVisualTicks = BEAM_VISUAL_TICKS;
        this.entityData.set(DATA_CREATOR_BEAM_TARGET_ID, target.getId());
        this.entityData.set(DATA_CREATOR_BEAM_ACTIVE, true);

        Vec3 start = this.getEyePosition().add(this.getLookAngle().scale(0.40D));
        Vec3 impact = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                start.x, start.y, start.z,
                7, 0.10D, 0.08D, 0.10D, 0.02D);
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                impact.x, impact.y, impact.z,
                10, 0.28D, 0.25D, 0.28D, 0.055D);
        return true;
    }

    private int calculateCreatorBeamCooldown(
            LivingEntity target,
            boolean creeperEmergency) {

        int cooldown = BEAM_COOLDOWN_TICKS;

        if (creeperEmergency) {
            cooldown = BEAM_CREEPER_COOLDOWN_TICKS;
        } else if (!target.onGround() || target.getMaxHealth() >= 80.0F) {
            // Flying threats and boss-scale enemies should be pressured rather
            // than producing long stretches where Creator has no ranged answer.
            cooldown = BEAM_HIGH_THREAT_COOLDOWN_TICKS;
        }

        if (perfectInstinctTicks > 0 || creatorWillTicks > 0) {
            cooldown = Math.min(cooldown, BEAM_EMPOWERED_COOLDOWN_TICKS);
        }

        return cooldown;
    }

    private void clearCreatorBeamVisual() {
        this.beamVisualTicks = 0;
        this.entityData.set(DATA_CREATOR_BEAM_ACTIVE, false);
        this.entityData.set(DATA_CREATOR_BEAM_TARGET_ID, 0);
    }

    public boolean isCreatorBeamActive() {
        return this.entityData.get(DATA_CREATOR_BEAM_ACTIVE);
    }

    public int getCreatorBeamVisualTargetId() {
        return this.entityData.get(DATA_CREATOR_BEAM_TARGET_ID);
    }

    // ---------------------------------------------------------------------
    // Creator's Touch — strengthens specialists instead of copying them.
    // ---------------------------------------------------------------------

    private boolean tryCreatorsTouch(ServerLevel level) {
        if (!this.isTame() || touchCooldownTicks > 0) return false;

        AbstractWolfismWolf candidate = findWolfismFamily(level, TOUCH_RADIUS).stream()
                .filter(wolf -> wolf != this)
                .filter(wolf -> !wolf.isBaby())
                .filter(wolf -> !wolf.isOrderedToSit() && !wolf.isInSittingPose())
                .filter(wolf -> wolf.getTarget() != null
                        || wolf.getHealth() / Math.max(1.0F, wolf.getMaxHealth()) < 0.70F)
                .max(Comparator.comparingDouble(this::scoreTouchCandidate))
                .orElse(null);

        if (candidate == null) return false;

        touchCooldownTicks = TOUCH_COOLDOWN_TICKS;
        applySpecialistAmplification(candidate, TOUCH_DURATION_TICKS, false);

        level.sendParticles(
                ParticleTypes.ENCHANT,
                candidate.getX(), candidate.getY(0.62D), candidate.getZ(),
                20, 0.42D, 0.36D, 0.42D, 0.045D);
        return true;
    }

    private double scoreTouchCandidate(AbstractWolfismWolf wolf) {
        double ratio = wolf.getHealth() / Math.max(1.0F, wolf.getMaxHealth());
        double score = (1.0D - ratio) * 80.0D;
        if (wolf.getTarget() != null) score += 50.0D;
        if (wolf instanceof AngelWolf || wolf instanceof SalvaWolf) score += 18.0D;
        if (wolf instanceof WolfKing || wolf instanceof PrimordialWolf) score += 14.0D;
        return score;
    }

    private void applySpecialistAmplification(
            AbstractWolfismWolf wolf,
            int duration,
            boolean ultimate) {

        // Healing / salvation specialists.
        if (wolf instanceof AngelWolf || wolf instanceof SalvaWolf) {
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, duration, ultimate ? 2 : 1, true, false), this);
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE, duration, ultimate ? 1 : 0, true, false), this);
            return;
        }

        // Frontline execution / pressure specialists.
        if (wolf instanceof BloodWolf || wolf instanceof InfernalWolf) {
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH, duration, ultimate ? 2 : 1, true, false), this);
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, duration, ultimate ? 1 : 0, true, false), this);
            return;
        }

        // Spatial / aerial / void positioning specialists.
        if (wolf instanceof RiftWolf || wolf instanceof VoidWolf || wolf instanceof PhantomWolf) {
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, duration, ultimate ? 2 : 1, true, false), this);
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE, duration, ultimate ? 1 : 0, true, false), this);
            return;
        }

        // Leadership / instinct specialists keep their niche, but execute it harder.
        if (wolf instanceof WolfKing) {
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE, duration, ultimate ? 2 : 1, true, false), this);
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH, duration, ultimate ? 1 : 0, true, false), this);
            return;
        }
        if (wolf instanceof PrimordialWolf) {
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, duration, ultimate ? 2 : 1, true, false), this);
            wolf.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH, duration, ultimate ? 1 : 0, true, false), this);
            return;
        }

        // Generic fallback for existing/future Wolfism specialists until they
        // receive a dedicated Creator-amplification hook in their own class.
        wolf.addEffect(new MobEffectInstance(
                MobEffects.SPEED, duration, ultimate ? 1 : 0, true, false), this);
        wolf.addEffect(new MobEffectInstance(
                MobEffects.STRENGTH, duration, ultimate ? 1 : 0, true, false), this);
    }

    // ---------------------------------------------------------------------
    // Creator's Intervention — prevent a catastrophic family swing.
    // ---------------------------------------------------------------------

    private boolean tryCreatorsIntervention(ServerLevel level) {
        if (!this.isTame() || interventionCooldownTicks > 0) return false;

        LivingEntity endangered = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_ENDANGERED_FAMILY.get())
                .filter(LivingEntity::isAlive)
                .orElse(null);
        LivingEntity attacker = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_FAMILY_ATTACKER.get())
                .filter(this::isPotentialCreatorThreat)
                .orElse(null);

        if (endangered == null || attacker == null) return false;
        if (this.distanceToSqr(endangered) > INTERVENTION_RADIUS * INTERVENTION_RADIUS) return false;

        double ratio = endangered.getHealth() / Math.max(1.0F, endangered.getMaxHealth());
        if (ratio > 0.48D && endangered != this.getOwner()) return false;

        interventionCooldownTicks = INTERVENTION_COOLDOWN_TICKS;

        endangered.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 3, 3, true, true), this);
        endangered.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 4, 1, true, true), this);

        if (this.isWithinWolfismPhysicalAggroAcquireRange(attacker)) {
            commitToThreat(attacker);
            this.getNavigation().moveTo(attacker, 1.45D);
            this.setSprinting(true);
        } else {
            // Ranged exception: protect the family without turning broad
            // awareness into a long-distance chase.
            tryCreatorBeam(level, attacker);
        }

        if (this.distanceToSqr(attacker) <= 5.0D * 5.0D) {
            Vec3 away = attacker.position().subtract(endangered.position());
            if (away.lengthSqr() > 0.01D) {
                away = away.normalize().scale(0.85D);
                attacker.push(away.x, 0.22D, away.z);
            }
        }

        level.sendParticles(
                ParticleTypes.END_ROD,
                endangered.getX(), endangered.getY(0.65D), endangered.getZ(),
                22, 0.55D, 0.45D, 0.55D, 0.055D);
        return true;
    }

    // ---------------------------------------------------------------------
    // Creation's Presence — broad family coordination, not giant stat aura.
    // ---------------------------------------------------------------------

    private void runCreationsPresence(ServerLevel level, LivingEntity priority) {
        if (!this.isTame() || this.tickCount % 5 != 0 || !isPotentialCreatorThreat(priority)) return;

        boolean emergency = isTargetingCreatorFamily(priority);
        boolean localThreat = this.distanceToSqr(priority)
                <= PRESENCE_SHARE_RADIUS * PRESENCE_SHARE_RADIUS;

        // Nearby enemies can be shared normally. Distant enemies are ignored
        // unless they are actively threatening Creator's family.
        if (!localThreat && !emergency) return;

        for (Wolf wolf : findFamilyWolves(level, FAMILY_RADIUS)) {
            if (wolf == this || wolf.isBaby() || wolf.isOrderedToSit() || wolf.isInSittingPose()) continue;
            if (!wolf.canAttack(priority)) continue;

            LivingEntity current = wolf.getTarget();
            if (emergency || current == null || !current.isAlive() || creatorWillTicks > 0) {
                wolf.setTarget(priority);
                if ((emergency || creatorWillTicks > 0)
                        && wolf.distanceToSqr(priority) > 2.5D * 2.5D) {
                    wolf.getNavigation().moveTo(priority, creatorWillTicks > 0 ? 1.32D : 1.24D);
                    wolf.setSprinting(true);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Perfect Instinct — 10 seconds of extreme reaction / movement mastery.
    // ---------------------------------------------------------------------

    private boolean tryPerfectInstinct(ServerLevel level, @Nullable LivingEntity priority) {
        if (perfectInstinctTicks > 0
                || perfectInstinctCooldownTicks > 0
                || !isPotentialCreatorThreat(priority)) {
            return false;
        }

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_HOSTILE_COUNT.get())
                .orElse(0);
        if (hostileCount < 3 && adaptationRepeats < 2 && priority.getMaxHealth() < 55.0F) return false;

        perfectInstinctTicks = PERFECT_INSTINCT_DURATION_TICKS;
        perfectInstinctCooldownTicks = PERFECT_INSTINCT_COOLDOWN_TICKS;
        refreshStateAttributes();

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                this.getX(), this.getY(0.60D), this.getZ(),
                28, 0.70D, 0.35D, 0.70D, 0.06D);
        return true;
    }

    // ---------------------------------------------------------------------
    // Creator's Will — ultimate family amplifier.
    // ---------------------------------------------------------------------

    private boolean tryCreatorsWill(ServerLevel level, @Nullable LivingEntity priority) {
        if (!this.isTame() || creatorWillTicks > 0 || creatorWillCooldownTicks > 0) return false;

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_HOSTILE_COUNT.get())
                .orElse(0);
        int familyCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CREATOR_FAMILY_COUNT.get())
                .orElse(1);
        boolean familyDanger = this.getBrain()
                .hasMemoryValue(ModMemoryModuleTypes.CREATOR_ENDANGERED_FAMILY.get());
        boolean bossScale = isPotentialCreatorThreat(priority) && priority.getMaxHealth() >= 80.0F;

        if (familyCount < 2 || (!familyDanger && hostileCount < 4 && !bossScale)) return false;

        creatorWillTicks = CREATOR_WILL_DURATION_TICKS;
        creatorWillCooldownTicks = CREATOR_WILL_COOLDOWN_TICKS;
        refreshStateAttributes();

        this.addEffect(new MobEffectInstance(
                MobEffects.RESISTANCE, CREATOR_WILL_DURATION_TICKS, 1, true, true), this);

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.distanceToSqr(owner) <= CREATOR_WILL_RADIUS * CREATOR_WILL_RADIUS) {
            owner.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, CREATOR_WILL_DURATION_TICKS, 0, true, true), this);
            owner.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE, CREATOR_WILL_DURATION_TICKS, 0, true, true), this);
        }

        for (AbstractWolfismWolf wolf : findWolfismFamily(level, CREATOR_WILL_RADIUS)) {
            if (wolf == this || wolf.isBaby()) continue;
            applySpecialistAmplification(wolf, CREATOR_WILL_DURATION_TICKS, true);

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    wolf.getX(), wolf.getY(0.62D), wolf.getZ(),
                    4, 0.30D, 0.24D, 0.30D, 0.035D);
        }

        if (isPotentialCreatorThreat(priority)) {
            runCreationsPresence(level, priority);
        }

        level.sendParticles(
                ParticleTypes.POOF,
                this.getX(), this.getY(0.55D), this.getZ(),
                18, 1.05D, 0.32D, 1.05D, 0.045D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(), this.getY(0.80D), this.getZ(),
                12, 0.72D, 0.45D, 0.72D, 0.055D);
        return true;
    }

    private void maintainCreatorWillPressure(ServerLevel level, LivingEntity priority) {
        if (this.tickCount % 5 == 0) runCreationsPresence(level, priority);
    }

    // ---------------------------------------------------------------------
    // Temporary Creator self-state attributes.
    // ---------------------------------------------------------------------

    private void refreshStateAttributes() {
        if (perfectInstinctTicks > 0) {
            applyTransient(Attributes.MOVEMENT_SPEED, PERFECT_SPEED, 0.08D);
            applyTransient(Attributes.ARMOR, PERFECT_ARMOR, 3.0D);
            applyTransient(Attributes.KNOCKBACK_RESISTANCE, PERFECT_KB, 0.20D);
        } else {
            removeTransient(Attributes.MOVEMENT_SPEED, PERFECT_SPEED);
            removeTransient(Attributes.ARMOR, PERFECT_ARMOR);
            removeTransient(Attributes.KNOCKBACK_RESISTANCE, PERFECT_KB);
        }

        if (creatorWillTicks > 0) {
            applyTransient(Attributes.MOVEMENT_SPEED, WILL_SPEED, 0.06D);
            applyTransient(Attributes.ATTACK_DAMAGE, WILL_DAMAGE, 2.0D);
            applyTransient(Attributes.ARMOR, WILL_ARMOR, 4.0D);
        } else {
            removeTransient(Attributes.MOVEMENT_SPEED, WILL_SPEED);
            removeTransient(Attributes.ATTACK_DAMAGE, WILL_DAMAGE);
            removeTransient(Attributes.ARMOR, WILL_ARMOR);
        }
    }

    private void clearStateAttributes() {
        removeTransient(Attributes.MOVEMENT_SPEED, PERFECT_SPEED);
        removeTransient(Attributes.ARMOR, PERFECT_ARMOR);
        removeTransient(Attributes.KNOCKBACK_RESISTANCE, PERFECT_KB);
        removeTransient(Attributes.MOVEMENT_SPEED, WILL_SPEED);
        removeTransient(Attributes.ATTACK_DAMAGE, WILL_DAMAGE);
        removeTransient(Attributes.ARMOR, WILL_ARMOR);
    }

    private void applyTransient(Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private void removeTransient(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    // ---------------------------------------------------------------------
    // Recovery State — the closest Creator comes to dying.
    // ---------------------------------------------------------------------

    private void enterRecoveryState(ServerLevel level) {
        clearCreatorBeamVisual();
        this.recoveryTicks = RECOVERY_DURATION_TICKS;
        this.setHealth(1.0F);
        this.setTarget(null);
        this.committedTarget = null;
        this.targetCommitmentTicks = 0;
        this.getNavigation().stop();
        this.setSprinting(false);
        clearStateAttributes();

        level.sendParticles(
                ParticleTypes.POOF,
                this.getX(), this.getY(0.50D), this.getZ(),
                32, 0.72D, 0.34D, 0.72D, 0.05D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(), this.getY(0.72D), this.getZ(),
                24, 0.50D, 0.42D, 0.50D, 0.04D);
    }

    private void tickRecovery(ServerLevel level) {
        --this.recoveryTicks;
        this.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, motion.y, 0.0D);

        if (this.tickCount % 10 == 0) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(), this.getY(0.55D), this.getZ(),
                    8, 0.32D, 0.30D, 0.32D, 0.025D);
        }

        if (this.recoveryTicks > 0) return;

        this.setHealth(Math.max(
                1.0F,
                this.getMaxHealth() * RECOVERY_RETURN_HEALTH_RATIO));
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 4, 2, true, true), this);
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 1, true, true), this);

        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(), this.getY(0.70D), this.getZ(),
                36, 0.70D, 0.52D, 0.70D, 0.065D);
    }

    public boolean isRecoveryActive() {
        return recoveryTicks > 0;
    }

    // ---------------------------------------------------------------------
    // Family helpers.
    // ---------------------------------------------------------------------

    public boolean isCreatorFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (!this.isTame()) return false;

        LivingEntity owner = this.getOwner();
        if (entity == owner) return true;

        if (entity instanceof Wolf wolf && wolf.isTame()) {
            return Objects.equals(this.getOwnerReference(), wolf.getOwnerReference());
        }
        return false;
    }

    public boolean isTargetingCreatorFamily(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;
        LivingEntity target = mob.getTarget();
        return target != null && isCreatorFamilyMember(target);
    }

    public List<Wolf> findFamilyWolves(ServerLevel level, double radius) {
        if (!this.isTame()) return List.of();
        return level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(radius),
                wolf -> wolf.isAlive()
                        && wolf.isTame()
                        && Objects.equals(this.getOwnerReference(), wolf.getOwnerReference()));
    }

    public List<AbstractWolfismWolf> findWolfismFamily(ServerLevel level, double radius) {
        if (!this.isTame()) return List.of();
        return level.getEntitiesOfClass(
                AbstractWolfismWolf.class,
                this.getBoundingBox().inflate(radius),
                wolf -> wolf.isAlive()
                        && wolf.isTame()
                        && Objects.equals(this.getOwnerReference(), wolf.getOwnerReference()));
    }

    public List<LivingEntity> findCreatorFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        family.add(this);
        if (!this.isTame()) return family;

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.distanceToSqr(owner) <= radius * radius) {
            family.add(owner);
        }
        family.addAll(findFamilyWolves(level, radius));
        return family;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isWaitingForBond()
                && !this.isRecoveryActive()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isCreatorFamilyMember(target)
                && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Spawn initialization. Real survival gating comes in the next pass.
    // ---------------------------------------------------------------------

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData) {

        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        if (!this.isTame()) {
            this.waitingForBond = true;
            this.boneAccepted = false;
            this.applyUndiscoveredIdentity();
        } else {
            this.identityRevealed = true;
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Persistence.
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("CreatorWaitingForBond", this.waitingForBond);
        output.putBoolean("CreatorBoneAccepted", this.boneAccepted);
        output.putBoolean("CreatorIdentityRevealed", this.identityRevealed);
        output.putInt("CreatorBeamCooldown", this.beamCooldownTicks);
        output.putInt("CreatorTouchCooldown", this.touchCooldownTicks);
        output.putInt("CreatorInterventionCooldown", this.interventionCooldownTicks);
        output.putInt("CreatorPerfectTicks", this.perfectInstinctTicks);
        output.putInt("CreatorPerfectCooldown", this.perfectInstinctCooldownTicks);
        output.putInt("CreatorWillTicks", this.creatorWillTicks);
        output.putInt("CreatorWillCooldown", this.creatorWillCooldownTicks);
        output.putInt("CreatorRecoveryTicks", this.recoveryTicks);
        output.putInt("CreatorGrowlCooldown", this.creatorGrowlCooldownTicks);
        output.putInt("CreatorInstinctEscapeCooldown", this.instinctEscapeCooldownTicks);
        output.putBoolean("CreatorReacquisitionEncounter", this.reacquisitionEncounter);
        if (this.unlockPlayerUuid != null) {
            output.putString("CreatorUnlockPlayer", this.unlockPlayerUuid.toString());
        }
        output.putInt("CreatorAdaptationRepeats", this.adaptationRepeats);
        output.putInt("CreatorAdaptationDecay", this.adaptationDecayTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.waitingForBond = input.getBooleanOr("CreatorWaitingForBond", !this.isTame());
        this.boneAccepted = input.getBooleanOr("CreatorBoneAccepted", this.isTame());
        this.identityRevealed = input.getBooleanOr("CreatorIdentityRevealed", this.isTame());

        /*
         * Migration for Creators saved before the secret-name system:
         * old tamed Creators become revealed; old untamed Creators become
         * mysterious. A genuinely player-assigned custom name is preserved.
         */
        Component loadedName = this.getCustomName();
        boolean managedMystery = loadedName != null
                && UNDISCOVERED_IDENTITY_TEXT.equals(loadedName.getString());

        if (this.identityRevealed && (loadedName == null || managedMystery)) {
            this.setCustomName(revealedCreatorName());
        } else if (!this.identityRevealed && loadedName == null) {
            this.setCustomName(undiscoveredIdentityName());
        }

        this.beamCooldownTicks = Math.max(0, input.getIntOr("CreatorBeamCooldown", 0));
        this.touchCooldownTicks = Math.max(0, input.getIntOr("CreatorTouchCooldown", 0));
        this.interventionCooldownTicks = Math.max(0, input.getIntOr("CreatorInterventionCooldown", 0));
        this.perfectInstinctTicks = Math.max(0, input.getIntOr("CreatorPerfectTicks", 0));
        this.perfectInstinctCooldownTicks = Math.max(0, input.getIntOr("CreatorPerfectCooldown", 0));
        this.creatorWillTicks = Math.max(0, input.getIntOr("CreatorWillTicks", 0));
        this.creatorWillCooldownTicks = Math.max(0, input.getIntOr("CreatorWillCooldown", 0));
        this.recoveryTicks = Math.max(0, input.getIntOr("CreatorRecoveryTicks", 0));
        this.creatorGrowlCooldownTicks = Math.max(0, input.getIntOr("CreatorGrowlCooldown", 0));
        this.instinctEscapeCooldownTicks = Math.max(
                0,
                input.getIntOr("CreatorInstinctEscapeCooldown", 0));
        this.reacquisitionEncounter = input.getBooleanOr("CreatorReacquisitionEncounter", false);

        String unlockPlayer = input.getStringOr("CreatorUnlockPlayer", "");
        if (!unlockPlayer.isBlank()) {
            try {
                this.unlockPlayerUuid = UUID.fromString(unlockPlayer);
            } catch (IllegalArgumentException ignored) {
                this.unlockPlayerUuid = null;
            }
        } else {
            this.unlockPlayerUuid = null;
        }

        this.adaptationRepeats = Math.max(0, Math.min(
                MAX_ADAPTATION_REPEATS,
                input.getIntOr("CreatorAdaptationRepeats", 0)));
        this.adaptationDecayTicks = Math.max(0, input.getIntOr("CreatorAdaptationDecay", 0));

        if (this.isTame()) {
            this.waitingForBond = false;
            this.identityRevealed = true;
        }
        refreshStateAttributes();
    }
}
