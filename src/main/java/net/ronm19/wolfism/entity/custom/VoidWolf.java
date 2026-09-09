package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
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
 * Wolfism #36 — Void Wolf ♂.
 *
 * <p>Void / Rescue / Spatial / End Support / Annihilation.</p>
 *
 * <p>Identity boundary:</p>
 * <ul>
 *     <li>End Wolf controls her own short tactical positioning.</li>
 *     <li>Rift Wolf transports entities through space.</li>
 *     <li>Void Wolf prevents spatial/environmental catastrophe and turns the
 *         End into a safer battlefield for his family.</li>
 * </ul>
 */
public final class VoidWolf extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Base balance
    // ---------------------------------------------------------------------

    private static final double VOID_MAX_HEALTH = 34.0D;
    private static final double VOID_ATTACK_DAMAGE = 6.5D;
    private static final double VOID_MOVE_SPEED = 0.30D;

    // Void Awareness / Void Recall
    public static final double VOID_AWARENESS_RADIUS = 48.0D;
    public static final double VOID_RECALL_MAX_RADIUS = 64.0D;
    private static final double VOID_RECALL_VERTICAL_SCAN = 192.0D;
    private static final double VOID_RECALL_GENERIC_TRIGGER_ABOVE_MIN_Y = 8.0D;
    private static final double VOID_RECALL_END_TRIGGER_Y = 0.0D;
    private static final int VOID_RECALL_GROUND_PROBE_DEPTH = 16;
    private static final int SAFE_POSITION_SAMPLE_INTERVAL = 5;
    private static final int VOID_RECALL_PER_MEMBER_COOLDOWN = 20 * 8;
    private static final int SAFE_SUPPORT_REQUIRED = 6;
    private static final int SAFE_HAZARD_RADIUS = 2;

    // Void Cataclysm
    public static final double VOID_CATACLYSM_RADIUS = 10.0D;
    public static final int VOID_CATACLYSM_DURATION_TICKS = 20 * 10;
    public static final int VOID_CATACLYSM_COOLDOWN_TICKS = 20 * 50;

    // Blink
    public static final int BLINK_COOLDOWN_TICKS = 20 * 14;
    private static final double BLINK_MIN_RANGE = 4.0D;
    private static final double BLINK_MAX_RANGE = 26.0D;

    // Abyssal Command
    public static final double ABYSSAL_COMMAND_RADIUS = 20.0D;
    private static final int ABYSSAL_COMMAND_INTERVAL = 4;

    // Quick Void Shot — normal ranged pressure outside the ultimate
    public static final int QUICK_VOID_SHOT_COOLDOWN_TICKS = 20 * 6;
    private static final double QUICK_VOID_SHOT_MIN_RANGE_SQR = 4.0D * 4.0D;
    private static final double QUICK_VOID_SHOT_MAX_RANGE_SQR = 20.0D * 20.0D;
    private static final double QUICK_VOID_SHOT_SPEED = 1.15D;
    private static final int QUICK_VOID_SHOT_MAX_TICKS = 26;
    private static final double QUICK_VOID_SHOT_HIT_DISTANCE_SQR = 1.05D * 1.05D;
    private static final float QUICK_VOID_SHOT_DAMAGE = 4.5F;

    // Eye of Void
    public static final int EYE_OF_VOID_DURATION_TICKS = 20 * 12;
    public static final int EYE_OF_VOID_COOLDOWN_TICKS = 20 * 75;
    public static final double EYE_OF_VOID_RANGE = 48.0D;
    private static final double EYE_OF_VOID_VERTICAL_RANGE = 64.0D;

    private static final double EYE_WILD_VOID_SUPPRESSION_RADIUS = 32.0D;
    private static final int EYE_ORB_FIRE_INTERVAL = 35;
    private static final int EYE_MAX_CONCURRENT_SHOTS = 2;
    private static final double VOID_SHOT_SPEED = 0.78D;
    private static final int VOID_SHOT_MAX_TICKS = 45;
    private static final double VOID_SHOT_HIT_DISTANCE_SQR = 1.35D * 1.35D;
    private static final float VOID_SHOT_DAMAGE = 8.5F;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    /**
     * Last strict safe point for each protected family member.
     * Runtime-only for V1: no emergency position is guessed on the fly.
     */
    private final Map<UUID, SafeSpot> lastSafePositions = new HashMap<>();
    private SafeSpot ownerLastSafePosition;
    private final Map<UUID, Integer> recallCooldowns = new HashMap<>();

    private int voidCataclysmTicks;
    private int voidCataclysmCooldownTicks;
    private final Set<Integer> cataclysmDeflectedProjectiles = new HashSet<>();

    private int blinkCooldownTicks;
    private int quickVoidShotCooldownTicks;
    private QuickVoidShot quickVoidShot;

    private int eyeOfVoidTicks;
    private int eyeOfVoidCooldownTicks;
    private int eyeOrbFireTicks;
    private final List<VoidShot> voidShots = new ArrayList<>();

    private static final class BrainHolder {
        private static final Brain.Provider<VoidWolf> PROVIDER = Brain.<VoidWolf>provider(
                ImmutableList.of(
                        SensorType.HURT_BY,
                        SensorType.NEAREST_LIVING_ENTITIES),
                wolf -> List.of());
    }

    public VoidWolf(EntityType<? extends VoidWolf> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Foundation
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, VOID_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, VOID_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, VOID_MOVE_SPEED)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.VOID_WOLF.get();
    }

    @Override
    protected Brain<VoidWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<VoidWolf> getBrain() {
        return (Brain<VoidWolf>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(VOID_MAX_HEALTH);
        }
        if (this.isTame()) {
            this.setHealth((float) VOID_MAX_HEALTH);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isVoidFamilyMember(target)
                && super.canAttack(target);
    }

    // ---------------------------------------------------------------------
    // Voidborn
    // ---------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        /*
         * Eye of Void is Void's maximum state. For its active ~12 seconds,
         * incoming damage is completely rejected.
         */
        if (this.eyeOfVoidTicks > 0) {
            return true;
        }

        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return true;
        }

        return super.isInvulnerableTo(level, source);
    }

    // ---------------------------------------------------------------------
    // Server loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickCooldowns();
        this.tickQuickVoidShot(level);
        this.tickVoidShots(level);
        this.tickVoidAwarenessAndRecall(level);

        if (this.isBaby()) {
            this.endEyeOfVoidIfNeeded();
            this.voidCataclysmTicks = 0;
            return;
        }

        this.applyRetaliationMemory();

        if (this.eyeOfVoidTicks > 0) {
            this.tickEyeOfVoid(level);
            return;
        }

        if (this.voidCataclysmTicks > 0) {
            this.tickVoidCataclysm(level);
        }

        if (Math.floorMod(this.tickCount + this.getId(), ABYSSAL_COMMAND_INTERVAL) == 0) {
            this.tickAbyssalCommand(level);
        }

        if (this.isOrderedToSit()) {
            return;
        }

        if (this.tryStartEyeOfVoid(level)) {
            return;
        }

        if (this.voidCataclysmTicks <= 0 && this.tryStartVoidCataclysm(level)) {
            return;
        }

        if (this.tryBlink(level)) {
            return;
        }

        this.tryQuickVoidShot(level);
    }

    private void tickCooldowns() {
        if (this.voidCataclysmCooldownTicks > 0) --this.voidCataclysmCooldownTicks;
        if (this.blinkCooldownTicks > 0) --this.blinkCooldownTicks;
        if (this.quickVoidShotCooldownTicks > 0) --this.quickVoidShotCooldownTicks;
        if (this.eyeOfVoidCooldownTicks > 0) --this.eyeOfVoidCooldownTicks;

        Iterator<Map.Entry<UUID, Integer>> iterator = this.recallCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;
            if (next <= 0) iterator.remove();
            else entry.setValue(next);
        }
    }

    private void applyRetaliationMemory() {
        LivingEntity attacker = this.getBrain()
                .getMemory(MemoryModuleType.HURT_BY_ENTITY)
                .orElse(null);

        if (this.isPotentialVoidThreat(attacker)
                && (this.getTarget() == null || !this.getTarget().isAlive())) {
            this.setTarget(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Family / threat helpers
    // ---------------------------------------------------------------------

    public boolean isVoidFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (!this.isTame()) return false;

        LivingEntity owner = this.getOwner();
        if (owner != null && entity == owner) return true;

        return entity instanceof Wolf wolf && wolf.isTame();
    }

    private boolean isTargetingVoidFamily(LivingEntity candidate) {
        if (!(candidate instanceof Mob mob)) return false;
        LivingEntity target = mob.getTarget();
        return target != null && this.isVoidFamilyMember(target);
    }

    private boolean isPotentialVoidThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate instanceof Creeper
                || this.isVoidFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        return candidate instanceof Enemy
                || candidate == this.getTarget()
                || this.isTargetingVoidFamily(candidate);
    }

    private List<LivingEntity> findThreats(ServerLevel level, Vec3 center, double radius) {
        AABB area = new AABB(
                center.x - radius, center.y - 8.0D, center.z - radius,
                center.x + radius, center.y + 8.0D, center.z + radius);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> this.isPotentialVoidThreat(candidate)
                        && candidate.position().distanceToSqr(center) <= radius * radius);
    }

    private List<LivingEntity> findProtectedFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        family.add(this);

        if (!this.isTame()) return family;

        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.level() == level
                && this.distanceToSqr(owner) <= radius * radius) {
            family.add(owner);
        }

        family.addAll(level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(radius),
                wolf -> wolf != this && wolf.isAlive() && wolf.isTame()));

        return family;
    }

    // ---------------------------------------------------------------------
    // Void Awareness + Void Recall
    // ---------------------------------------------------------------------

    /**
     * Recall protection uses horizontal proximity plus a tall vertical column.
     *
     * <p>A player falling straight beneath an End island must remain protected
     * even after the Y distance from Void becomes enormous. Other support
     * abilities continue to use normal 3D range.</p>
     */
    private List<LivingEntity> findRecallProtectedFamily(
            ServerLevel level) {

        List<LivingEntity> family =
                new ArrayList<>();

        family.add(this);

        if (!this.isTame()) {
            return family;
        }

        double radiusSqr =
                VOID_RECALL_MAX_RADIUS
                        * VOID_RECALL_MAX_RADIUS;

        LivingEntity owner =
                this.getOwner();

        if (owner != null
                && owner.isAlive()
                && owner.level() == level) {

            double dx =
                    owner.getX() - this.getX();
            double dz =
                    owner.getZ() - this.getZ();

            if (dx * dx + dz * dz <= radiusSqr) {
                family.add(owner);
            }
        }

        AABB recallColumn =
                this.getBoundingBox()
                        .inflate(
                                VOID_RECALL_MAX_RADIUS,
                                VOID_RECALL_VERTICAL_SCAN,
                                VOID_RECALL_MAX_RADIUS);

        family.addAll(
                level.getEntitiesOfClass(
                        Wolf.class,
                        recallColumn,
                        wolf -> {
                            if (wolf == this
                                    || !wolf.isAlive()
                                    || !wolf.isTame()) {
                                return false;
                            }

                            double dx =
                                    wolf.getX() - this.getX();
                            double dz =
                                    wolf.getZ() - this.getZ();

                            return dx * dx + dz * dz
                                    <= radiusSqr;
                        }));

        return family;
    }

    private void tickVoidAwarenessAndRecall(ServerLevel level) {
        List<LivingEntity> family =
                this.findRecallProtectedFamily(level);

        if (Math.floorMod(this.tickCount + this.getId(), SAFE_POSITION_SAMPLE_INTERVAL) == 0) {
            for (LivingEntity member : family) {
                this.recordSafePositionIfValid(level, member);
            }
        }

        /*
         * Owner rescue is checked by DIRECT bonded reference first.
         * It does not depend on the generic family/entity scan.
         */
        LivingEntity owner =
                this.isTame()
                        ? this.getOwner()
                        : null;

        if (owner != null
                && owner.isAlive()
                && owner.level() == level
                && this.isInVoidRecallDanger(level, owner)) {
            this.tryVoidRecall(level, owner);
        }

        for (LivingEntity member : family) {
            if (member == owner) {
                continue;
            }

            if (this.isInVoidRecallDanger(level, member)) {
                this.tryVoidRecall(level, member);
            }
        }

        if (this.tickCount % 20 == 0) {
            for (LivingEntity member : family) {
                if (member != this && this.isNearDangerousDrop(level, member, 4)) {
                    level.sendParticles(
                            ParticleTypes.REVERSE_PORTAL,
                            member.getX(),
                            member.getY() + member.getBbHeight() * 0.55D,
                            member.getZ(),
                            4,
                            0.24D, 0.28D, 0.24D,
                            0.0D);
                }
            }
        }
    }

    private void recordSafePositionIfValid(ServerLevel level, LivingEntity member) {
        BlockPos feet = member.blockPosition();

        if (!member.onGround()
                || member.isPassenger()
                || !level.getFluidState(feet).isEmpty()) {
            return;
        }

        if (!this.isStrictSafePosition(level, member, feet)) return;

        SafeSpot safeSpot =
                new SafeSpot(
                        level.dimension(),
                        Vec3.atBottomCenterOf(feet));

        this.lastSafePositions.put(
                member.getUUID(),
                safeSpot);

        if (this.isTame()
                && member == this.getOwner()) {
            this.ownerLastSafePosition =
                    safeSpot;
        }
    }

    private boolean isInVoidRecallDanger(ServerLevel level, LivingEntity member) {
        if (!member.isAlive()
                || member.isPassenger()
                || member.onGround()
                || member.getDeltaMovement().y > 0.0D) {
            return false;
        }

        /*
         * Low Y by itself is NOT proof of Void danger.
         *
         * Superflat worlds can legitimately place their grass surface only a
         * handful of blocks above minY. While walking/jumping, onGround can be
         * false for a tick and the old rule would repeatedly "rescue" a player
         * who was standing on perfectly safe terrain.
         *
         * True Void Recall therefore requires BOTH:
         * 1) dangerous world height, and
         * 2) no meaningful solid ground beneath the falling entity.
         */
        double triggerY =
                Level.END.equals(level.dimension())
                        ? Math.max(
                        VOID_RECALL_END_TRIGGER_Y,
                        level.getMinY()
                                + VOID_RECALL_GENERIC_TRIGGER_ABOVE_MIN_Y)
                        : level.getMinY()
                        + VOID_RECALL_GENERIC_TRIGGER_ABOVE_MIN_Y;

        if (member.getY() > triggerY) {
            return false;
        }

        return !this.hasRecallGroundBelow(
                level,
                member,
                VOID_RECALL_GROUND_PROBE_DEPTH);
    }

    /**
     * Returns true when the entity still has legitimate terrain directly below
     * it within the near-fall zone.
     *
     * <p>This is intentionally a narrow vertical probe under the entity rather
     * than a broad cliff scan. Void Recall should ignore normal ground,
     * stairs/ledges and superflat terrain, while still recognizing the open
     * abyss beneath an End island.</p>
     */
    private boolean hasRecallGroundBelow(
            ServerLevel level,
            LivingEntity member,
            int depth) {

        BlockPos start =
                BlockPos.containing(
                        member.getX(),
                        member.getY(),
                        member.getZ());

        for (int down = 1;
             down <= depth;
             ++down) {

            BlockPos check =
                    start.below(down);

            if (check.getY() < level.getMinY()) {
                break;
            }

            if (!level.getBlockState(check)
                    .getCollisionShape(level, check)
                    .isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean tryVoidRecall(ServerLevel level, LivingEntity member) {
        if (this.recallCooldowns.containsKey(member.getUUID())) return false;

        SafeSpot safeSpot;

        if (this.isTame()
                && member == this.getOwner()
                && this.ownerLastSafePosition != null) {
            safeSpot =
                    this.ownerLastSafePosition;
        } else {
            safeSpot =
                    this.lastSafePositions.get(
                            member.getUUID());
        }

        if (safeSpot == null
                || !safeSpot.dimension.equals(level.dimension())) {
            return false;
        }

        Vec3 destination = this.resolveRecordedSafeSpot(level, member, safeSpot.position);
        if (destination == null) return false;

        Vec3 origin = member.position();

        if (member instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        if (member instanceof ServerPlayer player) {
            player.teleportTo(destination.x, destination.y, destination.z);
        } else {
            member.setPos(destination.x, destination.y, destination.z);
        }

        member.setDeltaMovement(Vec3.ZERO);
        member.fallDistance = 0.0F;
        member.hurtMarked = true;

        this.recallCooldowns.put(member.getUUID(), VOID_RECALL_PER_MEMBER_COOLDOWN);

        this.sendVoidRecallLine(level, origin, destination);
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                destination.x,
                destination.y + member.getBbHeight() * 0.45D,
                destination.z,
                42,
                0.55D, 0.65D, 0.55D,
                0.065D);

        member.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.85F, 0.72F);
        return true;
    }

    private void sendVoidRecallLine(ServerLevel level, Vec3 from, Vec3 to) {
        int points = 18;
        for (int i = 0; i < points; ++i) {
            double t = i / (double) (points - 1);
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    from.x + (to.x - from.x) * t,
                    from.y + 0.6D + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    1,
                    0.015D, 0.015D, 0.015D,
                    0.0D);
        }
    }

    private Vec3 resolveRecordedSafeSpot(ServerLevel level, LivingEntity member, Vec3 recorded) {
        BlockPos base = BlockPos.containing(recorded);

        if (this.isStrictSafePosition(level, member, base)) {
            return Vec3.atBottomCenterOf(base);
        }

        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
        };

        for (int[] offset : offsets) {
            for (int dy = 1; dy >= -2; --dy) {
                BlockPos candidate = base.offset(offset[0], dy, offset[1]);
                if (this.isStrictSafePosition(level, member, candidate)) {
                    return Vec3.atBottomCenterOf(candidate);
                }
            }
        }

        return null;
    }

    private boolean isStrictSafePosition(ServerLevel level, LivingEntity member, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos floor = feet.below();

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()
                || !level.getFluidState(floor).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            return false;
        }

        if (this.hasNearbyVoidHazard(level, feet, SAFE_HAZARD_RADIUS)) return false;

        int supported = 0;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                BlockPos check = floor.offset(dx, 0, dz);
                if (level.getFluidState(check).isEmpty()
                        && !level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                    ++supported;
                }
            }
        }

        if (supported < SAFE_SUPPORT_REQUIRED) return false;

        double dx = feet.getX() + 0.5D - member.getX();
        double dy = feet.getY() - member.getY();
        double dz = feet.getZ() + 0.5D - member.getZ();

        AABB moved =
                member.getBoundingBox()
                        .move(dx, dy, dz);

        /*
         * Do not symmetrically inflate downward into the floor.
         * That can reject a valid player standing position and also make every
         * Blink destination look obstructed.
         */
        AABB clearance =
                new AABB(
                        moved.minX - 0.08D,
                        moved.minY + 0.01D,
                        moved.minZ - 0.08D,
                        moved.maxX + 0.08D,
                        moved.maxY + 0.05D,
                        moved.maxZ + 0.08D);

        return level.noCollision(
                member,
                clearance);
    }

    private boolean hasNearbyVoidHazard(ServerLevel level, BlockPos feet, int radius) {
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    BlockPos check = feet.offset(dx, dy, dz);
                    var state = level.getBlockState(check);

                    if (!level.getFluidState(check).isEmpty()
                            || state.is(Blocks.FIRE)
                            || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.CACTUS)
                            || state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.POWDER_SNOW)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Void Cataclysm
    // ---------------------------------------------------------------------

    public boolean isVoidCataclysmActive() {
        return this.voidCataclysmTicks > 0;
    }

    private boolean tryStartVoidCataclysm(ServerLevel level) {
        if (!this.isTame()
                || this.voidCataclysmCooldownTicks > 0
                || this.eyeOfVoidTicks > 0) {
            return false;
        }

        List<LivingEntity> threats = this.findThreats(level, this.position(), VOID_CATACLYSM_RADIUS);
        boolean familyEmergency = this.hasCombatFamilyEmergency(level, VOID_CATACLYSM_RADIUS);

        if (threats.size() < 3 && !familyEmergency) return false;

        this.voidCataclysmTicks = VOID_CATACLYSM_DURATION_TICKS;
        this.cataclysmDeflectedProjectiles.clear();

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY() + 0.65D, this.getZ(),
                58,
                1.55D, 0.70D, 1.55D,
                0.055D);

        this.playSound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.80F, 0.72F);
        return true;
    }

    private void tickVoidCataclysm(ServerLevel level) {
        if (this.voidCataclysmTicks <= 0) return;

        --this.voidCataclysmTicks;

        if (this.tickCount % 5 == 0) this.sendVoidCataclysmRing(level);
        if (this.tickCount % 20 == 0) this.refreshCataclysmFamilyEffects(level);

        this.deflectCataclysmProjectiles(level);

        if (this.voidCataclysmTicks == 0) {
            this.voidCataclysmCooldownTicks = VOID_CATACLYSM_COOLDOWN_TICKS;
            this.cataclysmDeflectedProjectiles.clear();
        }
    }

    private void refreshCataclysmFamilyEffects(ServerLevel level) {
        for (LivingEntity family : this.findProtectedFamily(level, VOID_CATACLYSM_RADIUS)) {
            family.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 45, 0, true, true), this);
            family.addEffect(new MobEffectInstance(MobEffects.SPEED, 45, 0, true, true), this);
            family.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 12, 0, true, false), this);
            family.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 45, 1, true, true), this);
        }
    }

    private void deflectCataclysmProjectiles(ServerLevel level) {
        AABB field = this.getBoundingBox().inflate(VOID_CATACLYSM_RADIUS);
        List<LivingEntity> family = this.findProtectedFamily(level, VOID_CATACLYSM_RADIUS);

        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class,
                field,
                Projectile::isAlive)) {

            if (this.cataclysmDeflectedProjectiles.contains(projectile.getId())) continue;

            Entity owner = projectile.getOwner();
            if (owner instanceof LivingEntity livingOwner && this.isVoidFamilyMember(livingOwner)) continue;

            boolean threatensFamily = false;
            for (LivingEntity protectedMember : family) {
                if (projectile.distanceToSqr(protectedMember) <= 4.5D * 4.5D) {
                    threatensFamily = true;
                    break;
                }
            }

            if (!threatensFamily) continue;

            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() <= 1.0E-6D) continue;

            projectile.setDeltaMovement(velocity.scale(-0.72D).add(0.0D, 0.08D, 0.0D));
            this.cataclysmDeflectedProjectiles.add(projectile.getId());

            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    10,
                    0.16D, 0.16D, 0.16D,
                    0.025D);
        }
    }

    private void sendVoidCataclysmRing(ServerLevel level) {
        int points = 32;
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    this.getX() + Math.cos(angle) * VOID_CATACLYSM_RADIUS,
                    this.getY() + 0.12D,
                    this.getZ() + Math.sin(angle) * VOID_CATACLYSM_RADIUS,
                    1,
                    0.02D, 0.02D, 0.02D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Blink
    // ---------------------------------------------------------------------

    private boolean tryBlink(ServerLevel level) {
        if (this.blinkCooldownTicks > 0
                || this.eyeOfVoidTicks > 0
                || this.isOrderedToSit()) {
            return false;
        }

        LivingEntity target =
                this.getTarget();

        if (!this.isPotentialVoidThreat(target)) {
            target =
                    this.findPriorityThreat(
                            level,
                            BLINK_MAX_RANGE);
        }

        if (this.isPotentialVoidThreat(target)) {
            double distanceSqr =
                    this.distanceToSqr(target);

            if (distanceSqr >= BLINK_MIN_RANGE * BLINK_MIN_RANGE
                    && distanceSqr <= BLINK_MAX_RANGE * BLINK_MAX_RANGE) {

                Vec3 safe =
                        this.findBlinkCombatPosition(
                                level,
                                target);

                if (safe == null) {
                    double towardTarget =
                            Math.atan2(
                                    target.getZ() - this.getZ(),
                                    target.getX() - this.getX());

                    safe =
                            this.findSafePositionAround(
                                    level,
                                    this,
                                    this,
                                    7.0D,
                                    towardTarget);
                }

                if (safe != null
                        && safe.distanceToSqr(this.position())
                        >= 3.5D * 3.5D) {
                    this.performBlink(
                            level,
                            safe,
                            target);
                    return true;
                }
            }
        }

        if (this.isTame() && Level.END.equals(level.dimension())) {
            LivingEntity owner = this.getOwner();

            if (owner != null && owner.isAlive() && owner.level() == level) {
                double distanceSqr = this.distanceToSqr(owner);

                if (distanceSqr >= 18.0D * 18.0D
                        && distanceSqr <= BLINK_MAX_RANGE * BLINK_MAX_RANGE
                        && !this.getSensing().hasLineOfSight(owner)) {

                    Vec3 safe = this.findSafePositionAround(
                            level,
                            owner,
                            this,
                            3.0D,
                            Math.atan2(this.getZ() - owner.getZ(), this.getX() - owner.getX()));

                    if (safe != null) {
                        this.performBlink(level, safe, owner);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Vec3 findBlinkCombatPosition(ServerLevel level, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);

        double angle = flat.lengthSqr() > 1.0E-5D
                ? Math.atan2(-flat.z, -flat.x)
                : Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX());

        return this.findSafePositionAround(level, target, this, 4.5D, angle);
    }

    private void performBlink(ServerLevel level, Vec3 destination, LivingEntity lookAt) {
        Vec3 origin = this.position();
        this.getNavigation().stop();

        this.setPos(destination.x, destination.y, destination.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.hurtMarked = true;
        this.getLookControl().setLookAt(lookAt, 90.0F, 90.0F);
        this.blinkCooldownTicks = BLINK_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                origin.x, origin.y + 0.55D, origin.z,
                24,
                0.35D, 0.40D, 0.35D,
                0.05D);

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                destination.x, destination.y + 0.55D, destination.z,
                30,
                0.42D, 0.48D, 0.42D,
                0.055D);

        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.78F, 0.62F);
    }

    // ---------------------------------------------------------------------
    // Quick Void Shot — regular ranged pressure
    // ---------------------------------------------------------------------

    private boolean tryQuickVoidShot(
            ServerLevel level) {

        if (this.quickVoidShotCooldownTicks > 0
                || this.quickVoidShot != null
                || this.eyeOfVoidTicks > 0
                || this.isOrderedToSit()) {
            return false;
        }

        LivingEntity target =
                this.findPriorityThreat(
                        level,
                        20.0D);

        if (target == null
                || !target.isAlive()
                || !this.isPotentialVoidThreat(target)
                || !this.getSensing()
                .hasLineOfSight(target)) {
            return false;
        }

        double distanceSqr =
                this.distanceToSqr(target);

        if (distanceSqr < QUICK_VOID_SHOT_MIN_RANGE_SQR
                || distanceSqr > QUICK_VOID_SHOT_MAX_RANGE_SQR) {
            return false;
        }

        Vec3 start =
                this.getEyePosition()
                        .add(
                                0.0D,
                                0.08D,
                                0.0D);

        this.quickVoidShot =
                new QuickVoidShot(
                        start,
                        target.getId());

        this.quickVoidShotCooldownTicks =
                QUICK_VOID_SHOT_COOLDOWN_TICKS;

        level.sendParticles(
                ParticleTypes.PORTAL,
                start.x,
                start.y,
                start.z,
                10,
                0.14D,
                0.14D,
                0.14D,
                0.025D);

        return true;
    }

    private void tickQuickVoidShot(
            ServerLevel level) {

        if (this.quickVoidShot == null) {
            return;
        }

        Entity entity =
                level.getEntity(
                        this.quickVoidShot.targetId);

        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || !this.isPotentialVoidThreat(target)) {

            this.quickVoidShot = null;
            return;
        }

        Vec3 delta =
                target.getEyePosition()
                        .subtract(
                                this.quickVoidShot.position);

        if (delta.lengthSqr()
                <= QUICK_VOID_SHOT_HIT_DISTANCE_SQR) {

            DamageSource source =
                    this.damageSources().source(
                            DamageTypes.MOB_PROJECTILE,
                            this);

            target.hurtServer(
                    level,
                    source,
                    QUICK_VOID_SHOT_DAMAGE);

            Vec3 knock =
                    target.position()
                            .subtract(this.position())
                            .multiply(
                                    1.0D,
                                    0.0D,
                                    1.0D);

            if (knock.lengthSqr() > 1.0E-5D) {
                knock = knock.normalize();

                target.push(
                        knock.x * 0.18D,
                        0.04D,
                        knock.z * 0.18D);
            }

            level.sendParticles(
                    ParticleTypes.PORTAL,
                    target.getX(),
                    target.getY()
                            + target.getBbHeight() * 0.55D,
                    target.getZ(),
                    14,
                    0.22D,
                    0.24D,
                    0.22D,
                    0.035D);

            this.quickVoidShot = null;
            return;
        }

        if (delta.lengthSqr() <= 1.0E-6D) {
            this.quickVoidShot = null;
            return;
        }

        Vec3 movement =
                delta.normalize()
                        .scale(
                                QUICK_VOID_SHOT_SPEED);

        this.quickVoidShot.position =
                this.quickVoidShot.position
                        .add(movement);

        ++this.quickVoidShot.ticks;

        level.sendParticles(
                ParticleTypes.PORTAL,
                this.quickVoidShot.position.x,
                this.quickVoidShot.position.y,
                this.quickVoidShot.position.z,
                2,
                0.04D,
                0.04D,
                0.04D,
                0.0D);

        if (this.quickVoidShot.ticks % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.quickVoidShot.position.x,
                    this.quickVoidShot.position.y,
                    this.quickVoidShot.position.z,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D);
        }

        if (this.quickVoidShot.ticks
                >= QUICK_VOID_SHOT_MAX_TICKS) {
            this.quickVoidShot = null;
        }
    }

    // ---------------------------------------------------------------------
    // Abyssal Command
    // ---------------------------------------------------------------------

    private void tickAbyssalCommand(ServerLevel level) {
        if (!this.isTame() || !Level.END.equals(level.dimension())) return;

        LivingEntity sharedThreat = this.findPriorityThreat(level, ABYSSAL_COMMAND_RADIUS);

        for (LivingEntity family : this.findProtectedFamily(level, ABYSSAL_COMMAND_RADIUS)) {
            if (family == this) continue;

            boolean ledgeDanger =
                    this.isNearDangerousDrop(
                            level,
                            family,
                            4);

            if (ledgeDanger
                    && this.tickCount % 12 == 0) {
                /*
                 * Awareness only. Never Slow-Fall somebody toward their death;
                 * actual abyss failure is handled by Void Recall.
                 */
                level.sendParticles(
                        ParticleTypes.REVERSE_PORTAL,
                        family.getX(),
                        family.getY()
                                + family.getBbHeight() * 0.45D,
                        family.getZ(),
                        3,
                        0.18D,
                        0.18D,
                        0.18D,
                        0.0D);
            }

            if (family instanceof Mob mob) {
                LivingEntity target = mob.getTarget();

                if (target != null
                        && this.isUnsafePursuit(
                        level,
                        mob,
                        target)) {

                    /*
                     * Stop the path AND clear the suicidal target. Merely
                     * stopping navigation can be undone immediately by vanilla
                     * wolf combat AI.
                     */
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                    continue;
                }

                if (sharedThreat != null
                        && target == null
                        && family instanceof Wolf wolf
                        && !wolf.isBaby()
                        && !wolf.isOrderedToSit()
                        && !this.isUnsafePursuit(level, mob, sharedThreat)) {
                    mob.setTarget(sharedThreat);
                }
            }
        }
    }

    private boolean isUnsafePursuit(ServerLevel level, Mob mob, LivingEntity target) {
        Vec3 toward = target.position()
                .subtract(mob.position())
                .multiply(1.0D, 0.0D, 1.0D);

        if (toward.lengthSqr() <= 1.0E-5D) return false;
        toward = toward.normalize();

        double[] samples = {1.4D, 2.4D, 3.4D};
        for (double distance : samples) {
            BlockPos sample = BlockPos.containing(
                    mob.getX() + toward.x * distance,
                    mob.getY(),
                    mob.getZ() + toward.z * distance);

            if (!this.hasGroundWithin(level, sample, 4)) return true;
        }

        return false;
    }

    private boolean isNearDangerousDrop(ServerLevel level, LivingEntity entity, int depth) {
        BlockPos feet = entity.blockPosition();
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        int unsafe = 0;
        for (int[] offset : offsets) {
            BlockPos edge = feet.offset(offset[0], 0, offset[1]);
            if (!this.hasGroundWithin(level, edge, depth)) ++unsafe;
        }

        return unsafe >= 2;
    }

    private boolean hasGroundWithin(ServerLevel level, BlockPos feet, int depth) {
        for (int down = 1; down <= depth; ++down) {
            BlockPos check = feet.below(down);

            if (!level.getFluidState(check).isEmpty()) return false;
            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Eye of Void
    // ---------------------------------------------------------------------

    public boolean isEyeOfVoidActive() {
        return this.eyeOfVoidTicks > 0;
    }

    /**
     * Eye of Void has a deliberately broader target contract than Void's normal
     * ranged/melee routines.
     *
     * <p>Endermen and the Ender Dragon are explicit ultimate-valid targets.
     * Family/allies/Creepers remain protected exactly as before.</p>
     */
    private boolean isEyeVoidTarget(
            LivingEntity candidate) {

        if (candidate == null
                || !candidate.isAlive()
                || candidate instanceof Creeper
                || this.isVoidFamilyMember(candidate)
                || this.isAlliedTo(candidate)) {
            return false;
        }

        if (candidate.getType() == EntityType.ENDERMAN
                || candidate.getType() == EntityType.ENDER_DRAGON) {
            return true;
        }

        return this.isPotentialVoidThreat(candidate);
    }

    private List<LivingEntity> findEyeThreats(
            ServerLevel level) {

        AABB eyeArea =
                this.getBoundingBox()
                        .inflate(
                                EYE_OF_VOID_RANGE,
                                EYE_OF_VOID_VERTICAL_RANGE,
                                EYE_OF_VOID_RANGE);

        return level.getEntitiesOfClass(
                LivingEntity.class,
                eyeArea,
                candidate -> {
                    if (!this.isEyeVoidTarget(candidate)) {
                        return false;
                    }

                    double dx =
                            candidate.getX() - this.getX();
                    double dz =
                            candidate.getZ() - this.getZ();

                    /*
                     * Horizontal operating radius stays bounded while the tall
                     * vertical scan allows the Dragon to be attacked overhead.
                     */
                    return dx * dx + dz * dz
                            <= EYE_OF_VOID_RANGE
                            * EYE_OF_VOID_RANGE;
                });
    }

    private LivingEntity findEyePriorityThreat(
            ServerLevel level) {

        return this.findEyeThreats(level)
                .stream()
                .max(
                        Comparator.comparingDouble(
                                this::eyeVoidThreatScore))
                .orElse(null);
    }

    private double eyeVoidThreatScore(
            LivingEntity threat) {

        double score =
                this.voidThreatScore(threat);

        if (threat.getType()
                == EntityType.ENDER_DRAGON) {
            score += 80.0D;
        } else if (threat.getType()
                == EntityType.ENDERMAN) {
            score += 12.0D;
        }

        return score;
    }

    /**
     * Eye of Void is a bonded/controlled ultimate.
     *
     * <p>If an untamed Void Wolf is nearby, the ultimate is suppressed. This
     * prevents a natural Void population around the central End island from
     * turning boss fights into instant Void artillery spam.</p>
     */
    private boolean hasNearbyWildVoidWolf(
            ServerLevel level) {

        return !level.getEntitiesOfClass(
                        VoidWolf.class,
                        this.getBoundingBox()
                                .inflate(
                                        EYE_WILD_VOID_SUPPRESSION_RADIUS,
                                        24.0D,
                                        EYE_WILD_VOID_SUPPRESSION_RADIUS),
                        wolf -> wolf != this
                                && wolf.isAlive()
                                && !wolf.isTame())
                .isEmpty();
    }

    private boolean tryStartEyeOfVoid(ServerLevel level) {
        if (!this.isTame()
                || this.eyeOfVoidCooldownTicks > 0
                || this.voidCataclysmTicks > 0
                || this.isOrderedToSit()
                || this.hasNearbyWildVoidWolf(level)) {
            return false;
        }

        List<LivingEntity> threats = this.findEyeThreats(level);
        boolean heavyweight = threats.stream().anyMatch(threat -> threat.getMaxHealth() >= 60.0F);
        boolean familyEmergency = this.hasCombatFamilyEmergency(level, 18.0D);

        if (threats.size() < 4 && !heavyweight && !familyEmergency) return false;

        this.eyeOfVoidTicks = EYE_OF_VOID_DURATION_TICKS;
        this.eyeOrbFireTicks = 10;
        this.setNoGravity(true);
        this.setDeltaMovement(
                this.getDeltaMovement().multiply(0.40D, 0.0D, 0.40D).add(0.0D, 0.32D, 0.0D));

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY() + 0.75D, this.getZ(),
                72,
                1.05D, 0.95D, 1.05D,
                0.075D);

        level.sendParticles(
                ParticleTypes.END_ROD,
                this.getX(), this.getY() + 0.90D, this.getZ(),
                24,
                0.45D, 0.55D, 0.45D,
                0.035D);

        this.playSound(SoundEvents.END_PORTAL_SPAWN, 0.72F, 0.60F);
        return true;
    }

    private void tickEyeOfVoid(ServerLevel level) {
        if (this.eyeOfVoidTicks <= 0) return;

        --this.eyeOfVoidTicks;
        this.setNoGravity(true);

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x * 0.72D, 0.0D, motion.z * 0.72D);
        this.fallDistance = 0.0F;

        this.tickEyeOrbitVisuals(level);

        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 1, true, true));
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, true, true));
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, true, true));

        if (this.tickCount % 10 == 0) {
            this.shareEyeThreat(level);
            if (Level.END.equals(level.dimension())) this.tickAbyssalCommand(level);
            this.refreshEyeFamilyProtection(level);
        }

        if (--this.eyeOrbFireTicks <= 0) {
            this.eyeOrbFireTicks = EYE_ORB_FIRE_INTERVAL;
            this.tryLaunchVoidShot(level);
        }

        if (this.eyeOfVoidTicks <= 0) this.finishEyeOfVoid();
    }

    private void tickEyeOrbitVisuals(ServerLevel level) {
        double time = this.tickCount * 0.16D;

        for (int i = 0; i < 4; ++i) {
            double angle = time + i * (Math.PI * 2.0D / 4.0D);
            double x = this.getX() + Math.cos(angle) * 1.45D;
            double z = this.getZ() + Math.sin(angle) * 1.45D;
            double y = this.getY() + 0.85D + Math.sin(time * 0.65D + i) * 0.22D;

            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    x, y, z,
                    3,
                    0.08D, 0.08D, 0.08D,
                    0.0D);

            level.sendParticles(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    1,
                    0.01D, 0.01D, 0.01D,
                    0.0D);
        }
    }

    private void refreshEyeFamilyProtection(ServerLevel level) {
        for (LivingEntity family : this.findProtectedFamily(level, 18.0D)) {
            family.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 35, 0, true, true), this);
            family.addEffect(new MobEffectInstance(MobEffects.SPEED, 35, 0, true, true), this);
        }
    }

    private void shareEyeThreat(ServerLevel level) {
        LivingEntity threat = this.findPriorityThreat(level, 24.0D);
        if (threat == null) return;

        for (LivingEntity family : this.findProtectedFamily(level, 24.0D)) {
            if (!(family instanceof Wolf wolf)
                    || wolf == this
                    || wolf.isBaby()
                    || wolf.isOrderedToSit()
                    || wolf.getTarget() != null) {
                continue;
            }

            wolf.setTarget(threat);
        }
    }

    private void tryLaunchVoidShot(ServerLevel level) {
        if (this.voidShots.size() >= EYE_MAX_CONCURRENT_SHOTS) return;

        LivingEntity target =
                this.findEyePriorityThreat(level);

        if (target == null) {
            return;
        }

        /*
         * Dragon is enormous, airborne and multipart; don't let ordinary wolf
         * sensing/LOS quirks prevent the ultimate from engaging it.
         */
        if (target.getType() != EntityType.ENDER_DRAGON
                && !this.getSensing().hasLineOfSight(target)) {
            return;
        }

        Vec3 start = this.getEyePosition().add(0.0D, 0.25D, 0.0D);
        this.voidShots.add(new VoidShot(start, target.getId()));

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                start.x, start.y, start.z,
                18,
                0.20D, 0.20D, 0.20D,
                0.035D);

        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.55F, 0.42F);
    }

    /**
     * Heavy, visibly moving orb simulation — not hitscan.
     * Each shot owns a world position and velocity and advances every server tick.
     */
    private void tickVoidShots(ServerLevel level) {
        Iterator<VoidShot> iterator = this.voidShots.iterator();

        while (iterator.hasNext()) {
            VoidShot shot = iterator.next();
            Entity entity = level.getEntity(shot.targetId);

            if (!(entity instanceof LivingEntity target)
                    || !this.isEyeVoidTarget(target)) {
                iterator.remove();
                continue;
            }

            Vec3 targetPos;

            if (target instanceof EnderDragon dragon) {
                /*
                 * Aim at the public Dragon head part rather than the huge parent
                 * entity center. The shot therefore looks like it is actually
                 * tracking the boss rather than chasing an invisible pivot.
                 */
                targetPos =
                        dragon.head.position()
                                .add(
                                        0.0D,
                                        dragon.head.getBbHeight() * 0.45D,
                                        0.0D);
            } else {
                targetPos =
                        target.getEyePosition();
            }

            Vec3 delta =
                    targetPos.subtract(
                            shot.position);

            double hitDistanceSqr =
                    target.getType() == EntityType.ENDER_DRAGON
                            ? 3.50D * 3.50D
                            : VOID_SHOT_HIT_DISTANCE_SQR;

            if (delta.lengthSqr() <= hitDistanceSqr) {
                /*
                 * End-special Eye hits must use a NON-projectile source.
                 *
                 * Endermen explicitly dodge projectile-tag damage.
                 * The Ender Dragon's boss damage path is also much more reliable
                 * when the causing entity is an actual player.
                 *
                 * Because Eye of Void is a tamed-companion ultimate, attribute
                 * these special End hits to Void's bonded ServerPlayer owner.
                 * The orb still physically travels from Void; only the DAMAGE
                 * SOURCE used at the instant of impact is owner-attributed.
                 */
                boolean endSpecial =
                        target.getType() == EntityType.ENDERMAN
                                || target.getType() == EntityType.ENDER_DRAGON;

                DamageSource source;

                LivingEntity bondedOwner =
                        this.isTame()
                                ? this.getOwner()
                                : null;

                if (endSpecial
                        && bondedOwner instanceof ServerPlayer playerOwner) {

                    source =
                            playerOwner.damageSources()
                                    .playerAttack(playerOwner);
                } else if (endSpecial) {
                    /*
                     * Defensive fallback for unusual ownership states.
                     * Still non-projectile, so an Enderman cannot invoke its
                     * projectile-dodge branch.
                     */
                    source =
                            this.damageSources()
                                    .mobAttack(this);
                } else {
                    source =
                            this.damageSources().source(
                                    DamageTypes.MOB_PROJECTILE,
                                    this);
                }

                boolean damaged;

                if (target instanceof EnderDragon dragon) {
                    /*
                     * Ender Dragon is multipart. Route the player-attributed
                     * impact through the head part so the boss health is
                     * actually modified.
                     */
                    damaged =
                            dragon.hurt(
                                    level,
                                    dragon.head,
                                    source,
                                    VOID_SHOT_DAMAGE);
                } else {
                    damaged =
                            target.hurtServer(
                                    level,
                                    source,
                                    VOID_SHOT_DAMAGE);
                }

                /*
                 * Don't fake a successful hit. If some external mod/event
                 * cancels damage, show a smaller fizzle and end the orb cleanly.
                 */
                if (!damaged) {
                    level.sendParticles(
                            ParticleTypes.PORTAL,
                            targetPos.x,
                            targetPos.y,
                            targetPos.z,
                            6,
                            0.16D,
                            0.16D,
                            0.16D,
                            0.01D);

                    iterator.remove();
                    continue;
                }

                Vec3 knock = target.position()
                        .subtract(this.position())
                        .multiply(1.0D, 0.0D, 1.0D);

                if (knock.lengthSqr() > 1.0E-5D) {
                    knock = knock.normalize();
                    target.push(knock.x * 0.42D, 0.12D, knock.z * 0.42D);
                }

                level.sendParticles(
                        ParticleTypes.REVERSE_PORTAL,
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.55D,
                        target.getZ(),
                        32,
                        0.45D, 0.50D, 0.45D,
                        0.06D);

                level.sendParticles(
                        ParticleTypes.END_ROD,
                        targetPos.x,
                        targetPos.y,
                        targetPos.z,
                        endSpecial ? 16 : 10,
                        endSpecial ? 0.36D : 0.22D,
                        endSpecial ? 0.36D : 0.26D,
                        endSpecial ? 0.36D : 0.22D,
                        0.025D);

                iterator.remove();
                continue;
            }

            if (delta.lengthSqr() <= 1.0E-6D) {
                iterator.remove();
                continue;
            }

            Vec3 desired = delta.normalize().scale(VOID_SHOT_SPEED);
            shot.velocity = shot.velocity.scale(0.35D).add(desired.scale(0.65D));
            shot.position = shot.position.add(shot.velocity);
            ++shot.ticks;

            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    shot.position.x, shot.position.y, shot.position.z,
                    4,
                    0.10D, 0.10D, 0.10D,
                    0.0D);

            if (shot.ticks % 2 == 0) {
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        shot.position.x, shot.position.y, shot.position.z,
                        1,
                        0.02D, 0.02D, 0.02D,
                        0.0D);
            }

            if (shot.ticks >= VOID_SHOT_MAX_TICKS) iterator.remove();
        }
    }

    private LivingEntity findPriorityThreat(ServerLevel level, double radius) {
        return this.findThreats(level, this.position(), radius)
                .stream()
                .max(Comparator.comparingDouble(this::voidThreatScore))
                .orElse(null);
    }

    private double voidThreatScore(LivingEntity threat) {
        double score = Math.min(60.0D, threat.getMaxHealth()) * 0.20D;
        if (this.isTargetingVoidFamily(threat)) score += 25.0D;
        if (threat == this.getTarget()) score += 8.0D;
        score -= Math.sqrt(this.distanceToSqr(threat)) * 0.20D;
        return score;
    }

    private void finishEyeOfVoid() {
        this.eyeOfVoidTicks = 0;
        this.eyeOfVoidCooldownTicks = EYE_OF_VOID_COOLDOWN_TICKS;
        this.eyeOrbFireTicks = 0;
        this.setNoGravity(false);
    }

    private void endEyeOfVoidIfNeeded() {
        if (this.eyeOfVoidTicks > 0) this.finishEyeOfVoid();
        else this.setNoGravity(false);
    }

    // ---------------------------------------------------------------------
    // Shared emergency / safe teleport helpers
    // ---------------------------------------------------------------------

    /**
     * Combat emergency only.
     * Falling into the abyss belongs to Void Recall, not Cataclysm/Eye.
     */
    private boolean hasCombatFamilyEmergency(ServerLevel level, double radius) {
        for (LivingEntity family : this.findProtectedFamily(level, radius)) {
            if (family.getMaxHealth() > 0.0F
                    && family.getHealth() / family.getMaxHealth() <= 0.40F
                    && this.findNearestThreatTo(level, family, 8.0D) != null) {
                return true;
            }
        }
        return false;
    }

    private LivingEntity findNearestThreatTo(ServerLevel level, LivingEntity family, double radius) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        family.getBoundingBox().inflate(radius),
                        this::isPotentialVoidThreat)
                .stream()
                .min(Comparator.comparingDouble(family::distanceToSqr))
                .orElse(null);
    }

    private Vec3 findSafePositionAround(
            ServerLevel level,
            LivingEntity anchor,
            LivingEntity moving,
            double radius,
            double preferredAngle) {

        double[] angles = {
                0.0D,
                Math.PI / 4.0D,
                -Math.PI / 4.0D,
                Math.PI / 2.0D,
                -Math.PI / 2.0D,
                Math.PI,
                Math.PI * 3.0D / 4.0D,
                -Math.PI * 3.0D / 4.0D
        };

        double[] radii = {radius, radius + 1.5D, radius + 3.0D};

        for (double currentRadius : radii) {
            for (double offset : angles) {
                double angle = preferredAngle + offset;
                int x = (int) Math.floor(anchor.getX() + Math.cos(angle) * currentRadius);
                int z = (int) Math.floor(anchor.getZ() + Math.sin(angle) * currentRadius);
                BlockPos base = BlockPos.containing(x, anchor.getY(), z);

                for (int dy = 3; dy >= -4; --dy) {
                    BlockPos candidate = base.offset(0, dy, 0);
                    if (this.isStrictSafePosition(level, moving, candidate)) {
                        return Vec3.atBottomCenterOf(candidate);
                    }
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------
    // Natural spawning
    // ---------------------------------------------------------------------

    /**
     * Wild Void Wolves are rare solitary OUTER-END encounters.
     *
     * <p>The biome tag controls the habitat: End Highlands + End Midlands.
     * The central {@code minecraft:the_end} Dragon island is deliberately not
     * part of that tag, so natural Void populations do not turn the Dragon fight
     * into uncontrolled Eye-of-Void artillery.</p>
     *
     * <p>MobCategory.AMBIENT is only the spawn-budget category. The entity's
     * actual Wolf AI remains neutral.</p>
     */
    public static boolean checkVoidWolfSpawnRules(
            EntityType<VoidWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        /*
         * Spawn egg, /summon, breeding and other explicit spawns should not be
         * blocked by natural-habitat rules.
         */
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        /*
         * Outer End islands normally live comfortably above this, while the
         * floor also prevents bizarre low-Y placement in custom terrain.
         */
        if (pos.getY() < 40) {
            return false;
        }

        /*
         * Void Wolf belongs on actual End-island terrain.
         * Keep this strict: no chorus plant tops, no obsidian towers, no fluids.
         */
        if (!level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()
                || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos.below()).is(Blocks.END_STONE)) {
            return false;
        }

        /*
         * ★★★★★ solitary encounter.
         *
         * Persistent wolves can accumulate over time, and Eye of Void has a
         * nearby-wild suppression rule. Keeping only one untamed Void Wolf in a
         * wide local area prevents accidental packs and makes each sighting feel
         * important.
         */
        if (level instanceof ServerLevel serverLevel) {
            int nearbyWild =
                    serverLevel.getEntitiesOfClass(
                                    VoidWolf.class,
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
        output.putInt("VoidCataclysmTicks", this.voidCataclysmTicks);
        output.putInt("VoidCataclysmCooldown", this.voidCataclysmCooldownTicks);
        output.putInt("VoidBlinkCooldown", this.blinkCooldownTicks);
        output.putInt("VoidQuickShotCooldown", this.quickVoidShotCooldownTicks);
        output.putInt("EyeOfVoidTicks", this.eyeOfVoidTicks);
        output.putInt("EyeOfVoidCooldown", this.eyeOfVoidCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(VOID_MAX_HEALTH);

        super.readAdditionalSaveData(input);

        if (maxHealth != null) maxHealth.setBaseValue(VOID_MAX_HEALTH);

        this.voidCataclysmTicks = Math.max(0, input.getIntOr("VoidCataclysmTicks", 0));
        this.voidCataclysmCooldownTicks = Math.max(0, input.getIntOr("VoidCataclysmCooldown", 0));
        this.blinkCooldownTicks = Math.max(0, input.getIntOr("VoidBlinkCooldown", 0));
        this.quickVoidShotCooldownTicks = Math.max(0, input.getIntOr("VoidQuickShotCooldown", 0));
        this.eyeOfVoidTicks = Math.max(0, input.getIntOr("EyeOfVoidTicks", 0));
        this.eyeOfVoidCooldownTicks = Math.max(0, input.getIntOr("EyeOfVoidCooldown", 0));
        this.eyeOrbFireTicks = this.eyeOfVoidTicks > 0 ? 10 : 0;

        this.ownerLastSafePosition = null;
        this.quickVoidShot = null;
        this.voidShots.clear();
        this.cataclysmDeflectedProjectiles.clear();

        if (this.isBaby()) {
            this.eyeOfVoidTicks = 0;
            this.voidCataclysmTicks = 0;
            this.setNoGravity(false);
        }
    }

    // ---------------------------------------------------------------------
    // Test accessors
    // ---------------------------------------------------------------------

    public boolean isBlinkReady() {
        return this.blinkCooldownTicks <= 0;
    }

    public boolean isVoidCataclysmReady() {
        return this.voidCataclysmCooldownTicks <= 0;
    }

    public boolean isQuickVoidShotReady() {
        return this.quickVoidShotCooldownTicks <= 0
                && this.quickVoidShot == null;
    }

    public boolean isEyeOfVoidReady() {
        return this.eyeOfVoidCooldownTicks <= 0;
    }

    public int getVoidShotsInFlight() {
        return this.voidShots.size();
    }

    // ---------------------------------------------------------------------
    // Small runtime records
    // ---------------------------------------------------------------------

    private static final class SafeSpot {
        private final net.minecraft.resources.ResourceKey<Level> dimension;
        private final Vec3 position;

        private SafeSpot(net.minecraft.resources.ResourceKey<Level> dimension, Vec3 position) {
            this.dimension = dimension;
            this.position = position;
        }
    }

    private static final class QuickVoidShot {
        private Vec3 position;
        private final int targetId;
        private int ticks;

        private QuickVoidShot(
                Vec3 position,
                int targetId) {

            this.position = position;
            this.targetId = targetId;
        }
    }

    private static final class VoidShot {
        private Vec3 position;
        private Vec3 velocity = Vec3.ZERO;
        private final int targetId;
        private int ticks;

        private VoidShot(Vec3 position, int targetId) {
            this.position = position;
            this.targetId = targetId;
        }
    }
}
