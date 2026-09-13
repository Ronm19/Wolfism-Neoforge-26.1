package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;

/**
 * War Wolf ♂ — five-star battlefield commander.
 *
 * <p>Identity: Warfare / Tactical / Leadership / Melee.</p>
 *
 * <p>Wolf King decides what the family is doing.
 * War Wolf decides how they win the fight.</p>
 *
 * <p>War Wolf does not replace specialist AI. Battle Command and Call to War
 * mainly repair bad target distribution, rescue idle/lost combatants, highlight
 * urgent threats, and reduce pointless dog-piling. Wolves already handling a
 * valid target normally keep their own species Brain decisions.</p>
 */
public final class WarWolf extends AbstractWolfismWolf {

    private static final double BASE_MAX_HEALTH = 56.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 0.55D;

    private static final double NORMAL_COMMAND_RADIUS = 16.0D;
    private static final double CALL_TO_WAR_RADIUS = 20.0D;
    private static final double NORMAL_ASSIGNMENT_RADIUS = 12.0D;
    private static final double URGENT_ASSIGNMENT_RADIUS = 18.0D;

    private static final int NORMAL_DECISION_INTERVAL = 4;
    private static final int COMMAND_COORDINATION_INTERVAL = 10;
    private static final int ULTIMATE_COORDINATION_INTERVAL = 5;

    public static final int WAR_BITE_COOLDOWN_TICKS = 20 * 5;
    private static final double WAR_BITE_BONUS_DAMAGE = 2.5D;

    private static final int BATTLE_COMMAND_INTENT_TICKS = 20 * 3;

    public static final int RALLY_PACK_COOLDOWN_TICKS = 20 * 30;
    public static final int RALLY_PACK_DURATION_TICKS = 20 * 12;
    private static final double RALLY_PACK_RADIUS = 14.0D;
    private static final double RALLY_KNOCKBACK_BONUS = 0.25D;
    private static final Identifier RALLY_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "war_wolf_rally_knockback");

    public static final int TACTICAL_INTERCEPT_COOLDOWN_TICKS = 20 * 8;
    private static final int TACTICAL_INTERCEPT_DURATION_TICKS = 20 * 3;
    private static final double TACTICAL_INTERCEPT_RADIUS = 18.0D;
    private static final double TACTICAL_INTERCEPT_SPEED = 1.35D;

    private static final int DUELIST_COUNTER_WINDOW_TICKS = 20 * 2;
    private static final double DUELIST_MIN_REPOSITION_RANGE = 3.75D;
    private static final double DUELIST_MAX_REPOSITION_RANGE = 8.0D;

    public static final int CALL_TO_WAR_COOLDOWN_TICKS = 20 * 80;
    public static final int CALL_TO_WAR_DURATION_TICKS = 20 * 20;
    private static final double CALL_TO_WAR_KNOCKBACK_BONUS = 0.35D;
    private static final Identifier CALL_TO_WAR_KNOCKBACK_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    Wolfism.MOD_ID,
                    "war_wolf_call_to_war_knockback");

    private int warBiteCooldownTicks;

    private int battleCommandPrimaryId = -1;
    private int battleCommandIntentTicks;

    private int rallyPackCooldownTicks;
    private int rallyPackTicks;

    private int tacticalInterceptCooldownTicks;
    private int tacticalInterceptTicks;
    private int tacticalInterceptTargetId = -1;
    private int tacticalInterceptProtectedId = -1;

    private int duelistCounterTicks;
    private int duelistTargetId = -1;

    private int callToWarCooldownTicks;
    private int callToWarTicks;

    private final Map<UUID, LivingEntity> rallyRecipients = new HashMap<>();
    private final Map<UUID, LivingEntity> callToWarRecipients = new HashMap<>();

    private static final class BrainHolder {
        private static final Brain.Provider<WarWolf> PROVIDER =
                Brain.<WarWolf>provider(
                        ImmutableList.of(
                                ModSensorTypes.WAR_TACTICAL.get()),
                        wolf -> List.of());
    }

    public WarWolf(
            EntityType<? extends WarWolf> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.WAR_WOLF.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        float previousHealth = this.getHealth();
        maxHealth.setBaseValue(BASE_MAX_HEALTH);

        if (this.isTame()) {
            this.setHealth(this.getMaxHealth());
        } else {
            this.setHealth(Math.min(previousHealth, this.getMaxHealth()));
        }
    }

    @Override
    protected Brain<WarWolf> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<WarWolf> getBrain() {
        return (Brain<WarWolf>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        tickCooldowns();
        tickBattleCommandIntent();
        tickRallyPack(level);
        tickTacticalIntercept(level);
        tickDuelist(level);
        tickCallToWar(level);

        if (this.isBaby()) {
            cancelAdultWarStates();
            this.setTarget(null);
            return;
        }

        if (this.tickCount % NORMAL_DECISION_INTERVAL == 0) {
            tickWarDecisionBrain(level);
        }
    }

    private void tickCooldowns() {
        if (warBiteCooldownTicks > 0) --warBiteCooldownTicks;
        if (rallyPackCooldownTicks > 0) --rallyPackCooldownTicks;
        if (tacticalInterceptCooldownTicks > 0) --tacticalInterceptCooldownTicks;
        if (callToWarCooldownTicks > 0) --callToWarCooldownTicks;
    }

    private void tickBattleCommandIntent() {
        if (battleCommandIntentTicks > 0) {
            --battleCommandIntentTicks;
        }

        if (battleCommandIntentTicks <= 0) {
            battleCommandPrimaryId = -1;
        }
    }

    // =====================================================================
    // Battle Hardened
    // =====================================================================

    private void normalizeCurrentWarTarget() {
        LivingEntity current = this.getTarget();
        if (current == null) return;

        if (!current.isAlive() || !isValidWarThreat(current)) {
            this.setTarget(null);
            this.getNavigation().stop();
            return;
        }

        boolean urgent = isUrgentWarThreat(current);
        if (!urgent
                && this.distanceToSqr(current)
                > URGENT_ASSIGNMENT_RADIUS * URGENT_ASSIGNMENT_RADIUS) {
            this.setTarget(null);
            this.getNavigation().stop();
        }
    }

    // =====================================================================
    // War Bite
    // =====================================================================

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        LivingEntity livingTarget = target instanceof LivingEntity living ? living : null;

        boolean empoweredBite = livingTarget != null
                && !this.isBaby()
                && warBiteCooldownTicks <= 0
                && isValidWarThreat(livingTarget);

        AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double oldBase = attackDamage == null ? 0.0D : attackDamage.getBaseValue();

        if (empoweredBite && attackDamage != null) {
            attackDamage.setBaseValue(oldBase + WAR_BITE_BONUS_DAMAGE);
        }

        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (empoweredBite && attackDamage != null) {
                attackDamage.setBaseValue(oldBase);
            }
        }

        if (!hit
                || this.isBaby()
                || livingTarget == null
                || !isValidWarThreat(livingTarget)) {
            return hit;
        }

        if (empoweredBite) {
            warBiteCooldownTicks = WAR_BITE_COOLDOWN_TICKS;
            WolfVfx.sendParticles("war_wolf", level,
                    ParticleTypes.POOF,
                    livingTarget.getX(),
                    livingTarget.getY() + livingTarget.getBbHeight() * 0.55D,
                    livingTarget.getZ(),
                    7,
                    0.22D, 0.18D, 0.22D,
                    0.02D);
        }

        if (tacticalInterceptTicks > 0
                && livingTarget.getId() == tacticalInterceptTargetId) {
            double dx = this.getX() - livingTarget.getX();
            double dz = this.getZ() - livingTarget.getZ();
            livingTarget.knockback(0.85D, dx, dz);
            endTacticalIntercept();
        }

        return true;
    }

    // =====================================================================
    // Battle Command
    // =====================================================================

    private void updateBattleCommand(ServerLevel level, boolean ultimateIntensity) {
        if (!this.canUseActiveWolfismAbility()) {
            return;
        }

        double radius = ultimateIntensity ? CALL_TO_WAR_RADIUS : NORMAL_COMMAND_RADIUS;
        List<LivingEntity> threats = getNearbyWarThreats(level, radius);
        if (threats.isEmpty()) return;

        threats = threats.stream()
                .sorted(Comparator.comparingDouble(this::warThreatScore))
                .toList();

        LivingEntity primary = threats.getFirst();
        boolean changed = battleCommandPrimaryId != primary.getId();

        battleCommandPrimaryId = primary.getId();
        battleCommandIntentTicks = BATTLE_COMMAND_INTENT_TICKS;

        double ownAllowed = isUrgentWarThreat(primary)
                ? URGENT_ASSIGNMENT_RADIUS
                : NORMAL_ASSIGNMENT_RADIUS;

        if (this.distanceToSqr(primary) <= ownAllowed * ownAllowed) {
            LivingEntity current = this.getTarget();
            if (shouldWarOverrideOwnTarget(current, primary)) {
                this.setTarget(primary);
            }
        }

        if (this.isTame()) {
            coordinateFamilyTactics(level, threats, ultimateIntensity);
        }

        if (changed) {
            WolfVfx.sendParticles("war_wolf", level,
                    ParticleTypes.ENCHANTED_HIT,
                    primary.getX(),
                    primary.getY() + primary.getBbHeight() * 0.70D,
                    primary.getZ(),
                    ultimateIntensity ? 12 : 6,
                    0.30D, 0.32D, 0.30D,
                    0.02D);
        }
    }

    private boolean shouldWarOverrideOwnTarget(LivingEntity current, LivingEntity primary) {
        if (current == primary) return false;
        if (current == null || !current.isAlive() || !isValidWarThreat(current)) return true;
        if (isUrgentWarThreat(primary)) return true;
        if (isUrgentWarThreat(current)) return false;
        return warThreatScore(primary) < warThreatScore(current) - 28.0D;
    }

    private void coordinateFamilyTactics(
            ServerLevel level,
            List<LivingEntity> threats,
            boolean ultimateIntensity) {

        List<Wolf> family = findOperationalFamilyWolves(
                level,
                ultimateIntensity ? CALL_TO_WAR_RADIUS : NORMAL_COMMAND_RADIUS);

        if (family.isEmpty()) return;

        Map<Integer, Integer> assignmentLoad = new HashMap<>();

        for (Wolf ally : family) {
            LivingEntity current = ally.getTarget();
            if (current != null && current.isAlive() && isValidWarThreat(current)) {
                assignmentLoad.merge(current.getId(), 1, Integer::sum);
            }
        }

        for (Wolf ally : family) {
            if (ally == this
                    || ally.isBaby()
                    || ally.isOrderedToSit()
                    || ally.isInSittingPose()) {
                continue;
            }

            LivingEntity current = ally.getTarget();
            if (!shouldWarRedirectFamilyWolf(
                    ally,
                    current,
                    threats,
                    ultimateIntensity)) {
                continue;
            }

            LivingEntity assignment = chooseAssignmentFor(
                    ally,
                    threats,
                    assignmentLoad,
                    ultimateIntensity);

            if (assignment == null) continue;

            ally.setTarget(assignment);
            assignmentLoad.merge(assignment.getId(), 1, Integer::sum);

            double distance = Math.sqrt(ally.distanceToSqr(assignment));
            if (!isRangedWarSpecialist(ally) && distance > 4.0D) {
                ally.getNavigation().moveTo(
                        assignment,
                        ultimateIntensity ? 1.22D : 1.12D);
            }
        }

        if (ultimateIntensity) {
            regroupIdleFamily(family);
        }
    }

    private boolean shouldWarRedirectFamilyWolf(
            Wolf ally,
            LivingEntity current,
            List<LivingEntity> threats,
            boolean ultimateIntensity) {

        if (current == null || !current.isAlive() || !isValidWarThreat(current)) {
            return true;
        }

        if (!ultimateIntensity) {
            return threats.stream()
                    .anyMatch(threat -> isUrgentWarThreat(threat) && current != threat);
        }

        if (isUrgentWarThreat(current)) return false;

        LivingEntity bestUrgent = threats.stream()
                .filter(this::isUrgentWarThreat)
                .findFirst()
                .orElse(null);

        if (bestUrgent != null && current != bestUrgent) {
            return true;
        }

        return ally.distanceToSqr(current)
                > URGENT_ASSIGNMENT_RADIUS * URGENT_ASSIGNMENT_RADIUS;
    }

    private LivingEntity chooseAssignmentFor(
            Wolf ally,
            List<LivingEntity> threats,
            Map<Integer, Integer> assignmentLoad,
            boolean ultimateIntensity) {

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (LivingEntity threat : threats) {
            if (!canFamilyWolfAcceptAssignment(ally, threat)) continue;

            if (isSupportWarSpecialist(ally)
                    && !isUrgentWarThreat(threat)
                    && ally.distanceToSqr(threat) > 6.0D * 6.0D) {
                continue;
            }

            int load = assignmentLoad.getOrDefault(threat.getId(), 0);
            double score = warThreatScore(threat)
                    + load * (ultimateIntensity ? 34.0D : 26.0D)
                    + Math.sqrt(ally.distanceToSqr(threat)) * 0.45D
                    + tacticalRoleBias(ally, threat);

            if (score < bestScore) {
                bestScore = score;
                best = threat;
            }
        }

        return best;
    }

    private boolean canFamilyWolfAcceptAssignment(Wolf ally, LivingEntity target) {
        if (target == null || !target.isAlive() || !ally.canAttack(target)) {
            return false;
        }

        LivingEntity owner = ally.getOwner();
        if (ally.isTame() && owner != null && !ally.wantsToAttack(target, owner)) {
            return false;
        }

        double allowed = isUrgentWarThreat(target)
                ? URGENT_ASSIGNMENT_RADIUS
                : NORMAL_ASSIGNMENT_RADIUS;

        if (isRangedWarSpecialist(ally)) {
            allowed = Math.max(allowed, 16.0D);
        }

        return ally.distanceToSqr(target) <= allowed * allowed;
    }

    private double tacticalRoleBias(Wolf ally, LivingEntity target) {
        String role = ally.getClass().getSimpleName();
        double healthFraction = target.getHealth() / Math.max(1.0F, target.getMaxHealth());

        if (role.contains("Blood") || role.contains("Vampire") || role.contains("Blade")) {
            return healthFraction <= 0.45F ? -24.0D : 8.0D;
        }

        if (role.contains("Infernal")
                || role.contains("Magma")
                || role.contains("Primordial")
                || role.contains("WolfKing")
                || role.contains("WarWolf")) {
            return -Math.min(24.0D, target.getMaxHealth() * 0.20D);
        }

        if (isRangedWarSpecialist(ally)) {
            return isHighStrategicWarTarget(target) ? -20.0D : 0.0D;
        }

        if (isSupportWarSpecialist(ally)) {
            return isUrgentWarThreat(target) ? -30.0D : 18.0D;
        }

        return 0.0D;
    }

    private boolean isSupportWarSpecialist(Wolf ally) {
        String role = ally.getClass().getSimpleName();
        return role.contains("Angel") || role.contains("Spirit") || role.contains("Cherry");
    }

    private boolean isRangedWarSpecialist(Wolf ally) {
        String role = ally.getClass().getSimpleName();
        return role.contains("Phantom")
                || role.contains("Wither")
                || role.contains("Blaze")
                || role.contains("Toxic")
                || role.contains("Storm");
    }

    private void regroupIdleFamily(List<Wolf> family) {
        for (Wolf ally : family) {
            if (ally == this
                    || ally.isBaby()
                    || ally.isOrderedToSit()
                    || ally.isInSittingPose()
                    || ally.getTarget() != null) {
                continue;
            }

            if (ally.distanceToSqr(this) > 10.0D * 10.0D) {
                ally.getNavigation().moveTo(this, 1.16D);
            }
        }
    }

    // =====================================================================
    // Rally Pack
    // =====================================================================

    private boolean startRallyPack(ServerLevel level) {
        if (!this.isTame()
                || this.isBaby()
                || !this.canUseActiveWolfismAbility()
                || rallyPackCooldownTicks > 0
                || rallyPackTicks > 0
                || callToWarTicks > 0) {
            return false;
        }

        List<Wolf> family = findOperationalFamilyWolves(level, RALLY_PACK_RADIUS);
        List<LivingEntity> threats = getNearbyWarThreats(level, RALLY_PACK_RADIUS);

        // Use a fresh authoritative scan here instead of relying on sensor memory.
        // This keeps Rally Pack aligned with its actual 14-block activation radius.
        if (family.size() < 2 || threats.size() < 3) return false;

        rallyPackTicks = RALLY_PACK_DURATION_TICKS;
        rallyPackCooldownTicks = RALLY_PACK_COOLDOWN_TICKS;
        applyRallyPack(level);

        WolfVfx.sendParticles("war_wolf", level,
                ParticleTypes.POOF,
                this.getX(),
                this.getY() + 0.55D,
                this.getZ(),
                28,
                1.3D, 0.55D, 1.3D,
                0.04D);

        return true;
    }

    private void tickRallyPack(ServerLevel level) {
        if (rallyPackTicks <= 0) {
            if (!rallyRecipients.isEmpty()) clearRallyPack();
            return;
        }

        --rallyPackTicks;

        if (callToWarTicks > 0) {
            clearRallyPack();
            return;
        }

        if (this.isWolfismWorkTick(10)) {
            applyRallyPack(level);
        }

        if (rallyPackTicks <= 0) clearRallyPack();
    }

    private void applyRallyPack(ServerLevel level) {
        Map<UUID, LivingEntity> current = new HashMap<>();

        for (Wolf ally : findOperationalFamilyWolves(level, RALLY_PACK_RADIUS)) {
            current.put(ally.getUUID(), ally);

            ally.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH,
                    30,
                    0,
                    true,
                    true), this);

            ally.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    30,
                    0,
                    true,
                    true), this);

            applyTransientModifier(
                    ally,
                    Attributes.KNOCKBACK_RESISTANCE,
                    RALLY_KNOCKBACK_MODIFIER,
                    RALLY_KNOCKBACK_BONUS,
                    AttributeModifier.Operation.ADD_VALUE);
        }

        for (Map.Entry<UUID, LivingEntity> old : rallyRecipients.entrySet()) {
            if (!current.containsKey(old.getKey())) {
                removeTransientModifier(
                        old.getValue(),
                        Attributes.KNOCKBACK_RESISTANCE,
                        RALLY_KNOCKBACK_MODIFIER);
            }
        }

        rallyRecipients.clear();
        rallyRecipients.putAll(current);

        updateBattleCommand(level, false);
    }

    private void clearRallyPack() {
        rallyPackTicks = 0;
        for (LivingEntity recipient : rallyRecipients.values()) {
            removeTransientModifier(
                    recipient,
                    Attributes.KNOCKBACK_RESISTANCE,
                    RALLY_KNOCKBACK_MODIFIER);
        }
        rallyRecipients.clear();
    }

    // =====================================================================
    // Tactical Intercept
    // =====================================================================

    private LivingEntity findInterceptThreat(ServerLevel level) {
        LivingEntity emergency = this.getFamilyDefenseTarget();

        if (emergency != null
                && emergency.isAlive()
                && isValidWarThreat(emergency)
                && this.distanceToSqr(emergency)
                <= TACTICAL_INTERCEPT_RADIUS * TACTICAL_INTERCEPT_RADIUS) {
            return emergency;
        }

        return getNearbyWarThreats(level, TACTICAL_INTERCEPT_RADIUS)
                .stream()
                .filter(this::isThreateningWarFamily)
                .min(Comparator.comparingDouble(this::warThreatScore))
                .orElse(null);
    }

    private boolean startTacticalIntercept(ServerLevel level, LivingEntity threat) {
        if (!this.isTame()
                || this.isBaby()
                || !this.canUseActiveWolfismAbility()
                || tacticalInterceptCooldownTicks > 0
                || tacticalInterceptTicks > 0
                || threat == null
                || !threat.isAlive()
                || !isValidWarThreat(threat)
                || this.distanceToSqr(threat)
                > TACTICAL_INTERCEPT_RADIUS * TACTICAL_INTERCEPT_RADIUS) {
            return false;
        }

        LivingEntity protectedMember = getThreatenedWarFamilyMember(threat);
        if (protectedMember == null) {
            protectedMember = this.getProtectedFamilyMember();
        }

        tacticalInterceptTargetId = threat.getId();
        tacticalInterceptProtectedId = protectedMember == null ? -1 : protectedMember.getId();
        tacticalInterceptTicks = TACTICAL_INTERCEPT_DURATION_TICKS;
        tacticalInterceptCooldownTicks = TACTICAL_INTERCEPT_COOLDOWN_TICKS;

        this.setTarget(threat);
        this.getNavigation().moveTo(threat, TACTICAL_INTERCEPT_SPEED);
        this.addEffect(new MobEffectInstance(
                MobEffects.SPEED,
                35,
                1,
                true,
                false), this);

        WolfVfx.sendParticles("war_wolf", level,
                ParticleTypes.POOF,
                this.getX(),
                this.getY() + 0.35D,
                this.getZ(),
                12,
                0.45D, 0.20D, 0.45D,
                0.03D);

        return true;
    }

    private void tickTacticalIntercept(ServerLevel level) {
        if (tacticalInterceptTicks <= 0) return;

        --tacticalInterceptTicks;

        LivingEntity threat = getLivingEntityById(level, tacticalInterceptTargetId);
        LivingEntity protectedMember = getLivingEntityById(level, tacticalInterceptProtectedId);

        if (threat == null || !threat.isAlive() || !isValidWarThreat(threat)) {
            endTacticalIntercept();
            return;
        }

        if (protectedMember != null
                && threat.distanceToSqr(protectedMember)
                > TACTICAL_INTERCEPT_RADIUS * TACTICAL_INTERCEPT_RADIUS
                && !isThreateningWarFamily(threat)) {
            endTacticalIntercept();
            return;
        }

        this.setTarget(threat);

        if (this.tickCount % 5 == 0) {
            this.getNavigation().moveTo(threat, TACTICAL_INTERCEPT_SPEED);
            this.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    15,
                    1,
                    true,
                    false), this);
        }

        if (tacticalInterceptTicks <= 0) endTacticalIntercept();
    }

    private void endTacticalIntercept() {
        tacticalInterceptTicks = 0;
        tacticalInterceptTargetId = -1;
        tacticalInterceptProtectedId = -1;
    }

    // =====================================================================
    // Duelist
    // =====================================================================

    private void tickDuelist(ServerLevel level) {
        if (duelistCounterTicks > 0) {
            --duelistCounterTicks;
        } else {
            duelistTargetId = -1;
        }

        LivingEntity target = this.getTarget();
        if (target == null
                || !target.isAlive()
                || !isSignificantWarTarget(target)
                || this.hasFamilyDefenseEmergency()
                || getNearbyWarThreats(level, 8.0D).size() > 2) {
            return;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < DUELIST_MIN_REPOSITION_RANGE * DUELIST_MIN_REPOSITION_RANGE
                || distanceSqr > DUELIST_MAX_REPOSITION_RANGE * DUELIST_MAX_REPOSITION_RANGE
                || !this.getSensing().hasLineOfSight(target)
                || !this.isWolfismWorkTick(20)) {
            return;
        }

        Vec3 toTarget = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (toTarget.lengthSqr() < 1.0E-5D) return;
        toTarget = toTarget.normalize();

        Vec3 side = new Vec3(-toTarget.z, 0.0D, toTarget.x)
                .scale(this.getRandom().nextBoolean() ? 2.2D : -2.2D);

        Vec3 flank = target.position()
                .add(side)
                .subtract(toTarget.scale(1.7D));

        this.getNavigation().moveTo(
                flank.x,
                flank.y,
                flank.z,
                1.14D);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || this.isBaby()) return hurt;

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker
                && livingAttacker.isAlive()
                && isValidWarThreat(livingAttacker)
                && isSignificantWarTarget(livingAttacker)
                && this.distanceToSqr(livingAttacker)
                <= NORMAL_ASSIGNMENT_RADIUS * NORMAL_ASSIGNMENT_RADIUS) {

            duelistCounterTicks = DUELIST_COUNTER_WINDOW_TICKS;
            duelistTargetId = livingAttacker.getId();
            warBiteCooldownTicks = Math.min(warBiteCooldownTicks, 20);

            if (!this.hasFamilyDefenseEmergency()) {
                this.setTarget(livingAttacker);
            }
        }

        return true;
    }

    // =====================================================================
    // Call to War — Ultimate
    // =====================================================================

    private boolean startCallToWar(ServerLevel level) {
        if (!this.isTame()
                || this.isBaby()
                || !this.canUseActiveWolfismAbility()
                || callToWarCooldownTicks > 0
                || callToWarTicks > 0) {
            return false;
        }

        List<Wolf> family = findOperationalFamilyWolves(level, CALL_TO_WAR_RADIUS);
        List<LivingEntity> threats = getNearbyWarThreats(level, CALL_TO_WAR_RADIUS);

        // Call to War owns its activation scan. Do not gate this ultimate through
        // WAR_HOSTILE_COUNT, whose tactical sensor intentionally uses a tighter
        // 12-block local-combat radius.
        if (family.size() < 3 || threats.size() < 5) return false;

        if (rallyPackTicks > 0) clearRallyPack();

        callToWarTicks = CALL_TO_WAR_DURATION_TICKS;
        callToWarCooldownTicks = CALL_TO_WAR_COOLDOWN_TICKS;

        WolfVfx.sendParticles("war_wolf", level,
                ParticleTypes.ENCHANTED_HIT,
                this.getX(),
                this.getY() + 0.65D,
                this.getZ(),
                52,
                2.2D, 0.75D, 2.2D,
                0.05D);

        applyCallToWar(level);
        return true;
    }

    private void tickCallToWar(ServerLevel level) {
        if (callToWarTicks <= 0) {
            if (!callToWarRecipients.isEmpty()) clearCallToWar();
            return;
        }

        --callToWarTicks;

        if (this.isWolfismWorkTick(10)) {
            applyCallToWar(level);
        }

        if (this.tickCount % ULTIMATE_COORDINATION_INTERVAL == 0) {
            updateBattleCommand(level, true);
        }

        if (this.isWolfismWorkTick(20)) {
            WolfVfx.sendParticles("war_wolf", level,
                    ParticleTypes.ENCHANTED_HIT,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    9,
                    0.95D, 0.28D, 0.95D,
                    0.015D);
        }

        if (callToWarTicks <= 0) clearCallToWar();
    }

    private void applyCallToWar(ServerLevel level) {
        Map<UUID, LivingEntity> current = new HashMap<>();

        for (Wolf ally : findOperationalFamilyWolves(level, CALL_TO_WAR_RADIUS)) {
            current.put(ally.getUUID(), ally);

            ally.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH,
                    30,
                    0,
                    true,
                    true), this);

            ally.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    30,
                    0,
                    true,
                    true), this);

            ally.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE,
                    30,
                    0,
                    true,
                    true), this);

            applyTransientModifier(
                    ally,
                    Attributes.KNOCKBACK_RESISTANCE,
                    CALL_TO_WAR_KNOCKBACK_MODIFIER,
                    CALL_TO_WAR_KNOCKBACK_BONUS,
                    AttributeModifier.Operation.ADD_VALUE);
        }

        for (Map.Entry<UUID, LivingEntity> old : callToWarRecipients.entrySet()) {
            if (!current.containsKey(old.getKey())) {
                removeTransientModifier(
                        old.getValue(),
                        Attributes.KNOCKBACK_RESISTANCE,
                        CALL_TO_WAR_KNOCKBACK_MODIFIER);
            }
        }

        callToWarRecipients.clear();
        callToWarRecipients.putAll(current);
    }

    private void clearCallToWar() {
        callToWarTicks = 0;
        for (LivingEntity recipient : callToWarRecipients.values()) {
            removeTransientModifier(
                    recipient,
                    Attributes.KNOCKBACK_RESISTANCE,
                    CALL_TO_WAR_KNOCKBACK_MODIFIER);
        }
        callToWarRecipients.clear();
    }

    // =====================================================================
    // Brain decision policy
    // =====================================================================

    private void tickWarDecisionBrain(ServerLevel level) {
        normalizeCurrentWarTarget();

        LivingEntity sensorPrimary = this.getBrain()
                .getMemory(ModMemoryModuleTypes.WAR_PRIMARY_TARGET.get())
                .orElse(null);

        LivingEntity intercept = findInterceptThreat(level);
        if (intercept != null
                && tacticalInterceptCooldownTicks <= 0
                && tacticalInterceptTicks <= 0) {
            startTacticalIntercept(level, intercept);
        }

        if (callToWarTicks <= 0
                && callToWarCooldownTicks <= 0
                && startCallToWar(level)) {
            return;
        }

        if (callToWarTicks <= 0
                && rallyPackTicks <= 0
                && rallyPackCooldownTicks <= 0) {
            startRallyPack(level);
        }

        if (callToWarTicks <= 0
                && this.tickCount % COMMAND_COORDINATION_INTERVAL == 0) {
            updateBattleCommand(level, false);
        }

        if (sensorPrimary != null
                && sensorPrimary.isAlive()
                && isValidWarThreat(sensorPrimary)
                && isUrgentWarThreat(sensorPrimary)
                && this.distanceToSqr(sensorPrimary)
                <= URGENT_ASSIGNMENT_RADIUS * URGENT_ASSIGNMENT_RADIUS) {
            this.setTarget(sensorPrimary);
        }
    }

    @Override
    protected void onFamilyDefenseStarted(
            LivingEntity attacker,
            LivingEntity protectedFamily) {

        if (this.isBaby() || attacker == null || !attacker.isAlive()) return;

        battleCommandPrimaryId = attacker.getId();
        battleCommandIntentTicks = BATTLE_COMMAND_INTENT_TICKS;

        if (this.level() instanceof ServerLevel level
                && tacticalInterceptCooldownTicks <= 0
                && tacticalInterceptTicks <= 0) {
            startTacticalIntercept(level, attacker);
        }
    }

    // =====================================================================
    // Family / threat helpers
    // =====================================================================

    public boolean isWarFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this) return true;

        if (this.isTame()) {
            return this.isWolfismFamily(entity);
        }

        return entity instanceof WarWolf war && !war.isTame();
    }

    public boolean isValidWarThreat(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || isWarFamilyMember(target)
                || this.isAlliedTo(target)
                || (!(target instanceof Enemy) && !isBossWarTarget(target))) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return !this.isTame()
                || owner == null
                || this.wantsToAttack(target, owner);
    }

    public boolean isVulnerableWarFamilyMember(LivingEntity member) {
        if (!isWarFamilyMember(member) || member == this) return false;
        return member.getHealth() <= member.getMaxHealth() * 0.45F;
    }

    /**
     * Illager Warfare Knowledge: lower score = more strategically important.
     */
    public double warThreatScore(LivingEntity target) {
        double distance = Math.sqrt(this.distanceToSqr(target));
        double healthWeight = Math.min(22.0D, target.getMaxHealth() * 0.12D);
        double score = distance - healthWeight;

        if (target == this.getFamilyDefenseTarget()) score -= 180.0D;
        if (isThreateningWarFamily(target)) score -= 140.0D;

        String typeName = target.getClass().getSimpleName();

        if ("Evoker".equals(typeName)) {
            score -= 92.0D;
        } else if (target instanceof Ravager) {
            score -= 72.0D;
        } else if ("Vindicator".equals(typeName)) {
            score -= 48.0D;
        } else if ("Pillager".equals(typeName)) {
            score -= 36.0D;
        } else if ("Illusioner".equals(typeName)) {
            score -= 52.0D;
        } else if ("Witch".equals(typeName)) {
            score -= 26.0D;
        }

        if (isSignificantWarTarget(target)) score -= 12.0D;
        return score;
    }

    private boolean isHighStrategicWarTarget(LivingEntity target) {
        String typeName = target.getClass().getSimpleName();
        return "Evoker".equals(typeName)
                || "Pillager".equals(typeName)
                || "Illusioner".equals(typeName)
                || target instanceof Ravager
                || isUrgentWarThreat(target);
    }

    private boolean isSignificantWarTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;

        double armor = target.getAttributeValue(Attributes.ARMOR);
        String typeName = target.getClass().getSimpleName();

        return target.getMaxHealth() >= 36.0F
                || armor >= 6.0D
                || target instanceof Ravager
                || target instanceof WitherBoss
                || target instanceof EnderDragon
                || "Evoker".equals(typeName)
                || "Vindicator".equals(typeName);
    }

    private boolean isUrgentWarThreat(LivingEntity target) {
        return target != null
                && target.isAlive()
                && (target == this.getFamilyDefenseTarget()
                || isThreateningWarFamily(target));
    }

    private boolean isThreateningWarFamily(LivingEntity target) {
        if (!(target instanceof Mob mob)) return false;
        LivingEntity mobTarget = mob.getTarget();
        return mobTarget != null && isWarFamilyMember(mobTarget);
    }

    private LivingEntity getThreatenedWarFamilyMember(LivingEntity threat) {
        if (!(threat instanceof Mob mob)) return null;
        LivingEntity mobTarget = mob.getTarget();
        return mobTarget != null && isWarFamilyMember(mobTarget) ? mobTarget : null;
    }

    private boolean isBossWarTarget(LivingEntity target) {
        return target instanceof WitherBoss || target instanceof EnderDragon;
    }

    public List<LivingEntity> getNearbyWarFamily(ServerLevel level, double radius) {
        double radiusSqr = radius * radius;
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isWarFamilyMember)
                .stream()
                .filter(member -> this.distanceToSqr(member) <= radiusSqr)
                .toList();
    }

    public List<LivingEntity> getNearbyWarThreats(ServerLevel level, double radius) {
        double radiusSqr = radius * radius;
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(radius),
                        this::isValidWarThreat)
                .stream()
                .filter(enemy -> this.distanceToSqr(enemy) <= radiusSqr)
                .toList();
    }

    private List<Wolf> findOperationalFamilyWolves(ServerLevel level, double radius) {
        if (!this.isTame()) return List.<Wolf>of(this);

        LivingEntity owner = this.getOwner();
        if (owner == null) return List.<Wolf>of(this);

        double radiusSqr = radius * radius;
        List<Wolf> result = new ArrayList<>();

        for (Wolf wolf : level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive()
                        && candidate.isTame()
                        && candidate.isOwnedBy(owner)
                        && !candidate.isBaby()
                        && !candidate.isOrderedToSit()
                        && !candidate.isInSittingPose())) {

            if (this.distanceToSqr(wolf) <= radiusSqr) {
                result.add(wolf);
            }
        }

        if (!result.contains(this)
                && this.isAlive()
                && !this.isBaby()
                && !this.isOrderedToSit()
                && !this.isInSittingPose()) {
            result.add(this);
        }
        return result;
    }

    private LivingEntity getLivingEntityById(ServerLevel level, int id) {
        if (id < 0) return null;
        return level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public boolean isWarCombatVisualActive() {
        return rallyPackTicks > 0
                || tacticalInterceptTicks > 0
                || duelistCounterTicks > 0
                || callToWarTicks > 0
                || battleCommandIntentTicks > 0;
    }

    // =====================================================================
    // Transient attribute helpers
    // =====================================================================

    private void applyTransientModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation) {

        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, operation));
    }

    private void removeTransientModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier id) {

        if (entity == null) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    // =====================================================================
    // Natural spawning
    // =====================================================================

    public static boolean checkWarWolfSpawnRules(
            EntityType<WarWolf> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(pos)
                .getCollisionShape(level, pos)
                .isEmpty()
                || !level.getBlockState(pos.above())
                .getCollisionShape(level, pos.above())
                .isEmpty()) {
            return false;
        }

        BlockPos floor = pos.below();
        if (!level.getBlockState(floor)
                .isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildWar = !serverLevel.getEntitiesOfClass(
                            WarWolf.class,
                            new AABB(pos).inflate(64.0D, 32.0D, 64.0D),
                            wolf -> wolf.isAlive() && !wolf.isTame())
                    .isEmpty();

            if (anotherWildWar) return false;
        }

        return true;
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt("WarBiteCooldown", warBiteCooldownTicks);
        output.putInt("WarRallyPackCooldown", rallyPackCooldownTicks);
        output.putInt("WarTacticalInterceptCooldown", tacticalInterceptCooldownTicks);
        output.putInt("WarCallToWarCooldown", callToWarCooldownTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        warBiteCooldownTicks = Math.max(
                0,
                input.getIntOr("WarBiteCooldown", 0));

        rallyPackCooldownTicks = Math.max(
                0,
                input.getIntOr("WarRallyPackCooldown", 0));

        tacticalInterceptCooldownTicks = Math.max(
                0,
                input.getIntOr("WarTacticalInterceptCooldown", 0));

        callToWarCooldownTicks = Math.max(
                0,
                input.getIntOr("WarCallToWarCooldown", 0));

        cancelTemporaryWarStatesAfterLoad();
    }

    private void cancelTemporaryWarStatesAfterLoad() {
        battleCommandPrimaryId = -1;
        battleCommandIntentTicks = 0;

        rallyPackTicks = 0;
        rallyRecipients.clear();

        tacticalInterceptTicks = 0;
        tacticalInterceptTargetId = -1;
        tacticalInterceptProtectedId = -1;

        duelistCounterTicks = 0;
        duelistTargetId = -1;

        callToWarTicks = 0;
        callToWarRecipients.clear();
    }

    private void cancelAdultWarStates() {
        battleCommandPrimaryId = -1;
        battleCommandIntentTicks = 0;

        if (rallyPackTicks > 0 || !rallyRecipients.isEmpty()) {
            clearRallyPack();
        }

        endTacticalIntercept();

        duelistCounterTicks = 0;
        duelistTargetId = -1;

        if (callToWarTicks > 0 || !callToWarRecipients.isEmpty()) {
            clearCallToWar();
        }
    }
}
