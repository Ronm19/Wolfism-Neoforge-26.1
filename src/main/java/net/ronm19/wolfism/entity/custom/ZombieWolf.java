package net.ronm19.wolfism.entity.custom;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.ZombieWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Zombie Wolf #21: male undead bruiser / survivor.
 *
 * <p>Kit: Rotten Bite, Undying Flesh, Grave Scent, Deathless Rush,
 * and Rise Again. The core fantasy is an undead wolf that is difficult to
 * put down, while still obeying normal Wolfism family-safety rules.</p>
 */
public final class ZombieWolf extends AbstractWolfismWolf {
    private static final TagKey<EntityType<?>> UNDEAD_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("minecraft", "undead"));
    public static final double GRAVE_SCENT_RANGE = 24.0D;
    public static final int DEATHLESS_RUSH_COOLDOWN_TICKS = 20 * 25;
    public static final int RISE_AGAIN_COOLDOWN_TICKS = 20 * 90;

    private static final int GRAVE_SCENT_SCAN_INTERVAL = 60;
    private static final int GRAVE_SCENT_VISUAL_INTERVAL = 20;
    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final int UNDYING_FLESH_DELAY_TICKS = 20 * 5;
    private static final int UNDYING_FLESH_HEAL_INTERVAL = 40;
    private static final float UNDYING_FLESH_HEAL = 1.0F;
    private static final float ROTTEN_BITE_CHANCE = 0.40F;
    private static final int ROTTEN_BITE_HUNGER_TICKS = 20 * 8;
    private static final int ROTTEN_BITE_WEAKNESS_TICKS = 20 * 4;
    private static final float DEATHLESS_RUSH_HEALTH_RATIO = 0.35F;
    private static final int DEATHLESS_RUSH_DURATION = 20 * 8;

    private int abilityLockoutTicks;
    private int ticksSinceLastDamage = Integer.MAX_VALUE / 4;

    private static final class BrainHolder {
        private static final Brain.Provider<ZombieWolf> PROVIDER = Brain.<ZombieWolf>provider(
                ImmutableList.of(ModSensorTypes.ZOMBIE_PACK.get()),
                wolf -> List.of());
    }

    public ZombieWolf(EntityType<? extends ZombieWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.ZOMBIE_WOLF.get();
    }

    @Override
    protected Brain<ZombieWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ZombieWolf> getBrain() {
        return (Brain<ZombieWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof ZombieWolf zombie && this.isZombiePackmate(zombie)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.ZOMBIE_DEATHLESS_RUSH_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.ZOMBIE_RISE_AGAIN_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.ZOMBIE_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidZombieCombatTarget(sharedThreat)) {
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
        if (this.ticksSinceLastDamage < Integer.MAX_VALUE - 1) {
            ++this.ticksSinceLastDamage;
        }

        if (this.isBaby()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get());
            return;
        }

        // 2. Undying Flesh is a passive recovery system and does not consume
        // the active ability slot.
        if (this.tickCount % UNDYING_FLESH_HEAL_INTERVAL == 0) {
            this.tickUndyingFlesh(level);
        }

        // 3. Grave Scent is owner-facing utility. Scan and visual ticks are
        // staggered so a pack does not spam the same expensive work at once.
        if (this.isTame()
                && this.tickCount % GRAVE_SCENT_SCAN_INTERVAL == Math.floorMod(this.getId(), GRAVE_SCENT_SCAN_INTERVAL)) {
            this.updateGraveScent(level);
        }
        if (this.tickCount % GRAVE_SCENT_VISUAL_INTERVAL == 0) {
            this.tickGraveScentVisual(level);
        }

        // 4. Deathless Rush is the active low-health combat burst.
        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            this.tryDeathlessRush(level);
        }
    }

    // ---------------------------------------------------------------------
    // 1. Rotten Bite (combat passive)
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(entity);
        if (hurt
                && !this.isBaby()
                && entity instanceof LivingEntity target
                && !(target instanceof Creeper)
                && this.random.nextFloat() < ROTTEN_BITE_CHANCE) {
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, ROTTEN_BITE_HUNGER_TICKS, 0), this);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROTTEN_BITE_WEAKNESS_TICKS, 0), this);
            level.sendParticles(
                    ParticleTypes.COMPOSTER,
                    target.getX(), target.getY(0.55D), target.getZ(),
                    14, 0.28D, 0.30D, 0.28D, 0.04D);
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    target.getX(), target.getY(0.45D), target.getZ(),
                    7, 0.20D, 0.20D, 0.20D, 0.01D);
        }
        return hurt;
    }

    // ---------------------------------------------------------------------
    // 2. Undying Flesh (passive recovery)
    // ---------------------------------------------------------------------

    private void tickUndyingFlesh(ServerLevel level) {
        if (this.ticksSinceLastDamage < UNDYING_FLESH_DELAY_TICKS
                || this.getTarget() != null
                || this.getHealth() >= this.getMaxHealth()) {
            return;
        }

        this.heal(UNDYING_FLESH_HEAL);
        level.sendParticles(
                ParticleTypes.SOUL,
                this.getX(), this.getY(0.55D), this.getZ(),
                4, 0.18D, 0.24D, 0.18D, 0.01D);
    }

    // ---------------------------------------------------------------------
    // 3. Grave Scent (undead tracker)
    // ---------------------------------------------------------------------

    private void updateGraveScent(ServerLevel level) {
        ServerPlayer owner = this.getZombieOwner();
        if (owner == null || owner.distanceToSqr(this) > 24.0D * 24.0D) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get());
            return;
        }

        LivingEntity nearest = level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(GRAVE_SCENT_RANGE),
                        candidate -> candidate != this
                                && candidate.isAlive()
                                && candidate.is(UNDEAD_ENTITY_TYPES)
                                && !this.isZombieFamilyMember(candidate))
                .stream()
                .min((a, b) -> Double.compare(this.distanceToSqr(a), this.distanceToSqr(b)))
                .orElse(null);

        if (nearest == null) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get());
        } else {
            this.getBrain().setMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get(), nearest);
        }
    }

    private void tickGraveScentVisual(ServerLevel level) {
        LivingEntity target = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get())
                .orElse(null);
        ServerPlayer owner = this.getZombieOwner();
        if (target == null || owner == null) {
            return;
        }

        if (!target.isAlive()
                || this.distanceToSqr(target) > GRAVE_SCENT_RANGE * GRAVE_SCENT_RANGE
                || owner.distanceToSqr(this) > 24.0D * 24.0D
                || this.isZombieFamilyMember(target)) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.ZOMBIE_GRAVE_SCENT_TARGET.get());
            return;
        }

        this.sendDirectionalTrace(
                level,
                this.position().add(0.0D, 0.62D, 0.0D),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D),
                3.2D,
                9,
                ParticleTypes.SOUL);

        level.sendParticles(
                ParticleTypes.SMOKE,
                target.getX(), target.getY(0.55D), target.getZ(),
                3, 0.20D, 0.24D, 0.20D, 0.01D);

        if (owner.distanceToSqr(this) <= 16.0D * 16.0D) {
            int horizontalDistance = (int) Math.round(Math.sqrt(
                    horizontalDistanceSqr(owner.position(), target.position())));
            int vertical = target.blockPosition().getY() - owner.blockPosition().getY();
            String verticalText = vertical == 0 ? "LEVEL" : vertical > 0 ? "UP " + vertical : "DOWN " + (-vertical);
            String direction = horizontalDirection(owner.position(), target.position());
            owner.sendOverlayMessage(Component.literal(
                    "Grave Scent ◆ " + target.getName().getString()
                            + " ◆ " + horizontalDistance + " blocks ◆ " + direction
                            + " ◆ " + verticalText));
        }
    }

    // ---------------------------------------------------------------------
    // 4. Deathless Rush (active low-health combat burst)
    // ---------------------------------------------------------------------

    private boolean tryDeathlessRush(ServerLevel level) {
        if (!this.canUseActiveZombieAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.ZOMBIE_DEATHLESS_RUSH_COOLDOWN.get())
                || this.getHealth() / this.getMaxHealth() > DEATHLESS_RUSH_HEALTH_RATIO) {
            return false;
        }

        LivingEntity target = this.getPrimaryZombieTarget();
        if (target == null) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.ZOMBIE_DEATHLESS_RUSH_COOLDOWN.get(),
                DEATHLESS_RUSH_COOLDOWN_TICKS);
        this.abilityLockoutTicks = 20;

        this.addEffect(new MobEffectInstance(MobEffects.SPEED, DEATHLESS_RUSH_DURATION, 1, true, true), this);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, DEATHLESS_RUSH_DURATION, 0, true, true), this);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DEATHLESS_RUSH_DURATION, 0, true, true), this);

        level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                this.getX(), this.getY(0.65D), this.getZ(),
                18, 0.35D, 0.38D, 0.35D, 0.02D);
        this.sendRing(level, this.position(), 1.6D, 24, ParticleTypes.SOUL);

        this.alertPackToThreat(target);
        return true;
    }

    // ---------------------------------------------------------------------
    // 5. Rise Again (signature lethal-hit prevention)
    // ---------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }

        this.ticksSinceLastDamage = 0;

        if (!this.isBaby()
                && damage >= this.getHealth()
                && !this.isCoolingDown(ModMemoryModuleTypes.ZOMBIE_RISE_AGAIN_COOLDOWN.get())) {
            this.getBrain().setMemory(
                    ModMemoryModuleTypes.ZOMBIE_RISE_AGAIN_COOLDOWN.get(),
                    RISE_AGAIN_COOLDOWN_TICKS);

            this.setHealth(1.0F);
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 4, 2, true, true), this);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 1, true, true), this);
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, 30);

            level.sendParticles(
                    ParticleTypes.SOUL,
                    this.getX(), this.getY(0.55D), this.getZ(),
                    34, 0.42D, 0.48D, 0.42D, 0.06D);
            level.sendParticles(
                    ParticleTypes.POOF,
                    this.getX(), this.getY(0.50D), this.getZ(),
                    18, 0.34D, 0.36D, 0.34D, 0.04D);
            this.sendRing(level, this.position(), 2.0D, 32, ParticleTypes.SOUL);
            return true;
        }

        return super.hurtServer(level, source, damage);
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof ZombieWolf zombie && this.isZombiePackmate(zombie)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isZombiePackmate(ZombieWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isZombieFamilyMember(LivingEntity entity) {
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
            // Wolfism family rule: all tamed wolves are family regardless of
            // elemental/supernatural type.
            return entity instanceof Wolf wolf && wolf.isTame();
        }
        return entity instanceof ZombieWolf zombie && this.isZombiePackmate(zombie);
    }

    public boolean isValidZombieCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof ZombieWolf zombie && this.isZombiePackmate(zombie)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private LivingEntity getPrimaryZombieTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && this.isValidZombieCombatTarget(target)) {
            return target;
        }

        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && this.isValidZombieCombatTarget(familyTarget)) {
            return familyTarget;
        }

        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.ZOMBIE_PACK_THREAT.get())
                .orElse(null);
        return shared != null && this.isValidZombieCombatTarget(shared) ? shared : null;
    }

    private ServerPlayer getZombieOwner() {
        return this.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    private boolean canUseActiveZombieAbility() {
        return !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()
                && this.abilityLockoutTicks <= 0;
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

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidZombieCombatTarget(threat)) {
            return;
        }

        for (ZombieWolf mate : level.getEntitiesOfClass(
                ZombieWolf.class,
                this.getBoundingBox().inflate(ZombieWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isZombiePackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.ZOMBIE_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidZombieCombatTarget(threat)) {
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

    private void sendDirectionalTrace(
            ServerLevel level,
            Vec3 source,
            Vec3 target,
            double length,
            int points,
            ParticleOptions particle) {
        Vec3 delta = target.subtract(source);
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 direction = delta.normalize();
        for (int i = 1; i <= points; ++i) {
            double step = length * i / points;
            Vec3 point = source.add(direction.scale(step));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private void sendRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {
        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.18D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return dx * dx + dz * dz;
    }

    private static String horizontalDirection(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        String northSouth = Math.abs(dz) < 1.5D ? "" : dz < 0.0D ? "N" : "S";
        String eastWest = Math.abs(dx) < 1.5D ? "" : dx > 0.0D ? "E" : "W";
        String combined = northSouth + eastWest;
        return combined.isEmpty() ? "HERE" : combined;
    }

    public static boolean checkZombieWolfSpawnRules(
            EntityType<ZombieWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        // Physical ground/obstruction validity is already enforced by the
        // ON_GROUND spawn placement registration. Zombie Wolf's species rule
        // is intentionally simple: if the outside world is dark, he may spawn.
        // This avoids rejecting valid forest/swamp surfaces through the narrow
        // WOLVES_SPAWNABLE_ON block tag and avoids raw-light edge cases.
        return level.getLevel().isDarkOutside();
    }
}
