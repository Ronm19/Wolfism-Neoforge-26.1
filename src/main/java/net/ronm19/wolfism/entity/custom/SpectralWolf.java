package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Spectral Wolf
 *
 * <p>Ghost / Phasing / Possession / Control / Utility specialist.</p>
 *
 * <p>Identity boundaries:</p>
 * <ul>
 *     <li>Spirit = benevolent soul support and healing.</li>
 *     <li>Shadow = darkness assassin/control.</li>
 *     <li>Grave = death/reaper mechanics.</li>
 *     <li>Rift = true spatial transport and rescue.</li>
 *     <li>Spectral = matter phasing, intangibility, possession and hostile AI collapse.</li>
 * </ul>
 */
public final class SpectralWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Baseline
    // ---------------------------------------------------------------------

    private static final double BASE_MAX_HEALTH = 50.0D;

    // ---------------------------------------------------------------------
    // Ghost Bite
    // ---------------------------------------------------------------------

    public static final int GHOST_BITE_COOLDOWN_TICKS = 20 * 5;
    private static final float GHOST_BITE_ARMOR_BYPASS_DAMAGE = 3.0F;

    // ---------------------------------------------------------------------
    // Spectral Phase
    // ---------------------------------------------------------------------

    public static final int SPECTRAL_PHASE_COOLDOWN_TICKS = 20 * 11;
    private static final double SPECTRAL_PHASE_MAX_DISTANCE = 10.0D;
    private static final double SPECTRAL_PHASE_MAX_SOLID_THICKNESS = 5.5D;
    private static final double SPECTRAL_PHASE_STEP = 0.65D;
    private static final int SPECTRAL_PHASE_MAX_TICKS = 24;

    // ---------------------------------------------------------------------
    // Ethereal Shift
    // ---------------------------------------------------------------------

    public static final int ETHEREAL_SHIFT_COOLDOWN_TICKS = 20 * 24;
    private static final int ETHEREAL_SHIFT_DURATION_TICKS = 20 * 5 / 2; // 2.5s
    private static final float ETHEREAL_SHIFT_DAMAGE_MULTIPLIER = 0.15F;
    private static final float ETHEREAL_SHIFT_TRIGGER_DAMAGE = 6.0F;

    // ---------------------------------------------------------------------
    // Possession
    // ---------------------------------------------------------------------

    public static final int POSSESSION_COOLDOWN_TICKS = 20 * 24;
    private static final double POSSESSION_RANGE = 14.0D;
    private static final int ORDINARY_POSSESSION_DURATION_TICKS = 20 * 7;
    private static final int ELITE_POSSESSION_DURATION_TICKS = 20 * 3;

    // ---------------------------------------------------------------------
    // Haunting Field
    // ---------------------------------------------------------------------

    public static final int HAUNTING_FIELD_COOLDOWN_TICKS = 20 * 28;
    private static final int HAUNTING_FIELD_DURATION_TICKS = 20 * 8;
    private static final double HAUNTING_FIELD_RADIUS = 10.0D;

    // ---------------------------------------------------------------------
    // Mass Possession
    // ---------------------------------------------------------------------

    public static final int MASS_POSSESSION_COOLDOWN_TICKS = 20 * 75;
    private static final int MASS_POSSESSION_DURATION_TICKS = 20 * 10;
    private static final double MASS_POSSESSION_RADIUS = 12.0D;
    private static final int MASS_POSSESSION_MAX_TARGETS = 12;

    // ---------------------------------------------------------------------
    // Brain
    // ---------------------------------------------------------------------

    private static final int DECISION_INTERVAL = 8;

    // ---------------------------------------------------------------------
    // Runtime
    // ---------------------------------------------------------------------

    private int ghostBiteCooldownTicks;
    private int spectralPhaseCooldownTicks;
    private int spectralPhaseTicks;
    private Vec3 spectralPhaseOrigin;
    private Vec3 spectralPhaseDestination;

    private int etherealShiftCooldownTicks;
    private int etherealShiftTicks;

    private int possessionCooldownTicks;
    private final Map<Integer, PossessionState> possessedTargets = new HashMap<>();

    private int hauntingFieldCooldownTicks;
    private int hauntingFieldTicks;

    private int massPossessionCooldownTicks;
    private int massPossessionTicks;
    private Vec3 massPossessionCenter;

    private static final class BrainHolder {
        private static final Brain.Provider<SpectralWolf> PROVIDER =
                Brain.<SpectralWolf>provider(
                        ImmutableList.of(ModSensorTypes.SPECTRAL_TACTICAL.get()),
                        wolf -> List.of());
    }

    public SpectralWolf(EntityType<? extends SpectralWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SPECTRAL_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 42.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.14D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected Brain<SpectralWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SpectralWolf> getBrain() {
        return (Brain<SpectralWolf>) super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isSpectralFamilyMember(target)
                && super.canAttack(target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) return;

        tickDownCooldowns();
        tickSpectralPhase(level);
        tickEtherealShift(level);
        tickPossessions(level);
        tickHauntingField(level);
        tickMassPossession(level);

        if (this.isBaby()) {
            cancelAdultSpectralStates(level);
            this.setTarget(null);
            return;
        }

        if (this.tickCount % DECISION_INTERVAL == 0 && spectralPhaseTicks <= 0) {
            tickSpectralDecisionBrain(level);
        }
    }

    private void tickDownCooldowns() {
        if (ghostBiteCooldownTicks > 0) --ghostBiteCooldownTicks;
        if (spectralPhaseCooldownTicks > 0) --spectralPhaseCooldownTicks;
        if (etherealShiftCooldownTicks > 0) --etherealShiftCooldownTicks;
        if (possessionCooldownTicks > 0) --possessionCooldownTicks;
        if (hauntingFieldCooldownTicks > 0) --hauntingFieldCooldownTicks;
        if (massPossessionCooldownTicks > 0) --massPossessionCooldownTicks;
    }

    // ---------------------------------------------------------------------
    // Passive ghost physiology
    // ---------------------------------------------------------------------

    @Override
    public boolean causeFallDamage(
            double fallDistance,
            float damageMultiplier,
            DamageSource damageSource) {

        this.fallDistance = 0.0F;
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypes.IN_WALL)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    // ---------------------------------------------------------------------
    // Ethereal Shift
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableTo(level, source)) return false;

        if (!this.isBaby()
                && etherealShiftTicks <= 0
                && etherealShiftCooldownTicks <= 0
                && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && shouldTriggerEtherealShift(amount)) {

            startEtherealShift(level);
        }

        float adjusted = amount;

        if (etherealShiftTicks > 0
                && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {

            adjusted *= ETHEREAL_SHIFT_DAMAGE_MULTIPLIER;
        }

        return super.hurtServer(level, source, adjusted);
    }

    private boolean shouldTriggerEtherealShift(float incomingDamage) {
        if (incomingDamage >= ETHEREAL_SHIFT_TRIGGER_DAMAGE) {
            return true;
        }

        float projectedHealth = this.getHealth() - incomingDamage;
        return projectedHealth <= this.getMaxHealth() * 0.45F;
    }

    private boolean startEtherealShift(ServerLevel level) {
        if (etherealShiftTicks > 0
                || etherealShiftCooldownTicks > 0
                || this.isBaby()) {
            return false;
        }

        etherealShiftTicks = ETHEREAL_SHIFT_DURATION_TICKS;
        etherealShiftCooldownTicks = ETHEREAL_SHIFT_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.SOUL,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                28,
                0.50D, 0.40D, 0.50D,
                0.03D);

        return true;
    }

    private void tickEtherealShift(ServerLevel level) {
        if (etherealShiftTicks <= 0) return;

        --etherealShiftTicks;
        this.fallDistance = 0.0F;

        if (etherealShiftTicks % 4 == 0) {
            level.sendParticles(
                    ParticleTypes.SOUL,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    4,
                    0.38D, 0.28D, 0.38D,
                    0.01D);
        }
    }

    // ---------------------------------------------------------------------
    // Ghost Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);

        if (!hit
                || this.isBaby()
                || !(target instanceof LivingEntity livingTarget)
                || !isValidSpectralThreat(livingTarget)) {
            return hit;
        }

        if (ghostBiteCooldownTicks <= 0) {
            /*
             * The normal wolf bite already happened through super.
             * This smaller generic phase-damage component bypasses armor,
             * representing the bite partially materializing inside protection.
             */
            livingTarget.hurtServer(
                    level,
                    this.damageSources().generic(),
                    GHOST_BITE_ARMOR_BYPASS_DAMAGE);

            level.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    livingTarget.getX(),
                    livingTarget.getY() + livingTarget.getBbHeight() * 0.55D,
                    livingTarget.getZ(),
                    12,
                    0.30D, 0.28D, 0.30D,
                    0.025D);

            ghostBiteCooldownTicks = GHOST_BITE_COOLDOWN_TICKS;
        }

        return hit;
    }

    // ---------------------------------------------------------------------
    // Spectral Phase
    // ---------------------------------------------------------------------

    private boolean trySpectralPhase(ServerLevel level, LivingEntity target) {
        if (spectralPhaseCooldownTicks > 0
                || spectralPhaseTicks > 0
                || target == null
                || !target.isAlive()
                || target.level() != level
                || this.isOrderedToSit()
                || this.isInSittingPose()
                || this.isBaby()) {
            return false;
        }

        Vec3 exit = findControlledPhaseExit(level, target);
        if (exit == null) return false;

        spectralPhaseOrigin = this.position();
        spectralPhaseDestination = exit;
        spectralPhaseTicks = SPECTRAL_PHASE_MAX_TICKS;
        spectralPhaseCooldownTicks = SPECTRAL_PHASE_COOLDOWN_TICKS;

        this.getNavigation().stop();
        level.sendParticles(
                ParticleTypes.SOUL,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                20,
                0.45D, 0.35D, 0.45D,
                0.025D);

        return true;
    }

    private void tickSpectralPhase(ServerLevel level) {
        if (spectralPhaseTicks <= 0 || spectralPhaseDestination == null) {
            if (this.noPhysics) {
                this.noPhysics = false;
            }
            return;
        }

        --spectralPhaseTicks;

        this.noPhysics = true;
        this.fallDistance = 0.0F;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);

        Vec3 current = this.position();
        Vec3 delta = spectralPhaseDestination.subtract(current);
        double distance = delta.length();

        if (distance <= 0.45D) {
            finishSpectralPhase(level, true);
            return;
        }

        double stepLength = Math.min(SPECTRAL_PHASE_STEP, distance);
        Vec3 next = current.add(delta.normalize().scale(stepLength));

        this.setPos(next.x, next.y, next.z);

        if (spectralPhaseTicks % 2 == 0) {
            level.sendParticles(
                    ParticleTypes.SOUL,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    3,
                    0.25D, 0.20D, 0.25D,
                    0.005D);
        }

        if (spectralPhaseTicks <= 0) {
            finishSpectralPhase(level, isSafeSpectralStandingPosition(
                    level,
                    BlockPos.containing(
                            spectralPhaseDestination.x,
                            spectralPhaseDestination.y,
                            spectralPhaseDestination.z)));
        }
    }

    private void finishSpectralPhase(ServerLevel level, boolean destinationStillSafe) {
        Vec3 destination = spectralPhaseDestination;
        Vec3 origin = spectralPhaseOrigin;

        this.noPhysics = false;
        this.setDeltaMovement(Vec3.ZERO);

        if (destinationStillSafe && destination != null) {
            this.setPos(destination.x, destination.y, destination.z);

            level.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    destination.x,
                    destination.y + 0.45D,
                    destination.z,
                    18,
                    0.45D, 0.32D, 0.45D,
                    0.02D);

        } else if (origin != null) {
            this.setPos(origin.x, origin.y, origin.z);
        }

        spectralPhaseTicks = 0;
        spectralPhaseOrigin = null;
        spectralPhaseDestination = null;
    }

    private Vec3 findControlledPhaseExit(ServerLevel level, LivingEntity target) {
        Vec3 start = this.position();
        Vec3 towardTarget = target.position().subtract(start);

        double targetDistance = towardTarget.length();
        if (targetDistance < 2.0D) return null;

        double scanDistance = Math.min(
                SPECTRAL_PHASE_MAX_DISTANCE,
                targetDistance);

        Vec3 direction = towardTarget.normalize();

        boolean enteredSolid = false;
        double solidStart = 0.0D;

        for (double distance = 0.35D;
             distance <= scanDistance;
             distance += 0.35D) {

            Vec3 sample = start.add(direction.scale(distance));

            BlockPos sampleFeet = BlockPos.containing(
                    sample.x,
                    sample.y,
                    sample.z);

            boolean blocked = isSpectralBodyBlocked(level, sampleFeet);

            if (blocked) {
                if (!enteredSolid) {
                    enteredSolid = true;
                    solidStart = distance;
                }

                if (distance - solidStart
                        > SPECTRAL_PHASE_MAX_SOLID_THICKNESS) {
                    return null;
                }

                continue;
            }

            if (!enteredSolid) {
                continue;
            }

            double solidThickness = distance - solidStart;
            if (solidThickness > SPECTRAL_PHASE_MAX_SOLID_THICKNESS) {
                return null;
            }

            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos exitFeet = sampleFeet.offset(0, dy, 0);

                if (isSafeSpectralStandingPosition(level, exitFeet)) {
                    return Vec3.atBottomCenterOf(exitFeet);
                }
            }
        }

        return null;
    }

    private boolean isSpectralBodyBlocked(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();

        return !level.getBlockState(feet)
                .getCollisionShape(level, feet)
                .isEmpty()
                || !level.getBlockState(head)
                .getCollisionShape(level, head)
                .isEmpty();
    }

    private boolean isSafeSpectralStandingPosition(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(feet)
                .getCollisionShape(level, feet)
                .isEmpty()
                || !level.getBlockState(head)
                .getCollisionShape(level, head)
                .isEmpty()) {
            return false;
        }

        return level.getBlockState(ground)
                .isFaceSturdy(level, ground, Direction.UP);
    }

    // ---------------------------------------------------------------------
    // Possession
    // ---------------------------------------------------------------------

    private boolean tryPossession(ServerLevel level, LivingEntity target) {
        if (possessionCooldownTicks > 0
                || target == null
                || !target.isAlive()
                || !isValidSpectralThreat(target)
                || this.distanceToSqr(target) > POSSESSION_RANGE * POSSESSION_RANGE) {
            return false;
        }

        if (isBossSpectralTarget(target)) {
            applyBossDisruption(level, target, 20 * 2);
            possessionCooldownTicks = POSSESSION_COOLDOWN_TICKS;
            return true;
        }

        if (!(target instanceof Mob mob)) {
            return false;
        }

        int duration = isEliteSpectralTarget(target)
                ? ELITE_POSSESSION_DURATION_TICKS
                : ORDINARY_POSSESSION_DURATION_TICKS;

        if (!beginPossession(level, mob, duration, false)) {
            return false;
        }

        possessionCooldownTicks = POSSESSION_COOLDOWN_TICKS;
        return true;
    }

    private boolean beginPossession(
            ServerLevel level,
            Mob mob,
            int duration,
            boolean fromMassPossession) {

        if (!mob.isAlive()
                || !isValidSpectralThreat(mob)
                || isBossSpectralTarget(mob)) {
            return false;
        }

        PossessionState existing = possessedTargets.get(mob.getId());
        if (existing != null) {
            existing.ticks = Math.max(existing.ticks, duration);
            existing.massPossession |= fromMassPossession;
            return true;
        }

        LivingEntity originalTarget = mob.getTarget();
        int originalTargetId = originalTarget == null
                ? -1
                : originalTarget.getId();

        boolean elite = isEliteSpectralTarget(mob);

        PossessionMode mode;
        int forcedTargetId = -1;

        if (elite) {
            mode = PossessionMode.SUPPRESSED;
        } else {
            LivingEntity turncoatTarget = findTurncoatTarget(level, mob, 10.0D);

            if (turncoatTarget != null && this.random.nextFloat() < 0.65F) {
                mode = PossessionMode.TURNCOAT;
                forcedTargetId = turncoatTarget.getId();
            } else if (this.random.nextBoolean()) {
                mode = PossessionMode.FROZEN;
            } else {
                mode = PossessionMode.WANDER;
            }
        }

        possessedTargets.put(
                mob.getId(),
                new PossessionState(
                        mob.getId(),
                        originalTargetId,
                        forcedTargetId,
                        duration,
                        mode,
                        fromMassPossession));

        mob.setTarget(null);
        mob.getNavigation().stop();

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                mob.getX(),
                mob.getY() + mob.getBbHeight() * 0.55D,
                mob.getZ(),
                18,
                0.40D, 0.45D, 0.40D,
                0.03D);

        return true;
    }

    private void tickPossessions(ServerLevel level) {
        if (possessedTargets.isEmpty()) return;

        List<Integer> finished = new ArrayList<>();

        for (PossessionState state : possessedTargets.values()) {
            Entity entity = level.getEntity(state.entityId);

            if (!(entity instanceof Mob mob)
                    || !mob.isAlive()
                    || !isValidSpectralThreat(mob)) {

                finished.add(state.entityId);
                continue;
            }

            --state.ticks;

            switch (state.mode) {
                case TURNCOAT -> tickTurncoatPossession(level, mob, state);
                case FROZEN -> tickFrozenPossession(mob);
                case WANDER -> tickWanderPossession(mob, state);
                case SUPPRESSED -> tickSuppressedPossession(mob);
            }

            if (state.ticks % 10 == 0) {
                level.sendParticles(
                        ParticleTypes.SOUL,
                        mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.55D,
                        mob.getZ(),
                        4,
                        0.28D, 0.35D, 0.28D,
                        0.01D);
            }

            if (state.ticks <= 0) {
                restorePossessedMob(level, mob, state);
                finished.add(state.entityId);
            }
        }

        for (int id : finished) {
            possessedTargets.remove(id);
        }
    }

    private void tickTurncoatPossession(
            ServerLevel level,
            Mob mob,
            PossessionState state) {

        LivingEntity forcedTarget =
                getLivingEntityById(level, state.forcedTargetId);

        if (forcedTarget == null
                || !forcedTarget.isAlive()
                || forcedTarget == mob
                || !isValidSpectralThreat(forcedTarget)) {

            forcedTarget = findTurncoatTarget(level, mob, 10.0D);

            if (forcedTarget == null) {
                state.mode = PossessionMode.FROZEN;
                mob.setTarget(null);
                mob.getNavigation().stop();
                return;
            }

            state.forcedTargetId = forcedTarget.getId();
        }

        mob.setTarget(forcedTarget);
    }

    private void tickFrozenPossession(Mob mob) {
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
    }

    private void tickWanderPossession(Mob mob, PossessionState state) {
        mob.setTarget(null);

        if (state.ticks % 20 == 0 || mob.getNavigation().isDone()) {
            double x = mob.getX() + this.random.nextIntBetweenInclusive(-6, 6);
            double z = mob.getZ() + this.random.nextIntBetweenInclusive(-6, 6);

            mob.getNavigation().moveTo(
                    x,
                    mob.getY(),
                    z,
                    0.75D);
        }
    }

    private void tickSuppressedPossession(Mob mob) {
        mob.setTarget(null);
        mob.getNavigation().stop();

        mob.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                15,
                1,
                true,
                false));

        mob.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                15,
                0,
                true,
                false));
    }

    private void restorePossessedMob(
            ServerLevel level,
            Mob mob,
            PossessionState state) {

        LivingEntity original =
                getLivingEntityById(level, state.originalTargetId);

        if (original != null
                && original.isAlive()
                && original != this
                && !this.isSpectralFamilyMember(original)) {

            mob.setTarget(original);
        } else {
            mob.setTarget(null);
        }
    }

    private LivingEntity findTurncoatTarget(
            ServerLevel level,
            LivingEntity possessed,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        possessed.getBoundingBox().inflate(radius),
                        candidate -> candidate != possessed
                                && this.isValidSpectralThreat(candidate)
                                && candidate.distanceToSqr(possessed) <= radiusSqr)
                .stream()
                .min(Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(possessed)))
                .orElse(null);
    }

    private LivingEntity findBestPossessionTarget(ServerLevel level) {
        return getNearbySpectralThreats(level, POSSESSION_RANGE)
                .stream()
                .filter(target -> target instanceof Mob)
                .filter(target -> !possessedTargets.containsKey(target.getId()))
                .sorted(
                        Comparator
                                .<LivingEntity>comparingInt(
                                        target -> isThreateningFamily(target) ? 0 : 1)
                                .thenComparingInt(
                                        target -> isBossSpectralTarget(target) ? 1 : 0)
                                .thenComparingInt(
                                        target -> isEliteSpectralTarget(target) ? 0 : 1)
                                .thenComparingDouble(this::spectralThreatScore))
                .findFirst()
                .orElse(null);
    }

    private boolean isThreateningFamily(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;

        LivingEntity target = mob.getTarget();
        return target != null && isSpectralFamilyMember(target);
    }

    private boolean isEliteSpectralTarget(LivingEntity target) {
        return !isBossSpectralTarget(target)
                && (target.getMaxHealth() >= 40.0F
                || target.getArmorValue() >= 10);
    }

    private boolean isBossSpectralTarget(LivingEntity target) {
        return target instanceof WitherBoss
                || target instanceof EnderDragon;
    }

    private void applyBossDisruption(
            ServerLevel level,
            LivingEntity boss,
            int duration) {

        boss.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                duration,
                0,
                true,
                true));

        boss.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                duration,
                0,
                true,
                true));

        if (boss instanceof Mob mob) {
            LivingEntity target = mob.getTarget();

            if (target != null && isSpectralFamilyMember(target)) {
                mob.setTarget(null);
            }

            mob.getNavigation().stop();
        }

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                boss.getX(),
                boss.getY() + boss.getBbHeight() * 0.55D,
                boss.getZ(),
                20,
                0.50D, 0.55D, 0.50D,
                0.035D);
    }

    // ---------------------------------------------------------------------
    // Haunting Field
    // ---------------------------------------------------------------------

    private boolean startHauntingField(ServerLevel level) {
        if (hauntingFieldCooldownTicks > 0
                || hauntingFieldTicks > 0
                || massPossessionTicks > 0
                || this.isBaby()) {
            return false;
        }

        hauntingFieldTicks = HAUNTING_FIELD_DURATION_TICKS;
        hauntingFieldCooldownTicks = HAUNTING_FIELD_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.SOUL,
                this.getX(),
                this.getY() + 0.45D,
                this.getZ(),
                34,
                1.20D, 0.55D, 1.20D,
                0.035D);

        return true;
    }

    private void tickHauntingField(ServerLevel level) {
        if (hauntingFieldTicks <= 0) return;

        if (massPossessionTicks > 0) {
            hauntingFieldTicks = 0;
            return;
        }

        --hauntingFieldTicks;

        if (hauntingFieldTicks % 10 != 0) return;

        for (LivingEntity enemy :
                getNearbySpectralThreats(level, HAUNTING_FIELD_RADIUS)) {

            enemy.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS,
                    30,
                    0,
                    true,
                    false));

            if (isBossSpectralTarget(enemy)) {
                enemy.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        25,
                        0,
                        true,
                        false));
                continue;
            }

            if (!isEliteSpectralTarget(enemy)) {
                enemy.addEffect(new MobEffectInstance(
                        MobEffects.DARKNESS,
                        25,
                        0,
                        true,
                        false));
            }

            if (enemy instanceof Mob mob) {
                float confusionChance = isEliteSpectralTarget(enemy)
                        ? 0.20F
                        : 0.45F;

                if (this.random.nextFloat() < confusionChance) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                } else if (!isEliteSpectralTarget(enemy)
                        && this.random.nextFloat() < 0.20F) {

                    LivingEntity hostileTarget =
                            findTurncoatTarget(level, enemy, 7.0D);

                    if (hostileTarget != null) {
                        mob.setTarget(hostileTarget);
                    }
                }
            }
        }

        spawnHauntingRing(level);
    }

    private void spawnHauntingRing(ServerLevel level) {
        int points = 20;

        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;

            double x = this.getX()
                    + Math.cos(angle) * HAUNTING_FIELD_RADIUS;
            double z = this.getZ()
                    + Math.sin(angle) * HAUNTING_FIELD_RADIUS;

            level.sendParticles(
                    ParticleTypes.SOUL,
                    x,
                    this.getY() + 0.12D,
                    z,
                    1,
                    0.02D, 0.04D, 0.02D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Mass Possession
    // ---------------------------------------------------------------------

    private boolean startMassPossession(ServerLevel level) {
        if (massPossessionCooldownTicks > 0
                || massPossessionTicks > 0
                || this.isBaby()) {
            return false;
        }

        hauntingFieldTicks = 0;

        massPossessionCenter = this.position();
        massPossessionTicks = MASS_POSSESSION_DURATION_TICKS;
        massPossessionCooldownTicks = MASS_POSSESSION_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                massPossessionCenter.x,
                massPossessionCenter.y + 0.45D,
                massPossessionCenter.z,
                56,
                3.0D, 0.65D, 3.0D,
                0.045D);

        spawnMassPossessionRing(level, true);
        applyMassPossessionPulse(level);

        return true;
    }

    private void tickMassPossession(ServerLevel level) {
        if (massPossessionTicks <= 0
                || massPossessionCenter == null) {
            return;
        }

        --massPossessionTicks;

        if (massPossessionTicks % 20 == 0) {
            applyMassPossessionPulse(level);
        }

        if (massPossessionTicks % 10 == 0) {
            spawnMassPossessionRing(level, false);
        }

        if (massPossessionTicks <= 0) {
            massPossessionCenter = null;
        }
    }

    private void applyMassPossessionPulse(ServerLevel level) {
        if (massPossessionCenter == null) return;

        double radiusSqr =
                MASS_POSSESSION_RADIUS * MASS_POSSESSION_RADIUS;

        AABB area = new AABB(
                massPossessionCenter.x - MASS_POSSESSION_RADIUS,
                massPossessionCenter.y - 5.0D,
                massPossessionCenter.z - MASS_POSSESSION_RADIUS,
                massPossessionCenter.x + MASS_POSSESSION_RADIUS,
                massPossessionCenter.y + 5.0D,
                massPossessionCenter.z + MASS_POSSESSION_RADIUS);

        List<LivingEntity> threats = level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        this::isValidSpectralThreat)
                .stream()
                .filter(enemy -> enemy.position()
                        .distanceToSqr(massPossessionCenter) <= radiusSqr)
                .sorted(Comparator.comparingDouble(
                        enemy -> enemy.position()
                                .distanceToSqr(massPossessionCenter)))
                .limit(MASS_POSSESSION_MAX_TARGETS)
                .toList();

        for (LivingEntity enemy : threats) {
            if (isBossSpectralTarget(enemy)) {
                applyBossDisruption(level, enemy, 20);
                continue;
            }

            if (!(enemy instanceof Mob mob)) {
                continue;
            }

            int duration = isEliteSpectralTarget(enemy)
                    ? Math.min(
                    ELITE_POSSESSION_DURATION_TICKS,
                    Math.max(20, massPossessionTicks))
                    : Math.max(20, massPossessionTicks);

            beginPossession(
                    level,
                    mob,
                    duration,
                    true);
        }
    }

    private void spawnMassPossessionRing(
            ServerLevel level,
            boolean bright) {

        if (massPossessionCenter == null) return;

        int points = bright ? 44 : 28;

        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;

            double x = massPossessionCenter.x
                    + Math.cos(angle) * MASS_POSSESSION_RADIUS;
            double z = massPossessionCenter.z
                    + Math.sin(angle) * MASS_POSSESSION_RADIUS;

            level.sendParticles(
                    bright
                            ? ParticleTypes.SOUL_FIRE_FLAME
                            : ParticleTypes.SOUL,
                    x,
                    massPossessionCenter.y + 0.15D,
                    z,
                    1,
                    0.02D, 0.05D, 0.02D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Brain decision policy
    // ---------------------------------------------------------------------

    private void tickSpectralDecisionBrain(ServerLevel level) {
        if (this.isOrderedToSit()
                || this.isInSittingPose()
                || spectralPhaseTicks > 0) {
            return;
        }

        int observedHostiles = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SPECTRAL_HOSTILE_COUNT.get())
                .orElse(0);

        /*
         * Large hostile force -> Mass Possession.
         */
        if (observedHostiles >= 5
                && massPossessionCooldownTicks <= 0
                && countThreatsAround(
                level,
                this.position(),
                MASS_POSSESSION_RADIUS) >= 5) {

            if (startMassPossession(level)) {
                return;
            }
        }

        /*
         * Family surrounded -> Haunting Field.
         */
        if (observedHostiles >= 3
                && massPossessionTicks <= 0
                && hauntingFieldCooldownTicks <= 0
                && countThreatsAround(
                level,
                this.position(),
                HAUNTING_FIELD_RADIUS) >= 3) {

            if (startHauntingField(level)) {
                return;
            }
        }

        /*
         * Heavy pressure -> proactive Ethereal Shift.
         * The hurtServer hook also reacts to a large incoming hit.
         */
        if (etherealShiftCooldownTicks <= 0
                && etherealShiftTicks <= 0
                && observedHostiles >= 2
                && this.getHealth() <= this.getMaxHealth() * 0.60F) {

            if (startEtherealShift(level)) {
                return;
            }
        }

        /*
         * Endangered family behind matter -> phase through to reinforce them.
         * No healing or teleport-rescue effect: Spirit/Rift keep those roles.
         */
        LivingEntity vulnerableFamily = this.getBrain()
                .getMemory(
                        ModMemoryModuleTypes.SPECTRAL_VULNERABLE_FAMILY.get())
                .orElse(null);

        if (vulnerableFamily != null
                && !this.hasLineOfSight(vulnerableFamily)
                && trySpectralPhase(level, vulnerableFamily)) {
            return;
        }

        /*
         * Enemy behind a barrier -> Spectral Phase.
         */
        LivingEntity phaseTarget = this.getBrain()
                .getMemory(ModMemoryModuleTypes.SPECTRAL_PHASE_TARGET.get())
                .orElse(null);

        if (phaseTarget != null
                && !this.hasLineOfSight(phaseTarget)
                && trySpectralPhase(level, phaseTarget)) {
            return;
        }

        /*
         * Dangerous ordinary/elite mob -> Possession.
         */
        if (possessionCooldownTicks <= 0
                && massPossessionTicks <= 0) {

            LivingEntity possessionTarget =
                    findBestPossessionTarget(level);

            if (possessionTarget != null
                    && tryPossession(level, possessionTarget)) {
                return;
            }
        }
    }

    private int countThreatsAround(
            ServerLevel level,
            Vec3 center,
            double radius) {

        double radiusSqr = radius * radius;

        AABB area = new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius);

        int count = 0;

        for (LivingEntity enemy : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isValidSpectralThreat)) {

            if (enemy.position().distanceToSqr(center) <= radiusSqr) {
                ++count;
            }
        }

        return count;
    }

    // ---------------------------------------------------------------------
    // Family / threat helpers
    // ---------------------------------------------------------------------

    public boolean isSpectralFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            LivingEntity owner = this.getOwner();

            if (entity == owner) return true;

            if (entity instanceof Wolf wolf
                    && wolf.isTame()
                    && owner != null) {

                return wolf.isOwnedBy(owner);
            }

            return false;
        }

        return entity instanceof SpectralWolf spectral
                && !spectral.isTame();
    }

    public boolean isValidSpectralThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || this.isSpectralFamilyMember(target)
                || this.isAlliedTo(target)
                || !(target instanceof Enemy)) {
            return false;
        }

        LivingEntity owner = this.getOwner();

        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public double spectralThreatScore(LivingEntity target) {
        double distance =
                Math.sqrt(this.distanceToSqr(target));

        double healthWeight =
                Math.min(20.0D, target.getMaxHealth() * 0.12D);

        return distance - healthWeight;
    }

    public List<LivingEntity> getNearbySpectralFamily(
            ServerLevel level,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isSpectralFamilyMember)
                .stream()
                .filter(member -> this.distanceToSqr(member) <= radiusSqr)
                .toList();
    }

    private List<LivingEntity> getNearbySpectralThreats(
            ServerLevel level,
            double radius) {

        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isValidSpectralThreat)
                .stream()
                .filter(enemy -> this.distanceToSqr(enemy) <= radiusSqr)
                .toList();
    }

    public boolean isVulnerableSpectralFamilyMember(LivingEntity member) {
        if (!isSpectralFamilyMember(member)) return false;

        return member.fallDistance >= 4.0F
                || member.getHealth() <= member.getMaxHealth() * 0.34F;
    }

    private LivingEntity getLivingEntityById(ServerLevel level, int id) {
        if (id < 0) return null;

        if (level.getEntity(id) instanceof LivingEntity living) {
            return living;
        }

        return null;
    }

    public boolean isSpectralCombatVisualActive() {
        return spectralPhaseTicks > 0
                || etherealShiftTicks > 0
                || hauntingFieldTicks > 0
                || massPossessionTicks > 0
                || !possessedTargets.isEmpty();
    }

    // ---------------------------------------------------------------------
    // Natural spawning — unchanged from the already-tested V1.1 rules
    // ---------------------------------------------------------------------

    public static boolean checkSpectralWolfSpawnRules(
            EntityType<SpectralWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(pos.above())
                .getCollisionShape(level, pos.above())
                .isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();

        if (!level.getBlockState(floor)
                .isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildSpectral =
                    !serverLevel.getEntitiesOfClass(
                                    SpectralWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf ->
                                            wolf.isAlive()
                                                    && !wolf.isTame())
                            .isEmpty();

            if (anotherWildSpectral) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt("GhostBiteCooldown", ghostBiteCooldownTicks);
        output.putInt("SpectralPhaseCooldown", spectralPhaseCooldownTicks);
        output.putInt("EtherealShiftCooldown", etherealShiftCooldownTicks);
        output.putInt("SpectralPossessionCooldown", possessionCooldownTicks);
        output.putInt("HauntingFieldCooldown", hauntingFieldCooldownTicks);
        output.putInt("MassPossessionCooldown", massPossessionCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        /*
         * New keys read the old V1/V1.1 cooldown keys as fallbacks,
         * so an existing Spectral Wolf does not lose all persisted timing.
         */
        ghostBiteCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "GhostBiteCooldown",
                        input.getIntOr("SpectralBiteCooldown", 0)));

        spectralPhaseCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "SpectralPhaseCooldown",
                        input.getIntOr("SpectralGhostStepCooldown", 0)));

        etherealShiftCooldownTicks = Math.max(
                0,
                input.getIntOr("EtherealShiftCooldown", 0));

        possessionCooldownTicks = Math.max(
                0,
                input.getIntOr("SpectralPossessionCooldown", 0));

        hauntingFieldCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "HauntingFieldCooldown",
                        input.getIntOr("SpectralHauntingCooldown", 0)));

        massPossessionCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "MassPossessionCooldown",
                        input.getIntOr("SpectralDominionCooldown", 0)));

        spectralPhaseTicks = 0;
        spectralPhaseOrigin = null;
        spectralPhaseDestination = null;

        etherealShiftTicks = 0;

        hauntingFieldTicks = 0;

        massPossessionTicks = 0;
        massPossessionCenter = null;

        possessedTargets.clear();

        this.noPhysics = false;
    }

    private void cancelAdultSpectralStates(ServerLevel level) {
        if (spectralPhaseTicks > 0) {
            finishSpectralPhase(level, false);
        }

        etherealShiftTicks = 0;
        hauntingFieldTicks = 0;
        massPossessionTicks = 0;
        massPossessionCenter = null;

        for (PossessionState state :
                new ArrayList<>(possessedTargets.values())) {

            Entity entity = level.getEntity(state.entityId);

            if (entity instanceof Mob mob && mob.isAlive()) {
                restorePossessedMob(level, mob, state);
            }
        }

        possessedTargets.clear();
        this.noPhysics = false;
    }

    // ---------------------------------------------------------------------
    // Possession runtime state
    // ---------------------------------------------------------------------

    private enum PossessionMode {
        TURNCOAT,
        FROZEN,
        WANDER,
        SUPPRESSED
    }

    private static final class PossessionState {
        private final int entityId;
        private final int originalTargetId;
        private int forcedTargetId;
        private int ticks;
        private PossessionMode mode;
        private boolean massPossession;

        private PossessionState(
                int entityId,
                int originalTargetId,
                int forcedTargetId,
                int ticks,
                PossessionMode mode,
                boolean massPossession) {

            this.entityId = entityId;
            this.originalTargetId = originalTargetId;
            this.forcedTargetId = forcedTargetId;
            this.ticks = ticks;
            this.mode = mode;
            this.massPossession = massPossession;
        }
    }
}
