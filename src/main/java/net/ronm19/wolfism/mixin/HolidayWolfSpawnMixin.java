package net.ronm19.wolfism.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.ronm19.wolfism.entity.holiday.AbstractWolfismHolidayWolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records creation provenance even when a command supplies NBT and skips finalization. */
@Mixin(EntityType.class)
public abstract class HolidayWolfSpawnMixin {
    @Inject(
            method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"))
    private void wolfism$recordHolidaySpawnReason(
            Level level,
            EntitySpawnReason reason,
            CallbackInfoReturnable<Entity> callback) {
        if (callback.getReturnValue() instanceof AbstractWolfismHolidayWolf wolf) {
            wolf.recordHolidaySpawnReason(reason);
        }
    }
}
