package net.ronm19.wolfism.mixin.compat;

import com.lowdragmc.lowdraglib2.registry.LDLRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Photon 26.1.2.2 eagerly discovers client FX creators and timeline editors on
 * dedicated servers. Their Minecraft client base classes do not exist there.
 * Keep the common registries/codecs and network handlers; defer these two
 * client-only registries to the client. The upstream jars remain unchanged.
 * WolfismMixinPlugin applies this only to the exact tested Photon server build.
 */
@Mixin(targets = "com.lowdragmc.photon.PhotonRegistries$Client", remap = false)
public abstract class PhotonServerRegistryMixin {
    @Inject(method = "registerStaticInstances", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static <T> void wolfism$avoidClientRegistryDiscovery(LDLRegistry.String<T> registry,
                                                               Class<T> baseType, CallbackInfo callback) {
        String id = registry.getRegistryName().toString();
        if (id.equals("photon:fx_object") || id.equals("photon:timeline_track")) callback.cancel();
    }
}
