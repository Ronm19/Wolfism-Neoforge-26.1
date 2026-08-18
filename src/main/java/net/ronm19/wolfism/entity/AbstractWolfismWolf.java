package net.ronm19.wolfism.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperPackAttackGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismFamilyDefenseGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismFamilyPupEmergencyGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperRetreatGoal;
import net.ronm19.wolfism.entity.ai.goal.WolfismCreeperTargetGoal;

/**
 * Common foundation for Wolfism's normal wolf-shaped species.
 *
 * <p>The class deliberately builds on vanilla {@link Wolf} so every species gets
 * vanilla wolf movement, taming, sitting, following, wolf armor, wet/shake
 * behavior, sounds and combat behavior as a stable baseline. Species-specific
 * rendering belongs in each species' client renderer instead of this common
 * entity class.</p>
 */
public abstract class AbstractWolfismWolf extends Wolf {
    public static final int FAMILY_DEFENSE_DURATION_TICKS = 20 * 12;
    public static final double FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE = 64.0D;

    private LivingEntity familyDefenseTarget;
    private LivingEntity protectedFamilyMember;
    private int familyDefenseTicks;
    private boolean resumeOrderedSitAfterFamilyDefense;
    protected AbstractWolfismWolf(EntityType<? extends AbstractWolfismWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Shared Wolfism combat intelligence: Creepers are valid threats, but
        // wolves never stand beside one while its fuse is active. The safety goal is
        // priority 0 so an imminent explosion can preempt ordinary movement,
        // including pup follow/play and ordered sitting.
        this.goalSelector.addGoal(0, new WolfismCreeperRetreatGoal(this));
        this.goalSelector.addGoal(1, new WolfismFamilyPupEmergencyGoal(this));
        this.goalSelector.addGoal(1, new WolfismCreeperPackAttackGoal(this));

        // Family retaliation owns target priority, while species-specific combat
        // goals remain free to decide HOW to engage that emergency target.
        this.targetSelector.addGoal(0, new WolfismFamilyDefenseGoal(this));
        this.targetSelector.addGoal(4, new WolfismCreeperTargetGoal(this));
    }


    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel && this.familyDefenseTicks > 0) {
            --this.familyDefenseTicks;

            LivingEntity threat = this.familyDefenseTarget;
            if (threat == null
                    || !threat.isAlive()
                    || this.isAlliedTo(threat)
                    || this.distanceToSqr(threat) > FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE * FAMILY_DEFENSE_MAX_PURSUIT_DISTANCE
                    || this.familyDefenseTicks <= 0) {
                this.clearFamilyDefense();
            }
        }
    }

    /**
     * Starts or refreshes the universal tamed-family emergency. The latest
     * legitimate attacker always wins immediately over ordinary goals/targets.
     */
    public boolean beginFamilyDefense(LivingEntity attacker, LivingEntity protectedFamily) {
        if (!this.isTame()
                || attacker == null
                || protectedFamily == null
                || !attacker.isAlive()
                || attacker == this
                || attacker == protectedFamily
                || this.isAlliedTo(attacker)) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null) {
            // A wolf never turns on its own owner, even when two family
            // relationships collide. Other wolves may still defend the victim.
            if (attacker == owner || !this.wantsToAttack(attacker, owner)) {
                return false;
            }
        }

        if (this.isOrderedToSit()) {
            this.resumeOrderedSitAfterFamilyDefense = true;
            this.setOrderedToSit(false);
        }

        this.familyDefenseTarget = attacker;
        this.protectedFamilyMember = protectedFamily;
        this.familyDefenseTicks = FAMILY_DEFENSE_DURATION_TICKS;
        this.getNavigation().stop();

        this.onFamilyDefenseStarted(attacker, protectedFamily);

        if (this.isBaby()) {
            this.setTarget(null);
        } else {
            // Force replacement even if the wolf was already fighting another
            // hostile. Family protection is not merely an assist suggestion.
            this.setTarget(attacker);
        }
        return true;
    }

    public boolean hasFamilyDefenseEmergency() {
        return this.familyDefenseTicks > 0
                && this.familyDefenseTarget != null
                && this.familyDefenseTarget.isAlive();
    }

    public LivingEntity getFamilyDefenseTarget() {
        return this.familyDefenseTarget;
    }

    public LivingEntity getProtectedFamilyMember() {
        return this.protectedFamilyMember;
    }

    public void clearFamilyDefense() {
        LivingEntity oldTarget = this.familyDefenseTarget;
        this.familyDefenseTarget = null;
        this.protectedFamilyMember = null;
        this.familyDefenseTicks = 0;

        if (this.getTarget() == oldTarget) {
            this.setTarget(null);
        }

        if (this.resumeOrderedSitAfterFamilyDefense && this.isTame() && this.isAlive()) {
            this.setOrderedToSit(true);
        }
        this.resumeOrderedSitAfterFamilyDefense = false;
    }

    /** Species hook for immediately canceling non-family work/ability state. */
    protected void onFamilyDefenseStarted(LivingEntity attacker, LivingEntity protectedFamily) {
    }

    /** Returns the registered EntityType used when this species breeds. */
    protected abstract EntityType<? extends AbstractWolfismWolf> wolfismEntityType();

    /**
     * Wolfism family rule: once tamed, wolves do not treat another tamed wolf as
     * a valid enemy, even when their owners differ.
     */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        // Vanilla wolves normally reject Creepers as owner-directed targets.
        // Wolfism wolves know how to disengage from the fuse, so adults may
        // legitimately defend their owner from / engage a Creeper.
        if (!this.isBaby() && target instanceof Creeper) {
            return true;
        }

        if (this.isTame() && target instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }
        return super.wantsToAttack(target, owner);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isTame() && target instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (this.isTame() && other instanceof Wolf wolf && wolf.isTame()) {
            return true;
        }
        return super.considersEntityAsAlly(other);
    }

    /**
     * The default Wolfism breeding rule keeps species pure. A future species can
     * override this if it intentionally supports cross-species offspring.
     */
    @Override
    public boolean canMate(Animal partner) {
        return partner instanceof AbstractWolfismWolf wolf
                && wolf.getType() == this.getType()
                && super.canMate(partner);
    }

    @Override
    public AbstractWolfismWolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (!(partner instanceof AbstractWolfismWolf wolf) || wolf.getType() != this.getType()) {
            return null;
        }

        AbstractWolfismWolf baby = this.wolfismEntityType().create(level, EntitySpawnReason.BREEDING);
        if (baby != null && this.isTame()) {
            baby.setOwnerReference(this.getOwnerReference());
            baby.setTame(true, true);
            baby.setComponent(
                    DataComponents.WOLF_COLLAR,
                    DyeColor.getMixedColor(level, this.getCollarColor(), wolf.getCollarColor()));
            baby.setComponent(
                    DataComponents.WOLF_SOUND_VARIANT,
                    WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
        }
        return baby;
    }
}
