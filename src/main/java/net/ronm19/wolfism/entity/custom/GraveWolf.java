package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.wolf.Wolf;
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
 * Grave Wolf #34 — "The Soulbound Guardian".
 *
 * <p>Identity: quiet death-magic protector. Grave does not compete with Demon
 * for raw offensive pressure and does not compete with Spirit/Angel for normal
 * healing. He turns nearby deaths into a limited soul resource, uses that death
 * energy for territory control and emergency intervention, and can spatially
 * move through soul energy to reach the fight.</p>
 *
 * <p>Core kit:</p>
 * <ul>
 *     <li><b>Soul Collector</b> — absorbs nearby hostile deaths.</li>
 *     <li><b>Soul Step</b> — short safe tactical teleport through soul energy.</li>
 *     <li><b>Graveyard</b> — static soul field that weakens enemies.</li>
 *     <li><b>Death Interception</b> — prevents a lethal hit on family at a cost.</li>
 * </ul>
 */
public final class GraveWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Balance
    // ---------------------------------------------------------------------

    private static final double GRAVE_MAX_HEALTH = 34.0D;
    private static final double GRAVE_ATTACK_DAMAGE = 6.0D;
    private static final double GRAVE_MOVE_SPEED = 0.30D;

    public static final int MAX_SOULS = 6;
    public static final double SOUL_COLLECTION_RADIUS = 12.0D;

    public static final int SOUL_STEP_COOLDOWN_TICKS = 20 * 9;
    private static final double SOUL_STEP_MIN_RANGE = 5.0D;
    private static final double SOUL_STEP_MAX_RANGE = 15.0D;
    private static final int SOUL_STEP_VERTICAL_SEARCH = 4;

    public static final double GRAVEYARD_RADIUS = 8.0D;
    public static final int GRAVEYARD_DURATION_TICKS = 20 * 10;
    public static final int GRAVEYARD_COOLDOWN_TICKS = 20 * 45;
    public static final int GRAVEYARD_SOUL_COST = 2;
    private static final int GRAVEYARD_PULSE_INTERVAL = 10;

    public static final double DEATH_INTERCEPTION_RADIUS = 12.0D;
    public static final int DEATH_INTERCEPTION_COOLDOWN_TICKS = 20 * 30;
    public static final int DEATH_INTERCEPTION_SOUL_COST = 2;
    private static final float DEATH_INTERCEPTION_GRAVE_COST = 4.0F;

    private static final int ABILITY_DECISION_INTERVAL = 10;

    // ---------------------------------------------------------------------
    // Synced state
    // ---------------------------------------------------------------------

    private static final EntityDataAccessor<Integer> DATA_SOULS =
            SynchedEntityData.defineId(GraveWolf.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_GRAVEYARD_ACTIVE =
            SynchedEntityData.defineId(GraveWolf.class, EntityDataSerializers.BOOLEAN);

    // ---------------------------------------------------------------------
    // Runtime
    // ---------------------------------------------------------------------

    private int soulStepCooldownTicks;
    private int graveyardCooldownTicks;
    private int graveyardTicks;
    private int deathInterceptionCooldownTicks;
    private int abilityLockoutTicks;

    private Vec3 graveyardCenter;

    /**
     * Grave uses vanilla sensors instead of adding more registry objects for this
     * first pass. HURT_BY provides Brain-owned retaliation context and
     * NEAREST_LIVING_ENTITIES gives the Brain a real battlefield perception loop.
     * The actual named abilities remain explicit mechanics in this class.
     */
    private static final class BrainHolder {
        private static final Brain.Provider<GraveWolf> PROVIDER = Brain.<GraveWolf>provider(
                ImmutableList.of(
                        SensorType.HURT_BY,
                        SensorType.NEAREST_LIVING_ENTITIES),
                wolf -> List.of());
    }

    public GraveWolf(EntityType<? extends GraveWolf> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Foundation
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, GRAVE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, GRAVE_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, GRAVE_MOVE_SPEED)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SOULS, 0);
        builder.define(DATA_GRAVEYARD_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.GRAVE_WOLF.get();
    }

    @Override
    protected Brain<GraveWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<GraveWolf> getBrain() {
        return (Brain<GraveWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(GRAVE_MAX_HEALTH);
        }

        if (this.isTame()) {
            this.setHealth((float) GRAVE_MAX_HEALTH);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isGraveFamilyMember(target)
                && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Server loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickGraveTimers();

        if (this.isBaby()) {
            this.clearAdultState();
            return;
        }

        /*
         * An already-created Graveyard remains a static battlefield effect even
         * if Grave is subsequently ordered to sit.
         */
        if (this.graveyardTicks > 0) {
            this.tickGraveyard(level);
        }

        if (this.isOrderedToSit()) {
            return;
        }

        this.applyBrainRetaliationMemory();

        if (this.abilityLockoutTicks > 0
                || this.tickCount % ABILITY_DECISION_INTERVAL != 0) {
            return;
        }

        /*
         * Graveyard has priority when the battlefield is genuinely dangerous.
         * Otherwise Soul Step solves positioning.
         */
        if (this.graveyardTicks <= 0 && this.tryStartGraveyard(level)) {
            return;
        }

        this.trySoulStep(level);
    }

    private void tickGraveTimers() {
        if (this.soulStepCooldownTicks > 0) {
            --this.soulStepCooldownTicks;
        }
        if (this.graveyardCooldownTicks > 0) {
            --this.graveyardCooldownTicks;
        }
        if (this.deathInterceptionCooldownTicks > 0) {
            --this.deathInterceptionCooldownTicks;
        }
        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }
    }

    private void clearAdultState() {
        this.setTarget(null);
        this.graveyardTicks = 0;
        this.graveyardCenter = null;
        this.setGraveyardActive(false);
        this.setSoulCount(0);
    }

    private void applyBrainRetaliationMemory() {
        LivingEntity attacker = this.getBrain()
                .getMemory(MemoryModuleType.HURT_BY_ENTITY)
                .orElse(null);

        if (this.isRelevantGraveThreat(attacker)
                && (this.getTarget() == null || !this.getTarget().isAlive())) {
            this.setTarget(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Family / threat rules
    // ---------------------------------------------------------------------

    /**
     * Mirrors Wolfism's broad family rule: once tamed, Grave protects his owner
     * and tamed wolves rather than only Grave Wolves.
     */
    public boolean isGraveFamilyMember(LivingEntity entity) {
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

        return entity instanceof Wolf wolf && wolf.isTame();
    }

    private boolean isTargetingProtectedFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        return target != null && this.isGraveFamilyMember(target);
    }

    private boolean isRelevantGraveThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || this.isGraveFamilyMember(candidate)
                || !this.canAttack(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || this.isTargetingProtectedFamily(candidate);
    }

    private List<LivingEntity> findGraveThreats(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isRelevantGraveThreat);
    }

    private LivingEntity findCriticalFamily(ServerLevel level) {
        if (!this.isTame()) {
            return null;
        }

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(SOUL_STEP_MAX_RANGE),
                        member -> member != this
                                && this.isGraveFamilyMember(member)
                                && member.getMaxHealth() > 0.0F
                                && member.getHealth() <= member.getMaxHealth() * 0.40F)
                .stream()
                .filter(member -> this.distanceToSqr(member) >= 6.5D * 6.5D)
                .min(Comparator.comparingDouble(
                        member -> member.getHealth() / member.getMaxHealth()))
                .orElse(null);
    }

    // ---------------------------------------------------------------------
    // 1) Soul Collector
    // ---------------------------------------------------------------------

    public int getSoulCount() {
        return this.entityData.get(DATA_SOULS);
    }

    private void setSoulCount(int souls) {
        this.entityData.set(DATA_SOULS, Mth.clamp(souls, 0, MAX_SOULS));
    }

    public boolean hasSouls(int amount) {
        return amount <= 0 || this.getSoulCount() >= amount;
    }

    public boolean spendSouls(int amount) {
        if (amount <= 0) {
            return true;
        }

        if (!this.hasSouls(amount)) {
            return false;
        }

        this.setSoulCount(this.getSoulCount() - amount);
        return true;
    }

    /**
     * Called by GraveWolfGameplayEvents when a nearby hostile dies.
     */
    public boolean absorbSoul(ServerLevel level, LivingEntity fallen, int amount) {
        if (this.isBaby()
                || !this.isAlive()
                || amount <= 0
                || this.getSoulCount() >= MAX_SOULS) {
            return false;
        }

        int before = this.getSoulCount();
        this.setSoulCount(before + amount);

        if (this.getSoulCount() <= before) {
            return false;
        }

        this.sendSoulTether(level, fallen.position().add(
                0.0D,
                fallen.getBbHeight() * 0.55D,
                0.0D));

        WolfVfx.sendParticles("grave_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(),
                this.getY(0.65D),
                this.getZ(),
                8,
                0.30D,
                0.32D,
                0.30D,
                0.015D);

        return true;
    }

    private void sendSoulTether(ServerLevel level, Vec3 origin) {
        Vec3 destination = this.position().add(
                0.0D,
                this.getBbHeight() * 0.65D,
                0.0D);

        for (int i = 1; i <= 9; ++i) {
            double t = i / 10.0D;
            WolfVfx.sendParticles("grave_wolf", level,
                    ParticleTypes.REVERSE_PORTAL,
                    Mth.lerp(t, origin.x, destination.x),
                    Mth.lerp(t, origin.y, destination.y),
                    Mth.lerp(t, origin.z, destination.z),
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // 2) Soul Step
    // ---------------------------------------------------------------------

    private boolean trySoulStep(ServerLevel level) {
        if (this.soulStepCooldownTicks > 0
                || !this.hasSouls(1)
                || this.isOrderedToSit()) {
            return false;
        }

        LivingEntity destinationAnchor = null;
        boolean combatStep = false;

        LivingEntity target = this.getTarget();
        if (this.isRelevantGraveThreat(target)) {
            double distanceSqr = this.distanceToSqr(target);
            boolean usefulDistance =
                    distanceSqr >= SOUL_STEP_MIN_RANGE * SOUL_STEP_MIN_RANGE
                            && distanceSqr <= SOUL_STEP_MAX_RANGE * SOUL_STEP_MAX_RANGE;

            boolean blocked = !this.getSensing().hasLineOfSight(target);
            boolean largeGap = distanceSqr >= 8.0D * 8.0D;

            if (usefulDistance && (blocked || largeGap)) {
                destinationAnchor = target;
                combatStep = true;
            }
        }

        if (destinationAnchor == null) {
            destinationAnchor = this.findCriticalFamily(level);
        }

        if (destinationAnchor == null) {
            return false;
        }

        double currentAngle = Math.atan2(
                this.getZ() - destinationAnchor.getZ(),
                this.getX() - destinationAnchor.getX());

        /*
         * Combat steps favor the opposite side of the target for a true spectral
         * flank. Protective steps arrive on Grave's current side of family.
         */
        double preferredAngle = combatStep
                ? currentAngle + Math.PI
                : currentAngle;

        Vec3 safe = this.findSafeSoulStepPosition(
                level,
                destinationAnchor,
                2.35D,
                preferredAngle);

        if (safe == null) {
            return false;
        }

        this.performSoulStep(level, safe, destinationAnchor);
        this.soulStepCooldownTicks = SOUL_STEP_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 12;
        return true;
    }

    private Vec3 findSafeSoulStepPosition(
            ServerLevel level,
            LivingEntity anchor,
            double radius,
            double preferredAngle) {

        double[] angleOffsets = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        for (int ring = 0; ring < 2; ++ring) {
            double currentRadius = radius + ring * 1.10D;

            for (double offset : angleOffsets) {
                double angle = preferredAngle + offset;
                double x = anchor.getX() + Math.cos(angle) * currentRadius;
                double z = anchor.getZ() + Math.sin(angle) * currentRadius;

                Vec3 safe = this.findSafeSoulLanding(
                        level,
                        x,
                        anchor.getY(),
                        z);

                if (safe != null
                        && !this.isSoulStepDestinationOccupied(
                        level,
                        safe,
                        anchor)) {
                    return safe;
                }
            }
        }

        return null;
    }

    private Vec3 findSafeSoulLanding(
            ServerLevel level,
            double x,
            double referenceY,
            double z) {

        int startY = (int) Math.floor(referenceY) + SOUL_STEP_VERTICAL_SEARCH;
        int endY = (int) Math.floor(referenceY) - SOUL_STEP_VERTICAL_SEARCH;

        for (int y = startY; y >= endY; --y) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (this.isSafeSoulStepPosition(level, pos)) {
                return new Vec3(
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D);
            }
        }

        return null;
    }

    private boolean isSafeSoulStepPosition(
            ServerLevel level,
            BlockPos pos) {

        if (pos.getY() <= level.getMinY()
                || pos.getY() + 1 >= level.getMaxY()) {
            return false;
        }

        BlockPos head = pos.above();
        BlockPos floor = pos.below();

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(head).isEmpty()
                || !level.getFluidState(floor).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(head)
                .getCollisionShape(level, head)
                .isEmpty()
                || level.getBlockState(floor)
                .getCollisionShape(level, floor)
                .isEmpty()) {
            return false;
        }

        int supportedNeighbors = 0;
        int[][] offsets = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] offset : offsets) {
            BlockPos neighborFloor =
                    floor.offset(offset[0], 0, offset[1]);

            if (level.getFluidState(neighborFloor).isEmpty()
                    && !level.getBlockState(neighborFloor)
                    .getCollisionShape(level, neighborFloor)
                    .isEmpty()) {
                ++supportedNeighbors;
            }
        }

        return supportedNeighbors >= 2;
    }

    private boolean isSoulStepDestinationOccupied(
            ServerLevel level,
            Vec3 destination,
            LivingEntity intendedAnchor) {

        AABB landingBox = new AABB(
                destination.x - 0.45D,
                destination.y,
                destination.z - 0.45D,
                destination.x + 0.45D,
                destination.y + 1.25D,
                destination.z + 0.45D);

        return !level.getEntitiesOfClass(
                        LivingEntity.class,
                        landingBox,
                        entity -> entity != this
                                && entity != intendedAnchor
                                && entity.isAlive())
                .isEmpty();
    }

    private void performSoulStep(
            ServerLevel level,
            Vec3 destination,
            LivingEntity anchor) {

        Vec3 origin = this.position();

        this.getNavigation().stop();
        this.setPos(destination.x, destination.y, destination.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.getLookControl().setLookAt(anchor, 90.0F, 90.0F);

        this.playSound(
                SoundEvents.ENDERMAN_TELEPORT,
                0.70F,
                0.72F + this.random.nextFloat() * 0.08F);

        WolfVfx.sendParticles("grave_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                origin.x,
                origin.y + 0.45D,
                origin.z,
                22,
                0.30D,
                0.35D,
                0.30D,
                0.045D);

        WolfVfx.sendParticles("grave_wolf", level,
                ParticleTypes.SOUL_FIRE_FLAME,
                destination.x,
                destination.y + 0.45D,
                destination.z,
                16,
                0.30D,
                0.35D,
                0.30D,
                0.025D);
    }

    // ---------------------------------------------------------------------
    // 3) Graveyard
    // ---------------------------------------------------------------------

    private boolean tryStartGraveyard(ServerLevel level) {
        if (this.graveyardCooldownTicks > 0
                || this.graveyardTicks > 0
                || !this.hasSouls(GRAVEYARD_SOUL_COST)) {
            return false;
        }

        List<LivingEntity> threats =
                this.findGraveThreats(level, GRAVEYARD_RADIUS);

        if (threats.isEmpty()) {
            return false;
        }

        boolean familyEmergency = threats.size() >= 2
                && threats.stream().anyMatch(
                this::isTargetingProtectedFamily);

        LivingEntity primary = this.getTarget();
        boolean heavyweight = this.isRelevantGraveThreat(primary)
                && primary.distanceToSqr(this)
                <= GRAVEYARD_RADIUS * GRAVEYARD_RADIUS
                && primary.getMaxHealth() >= 50.0F;

        if (threats.size() < 3
                && !familyEmergency
                && !heavyweight) {
            return false;
        }

        if (!this.spendSouls(GRAVEYARD_SOUL_COST)) {
            return false;
        }

        this.graveyardCenter = this.position();
        this.graveyardTicks = GRAVEYARD_DURATION_TICKS;
        this.graveyardCooldownTicks = GRAVEYARD_COOLDOWN_TICKS;
        this.abilityLockoutTicks = 30;
        this.setGraveyardActive(true);

        this.sendRing(
                level,
                this.graveyardCenter,
                2.5D,
                28,
                ParticleTypes.SOUL_FIRE_FLAME);

        this.sendRing(
                level,
                this.graveyardCenter,
                GRAVEYARD_RADIUS,
                52,
                ParticleTypes.REVERSE_PORTAL);

        this.playSound(
                this.getWolfismHowlSound(),
                0.55F,
                0.62F);

        return true;
    }

    private void tickGraveyard(ServerLevel level) {
        if (this.graveyardCenter == null) {
            this.finishGraveyard();
            return;
        }

        --this.graveyardTicks;

        if (this.tickCount % 5 == 0) {
            this.sendRing(
                    level,
                    this.graveyardCenter,
                    GRAVEYARD_RADIUS,
                    24,
                    ParticleTypes.REVERSE_PORTAL);
        }

        if (this.tickCount % GRAVEYARD_PULSE_INTERVAL == 0) {
            AABB zone = new AABB(
                    this.graveyardCenter.x - GRAVEYARD_RADIUS,
                    this.graveyardCenter.y - 3.0D,
                    this.graveyardCenter.z - GRAVEYARD_RADIUS,
                    this.graveyardCenter.x + GRAVEYARD_RADIUS,
                    this.graveyardCenter.y + 4.0D,
                    this.graveyardCenter.z + GRAVEYARD_RADIUS);

            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    zone,
                    this::isRelevantGraveThreat)) {

                if (target.position()
                        .distanceToSqr(this.graveyardCenter)
                        > GRAVEYARD_RADIUS * GRAVEYARD_RADIUS) {
                    continue;
                }

                target.addEffect(
                        new MobEffectInstance(
                                MobEffects.WEAKNESS,
                                50,
                                0,
                                true,
                                true),
                        this);

                target.addEffect(
                        new MobEffectInstance(
                                MobEffects.SLOWNESS,
                                50,
                                0,
                                true,
                                true),
                        this);

                WolfVfx.sendParticles("grave_wolf", level,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        target.getX(),
                        target.getY(0.35D),
                        target.getZ(),
                        3,
                        0.25D,
                        0.22D,
                        0.25D,
                        0.01D);
            }
        }

        if (this.graveyardTicks <= 0) {
            this.finishGraveyard();
        }
    }

    private void finishGraveyard() {
        this.graveyardTicks = 0;
        this.graveyardCenter = null;
        this.setGraveyardActive(false);
    }

    public boolean isGraveyardActive() {
        return this.entityData.get(DATA_GRAVEYARD_ACTIVE);
    }

    private void setGraveyardActive(boolean active) {
        this.entityData.set(DATA_GRAVEYARD_ACTIVE, active);
    }

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {

        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;

            WolfVfx.sendParticles("grave_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.12D,
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.015D,
                    0.02D,
                    0.015D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // 4) Death Interception
    // ---------------------------------------------------------------------

    public int getDeathInterceptionCooldownTicks() {
        return this.deathInterceptionCooldownTicks;
    }

    public boolean canInterceptDeathFor(LivingEntity familyMember) {
        return this.isAlive()
                && !this.isBaby()
                && this.isTame()
                && familyMember != null
                && familyMember != this
                && familyMember.isAlive()
                && this.isGraveFamilyMember(familyMember)
                && this.deathInterceptionCooldownTicks <= 0
                && this.hasSouls(DEATH_INTERCEPTION_SOUL_COST)
                && this.distanceToSqr(familyMember)
                <= DEATH_INTERCEPTION_RADIUS
                * DEATH_INTERCEPTION_RADIUS;
    }

    /**
     * Called only after GraveWolfGameplayEvents has confirmed the incoming hit
     * is genuinely lethal and canceled that damage event.
     */
    public boolean performDeathInterception(
            ServerLevel level,
            LivingEntity familyMember) {

        if (!this.canInterceptDeathFor(familyMember)
                || !this.spendSouls(DEATH_INTERCEPTION_SOUL_COST)) {
            return false;
        }

        this.deathInterceptionCooldownTicks =
                DEATH_INTERCEPTION_COOLDOWN_TICKS;

        float rescueHealth = Math.max(
                1.0F,
                familyMember.getMaxHealth() * 0.20F);

        // Incoming damage is inspected before armor/resistance. A rescue is
        // always a health floor, never a penalty for an already healthy ally.
        familyMember.setHealth(Math.max(familyMember.getHealth(), rescueHealth));

        familyMember.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        20 * 3,
                        1,
                        true,
                        true),
                this);

        familyMember.addEffect(
                new MobEffectInstance(
                        MobEffects.REGENERATION,
                        20 * 4,
                        0,
                        true,
                        true),
                this);

        /*
         * Every save comes at a cost. Grave pays real health but cannot kill
         * himself with the interception payment.
         */
        this.setHealth(Math.max(
                1.0F,
                this.getHealth() - DEATH_INTERCEPTION_GRAVE_COST));

        Vec3 familyCenter = familyMember.position().add(
                0.0D,
                familyMember.getBbHeight() * 0.55D,
                0.0D);

        Vec3 graveCenter = this.position().add(
                0.0D,
                this.getBbHeight() * 0.60D,
                0.0D);

        for (int i = 0; i < 16; ++i) {
            double t = i / 15.0D;
            WolfVfx.sendParticles("grave_wolf", level,
                    ParticleTypes.REVERSE_PORTAL,
                    Mth.lerp(t, graveCenter.x, familyCenter.x),
                    Mth.lerp(t, graveCenter.y, familyCenter.y),
                    Mth.lerp(t, graveCenter.z, familyCenter.z),
                    1,
                    0.02D,
                    0.02D,
                    0.02D,
                    0.0D);
        }

        WolfVfx.emergency(this, familyMember);

        WolfVfx.sendParticles("grave_wolf", level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY(0.55D),
                this.getZ(),
                22,
                0.40D,
                0.45D,
                0.40D,
                0.04D);

        this.playSound(
                SoundEvents.TOTEM_USE,
                0.85F,
                0.68F);

        return true;
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    /**
     * Grave Wolf is a rare, neutral, nocturnal surface encounter.
     *
     * <p>Biome placement is handled by the Grave spawn biome tag. This predicate
     * keeps the species on normal wolf-valid ground (plus Pale Moss for the Pale
     * Garden), requires nighttime for natural/chunk-generation spawning, and
     * prevents persistent wild animals from accumulating indefinitely.</p>
     */
    public static boolean checkGraveWolfSpawnRules(
            EntityType<GraveWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Placement predicates should not make explicit/admin spawning awkward.
         * The actual natural restrictions matter for NATURAL and chunk generation.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        if (!(level instanceof Level actualLevel)
                || !actualLevel.isDarkOutside()) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        // Forest plants can occupy these blocks without obstructing a wolf.
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        var ground = level.getBlockState(pos.below());
        if (!ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                && !ground.is(Blocks.PALE_MOSS_BLOCK)) {
            return false;
        }

        /*
         * Grave Wolves are persistent animals once spawned, so keep the local
         * wild population tiny. Two in the wider area is enough for a rare
         * supernatural encounter without building up forever.
         */
        if (level instanceof ServerLevel serverLevel) {
            int nearbyWild = serverLevel.getEntitiesOfClass(
                            GraveWolf.class,
                            new AABB(pos).inflate(56.0D, 24.0D, 56.0D),
                            wolf -> wolf.isAlive() && !wolf.isTame())
                    .size();

            if (nearbyWild >= 2) {
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

        output.putInt("GraveSouls", this.getSoulCount());
        output.putInt(
                "GraveSoulStepCooldown",
                this.soulStepCooldownTicks);
        output.putInt(
                "GraveGraveyardCooldown",
                this.graveyardCooldownTicks);
        output.putInt(
                "GraveGraveyardTicks",
                this.graveyardTicks);
        output.putInt(
                "GraveDeathInterceptionCooldown",
                this.deathInterceptionCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        AttributeInstance maxHealth =
                this.getAttribute(Attributes.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.setBaseValue(GRAVE_MAX_HEALTH);
        }

        super.readAdditionalSaveData(input);

        if (maxHealth != null) {
            maxHealth.setBaseValue(GRAVE_MAX_HEALTH);
        }

        this.setSoulCount(Mth.clamp(
                input.getIntOr("GraveSouls", 0),
                0,
                MAX_SOULS));

        this.soulStepCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "GraveSoulStepCooldown",
                        0));

        this.graveyardCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "GraveGraveyardCooldown",
                        0));

        this.graveyardTicks = Mth.clamp(
                input.getIntOr(
                        "GraveGraveyardTicks",
                        0),
                0,
                GRAVEYARD_DURATION_TICKS);

        this.deathInterceptionCooldownTicks = Math.max(
                0,
                input.getIntOr(
                        "GraveDeathInterceptionCooldown",
                        0));

        /*
         * A static world position is intentionally not serialized. If the world
         * reloads during Graveyard, restart its remaining duration from Grave's
         * current location instead of resurrecting a stale cross-chunk coordinate.
         */
        this.graveyardCenter =
                this.graveyardTicks > 0 && !this.isBaby()
                        ? this.position()
                        : null;

        this.setGraveyardActive(
                this.graveyardTicks > 0
                        && !this.isBaby());

        if (this.isBaby()) {
            this.clearAdultState();
        }
    }
}
