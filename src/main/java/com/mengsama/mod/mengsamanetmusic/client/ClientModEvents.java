package com.mengsama.mod.mengsamanetmusic.client;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.config.ConfigManager;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.config.MusicHudConfig;
import com.mengsama.mod.mengsamanetmusic.gui.*;
import com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud;
import net.minecraft.client.Minecraft;
import com.mengsama.mod.mengsamanetmusic.init.ModMenuTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> register = extension -> event.registerItem(extension, item);
            if (item instanceof com.mengsama.mod.mengsamanetmusic.item.MusicDeviceBlockItem device) device.createClientExtensions(register);
            else if (item instanceof com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem headphones) headphones.createClientExtensions(register);
            else if (item instanceof com.mengsama.mod.mengsamanetmusic.earbuds.EarbudItem earbud) earbud.createClientExtensions(register);
        }
    }

    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
event.register(ModMenuTypes.EARBUDS.get(), com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudScreen::new);
event.register(
                    ModMenuTypes.MUSIC_PLAYER.get(),
                    MusicPlayerScreen::new);
event.register(
                    ModMenuTypes.MUSIC_PLAYER_PLAYLIST.get(),
                    MusicPlayerPlaylistScreen::new);
    }
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {

        ConfigManager.initCookies();
        com.mengsama.mod.mengsamanetmusic.cache.MusicAssetCache.instance().loadAsync();
        MusicPlayerUiConfig.load();
        MusicHudConfig.load();

        if (net.neoforged.fml.ModList.get().isLoaded("cloth_config"))
            com.mengsama.mod.mengsamanetmusic.compat.ClothConfigCompat.registerModsPage();

        event.enqueueWork(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("curios"))
                com.mengsama.mod.mengsamanetmusic.client.renderer.CuriosHeadphonesRenderer.register();
            
            MusicPlayerBackground.reload();
            

            
        });
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MusicClientKeys.register(event);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                                   ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                                                   Executor backgroundExecutor, Executor gameExecutor) {
                return CompletableFuture.supplyAsync(() -> Unit.INSTANCE, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenRunAsync(() -> {
                            MusicPlayerUiConfig.load();
                            MusicPlayerBackground.reload();
                            Minecraft minecraft = Minecraft.getInstance();
                            if (minecraft.screen instanceof MusicPlayerScreen
                                    || minecraft.screen instanceof MusicPlayerPlaylistScreen) {
                                minecraft.screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                                        minecraft.getWindow().getGuiScaledHeight());
                            }
                        }, gameExecutor);
            }
        });
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onLogin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback.resetSession();
        }
        @SubscribeEvent
        public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback.resetSession();
            com.mengsama.mod.mengsamanetmusic.network.SyncVipCookiePacket.CLIENT_HAS_VIP_COOKIE = false;
        }
        @SubscribeEvent
        public static void onStreamingSourceStarted(PlayStreamingSourceEvent event) {
            com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback.onChannelStarted(
                    event.getSound(), event.getChannel());
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            MusicInfoHud.render(guiGraphics);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            
            Minecraft mc = Minecraft.getInstance();
            MusicClientKeys.consumeHudEditor(() -> {
                if (mc.screen == null) MoveHudScreen.open();
            });
        }
    }
}
