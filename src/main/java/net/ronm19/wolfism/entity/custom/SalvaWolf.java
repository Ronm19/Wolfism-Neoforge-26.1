package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;

/**
 * Wolfism #37 — Salva Wolf ♀.
 *
 * <p>Salvation / Guardian / Rescue / Emergency Support.</p>
 *
 * <p>Identity boundary:</p>
 * <ul>
 *     <li>Angel Wolf is the better dedicated healer.</li>
 *     <li>Salva is the wolf who reacts when the formation is already failing.</li>
 *     <li>Her job is emergency stabilization, extraction-by-rush, regrouping,
 *         cleansing and one-more-chance survival.</li>
 * </ul>
 */
public final class SalvaWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Base balance
    // ---------------------------------------------------------------------

    private static final double SALVA_MAX_HEALTH = 36.0D;
    private static final double SALVA_ATTACK_DAMAGE = 5.5D;
    private static final double SALVA_MOVE_SPEED = 0.31D;

    // Salvation Sense
    public static final double SALVATION_SENSE_RADIUS = 24.0D;
    private static final int DECISION_INTERVAL = 5;

    // Saving Grace
    public static final int SAVING_GRACE_COOLDOWN_TICKS = 20 * 18;
    private static final double SAVING_GRACE_CONTACT_RANGE_SQR = 3.4D * 3.4D;

    // Sanctuary
    public static final double SANCTUARY_RADIUS = 7.0D;
    public static final double SANCTUARY_REGROUP_RADIUS = 18.0D;
    public static final int SANCTUARY_DURATION_TICKS = 20 * 10;
    public static final int SANCTUARY_COOLDOWN_TICKS = 20 * 40;

    // Purifying Howl
    public static final double PURIFY_RADIUS = 12.0D;
    public static final int PURIFY_COOLDOWN_TICKS = 20 * 30;

    // Salvation Rush
    public static final int SALVATION_RUSH_COOLDOWN_TICKS = 20 * 12;
    private static final int SALVATION_RUSH_MAX_TICKS = 35;
    private static final double SALVATION_RUSH_RANGE = 24.0D;

    // Guardian's Bite
    private static final float GUARDIANS_BITE_BONUS_DAMAGE = 3.0F;

    // No One Left Behind
    public static final double NO_ONE_LEFT_BEHIND_RADIUS = 18.0D;
    public static final int NO_ONE_LEFT_BEHIND_DURATION_TICKS = 20 * 14;
    public static final int NO_ONE_LEFT_BEHIND_COOLDOWN_TICKS = 20 * 70;
    public static final float LAST_STAND_TRIGGER_RATIO = 0.20F;
    public static final float LAST_STAND_FLOOR_RATIO = 0.25F;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int savingGraceCooldownTicks;
    private int sanctuaryTicks;
    private int sanctuaryCooldownTicks;
    private Vec3 sanctuaryCenter;
    private int purifyCooldownTicks;
    private int salvationRushCooldownTicks;
    private int salvationRushTicks;
    private LivingEntity salvationRushTarget;
    private int noOneLeftBehindTicks;
    private int noOneLeftBehindCooldownTicks;

    /** One emergency save per eligible wolf per ultimate activation. */
    private final Set<UUID> lastStandSpent = new HashSet<>();

    private static final class BrainHolder {
        private static final Brain.Provider<SalvaWolf> PROVIDER =
                Brain.<SalvaWolf>provider(
                        ImmutableList.of(
                                SensorType.HURT_BY,
                                SensorType.NEAREST_LIVING_ENTITIES),
                        wolf -> List.of());
    }

    public SalvaWolf(
            EntityType<? extends SalvaWolf> type,
            Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Foundation
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, SALVA_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SALVA_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SALVA_MOVE_SPEED)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SALVA_WOLF.get();
    }

    @Override
    protected Brain<SalvaWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SalvaWolf> getBrain() {
        return (Brain<SalvaWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth =
                this.getAttribute(Attributes.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.setBaseValue(SALVA_MAX_HEALTH);
        }

        if (this.isTame()) {
            this.setHealth((float) SALVA_MAX_HEALTH);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isSalvaFamilyMember(target)
                && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Server AI loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickTimers();

        if (this.isBaby()) {
            this.clearAdultRuntime();
            return;
        }

        this.applyRetaliationMemory();

        if (this.sanctuaryTicks > 0) {
            this.tickSanctuary(level);
        }

        if (this.noOneLeftBehindTicks > 0) {
            this.tickNoOneLeftBehind(level);
        }

        if (this.salvationRushTicks > 0) {
            this.tickSalvationRush(level);
        }

        if (this.isOrderedToSit()) {
            return;
        }

        if (this.tickCount % DECISION_INTERVAL != 0) {
            return;
        }

        LivingEntity emergency =
                this.findHighestPriorityEmergency(level);

        if (this.tryStartNoOneLeftBehind(level)) {
            return;
        }

        if (this.tryPurifyingHowl(level)) {
            return;
        }

        if (this.tryStartSanctuary(level)) {
            return;
        }

        if (emergency != null) {
            double distanceSqr =
                    this.distanceToSqr(emergency);

            if (distanceSqr <= SAVING_GRACE_CONTACT_RANGE_SQR
                    && this.savingGraceCooldownTicks <= 0) {
                this.performSavingGrace(level, emergency);
                return;
            }

            if (this.salvationRushTicks <= 0
                    && this.salvationRushCooldownTicks <= 0
                    && distanceSqr <= SALVATION_RUSH_RANGE * SALVATION_RUSH_RANGE) {
                this.startSalvationRush(level, emergency);
            }
        }
    }

    private void tickTimers() {
        if (this.savingGraceCooldownTicks > 0) {
            --this.savingGraceCooldownTicks;
        }
        if (this.sanctuaryCooldownTicks > 0) {
            --this.sanctuaryCooldownTicks;
        }
        if (this.purifyCooldownTicks > 0) {
            --this.purifyCooldownTicks;
        }
        if (this.salvationRushCooldownTicks > 0) {
            --this.salvationRushCooldownTicks;
        }
        if (this.noOneLeftBehindCooldownTicks > 0) {
            --this.noOneLeftBehindCooldownTicks;
        }
    }

    private void clearAdultRuntime() {
        this.salvationRushTicks = 0;
        this.salvationRushTarget = null;
        this.sanctuaryTicks = 0;
        this.sanctuaryCenter = null;
        this.noOneLeftBehindTicks = 0;
        this.lastStandSpent.clear();
    }

    private void applyRetaliationMemory() {
        LivingEntity attacker =
                this.getBrain()
                        .getMemory(MemoryModuleType.HURT_BY_ENTITY)
                        .orElse(null);

        if (this.isThreat(attacker)
                && (this.getTarget() == null
                || !this.getTarget().isAlive())) {
            this.setTarget(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Family / threat helpers
    // ---------------------------------------------------------------------

    public boolean isSalvaFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        if (!this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && entity == owner) {
            return true;
        }

        return entity instanceof Wolf wolf
                && wolf.isTame();
    }

    private List<LivingEntity> findFamily(
            ServerLevel level,
            double radius) {

        List<LivingEntity> family =
                new ArrayList<>();

        family.add(this);

        if (!this.isTame()) {
            return family;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.level() == level
                && this.distanceToSqr(owner) <= radius * radius) {
            family.add(owner);
        }

        family.addAll(
                level.getEntitiesOfClass(
                        Wolf.class,
                        this.getBoundingBox().inflate(radius),
                        wolf -> wolf != this
                                && wolf.isAlive()
                                && wolf.isTame()));

        return family;
    }

    private boolean isThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate instanceof Creeper
                || this.isSalvaFamilyMember(candidate)
                || !this.canAttack(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || this.isTargetingFamily(candidate);
    }

    private boolean isTargetingFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        return target != null
                && this.isSalvaFamilyMember(target);
    }

    private List<LivingEntity> findThreatsAround(
            ServerLevel level,
            LivingEntity center,
            double radius) {

        return level.getEntitiesOfClass(
                LivingEntity.class,
                center.getBoundingBox().inflate(radius),
                this::isThreat);
    }

    private LivingEntity findNearestThreatTo(
            ServerLevel level,
            LivingEntity family,
            double radius) {

        return this.findThreatsAround(level, family, radius)
                .stream()
                .min(Comparator.comparingDouble(family::distanceToSqr))
                .orElse(null);
    }

    private float healthRatio(LivingEntity entity) {
        return entity.getMaxHealth() <= 0.0F
                ? 1.0F
                : entity.getHealth() / entity.getMaxHealth();
    }

    /**
     * A player-issued sit command is stronger than Salva's regroup order.
     *
     * <p>Seated wolves still receive every support effect and may be selected
     * by Salvation Rush / Saving Grace. They simply never get ordered to walk
     * toward Sanctuary or Salva while commanded to sit.</p>
     */
    private boolean isSeatedFamilyWolf(LivingEntity member) {
        return member instanceof Wolf wolf
                && (wolf.isOrderedToSit()
                || wolf.isInSittingPose());
    }

    // ---------------------------------------------------------------------
    // Salvation Sense — passive
    // ---------------------------------------------------------------------

    private LivingEntity findHighestPriorityEmergency(
            ServerLevel level) {

        if (!this.isTame()) {
            return null;
        }

        LivingEntity owner = this.getOwner();

        return this.findFamily(level, SALVATION_SENSE_RADIUS)
                .stream()
                .filter(member -> this.emergencyScore(level, member, owner) >= 24.0D)
                .max(Comparator.comparingDouble(
                        member -> this.emergencyScore(level, member, owner)))
                .orElse(null);
    }

    private double emergencyScore(
            ServerLevel level,
            LivingEntity member,
            LivingEntity owner) {

        float ratio = this.healthRatio(member);
        double score = 0.0D;

        if (ratio <= 0.25F) {
            score += 100.0D;
        } else if (ratio <= 0.40F) {
            score += 72.0D;
        } else if (ratio <= 0.60F) {
            score += 34.0D;
        }

        if (member == owner) {
            score += 12.0D;
        }

        if (this.hasPurifiableEffect(member)) {
            score += 18.0D;
        }

        List<LivingEntity> threats =
                this.findThreatsAround(level, member, 5.5D);

        score += Math.min(4, threats.size()) * 12.0D;

        for (LivingEntity threat : threats) {
            if (this.isTargetingFamily(threat)) {
                score += 12.0D;
            }

            if (threat.getMaxHealth() >= 40.0F) {
                score += 10.0D;
            }
        }

        if (this.distanceToSqr(member) >= 14.0D * 14.0D
                && ratio <= 0.55F) {
            score += 14.0D;
        }

        return score;
    }

    // ---------------------------------------------------------------------
    // Saving Grace
    // ---------------------------------------------------------------------

    private boolean performSavingGrace(
            ServerLevel level,
            LivingEntity member) {

        if (this.savingGraceCooldownTicks > 0
                || !this.isSalvaFamilyMember(member)) {
            return false;
        }

        this.savingGraceCooldownTicks =
                SAVING_GRACE_COOLDOWN_TICKS;

        member.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        20 * 5,
                        1,
                        true,
                        true),
                this);

        member.addEffect(
                new MobEffectInstance(
                        MobEffects.REGENERATION,
                        20 * 6,
                        1,
                        true,
                        true),
                this);

        member.setDeltaMovement(
                member.getDeltaMovement()
                        .multiply(0.35D, 1.0D, 0.35D));

        /*
         * Attribute-style knockback resistance is approximated here with a
         * short, strong physical stabilization: damp existing horizontal knockback
         * and pair it with Resistance/Regeneration. This avoids permanently
         * mutating another entity's attribute modifiers.
         */
        member.hurtMarked = true;

        level.sendParticles(
                ParticleTypes.END_ROD,
                member.getX(),
                member.getY(0.55D),
                member.getZ(),
                28,
                0.45D,
                0.50D,
                0.45D,
                0.035D);

        level.sendParticles(
                ParticleTypes.HEART,
                member.getX(),
                member.getY(0.75D),
                member.getZ(),
                8,
                0.35D,
                0.35D,
                0.35D,
                0.02D);

        this.playSound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                0.95F,
                1.10F);

        return true;
    }

    // ---------------------------------------------------------------------
    // Salvation Rush
    // ---------------------------------------------------------------------

    private void startSalvationRush(
            ServerLevel level,
            LivingEntity member) {

        this.salvationRushTarget = member;
        this.salvationRushTicks =
                SALVATION_RUSH_MAX_TICKS;
        this.salvationRushCooldownTicks =
                SALVATION_RUSH_COOLDOWN_TICKS;

        this.getNavigation().stop();

        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY(0.55D),
                this.getZ(),
                16,
                0.30D,
                0.30D,
                0.30D,
                0.025D);
    }

    private void tickSalvationRush(ServerLevel level) {
        LivingEntity member = this.salvationRushTarget;

        if (member == null
                || !member.isAlive()
                || member.level() != level
                || !this.isSalvaFamilyMember(member)) {
            this.stopSalvationRush();
            return;
        }

        if (--this.salvationRushTicks <= 0) {
            this.stopSalvationRush();
            return;
        }

        double distanceSqr =
                this.distanceToSqr(member);

        if (distanceSqr <= SAVING_GRACE_CONTACT_RANGE_SQR) {
            this.getNavigation().stop();

            if (this.savingGraceCooldownTicks <= 0) {
                this.performSavingGrace(level, member);
            }

            LivingEntity attacker =
                    this.findNearestThreatTo(level, member, 6.0D);

            if (attacker != null) {
                this.setTarget(attacker);
            }

            this.stopSalvationRush();
            return;
        }

        this.getLookControl().setLookAt(member, 45.0F, 45.0F);
        this.getNavigation().moveTo(member, 1.65D);

        /*
         * When the ally is visible, add a real physical rescue burst on top of
         * navigation. It does not teleport through walls/fences like Rift.
         */
        if (this.getSensing().hasLineOfSight(member)
                && distanceSqr >= 5.0D * 5.0D) {

            Vec3 toward =
                    member.position()
                            .subtract(this.position())
                            .multiply(1.0D, 0.0D, 1.0D);

            if (toward.lengthSqr() > 1.0E-5D) {
                toward = toward.normalize();

                Vec3 current = this.getDeltaMovement();
                this.setDeltaMovement(
                        current.x * 0.45D + toward.x * 0.34D,
                        Math.max(current.y, 0.05D),
                        current.z * 0.45D + toward.z * 0.34D);

                this.hurtMarked = true;
            }
        }

        if (this.tickCount % 2 == 0) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY(0.45D),
                    this.getZ(),
                    2,
                    0.12D,
                    0.12D,
                    0.12D,
                    0.0D);
        }
    }

    private void stopSalvationRush() {
        this.salvationRushTicks = 0;
        this.salvationRushTarget = null;
    }

    // ---------------------------------------------------------------------
    // Sanctuary
    // ---------------------------------------------------------------------

    public boolean isSanctuaryActive() {
        return this.sanctuaryTicks > 0;
    }

    private boolean tryStartSanctuary(ServerLevel level) {
        if (!this.isTame()
                || this.sanctuaryCooldownTicks > 0
                || this.sanctuaryTicks > 0) {
            return false;
        }

        List<LivingEntity> family =
                this.findFamily(level, SANCTUARY_REGROUP_RADIUS);

        long wounded = family.stream()
                .filter(member -> this.healthRatio(member) <= 0.55F)
                .count();

        long critical = family.stream()
                .filter(member -> this.healthRatio(member) <= 0.35F)
                .count();

        int threats =
                this.findThreatsAround(level, this, 10.0D).size();

        if (wounded < 2
                && !(critical >= 1 && threats >= 2)) {
            return false;
        }

        this.sanctuaryCenter = this.position();
        this.sanctuaryTicks = SANCTUARY_DURATION_TICKS;
        this.sanctuaryCooldownTicks = SANCTUARY_COOLDOWN_TICKS;

        this.playSound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                1.0F,
                0.82F);

        return true;
    }

    private void tickSanctuary(ServerLevel level) {
        if (this.sanctuaryCenter == null) {
            this.sanctuaryCenter = this.position();
        }

        if (--this.sanctuaryTicks <= 0) {
            this.sanctuaryTicks = 0;
            this.sanctuaryCenter = null;
            return;
        }

        if (this.tickCount % 5 == 0) {
            this.sendSanctuaryRing(level);
        }

        if (this.tickCount % 10 != 0) {
            return;
        }

        for (LivingEntity member
                : this.findFamily(level, SANCTUARY_REGROUP_RADIUS)) {

            double centerDistanceSqr =
                    member.position()
                            .distanceToSqr(this.sanctuaryCenter);

            if (centerDistanceSqr <= SANCTUARY_RADIUS * SANCTUARY_RADIUS) {
                member.addEffect(
                        new MobEffectInstance(
                                MobEffects.RESISTANCE,
                                25,
                                0,
                                true,
                                true),
                        this);

                member.addEffect(
                        new MobEffectInstance(
                                MobEffects.REGENERATION,
                                25,
                                0,
                                true,
                                true),
                        this);
            }

            if (member instanceof Mob mob
                    && member != this
                    && this.healthRatio(member) <= 0.45F) {

                mob.setTarget(null);
                mob.getNavigation().stop();

                /*
                 * Sitting is an explicit owner command.
                 * Buff the wolf where it is and let Salva come to it if needed.
                 */
                if (this.isSeatedFamilyWolf(member)) {
                    continue;
                }

                mob.getNavigation().moveTo(
                        this.sanctuaryCenter.x,
                        this.sanctuaryCenter.y,
                        this.sanctuaryCenter.z,
                        1.35D);
            }
        }
    }

    private void sendSanctuaryRing(ServerLevel level) {
        if (this.sanctuaryCenter == null) {
            return;
        }

        int points = 28;
        for (int i = 0; i < points; ++i) {
            double angle =
                    Math.PI * 2.0D * i / points;

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.sanctuaryCenter.x
                            + Math.cos(angle) * SANCTUARY_RADIUS,
                    this.sanctuaryCenter.y + 0.12D,
                    this.sanctuaryCenter.z
                            + Math.sin(angle) * SANCTUARY_RADIUS,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Purifying Howl
    // ---------------------------------------------------------------------

    private boolean tryPurifyingHowl(ServerLevel level) {
        if (!this.isTame()
                || this.purifyCooldownTicks > 0) {
            return false;
        }

        List<LivingEntity> affected =
                this.findFamily(level, PURIFY_RADIUS)
                        .stream()
                        .filter(this::hasPurifiableEffect)
                        .toList();

        if (affected.isEmpty()) {
            return false;
        }

        this.purifyCooldownTicks =
                PURIFY_COOLDOWN_TICKS;

        for (LivingEntity member : affected) {
            this.cleanseStandardHarmfulEffects(member);

            /* Brief post-cleanse protection against immediately restacking. */
            member.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            20 * 4,
                            0,
                            true,
                            true),
                    this);
        }

        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY(0.65D),
                this.getZ(),
                54,
                1.4D,
                0.65D,
                1.4D,
                0.055D);

        this.playSound(
                SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
                1.0F,
                1.35F);

        return true;
    }

    private boolean hasPurifiableEffect(LivingEntity member) {
        return member.hasEffect(MobEffects.POISON)
                || member.hasEffect(MobEffects.WITHER)
                || member.hasEffect(MobEffects.WEAKNESS)
                || member.hasEffect(MobEffects.SLOWNESS)
                || member.hasEffect(MobEffects.DARKNESS);
    }

    private void cleanseStandardHarmfulEffects(LivingEntity member) {
        member.removeEffect(MobEffects.POISON);
        member.removeEffect(MobEffects.WITHER);
        member.removeEffect(MobEffects.WEAKNESS);
        member.removeEffect(MobEffects.SLOWNESS);
        member.removeEffect(MobEffects.DARKNESS);
    }

    // ---------------------------------------------------------------------
    // Guardian's Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(
            ServerLevel level,
            Entity entity) {

        boolean hurt = super.doHurtTarget(level, entity);

        if (!hurt
                || this.isBaby()
                || !(entity instanceof LivingEntity target)) {
            return hurt;
        }

        if (this.shouldEmpowerGuardiansBite(level, target)) {
            target.hurtServer(
                    level,
                    this.damageSources().mobAttack(this),
                    GUARDIANS_BITE_BONUS_DAMAGE);

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    target.getX(),
                    target.getY(0.55D),
                    target.getZ(),
                    8,
                    0.24D,
                    0.25D,
                    0.24D,
                    0.025D);
        }

        return true;
    }

    private boolean shouldEmpowerGuardiansBite(
            ServerLevel level,
            LivingEntity target) {

        if (this.isTargetingFamily(target)) {
            return true;
        }

        for (LivingEntity member
                : this.findFamily(level, 10.0D)) {

            if (this.healthRatio(member) <= 0.40F
                    && target.distanceToSqr(member) <= 6.0D * 6.0D) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------
    // No One Left Behind — ultimate
    // ---------------------------------------------------------------------

    public boolean isNoOneLeftBehindActive() {
        return this.noOneLeftBehindTicks > 0;
    }

    private boolean tryStartNoOneLeftBehind(ServerLevel level) {
        if (!this.isTame()
                || this.noOneLeftBehindCooldownTicks > 0
                || this.noOneLeftBehindTicks > 0) {
            return false;
        }

        List<LivingEntity> family =
                this.findFamily(level, NO_ONE_LEFT_BEHIND_RADIUS);

        LivingEntity owner = this.getOwner();

        long critical = family.stream()
                .filter(member -> this.healthRatio(member) <= 0.40F)
                .count();

        int nearbyThreats =
                this.findThreatsAround(level, this, NO_ONE_LEFT_BEHIND_RADIUS)
                        .size();

        boolean ownerCollapse =
                owner != null
                        && owner.isAlive()
                        && this.distanceToSqr(owner)
                        <= NO_ONE_LEFT_BEHIND_RADIUS
                        * NO_ONE_LEFT_BEHIND_RADIUS
                        && this.healthRatio(owner) <= 0.30F
                        && this.findNearestThreatTo(level, owner, 8.0D) != null;

        if (!ownerCollapse
                && critical < 2
                && !(critical >= 1 && nearbyThreats >= 4)) {
            return false;
        }

        this.noOneLeftBehindTicks =
                NO_ONE_LEFT_BEHIND_DURATION_TICKS;
        this.noOneLeftBehindCooldownTicks =
                NO_ONE_LEFT_BEHIND_COOLDOWN_TICKS;
        this.lastStandSpent.clear();

        /*
         * Make activation unmistakable. V1 technically worked, but the player
         * could reasonably miss the moment and wonder whether the ultimate
         * actually fired.
         */
        this.sendNoOneLeftBehindActivation(level);

        /*
         * Apply the first support pulse RIGHT NOW instead of waiting for the
         * next tickCount % 10 window.
         */
        this.applyNoOneLeftBehindSupport(level, true);

        return true;
    }

    private void tickNoOneLeftBehind(ServerLevel level) {
        if (--this.noOneLeftBehindTicks <= 0) {
            this.noOneLeftBehindTicks = 0;
            this.lastStandSpent.clear();

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY(0.55D),
                    this.getZ(),
                    18,
                    0.45D,
                    0.35D,
                    0.45D,
                    0.025D);

            return;
        }

        /*
         * Persistent rotating double-ring tells the player:
         * "No One Left Behind is STILL ACTIVE."
         *
         * Sanctuary remains the static large ground ring, so the two abilities
         * now have visibly different silhouettes.
         */
        if (this.tickCount % 5 == 0) {
            this.sendNoOneLeftBehindActiveRing(level);
        }

        if (this.tickCount % 10 == 0) {
            this.applyNoOneLeftBehindSupport(level, false);
        }
    }

    private void applyNoOneLeftBehindSupport(
            ServerLevel level,
            boolean activationPulse) {

        for (LivingEntity member
                : this.findFamily(level, NO_ONE_LEFT_BEHIND_RADIUS)) {

            member.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE,
                            30,
                            1,
                            true,
                            true),
                    this);

            member.addEffect(
                    new MobEffectInstance(
                            MobEffects.REGENERATION,
                            35,
                            0,
                            true,
                            true),
                    this);

            /*
             * Short white outline on activation makes it immediately clear
             * which family members are covered by the ultimate.
             */
            if (activationPulse) {
                member.addEffect(
                        new MobEffectInstance(
                                MobEffects.GLOWING,
                                20 * 2,
                                0,
                                true,
                                false),
                        this);
            }

            /* Strong harmful-effect resistance = repeated emergency cleansing. */
            if (activationPulse
                    || this.tickCount % 20 == 0) {
                this.cleanseStandardHarmfulEffects(member);
            }

            if (member instanceof Mob mob
                    && member != this
                    && this.healthRatio(member) <= 0.45F) {

                mob.setTarget(null);
                mob.getNavigation().stop();

                /*
                 * DO NOT override the player's sit command.
                 * Salva's Rush/Grace can still go TO this wolf.
                 */
                if (!this.isSeatedFamilyWolf(member)) {
                    Vec3 regroup =
                            this.sanctuaryCenter != null
                                    ? this.sanctuaryCenter
                                    : this.position();

                    mob.getNavigation().moveTo(
                            regroup.x,
                            regroup.y,
                            regroup.z,
                            1.45D);
                }
            }

            if (activationPulse
                    || this.tickCount % 20 == 0) {

                level.sendParticles(
                        ParticleTypes.END_ROD,
                        member.getX(),
                        member.getY(0.65D),
                        member.getZ(),
                        activationPulse ? 10 : 4,
                        activationPulse ? 0.28D : 0.18D,
                        activationPulse ? 0.32D : 0.22D,
                        activationPulse ? 0.28D : 0.18D,
                        0.0D);

                if (activationPulse) {
                    level.sendParticles(
                            ParticleTypes.HEART,
                            member.getX(),
                            member.getY(0.85D),
                            member.getZ(),
                            3,
                            0.22D,
                            0.24D,
                            0.22D,
                            0.015D);
                }
            }
        }
    }

    private void sendNoOneLeftBehindActivation(
            ServerLevel level) {

        /*
         * Three expanding salvation rings + vertical burst.
         * Much larger/readier than Sanctuary's one ground perimeter.
         */
        double[] radii = {
                3.5D,
                7.0D,
                10.5D
        };

        for (int ring = 0;
             ring < radii.length;
             ++ring) {

            double radius =
                    radii[ring];

            int points =
                    24 + ring * 8;

            for (int i = 0;
                 i < points;
                 ++i) {

                double angle =
                        Math.PI * 2.0D
                                * i
                                / points;

                level.sendParticles(
                        ParticleTypes.END_ROD,
                        this.getX()
                                + Math.cos(angle)
                                * radius,
                        this.getY()
                                + 0.15D
                                + ring * 0.18D,
                        this.getZ()
                                + Math.sin(angle)
                                * radius,
                        1,
                        0.01D,
                        0.01D,
                        0.01D,
                        0.0D);
            }
        }

        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY(0.70D),
                this.getZ(),
                95,
                1.20D,
                1.10D,
                1.20D,
                0.075D);

        level.sendParticles(
                ParticleTypes.HEART,
                this.getX(),
                this.getY(0.85D),
                this.getZ(),
                20,
                0.80D,
                0.55D,
                0.80D,
                0.035D);

        this.playSound(
                SoundEvents.ALLAY_AMBIENT_WITH_ITEM,
                1.20F,
                0.74F);

        this.playSound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                1.05F,
                0.72F);
    }

    private void sendNoOneLeftBehindActiveRing(
            ServerLevel level) {

        int points = 24;
        double time =
                this.tickCount * 0.10D;

        for (int i = 0;
             i < points;
             ++i) {

            double angle =
                    time
                            + Math.PI
                            * 2.0D
                            * i
                            / points;

            double radius =
                    3.0D
                            + Math.sin(
                            time * 0.70D)
                            * 0.25D;

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX()
                            + Math.cos(angle)
                            * radius,
                    this.getY()
                            + 0.45D
                            + Math.sin(
                            angle * 2.0D)
                            * 0.18D,
                    this.getZ()
                            + Math.sin(angle)
                            * radius,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }
    }

    /**
     * Event-side eligibility for the once-per-ultimate Last Stand.
     *
     * <p>Per the design wording, the one-use interception belongs to eligible
     * wolves. The owner still receives the ultimate's Resistance/Regeneration
     * and regroup support, but the actual Last Stand token is wolf-only.</p>
     */
    public boolean canLastStandProtect(LivingEntity victim) {
        return this.noOneLeftBehindTicks > 0
                && victim instanceof Wolf
                && this.isSalvaFamilyMember(victim)
                && this.distanceToSqr(victim)
                <= NO_ONE_LEFT_BEHIND_RADIUS
                * NO_ONE_LEFT_BEHIND_RADIUS
                && !this.lastStandSpent.contains(victim.getUUID());
    }

    public boolean performLastStandSave(
            ServerLevel level,
            LivingEntity victim) {

        if (!this.canLastStandProtect(victim)) {
            return false;
        }

        this.lastStandSpent.add(victim.getUUID());

        float floor =
                Math.max(
                        1.0F,
                        victim.getMaxHealth()
                                * LAST_STAND_FLOOR_RATIO);

        if (victim.getHealth() < floor) {
            victim.setHealth(floor);
        }

        victim.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        20 * 3,
                        2,
                        true,
                        true),
                this);

        victim.addEffect(
                new MobEffectInstance(
                        MobEffects.REGENERATION,
                        20 * 4,
                        1,
                        true,
                        true),
                this);

        /*
         * Last Stand should never be a mystery. Give the rescued wolf a short
         * outline so the player can identify exactly whose token was spent.
         */
        victim.addEffect(
                new MobEffectInstance(
                        MobEffects.GLOWING,
                        20 * 3,
                        0,
                        true,
                        false),
                this);

        if (victim instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        level.sendParticles(
                ParticleTypes.END_ROD,
                victim.getX(),
                victim.getY(0.60D),
                victim.getZ(),
                70,
                0.62D,
                0.72D,
                0.62D,
                0.075D);

        level.sendParticles(
                ParticleTypes.HEART,
                victim.getX(),
                victim.getY(0.80D),
                victim.getZ(),
                18,
                0.45D,
                0.45D,
                0.45D,
                0.035D);

        /*
         * Grave's successful death interception already proved TOTEM_USE is a
         * valid/current sound in this project. Reuse the audible language for
         * "a lethal/critical outcome was just denied."
         */
        victim.playSound(
                SoundEvents.TOTEM_USE,
                0.95F,
                1.18F);

        return true;
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    /**
     * Wild Salva Wolves are rare solitary highland guardians.
     *
     * <p>The biome tag supplies the actual habitat: Meadow + Grove.
     * MobCategory.AMBIENT is only the practical spawn-budget category; Salva's
     * Wolf AI and temperament remain neutral.</p>
     */
    public static boolean checkSalvaWolfSpawnRules(
            EntityType<SalvaWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Spawn eggs, /summon, breeding and other explicit creation paths
         * should never be blocked by natural habitat rules.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        /*
         * Salva is a highland guardian rather than a generic plains wolf.
         * Meadows and Groves normally satisfy this comfortably.
         */
        if (pos.getY() < 70) {
            return false;
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

        /*
         * Natural highland ground:
         * - normal vanilla wolf-spawnable terrain
         * - solid snow block for Grove compatibility
         *
         * Powder snow is deliberately NOT accepted as a spawn floor.
         */
        var ground =
                level.getBlockState(pos.below());

        if (!ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                && !ground.is(Blocks.SNOW_BLOCK)) {
            return false;
        }

        /*
         * Legendary solitary encounter.
         *
         * Persistent tamed/wild wolves can accumulate, so prevent multiple wild
         * Salvas from clustering in the same small mountain area.
         */
        if (level instanceof ServerLevel serverLevel) {
            int nearbyWild =
                    serverLevel.getEntitiesOfClass(
                                    SalvaWolf.class,
                                    new AABB(pos)
                                            .inflate(
                                                    64.0D,
                                                    32.0D,
                                                    64.0D),
                                    wolf -> wolf.isAlive()
                                            && !wolf.isTame())
                            .size();

            if (nearbyWild >= 1) {
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

        output.putInt(
                "SalvaSavingGraceCooldown",
                this.savingGraceCooldownTicks);
        output.putInt(
                "SalvaSanctuaryCooldown",
                this.sanctuaryCooldownTicks);
        output.putInt(
                "SalvaPurifyCooldown",
                this.purifyCooldownTicks);
        output.putInt(
                "SalvaRushCooldown",
                this.salvationRushCooldownTicks);
        output.putInt(
                "SalvaUltimateCooldown",
                this.noOneLeftBehindCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        AttributeInstance maxHealth =
                this.getAttribute(Attributes.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.setBaseValue(SALVA_MAX_HEALTH);
        }

        super.readAdditionalSaveData(input);

        if (maxHealth != null) {
            maxHealth.setBaseValue(SALVA_MAX_HEALTH);
        }

        this.savingGraceCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "SalvaSavingGraceCooldown",
                                0));

        this.sanctuaryCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "SalvaSanctuaryCooldown",
                                0));

        this.purifyCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "SalvaPurifyCooldown",
                                0));

        this.salvationRushCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "SalvaRushCooldown",
                                0));

        this.noOneLeftBehindCooldownTicks =
                Math.max(
                        0,
                        input.getIntOr(
                                "SalvaUltimateCooldown",
                                0));

        /*
         * Active rescue/zone/ultimate state intentionally does not survive a
         * reload. This prevents a reload from granting a fresh Last Stand token
         * during the same ultimate activation.
         */
        this.salvationRushTicks = 0;
        this.salvationRushTarget = null;
        this.sanctuaryTicks = 0;
        this.sanctuaryCenter = null;
        this.noOneLeftBehindTicks = 0;
        this.lastStandSpent.clear();
    }

    // ---------------------------------------------------------------------
    // Test accessors
    // ---------------------------------------------------------------------

    public boolean isSavingGraceReady() {
        return this.savingGraceCooldownTicks <= 0;
    }

    public boolean isPurifyingHowlReady() {
        return this.purifyCooldownTicks <= 0;
    }

    public boolean isSalvationRushActive() {
        return this.salvationRushTicks > 0;
    }

    public boolean isNoOneLeftBehindReady() {
        return this.noOneLeftBehindCooldownTicks <= 0;
    }

    public boolean hasLastStandToken(LivingEntity wolf) {
        return this.noOneLeftBehindTicks > 0
                && wolf instanceof Wolf
                && this.isSalvaFamilyMember(wolf)
                && !this.lastStandSpent.contains(wolf.getUUID());
    }
}
