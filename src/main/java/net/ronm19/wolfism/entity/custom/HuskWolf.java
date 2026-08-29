package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.HuskWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Husk Wolf #23 (male): the Irritable Drifter / desert attrition hunter.
 *
 * <p>Canonical kit: Sandstorm Call, Wither in Sun, Drying Howl and Heat
 * Resistance, with the Husk family trait of inflicting Hunger through melee.
 * He is deliberately different from Zombie Wolf's resurrection identity and
 * Skeleton Wolf's ranged skirmishing: Husk Wolf wins by exhausting enemies.</p>
 */
public final class HuskWolf extends AbstractWolfismWolf {
    public static final int SANDSTORM_CALL_COOLDOWN_TICKS = 20 * 28;
    public static final int DRYING_HOWL_COOLDOWN_TICKS = 20 * 22;

    private static final double SANDSTORM_RADIUS = 7.0D;
    private static final double DRYING_HOWL_RADIUS = 10.0D;

    private static final int HUNGER_BITE_TICKS = 20 * 7;
    private static final int WITHER_IN_SUN_TICKS = 20 * 5;
    private static final float WITHER_IN_SUN_CHANCE = 0.40F;

    private static final int SANDSTORM_BLINDNESS_TICKS = 20 * 4;
    private static final int SANDSTORM_SLOWNESS_TICKS = 20 * 6;

    private static final int DRYING_HOWL_HUNGER_TICKS = 20 * 10;
    private static final int DRYING_HOWL_WEAKNESS_TICKS = 20 * 6;

    private static final float FIRE_DAMAGE_MULTIPLIER = 0.35F;
    private static final int ABILITY_DECISION_INTERVAL = 10;

    private int abilityLockoutTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<HuskWolf> PROVIDER = Brain.<HuskWolf>provider(
                ImmutableList.of(ModSensorTypes.HUSK_PACK.get()),
                wolf -> List.of());
    }

    public HuskWolf(EntityType<? extends HuskWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.HUSK_WOLF.get();
    }

    @Override
    protected Brain<HuskWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<HuskWolf> getBrain() {
        return (Brain<HuskWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof HuskWolf husk && this.isHuskPackmate(husk)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.HUSK_SANDSTORM_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.HUSK_DRYING_HOWL_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.canParticipateInWolfismCombat() && this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.HUSK_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidHuskCombatTarget(sharedThreat)) {
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

        if (this.abilityLockoutTicks > 0) {
            --this.abilityLockoutTicks;
        }

        if (!this.canParticipateInWolfismCombat()) {
            return;
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0) {
            if (!this.trySandstormCall(level)) {
                this.tryDryingHowl(level);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Husk family trait: Hunger on melee
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(entity);
        if (!hurt
                || !this.canParticipateInWolfismCombat()
                || !(entity instanceof LivingEntity target)
                || this.isHuskFamilyMember(target)) {
            return hurt;
        }

        // Vanilla-Husk-inspired attrition identity: every successful adult bite
        // dries the victim out enough to apply Hunger I.
        target.addEffect(new MobEffectInstance(
                MobEffects.HUNGER,
                HUNGER_BITE_TICKS,
                0),
                this);

        level.sendParticles(
                new BlockParticleOption(ParticleTypes.FALLING_DUST, this.getSandParticleState()),
                target.getX(), target.getY(0.55D), target.getZ(),
                7, 0.22D, 0.28D, 0.22D, 0.01D);

        // -----------------------------------------------------------------
        // Wither in Sun
        // -----------------------------------------------------------------
        // Husk Wolf himself is sunlight-resistant. Under open daylight, his
        // successful bite can instead turn the desert sun against the victim.
        if (this.isStandingInOpenSun(level)
                && this.random.nextFloat() < WITHER_IN_SUN_CHANCE) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.WITHER,
                    WITHER_IN_SUN_TICKS,
                    0),
                    this);

            level.sendParticles(
                    ParticleTypes.SMOKE,
                    target.getX(), target.getY(0.55D), target.getZ(),
                    10, 0.24D, 0.30D, 0.24D, 0.02D);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // 1. Sandstorm Call
    // ---------------------------------------------------------------------

    private boolean trySandstormCall(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.abilityLockoutTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.HUSK_SANDSTORM_COOLDOWN.get())) {
            return false;
        }

        List<LivingEntity> hostiles = this.getHostilesInRange(level, SANDSTORM_RADIUS);
        if (hostiles.size() < 2) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.HUSK_SANDSTORM_COOLDOWN.get(),
                SANDSTORM_CALL_COOLDOWN_TICKS);
        this.abilityLockoutTicks = 20;

        BlockParticleOption sand = new BlockParticleOption(
                ParticleTypes.FALLING_DUST,
                this.getSandParticleState());

        // Layered drifting rings make the call readable without becoming a
        // screen-filling particle wall.
        this.sendRing(level, this.position(), 2.0D, 28, sand, 0.20D);
        this.sendRing(level, this.position(), 4.0D, 36, sand, 0.35D);
        this.sendRing(level, this.position(), 6.0D, 44, sand, 0.50D);

        for (LivingEntity target : hostiles) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    SANDSTORM_BLINDNESS_TICKS,
                    0),
                    this);
            target.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS,
                    SANDSTORM_SLOWNESS_TICKS,
                    0),
                    this);

            Vec3 push = target.position().subtract(this.position());
            if (push.lengthSqr() > 1.0E-5D) {
                push = push.normalize().scale(0.18D);
                target.setDeltaMovement(target.getDeltaMovement().add(
                        push.x, 0.04D, push.z));
                target.hurtMarked = true;
            }
        }

        this.sharePackCooldown(
                ModMemoryModuleTypes.HUSK_SANDSTORM_COOLDOWN.get(),
                20 * 6);
        this.playSound(SoundEvents.HUSK_AMBIENT, 1.15F, 0.72F);
        return true;
    }

    // ---------------------------------------------------------------------
    // 2. Wither in Sun
    // ---------------------------------------------------------------------
    // Implemented inside doHurtTarget(): successful melee in open daylight has
    // a 40% chance to inflict Wither I for five seconds.

    // ---------------------------------------------------------------------
    // 3. Drying Howl
    // ---------------------------------------------------------------------

    private boolean tryDryingHowl(ServerLevel level) {
        if (!this.canUseActiveWolfismAbility()
                || this.abilityLockoutTicks > 0
                || this.isCoolingDown(ModMemoryModuleTypes.HUSK_DRYING_HOWL_COOLDOWN.get())) {
            return false;
        }

        List<LivingEntity> hostiles = this.getHostilesInRange(level, DRYING_HOWL_RADIUS);
        if (hostiles.isEmpty()) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.HUSK_DRYING_HOWL_COOLDOWN.get(),
                DRYING_HOWL_COOLDOWN_TICKS);
        this.abilityLockoutTicks = 16;

        for (LivingEntity target : hostiles) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.HUNGER,
                    DRYING_HOWL_HUNGER_TICKS,
                    1),
                    this);
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    DRYING_HOWL_WEAKNESS_TICKS,
                    0),
                    this);
        }

        BlockParticleOption sand = new BlockParticleOption(
                ParticleTypes.FALLING_DUST,
                this.getSandParticleState());

        this.sendRing(level, this.position(), 2.4D, 30, sand, 0.22D);
        this.sendRing(level, this.position(), 5.0D, 40, ParticleTypes.SMOKE, 0.34D);
        this.sendRing(level, this.position(), 8.0D, 52, sand, 0.46D);

        this.sharePackCooldown(
                ModMemoryModuleTypes.HUSK_DRYING_HOWL_COOLDOWN.get(),
                20 * 5);
        this.playSound(SoundEvents.HUSK_AMBIENT, 1.35F, 0.58F);
        return true;
    }

    // ---------------------------------------------------------------------
    // 4. Heat Resistance
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // Innate species trait, so pups receive it too even though they remain
        // non-combatants. Sunlight itself never ignites Husk Wolf; this also
        // makes ordinary fire/lava substantially less dangerous.
        if (source.is(DamageTypeTags.IS_FIRE)) {
            damage *= FIRE_DAMAGE_MULTIPLIER;
        }
        return super.hurtServer(level, source, damage);
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    public boolean isHuskPackmate(HuskWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }

        if (!this.isTame()) {
            return true;
        }

        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isHuskFamilyMember(LivingEntity entity) {
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

            // Universal Wolfism rule: every tamed wolf is family.
            return entity instanceof Wolf wolf && wolf.isTame();
        }

        return entity instanceof HuskWolf husk && this.isHuskPackmate(husk);
    }

    public boolean isValidHuskCombatTarget(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !this.canParticipateInWolfismCombat()
                || !this.canAttack(target)
                || this.isAlliedTo(target)
                || this.isHuskFamilyMember(target)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidHuskCombatTarget(threat)) {
            return;
        }

        for (HuskWolf mate : level.getEntitiesOfClass(
                HuskWolf.class,
                this.getBoundingBox().inflate(HuskWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isHuskPackmate(wolf)))) {
            mate.getBrain().setMemory(
                    ModMemoryModuleTypes.HUSK_PACK_THREAT.get(),
                    threat);

            if (mate.canParticipateInWolfismCombat()
                    && mate.isValidHuskCombatTarget(threat)) {
                mate.setTarget(threat);
            }
        }
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {
        if (attacker != null && attacker.isAlive()) {
            this.alertPackToThreat(attacker);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<LivingEntity> getHostilesInRange(
            ServerLevel level,
            double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isValidHuskCombatTarget);
    }

    private boolean isStandingInOpenSun(ServerLevel level) {
        // isDarkOutside() is the proven 26.1 day/night helper already used by
        // Wolfism's finalized undead spawn logic. Its inverse + sky visibility
        // gives us an uncomplicated "open daylight" test.
        return !level.isDarkOutside()
                && level.canSeeSky(this.blockPosition());
    }

    private BlockState getSandParticleState() {
        BlockState below = this.level().getBlockState(this.blockPosition().below());
        return below.is(Blocks.RED_SAND)
                ? Blocks.RED_SAND.defaultBlockState()
                : Blocks.SAND.defaultBlockState();
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

    private void sharePackCooldown(
            MemoryModuleType<Integer> memory,
            int minimumTicks) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        for (HuskWolf mate : level.getEntitiesOfClass(
                HuskWolf.class,
                this.getBoundingBox().inflate(HuskWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isHuskPackmate(wolf)))) {
            int current = mate.getBrain().getMemory(memory).orElse(0);
            if (current < minimumTicks) {
                mate.getBrain().setMemory(memory, minimumTicks);
            }
        }
    }

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle,
            double yOffset) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + yOffset,
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.02D, 0.05D, 0.02D,
                    0.0D);
        }
    }

    public static boolean checkHuskWolfSpawnRules(
            EntityType<HuskWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        // Husk Wolf is explicitly sun-resistant. The biome modifier already
        // restricts him to desert/badlands habitats, while ON_GROUND +
        // MOTION_BLOCKING_NO_LEAVES handles physical placement. No darkness
        // gate: he is allowed to exist in the desert during the day.
        return true;
    }
}
