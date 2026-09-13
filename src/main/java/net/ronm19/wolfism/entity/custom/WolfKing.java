package net.ronm19.wolfism.entity.custom;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.registry.ModSounds;

/**
 * Wolfism #38 — Wolf King ♂.
 *
 * <p>The Alpha-King / apex leader of the pack.</p>
 *
 * <p>Wolf King is a Brain-driven frontline alpha: physically the strongest
 * Wolfism wolf, but still defined by leadership rather than boss gimmicks.
 * Wild Kings can gather untamed wolves into a temporary Alpha Hunt when the
 * command howl is issued. Tamed Kings become the owner's dominant field leader,
 * redirecting free packmates toward the threats that matter while explicit
 * sit/stay orders remain authoritative.</p>
 *
 * <p>Ability contract:</p>
 * <ul>
 *     <li><b>King's Presence</b> — passive nearby wolf damage/speed aura.</li>
 *     <li><b>Pack Leader</b> — passive target-priority / family-defense coordination.</li>
 *     <li><b>Royal Command</b> — Strength + Resistance rally, 20s cooldown.</li>
 *     <li><b>Commanding Howl</b> — fear/Weakness pressure, 25s cooldown.</li>
 *     <li><b>Iron Will</b> — heavy knockback resistance / formation stability, 30s cooldown.</li>
 *     <li><b>Leadership Dash</b> — rushes and marks a priority target, 18s cooldown.</li>
 *     <li><b>Alpha Hunt</b> — wild howl recruits nearby untamed wolves into the hunt.</li>
 *     <li><b>Alpha Command</b> — tamed King assertively leads his owner's free wolf pack.</li>
 * </ul>
 *
 * <p>Natural Wolf Kings are uncommon Alpha encounters in taiga/grove wolf
 * territory. They spawn alone and recruit the local wild wolf population
 * organically through Alpha Hunt rather than receiving artificial escorts.</p>
 */
public final class WolfKing extends AbstractWolfismWolf {

    // ---------------------------------------------------------------------
    // Base balance
    // ---------------------------------------------------------------------

    private static final double KING_MAX_HEALTH = 60.0D;
    private static final double KING_ATTACK_DAMAGE = 9.0D;
    private static final double KING_MOVE_SPEED = 0.33D;
    private static final double KING_ARMOR = 7.0D;
    private static final double KING_KNOCKBACK_RESISTANCE = 0.40D;
    private static final double KING_FOLLOW_RANGE = 56.0D;

    // ---------------------------------------------------------------------
    // King's Presence — passive aura
    // ---------------------------------------------------------------------

    public static final double KINGS_PRESENCE_RADIUS = 10.0D;
    private static final int KINGS_PRESENCE_REFRESH_INTERVAL = 10;
    private static final double KINGS_PRESENCE_DAMAGE_BONUS = 0.12D;
    private static final double KINGS_PRESENCE_SPEED_BONUS = 0.08D;

    private static final Identifier KINGS_PRESENCE_DAMAGE_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "wolf_king_presence_damage");

    private static final Identifier KINGS_PRESENCE_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "wolf_king_presence_speed");

    // ---------------------------------------------------------------------
    // Royal Command
    // ---------------------------------------------------------------------

    public static final double ROYAL_COMMAND_RADIUS = 14.0D;
    public static final int ROYAL_COMMAND_DURATION_TICKS = 20 * 8;
    public static final int ROYAL_COMMAND_COOLDOWN_TICKS = 20 * 20;

    // ---------------------------------------------------------------------
    // Commanding Howl
    // ---------------------------------------------------------------------

    public static final double COMMANDING_HOWL_RADIUS = 12.0D;
    public static final int COMMANDING_HOWL_WEAKNESS_TICKS = 20 * 7;
    public static final int COMMANDING_HOWL_HESITATION_TICKS = 20 * 2;
    public static final int COMMANDING_HOWL_COOLDOWN_TICKS = 20 * 25;

    // ---------------------------------------------------------------------
    // Alpha Hunt / Alpha Command
    // ---------------------------------------------------------------------

    /** Wild wolves inside this radius can answer the King's hunting howl. */
    public static final double ALPHA_HOWL_RECRUIT_RADIUS = 40.0D;
    public static final int ALPHA_HUNT_DURATION_TICKS = 20 * 40;
    private static final double ALPHA_HUNT_RELEASE_RADIUS = 72.0D;
    private static final double ALPHA_FOLLOW_START_DISTANCE = 14.0D;
    private static final double ALPHA_HOWL_RALLY_DISTANCE = 7.0D;
    private static final double ALPHA_FOLLOW_SPEED = 1.30D;
    private static final int ALPHA_HUNT_UPDATE_INTERVAL = 5;
    private static final int ALPHA_HOWL_RALLY_TICKS = 20 * 2 + 10;
    private static final double ALPHA_HUNT_TARGET_SWITCH_MARGIN = 80.0D;

    /** Tamed Alpha-King only commands wolves belonging to the same owner. */
    private static final double TAMED_ALPHA_RALLY_DISTANCE = 12.0D;
    private static final double TAMED_ALPHA_RALLY_SPEED = 1.20D;
    private static final double ALPHA_OVERRIDE_SCORE_MARGIN = 120.0D;

    // ---------------------------------------------------------------------
    // Iron Will
    // ---------------------------------------------------------------------

    public static final double IRON_WILL_RADIUS = 14.0D;
    public static final int IRON_WILL_DURATION_TICKS = 20 * 10;
    public static final int IRON_WILL_COOLDOWN_TICKS = 20 * 30;
    private static final double IRON_WILL_KNOCKBACK_RESISTANCE = 0.75D;

    private static final Identifier IRON_WILL_MODIFIER =
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "wolf_king_iron_will");

    // ---------------------------------------------------------------------
    // Leadership Dash / mark
    // ---------------------------------------------------------------------

    public static final int LEADERSHIP_DASH_COOLDOWN_TICKS = 20 * 18;
    public static final int LEADERSHIP_MARK_DURATION_TICKS = 20 * 8;
    private static final int LEADERSHIP_DASH_DURATION_TICKS = 14;
    private static final double LEADERSHIP_DASH_SPEED = 0.95D;
    private static final double LEADERSHIP_DASH_MIN_RANGE = 5.0D;
    private static final double LEADERSHIP_DASH_MAX_RANGE = 18.0D;
    private static final double LEADERSHIP_DASH_CONTACT_RANGE = 2.2D;

    // ---------------------------------------------------------------------
    // Brain / decision tuning
    // ---------------------------------------------------------------------

    public static final double PACK_SCAN_RADIUS = 30.0D;
    public static final double THREAT_SCAN_RADIUS = 34.0D;
    private static final double PACK_COORDINATION_RADIUS = 24.0D;
    private static final int DECISION_INTERVAL = 5;
    private static final int MAJOR_ABILITY_LOCKOUT_TICKS = 30;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private final Map<UUID, LivingEntity> kingsPresenceRecipients = new HashMap<>();
    private final Map<UUID, LivingEntity> ironWillRecipients = new HashMap<>();

    /** Runtime membership for wild wolves that answered the Alpha Hunt howl. */
    private final Map<UUID, Wolf> alphaHuntFollowers = new HashMap<>();

    private int alphaHuntTicks;
    private int alphaHowlRallyTicks;
    private int ironWillTicks;
    private int leadershipDashTicks;
    private int leadershipMarkTicks;
    private int majorAbilityLockoutTicks;

    private Vec3 leadershipDashDirection = Vec3.ZERO;

    private static final class BrainHolder {
        private static final Brain.Provider<WolfKing> PROVIDER =
                Brain.<WolfKing>provider(
                        List.of(
                                ModSensorTypes.WOLF_KING_PACK.get(),
                                ModSensorTypes.WOLF_KING_THREAT.get()),
                        wolf -> List.of());
    }

    public WolfKing(
            EntityType<? extends WolfKing> type,
            Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------
    // Foundation
    // ---------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, KING_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, KING_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, KING_MOVE_SPEED)
                .add(Attributes.ARMOR, KING_ARMOR)
                .add(Attributes.FOLLOW_RANGE, KING_FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KING_KNOCKBACK_RESISTANCE);
    }

    @Override
    protected EntityType<? extends AbstractWolfismWolf> wolfismEntityType() {
        return ModEntities.WOLF_KING.get();
    }

    @Override
    protected Brain<WolfKing> makeBrain(Brain.Packed packedBrain) {
        return BrainHolder.PROVIDER.makeBrain(this, packedBrain);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<WolfKing> getBrain() {
        return (Brain<WolfKing>) super.getBrain();
    }

    @Override
    protected void applyTamingSideEffects() {
        /*
         * Alpha-King owns a fixed apex statline. Keep it identical on both
         * sides of taming so becoming bonded never turns the strongest wolf
         * back into vanilla-wolf combat numbers.
         */
        this.restoreAlphaBaseAttribute(Attributes.MAX_HEALTH, KING_MAX_HEALTH);
        this.restoreAlphaBaseAttribute(Attributes.ATTACK_DAMAGE, KING_ATTACK_DAMAGE);
        this.restoreAlphaBaseAttribute(Attributes.MOVEMENT_SPEED, KING_MOVE_SPEED);
        this.restoreAlphaBaseAttribute(Attributes.ARMOR, KING_ARMOR);
        this.restoreAlphaBaseAttribute(Attributes.FOLLOW_RANGE, KING_FOLLOW_RANGE);
        this.restoreAlphaBaseAttribute(Attributes.KNOCKBACK_RESISTANCE, KING_KNOCKBACK_RESISTANCE);

        if (this.isTame()) {
            this.setHealth((float) KING_MAX_HEALTH);
        }
    }

    private void restoreAlphaBaseAttribute(
            Holder<Attribute> attribute,
            double value) {

        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isBaby() || this.isOrderedToSit()) {
            return false;
        }

        if (target instanceof Wolf wolf && this.isWolfKingPackAlly(wolf)) {
            return false;
        }

        return !this.isWolfKingFamilyMember(target)
                && super.canAttack(target);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.clearKingsPresence();
        this.clearIronWill();
        this.clearWildAlphaHunt();
        this.cancelLeadershipDash();
        super.remove(reason);
    }

    // ---------------------------------------------------------------------
    // Server Brain loop
    // ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        super.customServerAiStep(level);

        this.tickCooldown(ModMemoryModuleTypes.WOLF_KING_ROYAL_COMMAND_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.WOLF_KING_COMMANDING_HOWL_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.WOLF_KING_IRON_WILL_COOLDOWN.get());
        this.tickCooldown(ModMemoryModuleTypes.WOLF_KING_LEADERSHIP_DASH_COOLDOWN.get());

        if (this.majorAbilityLockoutTicks > 0) {
            --this.majorAbilityLockoutTicks;
        }

        if (this.isBaby()) {
            this.clearAdultKingState();
            return;
        }

        if (this.isTame()) {
            // Taming ends any temporary wild-alpha hierarchy immediately.
            if (!this.alphaHuntFollowers.isEmpty() || this.alphaHuntTicks > 0) {
                this.clearWildAlphaHunt();
            }
        } else if (this.alphaHuntTicks > 0) {
            this.tickWildAlphaHunt(level);
        }

        if (this.tickCount % KINGS_PRESENCE_REFRESH_INTERVAL == 0) {
            this.refreshKingsPresence(level);
        }

        if (this.ironWillTicks > 0) {
            this.tickIronWill(level);
        }

        if (this.leadershipMarkTicks > 0) {
            this.tickLeadershipMark(level);
        }

        if (this.leadershipDashTicks > 0) {
            this.tickLeadershipDash(level);
            return;
        }

        if (this.isOrderedToSit() || this.isInSittingPose()) {
            return;
        }

        if (this.tickCount % DECISION_INTERVAL != 0) {
            return;
        }

        LivingEntity priority = this.getRoyalPriorityTarget();
        if (this.isPotentialRoyalThreat(priority)) {
            this.adoptRoyalPriorityTarget(priority);
            this.coordinatePackTarget(level, priority, false);

            if (this.isTame()) {
                this.rallyTamedAlphaPack(level);
            }
        }

        if (this.majorAbilityLockoutTicks > 0) {
            return;
        }

        if (this.tryCommandingHowl(level, priority)) {
            return;
        }

        if (this.tryIronWill(level, priority)) {
            return;
        }

        if (this.tryRoyalCommand(level, priority)) {
            return;
        }

        this.tryLeadershipDash(level, priority);
    }

    private void clearAdultKingState() {
        this.setTarget(null);
        this.clearKingsPresence();
        this.clearIronWill();
        this.clearWildAlphaHunt();
        this.cancelLeadershipDash();
        this.leadershipMarkTicks = 0;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.WOLF_KING_MARKED_TARGET.get());
    }

    // ---------------------------------------------------------------------
    // King's Presence — passive aura
    // ---------------------------------------------------------------------

    private void refreshKingsPresence(ServerLevel level) {
        Map<UUID, LivingEntity> current = new HashMap<>();

        for (Wolf ally : this.findOperationalPackWolves(level, KINGS_PRESENCE_RADIUS)) {
            if (ally == this || !ally.isAlive()) {
                continue;
            }

            current.put(ally.getUUID(), ally);

            this.applyTransientModifier(
                    ally,
                    Attributes.ATTACK_DAMAGE,
                    KINGS_PRESENCE_DAMAGE_MODIFIER,
                    KINGS_PRESENCE_DAMAGE_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

            this.applyTransientModifier(
                    ally,
                    Attributes.MOVEMENT_SPEED,
                    KINGS_PRESENCE_SPEED_MODIFIER,
                    KINGS_PRESENCE_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        for (Map.Entry<UUID, LivingEntity> entry : this.kingsPresenceRecipients.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                this.removeKingsPresenceModifiers(entry.getValue());
            }
        }

        this.kingsPresenceRecipients.clear();
        this.kingsPresenceRecipients.putAll(current);

        if (!current.isEmpty() && this.isWolfismWorkTick(20)) {
            WolfVfx.sendParticles("wolf_king", level,
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY() + 0.55D,
                    this.getZ(),
                    3,
                    0.55D, 0.22D, 0.55D,
                    0.006D);
        }
    }

    private void clearKingsPresence() {
        for (LivingEntity recipient : this.kingsPresenceRecipients.values()) {
            this.removeKingsPresenceModifiers(recipient);
        }
        this.kingsPresenceRecipients.clear();
    }

    private void removeKingsPresenceModifiers(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        this.removeTransientModifier(
                entity,
                Attributes.ATTACK_DAMAGE,
                KINGS_PRESENCE_DAMAGE_MODIFIER);

        this.removeTransientModifier(
                entity,
                Attributes.MOVEMENT_SPEED,
                KINGS_PRESENCE_SPEED_MODIFIER);
    }

    // ---------------------------------------------------------------------
    // Pack Leader — passive coordination
    // ---------------------------------------------------------------------

    private void adoptRoyalPriorityTarget(LivingEntity priority) {
        LivingEntity current = this.getTarget();

        if (current == null
                || !current.isAlive()
                || !this.isPotentialRoyalThreat(current)
                || this.scoreRoyalThreat(priority) > this.scoreRoyalThreat(current) + 30.0D) {
            this.setTarget(priority);
        }
    }

    private void coordinatePackTarget(
            ServerLevel level,
            LivingEntity target,
            boolean forceMarkedTarget) {

        if (!this.isPotentialRoyalThreat(target)) {
            return;
        }

        LivingEntity protectedFamily = this.getProtectedMemberForThreat(target);

        for (Wolf ally : this.findOperationalPackWolves(level, PACK_COORDINATION_RADIUS)) {
            if (ally == this
                    || !ally.isAlive()
                    || ally.isBaby()
                    || ally.isOrderedToSit()
                    || ally.isInSittingPose()) {
                continue;
            }

            /*
             * Wild Alpha Hunt has a strict hierarchy: formation before prey.
             * V2 could issue moveTo(King) in tickWildAlphaHunt and then undo it
             * here in the same Brain cycle by assigning the prey again. Keep a
             * distant/rallying wild follower on the Alpha until formation is
             * actually closed. Tamed command behavior is unchanged.
             */
            if (!this.isTame()
                    && this.isActiveAlphaFollower(ally)
                    && (this.alphaHowlRallyTicks > 0
                    || this.distanceToSqr(ally)
                    > ALPHA_FOLLOW_START_DISTANCE * ALPHA_FOLLOW_START_DISTANCE)) {
                ally.setTarget(null);
                ally.getNavigation().moveTo(this, ALPHA_FOLLOW_SPEED);
                continue;
            }

            if (!ally.canAttack(target)) {
                continue;
            }

            LivingEntity current = ally.getTarget();

            /*
             * Use Wolfism's existing family-defense contract whenever the King
             * is responding to an actual family emergency. That lets each
             * species keep its own emergency logic instead of bypassing it.
             */
            if (protectedFamily != null
                    && ally instanceof AbstractWolfismWolf wolfismAlly
                    && wolfismAlly.beginFamilyDefense(target, protectedFamily)) {
                continue;
            }

            if (this.shouldAlphaOverrideTarget(ally, current, target, forceMarkedTarget)) {
                ally.setTarget(target);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Royal Command — Strength + Resistance rally
    // ---------------------------------------------------------------------

    private boolean tryRoyalCommand(
            ServerLevel level,
            LivingEntity priority) {

        if (this.isCoolingDown(ModMemoryModuleTypes.WOLF_KING_ROYAL_COMMAND_COOLDOWN.get())
                || !this.isPotentialRoyalThreat(priority)
                || this.getNearbyAllyCount() < 2) {
            return false;
        }

        boolean urgent = this.getOwnerThreat() != null
                || this.getThreatenedAlly() != null
                || this.getNearbyHostileCount() >= 2;

        if (!urgent) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_ROYAL_COMMAND_COOLDOWN.get(),
                ROYAL_COMMAND_COOLDOWN_TICKS);

        for (Wolf ally : this.findOperationalPackWolves(level, ROYAL_COMMAND_RADIUS)) {
            if (!ally.isAlive()) {
                continue;
            }

            ally.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH,
                    ROYAL_COMMAND_DURATION_TICKS,
                    0,
                    false,
                    false));

            ally.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE,
                    ROYAL_COMMAND_DURATION_TICKS,
                    0,
                    false,
                    false));
        }

        this.addEffect(new MobEffectInstance(
                MobEffects.RESISTANCE,
                ROYAL_COMMAND_DURATION_TICKS,
                0,
                false,
                false));

        this.coordinatePackTarget(level, priority, false);
        this.sendRoyalRing(level, this.position(), 4.0D, 36, ParticleTypes.END_ROD);
        this.majorAbilityLockoutTicks = MAJOR_ABILITY_LOCKOUT_TICKS;
        return true;
    }

    // ---------------------------------------------------------------------
    // Commanding Howl — intimidation / fear pressure
    // ---------------------------------------------------------------------

    private boolean tryCommandingHowl(
            ServerLevel level,
            LivingEntity priority) {

        if (this.isCoolingDown(ModMemoryModuleTypes.WOLF_KING_COMMANDING_HOWL_COOLDOWN.get())
                || !this.isPotentialRoyalThreat(priority)) {
            return false;
        }

        boolean seriousBattle = this.isPackOutnumbered()
                || this.getNearbyHostileCount() >= 3
                || (this.getOwnerThreat() != null && this.getNearbyHostileCount() >= 2);

        /*
         * Wild Alpha-King gets a second legitimate reason to howl: an active
         * hunt with untamed wolves close enough to answer him. This preserves
         * the original serious-battle trigger while adding the cinematic alpha
         * pack behavior without random howl spam.
         */
        boolean wildAlphaHunt = !this.isTame()
                && this.getNearbyWildRecruitCount() > 0
                && this.isPotentialRoyalThreat(priority);

        if (!seriousBattle && !wildAlphaHunt) {
            return false;
        }

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(COMMANDING_HOWL_RADIUS),
                this::isPotentialRoyalThreat);

        if (threats.isEmpty() && !wildAlphaHunt) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_COMMANDING_HOWL_COOLDOWN.get(),
                COMMANDING_HOWL_COOLDOWN_TICKS);

        /*
         * The species howl event owns the combined recording. Gameplay retains
         * this commanding howl's timing and volume without layering other mobs.
         */
        this.playSound(
                ModSounds.WOLF_KING_HOWL.value(),
                2.2F,
                1.0F);

        if (!this.isTame()) {
            // Every eligible untamed wolf in range may answer the hunting howl.
            this.beginWildAlphaHunt(level, priority);
        } else {
            // Tamed howl is an explicit field-command moment for the owner's pack.
            this.coordinatePackTarget(level, priority, true);
            this.rallyTamedAlphaPack(level);
        }

        for (LivingEntity threat : threats) {
            threat.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    COMMANDING_HOWL_WEAKNESS_TICKS,
                    1,
                    false,
                    true));

            threat.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS,
                    COMMANDING_HOWL_HESITATION_TICKS,
                    0,
                    false,
                    true));

            Vec3 away = threat.position().subtract(this.position());
            if (away.lengthSqr() > 1.0E-5D) {
                away = away.normalize();
                threat.push(away.x * 0.65D, 0.12D, away.z * 0.65D);
            }
        }

        this.coordinatePackTarget(level, priority, false);
        this.sendRoyalRing(level, this.position(), COMMANDING_HOWL_RADIUS, 56, ParticleTypes.ENCHANTED_HIT);

        WolfVfx.sendParticles("wolf_king", level,
                ParticleTypes.END_ROD,
                this.getX(),
                this.getY() + 1.0D,
                this.getZ(),
                14,
                0.65D, 0.30D, 0.65D,
                0.02D);

        this.majorAbilityLockoutTicks = MAJOR_ABILITY_LOCKOUT_TICKS;
        return true;
    }

    // ---------------------------------------------------------------------
    // Alpha Hunt / tamed Alpha Command
    // ---------------------------------------------------------------------

    /**
     * Recruit nearby untamed wolves into a temporary hunt hierarchy.
     *
     * <p>The wolves remain fully wild: no owner is assigned and no taming state
     * changes. Wolf King simply keeps a runtime roster for the duration of the
     * hunt and repeatedly gives those wolves a follow/engage intent.</p>
     */
    private void beginWildAlphaHunt(
            ServerLevel level,
            LivingEntity target) {

        if (this.isTame()
                || !this.isPotentialRoyalThreat(target)) {
            return;
        }

        List<Wolf> recruits = level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(ALPHA_HOWL_RECRUIT_RADIUS),
                this::canAnswerAlphaHuntHowl);

        for (Wolf recruit : recruits) {
            this.alphaHuntFollowers.put(recruit.getUUID(), recruit);

            /*
             * Every actual howl is a fresh Alpha rally pulse, including a
             * second/subsequent howl during the same hunt. Clear distractions
             * first so slimes or old prey cannot steal the recruit immediately.
             */
            recruit.setTarget(null);

            double kingDistanceSqr = this.distanceToSqr(recruit);
            if (kingDistanceSqr
                    > ALPHA_HOWL_RALLY_DISTANCE * ALPHA_HOWL_RALLY_DISTANCE) {
                recruit.getNavigation().moveTo(this, ALPHA_FOLLOW_SPEED);
            }
        }

        this.alphaHuntTicks = ALPHA_HUNT_DURATION_TICKS;
        this.alphaHowlRallyTicks = ALPHA_HOWL_RALLY_TICKS;
        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get(),
                target);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_ACTIVE.get(),
                true);
        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get(),
                this.alphaHuntFollowers.size());
    }

    private void tickWildAlphaHunt(ServerLevel level) {
        if (this.isTame() || this.alphaHuntTicks <= 0) {
            this.clearWildAlphaHunt();
            return;
        }

        --this.alphaHuntTicks;
        if (this.alphaHowlRallyTicks > 0) {
            --this.alphaHowlRallyTicks;
        }

        if (this.alphaHuntTicks <= 0) {
            this.clearWildAlphaHunt();
            return;
        }

        LivingEntity target = this.getAlphaHuntTarget();
        LivingEntity currentPriority = this.getRoyalPriorityTarget();

        /*
         * The King may reprioritize during a hunt. Followers answer the Alpha,
         * not a stale entity id, so the hunt memory follows his valid tactical
         * priority when one changes.
         */
        if (!this.isPotentialRoyalThreat(target)
                && this.isPotentialRoyalThreat(currentPriority)) {
            target = currentPriority;
            this.getBrain().setMemory(
                    ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get(),
                    target);
        } else if (this.isPotentialRoyalThreat(currentPriority)
                && currentPriority != target
                && this.scoreRoyalThreat(currentPriority)
                > this.scoreRoyalThreat(target) + ALPHA_HUNT_TARGET_SWITCH_MARGIN) {
            /*
             * Do not let a random slime/side target casually hijack an active
             * Alpha Hunt. Reprioritize only for a meaningfully greater threat.
             */
            target = currentPriority;
            this.getBrain().setMemory(
                    ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get(),
                    target);
        }

        if (!this.isPotentialRoyalThreat(target)) {
            this.clearWildAlphaHunt();
            return;
        }

        if (this.tickCount % ALPHA_HUNT_UPDATE_INTERVAL != 0) {
            return;
        }

        List<UUID> release = new ArrayList<>();

        for (Map.Entry<UUID, Wolf> entry : this.alphaHuntFollowers.entrySet()) {
            Wolf follower = entry.getValue();

            if (follower == null
                    || !follower.isAlive()
                    || follower.isTame()
                    || follower == this
                    || follower instanceof WolfKing
                    || follower instanceof CreatorWolf
                    || follower.level() != level
                    || this.distanceToSqr(follower)
                    > ALPHA_HUNT_RELEASE_RADIUS * ALPHA_HUNT_RELEASE_RADIUS) {
                release.add(entry.getKey());
                continue;
            }

            double kingDistanceSqr = this.distanceToSqr(follower);

            /*
             * A howl gives the pack a short, visible formation-first window.
             * This is deliberately reapplied on every howl so an already
             * recruited wolf visibly answers the Alpha again instead of acting
             * as if the second command never happened.
             */
            if (this.alphaHowlRallyTicks > 0) {
                follower.setTarget(null);

                if (kingDistanceSqr
                        > ALPHA_HOWL_RALLY_DISTANCE * ALPHA_HOWL_RALLY_DISTANCE) {
                    follower.getNavigation().moveTo(this, ALPHA_FOLLOW_SPEED);
                }
                continue;
            }

            if (kingDistanceSqr
                    > ALPHA_FOLLOW_START_DISTANCE * ALPHA_FOLLOW_START_DISTANCE) {
                follower.setTarget(null);
                follower.getNavigation().moveTo(this, ALPHA_FOLLOW_SPEED);
                continue;
            }

            /* Pups can follow the alpha hunt but remain non-combatants. */
            if (!follower.isBaby() && follower.canAttack(target)) {
                LivingEntity current = follower.getTarget();
                if (current == null
                        || !current.isAlive()
                        || current != target) {
                    follower.setTarget(target);
                }
            }
        }

        for (UUID uuid : release) {
            Wolf released = this.alphaHuntFollowers.remove(uuid);
            this.releaseAlphaFollower(released, target);
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get(),
                this.alphaHuntFollowers.size());

        if (this.alphaHuntFollowers.isEmpty()) {
            this.clearWildAlphaHunt();
        }
    }

    private void clearWildAlphaHunt() {
        LivingEntity target = this.getAlphaHuntTarget();

        for (Wolf follower : this.alphaHuntFollowers.values()) {
            this.releaseAlphaFollower(follower, target);
        }

        this.alphaHuntFollowers.clear();
        this.alphaHuntTicks = 0;
        this.alphaHowlRallyTicks = 0;

        this.getBrain().eraseMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get());
        this.getBrain().eraseMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_ACTIVE.get());
        this.getBrain().eraseMemory(
                ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get());
    }

    private void releaseAlphaFollower(
            Wolf follower,
            LivingEntity assignedTarget) {

        if (follower == null || !follower.isAlive()) {
            return;
        }

        if (assignedTarget != null && follower.getTarget() == assignedTarget) {
            follower.setTarget(null);
        }

        if (follower.getTarget() == null) {
            follower.getNavigation().stop();
        }
    }

    /**
     * Wild wolves, including Wolfism species, may answer the Alpha's howl.
     * Another Wolf King is excluded so two alpha entities cannot recursively
     * command one another. Creator Wolf is also excluded: Creator remains an
     * ally but exists outside the King's mechanical Alpha-command hierarchy.
     * Pups are allowed to follow but never forced to fight.
     */
    public boolean canAnswerAlphaHuntHowl(Wolf candidate) {
        return candidate != null
                && candidate != this
                && candidate.isAlive()
                && !candidate.isTame()
                && !(candidate instanceof WolfKing)
                && !(candidate instanceof CreatorWolf);
    }

    public boolean isActiveAlphaFollower(Wolf candidate) {
        return candidate != null
                && this.alphaHuntFollowers.containsKey(candidate.getUUID());
    }

    public int getRuntimeAlphaFollowerCount() {
        return this.alphaHuntFollowers.size();
    }

    /**
     * Operational pack membership is stricter than simple alliance.
     *
     * <p>Tamed: only wolves owned by the King's owner receive command authority.
     * Wild: only wolves that actually answered Alpha Hunt are operational pack.
     * Other tame/wild wolves remain allies where the existing Wolfism rules say
     * so, but are not commandeered.</p>
     */
    public boolean isWolfKingOperationalPackmate(Wolf wolf) {
        if (wolf == null || !wolf.isAlive()) {
            return false;
        }

        if (wolf == this) {
            return true;
        }

        /*
         * Creator may respect and fight alongside the King, but Creator is not
         * mechanically beneath the Alpha hierarchy. Wolf King's howl/rally/
         * command systems must never override Creator's own waiting state,
         * target decisions, navigation, or six-star behavior.
         */
        if (wolf instanceof CreatorWolf) {
            return false;
        }

        if (this.isTame()) {
            if (!wolf.isTame()) {
                return false;
            }

            LivingEntity kingOwner = this.getOwner();
            LivingEntity wolfOwner = wolf.getOwner();
            return kingOwner != null
                    && wolfOwner != null
                    && kingOwner.getUUID().equals(wolfOwner.getUUID());
        }

        return this.isActiveAlphaFollower(wolf);
    }

    /**
     * Bring free same-owner packmates back toward the Alpha during a fight.
     * Explicit sit/stay wins and current valid combat movement is not disturbed.
     */
    private void rallyTamedAlphaPack(ServerLevel level) {
        if (!this.isTame()) {
            return;
        }

        for (Wolf ally : this.findOperationalPackWolves(level, PACK_COORDINATION_RADIUS)) {
            if (ally == this
                    || ally.isBaby()
                    || ally.isOrderedToSit()
                    || ally.isInSittingPose()
                    || ally.getTarget() != null) {
                continue;
            }

            if (this.distanceToSqr(ally)
                    > TAMED_ALPHA_RALLY_DISTANCE * TAMED_ALPHA_RALLY_DISTANCE) {
                ally.getNavigation().moveTo(this, TAMED_ALPHA_RALLY_SPEED);
            }
        }
    }

    private boolean shouldAlphaOverrideTarget(
            Wolf ally,
            LivingEntity current,
            LivingEntity priority,
            boolean forceMarkedTarget) {

        if (forceMarkedTarget) {
            return true;
        }

        if (current == priority) {
            return false;
        }

        if (current == null || !current.isAlive()) {
            return true;
        }

        /*
         * Owner/family emergencies are alpha-level orders. A free packmate may
         * drop a lower-value chase to protect the family. Player sit/stay has
         * already been filtered out before this method is called.
         */
        if (priority == this.getOwnerThreat()
                || priority == this.getFamilyDefenseTarget()) {
            return true;
        }

        if (!this.isPotentialRoyalThreat(current)) {
            return true;
        }

        return this.scoreRoyalThreat(priority)
                > this.scoreRoyalThreat(current) + ALPHA_OVERRIDE_SCORE_MARGIN;
    }

    // ---------------------------------------------------------------------
    // Iron Will — pack stability
    // ---------------------------------------------------------------------

    private boolean tryIronWill(
            ServerLevel level,
            LivingEntity priority) {

        if (this.isCoolingDown(ModMemoryModuleTypes.WOLF_KING_IRON_WILL_COOLDOWN.get())
                || !this.isPotentialRoyalThreat(priority)
                || this.getNearbyAllyCount() < 2) {
            return false;
        }

        boolean needed = this.isPackScattered()
                || this.isPackOutnumbered()
                || priority instanceof Ravager
                || this.getNearbyHostileCount() >= 4;

        if (!needed) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_IRON_WILL_COOLDOWN.get(),
                IRON_WILL_COOLDOWN_TICKS);

        this.ironWillTicks = IRON_WILL_DURATION_TICKS;
        this.tickIronWill(level);
        this.sendRoyalRing(level, this.position(), 5.0D, 42, ParticleTypes.ENCHANTED_HIT);
        this.majorAbilityLockoutTicks = MAJOR_ABILITY_LOCKOUT_TICKS;
        return true;
    }

    private void tickIronWill(ServerLevel level) {
        if (this.ironWillTicks <= 0) {
            this.clearIronWill();
            return;
        }

        Map<UUID, LivingEntity> current = new HashMap<>();

        this.applyIronWillTo(this, current);

        for (Wolf ally : this.findOperationalPackWolves(level, IRON_WILL_RADIUS)) {
            if (!ally.isAlive()) {
                continue;
            }

            this.applyIronWillTo(ally, current);

            /*
             * Formation stabilization is intentionally conservative: never
             * override sitting wolves or wolves already handling a combat target.
             */
            if (this.isWolfismWorkTick(10)
                    && ally != this
                    && !ally.isOrderedToSit()
                    && !ally.isInSittingPose()
                    && ally.getTarget() == null
                    && this.distanceToSqr(ally) > 8.0D * 8.0D) {
                ally.getNavigation().moveTo(this, 1.15D);
            }
        }

        for (Map.Entry<UUID, LivingEntity> entry : this.ironWillRecipients.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                this.removeTransientModifier(
                        entry.getValue(),
                        Attributes.KNOCKBACK_RESISTANCE,
                        IRON_WILL_MODIFIER);
            }
        }

        this.ironWillRecipients.clear();
        this.ironWillRecipients.putAll(current);

        if (this.isWolfismWorkTick(20)) {
            WolfVfx.sendParticles("wolf_king", level,
                    ParticleTypes.ENCHANTED_HIT,
                    this.getX(),
                    this.getY() + 0.45D,
                    this.getZ(),
                    7,
                    0.65D, 0.22D, 0.65D,
                    0.01D);
        }

        --this.ironWillTicks;
        if (this.ironWillTicks <= 0) {
            this.clearIronWill();
        }
    }

    private void applyIronWillTo(
            LivingEntity entity,
            Map<UUID, LivingEntity> current) {

        current.put(entity.getUUID(), entity);

        this.applyTransientModifier(
                entity,
                Attributes.KNOCKBACK_RESISTANCE,
                IRON_WILL_MODIFIER,
                IRON_WILL_KNOCKBACK_RESISTANCE,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private void clearIronWill() {
        this.ironWillTicks = 0;

        for (LivingEntity recipient : this.ironWillRecipients.values()) {
            this.removeTransientModifier(
                    recipient,
                    Attributes.KNOCKBACK_RESISTANCE,
                    IRON_WILL_MODIFIER);
        }

        this.ironWillRecipients.clear();
    }

    // ---------------------------------------------------------------------
    // Leadership Dash — marked target / pack focus
    // ---------------------------------------------------------------------

    private boolean tryLeadershipDash(
            ServerLevel level,
            LivingEntity priority) {

        if (this.isCoolingDown(ModMemoryModuleTypes.WOLF_KING_LEADERSHIP_DASH_COOLDOWN.get())
                || !this.isPotentialRoyalThreat(priority)
                || priority instanceof Creeper
                || this.leadershipDashTicks > 0) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(priority);
        if (distanceSqr < LEADERSHIP_DASH_MIN_RANGE * LEADERSHIP_DASH_MIN_RANGE
                || distanceSqr > LEADERSHIP_DASH_MAX_RANGE * LEADERSHIP_DASH_MAX_RANGE) {
            return false;
        }

        Vec3 flat = new Vec3(
                priority.getX() - this.getX(),
                0.0D,
                priority.getZ() - this.getZ());

        if (flat.lengthSqr() < 1.0E-5D) {
            return false;
        }

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_LEADERSHIP_DASH_COOLDOWN.get(),
                LEADERSHIP_DASH_COOLDOWN_TICKS);

        this.getBrain().setMemory(
                ModMemoryModuleTypes.WOLF_KING_MARKED_TARGET.get(),
                priority);

        this.leadershipMarkTicks = LEADERSHIP_MARK_DURATION_TICKS;
        this.leadershipDashTicks = LEADERSHIP_DASH_DURATION_TICKS;
        this.leadershipDashDirection = flat.normalize();

        this.setTarget(priority);
        this.getNavigation().stop();
        this.setSprinting(true);
        this.coordinatePackTarget(level, priority, true);
        this.majorAbilityLockoutTicks = MAJOR_ABILITY_LOCKOUT_TICKS;
        return true;
    }

    private void tickLeadershipDash(ServerLevel level) {
        LivingEntity target = this.getMarkedTarget();

        if (!this.isPotentialRoyalThreat(target)) {
            this.cancelLeadershipDash();
            return;
        }

        Vec3 desired = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ());

        if (desired.lengthSqr() > 1.0E-5D) {
            Vec3 steered = this.leadershipDashDirection
                    .scale(0.72D)
                    .add(desired.normalize().scale(0.28D));

            if (steered.lengthSqr() > 1.0E-5D) {
                this.leadershipDashDirection = steered.normalize();
            }
        }

        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(
                this.leadershipDashDirection.x * LEADERSHIP_DASH_SPEED,
                current.y,
                this.leadershipDashDirection.z * LEADERSHIP_DASH_SPEED);
        this.hurtMarked = true;

        this.faceDirection(this.leadershipDashDirection);

        WolfVfx.sendParticles("wolf_king", level,
                ParticleTypes.END_ROD,
                this.getX() - this.leadershipDashDirection.x * 0.45D,
                this.getY() + 0.32D,
                this.getZ() - this.leadershipDashDirection.z * 0.45D,
                3,
                0.15D, 0.10D, 0.15D,
                0.006D);

        if (this.distanceToSqr(target)
                <= LEADERSHIP_DASH_CONTACT_RANGE * LEADERSHIP_DASH_CONTACT_RANGE) {
            super.doHurtTarget(level, target);
            this.cancelLeadershipDash();
            return;
        }

        if (this.horizontalCollision || --this.leadershipDashTicks <= 0) {
            this.cancelLeadershipDash();
        }
    }

    private void tickLeadershipMark(ServerLevel level) {
        LivingEntity marked = this.getMarkedTarget();

        if (!this.isPotentialRoyalThreat(marked)) {
            this.clearLeadershipMark();
            return;
        }

        if (this.isWolfismWorkTick(10)) {
            this.coordinatePackTarget(level, marked, true);

            WolfVfx.sendParticles("wolf_king", level,
                    ParticleTypes.ENCHANTED_HIT,
                    marked.getX(),
                    marked.getY() + marked.getBbHeight() * 0.65D,
                    marked.getZ(),
                    4,
                    0.25D, 0.25D, 0.25D,
                    0.01D);
        }

        --this.leadershipMarkTicks;
        if (this.leadershipMarkTicks <= 0) {
            this.clearLeadershipMark();
        }
    }

    private void clearLeadershipMark() {
        this.leadershipMarkTicks = 0;
        this.getBrain().eraseMemory(ModMemoryModuleTypes.WOLF_KING_MARKED_TARGET.get());
    }

    private void cancelLeadershipDash() {
        this.leadershipDashTicks = 0;
        this.leadershipDashDirection = Vec3.ZERO;
        this.setSprinting(false);
    }

    // ---------------------------------------------------------------------
    // Brain memory accessors
    // ---------------------------------------------------------------------

    public LivingEntity getRoyalPriorityTarget() {
        LivingEntity marked = this.getMarkedTarget();
        if (this.isPotentialRoyalThreat(marked)) {
            return marked;
        }

        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_PRIORITY_TARGET.get())
                .filter(this::isPotentialRoyalThreat)
                .orElse(null);
    }

    public LivingEntity getMarkedTarget() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_MARKED_TARGET.get())
                .orElse(null);
    }

    public LivingEntity getAlphaHuntTarget() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_TARGET.get())
                .orElse(null);
    }

    public int getNearbyWildRecruitCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_WILD_RECRUIT_COUNT.get())
                .orElse(0);
    }

    public int getAlphaFollowerCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_ALPHA_FOLLOWER_COUNT.get())
                .orElse(this.alphaHuntFollowers.size());
    }

    public boolean isAlphaHuntActive() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_ALPHA_HUNT_ACTIVE.get())
                .orElse(false);
    }

    public LivingEntity getOwnerThreat() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_OWNER_THREAT.get())
                .orElse(null);
    }

    public LivingEntity getThreatenedAlly() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_THREATENED_ALLY.get())
                .orElse(null);
    }

    public int getNearbyAllyCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_ALLY_COUNT.get())
                .orElse(1);
    }

    public int getNearbyHostileCount() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_HOSTILE_COUNT.get())
                .orElse(0);
    }

    public boolean isPackScattered() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_PACK_SCATTERED.get())
                .orElse(false);
    }

    public boolean isPackOutnumbered() {
        return this.getBrain()
                .getMemory(ModMemoryModuleTypes.WOLF_KING_PACK_OUTNUMBERED.get())
                .orElse(false);
    }

    // ---------------------------------------------------------------------
    // Family / threat rules used by sensors
    // ---------------------------------------------------------------------

    /**
     * This is the broad non-hostility/family rule only. Command authority is
     * intentionally narrower and lives in isWolfKingOperationalPackmate().
     * Tamed King remains allied to tamed wolves; wild King remains non-hostile
     * toward wild wolves, but only recruited/same-owner wolves receive orders.
     */
    public boolean isWolfKingPackAlly(Wolf wolf) {
        if (wolf == null || !wolf.isAlive()) {
            return false;
        }

        if (wolf == this) {
            return true;
        }

        if (this.isTame()) {
            return wolf.isTame();
        }

        return !wolf.isTame();
    }

    public boolean isWolfKingFamilyMember(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (entity == this) {
            return true;
        }

        if (entity instanceof Wolf wolf && this.isWolfKingPackAlly(wolf)) {
            return true;
        }

        if (!this.isTame()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        return owner != null && entity == owner;
    }

    public boolean isPotentialRoyalThreat(LivingEntity candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate == this
                || this.isWolfKingFamilyMember(candidate)
                || !super.canAttack(candidate)) {
            return false;
        }

        if (candidate instanceof Enemy) {
            return true;
        }

        if (candidate == this.getTarget()
                || candidate == this.getFamilyDefenseTarget()
                || candidate == this.getLastHurtByMob()) {
            return true;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && candidate == owner.getLastHurtByMob()) {
            return true;
        }

        if (candidate instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && this.isWolfKingFamilyMember(target);
        }

        return false;
    }

    public double scoreRoyalThreat(LivingEntity candidate) {
        if (!this.isPotentialRoyalThreat(candidate)) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = Math.min(90.0D, candidate.getMaxHealth() * 0.8D);

        LivingEntity owner = this.getOwner();
        if (owner != null) {
            if (candidate == owner.getLastHurtByMob()) {
                score += 300.0D;
            }

            if (candidate instanceof Mob mob && mob.getTarget() == owner) {
                score += 280.0D;
            }
        }

        if (candidate == this.getFamilyDefenseTarget()) {
            score += 250.0D;
        }

        LivingEntity threatenedAlly = this.getThreatenedAlly();
        if (candidate instanceof Mob mob
                && threatenedAlly != null
                && mob.getTarget() == threatenedAlly) {
            score += 220.0D;
        }

        if (candidate == this.getMarkedTarget()) {
            score += 200.0D;
        }

        if (candidate == this.getTarget()) {
            score += 100.0D;
        }

        if (candidate instanceof Ravager) {
            score += 90.0D;
        }

        if (candidate instanceof Enemy) {
            score += 40.0D;
        }

        score += Math.max(
                0.0D,
                70.0D - Math.sqrt(this.distanceToSqr(candidate)) * 2.0D);

        return score;
    }

    private LivingEntity getProtectedMemberForThreat(LivingEntity threat) {
        if (threat == null) {
            return null;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null) {
            if (threat == owner.getLastHurtByMob()) {
                return owner;
            }

            if (threat instanceof Mob mob && mob.getTarget() == owner) {
                return owner;
            }
        }

        LivingEntity threatened = this.getThreatenedAlly();
        if (threatened != null
                && threat instanceof Mob mob
                && mob.getTarget() == threatened) {
            return threatened;
        }

        return this.getProtectedFamilyMember();
    }

    private List<Wolf> findOperationalPackWolves(
            ServerLevel level,
            double radius) {

        List<Wolf> allies = new ArrayList<>();
        allies.add(this);

        allies.addAll(level.getEntitiesOfClass(
                Wolf.class,
                this.getBoundingBox().inflate(radius),
                wolf -> wolf != this && this.isWolfKingOperationalPackmate(wolf)));

        return allies;
    }

    // ---------------------------------------------------------------------
    // Generic cooldown / attribute / visual helpers
    // ---------------------------------------------------------------------

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

    private void applyTransientModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation) {

        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, operation));
    }

    private void removeTransientModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier id) {

        if (entity == null) {
            return;
        }

        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private void faceDirection(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-5D) {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    private void sendRoyalRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            int points,
            ParticleOptions particle) {

        for (int i = 0; i < points; ++i) {
            double angle = Math.PI * 2.0D * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            WolfVfx.sendParticles("wolf_king", level,
                    particle,
                    x,
                    center.y + 0.10D,
                    z,
                    1,
                    0.02D, 0.04D, 0.02D,
                    0.0D);
        }
    }

    // ---------------------------------------------------------------------
    // Natural spawning — Alpha encounter
    // ---------------------------------------------------------------------

    /**
     * Natural Wolf Kings are special, but intentionally findable.
     *
     * <p>The biome tag supplies Taiga, Snowy Taiga, Grove, Old Growth Pine
     * Taiga and Old Growth Spruce Taiga. The biome modifier uses weight 5 and
     * an exact group size of one King. Nearby untamed wolves are recruited
     * organically by Alpha Hunt rather than spawned as permanent escorts.</p>
     */
    public static boolean checkWolfKingSpawnRules(
            EntityType<WolfKing> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        // Never block spawn eggs, /summon, breeding/debug creation, etc.
        if (reason != EntitySpawnReason.NATURAL
                && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }

        // Standard natural wolf terrain; snow block remains valid through the
        // vanilla WOLVES_SPAWNABLE_ON tag in the intended cold habitats.
        if (!level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON)) {
            return false;
        }

        if (!level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        // Alpha territory rule: do not stack multiple wild Kings in one local
        // hunting territory. Tamed Kings do not suppress natural spawns.
        if (level instanceof ServerLevel serverLevel) {
            boolean anotherWildKing = !serverLevel.getEntitiesOfClass(
                            WolfKing.class,
                            new AABB(pos).inflate(96.0D, 48.0D, 96.0D),
                            king -> king.isAlive() && !king.isTame())
                    .isEmpty();

            if (anotherWildKing) {
                return false;
            }
        }

        return true;
    }

}
