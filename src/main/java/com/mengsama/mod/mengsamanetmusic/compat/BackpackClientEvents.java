package com.mengsama.mod.mengsamanetmusic.compat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@Mod.EventBusSubscriber(modid = "mengsamanetmusic", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BackpackClientEvents {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (BackpackAccess.available()) event.enqueueWork(com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackClient::register);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        if (BackpackAccess.available()) event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.BackpackCharmRenderer::reload);
    }
}
