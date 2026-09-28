package com.mengsama.mod.mengsamanetmusic.platform;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

 
public final class GameRegistries {
    private GameRegistries() {}
    public static HolderLookup.Provider lookup() {
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null && server.isSameThread())return server.registryAccess();
        if(FMLEnvironment.dist==Dist.CLIENT) {
            var client=ClientLookup.get();
            if(client!=null)return client;
        }
        if(server!=null)return server.registryAccess();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
    private static final class ClientLookup {
        static HolderLookup.Provider get() {
            var client=net.minecraft.client.Minecraft.getInstance();
            return client.getConnection()==null?null:client.getConnection().registryAccess();
        }
    }
}
