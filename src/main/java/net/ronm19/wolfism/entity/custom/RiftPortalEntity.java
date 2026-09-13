package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.registry.ModEntities;

/**
 * Short-lived spatial tear created by Rift Wolf.
 *
 * <p>The entity is intentionally no-save/no-summon. It is a server-authoritative
 * transport anchor and particle emitter, not a block portal. Paired portals can
 * therefore exist without terrain edits, portal-frame logic, or griefing.</p>
 *
 * <p>Only PASSAGE mode performs walk-through transport. STEP, RESCUE, BANISH and
 * FIELD use the exact same paired portal visual, but the RiftWolf performs their
 * transfer immediately because those abilities cannot wait for voluntary entry.</p>
 */
public final class RiftPortalEntity extends Entity {

    public enum Mode {
        STEP,
        RESCUE,
        BANISH,
        PASSAGE,
        FIELD,
        RECALL
    }

    private RiftWolf ownerWolf;
    private RiftPortalEntity pairedPortal;
    private Mode mode = Mode.STEP;
    private int lifetimeTicks = 20;

    /**
     * Entities currently overlapping this doorway. Passage only fires when an
     * allowed entity ENTERS the trigger, preventing ping-pong between paired
     * portals when somebody stands still on the exit.
     */
    private final Set<UUID> currentOccupants =
            new HashSet<>();

    public RiftPortalEntity(
            EntityType<? extends RiftPortalEntity> type,
            Level level) {
        super(type, level);
    }

    public static RiftPortalEntity[] spawnPair(
            ServerLevel level,
            RiftWolf owner,
            Vec3 entrance,
            Vec3 exit,
            Mode mode,
            int lifetimeTicks) {

        RiftPortalEntity first =
                new RiftPortalEntity(
                        ModEntities.RIFT_PORTAL.get(),
                        level);

        RiftPortalEntity second =
                new RiftPortalEntity(
                        ModEntities.RIFT_PORTAL.get(),
                        level);

        first.setPos(
                entrance.x,
                entrance.y,
                entrance.z);

        second.setPos(
                exit.x,
                exit.y,
                exit.z);

        double yaw = Math.toDegrees(
                Math.atan2(
                        exit.z - entrance.z,
                        exit.x - entrance.x));

        first.setYRot((float) yaw);
        second.setYRot((float) (yaw + 180.0D));

        first.configure(
                owner,
                second,
                mode,
                lifetimeTicks);

        second.configure(
                owner,
                first,
                mode,
                lifetimeTicks);

        level.addFreshEntity(first);
        level.addFreshEntity(second);

        first.emitOpeningBurst(level);
        second.emitOpeningBurst(level);

        return new RiftPortalEntity[] {
                first,
                second
        };
    }

    private void configure(
            RiftWolf owner,
            RiftPortalEntity pair,
            Mode mode,
            int lifetimeTicks) {

        this.ownerWolf = owner;
        this.pairedPortal = pair;
        this.mode = mode == null
                ? Mode.STEP
                : mode;

        this.lifetimeTicks =
                Math.max(2, lifetimeTicks);

        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.ownerWolf == null
                || this.ownerWolf.isRemoved()
                || !this.ownerWolf.isAlive()
                || this.pairedPortal == null
                || this.pairedPortal.isRemoved()) {
            this.discard();
            return;
        }

        --this.lifetimeTicks;

        /*
         * Passage is a gameplay object the player must physically find and walk
         * through, so keep its outline continuously visible. Short cinematic
         * rifts can remain slightly lighter.
         */
        if (this.mode == Mode.PASSAGE
                || this.tickCount % 2 == 0) {
            this.emitRiftOutline(level);
        }

        if (this.mode == Mode.PASSAGE) {
            this.tickPassageTransport(level);
        }

        if (this.lifetimeTicks <= 0) {
            this.emitClosingBurst(level);
            this.discard();
        }
    }

    private void tickPassageTransport(
            ServerLevel level) {

        AABB trigger =
                this.getBoundingBox()
                        .inflate(
                                1.05D,
                                0.40D,
                                1.05D);

        Set<UUID> nowInside =
                new HashSet<>();

        for (LivingEntity entity
                : level.getEntitiesOfClass(
                LivingEntity.class,
                trigger,
                candidate -> candidate.isAlive()
                        && this.ownerWolf
                        .isRiftFamilyMember(
                                candidate))) {

            UUID id = entity.getUUID();
            nowInside.add(id);

            if (this.currentOccupants.contains(id)) {
                continue;
            }

            this.transportThroughPassage(entity);
        }

        this.currentOccupants.retainAll(nowInside);
        this.currentOccupants.addAll(nowInside);
    }

    private void transportThroughPassage(
            LivingEntity entity) {

        if (this.pairedPortal == null
                || this.pairedPortal.isRemoved()) {
            return;
        }

        /*
         * Mark the destination doorway BEFORE movement so its next tick knows
         * this entity arrived from the pair and does not immediately return it.
         * The entity must leave and re-enter before the passage can fire again.
         */
        this.currentOccupants.add(entity.getUUID());
        this.pairedPortal.currentOccupants.add(
                entity.getUUID());

        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
        }

        Vec3 exit =
                this.pairedPortal.position();

        /*
         * Players need ServerPlayer's synchronized teleport path. Using raw
         * setPos here could move the server-side entity momentarily while the
         * client kept its old position / corrected it immediately.
         */
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(
                    exit.x,
                    exit.y,
                    exit.z);
        } else {
            entity.setPos(
                    exit.x,
                    exit.y,
                    exit.z);
        }

        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hurtMarked = true;

        entity.playSound(
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                0.75F,
                1.25F);
    }

    private void emitOpeningBurst(
            ServerLevel level) {

        WolfVfx.sendParticles(level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.90D,
                this.getZ(),
                this.mode == Mode.PASSAGE ? 56 : 32,
                0.45D,
                0.82D,
                0.45D,
                0.055D);

        WolfVfx.sendParticles(level,
                ParticleTypes.PORTAL,
                this.getX(),
                this.getY() + 0.90D,
                this.getZ(),
                this.mode == Mode.PASSAGE ? 18 : 20,
                0.30D,
                0.70D,
                0.30D,
                0.025D);

        if (this.mode == Mode.PASSAGE) {
            /*
             * Passage is the only rift the player is expected to physically
             * enter. Give it a stable bright core rather than a second noisy
             * purple particle cloud.
             */
            WolfVfx.sendParticles(level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.90D,
                    this.getZ(),
                    10,
                    0.12D,
                    0.55D,
                    0.12D,
                    0.012D);
        }
    }

    private void emitClosingBurst(
            ServerLevel level) {

        WolfVfx.sendParticles(level,
                ParticleTypes.REVERSE_PORTAL,
                this.getX(),
                this.getY() + 0.90D,
                this.getZ(),
                18,
                0.30D,
                0.55D,
                0.30D,
                0.035D);
    }

    /**
     * Particle-built upright oval. The entity itself owns the stable trigger;
     * this visual can later be replaced with a textured translucent renderer
     * without touching a single gameplay ability.
     */
    private void emitRiftOutline(
            ServerLevel level) {

        int points =
                this.mode == Mode.PASSAGE
                        ? 28
                        : 14;

        double horizontalRadius =
                this.mode == Mode.PASSAGE
                        ? 0.82D
                        : 0.48D;

        double verticalRadius =
                this.mode == Mode.PASSAGE
                        ? 1.12D
                        : 0.72D;

        double yaw =
                Math.toRadians(this.getYRot());

        double axisX = -Math.sin(yaw);
        double axisZ = Math.cos(yaw);

        for (int i = 0; i < points; ++i) {
            double angle =
                    Math.PI * 2.0D * i / points;

            double horizontal =
                    Math.cos(angle)
                            * horizontalRadius;

            double y =
                    this.getY()
                            + 0.90D
                            + Math.sin(angle)
                            * verticalRadius;

            double x =
                    this.getX()
                            + axisX
                            * horizontal;

            double z =
                    this.getZ()
                            + axisZ
                            * horizontal;

            WolfVfx.sendParticles(level,
                    ParticleTypes.REVERSE_PORTAL,
                    x,
                    y,
                    z,
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);
        }

        if (this.mode == Mode.PASSAGE) {
            /*
             * Stable three-point vertical core: visually communicates "walk
             * through THIS one" and distinguishes Passage from the short
             * cinematic rifts used by Step/Rescue/Banish/Recall.
             */
            WolfVfx.sendParticles(level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.28D,
                    this.getZ(),
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);

            WolfVfx.sendParticles(level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.90D,
                    this.getZ(),
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);

            WolfVfx.sendParticles(level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 1.52D,
                    this.getZ(),
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D);

            if (this.tickCount % 4 == 0) {
                WolfVfx.sendParticles(level,
                        ParticleTypes.PORTAL,
                        this.getX(),
                        this.getY() + 0.90D,
                        this.getZ(),
                        2,
                        0.10D,
                        0.36D,
                        0.10D,
                        0.0D);
            }
        } else if (this.tickCount % 4 == 0) {
            WolfVfx.sendParticles(level,
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + 0.90D,
                    this.getZ(),
                    3,
                    0.18D,
                    0.42D,
                    0.18D,
                    0.01D);
        }
    }

    @Override
    protected void defineSynchedData(
            net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // Runtime-only portal; no custom synchronized fields are required.
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input) {
        // EntityType is noSave. Deliberately empty.
    }

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output) {
        // EntityType is noSave. Deliberately empty.
    }

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource damageSource,
            float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
