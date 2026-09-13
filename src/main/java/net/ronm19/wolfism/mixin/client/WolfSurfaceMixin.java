package net.ronm19.wolfism.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.ronm19.wolfism.client.style.WolfSurfaceMaterials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies only to Wolfism body passes. Armor, collars, outlines and invisibility keep vanilla rendering. */
@Mixin(LivingEntityRenderer.class)
public abstract class WolfSurfaceMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<S>> {
    @Shadow public abstract Identifier getTextureLocation(S state);

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void wolfism$surface(S state, boolean visible, boolean transparent, boolean glowing,
                                CallbackInfoReturnable<RenderType> callback) {
        if (!(state instanceof WolfRenderState) || !visible || transparent || glowing
                || callback.getReturnValue() == null) return;
        Identifier texture = getTextureLocation(state);
        if (WolfSurfaceMaterials.accepts(texture)) callback.setReturnValue(WolfSurfaceMaterials.surface(texture));
    }
}
