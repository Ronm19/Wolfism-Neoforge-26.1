package net.ronm19.wolfism.entity.base;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

/**
 * Common foundation for Wolfism's normal wolf-shaped species.
 *
 * <p>The class deliberately builds on vanilla {@link Wolf} so every species gets
 * vanilla wolf movement, taming, sitting, following, collar rendering, armor,
 * wet/shake behavior, sounds, and combat behavior as a stable baseline. Species
 * can then add their own senses, memories, abilities and coordination without
 * duplicating the basic wolf implementation.</p>
 */

public abstract class AbstractWolfismWolf extends Wolf {
    protected AbstractWolfismWolf(EntityType<? extends AbstractWolfismWolf> type, Level level) {
        super(type, level);
    }

    /** Returns the registered EntityType used when this species breeds. */
    protected abstract EntityType<? extends AbstractWolfismWolf> wolfismEntityType();

    /**
     * Wolfism family rule: once tamed, wolves do not treat another tamed wolf as
     * a valid enemy, even when their owners differ.
     */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
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