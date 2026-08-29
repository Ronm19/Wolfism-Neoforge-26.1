package net.ronm19.wolfism.entity.custom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.ronm19.wolfism.registry.ModEntities;

/**
 * Physical bone projectile used by Skeleton Wolf's Bone Shot and Skeletal Volley.
 * The visual is the vanilla bone item rendered in flight; damage and family safety
 * are handled here rather than faked with particles.
 */
public final class BoneShardProjectile extends ThrowableItemProjectile {
    private static final float DEFAULT_DAMAGE = 4.0F;
    private float damage = DEFAULT_DAMAGE;

    public BoneShardProjectile(EntityType<? extends BoneShardProjectile> type, Level level) {
        super(type, level);
    }

    public BoneShardProjectile(Level level, LivingEntity owner) {
        super(ModEntities.BONE_SHARD.get(), owner, level, new ItemStack(Items.BONE));
    }

    public BoneShardProjectile setShardDamage(float damage) {
        this.damage = Math.max(0.0F, damage);
        return this;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.BONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.025D;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }

        Entity owner = this.getOwner();
        if (owner instanceof SkeletonWolf wolf && target instanceof LivingEntity living) {
            return wolf.isValidSkeletonCombatTarget(living);
        }

        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        Entity owner = this.getOwner();
        Entity hit = result.getEntity();
        if (owner instanceof SkeletonWolf wolf
                && hit instanceof LivingEntity target
                && wolf.isValidSkeletonCombatTarget(target)) {
            target.hurtServer(level, this.damageSources().thrown(this, owner), this.damage);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > 80) {
            this.discard();
        }
    }
}
