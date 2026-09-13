package net.ronm19.wolfism.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Limits the Photon compatibility fix to its verified artifact and physical side. */
public final class WolfismMixinPlugin implements IMixinConfigPlugin {
    private static final String PHOTON_FIX = "net.ronm19.wolfism.mixin.compat.PhotonServerRegistryMixin";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!PHOTON_FIX.equals(mixinClassName)) return true;
        if (FMLEnvironment.getDist() != Dist.DEDICATED_SERVER) return false;
        return FMLLoader.getCurrent().getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals("photon")
                        && mod.getVersion().toString().equals("26.1.2.2"));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
