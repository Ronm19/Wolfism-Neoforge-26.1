package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.CherryWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Cherry Wolf: renewal, mobility and living-area support.
 *
 * <p>Cherry is deliberately not Spirit Wolf 2.0. Spirit is an emergency soul
 * medic; Cherry controls a living battlefield through a sustained healing aura,
 * a speed-control burst, a literal blossom trail, and a fixed sanctuary zone.</p>
 */
public final class CherryWolf extends AbstractWolfismWolf {
    public static final int PETAL_AID_COOLDOWN_TICKS = 20 * 18;
    public static final int CHERRY_BURST_COOLDOWN_TICKS = 20 * 20;
    public static final int BLOOMING_PATH_COOLDOWN_TICKS = 20 * 25;
    public static final int SAKURA_SANCTUARY_COOLDOWN_TICKS = 20 * 90;

    public static final int PETAL_AID_DURATION_TICKS = 20 * 8;
    public static final int BLOOMING_PATH_DURATION_TICKS = 20 * 10;
    public static final int SAKURA_SANCTUARY_DURATION_TICKS = 20 * 12;

    public static final double BLOSSOM_SCENT_RADIUS = 10.0D;
    public static final double PETAL_AID_RADIUS = 10.0D;
    public static final double CHERRY_BURST_RADIUS = 8.0D;
    public static final double SAKURA_SANCTUARY_RADIUS = 12.0D;

    private static final int BLOSSOM_SCENT_INTERVAL = 40;
    private static final int FLORA_SCAN_INTERVAL = 80;
    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int PETAL_AID_PULSE_INTERVAL = 20;
    private static final int PATH_MARK_INTERVAL = 5;
    private static final int PATH_EFFECT_INTERVAL = 10;
    private static final int SANCTUARY_PULSE_INTERVAL = 20;

    private static final EntityDataAccessor<Boolean> DATA_PETAL_AID_ACTIVE =
            SynchedEntityData.defineId(CherryWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BURST_ACTIVE =
            SynchedEntityData.defineId(CherryWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BLOOMING_PATH_ACTIVE =
            SynchedEntityData.defineId(CherryWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SANCTUARY_ACTIVE =
            SynchedEntityData.defineId(CherryWolf.class, EntityDataSerializers.BOOLEAN);

    private int petalAidTicks;
    private int burstVisualTicks;
    private int bloomingPathTicks;
    private int sanctuaryTicks;
    private int abilityLockoutTicks;
    private Vec3 sanctuaryCenter;
    private final List<Vec3> bloomingPathMarkers = new ArrayList<>();

    private static final class BrainHolder {
        private static final Brain.Provider<CherryWolf> PROVIDER = Brain.<CherryWolf>provider(
                ImmutableList.of(ModSensorTypes.CHERRY_PACK.get()),
                wolf -> List.of());
    }

    public CherryWolf(EntityType<? extends CherryWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_PETAL_AID_ACTIVE, false);
        data.define(DATA_BURST_ACTIVE, false);
        data.define(DATA_BLOOMING_PATH_ACTIVE, false);
        data.define(DATA_SANCTUARY_ACTIVE, false);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.CHERRY_WOLF.get();
    }

    @Override
    protected Brain<CherryWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<CherryWolf> getBrain() {
        return (Brain<CherryWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof CherryWolf cherry && this.isCherryPackmate(cherry)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.CHERRY_PETAL_AID_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.CHERRY_BURST_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.CHERRY_BLOOMING_PATH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.CHERRY_SANCTUARY_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
            this.cancelActiveCherryAbilities();
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.CHERRY_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidCherryCombatTarget(sharedThreat)) {
                this.setTarget(sharedThreat);
            }
        }

        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.tickShortVisuals();
        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        if (this.isBaby()) {
            return;
        }

        if (this.tickCount % FLORA_SCAN_INTERVAL == 0) {
            this.updateBlossomScent(level);
        }
        if (this.tickCount % BLOSSOM_SCENT_INTERVAL == 0) {
            this.tickBlossomScentVisuals(level);
        }

        // Long-form states are mutually exclusive. Each ability gets its own clear
        // gameplay and visual moment instead of becoming another support-effect soup.
        if (this.sanctuaryTicks > 0) {
            this.tickSakuraSanctuary(level);
            return;
        }
        if (this.petalAidTicks > 0) {
            this.tickPetalAid(level);
            return;
        }
        if (this.bloomingPathTicks > 0) {
            this.tickBloomingPath(level);
            return;
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            if (this.tryStartSakuraSanctuary(level)) {
                return;
            }
            if (this.tryStartPetalAid(level)) {
                return;
            }
            if (this.tryCherryBlossomBurst(level)) {
                return;
            }
            this.tryStartBloomingPath(level);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof CherryWolf cherry && this.isCherryPackmate(cherry)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isCherryPackmate(CherryWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isCherryFamilyMember(LivingEntity entity) {
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
        return entity instanceof CherryWolf cherry && this.isCherryPackmate(cherry);
    }

    public boolean isValidCherryCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof CherryWolf cherry && this.isCherryPackmate(cherry)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private boolean isCherryAbilityThreat(LivingEntity candidate, LivingEntity primary) {
        if (candidate instanceof Creeper || !this.isValidCherryCombatTarget(candidate)) {
            return false;
        }
        if (candidate == primary
                || candidate == this.getTarget()
                || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CHERRY_PACK_THREAT.get())
                .orElse(null);
        if (candidate == shared || candidate instanceof Enemy) {
            return true;
        }
        if (candidate instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            return mobTarget != null && this.isCherryFamilyMember(mobTarget);
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // 1) Blossom Scent — passive family/flora awareness
    // ---------------------------------------------------------------------
    private void updateBlossomScent(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = (int) BLOSSOM_SCENT_RADIUS;

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -3; dy <= 4; ++dy) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!this.isUsefulBlossomState(state)) {
                        continue;
                    }
                    double distance = pos.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }

        if (best != null) {
            this.getBrain().setMemory(ModMemoryModuleTypes.CHERRY_BLOSSOM_POS.get(), best);
        } else {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.CHERRY_BLOSSOM_POS.get());
        }
    }

    private boolean isUsefulBlossomState(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.BEEHIVE)
                || state.is(Blocks.SPORE_BLOSSOM);
    }

    private void tickBlossomScentVisuals(ServerLevel level) {
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CHERRY_INJURED_FAMILY.get())
                .orElse(null);
        if (injured != null) {
            WolfVfx.sendParticles("cherry_wolf", level,
                    ParticleTypes.HEART,
                    injured.getX(), injured.getY() + injured.getBbHeight() * 0.75D, injured.getZ(),
                    2, 0.18D, 0.18D, 0.18D, 0.01D);
        }

        BlockPos blossom = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CHERRY_BLOSSOM_POS.get())
                .orElse(null);
        if (blossom != null) {
            WolfVfx.sendParticles("cherry_wolf", level,
                    ParticleTypes.CHERRY_LEAVES,
                    blossom.getX() + 0.5D, blossom.getY() + 1.05D, blossom.getZ() + 0.5D,
                    4, 0.28D, 0.18D, 0.28D, 0.01D);
        }
    }

    // ---------------------------------------------------------------------
    // 2) Petal Aid — sustained healing aura
    // ---------------------------------------------------------------------
    private boolean tryStartPetalAid(ServerLevel level) {
        if (!this.canUseActiveCherryAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.CHERRY_PETAL_AID_COOLDOWN.get())
                || this.hasPackActivePetalAid()) {
            return false;
        }

        LivingEntity injured = this.findFamily(PETAL_AID_RADIUS).stream()
                .filter(member -> member.getHealth() <= member.getMaxHealth() * 0.72F)
                .min(Comparator.comparingDouble(member -> member.getHealth() / member.getMaxHealth()))
                .orElse(null);
        if (injured == null) {
            return false;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.CHERRY_PETAL_AID_COOLDOWN.get(),
                PETAL_AID_COOLDOWN_TICKS);
        this.petalAidTicks = PETAL_AID_DURATION_TICKS;
        this.entityData.set(DATA_PETAL_AID_ACTIVE, true);
        this.applyPackAbilityLockout(30);

        this.sendCherryRing(level, this.position(), 3.0D, 26, ParticleTypes.CHERRY_LEAVES);
        WolfVfx.sendParticles("cherry_wolf", level,
                ParticleTypes.HEART,
                this.getX(), this.getY() + 0.7D, this.getZ(),
                8, 0.55D, 0.35D, 0.55D, 0.02D);
        return true;
    }

    private void tickPetalAid(ServerLevel level) {
        --this.petalAidTicks;

        if (this.tickCount % 4 == 0) {
            double radius = 2.0D + (this.tickCount % 24) / 24.0D * 3.2D;
            this.sendCherryRing(level, this.position(), radius, 16, ParticleTypes.CHERRY_LEAVES);
        }

        if (this.petalAidTicks % PETAL_AID_PULSE_INTERVAL == 0) {
            for (LivingEntity member : this.findFamily(PETAL_AID_RADIUS)) {
                if (member.getHealth() < member.getMaxHealth()) {
                    member.heal(1.0F);
                    WolfVfx.sendParticles("cherry_wolf", level,
                            ParticleTypes.HEART,
                            member.getX(), member.getY() + member.getBbHeight() * 0.72D, member.getZ(),
                            2, 0.18D, 0.22D, 0.18D, 0.01D);
                    WolfVfx.sendParticles("cherry_wolf", level,
                            ParticleTypes.CHERRY_LEAVES,
                            member.getX(), member.getY() + member.getBbHeight() * 0.55D, member.getZ(),
                            5, 0.28D, 0.35D, 0.28D, 0.015D);
                }
            }
        }

        if (this.petalAidTicks <= 0) {
            this.petalAidTicks = 0;
            this.entityData.set(DATA_PETAL_AID_ACTIVE, false);
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 16);
        }
    }

    // ---------------------------------------------------------------------
    // 3) Cherry Blossom Burst — crowd-control burst + ally speed
    // ---------------------------------------------------------------------
    private boolean tryCherryBlossomBurst(ServerLevel level) {
        if (!this.canUseActiveCherryAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.CHERRY_BURST_COOLDOWN.get())) {
            return false;
        }

        LivingEntity primary = this.getPrimaryCherryTarget();
        List<LivingEntity> threats = this.findCherryThreats(this.position(), CHERRY_BURST_RADIUS, primary);
        boolean familyEmergency = this.hasFamilyDefenseEmergency() && !threats.isEmpty();
        if (threats.size() < 2 && !familyEmergency) {
            return false;
        }

        for (LivingEntity threat : threats) {
            if (threat.hurtServer(level, this.damageSources().mobAttack(this), 4.0F)) {
                threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 3, 1));
                Vec3 away = threat.position().subtract(this.position());
                if (away.lengthSqr() > 0.01D) {
                    away = away.normalize();
                    threat.push(away.x * 0.35D, 0.08D, away.z * 0.35D);
                }
            }
        }

        for (LivingEntity member : this.findFamily(CHERRY_BURST_RADIUS + 2.0D)) {
            member.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 4, 1));
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.CHERRY_BURST_COOLDOWN.get(),
                CHERRY_BURST_COOLDOWN_TICKS);
        this.burstVisualTicks = 24;
        this.entityData.set(DATA_BURST_ACTIVE, true);
        this.applyPackAbilityLockout(28);

        this.sendCherryRing(level, this.position(), 3.0D, 26, ParticleTypes.CHERRY_LEAVES);
        this.sendCherryRing(level, this.position(), 5.5D, 38, ParticleTypes.CHERRY_LEAVES);
        this.sendCherryRing(level, this.position(), CHERRY_BURST_RADIUS, 52, ParticleTypes.HAPPY_VILLAGER);
        WolfVfx.sendParticles("cherry_wolf", level,
                ParticleTypes.SWEEP_ATTACK,
                this.getX(), this.getY() + 0.55D, this.getZ(),
                4, 0.8D, 0.25D, 0.8D, 0.0D);
        return true;
    }

    // ---------------------------------------------------------------------
    // 4) Blooming Path — moving family-speed trail
    // ---------------------------------------------------------------------
    private boolean tryStartBloomingPath(ServerLevel level) {
        if (!this.canUseActiveCherryAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.CHERRY_BLOOMING_PATH_COOLDOWN.get())
                || this.hasPackActiveBloomingPath()) {
            return false;
        }

        if (!this.shouldStartBloomingPath()) {
            return false;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.CHERRY_BLOOMING_PATH_COOLDOWN.get(),
                BLOOMING_PATH_COOLDOWN_TICKS);
        this.bloomingPathTicks = BLOOMING_PATH_DURATION_TICKS;
        this.bloomingPathMarkers.clear();
        this.bloomingPathMarkers.add(this.position());
        this.entityData.set(DATA_BLOOMING_PATH_ACTIVE, true);
        this.applyPackAbilityLockout(24);

        WolfVfx.sendParticles("cherry_wolf", level,
                ParticleTypes.CHERRY_LEAVES,
                this.getX(), this.getY() + 0.35D, this.getZ(),
                16, 0.45D, 0.22D, 0.45D, 0.025D);
        return true;
    }

    private boolean shouldStartBloomingPath() {
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner != null && owner.isAlive()) {
                double distance = this.distanceToSqr(owner);
                if (distance >= 6.0D * 6.0D && distance <= 24.0D * 24.0D) {
                    return true;
                }
            }
        }

        CherryWolf adult = this.getBrain()
                .getMemory(ModMemoryModuleTypes.NEAREST_CHERRY_ADULT_PACKMATE.get())
                .orElse(null);
        return adult != null && this.distanceToSqr(adult) >= 6.0D * 6.0D;
    }

    private void tickBloomingPath(ServerLevel level) {
        --this.bloomingPathTicks;

        if (this.tickCount % PATH_MARK_INTERVAL == 0) {
            Vec3 current = this.position();
            if (this.bloomingPathMarkers.isEmpty()
                    || this.bloomingPathMarkers.get(this.bloomingPathMarkers.size() - 1).distanceToSqr(current) >= 0.9D * 0.9D) {
                this.bloomingPathMarkers.add(current);
                if (this.bloomingPathMarkers.size() > 24) {
                    this.bloomingPathMarkers.remove(0);
                }
            }
        }

        if (this.tickCount % 4 == 0) {
            for (int i = Math.max(0, this.bloomingPathMarkers.size() - 10);
                    i < this.bloomingPathMarkers.size(); ++i) {
                Vec3 marker = this.bloomingPathMarkers.get(i);
                WolfVfx.sendParticles("cherry_wolf", level,
                        ParticleTypes.CHERRY_LEAVES,
                        marker.x, marker.y + 0.12D, marker.z,
                        1, 0.12D, 0.04D, 0.12D, 0.0D);
            }
        }

        if (this.bloomingPathTicks % PATH_EFFECT_INTERVAL == 0) {
            for (LivingEntity member : this.findFamily(18.0D)) {
                if (this.isNearBloomingPath(member.position(), 1.8D)) {
                    member.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, true, false));
                }
            }

            // The trail also exposes ambushers crossing it without becoming another
            // damage ability.
            LivingEntity primary = this.getPrimaryCherryTarget();
            for (LivingEntity threat : this.findCherryThreats(this.position(), 18.0D, primary)) {
                if (this.isNearBloomingPath(threat.position(), 2.4D)) {
                    threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false));
                }
            }
        }

        if (this.bloomingPathTicks <= 0) {
            this.bloomingPathTicks = 0;
            this.entityData.set(DATA_BLOOMING_PATH_ACTIVE, false);
            this.bloomingPathMarkers.clear();
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 14);
        }
    }

    private boolean isNearBloomingPath(Vec3 position, double radius) {
        double radiusSqr = radius * radius;
        return this.bloomingPathMarkers.stream().anyMatch(marker -> marker.distanceToSqr(position) <= radiusSqr);
    }

    // ---------------------------------------------------------------------
    // 5) Sakura Sanctuary — fixed protective/healing zone
    // ---------------------------------------------------------------------
    private boolean tryStartSakuraSanctuary(ServerLevel level) {
        if (!this.canUseActiveCherryAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.CHERRY_SANCTUARY_COOLDOWN.get())
                || this.hasPackActiveSanctuary()) {
            return false;
        }

        List<LivingEntity> family = this.findFamily(SAKURA_SANCTUARY_RADIUS);
        List<LivingEntity> injured = family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.65F)
                .sorted(Comparator.comparingDouble(member -> member.getHealth() / member.getMaxHealth()))
                .toList();

        LivingEntity primary = this.getPrimaryCherryTarget();
        int threats = this.findCherryThreats(this.position(), SAKURA_SANCTUARY_RADIUS, primary).size();
        boolean criticalEmergency = !injured.isEmpty()
                && injured.getFirst().getHealth() <= injured.getFirst().getMaxHealth() * 0.30F
                && threats >= 1;
        boolean packEmergency = injured.size() >= 2 && threats >= 2;
        boolean overwhelmed = threats >= 4 && !family.isEmpty();
        if (!criticalEmergency && !packEmergency && !overwhelmed) {
            return false;
        }

        LivingEntity anchor = injured.isEmpty() ? this : injured.getFirst();
        this.sanctuaryCenter = anchor.position();
        this.sanctuaryTicks = SAKURA_SANCTUARY_DURATION_TICKS;
        this.setPackCooldown(
                ModMemoryModuleTypes.CHERRY_SANCTUARY_COOLDOWN.get(),
                SAKURA_SANCTUARY_COOLDOWN_TICKS);
        this.entityData.set(DATA_SANCTUARY_ACTIVE, true);
        this.applyPackAbilityLockout(SAKURA_SANCTUARY_DURATION_TICKS);

        this.sendCherryRing(level, this.sanctuaryCenter, 4.0D, 32, ParticleTypes.CHERRY_LEAVES);
        this.sendCherryRing(level, this.sanctuaryCenter, 8.0D, 44, ParticleTypes.CHERRY_LEAVES);
        this.sendCherryRing(level, this.sanctuaryCenter, SAKURA_SANCTUARY_RADIUS, 60, ParticleTypes.HAPPY_VILLAGER);
        WolfVfx.sendParticles("cherry_wolf", level,
                ParticleTypes.HEART,
                this.sanctuaryCenter.x, this.sanctuaryCenter.y + 0.7D, this.sanctuaryCenter.z,
                16, 1.3D, 0.7D, 1.3D, 0.03D);
        return true;
    }

    private void tickSakuraSanctuary(ServerLevel level) {
        --this.sanctuaryTicks;
        if (this.sanctuaryCenter == null) {
            this.sanctuaryCenter = this.position();
        }

        if (this.tickCount % 5 == 0) {
            double pulse = 3.0D + ((SAKURA_SANCTUARY_DURATION_TICKS - this.sanctuaryTicks) % 60) / 60.0D * 8.0D;
            this.sendCherryRing(level, this.sanctuaryCenter, pulse, 28, ParticleTypes.CHERRY_LEAVES);
        }

        if (this.sanctuaryTicks % SANCTUARY_PULSE_INTERVAL == 0) {
            AABB zone = new AABB(
                    this.sanctuaryCenter.x - SAKURA_SANCTUARY_RADIUS,
                    this.sanctuaryCenter.y - 5.0D,
                    this.sanctuaryCenter.z - SAKURA_SANCTUARY_RADIUS,
                    this.sanctuaryCenter.x + SAKURA_SANCTUARY_RADIUS,
                    this.sanctuaryCenter.y + 5.0D,
                    this.sanctuaryCenter.z + SAKURA_SANCTUARY_RADIUS);

            for (LivingEntity member : level.getEntitiesOfClass(
                    LivingEntity.class,
                    zone,
                    this::isCherryFamilyMember)) {
                if (member.getHealth() < member.getMaxHealth()) {
                    member.heal(1.25F);
                }
                member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false));
                member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50, 0, true, false));
                WolfVfx.sendParticles("cherry_wolf", level,
                        ParticleTypes.CHERRY_LEAVES,
                        member.getX(), member.getY() + member.getBbHeight() * 0.60D, member.getZ(),
                        3, 0.20D, 0.28D, 0.20D, 0.01D);
            }

            LivingEntity primary = this.getPrimaryCherryTarget();
            for (LivingEntity threat : this.findCherryThreats(this.sanctuaryCenter, SAKURA_SANCTUARY_RADIUS, primary)) {
                threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 0, true, false));
                threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, true, false));
                Vec3 away = threat.position().subtract(this.sanctuaryCenter);
                if (away.lengthSqr() > 0.01D) {
                    away = away.normalize();
                    threat.push(away.x * 0.28D, 0.04D, away.z * 0.28D);
                }
            }
        }

        if (this.sanctuaryTicks <= 0) {
            this.sanctuaryTicks = 0;
            this.sanctuaryCenter = null;
            this.entityData.set(DATA_SANCTUARY_ACTIVE, false);
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 24);
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers / family coordination
    // ---------------------------------------------------------------------
    private LivingEntity getPrimaryCherryTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.isValidCherryCombatTarget(target)) {
            return target;
        }
        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && familyTarget.isAlive() && this.isValidCherryCombatTarget(familyTarget)) {
            return familyTarget;
        }
        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.CHERRY_PACK_THREAT.get())
                .orElse(null);
        return shared != null && shared.isAlive() && this.isValidCherryCombatTarget(shared) ? shared : null;
    }

    private List<LivingEntity> findFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isCherryFamilyMember);
    }

    private List<LivingEntity> findCherryThreats(Vec3 center, double radius, LivingEntity primary) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isCherryAbilityThreat(candidate, primary));
    }

    private boolean canUseActiveCherryAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.petalAidTicks <= 0
                && this.bloomingPathTicks <= 0
                && this.sanctuaryTicks <= 0;
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

    private boolean hasPackActivePetalAid() {
        return this.hasPackAbility(CherryWolf::isPetalAidActive);
    }

    private boolean hasPackActiveBloomingPath() {
        return this.hasPackAbility(CherryWolf::isBloomingPathActive);
    }

    private boolean hasPackActiveSanctuary() {
        return this.hasPackAbility(CherryWolf::isSakuraSanctuaryActive);
    }

    private boolean hasPackAbility(java.util.function.Predicate<CherryWolf> predicate) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getEntitiesOfClass(
                        CherryWolf.class,
                        this.getBoundingBox().inflate(CherryWolfPackSensor.PACK_SCAN_RADIUS),
                        mate -> mate != this && mate.isAlive() && this.isCherryPackmate(mate))
                .stream()
                .anyMatch(predicate);
    }

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }
        for (CherryWolf mate : level.getEntitiesOfClass(
                CherryWolf.class,
                this.getBoundingBox().inflate(CherryWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isCherryPackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void applyPackAbilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, ticks);
            return;
        }
        for (CherryWolf mate : level.getEntitiesOfClass(
                CherryWolf.class,
                this.getBoundingBox().inflate(CherryWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isCherryPackmate(wolf)))) {
            mate.abilityLockoutTicks = Math.max(mate.abilityLockoutTicks, ticks);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidCherryCombatTarget(threat)) {
            return;
        }
        for (CherryWolf mate : level.getEntitiesOfClass(
                CherryWolf.class,
                this.getBoundingBox().inflate(CherryWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isCherryPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.CHERRY_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidCherryCombatTarget(threat)) {
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

    private void tickShortVisuals() {
        if (this.burstVisualTicks > 0 && --this.burstVisualTicks <= 0) {
            this.burstVisualTicks = 0;
            this.entityData.set(DATA_BURST_ACTIVE, false);
        }
    }

    private void cancelActiveCherryAbilities() {
        this.petalAidTicks = 0;
        this.bloomingPathTicks = 0;
        this.sanctuaryTicks = 0;
        this.burstVisualTicks = 0;
        this.sanctuaryCenter = null;
        this.bloomingPathMarkers.clear();
        this.entityData.set(DATA_PETAL_AID_ACTIVE, false);
        this.entityData.set(DATA_BURST_ACTIVE, false);
        this.entityData.set(DATA_BLOOMING_PATH_ACTIVE, false);
        this.entityData.set(DATA_SANCTUARY_ACTIVE, false);
    }

    private void sendCherryRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("cherry_wolf", level,
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.14D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    public boolean isPetalAidActive() {
        return this.entityData.get(DATA_PETAL_AID_ACTIVE);
    }

    public boolean isCherryBlossomBurstActive() {
        return this.entityData.get(DATA_BURST_ACTIVE);
    }

    public boolean isBloomingPathActive() {
        return this.entityData.get(DATA_BLOOMING_PATH_ACTIVE);
    }

    public boolean isSakuraSanctuaryActive() {
        return this.entityData.get(DATA_SANCTUARY_ACTIVE);
    }

    public static boolean checkCherryWolfSpawnRules(
            EntityType<CherryWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)
                && isBrightEnoughToSpawn(level, pos);
    }
}
