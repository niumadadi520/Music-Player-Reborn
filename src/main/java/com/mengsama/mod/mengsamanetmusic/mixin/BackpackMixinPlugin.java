package com.mengsama.mod.mengsamanetmusic.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public final class BackpackMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!target.startsWith("net.p3pp3rf1y.sophisticatedbackpacks.")) return true;
        var list = net.minecraftforge.fml.loading.FMLLoader.getLoadingModList();
        return list != null && list.getModFileById("sophisticatedbackpacks") != null;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
