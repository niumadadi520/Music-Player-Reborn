package com.mengsama.mod.mengsamanetmusic.compat;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = "mengsamanetmusic", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BackpackClientEvents {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (BackpackAccess.available()) event.enqueueWork(com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackClient::register);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        if (BackpackAccess.available()) event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.BackpackCharmRenderer::reload);
    }
}
