package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.time.LocalDate;
import java.util.ArrayList;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.EasterWolfSpringGoal;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tag.ModBiomeTags;

/**
 * Holiday #6: Easter Wolf.
 *
 * <p>The spring explorer combines spectacular jumps, gentle nature growth,
 * controlled Easter hunts and moderate family renewal. It is intentionally
 * more mobile and playful than Angel/New Year's dedicated support.</p>
 */
public final class EasterWolf extends AbstractWolfismHolidayWolf {
    public static final double FAMILY_SENSE_RADIUS = 16.0D;
    public static final double THREAT_RADIUS = 24.0D;

    private static final double BASE_HEALTH = 36.0D;
    private static final int RENEWAL_COOLDOWN = 20 * 24;
    private static final int RUSH_DURATION = 20 * 10;
    private static final int RUSH_COOLDOWN = 20 * 38;
    private static final int REVIVAL_DURATION = 20 * 14;
    private static final int REVIVAL_COOLDOWN = 20 * 70;
    private static final int CACHE_LIFETIME = 20 * 180;
    private static final int CACHE_COOLDOWN = 20 * 300;
    private static final int BUNNY_HOP_COOLDOWN = 14;

    private static final EntityDataAccessor<Boolean> DATA_SPRING_ACTIVE =
            SynchedEntityData.defineId(EasterWolf.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions SPRING_GREEN =
            new DustParticleOptions(0x67D293, 1.0F);
    private static final DustParticleOptions EGG_LAVENDER =
            new DustParticleOptions(0xC78DEC, 1.0F);
    private static final DustParticleOptions EGG_YELLOW =
            new DustParticleOptions(0xFFE96A, 1.05F);
    private static final DustParticleOptions EGG_PINK =
            new DustParticleOptions(0xF5A7D2, 1.0F);

    private int renewalCooldown;
    private int rushTicks;
    private int rushCooldown;
    private int revivalTicks;
    private int revivalCooldown;
    private int bunnyHopCooldown;
    private int cacheTicks;
    private int cacheCooldown;
    private int cacheSearchDelay;
    private int springHintDelay;
    private BlockPos easterCachePos;

    private int springSenseScans;
    private int bunnyHopCount;
    private int cacheCreatedCount;
    private int cacheClaimedCount;
    private int bloomingGrowthCount;
    private int renewalPulseCount;
    private int springRushCount;
    private int springRevivalCount;
    private int springRevivalPulseCount;
    private int springBiteCount;

    public record SpringScan(BlockPos position, int count) {
    }

    private static final class BrainHolder {
        private static final Brain.Provider<EasterWolf> PROVIDER =
                Brain.<EasterWolf>provider(
                        List.of(ModSensorTypes.EASTER_AWARENESS.get()),
                        wolf -> List.of());
    }

    public EasterWolf(EntityType<? extends EasterWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.EASTER_WOLF.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new EasterWolfSpringGoal(this));
    }

    @Override
    protected Brain<EasterWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<EasterWolf> getBrain() {
        return (Brain<EasterWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPRING_ACTIVE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 42.0D)
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
        LocalDate easter = easterSunday(date.getYear());
        return !date.isBefore(easter.minusDays(7))
                && !date.isAfter(easter.plusDays(7));
    }

    public static boolean isEasterSeasonOpen(ServerLevel level) {
        if (level.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get())) {
            return true;
        }
        LocalDate today = LocalDate.now();
        LocalDate easter = easterSunday(today.getYear());
        return !today.isBefore(easter.minusDays(7))
                && !today.isAfter(easter.plusDays(7));
    }

    /** Gregorian computus, valid for Minecraft's practical calendar range. */
    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()
                && !this.isAngry()
                && !this.isHolidayRecovering()
                && stack.is(Items.EGG)) {
            if (this.level() instanceof ServerLevel level) {
                stack.consume(1, player);
                if (this.random.nextFloat() < 0.75F
                        && !EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    this.setOrderedToSit(true);
                    level.broadcastEntityEvent(this, (byte) 7);
                    springBurst(level, this.position(), 24);
                    this.playSound(SoundEvents.CHICKEN_EGG, 0.55F, 1.55F);
                } else {
                    level.broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public boolean canProvideEasterSupport() {
        return this.isAlive()
                && !this.isRemoved()
                && !this.isBaby()
                && !this.isNoAi()
                && !this.isHolidayRecovering()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && !this.isWolfStaffRecallActive();
    }

    public boolean isSpringVisualActive() {
        return this.entityData.get(DATA_SPRING_ACTIVE);
    }

    public boolean isSpringRushActive() {
        return this.rushTicks > 0 && this.canProvideEasterSupport();
    }

    public boolean isSpringRevivalActive() {
        return this.revivalTicks > 0 && this.canProvideEasterSupport();
    }

    public boolean isEasterFamily(LivingEntity entity) {
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

    public boolean isEasterRecipient(LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && this.isEasterFamily(entity)
                && !(entity instanceof AbstractWolfismHolidayWolf holiday
                && holiday.isHolidayRecovering());
    }

    public List<LivingEntity> getEasterFamily(ServerLevel level, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        if (!this.isHolidayRecovering()) result.add(this);
        result.addAll(level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                member -> member != this
                        && this.isEasterRecipient(member)
                        && this.distanceToSqr(member) <= radius * radius));
        return result;
    }

    public boolean isEasterThreat(LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == this
                || entity.isSpectator()
                || this.isEasterFamily(entity)
                || this.isAlliedTo(entity)) return false;
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof Enemy
                || entity == this.getTarget()
                || entity instanceof Mob mob && this.isEasterFamily(mob.getTarget());
    }

    public List<LivingEntity> getEasterThreats(ServerLevel level) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(THREAT_RADIUS),
                threat -> this.isEasterThreat(threat)
                        && this.distanceToSqr(threat) <= THREAT_RADIUS * THREAT_RADIUS
                        && this.getSensing().hasLineOfSight(threat));
    }

    public double easterNeedScore(LivingEntity member) {
        double health = member.getHealth() / Math.max(1.0F, member.getMaxHealth());
        double score = (1.0D - health) * 100.0D;
        if (countHarmfulEffects(member) > 0) score += 25.0D;
        if (member.getTicksFrozen() > 0 || member.getRemainingFireTicks() > 0) score += 18.0D;
        return score;
    }

    public SpringScan scanSpringInterests(ServerLevel level) {
        ++this.springSenseScans;
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int count = 0;
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

        for (int dx = -12; dx <= 12; ++dx) {
            for (int dz = -12; dz <= 12; ++dz) {
                if (dx * dx + dz * dz > 12 * 12) continue;
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int dy = -4; dy <= 4; ++dy) {
                    scan.set(x, origin.getY() + dy, z);
                    if (!isSpringInterest(level, scan)) continue;
                    ++count;
                    double distance = scan.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = scan.immutable();
                    }
                }
            }
        }

        for (Animal animal : level.getEntitiesOfClass(
                Animal.class,
                this.getBoundingBox().inflate(12.0D),
                animal -> animal != this && animal.isBaby())) {
            ++count;
            double distance = this.distanceToSqr(animal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = animal.blockPosition();
            }
        }
        return new SpringScan(best, count);
    }

    public boolean isSpringInterest(ServerLevel level, BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.BEE_GROWABLES)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.MOSS_BLOCK);
    }

    public BlockPos getEasterCachePosition() {
        return this.cacheTicks > 0 ? this.easterCachePos : null;
    }

    public boolean isEasterDestinationValid(BlockPos destination) {
        if (!(this.level() instanceof ServerLevel level) || destination == null) {
            return false;
        }
        if (!level.hasChunk(destination.getX() >> 4, destination.getZ() >> 4)) {
            return false;
        }
        if (this.cacheTicks > 0 && destination.equals(this.easterCachePos)) {
            return true;
        }
        if (this.isSpringInterest(level, destination)) {
            return true;
        }

        /*
         * Spring Sense also understands baby animals. Their position is stored
         * as a BlockPos rather than a permanent entity lock, so validate that a
         * living baby animal is still near that remembered point. This prevents
         * the sensor from immediately erasing a legitimate baby-animal interest.
         */
        return !level.getEntitiesOfClass(
                Animal.class,
                new net.minecraft.world.phys.AABB(destination).inflate(1.75D),
                animal -> animal.isAlive() && animal.isBaby())
                .isEmpty();
    }

    public void tryBunnyHopToward(BlockPos destination) {
        if (!this.canProvideEasterSupport()
                || this.bunnyHopCooldown > 0
                || !this.onGround()
                || this.isInWater()
                || destination == null) return;
        Vec3 toward = Vec3.atCenterOf(destination).subtract(this.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() < 2.0D * 2.0D) return;
        horizontal = horizontal.normalize().scale(this.isSpringRushActive() ? 0.58D : 0.42D);
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(horizontal.x, Math.max(current.y, this.isSpringRushActive() ? 0.78D : 0.62D), horizontal.z);
        this.bunnyHopCooldown = BUNNY_HOP_COOLDOWN;
        ++this.bunnyHopCount;
        if (this.level() instanceof ServerLevel level) {
            WolfVfx.sendParticles("easter_wolf", level,
                    EGG_LAVENDER,
                    this.getX(), this.getY() + 0.15D, this.getZ(),
                    6, 0.30D, 0.05D, 0.30D, 0.0D);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;

        tickCooldowns();
        if (!this.canProvideEasterSupport()) {
            cancelEasterStates();
            return;
        }

        if (this.rushTicks > 0) --this.rushTicks;
        if (this.revivalTicks > 0) --this.revivalTicks;
        if (this.cacheTicks > 0 && --this.cacheTicks == 0) this.easterCachePos = null;
        if (this.cacheSearchDelay > 0) --this.cacheSearchDelay;
        if (this.springHintDelay > 0) --this.springHintDelay;

        this.entityData.set(
                DATA_SPRING_ACTIVE,
                this.rushTicks > 0 || this.revivalTicks > 0 || this.cacheTicks > 0);

        LivingEntity target = this.getTarget();
        if (target != null && this.isEasterThreat(target)) {
            this.tryBunnyHopToward(target.blockPosition());
        }

        if (this.isWolfismWorkTick(40)) {
            applyBloomingTrail(level);
        }
        if (this.isWolfismWorkTick(20)) {
            if (this.rushTicks > 0) pulseSpringRush();
            if (this.revivalTicks > 0) pulseSpringRevival(level);
            checkEasterCache(level);
        }
        if (this.springHintDelay <= 0) {
            this.springHintDelay = 40;
            emitSpringSenseHint(level);
        }

        if (!this.isWolfismWorkTick(10) || this.revivalTicks > 0) return;
        int hostiles = this.getBrain()
                .getMemory(ModMemoryModuleTypes.EASTER_HOSTILE_COUNT.get())
                .orElse(0);
        LivingEntity need = this.getBrain()
                .getMemory(ModMemoryModuleTypes.EASTER_FAMILY_IN_NEED.get())
                .orElse(null);
        int injured = (int) this.getEasterFamily(level, FAMILY_SENSE_RADIUS).stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.70F)
                .count();

        if (this.revivalCooldown == 0
                && (need != null && need.getHealth() <= need.getMaxHealth() * 0.35F
                || hostiles >= 4 && injured >= 2)) {
            startSpringRevival(level);
            return;
        }
        if (this.renewalCooldown == 0 && need != null) {
            startRenewalPulse(level, need);
        }
        if (this.rushCooldown == 0 && hostiles >= 2) {
            startSpringRush(level);
        }
    }

    private void tickCooldowns() {
        if (this.renewalCooldown > 0) --this.renewalCooldown;
        if (this.rushCooldown > 0) --this.rushCooldown;
        if (this.revivalCooldown > 0) --this.revivalCooldown;
        if (this.bunnyHopCooldown > 0) --this.bunnyHopCooldown;
        if (this.cacheCooldown > 0) --this.cacheCooldown;
    }

    private void cancelEasterStates() {
        this.rushTicks = 0;
        this.revivalTicks = 0;
        this.entityData.set(DATA_SPRING_ACTIVE, false);
    }

    private void startRenewalPulse(ServerLevel level, LivingEntity patient) {
        this.renewalCooldown = RENEWAL_COOLDOWN;
        ++this.renewalPulseCount;
        for (LivingEntity member : this.getEasterFamily(level, 12.0D)) {
            applyAtLeast(member, MobEffects.REGENERATION, 20 * 6, 0);
            member.setTicksFrozen(Math.max(0, member.getTicksFrozen() - 80));
            member.clearFire();
            removeOneHarmfulEffect(member);
        }
        springBurst(level, patient.position(), 20);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55F, 1.60F);
    }

    private void startSpringRush(ServerLevel level) {
        this.rushTicks = RUSH_DURATION;
        this.rushCooldown = RUSH_COOLDOWN;
        ++this.springRushCount;
        springBurst(level, this.position(), 24);
        this.playSound(SoundEvents.RABBIT_JUMP, 0.65F, 1.30F);
        pulseSpringRush();
    }

    private void pulseSpringRush() {
        applyAtLeast(this, MobEffects.SPEED, 30, 1);
        applyAtLeast(this, MobEffects.JUMP_BOOST, 30, 2);
        applyAtLeast(this, MobEffects.RESISTANCE, 30, 0);
    }

    private void startSpringRevival(ServerLevel level) {
        this.revivalTicks = REVIVAL_DURATION;
        this.revivalCooldown = REVIVAL_COOLDOWN;
        ++this.springRevivalCount;
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(Component.literal("SPRING REVIVAL"));
        }
        springBurst(level, this.position(), 40);
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.80F, 1.35F);
        pulseSpringRevival(level);
    }

    private void pulseSpringRevival(ServerLevel level) {
        ++this.springRevivalPulseCount;
        for (LivingEntity member : this.getEasterFamily(level, 15.0D)) {
            applyAtLeast(member, MobEffects.REGENERATION, 30, 0);
            applyAtLeast(member, MobEffects.RESISTANCE, 30, 0);
            applyAtLeast(member, MobEffects.SPEED, 30, 0);
            member.setTicksFrozen(Math.max(0, member.getTicksFrozen() - 60));
            member.clearFire();
            if (this.isWolfismWorkTick(40)) removeOneHarmfulEffect(member);
            if (member.getHealth() < member.getMaxHealth() * 0.65F) member.heal(0.5F);
        }
        if (this.isWolfismWorkTick(40)) growNearbyPlants(level, 2);
        drawSpringField(level, 4.0D, 20);
    }

    private void applyBloomingTrail(ServerLevel level) {
        Vec3 movement = this.getDeltaMovement();
        if (movement.x * movement.x + movement.z * movement.z < 0.005D) return;
        growNearbyPlants(level, 1);
    }

    private void growNearbyPlants(ServerLevel level, int maximum) {
        int grown = 0;
        BlockPos origin = this.blockPosition();
        outer:
        for (int dx = -4; dx <= 4; ++dx) {
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dz = -4; dz <= 4; ++dz) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.BEE_GROWABLES)
                            || !(state.getBlock() instanceof BonemealableBlock bonemealable)
                            || !bonemealable.isValidBonemealTarget(level, pos, state)) {
                        continue;
                    }
                    bonemealable.performBonemeal(level, this.random, pos, state);
                    ++grown;
                    ++this.bloomingGrowthCount;
                    WolfVfx.sendParticles("easter_wolf", level,
                            ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                            5, 0.20D, 0.25D, 0.20D, 0.02D);
                    if (grown >= maximum) break outer;
                }
            }
        }
    }

    private void checkEasterCache(ServerLevel level) {
        if (!this.isTame()) return;
        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) return;

        if (this.easterCachePos != null && this.cacheTicks > 0) {
            if (owner.distanceToSqr(Vec3.atCenterOf(this.easterCachePos)) <= 2.8D * 2.8D) {
                claimEasterCache(level, owner);
            }
            return;
        }

        if (this.cacheCooldown > 0
                || this.cacheSearchDelay > 0
                || this.getTarget() != null
                || this.distanceToSqr(owner) > 16.0D * 16.0D) {
            return;
        }
        this.cacheSearchDelay = 20 * 5;
        createEasterCache(level, owner);
    }

    private void createEasterCache(ServerLevel level, LivingEntity owner) {
        for (int attempt = 0; attempt < 32; ++attempt) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 12.0D + this.random.nextDouble() * 12.0D;
            int x = owner.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = owner.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
            BlockPos feet = new BlockPos(x, y, z);
            if (!isSafeEasterSurface(level, feet)) continue;
            this.easterCachePos = feet;
            this.cacheTicks = CACHE_LIFETIME;
            this.cacheCooldown = CACHE_COOLDOWN;
            ++this.cacheCreatedCount;
            springBurst(level, Vec3.atCenterOf(feet), 18);
            if (owner instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.literal("An Easter cache is nearby!"));
            }
            return;
        }
    }

    private void claimEasterCache(ServerLevel level, LivingEntity owner) {
        ItemStack reward = switch (this.random.nextInt(6)) {
            case 0 -> new ItemStack(Items.EGG, 3);
            case 1 -> new ItemStack(Items.CARROT, 2);
            case 2 -> new ItemStack(Items.COOKIE, 2);
            case 3 -> new ItemStack(Items.DANDELION, 1);
            case 4 -> new ItemStack(Items.PINK_PETALS, 2);
            default -> new ItemStack(Items.GOLD_NUGGET, 1);
        };
        owner.spawnAtLocation(level, reward);
        springBurst(level, Vec3.atCenterOf(this.easterCachePos), 28);
        this.playSound(SoundEvents.CHICKEN_EGG, 0.65F, 1.75F);
        ++this.cacheClaimedCount;
        this.easterCachePos = null;
        this.cacheTicks = 0;
    }

    private void emitSpringSenseHint(ServerLevel level) {
        BlockPos destination = this.getEasterCachePosition();
        if (destination == null) {
            destination = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.EASTER_NATURE_POS.get())
                    .orElse(null);
        }
        if (destination == null) return;
        Vec3 from = this.position().add(0.0D, 0.75D, 0.0D);
        Vec3 to = Vec3.atCenterOf(destination);
        Vec3 direction = to.subtract(from);
        int points = Math.max(4, Math.min(12, (int) Math.ceil(direction.length())));
        for (int i = 1; i < points; ++i) {
            Vec3 point = from.add(direction.scale(i / (double) points));
            WolfVfx.sendParticles("easter_wolf", level,
                    (i & 1) == 0 ? EGG_LAVENDER : SPRING_GREEN,
                    point.x, point.y, point.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (!hurt || this.isBaby() || !(target instanceof LivingEntity living)) return hurt;
        living.hurtServer(level, this.damageSources().mobAttack(this), 2.0F);
        applyAtLeast(living, MobEffects.SLOWNESS, 20 * 3, 0);
        double dx = this.getX() - living.getX();
        double dz = this.getZ() - living.getZ();
        living.knockback(0.65D, dx, dz);
        ++this.springBiteCount;
        springBurst(level, living.position(), 9);
        return true;
    }

    private static int countHarmfulEffects(LivingEntity entity) {
        return (int) entity.getActiveEffects().stream()
                .filter(effect -> !effect.getEffect().value().isBeneficial())
                .count();
    }

    private static boolean removeOneHarmfulEffect(LivingEntity entity) {
        for (MobEffectInstance instance : List.copyOf(entity.getActiveEffects())) {
            if (!instance.getEffect().value().isBeneficial()) {
                entity.removeEffect(instance.getEffect());
                return true;
            }
        }
        return false;
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

    private void drawSpringField(ServerLevel level, double radius, int points) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            WolfVfx.sendParticles("easter_wolf", level,
                    i % 3 == 0 ? EGG_YELLOW : i % 2 == 0 ? SPRING_GREEN : EGG_PINK,
                    this.getX() + Math.cos(angle) * radius,
                    this.getY() + 0.20D,
                    this.getZ() + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static void springBurst(ServerLevel level, Vec3 center, int count) {
        WolfVfx.sendParticles("easter_wolf", level,
                SPRING_GREEN,
                center.x, center.y + 0.55D, center.z,
                count, 0.70D, 0.40D, 0.70D, 0.0D);
        WolfVfx.sendParticles("easter_wolf", level,
                EGG_LAVENDER,
                center.x, center.y + 0.65D, center.z,
                Math.max(4, count / 2), 0.60D, 0.45D, 0.60D, 0.0D);
        WolfVfx.sendParticles("easter_wolf", level,
                EGG_YELLOW,
                center.x, center.y + 0.75D, center.z,
                Math.max(3, count / 3), 0.50D, 0.35D, 0.50D, 0.0D);
    }

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        springBurst(level, this.position(), finishing ? 28 : 7);
        if (finishing) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.38D, 0.0D));
            this.playSound(SoundEvents.RABBIT_JUMP, 0.70F, 1.55F);
        }
    }

    public static boolean checkEasterWolfSpawnRules(
            EntityType<EasterWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        return level.getLevel().dimension() == Level.OVERWORLD
                && isEasterSeasonOpen(level.getLevel())
                && level.getBiome(pos).is(ModBiomeTags.EASTER_WOLF_SPAWNS)
                && isSafeEasterSurface(level, pos);
    }

    public static boolean isSafeEasterSurface(ServerLevel level, BlockPos pos) {
        return isSafeEasterSurface((ServerLevelAccessor) level, pos);
    }

    private static boolean isSafeEasterSurface(ServerLevelAccessor level, BlockPos pos) {
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
                || floor.is(Blocks.MOSS_BLOCK));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("EasterRenewalCooldown", this.renewalCooldown);
        output.putInt("EasterRushCooldown", this.rushCooldown);
        output.putInt("EasterRevivalCooldown", this.revivalCooldown);
        output.putInt("EasterCacheCooldown", this.cacheCooldown);
        output.putInt("EasterCacheTicks", this.cacheTicks);
        output.putBoolean("EasterCacheActive", this.easterCachePos != null && this.cacheTicks > 0);
        if (this.easterCachePos != null && this.cacheTicks > 0) {
            output.putInt("EasterCacheX", this.easterCachePos.getX());
            output.putInt("EasterCacheY", this.easterCachePos.getY());
            output.putInt("EasterCacheZ", this.easterCachePos.getZ());
        }

        ValueOutput diagnostics = output.child("EasterDiagnostics");
        diagnostics.putBoolean("SpringActive", this.isSpringVisualActive());
        diagnostics.putInt("SpringSenseScans", this.springSenseScans);
        diagnostics.putInt("BunnyHopCount", this.bunnyHopCount);
        diagnostics.putInt("CacheCreatedCount", this.cacheCreatedCount);
        diagnostics.putInt("CacheClaimedCount", this.cacheClaimedCount);
        diagnostics.putInt("BloomingGrowthCount", this.bloomingGrowthCount);
        diagnostics.putInt("RenewalPulseCount", this.renewalPulseCount);
        diagnostics.putInt("SpringRushCount", this.springRushCount);
        diagnostics.putInt("SpringRevivalCount", this.springRevivalCount);
        diagnostics.putInt("SpringRevivalPulseCount", this.springRevivalPulseCount);
        diagnostics.putInt("SpringBiteCount", this.springBiteCount);
        diagnostics.putInt("CacheTicks", this.cacheTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.renewalCooldown = Math.max(0, input.getIntOr("EasterRenewalCooldown", 0));
        this.rushCooldown = Math.max(0, input.getIntOr("EasterRushCooldown", 0));
        this.revivalCooldown = Math.max(0, input.getIntOr("EasterRevivalCooldown", 0));
        this.cacheCooldown = Math.max(0, input.getIntOr("EasterCacheCooldown", 0));
        this.cacheTicks = Math.max(0, input.getIntOr("EasterCacheTicks", 0));
        if (input.getBooleanOr("EasterCacheActive", false) && this.cacheTicks > 0) {
            this.easterCachePos = new BlockPos(
                    input.getIntOr("EasterCacheX", 0),
                    input.getIntOr("EasterCacheY", 0),
                    input.getIntOr("EasterCacheZ", 0));
        } else {
            this.easterCachePos = null;
        }
        this.rushTicks = 0;
        this.revivalTicks = 0;
        this.entityData.set(DATA_SPRING_ACTIVE, this.cacheTicks > 0);
    }
}
