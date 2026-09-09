package net.ronm19.wolfism.entity.holiday;

import java.time.LocalDate;
import java.time.ZoneId;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModGameRules;

/**
 * Shared base for Wolfism's seven real-world Holiday Wolves.
 *
 * <p>The date gate is the rarity: each concrete holiday wolf decides its own
 * real-world seasonal window, while this base provides the year-round testing
 * override and the Recovery/Downed system that prevents permanent death.</p>
 */
public abstract class AbstractWolfismHolidayWolf extends AbstractWolfismWolf {
    private static final EntityDataAccessor<Boolean> DATA_HOLIDAY_RECOVERING =
            SynchedEntityData.defineId(
                    AbstractWolfismHolidayWolf.class,
                    EntityDataSerializers.BOOLEAN);

    private static final int DEFAULT_RECOVERY_TICKS = 20 * 7;

    private int holidayRecoveryTicks;
    private boolean restoreOrderedSitAfterRecovery;

    protected AbstractWolfismHolidayWolf(
            EntityType<? extends AbstractWolfismHolidayWolf> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HOLIDAY_RECOVERING, false);
    }

    /** True only when this concrete wolf's real-world holiday window is open. */
    protected abstract boolean isHolidaySeasonActive(LocalDate date);

    /** Species hook for recovery visuals. */
    protected abstract void spawnHolidayRecoveryParticles(
            ServerLevel level,
            boolean finishing);

    /**
     * Shared seasonal test. The gamerule is intentionally a natural-spawn
     * override only; commands and spawn eggs already bypass species spawn rules.
     */
    public final boolean isCurrentHolidaySeason(ServerLevel level) {
        if (level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())) {
            return true;
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return this.isHolidaySeasonActive(today);
    }

    public final boolean isHolidayRecovering() {
        return this.entityData.get(DATA_HOLIDAY_RECOVERING);
    }

    public final int getHolidayRecoveryTicks() {
        return this.holidayRecoveryTicks;
    }

    protected int getHolidayRecoveryDurationTicks() {
        return DEFAULT_RECOVERY_TICKS;
    }

    @Override
    protected boolean canUseActiveWolfismAbility() {
        return !this.isHolidayRecovering()
                && super.canUseActiveWolfismAbility();
    }

    /**
     * Holiday wolves never enter vanilla death. Lethal damage calls this method;
     * we restore one health immediately and transition into a temporary downed
     * state instead of calling super.die(...).
     */
    @Override
    public void die(DamageSource source) {
        if (this.isHolidayRecovering()) {
            return;
        }

        this.setHealth(1.0F);
        this.beginHolidayRecovery();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isHolidayRecovering()) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    protected final void beginHolidayRecovery() {
        this.restoreOrderedSitAfterRecovery = this.isOrderedToSit();
        this.holidayRecoveryTicks = Math.max(20, this.getHolidayRecoveryDurationTicks());
        this.entityData.set(DATA_HOLIDAY_RECOVERING, true);

        this.setHealth(Math.max(1.0F, this.getHealth()));
        this.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);
        this.clearFire();
        this.removeAllEffects();
        this.stopRiding();
        if (this.isTame()) {
            this.setOrderedToSit(true);
        }
        this.setNoAi(true);

        if (this.level() instanceof ServerLevel level) {
            this.spawnHolidayRecoveryParticles(level, false);
            level.sendParticles(
                    ParticleTypes.POOF,
                    this.getX(), this.getY(0.45D), this.getZ(),
                    14, 0.55D, 0.25D, 0.55D, 0.02D);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)
                || !this.isHolidayRecovering()) {
            return;
        }

        this.setTarget(null);
        this.getNavigation().stop();
        this.setSprinting(false);
        this.clearFire();

        if (this.holidayRecoveryTicks > 0) {
            --this.holidayRecoveryTicks;
        }

        if (this.holidayRecoveryTicks % 10 == 0) {
            this.spawnHolidayRecoveryParticles(level, false);
        }

        if (this.holidayRecoveryTicks <= 0) {
            this.finishHolidayRecovery(level);
        }
    }

    private void finishHolidayRecovery(ServerLevel level) {
        this.entityData.set(DATA_HOLIDAY_RECOVERING, false);
        this.holidayRecoveryTicks = 0;
        this.setNoAi(false);
        this.clearFire();
        this.removeAllEffects();
        this.setHealth(Math.max(1.0F, this.getMaxHealth() * 0.55F));

        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner != null
                    && owner.isAlive()
                    && owner.level() == this.level()
                    && this.distanceToSqr(owner) > 14.0D * 14.0D) {
                BlockPos safe = this.findHolidayRecoveryPosition(level, owner.blockPosition());
                if (safe != null) {
                    this.teleportTo(
                            safe.getX() + 0.5D,
                            safe.getY(),
                            safe.getZ() + 0.5D);
                }
            }
        }

        this.setOrderedToSit(this.restoreOrderedSitAfterRecovery);
        this.spawnHolidayRecoveryParticles(level, true);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY(0.55D), this.getZ(),
                12, 0.55D, 0.35D, 0.55D, 0.02D);
    }

    private BlockPos findHolidayRecoveryPosition(ServerLevel level, BlockPos ownerPos) {
        for (int radius = 1; radius <= 5; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -1; dy <= 2; ++dy) {
                        BlockPos feet = ownerPos.offset(dx, dy, dz);
                        if (this.isSafeHolidayRecoveryPosition(level, feet)) {
                            return feet.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeHolidayRecoveryPosition(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos floor = feet.below();

        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }

        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("HolidayRecoveryTicks", this.holidayRecoveryTicks);
        output.putBoolean("HolidayRestoreSit", this.restoreOrderedSitAfterRecovery);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.holidayRecoveryTicks = Math.max(0, input.getIntOr("HolidayRecoveryTicks", 0));
        this.restoreOrderedSitAfterRecovery = input.getBooleanOr("HolidayRestoreSit", false);
        boolean recovering = this.holidayRecoveryTicks > 0;
        this.entityData.set(DATA_HOLIDAY_RECOVERING, recovering);
        if (recovering) {
            this.setHealth(Math.max(1.0F, this.getHealth()));
            this.setNoAi(true);
        }
    }
}
