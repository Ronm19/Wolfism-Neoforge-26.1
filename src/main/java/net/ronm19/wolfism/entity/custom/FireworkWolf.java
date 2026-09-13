package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.FireworkWolfRocketGoal;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #7: Firework Wolf / Fourth of July Wolf.
 *
 * <p>Firework Charge, Rocket Leap and Grand Finale turn this wolf into a
 * mobile burst-damage and crowd-control specialist. Every explosion is
 * entity-only: family is filtered out and terrain is never modified.</p>
 */
public final class FireworkWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_RADIUS = 13.0D;
    public static final double THREAT_RADIUS = 32.0D;

    private static final double BASE_HEALTH = 40.0D;
    private static final int CHARGE_DURATION = 14;
    private static final int CHARGE_COOLDOWN = 20 * 10;
    private static final int LEAP_COOLDOWN = 20 * 8;
    private static final int FIRECRACKER_COOLDOWN = 20 * 24;
    private static final int GRAND_FINALE_DURATION = 20 * 14;
    private static final int GRAND_FINALE_COOLDOWN = 20 * 70;
    private static final int ROCKET_FALL_PROTECTION_TICKS = 20 * 4;
    private static final String NEXT_SPARK_HIT = "WolfismFireworkNextSparkHit";

    private static final EntityDataAccessor<Boolean> DATA_FIREWORK_ACTIVE =
            SynchedEntityData.defineId(FireworkWolf.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions ROCKET_RED =
            new DustParticleOptions(0xED354B, 1.0F);
    private static final DustParticleOptions ROCKET_BLUE =
            new DustParticleOptions(0x2D8AE6, 1.0F);
    private static final DustParticleOptions ROCKET_GOLD =
            new DustParticleOptions(0xFFD34A, 1.08F);
    private static final DustParticleOptions ROCKET_WHITE =
            new DustParticleOptions(0xF4F7FF, 1.0F);

    private LivingEntity chargeTarget;
    private Vec3 chargeDirection = Vec3.ZERO;
    private int chargeTicks;
    private int chargeCooldown;
    private int leapCooldown;
    private int firecrackerCooldown;
    private int grandFinaleTicks;
    private int grandFinaleCooldown;
    private int finaleLaunchDelay;
    private int rocketFallProtectionTicks;
    private final List<FinaleMarker> finaleMarkers = new ArrayList<>();

    private int chargeCount;
    private int chargeImpactCount;
    private int rocketLeapCount;
    private int rocketBiteCount;
    private int sparkTrailHitCount;
    private int firecrackerBurstCount;
    private int celebrationPulseCount;
    private int grandFinaleCount;
    private int grandFinaleLaunchCount;
    private int grandFinaleDetonationCount;

    private static final class BrainHolder {
        private static final Brain.Provider<FireworkWolf> PROVIDER =
                Brain.<FireworkWolf>provider(
                        List.of(ModSensorTypes.FIREWORK_AWARENESS.get()),
                        wolf -> List.of());
    }

    private static final class FinaleMarker {
        private final Vec3 impact;
        private int ticks;

        private FinaleMarker(Vec3 impact, int ticks) {
            this.impact = impact;
            this.ticks = ticks;
        }
    }

    public FireworkWolf(EntityType<? extends FireworkWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.FIREWORK_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new FireworkWolfRocketGoal(this));
    }

    @Override
    protected Brain<FireworkWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<FireworkWolf> getBrain() {
        return (Brain<FireworkWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FIREWORK_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.37D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.2D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            float previous = this.getHealth();
            health.setBaseValue(BASE_HEALTH);
            this.setHealth(this.isTame()
                    ? this.getMaxHealth()
                    : Math.min(previous, this.getMaxHealth()));
        }
    }

    @Override
    protected boolean isHolidaySeasonActive(LocalDate date) {
        return date.getMonthValue() == 7 && date.getDayOfMonth() <= 7;
    }

    public static boolean isFireworkSeasonOpen(ServerLevel level) {
        if (level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return today.getMonthValue() == 7 && today.getDayOfMonth() <= 7;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()
                && !this.isAngry()
                && !this.isHolidayRecovering()
                && stack.is(Items.FIREWORK_ROCKET)) {
            if (this.level() instanceof ServerLevel level) {
                stack.consume(1, player);
                if (this.random.nextFloat() < 0.75F
                        && !EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    this.setOrderedToSit(true);
                    level.broadcastEntityEvent(this, (byte) 7);
                    fireworkBurst(level, this.position(), 22, false);
                    this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.70F, 1.25F);
                } else {
                    level.broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public boolean canUseFireworkCombat() {
        return this.isAlive()
                && !this.isRemoved()
                && !this.isBaby()
                && !this.isNoAi()
                && !this.isHolidayRecovering()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isFireworkVisualActive() {
        return this.entityData.get(DATA_FIREWORK_ACTIVE);
    }

    public boolean isFireworkMovementActive() {
        return this.chargeTicks > 0;
    }

    public boolean isGrandFinaleActive() {
        return this.grandFinaleTicks > 0 && this.canUseFireworkCombat();
    }

    public boolean isFireworkFamily(LivingEntity entity) {
        if (entity == this) return true;
        if (entity == null
                || !entity.isAlive()
                || entity.isRemoved()
                || entity.level() != this.level()
                || !this.isTame()) return false;
        LivingEntity owner = this.getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;
        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            LivingEntity petOwner = pet.getOwner();
            return petOwner != null && petOwner.getUUID().equals(owner.getUUID());
        }
        return false;
    }

    public boolean isFireworkThreat(LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == this
                || entity.isSpectator()
                || this.isFireworkFamily(entity)
                || this.isAlliedTo(entity)) return false;
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof Enemy
                || entity == this.getTarget()
                || entity instanceof Mob mob && this.isFireworkFamily(mob.getTarget());
    }

    public List<LivingEntity> getFireworkThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THREAT_RADIUS),
                threat -> this.isFireworkThreat(threat)
                        && this.distanceToSqr(threat) <= THREAT_RADIUS * THREAT_RADIUS);
    }

    public boolean canStartFireworkCharge(LivingEntity target) {
        if (!this.canUseFireworkCombat()
                || this.chargeCooldown > 0
                || this.chargeTicks > 0
                || !this.isFireworkThreat(target)
                || !this.getSensing().hasLineOfSight(target)) return false;
        double distance = this.distanceToSqr(target);
        return distance >= 6.0D * 6.0D && distance <= 20.0D * 20.0D;
    }

    public boolean tryStartFireworkCharge(LivingEntity target) {
        if (!canStartFireworkCharge(target)) return false;
        Vec3 direction = target.getEyePosition()
                .subtract(this.getEyePosition())
                .normalize();
        this.chargeTarget = target;
        this.chargeDirection = new Vec3(direction.x, Math.max(0.08D, direction.y), direction.z)
                .normalize();
        this.chargeTicks = CHARGE_DURATION;
        this.chargeCooldown = CHARGE_COOLDOWN;
        this.rocketFallProtectionTicks = ROCKET_FALL_PROTECTION_TICKS;
        this.fallDistance = 0.0D;
        this.getNavigation().stop();
        ++this.chargeCount;
        if (this.level() instanceof ServerLevel level) {
            fireworkBurst(level, this.position(), 14, false);
            this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.70F, 1.15F);
        }
        return true;
    }

    public boolean canStartRocketLeap(LivingEntity target) {
        if (!this.canUseFireworkCombat()
                || this.leapCooldown > 0
                || this.chargeTicks > 0
                || !this.onGround()
                || !this.isFireworkThreat(target)) return false;
        double distance = this.distanceToSqr(target);
        return distance >= 4.0D * 4.0D
                && distance <= 18.0D * 18.0D
                && (target.getY() > this.getY() + 1.4D || this.chargeCooldown > 0);
    }

    public boolean tryStartRocketLeap(LivingEntity target) {
        if (!canStartRocketLeap(target)) return false;
        Vec3 toward = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z).normalize().scale(0.62D);
        this.setDeltaMovement(horizontal.x, 0.78D, horizontal.z);
        this.leapCooldown = LEAP_COOLDOWN;
        this.rocketFallProtectionTicks = ROCKET_FALL_PROTECTION_TICKS;
        this.fallDistance = 0.0D;
        ++this.rocketLeapCount;
        if (this.level() instanceof ServerLevel level) {
            fireworkBurst(level, this.position(), 16, false);
            this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.65F, 1.35F);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;

        tickCooldowns();

        if (this.rocketFallProtectionTicks > 0) {
            --this.rocketFallProtectionTicks;
            this.fallDistance = 0.0D;
        }

        if (!this.canUseFireworkCombat()) {
            cancelFireworkStates();
            return;
        }

        // Delayed finale impacts are part of the active ability and therefore
        // stop immediately when Sit or Recall cancels Firework combat.
        tickFinaleMarkers(level);

        if (this.chargeTicks > 0) tickFireworkCharge(level);
        if (this.grandFinaleTicks > 0) {
            --this.grandFinaleTicks;
            if (--this.finaleLaunchDelay <= 0) {
                this.finaleLaunchDelay = 8;
                launchGrandFinaleFirework(level);
            }
        }

        this.entityData.set(
                DATA_FIREWORK_ACTIVE,
                this.chargeTicks > 0
                        || this.grandFinaleTicks > 0
                        || !this.finaleMarkers.isEmpty());

        if (this.isWolfismWorkTick(20) && !this.getFireworkThreats(level).isEmpty()) {
            pulseCelebrationField(level);
        }

        if (!this.isWolfismWorkTick(10) || this.grandFinaleTicks > 0) return;
        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIREWORK_HOSTILE_COUNT.get())
                .orElse(0);
        int clusterSize = this.getBrain()
                .getMemory(ModMemoryModuleTypes.FIREWORK_CLUSTER_SIZE.get())
                .orElse(0);

        if (this.grandFinaleCooldown == 0
                && (hostileCount >= 5 || clusterSize >= 4)) {
            startGrandFinale(level);
            return;
        }
        if (this.firecrackerCooldown == 0 && clusterSize >= 2) {
            startFirecrackerBurst(level);
        }
    }

    private void tickCooldowns() {
        if (this.chargeCooldown > 0) --this.chargeCooldown;
        if (this.leapCooldown > 0) --this.leapCooldown;
        if (this.firecrackerCooldown > 0) --this.firecrackerCooldown;
        if (this.grandFinaleCooldown > 0) --this.grandFinaleCooldown;
    }

    private void cancelFireworkStates() {
        this.chargeTicks = 0;
        this.chargeTarget = null;
        this.chargeDirection = Vec3.ZERO;
        this.setDeltaMovement(this.getDeltaMovement().scale(0.45D));
        this.grandFinaleTicks = 0;
        this.finaleMarkers.clear();
        this.entityData.set(DATA_FIREWORK_ACTIVE, false);
    }

    private void tickFireworkCharge(ServerLevel level) {
        --this.chargeTicks;
        if (this.chargeTarget != null && this.isFireworkThreat(this.chargeTarget)) {
            Vec3 desired = this.chargeTarget.getEyePosition()
                    .subtract(this.getEyePosition())
                    .normalize();
            this.chargeDirection = this.chargeDirection.scale(0.72D)
                    .add(desired.scale(0.28D))
                    .normalize();
        }
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                this.chargeDirection.x * 1.08D,
                Math.max(current.y, this.chargeDirection.y * 0.75D),
                this.chargeDirection.z * 1.08D);
        emitSparkTrail(level);

        LivingEntity impact = level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(0.75D),
                        this::isFireworkThreat)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (impact != null) {
            controlledBlast(level, impact.position(), 3.0D, 7.0F, 1.20D, true);
            ++this.chargeImpactCount;
            this.chargeTicks = 0;
        }
        if (this.chargeTicks <= 0) {
            this.chargeTarget = null;
            this.chargeDirection = Vec3.ZERO;
        }
    }

    private void emitSparkTrail(ServerLevel level) {
        WolfVfx.sendParticles("firework_wolf", level,
                ParticleTypes.FIREWORK,
                this.getX(), this.getY(0.35D), this.getZ(),
                5, 0.25D, 0.18D, 0.25D, 0.02D);
        WolfVfx.sendParticles("firework_wolf", level,
                this.tickCount % 2 == 0 ? ROCKET_RED : ROCKET_BLUE,
                this.getX(), this.getY(0.28D), this.getZ(),
                4, 0.28D, 0.12D, 0.28D, 0.0D);

        long now = level.getGameTime();
        for (LivingEntity threat : level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(1.35D),
                this::isFireworkThreat)) {
            if (threat.getPersistentData().getLongOr(NEXT_SPARK_HIT, 0L) > now) continue;
            threat.getPersistentData().putLong(NEXT_SPARK_HIT, now + 10L);
            if (threat.hurtServer(level, this.damageSources().mobAttack(this), 1.5F)) {
                threat.setRemainingFireTicks(Math.max(threat.getRemainingFireTicks(), 30));
                ++this.sparkTrailHitCount;
            }
        }
    }

    private void startFirecrackerBurst(ServerLevel level) {
        this.firecrackerCooldown = FIRECRACKER_COOLDOWN;
        ++this.firecrackerBurstCount;
        List<LivingEntity> threats = this.getFireworkThreats(level).stream()
                .sorted(Comparator.comparingDouble(this::distanceToSqr))
                .limit(5)
                .toList();
        for (int i = 0; i < threats.size(); ++i) {
            LivingEntity threat = threats.get(i);
            Vec3 predicted = threat.position().add(threat.getDeltaMovement().scale(4.0D));
            controlledBlast(level, predicted, 2.6D, 4.0F, 0.95D, false);
        }
        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.85F, 1.15F);
    }

    private void startGrandFinale(ServerLevel level) {
        this.grandFinaleTicks = GRAND_FINALE_DURATION;
        this.grandFinaleCooldown = GRAND_FINALE_COOLDOWN;
        this.finaleLaunchDelay = 0;
        ++this.grandFinaleCount;
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(Component.literal("GRAND FINALE!"));
        }
        fireworkBurst(level, this.position(), 40, true);
        this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.90F, 0.90F);
    }

    private void launchGrandFinaleFirework(ServerLevel level) {
        List<LivingEntity> threats = this.getFireworkThreats(level);
        if (threats.isEmpty()) return;
        LivingEntity target = threats.get(this.random.nextInt(threats.size()));
        Vec3 predicted = target.position()
                .add(target.getDeltaMovement().scale(5.0D))
                .add(0.0D, 0.25D, 0.0D);
        int delay = 8 + this.random.nextInt(8);
        this.finaleMarkers.add(new FinaleMarker(predicted, delay));
        ++this.grandFinaleLaunchCount;

        Vec3 start = this.position().add(0.0D, 0.65D, 0.0D);
        Vec3 apex = predicted.add(0.0D, 3.5D, 0.0D);
        Vec3 path = apex.subtract(start);
        for (int i = 1; i <= 8; ++i) {
            Vec3 point = start.add(path.scale(i / 8.0D));
            WolfVfx.sendParticles("firework_wolf", level,
                    i % 3 == 0 ? ROCKET_GOLD : i % 2 == 0 ? ROCKET_BLUE : ROCKET_RED,
                    point.x, point.y, point.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
        this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.45F, 1.25F);
    }

    private void tickFinaleMarkers(ServerLevel level) {
        for (int i = this.finaleMarkers.size() - 1; i >= 0; --i) {
            FinaleMarker marker = this.finaleMarkers.get(i);
            --marker.ticks;
            WolfVfx.sendParticles("firework_wolf", level,
                    ParticleTypes.FIREWORK,
                    marker.impact.x,
                    marker.impact.y + 2.0D + marker.ticks * 0.12D,
                    marker.impact.z,
                    1, 0.08D, 0.08D, 0.08D, 0.01D);
            if (marker.ticks > 0) continue;
            controlledBlast(level, marker.impact, 3.2D, 5.0F, 1.05D, true);
            ++this.grandFinaleDetonationCount;
            this.finaleMarkers.remove(i);
        }
    }

    private void controlledBlast(
            ServerLevel level,
            Vec3 center,
            double radius,
            float damage,
            double knockback,
            boolean disorient) {
        fireworkBurst(level, center, 24, true);
        DamageSource source = this.damageSources().source(DamageTypes.EXPLOSION, this);
        AABB area = new AABB(
                center.x - radius,
                center.y - 1.5D,
                center.z - radius,
                center.x + radius,
                center.y + 2.5D,
                center.z + radius);
        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::isFireworkThreat)) {
            double distance = Math.sqrt(victim.position().distanceToSqr(center));
            float scaled = damage * (float) Math.max(0.35D, 1.0D - distance / (radius * 1.35D));
            if (!victim.hurtServer(level, source, scaled)) continue;
            double dx = center.x - victim.getX();
            double dz = center.z - victim.getZ();
            victim.knockback(knockback, dx, dz);
            if (disorient) {
                applyAtLeast(victim, MobEffects.SLOWNESS, 20 * 2, 0);
                applyAtLeast(victim, MobEffects.WEAKNESS, 20 * 2, 0);
            }
        }
        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.75F, 0.95F + this.random.nextFloat() * 0.35F);
    }

    private void pulseCelebrationField(ServerLevel level) {
        ++this.celebrationPulseCount;
        for (LivingEntity member : level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(FAMILY_RADIUS),
                this::isFireworkFamily)) {
            applyAtLeast(member, MobEffects.SPEED, 30, 0);
            applyAtLeast(member, MobEffects.STRENGTH, 30, 0);
        }
        drawCelebrationRing(level, 3.0D, 16);
    }

    @Override
    public boolean causeFallDamage(
            double fallDistance,
            float damageModifier,
            DamageSource damageSource) {
        if (this.rocketFallProtectionTicks > 0) {
            this.fallDistance = 0.0D;
            return false;
        }
        return super.causeFallDamage(fallDistance, damageModifier, damageSource);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (!hurt || this.isBaby() || !(target instanceof LivingEntity living)) return hurt;
        ++this.rocketBiteCount;
        controlledBlast(level, living.position(), 1.8D, 2.5F, 0.55D, false);
        return true;
    }

    private static void applyAtLeast(
            LivingEntity entity,
            Holder<MobEffect> effect,
            int duration,
            int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current != null
                && (current.getAmplifier() > amplifier
                || current.getAmplifier() == amplifier
                && current.getDuration() >= duration)) return;
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    private void drawCelebrationRing(ServerLevel level, double radius, int points) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("firework_wolf", level,
                    i % 4 == 0 ? ROCKET_GOLD : i % 3 == 0 ? ROCKET_WHITE : i % 2 == 0 ? ROCKET_BLUE : ROCKET_RED,
                    this.getX() + Math.cos(angle) * radius,
                    this.getY() + 0.25D,
                    this.getZ() + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static void fireworkBurst(
            ServerLevel level,
            Vec3 center,
            int count,
            boolean explosion) {
        WolfVfx.sendParticles("firework_wolf", level,
                ParticleTypes.FIREWORK,
                center.x, center.y + 0.55D, center.z,
                explosion ? count : Math.max(3, count / 3),
                0.75D, 0.55D, 0.75D, explosion ? 0.08D : 0.02D);
        WolfVfx.sendParticles("firework_wolf", level,
                ROCKET_RED,
                center.x, center.y + 0.60D, center.z,
                Math.max(4, count / 3), 0.70D, 0.50D, 0.70D, 0.0D);
        WolfVfx.sendParticles("firework_wolf", level,
                ROCKET_BLUE,
                center.x, center.y + 0.65D, center.z,
                Math.max(4, count / 3), 0.70D, 0.50D, 0.70D, 0.0D);
        WolfVfx.sendParticles("firework_wolf", level,
                ROCKET_GOLD,
                center.x, center.y + 0.72D, center.z,
                Math.max(3, count / 4), 0.55D, 0.45D, 0.55D, 0.0D);
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        if (finishing) {
            fireworkBurst(level, this.position(), 24, true);
            this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.65F, 1.35F);
        } else {
            WolfVfx.sendParticles("firework_wolf", level,
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY(0.45D), this.getZ(),
                    6, 0.40D, 0.25D, 0.40D, 0.01D);
            WolfVfx.sendParticles("firework_wolf", level,
                    ROCKET_GOLD,
                    this.getX(), this.getY(0.40D), this.getZ(),
                    2, 0.30D, 0.20D, 0.30D, 0.0D);
        }
    }

    public static boolean checkFireworkWolfSpawnRules(
            EntityType<FireworkWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        return level.getLevel().dimension() == Level.OVERWORLD
                && isFireworkSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.FIREWORK_WOLF_SPAWNS)
                && isSafeFireworkSurface(level, pos);
    }

    public static boolean isSafeFireworkSurface(ServerLevel level, BlockPos pos) {
        return isSafeFireworkSurface((ServerLevelAccessor) level, pos);
    }

    private static boolean isSafeFireworkSurface(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) return false;
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && floor.isFaceSturdy(level, floorPos, Direction.UP)
                && (floor.is(BlockTags.DIRT)
                || floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.STONE)
                || floor.is(Blocks.SAND));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("FireworkChargeCooldown", this.chargeCooldown);
        output.putInt("FireworkLeapCooldown", this.leapCooldown);
        output.putInt("FireworkBurstCooldown", this.firecrackerCooldown);
        output.putInt("FireworkGrandFinaleCooldown", this.grandFinaleCooldown);

        ValueOutput diagnostics = output.child("FireworkDiagnostics");
        diagnostics.putBoolean("FireworkActive", this.isFireworkVisualActive());
        diagnostics.putInt("ChargeCount", this.chargeCount);
        diagnostics.putInt("ChargeImpactCount", this.chargeImpactCount);
        diagnostics.putInt("RocketLeapCount", this.rocketLeapCount);
        diagnostics.putInt("RocketBiteCount", this.rocketBiteCount);
        diagnostics.putInt("SparkTrailHitCount", this.sparkTrailHitCount);
        diagnostics.putInt("FirecrackerBurstCount", this.firecrackerBurstCount);
        diagnostics.putInt("CelebrationPulseCount", this.celebrationPulseCount);
        diagnostics.putInt("GrandFinaleCount", this.grandFinaleCount);
        diagnostics.putInt("GrandFinaleLaunchCount", this.grandFinaleLaunchCount);
        diagnostics.putInt("GrandFinaleDetonationCount", this.grandFinaleDetonationCount);
        diagnostics.putInt("GrandFinaleTicks", this.grandFinaleTicks);
        diagnostics.putInt("PendingFinaleMarkers", this.finaleMarkers.size());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.chargeCooldown = Math.max(0, input.getIntOr("FireworkChargeCooldown", 0));
        this.leapCooldown = Math.max(0, input.getIntOr("FireworkLeapCooldown", 0));
        this.firecrackerCooldown = Math.max(0, input.getIntOr("FireworkBurstCooldown", 0));
        this.grandFinaleCooldown = Math.max(0, input.getIntOr("FireworkGrandFinaleCooldown", 0));
        this.rocketFallProtectionTicks = 0;
        this.chargeTicks = 0;
        this.grandFinaleTicks = 0;
        this.finaleMarkers.clear();
        this.entityData.set(DATA_FIREWORK_ACTIVE, false);
    }
}
