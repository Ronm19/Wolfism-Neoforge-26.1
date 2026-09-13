package net.ronm19.wolfism.mixin;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.wolfism.entity.ai.support.HalloweenLightState;
import net.ronm19.wolfism.entity.ai.util.MagmaTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe completed placement events, including identical-state replacements and late cancellations. */
@Mixin(value = EventHooks.class, remap = false)
public abstract class WolfTemporaryBlockPlacementMixin {
    @Inject(method = "onBlockPlace", at = @At("RETURN"), remap = false)
    private static void wolfism$playerPlaced(Entity entity, BlockSnapshot snapshot, Direction direction,
                                             CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof Player && !result.getReturnValueZ()
                && snapshot.getLevel() instanceof ServerLevel level) {
            HalloweenLightState.playerPlaced(level, snapshot.getPos());
            MagmaTerrainState.playerPlaced(level, snapshot.getPos());
        }
    }

    @Inject(method = "onMultiBlockPlace", at = @At("RETURN"), remap = false)
    private static void wolfism$playerPlacedMultiple(Entity entity, List<BlockSnapshot> snapshots, Direction direction,
                                                     CallbackInfoReturnable<Boolean> result) {
        if (!(entity instanceof Player) || result.getReturnValueZ()) return;
        for (BlockSnapshot snapshot : snapshots) {
            if (snapshot.getLevel() instanceof ServerLevel level) {
                HalloweenLightState.playerPlaced(level, snapshot.getPos());
                MagmaTerrainState.playerPlaced(level, snapshot.getPos());
            }
        }
    }
}
