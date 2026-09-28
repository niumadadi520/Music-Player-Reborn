package com.mengsama.mod.mengsamanetmusic.client;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {

        ConfigManager.initCookies();
        com.mengsama.mod.mengsamanetmusic.cache.MusicAssetCache.instance().loadAsync();
        MusicPlayerUiConfig.load();
        MusicHudConfig.load();

        if (net.minecraftforge.fml.ModList.get().isLoaded("cloth_config"))
            com.mengsama.mod.mengsamanetmusic.compat.ClothConfigCompat.registerModsPage();

        event.enqueueWork(() -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("curios"))
                com.mengsama.mod.mengsamanetmusic.client.renderer.CuriosHeadphonesRenderer.register();
            net.minecraft.client.gui.screens.MenuScreens.register(ModMenuTypes.EARBUDS.get(), com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudScreen::new);
            MusicPlayerBackground.reload();
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.MUSIC_PLAYER.get(),
                    MusicPlayerScreen::new);

            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.MUSIC_PLAYER_PLAYLIST.get(),
                    MusicPlayerPlaylistScreen::new);
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

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onLogin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback.resetSession();
        }
        @SubscribeEvent
        public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
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
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            MusicClientKeys.consumeHudEditor(() -> {
                if (mc.screen == null) MoveHudScreen.open();
            });
        }
    }
}
