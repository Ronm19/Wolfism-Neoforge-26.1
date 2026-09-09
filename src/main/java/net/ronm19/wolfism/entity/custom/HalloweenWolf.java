package net.ronm19.wolfism.entity.custom;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModGameRules;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Halloween Wolf — Fear / Trickery / Spirits / Crowd Control.
 *
 * <p>Its power does not change between day and night. Darkness changes only its
 * presentation/awareness: jack-o'-lantern eyes activate and the wolf carries a
 * personal level-10 light source. Combat awareness also activates that state.</p>
 */
public final class HalloweenWolf extends AbstractWolfismHolidayWolf {

    private static final EntityDataAccessor<Boolean> DATA_HALLOWEEN_ILLUMINATED =
            SynchedEntityData.defineId(HalloweenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAUNTED_GROUND =
            SynchedEntityData.defineId(HalloweenWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NIGHT_OF_FRIGHT =
            SynchedEntityData.defineId(HalloweenWolf.class, EntityDataSerializers.BOOLEAN);

    private static final double BASE_MAX_HEALTH = 36.0D;
    private static final double BASE_ATTACK_DAMAGE = 7.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.33D;
    private static final double BASE_ARMOR = 3.0D;
    private static final double BASE_FOLLOW_RANGE = 44.0D;

    public static final double FRIGHT_SENSE_DARK_RADIUS = 30.0D;
    public static final double FRIGHT_SENSE_DAY_RADIUS = 20.0D;

    private static final int PERSONAL_LIGHT_LEVEL = 10;
    private static final int LIGHT_UPDATE_INTERVAL = 4;

    /*
     * Daytime natural spawning still uses vanilla's random creature spawn candidates.
     * A 10-block pumpkin check made the effective spawn area tiny enough that a wolf
     * could practically never pass the daytime rule. Treat pumpkins as a wider
     * attraction/haunting anchor instead: the wolf still needs a pumpkin patch in
     * the local area, but vanilla has enough valid candidate positions to find one.
     */
    private static final int DAYTIME_PUMPKIN_RADIUS = 32;
    private static final int DAYTIME_PUMPKIN_VERTICAL_RADIUS = 6;

    private static final int HOWL_COOLDOWN = 20 * 18;
    private static final int TRICK_OR_TREAT_COOLDOWN = 20 * 24;
    private static final int HAUNTED_GROUND_DURATION = 20 * 11;
    private static final int HAUNTED_GROUND_COOLDOWN = 20 * 38;
    private static final int NIGHT_OF_FRIGHT_DURATION = 20 * 14;
    private static final int NIGHT_OF_FRIGHT_COOLDOWN = 20 * 56;

    private int howlCooldownTicks;
    private int trickOrTreatCooldownTicks;
    private int hauntedGroundTicks;
    private int hauntedGroundCooldownTicks;
    private int nightOfFrightTicks;
    private int nightOfFrightCooldownTicks;
    private int pumpkinGuardPulseTicks;
    private int lightUpdateTicks;

    private BlockPos placedPersonalLight;

    private static final class BrainHolder {
        private static final Brain.Provider<HalloweenWolf> PROVIDER =
                Brain.<HalloweenWolf>provider(
                        List.of(ModSensorTypes.HALLOWEEN_AWARENESS.get()),
                        wolf -> List.of());
    }

    public HalloweenWolf(EntityType<? extends HalloweenWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.HALLOWEEN_WOLF.get();
    }

    @Override
    protected Brain<HalloweenWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<HalloweenWolf> getBrain() {
        return (Brain<HalloweenWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HALLOWEEN_ILLUMINATED, false);
        builder.define(DATA_HAUNTED_GROUND, false);
        builder.define(DATA_NIGHT_OF_FRIGHT, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ARMOR, BASE_ARMOR)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        float oldHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);
        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(oldHealth, this.getMaxHealth()));
        }
    }

    // ---------------------------------------------------------------------
    // Holiday / taming
    // ---------------------------------------------------------------------

    /** Halloween's natural season is the full month of October. */
    @Override
    protected boolean isHolidaySeasonActive(LocalDate date) {
        return date.getMonthValue() == 10;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame()
                && !this.isBaby()
                && !this.isAngry()
                && stack.is(Items.JACK_O_LANTERN)) {
            if (!this.level().isClientSide()) {
                stack.consume(1, player);
                if (!EventHooks.onAnimalTame(this, player)) {
                    this.tame(player);
                    this.getNavigation().stop();
                    this.setTarget(null);
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);

                    if (this.level() instanceof ServerLevel level) {
                        level.sendParticles(
                                ParticleTypes.FLAME,
                                this.getX(), this.getY(0.65D), this.getZ(),
                                24, 0.6D, 0.45D, 0.6D, 0.02D);
                        level.sendParticles(
                                ParticleTypes.WITCH,
                                this.getX(), this.getY(0.65D), this.getZ(),
                                18, 0.65D, 0.50D, 0.65D, 0.02D);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Bones remain the universal Holiday Wolf fallback via vanilla Wolf.
        return super.mobInteract(player, hand);
    }

    // ---------------------------------------------------------------------
    // Main decision loop
    // ---------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        tickCooldowns();
        tickHalloweenLighting(level);

        if (this.isHolidayRecovering() || this.isBaby()) {
            if (this.isBaby()) {
                cancelActiveHalloweenStates();
            }
            return;
        }

        tickPumpkinGuard(level);

        if (!this.canUseActiveWolfismAbility()) {
            cancelActiveHalloweenStates();
            return;
        }

        if (nightOfFrightTicks > 0) {
            tickNightOfFright(level);
            return;
        }

        if (hauntedGroundTicks > 0) {
            tickHauntedGround(level);
        }

        int hostileCount = this.getBrain()
                .getMemory(ModMemoryModuleTypes.HALLOWEEN_HOSTILE_COUNT.get())
                .orElse(0);

        if (nightOfFrightCooldownTicks <= 0
                && hostileCount > 0
                && (hostileCount >= 5 || this.getHealth() <= this.getMaxHealth() * 0.38F)) {
            startNightOfFright(level);
            return;
        }

        if (hauntedGroundTicks <= 0
                && hauntedGroundCooldownTicks <= 0
                && hostileCount >= 3) {
            startHauntedGround(level);
        }

        if (howlCooldownTicks <= 0 && hostileCount >= 2) {
            useHauntingHowl(level);
        }

        if (trickOrTreatCooldownTicks <= 0) {
            useTrickOrTreat(level);
        }
    }

    private void tickCooldowns() {
        if (howlCooldownTicks > 0) --howlCooldownTicks;
        if (trickOrTreatCooldownTicks > 0) --trickOrTreatCooldownTicks;
        if (hauntedGroundCooldownTicks > 0) --hauntedGroundCooldownTicks;
        if (nightOfFrightCooldownTicks > 0) --nightOfFrightCooldownTicks;
        if (hauntedGroundTicks > 0) {
            --hauntedGroundTicks;
            if (hauntedGroundTicks == 0) {
                this.entityData.set(DATA_HAUNTED_GROUND, false);
            }
        }
        if (nightOfFrightTicks > 0) {
            --nightOfFrightTicks;
            if (nightOfFrightTicks == 0) {
                this.entityData.set(DATA_NIGHT_OF_FRIGHT, false);
            }
        }
        if (pumpkinGuardPulseTicks > 0) --pumpkinGuardPulseTicks;
    }

    private void cancelActiveHalloweenStates() {
        hauntedGroundTicks = 0;
        nightOfFrightTicks = 0;
        this.entityData.set(DATA_HAUNTED_GROUND, false);
        this.entityData.set(DATA_NIGHT_OF_FRIGHT, false);
    }

    // ---------------------------------------------------------------------
    // Fright Sense + presentation
    // ---------------------------------------------------------------------

    public boolean isHalloweenDarkEnvironment(ServerLevel level) {
        return !level.isBrightOutside()
                || level.getMaxLocalRawBrightness(this.blockPosition()) <= 8;
    }

    public boolean isHalloweenIlluminated() {
        return this.entityData.get(DATA_HALLOWEEN_ILLUMINATED);
    }

    public boolean isHalloweenCombatVisualActive() {
        return this.entityData.get(DATA_HAUNTED_GROUND)
                || this.entityData.get(DATA_NIGHT_OF_FRIGHT)
                || this.getTarget() != null;
    }

    private void tickHalloweenLighting(ServerLevel level) {
        boolean sensedThreat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.HALLOWEEN_PRIORITY_THREAT.get())
                .filter(LivingEntity::isAlive)
                .isPresent();

        boolean active = this.isHalloweenDarkEnvironment(level)
                || sensedThreat
                || this.getTarget() != null
                || hauntedGroundTicks > 0
                || nightOfFrightTicks > 0;

        this.entityData.set(DATA_HALLOWEEN_ILLUMINATED, active);

        if (lightUpdateTicks > 0) {
            --lightUpdateTicks;
            return;
        }
        lightUpdateTicks = LIGHT_UPDATE_INTERVAL;

        if (!active) {
            clearPersonalLight(level);
            return;
        }

        BlockPos desired = findPersonalLightPosition(level);
        if (desired == null) {
            clearPersonalLight(level);
            return;
        }

        if (placedPersonalLight != null && !placedPersonalLight.equals(desired)) {
            clearPersonalLight(level);
        }

        if (placedPersonalLight != null && placedPersonalLight.equals(desired)) {
            return;
        }

        BlockState current = level.getBlockState(desired);
        if (current.isAir()) {
            BlockState lightState = Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, PERSONAL_LIGHT_LEVEL);
            level.setBlock(desired, lightState, 3);
            placedPersonalLight = desired.immutable();
        }
    }

    private BlockPos findPersonalLightPosition(ServerLevel level) {
        BlockPos feet = this.blockPosition();
        if (placedPersonalLight != null && placedPersonalLight.equals(feet)) {
            return feet;
        }
        if (level.getBlockState(feet).isAir()) {
            return feet;
        }

        BlockPos head = feet.above();
        if (placedPersonalLight != null && placedPersonalLight.equals(head)) {
            return head;
        }
        if (level.getBlockState(head).isAir()) {
            return head;
        }
        return null;
    }

    private void clearPersonalLight(ServerLevel level) {
        if (placedPersonalLight == null) {
            return;
        }

        BlockPos old = placedPersonalLight;
        placedPersonalLight = null;

        if (!level.getBlockState(old).is(Blocks.LIGHT)) {
            return;
        }

        // This wolf removes only the light position it personally placed.
        // Another illuminated Halloween Wolf will replace its own light on its
        // next lighting update, which avoids permanent ghost Light blocks.
        level.removeBlock(old, false);
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            clearPersonalLight(level);
        }
        super.onRemoval(reason);
    }

    // ---------------------------------------------------------------------
    // Awareness helpers consumed by HalloweenWolfAwarenessSensor
    // ---------------------------------------------------------------------

    public List<LivingEntity> getNearbyHalloweenThreats(ServerLevel level, double radius) {
        LivingEntity owner = this.getOwner();
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                entity -> entity.isAlive()
                        && entity != this
                        && entity != owner
                        && entity instanceof Enemy
                        && !this.isAlliedTo(entity));
    }

    public List<LivingEntity> getNearbyHalloweenFamily(ServerLevel level, double radius) {
        List<LivingEntity> family = new ArrayList<>();
        LivingEntity owner = this.getOwner();
        if (owner != null
                && owner.isAlive()
                && owner.distanceToSqr(this) <= radius * radius) {
            family.add(owner);
        }

        if (!this.isTame() || this.getOwner() == null) {
            return family;
        }

        for (AbstractWolfismWolf wolf : level.getEntitiesOfClass(
                AbstractWolfismWolf.class,
                this.getBoundingBox().inflate(radius),
                wolf -> wolf != this
                        && wolf.isAlive()
                        && wolf.isTame()
                        && wolf.getOwner() != null
                        && wolf.getOwner().getUUID().equals(this.getOwner().getUUID()))) {
            family.add(wolf);
        }
        return family;
    }

    public double halloweenThreatScore(LivingEntity entity) {
        double score = this.distanceToSqr(entity);
        if (entity instanceof Mob mob && mob.getTarget() == this.getOwner()) score -= 120.0D;
        if (entity instanceof WitherBoss || entity instanceof Warden) score -= 80.0D;
        if (entity.getHealth() > 40.0F) score -= 25.0D;
        return score;
    }

    // ---------------------------------------------------------------------
    // Haunting Howl / Fright
    // ---------------------------------------------------------------------

    private void useHauntingHowl(ServerLevel level) {
        howlCooldownTicks = HOWL_COOLDOWN;
        this.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.75F, 1.35F);

        level.sendParticles(
                ParticleTypes.WITCH,
                this.getX(), this.getY(0.7D), this.getZ(),
                34, 1.2D, 0.65D, 1.2D, 0.03D);
        level.sendParticles(
                ParticleTypes.SMOKE,
                this.getX(), this.getY(0.55D), this.getZ(),
                28, 1.0D, 0.45D, 1.0D, 0.025D);

        for (LivingEntity enemy : getNearbyHalloweenThreats(level, 13.0D)) {
            applyFright(enemy, 20 * 5, false);
        }
    }

    private void applyFright(LivingEntity enemy, int duration, boolean ultimate) {
        if (enemy == null || !enemy.isAlive() || this.isAlliedTo(enemy)) {
            return;
        }

        boolean boss = isFearBoss(enemy);
        boolean elite = boss || enemy.getMaxHealth() >= 80.0F;

        int actualDuration = boss
                ? Math.max(20, duration / 4)
                : elite
                ? Math.max(30, duration / 2)
                : duration;

        enemy.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS,
                actualDuration,
                0,
                false,
                true));
        enemy.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                actualDuration,
                ultimate && !boss ? 1 : 0,
                false,
                true));
        enemy.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                actualDuration,
                ultimate && !boss ? 1 : 0,
                false,
                true));

        // Bosses resist outright fear. Ordinary/elite mobs may lose their target
        // and are ordered away from Halloween for a short psychological break.
        if (!boss && enemy instanceof Mob mob) {
            mob.setTarget(null);
            Vec3 away = enemy.position().subtract(this.position());
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            away = away.normalize().scale(ultimate ? 9.0D : 6.0D);
            mob.getNavigation().moveTo(
                    mob.getX() + away.x,
                    mob.getY(),
                    mob.getZ() + away.z,
                    ultimate ? 1.35D : 1.20D);
        }
    }

    private static boolean isFearBoss(LivingEntity entity) {
        return entity instanceof WitherBoss
                || entity instanceof EnderDragon
                || entity instanceof Warden;
    }

    // ---------------------------------------------------------------------
    // Trick or Treat
    // ---------------------------------------------------------------------

    private void useTrickOrTreat(ServerLevel level) {
        LivingEntity vulnerable = this.getBrain()
                .getMemory(ModMemoryModuleTypes.HALLOWEEN_VULNERABLE_FAMILY.get())
                .filter(LivingEntity::isAlive)
                .orElse(null);

        LivingEntity threat = this.getBrain()
                .getMemory(ModMemoryModuleTypes.HALLOWEEN_PRIORITY_THREAT.get())
                .filter(LivingEntity::isAlive)
                .orElse(null);

        if (vulnerable == null && threat == null) {
            return;
        }

        trickOrTreatCooldownTicks = TRICK_OR_TREAT_COOLDOWN;

        // Contextual choice: family in trouble strongly biases Treat; otherwise
        // Halloween is equally happy to play a Trick on the enemy.
        boolean treat = vulnerable != null && (threat == null || this.random.nextFloat() < 0.72F);
        if (treat) {
            useTreat(level, vulnerable);
        } else if (threat != null) {
            useTrick(level, threat);
        }
    }

    private void useTreat(ServerLevel level, LivingEntity family) {
        family.heal(3.0F);
        family.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0, false, true));
        family.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 8, 0, false, true));

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                family.getX(), family.getY(0.7D), family.getZ(),
                18, 0.5D, 0.45D, 0.5D, 0.03D);
        this.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
    }

    private void useTrick(ServerLevel level, LivingEntity threat) {
        applyFright(threat, 20 * 7, false);
        threat.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0, false, true));

        level.sendParticles(
                ParticleTypes.WITCH,
                threat.getX(), threat.getY(0.7D), threat.getZ(),
                22, 0.6D, 0.55D, 0.6D, 0.035D);
        this.playSound(SoundEvents.WITCH_CELEBRATE, 0.7F, 1.15F);
    }

    // ---------------------------------------------------------------------
    // Pumpkin Guard
    // ---------------------------------------------------------------------

    private void tickPumpkinGuard(ServerLevel level) {
        if (pumpkinGuardPulseTicks > 0) {
            return;
        }
        pumpkinGuardPulseTicks = 20;

        List<LivingEntity> family = getNearbyHalloweenFamily(level, 11.0D);
        for (LivingEntity member : family) {
            member.removeEffect(MobEffects.DARKNESS);
            member.removeEffect(MobEffects.BLINDNESS);
            member.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE,
                    30,
                    0,
                    false,
                    false));
        }

        for (Mob hostile : level.getEntitiesOfClass(
                Mob.class,
                this.getBoundingBox().inflate(13.0D),
                mob -> mob instanceof Enemy && mob.isAlive())) {
            LivingEntity target = hostile.getTarget();
            if (target != null
                    && family.contains(target)
                    && this.random.nextFloat() < 0.18F) {
                hostile.setTarget(null);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Spirit Bite
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && !this.isBaby()) {
            applyFright(living, 20 * 4, false);
        }
        return hit;
    }

    // ---------------------------------------------------------------------
    // Haunted Ground
    // ---------------------------------------------------------------------

    private void startHauntedGround(ServerLevel level) {
        hauntedGroundTicks = HAUNTED_GROUND_DURATION;
        hauntedGroundCooldownTicks = HAUNTED_GROUND_COOLDOWN;
        this.entityData.set(DATA_HAUNTED_GROUND, true);

        this.playSound(SoundEvents.SOUL_ESCAPE.value(), 0.9F, 0.75F);
        level.sendParticles(
                ParticleTypes.SOUL,
                this.getX(), this.getY(0.3D), this.getZ(),
                38, 2.2D, 0.25D, 2.2D, 0.035D);
    }

    private void tickHauntedGround(ServerLevel level) {
        if (hauntedGroundTicks <= 0) {
            this.entityData.set(DATA_HAUNTED_GROUND, false);
            return;
        }

        if (this.tickCount % 8 == 0) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    this.getX(), this.getY(0.25D), this.getZ(),
                    9, 4.5D, 0.20D, 4.5D, 0.01D);
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY(0.18D), this.getZ(),
                    11, 4.0D, 0.18D, 4.0D, 0.015D);
        }

        if (this.tickCount % 20 == 0) {
            for (LivingEntity enemy : getNearbyHalloweenThreats(level, 11.0D)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 0, false, true));
                enemy.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, true));
                if (!isFearBoss(enemy) && enemy instanceof Mob mob && this.random.nextFloat() < 0.35F) {
                    mob.setTarget(null);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Night of Fright — ultimate
    // ---------------------------------------------------------------------

    private void startNightOfFright(ServerLevel level) {
        nightOfFrightTicks = NIGHT_OF_FRIGHT_DURATION;
        nightOfFrightCooldownTicks = NIGHT_OF_FRIGHT_COOLDOWN;
        hauntedGroundTicks = 0;
        this.entityData.set(DATA_HAUNTED_GROUND, false);
        this.entityData.set(DATA_NIGHT_OF_FRIGHT, true);

        this.addEffect(new MobEffectInstance(MobEffects.SPEED, NIGHT_OF_FRIGHT_DURATION, 1, false, false));
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, NIGHT_OF_FRIGHT_DURATION, 0, false, false));

        this.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.1F, 0.78F);
        level.sendParticles(
                ParticleTypes.WITCH,
                this.getX(), this.getY(0.65D), this.getZ(),
                70, 2.0D, 1.0D, 2.0D, 0.05D);
        level.sendParticles(
                ParticleTypes.FLAME,
                this.getX(), this.getY(0.45D), this.getZ(),
                44, 1.5D, 0.65D, 1.5D, 0.025D);
    }

    private void tickNightOfFright(ServerLevel level) {
        if (nightOfFrightTicks <= 0) {
            this.entityData.set(DATA_NIGHT_OF_FRIGHT, false);
            return;
        }

        if (this.tickCount % 5 == 0) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    this.getX(), this.getY(0.5D), this.getZ(),
                    12, 5.5D, 0.7D, 5.5D, 0.015D);
            level.sendParticles(
                    ParticleTypes.SOUL,
                    this.getX(), this.getY(0.35D), this.getZ(),
                    8, 5.0D, 0.4D, 5.0D, 0.01D);
        }

        if (this.tickCount % 10 == 0) {
            for (LivingEntity enemy : getNearbyHalloweenThreats(level, 17.0D)) {
                applyFright(enemy, 45, true);

                // Halloween becomes frustrating to focus during the horror show.
                if (enemy instanceof Mob mob
                        && mob.getTarget() == this
                        && this.random.nextFloat() < 0.55F) {
                    mob.setTarget(null);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Holiday Recovery presentation
    // ---------------------------------------------------------------------

    @Override
    protected void spawnHolidayRecoveryParticles(ServerLevel level, boolean finishing) {
        if (finishing) {
            level.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX(), this.getY(0.55D), this.getZ(),
                    30, 0.8D, 0.55D, 0.8D, 0.04D);
            level.sendParticles(
                    ParticleTypes.WITCH,
                    this.getX(), this.getY(0.65D), this.getZ(),
                    22, 0.8D, 0.55D, 0.8D, 0.035D);
            this.playSound(SoundEvents.PUMPKIN_CARVE, 0.8F, 1.25F);
        } else {
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY(0.45D), this.getZ(),
                    7, 0.45D, 0.30D, 0.45D, 0.015D);
            level.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX(), this.getY(0.38D), this.getZ(),
                    3, 0.35D, 0.20D, 0.35D, 0.01D);
        }
    }

    // ---------------------------------------------------------------------
    // Seasonal spawn rules
    // ---------------------------------------------------------------------

    public static boolean checkHalloweenWolfSpawnRules(
            EntityType<HalloweenWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return false;
        }

        boolean forced = serverLevel.getGameRules().get(ModGameRules.FORCE_HOLIDAY_SPAWNS.get());
        if (!forced && LocalDate.now().getMonthValue() != 10) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        // At night Halloween Wolves are free common forest/plains spawns.
        if (!serverLevel.isBrightOutside()) {
            return true;
        }

        // Daytime sightings are anchored to a local pumpkin patch / placed pumpkin area.
        // The wider attraction radius keeps the holiday encounter common instead of
        // requiring vanilla's random spawn candidate to land almost on top of a pumpkin.
        return hasPumpkinNearby(
                level,
                pos,
                DAYTIME_PUMPKIN_RADIUS,
                DAYTIME_PUMPKIN_VERTICAL_RADIUS);
    }

    private static boolean hasPumpkinNearby(
            ServerLevelAccessor level,
            BlockPos origin,
            int horizontalRadius,
            int verticalRadius) {
        int minX = origin.getX() - horizontalRadius;
        int maxX = origin.getX() + horizontalRadius;
        int minY = origin.getY() - verticalRadius;
        int maxY = origin.getY() + verticalRadius;
        int minZ = origin.getZ() - horizontalRadius;
        int maxZ = origin.getZ() + horizontalRadius;

        /*
         * Spawn-placement predicates may run from WorldGenRegion while a chunk is
         * still being generated. WorldGenRegion is intentionally backed by only a
         * limited chunk cache; asking getBlockState() for a position outside that
         * cache throws "Requested chunk unavailable during world generation".
         *
         * Never force/request neighboring chunks from a spawn predicate. If a
         * candidate position lies in a chunk that this accessor does not currently
         * have, simply skip it. Runtime ServerLevel checks can still see the full
         * loaded 32-block attraction area, while worldgen stays completely safe.
         */
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            int chunkX = x >> 4;

            for (int z = minZ; z <= maxZ; z++) {
                int chunkZ = z >> 4;

                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    scan.set(x, y, z);
                    BlockState state = level.getBlockState(scan);

                    if (state.is(Blocks.PUMPKIN)
                            || state.is(Blocks.CARVED_PUMPKIN)
                            || state.is(Blocks.JACK_O_LANTERN)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("HalloweenHowlCooldown", howlCooldownTicks);
        output.putInt("HalloweenTrickCooldown", trickOrTreatCooldownTicks);
        output.putInt("HalloweenHauntedGroundTicks", hauntedGroundTicks);
        output.putInt("HalloweenHauntedGroundCooldown", hauntedGroundCooldownTicks);
        output.putInt("HalloweenNightOfFrightTicks", nightOfFrightTicks);
        output.putInt("HalloweenNightOfFrightCooldown", nightOfFrightCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        howlCooldownTicks = Math.max(0, input.getIntOr("HalloweenHowlCooldown", 0));
        trickOrTreatCooldownTicks = Math.max(0, input.getIntOr("HalloweenTrickCooldown", 0));
        hauntedGroundTicks = Math.max(0, input.getIntOr("HalloweenHauntedGroundTicks", 0));
        hauntedGroundCooldownTicks = Math.max(0, input.getIntOr("HalloweenHauntedGroundCooldown", 0));
        nightOfFrightTicks = Math.max(0, input.getIntOr("HalloweenNightOfFrightTicks", 0));
        nightOfFrightCooldownTicks = Math.max(0, input.getIntOr("HalloweenNightOfFrightCooldown", 0));
        this.entityData.set(DATA_HAUNTED_GROUND, hauntedGroundTicks > 0);
        this.entityData.set(DATA_NIGHT_OF_FRIGHT, nightOfFrightTicks > 0);
    }
}
