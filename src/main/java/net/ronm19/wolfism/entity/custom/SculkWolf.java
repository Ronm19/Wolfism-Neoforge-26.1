package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.goal.SculkPackAssistGoal;
import net.ronm19.wolfism.entity.ai.goal.SculkPackCohesionGoal;
import net.ronm19.wolfism.entity.ai.goal.SculkPupFollowAdultGoal;
import net.ronm19.wolfism.entity.ai.goal.SculkPupPlayGoal;
import net.ronm19.wolfism.entity.ai.goal.SculkPupRetreatGoal;
import net.ronm19.wolfism.entity.ai.sensor.SculkWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Wolfism #28 - Sculk Wolf.
 *
 * <p>Identity: a calm Deep Dark sentinel and the Warden's natural predator.
 * Against ordinary enemies it remains a defensive/sensory wolf; against a Warden
 * it becomes an apex counter-hunter built to break the Warden in direct combat.
 * The core systems are:</p>
 * <ul>
 *     <li>Vibration Immunity - the Sculk Wolf itself is invisible to vibration listeners.</li>
 *     <li>Deep Dark Sense - nearby vanilla GameEvents are perceived as real vibrations.</li>
 *     <li>Pack Silence - a tamed adult suppresses movement vibrations from nearby tamed wolves.</li>
 *     <li>Sculk Shield - a timed family field that reduces incoming damage, including sonic-boom mitigation.</li>
 *     <li>Warden Slayer - extreme Warden-only bite damage, Warden resistance, pursuit, and stagger.</li>
 * </ul>
 */
public final class SculkWolf extends AbstractWolfismWolf implements VibrationSystem {
    private static final double SCULK_WOLF_BASE_MAX_HEALTH = 32.0D;
    public static final double DEEP_DARK_SENSE_RADIUS = 24.0D;
    public static final double PACK_SILENCE_RADIUS = 10.0D;
    public static final double SCULK_SHIELD_RADIUS = 10.0D;

    public static final int SCULK_SHIELD_DURATION_TICKS = 20 * 8;
    public static final int SCULK_SHIELD_COOLDOWN_TICKS = 20 * 30;
    public static final float SCULK_SHIELD_DAMAGE_MULTIPLIER = 0.80F;
    public static final float SCULK_SHIELD_SONIC_MULTIPLIER = 0.65F;

    /*
     * Warden Slayer tuning. These multipliers ONLY apply when a Warden is the
     * attacker/target; ordinary combat remains at the normal Sculk Wolf statline.
     */
    public static final float WARDEN_BITE_DAMAGE = 190.0F;
    public static final float WARDEN_MELEE_DAMAGE_MULTIPLIER = 0.35F;
    public static final float WARDEN_SONIC_DAMAGE_MULTIPLIER = 0.20F;
    private static final int WARDEN_STAGGER_TICKS = 20 * 2;
    private static final int WARDEN_WEAKNESS_TICKS = 20 * 3;

    private static final Identifier WARDEN_BITE_DAMAGE_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "sculk_warden_bite");
    private static final Identifier WARDEN_HUNT_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "sculk_warden_hunt_speed");
    private static final Identifier WARDEN_HUNT_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "sculk_warden_hunt_knockback");

    private static final int VIBRATION_MEMORY_TICKS = 20 * 4;
    private static final int SENSE_PARTICLE_INTERVAL = 8;
    private static final int PASSIVE_SCULK_SCAN_INTERVAL = 40;
    private static final int PASSIVE_SCULK_SCAN_RADIUS = 10;

    private int sculkShieldTicks;
    private int sculkShieldCooldownTicks;
    private int sensedVibrationTicks;
    private Vec3 lastSensedVibration;
    private int lastSensedSourceId = -1;
    private BlockPos nearestSculkTrigger;


    /*
     * Real vanilla vibration-listener state.
     */
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculkVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    public void updateDynamicGameEventListener(
            BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        super.updateDynamicGameEventListener(listenerConsumer);

        if (this.level() instanceof ServerLevel serverLevel) {
            listenerConsumer.accept(this.dynamicVibrationListener, serverLevel);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel serverLevel) {
            VibrationSystem.Ticker.tick(serverLevel, this.vibrationData, this.vibrationUser);
        }
    }

    private final class SculkVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource =
                new EntityPositionSource(SculkWolf.this, SculkWolf.this.getEyeHeight());

        @Override
        public int getListenerRadius() {
            return (int) DEEP_DARK_SENSE_RADIUS;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canReceiveVibration(
                ServerLevel level,
                BlockPos pos,
                Holder<GameEvent> event,
                GameEvent.Context context) {
            if (!SculkWolf.this.isAlive()) {
                return false;
            }

            Entity source = context.sourceEntity();
            return source == null || !SculkWolf.this.isSculkFamilyMember(source);
        }

        @Override
        public void onReceiveVibration(
                ServerLevel level,
                BlockPos pos,
                Holder<GameEvent> event,
                Entity sourceEntity,
                Entity projectileOwner,
                float receivingDistance) {
            Entity source = sourceEntity != null ? sourceEntity : projectileOwner;
            SculkWolf.this.observeVibration(event, Vec3.atCenterOf(pos), source);
        }
    }

    private static final class BrainHolder {
        private static final Brain.Provider<SculkWolf> PROVIDER = Brain.<SculkWolf>provider(
                ImmutableList.of(ModSensorTypes.SCULK_PACK.get()),
                wolf -> List.of());
    }

    public SculkWolf(EntityType<? extends SculkWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.SCULK_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, SCULK_WOLF_BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D);
    }

    /** Keep the species max-health value when vanilla wolf taming/load side-effects run. */
    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(SCULK_WOLF_BASE_MAX_HEALTH);
        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new SculkPupRetreatGoal(this));
        this.goalSelector.addGoal(5, new SculkPupFollowAdultGoal(this));
        this.goalSelector.addGoal(6, new SculkPackCohesionGoal(this));
        this.goalSelector.addGoal(7, new SculkPupPlayGoal(this));
        this.targetSelector.addGoal(3, new SculkPackAssistGoal(this));
    }

    @Override
    protected Brain<SculkWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<SculkWolf> getBrain() {
        return (Brain<SculkWolf>) super.getBrain();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    /**
     * Vanilla vibration listeners consult this flag. It is the first line of
     * defense for the species' personal Sculk Sensor/Shrieker immunity.
     */
    @Override
    public boolean dampensVibrations() {
        return true;
    }

    /**
     * Warden Slayer: ordinary targets use the normal 5.5-damage bite. Against a
     * Warden, a temporary attack modifier makes the single vanilla melee impact
     * land for roughly 190 damage. This avoids a second same-tick damage call and
     * therefore avoids vanilla hurt-invulnerability-frame weirdness.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (this.isBaby() || !(entity instanceof Warden warden)) {
            return super.doHurtTarget(entity);
        }

        AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return super.doHurtTarget(entity);
        }

        double currentDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double bonusDamage = Math.max(0.0D, WARDEN_BITE_DAMAGE - currentDamage);

        attackDamage.addOrUpdateTransientModifier(new AttributeModifier(
                WARDEN_BITE_DAMAGE_MODIFIER,
                bonusDamage,
                AttributeModifier.Operation.ADD_VALUE));

        boolean hurt;
        try {
            hurt = super.doHurtTarget(entity);
        } finally {
            attackDamage.removeModifier(WARDEN_BITE_DAMAGE_MODIFIER);
        }

        if (hurt) {
            // The bite disrupts the Warden's footing and melee pressure long
            // enough for the wolf to stay on top of the fight.
            warden.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WARDEN_STAGGER_TICKS, 2), this);
            warden.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WARDEN_WEAKNESS_TICKS, 1), this);

            level.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    warden.getX(), warden.getY() + 1.2D, warden.getZ(),
                    10,
                    0.45D, 0.55D, 0.45D,
                    0.025D);

            // A bonded guardian braces its family field as soon as it commits
            // to a Warden duel. Wild wolves keep the Warden-slayer offense and
            // innate Warden resistance but do not gain the tamed-only shield.
            this.tryActivateSculkShield();
        }

        return hurt;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof SculkWolf sculk && this.isSculkPackmate(sculk)) {
            this.setTarget(null);
        }

        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        if (this.isBaby()) {
            this.setTarget(null);
            this.sculkShieldTicks = 0;
        }

        this.tickSculkShield(level);
        this.tickDeepDarkSense(level);
        this.refreshWardenHunterAttributes();
    }

    private void tickSculkShield(ServerLevel level) {
        if (this.sculkShieldCooldownTicks > 0) {
            --this.sculkShieldCooldownTicks;
        }

        if (this.sculkShieldTicks <= 0) {
            return;
        }

        --this.sculkShieldTicks;

        if (this.tickCount % 5 == 0) {
            this.emitShieldParticles(level);
        }

        if (this.sculkShieldTicks == 0) {
            this.sculkShieldCooldownTicks = SCULK_SHIELD_COOLDOWN_TICKS;
        }
    }

    /**
     * Activates only for a tamed adult. Wild packs keep their social intelligence,
     * but the family-wide protection field is specifically a bonded-wolf ability.
     */
    public boolean tryActivateSculkShield() {
        if (!(this.level() instanceof ServerLevel level)
                || !this.isTame()
                || this.isBaby()
                || !this.isAlive()
                || this.sculkShieldTicks > 0
                || this.sculkShieldCooldownTicks > 0) {
            return false;
        }

        this.sculkShieldTicks = SCULK_SHIELD_DURATION_TICKS;
        this.emitShieldBurst(level);
        return true;
    }

    public boolean isSculkShieldActive() {
        return this.sculkShieldTicks > 0;
    }

    public int getSculkShieldCooldownTicks() {
        return this.sculkShieldCooldownTicks;
    }

    public boolean protectsWithSculkShield(Wolf wolf) {
        return this.isSculkShieldActive()
                && this.isTame()
                && !this.isBaby()
                && wolf != null
                && wolf.isAlive()
                && wolf.isTame()
                && this.distanceToSqr(wolf) <= SCULK_SHIELD_RADIUS * SCULK_SHIELD_RADIUS;
    }

    private void emitShieldBurst(ServerLevel level) {
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                this.getX(),
                this.getY() + 0.75D,
                this.getZ(),
                18,
                0.55D, 0.45D, 0.55D,
                0.025D);
    }

    private void emitShieldParticles(ServerLevel level) {
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                3,
                0.45D, 0.25D, 0.45D,
                0.005D);
    }

    /** Called by the global VanillaGameEvent bridge before the vibration is delivered. */
    public void observeVibration(Holder<GameEvent> event, Vec3 position, Entity source) {
        if (!(this.level() instanceof ServerLevel level)
                || position == null
                || this.position().distanceToSqr(position) > DEEP_DARK_SENSE_RADIUS * DEEP_DARK_SENSE_RADIUS) {
            return;
        }

        if (source != null && this.isSculkFamilyMember(source)) {
            return;
        }

        this.lastSensedVibration = position;
        this.sensedVibrationTicks = VIBRATION_MEMORY_TICKS;
        this.lastSensedSourceId = source == null ? -1 : source.getId();

        if (isHighPriorityVibration(event) && this.tickCount % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    this.getX(), this.getEyeY(), this.getZ(),
                    4,
                    0.22D, 0.18D, 0.22D,
                    0.01D);
        }
    }

    private void tickDeepDarkSense(ServerLevel level) {
        if (this.sensedVibrationTicks > 0) {
            --this.sensedVibrationTicks;
            if (this.lastSensedVibration != null
                    && this.getTarget() == null
                    && !this.isOrderedToSit()) {
                this.getLookControl().setLookAt(
                        this.lastSensedVibration.x,
                        this.lastSensedVibration.y,
                        this.lastSensedVibration.z,
                        25.0F,
                        20.0F);
            }

            if (this.tickCount % SENSE_PARTICLE_INTERVAL == 0) {
                level.sendParticles(
                        ParticleTypes.SCULK_SOUL,
                        this.getX(), this.getEyeY() + 0.15D, this.getZ(),
                        1,
                        0.08D, 0.08D, 0.08D,
                        0.0D);
            }
        } else {
            this.lastSensedVibration = null;
            this.lastSensedSourceId = -1;
        }

        if (this.tickCount % PASSIVE_SCULK_SCAN_INTERVAL == 0) {
            this.nearestSculkTrigger = this.findNearestSculkTrigger();
        }

        Warden warden = level.getEntitiesOfClass(
                        Warden.class,
                        this.getBoundingBox().inflate(DEEP_DARK_SENSE_RADIUS),
                        Entity::isAlive)
                .stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        if (warden != null) {
            /*
             * Warden priority: an adult Sculk Wolf that is free to fight treats
             * a nearby Warden as the highest-value threat. This is intentional
             * predator behavior, not generic hostility toward ordinary mobs.
             */
            if (!this.isBaby()
                    && !this.isOrderedToSit()
                    && this.isValidSculkCombatTarget(warden)) {
                LivingEntity currentTarget = this.getTarget();
                if (!(currentTarget instanceof Warden)) {
                    this.setTarget(warden);
                    this.alertPackToThreat(warden);
                }
            } else if (this.getTarget() == null && this.tickCount % 10 == 0) {
                this.getLookControl().setLookAt(warden, 35.0F, 25.0F);
            }
        }
    }

    /**
     * Blood-Wolf-like combat pressure, but only while the current target is a
     * Warden. The speed lets the Sculk Wolf maintain contact; the knockback
     * resistance stops the duel from becoming constant disengagement.
     */
    private void refreshWardenHunterAttributes() {
        boolean huntingWarden = !this.isBaby()
                && this.isAlive()
                && this.getTarget() instanceof Warden;

        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance knockback = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

        if (!huntingWarden) {
            if (speed != null) {
                speed.removeModifier(WARDEN_HUNT_SPEED_MODIFIER);
            }
            if (knockback != null) {
                knockback.removeModifier(WARDEN_HUNT_KNOCKBACK_MODIFIER);
            }
            return;
        }

        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    WARDEN_HUNT_SPEED_MODIFIER,
                    0.08D,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        if (knockback != null) {
            knockback.addOrUpdateTransientModifier(new AttributeModifier(
                    WARDEN_HUNT_KNOCKBACK_MODIFIER,
                    0.75D,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private BlockPos findNearestSculkTrigger() {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-PASSIVE_SCULK_SCAN_RADIUS, -4, -PASSIVE_SCULK_SCAN_RADIUS),
                origin.offset(PASSIVE_SCULK_SCAN_RADIUS, 4, PASSIVE_SCULK_SCAN_RADIUS))) {
            BlockState state = this.level().getBlockState(pos);
            if (!state.is(Blocks.SCULK_SHRIEKER) && !state.is(Blocks.SCULK_SENSOR)) {
                continue;
            }

            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }

        return best;
    }

    public boolean hasRecentVibrationSense() {
        return this.sensedVibrationTicks > 0 || this.nearestSculkTrigger != null;
    }

    public Vec3 getLastSensedVibration() {
        return this.lastSensedVibration;
    }

    public int getLastSensedSourceId() {
        return this.lastSensedSourceId;
    }

    public BlockPos getNearestSculkTrigger() {
        return this.nearestSculkTrigger;
    }

    public boolean isSculkPackmate(SculkWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isSculkFamilyMember(Entity entity) {
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

        return entity instanceof SculkWolf sculk && this.isSculkPackmate(sculk);
    }

    public boolean isValidSculkPackThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || target == this
                || target instanceof Creeper
                || this.isAlliedTo(target)
                || this.isSculkFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || target == this.getFamilyDefenseTarget()
                || this.wantsToAttack(target, owner);
    }

    public boolean isValidSculkCombatTarget(LivingEntity target) {
        return !this.isBaby()
                && target != null
                && !(target instanceof Creeper)
                && this.canAttack(target)
                && this.isValidSculkPackThreat(target);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || threat instanceof Creeper
                || !this.isValidSculkPackThreat(threat)) {
            return;
        }

        for (SculkWolf mate : level.getEntitiesOfClass(
                SculkWolf.class,
                this.getBoundingBox().inflate(SculkWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isSculkPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.SCULK_PACK_THREAT.get(), threat);

            if (!mate.isBaby() && mate.isValidSculkCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive() && !(attacker instanceof Creeper)) {
            this.alertPackToThreat(attacker);
        }

        if (protectedFamily instanceof Wolf protectedWolf
                && protectedWolf.isTame()
                && (attacker instanceof Warden
                || protectedWolf.getHealth() / Math.max(1.0F, protectedWolf.getMaxHealth()) <= 0.65F)) {
            this.tryActivateSculkShield();
        }
    }

    public int countNearbyHostileThreats(double radius) {
        if (!(this.level() instanceof ServerLevel level)) {
            return 0;
        }

        AABB area = this.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate instanceof Enemy
                        && !(candidate instanceof Creeper)
                        && candidate.isAlive()
                        && !this.isSculkFamilyMember(candidate)).size();
    }

    private static boolean isHighPriorityVibration(Holder<GameEvent> event) {
        return event.is(GameEvent.SHRIEK)
                || event.is(GameEvent.ENTITY_DAMAGE)
                || event.is(GameEvent.ENTITY_DIE)
                || event.is(GameEvent.EXPLODE)
                || event.is(GameEvent.BLOCK_DESTROY);
    }

    /**
     * Surface Sculk Wolf placement.
     *
     * <p>The biome spawn list decides WHERE the species may be selected. This
     * predicate only decides whether the exact block is a sensible spawn point:</p>
     * <ul>
     *     <li>Sculk is always valid ground, day or night.</li>
     *     <li>Normal vanilla-wolf ground is a daylight fallback.</li>
     *     <li>A small local population cap prevents this non-despawning Animal
     *     from permanently filling the MONSTER spawning budget.</li>
     * </ul>
     */
    public static boolean checkSculkWolfSpawnRules(
            EntityType<SculkWolf> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random
    ) {
        BlockState ground = level.getBlockState(pos.below());

        boolean validGround = ground.is(Blocks.SCULK)
                || ground.is(BlockTags.WOLVES_SPAWNABLE_ON);

        if (!validGround) {
            return false;
        }

        // Preferred habitat: Sculk itself does not care about the time of day.
        // Everywhere else uses the requested ServerLevel daylight fallback.
        if (!ground.is(Blocks.SCULK)) {
            if (!(level instanceof ServerLevel serverLevel) || !serverLevel.isBrightOutside()) {
                return false;
            }
        }

        // Animal#removeWhenFarAway is false, so keep naturally spawned packs local
        // instead of allowing them to accumulate until they consume the monster cap.
        if (spawnReason == EntitySpawnReason.NATURAL
                && level instanceof ServerLevel serverLevel) {
            int nearby = serverLevel.getEntitiesOfClass(
                    SculkWolf.class,
                    new AABB(pos).inflate(48.0D, 16.0D, 48.0D),
                    wolf -> wolf.isAlive() && !wolf.isTame()
            ).size();

            if (nearby >= 4) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SculkShieldTicks", this.sculkShieldTicks);
        output.putInt("SculkShieldCooldown", this.sculkShieldCooldownTicks);
        output.store(VibrationSystem.Data.NBT_TAG_KEY, VibrationSystem.Data.CODEC, this.vibrationData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.sculkShieldTicks = Math.max(0, Math.min(
                SCULK_SHIELD_DURATION_TICKS,
                input.getIntOr("SculkShieldTicks", 0)));
        this.sculkShieldCooldownTicks = Math.max(0,
                input.getIntOr("SculkShieldCooldown", 0));

        this.vibrationData = input.read(
                        VibrationSystem.Data.NBT_TAG_KEY,
                        VibrationSystem.Data.CODEC)
                .orElseGet(VibrationSystem.Data::new);

        if (this.isBaby()) {
            this.sculkShieldTicks = 0;
        }
    }
}
