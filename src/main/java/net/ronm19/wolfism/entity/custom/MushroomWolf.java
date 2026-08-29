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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.ai.sensor.MushroomWolfPackSensor;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * Mushroom Wolf #19: male fungal forager / support specialist.
 *
 * <p>Confirmed kit: Mushroom Finder, Inflict Nausea, Fungus Resistance,
 * and Spore Burst. The abilities are intentionally visually and mechanically
 * distinct so this wolf does not collapse into one generic particle aura.</p>
 */
public final class MushroomWolf extends AbstractWolfismWolf {
    public static final int SPORE_BURST_COOLDOWN_TICKS = 20 * 22;
    public static final double MUSHROOM_FINDER_RANGE = 18.0D;
    public static final int MUSHROOM_FINDER_VERTICAL_RANGE = 8;
    public static final double FUNGUS_RESISTANCE_RADIUS = 7.0D;
    public static final double SPORE_BURST_RADIUS = 7.0D;

    private static final int FINDER_SCAN_INTERVAL = 60;
    private static final int ABILITY_DECISION_INTERVAL = 10;
    private static final float SPORE_BURST_DAMAGE = 3.5F;
    private static final float NAUSEA_CHANCE = 0.35F;

    private int abilityLockoutTicks;

    private static final class BrainHolder {
        private static final Brain.Provider<MushroomWolf> PROVIDER = Brain.<MushroomWolf>provider(
                ImmutableList.of(ModSensorTypes.MUSHROOM_PACK.get()),
                wolf -> List.of());
    }

    public MushroomWolf(EntityType<? extends MushroomWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.MUSHROOM_WOLF.get();
    }

    @Override
    protected Brain<MushroomWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<MushroomWolf> getBrain() {
        return (Brain<MushroomWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof MushroomWolf mushroom && this.isMushroomPackmate(mushroom)) {
            this.setTarget(null);
        }

        this.tickCooldown(ModMemoryModuleTypes.MUSHROOM_SPORE_BURST_COOLDOWN.get());
        this.getBrain().tick(level, this);

        if (this.isBaby()) {
            this.setTarget(null);
        } else if (this.getTarget() == null) {
            LivingEntity sharedThreat = this.getBrain()
                    .getMemory(ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get())
                    .orElse(null);
            if (sharedThreat != null && this.isValidMushroomCombatTarget(sharedThreat)) {
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

        if (this.isBaby()) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get());
            return;
        }

        // Fungus Resistance is a true passive and does not consume the active slot.
        if (this.tickCount % 20 == 0) {
            this.tickFungusResistance(level);
        }

        // Finder scans are staggered per entity so packs do not all scan the same tick.
        if (this.isTame()
                && this.tickCount % FINDER_SCAN_INTERVAL == Math.floorMod(this.getId(), FINDER_SCAN_INTERVAL)) {
            this.updateMushroomFinder(level);
        }

        if (this.tickCount % 20 == 0) {
            this.tickMushroomFinderVisual(level);
        }

        if (this.tickCount % ABILITY_DECISION_INTERVAL == 0 && this.abilityLockoutTicks <= 0) {
            this.trySporeBurst(level);
        }
    }

    // ---------------------------------------------------------------------
    // 1. Mushroom Finder (passive utility)
    // ---------------------------------------------------------------------

    private void updateMushroomFinder(ServerLevel level) {
        ServerPlayer owner = this.getMushroomOwner();
        if (owner == null || owner.distanceToSqr(this) > 24.0D * 24.0D) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get());
            return;
        }

        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int horizontal = (int) MUSHROOM_FINDER_RANGE;

        for (int dx = -horizontal; dx <= horizontal; ++dx) {
            for (int dy = -MUSHROOM_FINDER_VERTICAL_RANGE; dy <= MUSHROOM_FINDER_VERTICAL_RANGE; ++dy) {
                for (int dz = -horizontal; dz <= horizontal; ++dz) {
                    if ((double) (dx * dx + dz * dz) > MUSHROOM_FINDER_RANGE * MUSHROOM_FINDER_RANGE) {
                        continue;
                    }

                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isFungalResource(level.getBlockState(candidate))) {
                        continue;
                    }

                    double distance = candidate.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }

        if (best == null) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get());
        } else {
            this.getBrain().setMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get(), best);
        }
    }

    private void tickMushroomFinderVisual(ServerLevel level) {
        BlockPos target = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get())
                .orElse(null);
        ServerPlayer owner = this.getMushroomOwner();
        if (target == null || owner == null) {
            return;
        }

        // Tracker lifetime is tied to the actual wolf and its detection radius.
        // No Gem-style remote particle trail survives after the companion leaves.
        if (owner.distanceToSqr(this) > 24.0D * 24.0D
                || target.distSqr(this.blockPosition()) > MUSHROOM_FINDER_RANGE * MUSHROOM_FINDER_RANGE
                || !isFungalResource(level.getBlockState(target))) {
            this.getBrain().eraseMemory(ModMemoryModuleTypes.MUSHROOM_FIND_POS.get());
            return;
        }

        this.sendDirectionalTrace(
                level,
                this.position().add(0.0D, 0.65D, 0.0D),
                target,
                3.0D,
                9,
                ParticleTypes.HAPPY_VILLAGER);

        if (owner.distanceToSqr(this) <= 16.0D * 16.0D) {
            this.sendDirectionalTrace(
                    level,
                    owner.position().add(0.0D, 1.05D, 0.0D),
                    target,
                    2.3D,
                    7,
                    ParticleTypes.WITCH);

            Block block = level.getBlockState(target).getBlock();
            int horizontalDistance = (int) Math.round(Math.sqrt(
                    owner.blockPosition().distSqr(new BlockPos(target.getX(), owner.blockPosition().getY(), target.getZ()))));
            int vertical = target.getY() - owner.blockPosition().getY();
            String verticalText = vertical == 0 ? "LEVEL" : vertical > 0 ? "UP " + vertical : "DOWN " + (-vertical);
            owner.sendOverlayMessage(
                    Component.literal("Mushroom Finder ◆ " + block.getName().getString()
                            + " ◆ " + horizontalDistance + " blocks ◆ " + verticalText)
                    );
        }
    }

    private static boolean isFungalResource(BlockState state) {
        return state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS);
    }

    // ---------------------------------------------------------------------
    // 2. Inflict Nausea (combat passive)
    // ---------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        boolean hurt = super.doHurtTarget(level, entity);

        if (hurt
                && !this.isBaby()
                && entity instanceof LivingEntity target
                && !(target instanceof Creeper)
                && this.random.nextFloat() < NAUSEA_CHANCE) {

            target.addEffect(
                    new MobEffectInstance(
                            MobEffects.NAUSEA,
                            20 * 6,
                            0),
                    this
            );

            level.sendParticles(
                    ParticleTypes.WITCH,
                    target.getX(),
                    target.getY(0.55D),
                    target.getZ(),
                    18,
                    0.30D,
                    0.35D,
                    0.30D,
                    0.06D
            );

            level.sendParticles(
                    ParticleTypes.POOF,
                    target.getX(),
                    target.getY(0.50D),
                    target.getZ(),
                    8,
                    0.22D,
                    0.25D,
                    0.22D,
                    0.02D
            );
        }

        return hurt;
    }

    // ---------------------------------------------------------------------
    // 3. Fungus Resistance (passive family cleanse)
    // ---------------------------------------------------------------------

    private void tickFungusResistance(ServerLevel level) {
        for (LivingEntity family : this.findFamily(FUNGUS_RESISTANCE_RADIUS)) {
            boolean cleansed = false;
            if (family.hasEffect(MobEffects.POISON)) {
                family.removeEffect(MobEffects.POISON);
                cleansed = true;
            }
            if (family.hasEffect(MobEffects.NAUSEA)) {
                family.removeEffect(MobEffects.NAUSEA);
                cleansed = true;
            }

            if (cleansed) {
                level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        family.getX(), family.getY(0.55D), family.getZ(),
                        12, 0.24D, 0.30D, 0.24D, 0.03D);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 4. Spore Burst (active combat/support burst)
    // ---------------------------------------------------------------------

    private boolean trySporeBurst(ServerLevel level) {
        if (!this.canUseActiveMushroomAbility()
                || this.isCoolingDown(ModMemoryModuleTypes.MUSHROOM_SPORE_BURST_COOLDOWN.get())) {
            return false;
        }

        LivingEntity primary = this.getPrimaryMushroomTarget();
        List<LivingEntity> threats = this.findMushroomThreats(this.position(), SPORE_BURST_RADIUS, primary);
        LivingEntity injured = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MUSHROOM_INJURED_FAMILY.get())
                .orElse(null);

        boolean familyEmergency = injured != null
                && injured.getHealth() / injured.getMaxHealth() <= 0.45F
                && !threats.isEmpty();

        if (threats.size() < 2 && !familyEmergency) {
            return false;
        }

        this.setPackCooldown(
                ModMemoryModuleTypes.MUSHROOM_SPORE_BURST_COOLDOWN.get(),
                SPORE_BURST_COOLDOWN_TICKS);
        this.applyPackAbilityLockout(24);

        this.sendSporeRing(level, this.position(), 2.4D, 28, ParticleTypes.HAPPY_VILLAGER);
        this.sendSporeRing(level, this.position(), 4.8D, 40, ParticleTypes.WITCH);
        this.sendSporeRing(level, this.position(), SPORE_BURST_RADIUS, 52, ParticleTypes.POOF);

        for (LivingEntity threat : threats) {
            if (threat.hurtServer(level, this.damageSources().mobAttack(this), SPORE_BURST_DAMAGE)) {
                threat.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 8, 0), this);
                threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 0), this);
                Vec3 away = threat.position().subtract(this.position());
                if (away.lengthSqr() > 1.0E-6D) {
                    Vec3 push = away.normalize().scale(0.55D);
                    threat.push(push.x, 0.14D, push.z);
                }
            }
        }

        // Beneficial spores distinguish this from a pure damage AoE.
        for (LivingEntity family : this.findFamily(SPORE_BURST_RADIUS)) {
            family.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0, true, true), this);
            family.removeEffect(MobEffects.POISON);
            family.removeEffect(MobEffects.NAUSEA);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Pack/family safety
    // ---------------------------------------------------------------------

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby()) {
            return false;
        }
        if (target instanceof MushroomWolf mushroom && this.isMushroomPackmate(mushroom)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isMushroomPackmate(MushroomWolf other) {
        if (other == this || other.isTame() != this.isTame()) {
            return false;
        }
        if (!this.isTame()) {
            return true;
        }
        return Objects.equals(this.getOwnerReference(), other.getOwnerReference());
    }

    public boolean isMushroomFamilyMember(LivingEntity entity) {
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
        return entity instanceof MushroomWolf mushroom && this.isMushroomPackmate(mushroom);
    }

    public boolean isValidMushroomCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.canAttack(target) || this.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof MushroomWolf mushroom && this.isMushroomPackmate(mushroom)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return !this.isTame() || owner == null || this.wantsToAttack(target, owner);
    }

    private boolean isMushroomAbilityThreat(LivingEntity candidate, LivingEntity primary) {
        if (candidate instanceof Creeper || !this.isValidMushroomCombatTarget(candidate)) {
            return false;
        }
        if (candidate == primary
                || candidate == this.getTarget()
                || candidate == this.getFamilyDefenseTarget()) {
            return true;
        }

        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get())
                .orElse(null);
        if (candidate == shared) {
            return true;
        }

        return candidate instanceof Enemy;
    }

    private LivingEntity getPrimaryMushroomTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.isValidMushroomCombatTarget(target)) {
            return target;
        }

        LivingEntity familyTarget = this.getFamilyDefenseTarget();
        if (familyTarget != null && familyTarget.isAlive() && this.isValidMushroomCombatTarget(familyTarget)) {
            return familyTarget;
        }

        LivingEntity shared = this.getBrain()
                .getMemory(ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get())
                .orElse(null);
        return shared != null && shared.isAlive() && this.isValidMushroomCombatTarget(shared) ? shared : null;
    }

    private List<LivingEntity> findFamily(double radius) {
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                this::isMushroomFamilyMember);
    }

    private List<LivingEntity> findMushroomThreats(Vec3 center, double radius, LivingEntity primary) {
        AABB area = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return this.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != this
                        && candidate.isAlive()
                        && this.isMushroomAbilityThreat(candidate, primary));
    }

    private ServerPlayer getMushroomOwner() {
        return this.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    private boolean canUseActiveMushroomAbility() {
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

    private void setPackCooldown(MemoryModuleType<Integer> memory, int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.getBrain().setMemory(memory, ticks);
            return;
        }
        for (MushroomWolf mate : level.getEntitiesOfClass(
                MushroomWolf.class,
                this.getBoundingBox().inflate(MushroomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isMushroomPackmate(wolf)))) {
            mate.getBrain().setMemory(memory, ticks);
        }
    }

    private void applyPackAbilityLockout(int ticks) {
        if (!(this.level() instanceof ServerLevel level)) {
            this.abilityLockoutTicks = Math.max(this.abilityLockoutTicks, ticks);
            return;
        }
        for (MushroomWolf mate : level.getEntitiesOfClass(
                MushroomWolf.class,
                this.getBoundingBox().inflate(MushroomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isMushroomPackmate(wolf)))) {
            mate.abilityLockoutTicks = Math.max(mate.abilityLockoutTicks, ticks);
        }
    }

    public void alertPackToThreat(LivingEntity threat) {
        if (!(this.level() instanceof ServerLevel level)
                || threat == null
                || !threat.isAlive()
                || !this.isValidMushroomCombatTarget(threat)) {
            return;
        }
        for (MushroomWolf mate : level.getEntitiesOfClass(
                MushroomWolf.class,
                this.getBoundingBox().inflate(MushroomWolfPackSensor.PACK_SCAN_RADIUS),
                wolf -> wolf.isAlive() && (wolf == this || this.isMushroomPackmate(wolf)))) {
            mate.getBrain().setMemory(ModMemoryModuleTypes.MUSHROOM_PACK_THREAT.get(), threat);
            if (!mate.isBaby() && mate.isValidMushroomCombatTarget(threat)) {
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
            BlockPos target,
            double length,
            int points,
            ParticleOptions particle) {
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 delta = targetCenter.subtract(source);
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

    private void sendSporeRing(
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
                    center.y + 0.20D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.02D, 0.03D, 0.02D, 0.0D);
        }
    }

    public static boolean checkMushroomWolfSpawnRules(
            EntityType<MushroomWolf> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        BlockState ground = level.getBlockState(pos.below());
        boolean fungalGround = ground.is(BlockTags.WOLVES_SPAWNABLE_ON)
                || ground.is(Blocks.MYCELIUM)
                || ground.is(Blocks.MUD)
                || ground.is(Blocks.MUDDY_MANGROVE_ROOTS);
        return fungalGround && isBrightEnoughToSpawn(level, pos);
    }
}
