package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.SpiritWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/** Spirit Wolf: family medic, soul guardian, and supernatural threat sentinel. */
public final class SpiritWolf extends AbstractWolfismWolf {
    public static final int SPIRIT_MEND_COOLDOWN_TICKS = 20 * 22;
    public static final int SOUL_GUARD_COOLDOWN_TICKS = 20 * 42;
    public static final int GUARDIAN_OF_SOULS_COOLDOWN_TICKS = 20 * 105;
    public static final double SUPPORT_RADIUS = 10.0D;
    public static final double GUARDIAN_RADIUS = 13.0D;

    private static final int SENSE_INTERVAL = 20;
    private static final int LINK_INTERVAL = 40;
    private static final int MEND_SUPPORT_LOCKOUT = 20 * 2;
    private static final int GUARD_SUPPORT_LOCKOUT = 20 * 3;
    private static final int GUARDIAN_SUPPORT_LOCKOUT = 20 * 6;
    private static final EntityDataAccessor<Boolean> DATA_MEND_ACTIVE = SynchedEntityData.defineId(SpiritWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GUARD_ACTIVE = SynchedEntityData.defineId(SpiritWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GUARDIAN_ACTIVE = SynchedEntityData.defineId(SpiritWolf.class, EntityDataSerializers.BOOLEAN);
    private int mendVisualTicks, guardVisualTicks, guardianVisualTicks;
    private int supportLockoutTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<SpiritWolf> PROVIDER = Brain.<SpiritWolf>provider(
                ImmutableList.of(ModSensorTypes.SPIRIT_PACK.get()), wolf -> List.of());
    }

    public SpiritWolf(EntityType<? extends SpiritWolf> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_MEND_ACTIVE, false); data.define(DATA_GUARD_ACTIVE, false); data.define(DATA_GUARDIAN_ACTIVE, false);
    }
    @Override protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() { return ModEntities.SPIRIT_WOLF.get(); }
    @Override protected Brain<SpiritWolf> makeBrain(Brain.Packed packed) { return BrainHolder.PROVIDER.makeBrain(this, packed); }
    @SuppressWarnings("unchecked") @Override public Brain<SpiritWolf> getBrain() { return (Brain<SpiritWolf>) super.getBrain(); }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof SpiritWolf spirit && this.isSpiritPackmate(spirit)) this.setTarget(null);
        tickCooldown(ModMemoryModuleTypes.SPIRIT_MEND_COOLDOWN.get());
        tickCooldown(ModMemoryModuleTypes.SPIRIT_SOUL_GUARD_COOLDOWN.get());
        tickCooldown(ModMemoryModuleTypes.SPIRIT_GUARDIAN_COOLDOWN.get());
        this.getBrain().tick(level, this);
        if (this.isBaby()) this.setTarget(null);
        super.customServerAiStep(level);
    }

    @Override public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        if (mendVisualTicks > 0 && --mendVisualTicks == 0) entityData.set(DATA_MEND_ACTIVE, false);
        if (guardVisualTicks > 0 && --guardVisualTicks == 0) entityData.set(DATA_GUARD_ACTIVE, false);
        if (guardianVisualTicks > 0 && --guardianVisualTicks == 0) entityData.set(DATA_GUARDIAN_ACTIVE, false);
        if (supportLockoutTicks > 0) --supportLockoutTicks;
        if (this.isBaby()) return;
        if (tickCount % SENSE_INTERVAL == 0) tickSpiritSense(level);
        if (tickCount % 10 == 0) tickEmergencySupport(level);
        if (tickCount % LINK_INTERVAL == 0 && supportLockoutTicks == 0) tickSpiritLink(level);
    }

    @Override public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && !(target instanceof SpiritWolf spirit && isSpiritPackmate(spirit)) && super.canAttack(target);
    }

    public boolean isSpiritPackmate(SpiritWolf other) {
        if (other == this || other.isTame() != this.isTame()) return false;
        return !this.isTame() || Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isSpiritFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;
        if (this.isTame()) {
            if (entity == this.getOwner()) return true;
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof SpiritWolf spirit && isSpiritPackmate(spirit);
    }

    public boolean isValidSpiritCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) return false;
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public boolean isSupernaturalThreat(LivingEntity target) {
        if (!isValidSpiritCombatTarget(target)) return false;
        return target instanceof Enemy && (target instanceof Zombie || target instanceof Skeleton || target instanceof Phantom
                || target.getType().toString().contains("wither") || target.getType().toString().contains("vex")
                || target.getType().toString().contains("warden"));
    }

    /** Spirit Sense: visible warning and target awareness, stronger at night. */
    private void tickSpiritSense(ServerLevel level) {
        LivingEntity injured = getBrain().getMemory(ModMemoryModuleTypes.SPIRIT_INJURED_FAMILY.get()).orElse(null);
        LivingEntity threat = getBrain().getMemory(ModMemoryModuleTypes.SPIRIT_SUPERNATURAL_THREAT.get()).orElse(null);
        if (injured != null) level.sendParticles(ParticleTypes.SOUL, injured.getX(), injured.getY() + injured.getBbHeight() * 0.65D, injured.getZ(), 2, .2, .2, .2, .01);
        if (threat != null && level.isDarkOutside()) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, threat.getX(), threat.getY() + threat.getBbHeight() * .65D, threat.getZ(), 3, .2, .3, .2, .01);
            threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 35, 0, true, false));
            if (this.getTarget() == null && !this.isTame()) this.setTarget(threat);
        }
    }

    /**
     * Spirit Link: a visible, targeted healing bond rather than another invisible AoE heal.
     * The most injured family member receives the main trickle and a second injured
     * member receives half of it, visually "sharing" the healing through the link.
     */
    private void tickSpiritLink(ServerLevel level) {
        List<LivingEntity> injured = findFamily(SUPPORT_RADIUS).stream()
                .filter(member -> member.getHealth() < member.getMaxHealth())
                .sorted(Comparator.comparingDouble(member -> member.getHealth() / member.getMaxHealth()))
                .toList();
        if (injured.isEmpty()) return;

        LivingEntity primary = injured.getFirst();
        primary.heal(0.8F);
        spiritLinkParticles(level, this, primary, 7);

        if (injured.size() > 1) {
            LivingEntity secondary = injured.get(1);
            secondary.heal(0.4F);
            spiritLinkParticles(level, primary, secondary, 5);
        }
    }

    private void tickEmergencySupport(ServerLevel level) {
        if (supportLockoutTicks > 0) return;
        LivingEntity critical = getBrain().getMemory(ModMemoryModuleTypes.SPIRIT_CRITICAL_FAMILY.get()).orElse(null);
        int threats = countImmediateThreats(GUARDIAN_RADIUS);
        if (critical != null && threats >= 3 && !cooling(ModMemoryModuleTypes.SPIRIT_GUARDIAN_COOLDOWN.get())) {
            performGuardianOfSouls(level); return;
        }
        if (critical != null && !cooling(ModMemoryModuleTypes.SPIRIT_SOUL_GUARD_COOLDOWN.get())) {
            performSoulGuard(level, critical); return;
        }
        LivingEntity injured = getBrain().getMemory(ModMemoryModuleTypes.SPIRIT_INJURED_FAMILY.get()).orElse(null);
        if (injured != null && injured.getHealth() <= injured.getMaxHealth() * .68F && !cooling(ModMemoryModuleTypes.SPIRIT_MEND_COOLDOWN.get()))
            performSpiritMend(level);
    }

    public void performSpiritMend(ServerLevel level) {
        for (LivingEntity member : findFamily(SUPPORT_RADIUS)) {
            if (member.getHealth() >= member.getMaxHealth()) continue;
            member.heal(4.0F);
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0));
            level.sendParticles(ParticleTypes.WITCH, member.getX(), member.getY() + member.getBbHeight() * .55D, member.getZ(), 7, .28, .32, .28, .02);
            level.sendParticles(ParticleTypes.END_ROD, member.getX(), member.getY() + member.getBbHeight() * .7D, member.getZ(), 2, .18, .22, .18, .01);
        }
        getBrain().setMemory(ModMemoryModuleTypes.SPIRIT_MEND_COOLDOWN.get(), SPIRIT_MEND_COOLDOWN_TICKS);
        applyPackSupportLockout(MEND_SUPPORT_LOCKOUT);
        mendVisualTicks = 22; entityData.set(DATA_MEND_ACTIVE, true);
        soulRing(level, 3.25D, 24);
    }

    public void performSoulGuard(ServerLevel level, LivingEntity critical) {
        for (LivingEntity member : findFamily(SUPPORT_RADIUS)) {
            if (member == critical || member.getHealth() <= member.getMaxHealth() * .45F) {
                // Soul Guard is intentionally defensive, not a second healing pulse.
                member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 8, 1));
                member.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 10, 1));
                level.sendParticles(ParticleTypes.PORTAL, member.getX(), member.getY() + member.getBbHeight() * .55D, member.getZ(), 18, .38, .48, .38, .12);
                level.sendParticles(ParticleTypes.END_ROD, member.getX(), member.getY() + member.getBbHeight() * .8D, member.getZ(), 4, .22, .3, .22, .015);
            }
        }
        getBrain().setMemory(ModMemoryModuleTypes.SPIRIT_SOUL_GUARD_COOLDOWN.get(), SOUL_GUARD_COOLDOWN_TICKS);
        applyPackSupportLockout(GUARD_SUPPORT_LOCKOUT);
        guardVisualTicks = 28; entityData.set(DATA_GUARD_ACTIVE, true);
        soulRingAt(level, critical.position(), 2.0D, 20, ParticleTypes.PORTAL);
    }

    public void performGuardianOfSouls(ServerLevel level) {
        for (LivingEntity member : findFamily(GUARDIAN_RADIUS)) {
            member.heal(7.0F);
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
            member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 10, 1));
            member.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 12, 2));
        }
        for (LivingEntity threat : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(GUARDIAN_RADIUS), this::isImmediateSpiritThreat)) {
            Vec3 away = threat.position().subtract(position());
            if (away.lengthSqr() > .01) { away = away.normalize(); threat.push(away.x * 1.15D, .28D, away.z * 1.15D); }
            threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 8, 1));
            threat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 6, 1));
        }
        getBrain().setMemory(ModMemoryModuleTypes.SPIRIT_GUARDIAN_COOLDOWN.get(), GUARDIAN_OF_SOULS_COOLDOWN_TICKS);
        applyPackSupportLockout(GUARDIAN_SUPPORT_LOCKOUT);
        guardianVisualTicks = 36; entityData.set(DATA_GUARDIAN_ACTIVE, true);
        soulRing(level, 5D, 32); soulRing(level, 9D, 44); soulRing(level, 12.5D, 56);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY()+.9, getZ(), 45,1.1,.8,1.1,.06);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY()+.5, getZ(), 32,1.2,.4,1.2,.025);
    }

    private List<LivingEntity> findFamily(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isSpiritFamilyMember);
    }
    private int countImmediateThreats(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isImmediateSpiritThreat).size();
    }

    /**
     * "Can attack" is deliberately not the same thing as "is threatening the family".
     * This keeps Guardian of Souls from treating sheep, cows, or unrelated wolves as
     * dark forces merely because vanilla technically allows combat with them.
     */
    private boolean isImmediateSpiritThreat(LivingEntity target) {
        if (!isValidSpiritCombatTarget(target)) return false;
        if (target instanceof Enemy) return true;
        if (target == this.getTarget()) return true;
        LivingEntity shared = getBrain().getMemory(ModMemoryModuleTypes.SPIRIT_PACK_THREAT.get()).orElse(null);
        if (target == shared) return true;
        if (target instanceof Mob mob) {
            LivingEntity mobTarget = mob.getTarget();
            return mobTarget != null && isSpiritFamilyMember(mobTarget);
        }
        return false;
    }
    private boolean cooling(MemoryModuleType<Integer> memory) { return getBrain().getMemory(memory).orElse(0) > 0; }
    private void tickCooldown(MemoryModuleType<Integer> memory) {
        int c=getBrain().getMemory(memory).orElse(0); if(c>1)getBrain().setMemory(memory,c-1); else if(c==1)getBrain().eraseMemory(memory);
    }
    private void soulRing(ServerLevel level, double radius, int points) {
        for(int i=0;i<points;i++){ double a=Math.PI*2*i/points; level.sendParticles(ParticleTypes.SOUL,
                getX()+Math.cos(a)*radius,getY()+.16,getZ()+Math.sin(a)*radius,1,.01,.02,.01,0); }
    }

    private void applyPackSupportLockout(int ticks) {
        if (!(level() instanceof ServerLevel level)) {
            supportLockoutTicks = Math.max(supportLockoutTicks, ticks);
            return;
        }
        for (SpiritWolf mate : level.getEntitiesOfClass(SpiritWolf.class,
                getBoundingBox().inflate(SpiritWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || isSpiritPackmate(wolf)))) {
            mate.supportLockoutTicks = Math.max(mate.supportLockoutTicks, ticks);
        }
    }

    private void spiritLinkParticles(ServerLevel level, LivingEntity from, LivingEntity to, int points) {
        Vec3 start = new Vec3(from.getX(), from.getY() + from.getBbHeight() * .62D, from.getZ());
        Vec3 end = new Vec3(to.getX(), to.getY() + to.getBbHeight() * .62D, to.getZ());
        for (int i = 1; i <= points; i++) {
            double t = i / (double) (points + 1);
            Vec3 p = start.lerp(end, t);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL, p.x, p.y, p.z, 1, .01, .01, .01, 0);
        }
    }

    private void soulRingAt(ServerLevel level, Vec3 center, double radius, int points, net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + .16D, center.z + Math.sin(a) * radius, 1, .01, .02, .01, 0);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(level() instanceof ServerLevel level) || threat == null || !threat.isAlive()) return;
        for (SpiritWolf mate : level.getEntitiesOfClass(SpiritWolf.class, getBoundingBox().inflate(SpiritWolfPackSensor.PACK_SCAN_RADIUS),
                w -> w.isAlive() && (w == this || isSpiritPackmate(w)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.SPIRIT_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidSpiritCombatTarget(threat)) mate.setTarget(threat);
        }
    }

    public boolean isSpiritMendActive(){return entityData.get(DATA_MEND_ACTIVE);}
    public boolean isSoulGuardActive(){return entityData.get(DATA_GUARD_ACTIVE);}
    public boolean isGuardianOfSoulsActive(){return entityData.get(DATA_GUARDIAN_ACTIVE);}

    public static boolean checkSpiritWolfSpawnRules(EntityType<SpiritWolf> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) >= 7;
    }
}
