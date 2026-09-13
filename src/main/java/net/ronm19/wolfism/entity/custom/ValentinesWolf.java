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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.ValentinesWolfSupportGoal;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #5: Valentine's Wolf.
 *
 * <p>Its power is the relationship between family members: it marks attackers,
 * links two relatives who need each other, softens incoming damage, and turns a
 * scattered pack into one coordinated defensive unit.</p>
 */
public final class ValentinesWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_SENSE_RADIUS = 18.0D;
    public static final double THREAT_RADIUS = 26.0D;
    public static final double HEARTBOUND_RADIUS = 14.0D;
    public static final double ULTIMATE_RADIUS = 17.0D;

    private static final double BASE_HEALTH = 42.0D;
    private static final int MARK_DURATION = 20 * 12;
    private static final int MARK_COOLDOWN = 20 * 20;
    private static final int CHARM_COOLDOWN = 20 * 30;
    private static final int BOND_DURATION = 20 * 10;
    private static final int BOND_COOLDOWN = 20 * 36;
    private static final int ULTIMATE_DURATION = 20 * 14;
    private static final int ULTIMATE_COOLDOWN = 20 * 70;
    private static final String NEXT_LOVING_HEAL = "WolfismValentinesNextLovingHeal";
    private static final String NEXT_HEART_HEAL = "WolfismValentinesNextHeartHeal";
    private static final String RECENT_FAMILY_ATTACK = "WolfismValentinesHurtFamilyUntil";
    private static final String NEXT_CRITICAL_SAVE = "WolfismValentinesNextCriticalSave";

    private static final EntityDataAccessor<Boolean> DATA_HEART_ACTIVE =
            SynchedEntityData.defineId(
                    ValentinesWolf.class,
                    EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions HEART_PINK =
            new DustParticleOptions(0xFF6E9C, 1.05F);
    private static final DustParticleOptions HEART_RED =
            new DustParticleOptions(0xC62D59, 1.08F);
    private static final DustParticleOptions HEART_GOLD =
            new DustParticleOptions(0xFFD477, 1.05F);

    private LivingEntity markedTarget;
    private LivingEntity bondFirst;
    private LivingEntity bondSecond;
    private int markTicks;
    private int markCooldown;
    private int charmCooldown;
    private int bondTicks;
    private int bondCooldown;
    private int ultimateTicks;
    private int ultimateCooldown;

    private int lovingPresencePulses;
    private int cupidMarkCount;
    private int heartCharmCount;
    private int bondCount;
    private int bondPulseCount;
    private int heartbreakBiteCount;
    private int ultimateCount;
    private int ultimatePulseCount;
    private int criticalSaveCount;

    private static final class BrainHolder {
        private static final Brain.Provider<ValentinesWolf> PROVIDER =
                Brain.<ValentinesWolf>provider(
                        List.of(ModSensorTypes.VALENTINES_AWARENESS.get()),
                        wolf -> List.of());
    }

    public ValentinesWolf(EntityType<? extends ValentinesWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.VALENTINES_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new ValentinesWolfSupportGoal(this));
    }

    @Override
    protected Brain<ValentinesWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ValentinesWolf> getBrain() {
        return (Brain<ValentinesWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEART_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.325D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 44.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
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
        return date.getMonthValue() == 2
                && date.getDayOfMonth() >= 7
                && date.getDayOfMonth() <= 21;
    }

    public static boolean isValentinesSeasonOpen(ServerLevel level) {
        if (level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return today.getMonthValue() == 2
                && today.getDayOfMonth() >= 7
                && today.getDayOfMonth() <= 21;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()
                && !this.isAngry()
                && !this.isHolidayRecovering()
                && (stack.is(Items.ROSE_BUSH)
                || stack.is(Items.RED_TULIP)
                || stack.is(Items.POPPY))) {
            if (this.level() instanceof ServerLevel level) {
                float chance = stack.is(Items.ROSE_BUSH) ? 0.90F : 0.65F;
                stack.consume(1, player);
                if (this.random.nextFloat() < chance
                        && !EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    this.setOrderedToSit(true);
                    level.broadcastEntityEvent(this, (byte) 7);
                    heartBurst(level, this.position(), 22);
                    this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.60F, 1.55F);
                } else {
                    level.broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public boolean canProvideValentinesSupport() {
        return this.isAlive()
                && !this.isRemoved()
                && !this.isBaby()
                && !this.isNoAi()
                && !this.isHolidayRecovering()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isHeartVisualActive() {
        return this.entityData.get(DATA_HEART_ACTIVE);
    }

    public boolean isHeartOfFamilyActive() {
        return this.ultimateTicks > 0 && this.canProvideValentinesSupport();
    }

    public LivingEntity getMarkedTargetForSensor() {
        return this.markTicks > 0 && this.isValentinesThreat(this.markedTarget)
                ? this.markedTarget
                : null;
    }

    public boolean isValentinesFamily(LivingEntity entity) {
        if (entity == this) return true;
        if (entity == null
                || !entity.isAlive()
                || entity.isRemoved()
                || entity.level() != this.level()
                || !this.isTame()) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;
        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            LivingEntity petOwner = pet.getOwner();
            return petOwner != null && petOwner.getUUID().equals(owner.getUUID());
        }
        return false;
    }

    public boolean isValentinesRecipient(LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && this.isValentinesFamily(entity)
                && !(entity instanceof AbstractWolfismHolidayWolf holiday
                && holiday.isHolidayRecovering());
    }

    public List<LivingEntity> getValentinesFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        if (!this.isHolidayRecovering()) family.add(this);
        family.addAll(level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                member -> member != this
                        && this.isValentinesRecipient(member)
                        && this.distanceToSqr(member) <= radius * radius));
        return family;
    }

    public boolean isValentinesThreat(LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == this
                || entity.isSpectator()
                || this.isValentinesFamily(entity)
                || this.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof Enemy
                || entity == this.getTarget()
                || entity instanceof Mob mob && this.isValentinesFamily(mob.getTarget());
    }

    public List<LivingEntity> getValentinesThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THREAT_RADIUS),
                entity -> this.isValentinesThreat(entity)
                        && this.distanceToSqr(entity) <= THREAT_RADIUS * THREAT_RADIUS
                        && this.getSensing().hasLineOfSight(entity));
    }

    public double valentineNeedScore(LivingEntity member) {
        double health = member.getHealth() / Math.max(1.0F, member.getMaxHealth());
        double score = (1.0D - health) * 100.0D;
        if (member.hurtTime > 0) score += 16.0D;
        if (member == this.getOwner()) score += 8.0D;
        if (member instanceof Mob mob && mob.getTarget() != null) score += 12.0D;
        return score;
    }

    public boolean hasRecentlyHurtFamily(LivingEntity attacker) {
        return attacker != null
                && attacker.getPersistentData().getLongOr(RECENT_FAMILY_ATTACK, 0L)
                > this.level().getGameTime();
    }

    public void recordFamilyAttacker(
            ServerLevel level,
            LivingEntity attacker,
            LivingEntity victim) {
        if (!this.canProvideValentinesSupport()
                || !this.isValentinesRecipient(victim)
                || !this.isValentinesThreat(attacker)) {
            return;
        }
        attacker.getPersistentData().putLong(
                RECENT_FAMILY_ATTACK,
                level.getGameTime() + 20L * 12L);
        if (this.markCooldown == 0 || this.markedTarget == attacker) {
            this.startCupidMark(level, attacker);
        }
    }

    public float protectionMultiplierFor(LivingEntity member) {
        if (!this.canProvideValentinesSupport()
                || !this.isValentinesRecipient(member)) {
            return 1.0F;
        }
        double max = this.isHeartOfFamilyActive()
                ? ULTIMATE_RADIUS
                : HEARTBOUND_RADIUS;
        if (this.distanceToSqr(member) > max * max) return 1.0F;
        if (this.isHeartOfFamilyActive()) return 0.72F;
        if (this.bondTicks > 0 && (member == this.bondFirst || member == this.bondSecond)) {
            return 0.82F;
        }
        return 0.90F;
    }

    public float applyCriticalFamilySave(
            ServerLevel level,
            LivingEntity member,
            DamageSource source,
            float amount) {
        if (!this.isHeartOfFamilyActive()
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || member.getPersistentData().getLongOr(NEXT_CRITICAL_SAVE, 0L)
                > level.getGameTime()) {
            return amount;
        }
        float effective = member.getHealth() + member.getAbsorptionAmount();
        if (effective - amount > 0.0F) return amount;
        member.getPersistentData().putLong(
                NEXT_CRITICAL_SAVE,
                level.getGameTime() + 20L * 28L);
        applyAtLeast(member, MobEffects.ABSORPTION, 20 * 6, 1);
        applyAtLeast(member, MobEffects.RESISTANCE, 20 * 6, 0);
        member.heal(Math.min(4.0F, Math.max(2.0F, member.getMaxHealth() * 0.08F)));
        ++this.criticalSaveCount;
        emitHeartLink(level, member);
        return Math.max(0.0F, effective - 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;

        tickCooldowns();
        if (!this.canProvideValentinesSupport()) {
            cancelValentinesStates();
            return;
        }

        if (this.markTicks > 0) --this.markTicks;
        if (this.bondTicks > 0) --this.bondTicks;
        if (this.ultimateTicks > 0) --this.ultimateTicks;

        if (this.markedTarget != null && !this.isValentinesThreat(this.markedTarget)) {
            this.markedTarget = null;
            this.markTicks = 0;
        }
        if (this.bondTicks > 0
                && (!this.isValentinesRecipient(this.bondFirst)
                || !this.isValentinesRecipient(this.bondSecond))) {
            this.bondTicks = 0;
            this.bondFirst = null;
            this.bondSecond = null;
        }

        this.entityData.set(
                DATA_HEART_ACTIVE,
                this.markTicks > 0 || this.bondTicks > 0 || this.ultimateTicks > 0);

        if (this.isWolfismWorkTick(20)) {
            applyLovingPresence(level);
            if (this.markTicks > 0 && this.markedTarget != null) {
                pulseCupidMark(level);
            }
            if (this.bondTicks > 0) pulseBondOfTwo(level);
            if (this.ultimateTicks > 0) pulseHeartOfFamily(level);
        }

        if (!this.isWolfismWorkTick(10) || this.ultimateTicks > 0) return;

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VALENTINES_HOSTILE_COUNT.get())
                .orElse(0);
        int injuredCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VALENTINES_INJURED_FAMILY_COUNT.get())
                .orElse(0);
        LivingEntity need = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VALENTINES_FAMILY_IN_NEED.get())
                .orElse(null);
        LivingEntity priority = this.getBrain()
                .getMemory(ModMemoryModuleTypes.VALENTINES_PRIORITY_THREAT.get())
                .orElse(null);

        if (this.ultimateCooldown == 0
                && ((need != null
                && need.getHealth() <= need.getMaxHealth() * 0.35F)
                || (hostileCount >= 4 && injuredCount >= 2))) {
            startHeartOfFamily(level);
            return;
        }
        if (priority != null && this.markCooldown == 0) {
            startCupidMark(level, priority);
        }
        if (this.bondCooldown == 0 && injuredCount >= 2) {
            startBondOfTwo(level);
        }
        if (this.charmCooldown == 0 && hostileCount >= 3) {
            startHeartCharm(level);
        }
    }

    private void tickCooldowns() {
        if (this.markCooldown > 0) --this.markCooldown;
        if (this.charmCooldown > 0) --this.charmCooldown;
        if (this.bondCooldown > 0) --this.bondCooldown;
        if (this.ultimateCooldown > 0) --this.ultimateCooldown;
    }

    private void cancelValentinesStates() {
        this.markTicks = 0;
        this.bondTicks = 0;
        this.ultimateTicks = 0;
        this.markedTarget = null;
        this.bondFirst = null;
        this.bondSecond = null;
        this.entityData.set(DATA_HEART_ACTIVE, false);
    }

    private void applyLovingPresence(ServerLevel level) {
        long now = level.getGameTime();
        for (LivingEntity member : this.getValentinesFamily(level, HEARTBOUND_RADIUS)) {
            if (member.getHealth() >= member.getMaxHealth()
                    || hasLiveCombatTarget(member)
                    || member.hurtTime > 0
                    || member.getPersistentData().getLongOr(NEXT_LOVING_HEAL, 0L) > now) {
                continue;
            }
            member.heal(0.5F);
            member.getPersistentData().putLong(NEXT_LOVING_HEAL, now + 20L * 5L);
            ++this.lovingPresencePulses;
            WolfVfx.sendParticles("valentines_wolf", level,
                    ParticleTypes.HEART,
                    member.getX(), member.getY(0.65D), member.getZ(),
                    2, 0.20D, 0.18D, 0.20D, 0.0D);
        }
    }

    private void startCupidMark(ServerLevel level, LivingEntity target) {
        if (!this.isValentinesThreat(target)) return;
        this.markedTarget = target;
        this.markTicks = MARK_DURATION;
        this.markCooldown = MARK_COOLDOWN;
        ++this.cupidMarkCount;
        applyAtLeast(target, MobEffects.GLOWING, MARK_DURATION, 0);
        applyAtLeast(target, MobEffects.WEAKNESS, MARK_DURATION, 0);
        heartBurst(level, target.position(), 18);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.60F, 1.65F);
    }

    private void pulseCupidMark(ServerLevel level) {
        if (!this.isValentinesThreat(this.markedTarget)) return;
        applyAtLeast(this.markedTarget, MobEffects.GLOWING, 30, 0);
        applyAtLeast(this.markedTarget, MobEffects.WEAKNESS, 30, 0);
        for (LivingEntity member : this.getValentinesFamily(level, ULTIMATE_RADIUS)) {
            if (member instanceof AbstractWolfismWolf wolf
                    && !wolf.hasWolfStaffCommand()
                    && !wolf.isOrderedToSit()
                    && !wolf.isInSittingPose()
                    && (wolf.getTarget() == null || !wolf.getTarget().isAlive())) {
                wolf.setTarget(this.markedTarget);
            }
        }
        WolfVfx.sendParticles("valentines_wolf", level,
                HEART_RED,
                this.markedTarget.getX(),
                this.markedTarget.getY(0.80D),
                this.markedTarget.getZ(),
                3, 0.28D, 0.32D, 0.28D, 0.0D);
    }

    private void startHeartCharm(ServerLevel level) {
        this.charmCooldown = CHARM_COOLDOWN;
        ++this.heartCharmCount;
        for (LivingEntity threat : this.getValentinesThreats(level)) {
            boolean boss = isBossLike(threat);
            applyAtLeast(threat, MobEffects.WEAKNESS, boss ? 20 * 3 : 20 * 7, 0);
            applyAtLeast(threat, MobEffects.SLOWNESS, boss ? 20 * 2 : 20 * 5, 0);
            if (!boss && threat instanceof Mob mob && threat.getMaxHealth() < 60.0F) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
        heartBurst(level, this.position(), 30);
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.65F, 1.35F);
    }

    private void startBondOfTwo(ServerLevel level) {
        List<LivingEntity> candidates = this.getValentinesFamily(level, FAMILY_SENSE_RADIUS)
                .stream()
                .filter(member -> member != this)
                .sorted(Comparator.comparingDouble(this::valentineNeedScore).reversed())
                .toList();
        if (candidates.isEmpty()) return;
        this.bondFirst = candidates.get(0);
        this.bondSecond = candidates.size() >= 2 ? candidates.get(1) : this;
        this.bondTicks = BOND_DURATION;
        this.bondCooldown = BOND_COOLDOWN;
        ++this.bondCount;
        emitHeartLink(level, this.bondFirst);
        emitHeartLink(level, this.bondSecond);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.65F, 1.45F);
    }

    private void pulseBondOfTwo(ServerLevel level) {
        if (!this.isValentinesRecipient(this.bondFirst)
                || !this.isValentinesRecipient(this.bondSecond)) {
            this.bondTicks = 0;
            return;
        }
        ++this.bondPulseCount;
        for (LivingEntity member : List.of(this.bondFirst, this.bondSecond)) {
            applyAtLeast(member, MobEffects.RESISTANCE, 30, 0);
            applyAtLeast(member, MobEffects.SPEED, 30, 0);
            if (member.getHealth() < member.getMaxHealth() * 0.60F) {
                refreshHeartRegeneration(member);
            }
        }
        regroupBondMember(this.bondFirst, this.bondSecond);
        regroupBondMember(this.bondSecond, this.bondFirst);
        drawHeartLink(level, this.bondFirst, this.bondSecond);
    }

    private void regroupBondMember(LivingEntity member, LivingEntity partner) {
        if (member instanceof AbstractWolfismWolf wolf
                && !wolf.hasWolfStaffCommand()
                && wolf.getTarget() == null
                && !wolf.isOrderedToSit()
                && member.distanceToSqr(partner) > 8.0D * 8.0D) {
            wolf.getNavigation().moveTo(partner, 1.05D);
        }
    }

    private void startHeartOfFamily(ServerLevel level) {
        this.ultimateTicks = ULTIMATE_DURATION;
        this.ultimateCooldown = ULTIMATE_COOLDOWN;
        ++this.ultimateCount;
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(Component.literal("HEART OF FAMILY"));
        }
        heartBurst(level, this.position(), 42);
        this.playSound(SoundEvents.BELL_BLOCK, 0.90F, 1.35F);
        pulseHeartOfFamily(level);
    }

    private void pulseHeartOfFamily(ServerLevel level) {
        ++this.ultimatePulseCount;
        List<LivingEntity> family = this.getValentinesFamily(level, ULTIMATE_RADIUS);
        for (LivingEntity member : family) {
            applyAtLeast(member, MobEffects.RESISTANCE, 30, 0);
            refreshHeartRegeneration(member);
            applyAtLeast(member, MobEffects.SPEED, 30, 0);
            long now = level.getGameTime();
            if (member.getHealth() < member.getMaxHealth() * 0.50F
                    && member.getPersistentData().getLongOr(NEXT_HEART_HEAL, 0L) <= now) {
                member.getPersistentData().putLong(NEXT_HEART_HEAL, now + 20L);
                member.heal(0.5F);
            }
            if (this.markedTarget != null
                    && member instanceof AbstractWolfismWolf wolf
                    && !wolf.hasWolfStaffCommand()
                    && !wolf.isOrderedToSit()) {
                wolf.setTarget(this.markedTarget);
            }
        }
        drawHeartRing(level, this.position(), 3.5D, 18);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (!hurt || this.isBaby() || !(target instanceof LivingEntity living)) {
            return hurt;
        }
        if (living == this.markedTarget || this.hasRecentlyHurtFamily(living)) {
            living.hurtServer(level, this.damageSources().mobAttack(this), 3.5F);
            applyAtLeast(living, MobEffects.WEAKNESS, 20 * 3, 0);
            ++this.heartbreakBiteCount;
            heartBurst(level, living.position(), 10);
        }
        return true;
    }

    private void emitHeartLink(ServerLevel level, LivingEntity member) {
        drawHeartLink(level, this, member);
        WolfVfx.sendParticles("valentines_wolf", level,
                ParticleTypes.HEART,
                member.getX(), member.getY(0.75D), member.getZ(),
                8, 0.35D, 0.30D, 0.35D, 0.0D);
    }

    private static void drawHeartLink(
            ServerLevel level,
            LivingEntity first,
            LivingEntity second) {
        Vec3 start = first.position().add(0.0D, first.getBbHeight() * 0.65D, 0.0D);
        Vec3 end = second.position().add(0.0D, second.getBbHeight() * 0.65D, 0.0D);
        Vec3 difference = end.subtract(start);
        int points = Math.max(4, Math.min(18, (int) Math.ceil(difference.length() * 1.5D)));
        for (int i = 1; i < points; ++i) {
            Vec3 point = start.add(difference.scale(i / (double) points));
            WolfVfx.sendParticles("valentines_wolf", level,
                    (i & 1) == 0 ? HEART_PINK : HEART_GOLD,
                    point.x, point.y, point.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static void drawHeartRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("valentines_wolf", level,
                    (i & 1) == 0 ? HEART_RED : HEART_PINK,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.35D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static boolean hasLiveCombatTarget(LivingEntity member) {
        return member instanceof Mob mob
                && mob.getTarget() != null
                && mob.getTarget().isAlive();
    }

    private static boolean isBossLike(LivingEntity entity) {
        return entity instanceof WitherBoss
                || entity instanceof EnderDragon
                || entity instanceof Warden;
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
                && current.getDuration() >= duration)) {
            return;
        }
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    private static void refreshHeartRegeneration(LivingEntity member) {
        MobEffectInstance current = member.getEffect(MobEffects.REGENERATION);
        // Regeneration I heals at 50-tick boundaries. Repeated 30-tick
        // applications never reached one; let this longer effect count down.
        if (current == null || current.getAmplifier() == 0
                && !current.isInfiniteDuration() && current.getDuration() <= 20) {
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        }
    }

    private static void heartBurst(ServerLevel level, Vec3 center, int count) {
        WolfVfx.sendParticles("valentines_wolf", level,
                ParticleTypes.HEART,
                center.x, center.y + 0.65D, center.z,
                Math.max(3, count / 3), 0.65D, 0.45D, 0.65D, 0.02D);
        WolfVfx.sendParticles("valentines_wolf", level,
                HEART_PINK,
                center.x, center.y + 0.55D, center.z,
                count, 0.75D, 0.45D, 0.75D, 0.0D);
        WolfVfx.sendParticles("valentines_wolf", level,
                HEART_GOLD,
                center.x, center.y + 0.60D, center.z,
                Math.max(3, count / 3), 0.55D, 0.35D, 0.55D, 0.0D);
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        heartBurst(level, this.position(), finishing ? 26 : 7);
        if (finishing) {
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.70F, 1.60F);
        }
    }

    public static boolean checkValentinesWolfSpawnRules(
            EntityType<ValentinesWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        return level.getLevel().dimension() == Level.OVERWORLD
                && isValentinesSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.VALENTINES_WOLF_SPAWNS)
                && isSafeValentinesSurface(level, pos);
    }

    public static boolean isSafeValentinesSurface(ServerLevel level, BlockPos pos) {
        return isSafeValentinesSurface((ServerLevelAccessor) level, pos);
    }

    private static boolean isSafeValentinesSurface(
            ServerLevelAccessor level,
            BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && floor.isFaceSturdy(level, floorPos, Direction.UP)
                && (floor.is(BlockTags.DIRT)
                || floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.MOSS_BLOCK));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ValentinesMarkTicks", this.markTicks);
        output.putInt("ValentinesMarkCooldown", this.markCooldown);
        output.putInt("ValentinesCharmCooldown", this.charmCooldown);
        output.putInt("ValentinesBondTicks", this.bondTicks);
        output.putInt("ValentinesBondCooldown", this.bondCooldown);
        output.putInt("ValentinesUltimateTicks", this.ultimateTicks);
        output.putInt("ValentinesUltimateCooldown", this.ultimateCooldown);

        ValueOutput diagnostics = output.child("ValentinesDiagnostics");
        diagnostics.putInt("LovingPresencePulses", this.lovingPresencePulses);
        diagnostics.putInt("CupidMarkCount", this.cupidMarkCount);
        diagnostics.putInt("HeartCharmCount", this.heartCharmCount);
        diagnostics.putInt("BondCount", this.bondCount);
        diagnostics.putInt("BondPulseCount", this.bondPulseCount);
        diagnostics.putInt("HeartbreakBiteCount", this.heartbreakBiteCount);
        diagnostics.putInt("HeartOfFamilyCount", this.ultimateCount);
        diagnostics.putInt("HeartOfFamilyPulseCount", this.ultimatePulseCount);
        diagnostics.putInt("CriticalSaveCount", this.criticalSaveCount);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.markTicks = input.getIntOr("ValentinesMarkTicks", 0);
        this.markCooldown = input.getIntOr("ValentinesMarkCooldown", 0);
        this.charmCooldown = input.getIntOr("ValentinesCharmCooldown", 0);
        this.bondTicks = input.getIntOr("ValentinesBondTicks", 0);
        this.bondCooldown = input.getIntOr("ValentinesBondCooldown", 0);
        this.ultimateTicks = input.getIntOr("ValentinesUltimateTicks", 0);
        this.ultimateCooldown = input.getIntOr("ValentinesUltimateCooldown", 0);
        this.markedTarget = null;
        this.bondFirst = null;
        this.bondSecond = null;
        this.entityData.set(
                DATA_HEART_ACTIVE,
                this.bondTicks > 0 || this.ultimateTicks > 0);
    }
}
