package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistScreen;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerScreen;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeState;
import com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

 
@EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID, value = Dist.CLIENT)
public final class KaraokeUi {
    private static KaraokeState state;
    private static KaraokeState serverState;
    private static AbstractContainerMenu boundMenu;
    private static AbstractContainerScreen<?> boundScreen;
    private static KaraokeSkin.Button settings, toggle;
    private static Rect header;
    private static boolean capturing;
    private static String lastNotice = "";
    private static String visibleNotice = "";
    private static int noticeTicks;

    private KaraokeUi() {}

    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MusicPlayerScreen) && !(event.getScreen() instanceof MusicPlayerPlaylistScreen)) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        if (boundMenu != screen.getMenu()) { state = null; serverState = null; lastNotice = ""; noticeTicks = 0; }
        boundMenu = screen.getMenu();
        boundScreen = screen;
        var ui = MusicPlayerUiConfig.get();
        var playerLayout = RosewoodPlayerLayout.fit(ui.screenWidth, ui.screenHeight, screen.width, screen.height);
        header = KaraokeLayout.header(playerLayout, (screen.width - playerLayout.width()) / 2,
                (screen.height - playerLayout.height()) / 2);
        settings = KaraokeSkin.button(header, "K歌设置", () -> open(screen));
        toggle = KaraokeSkin.button(header, "开麦", () -> toggle(screen.getMenu().containerId));
        settings.visible = toggle.visible = false;
        settings.tooltip("麦克风设置或音响连接与音量");
        toggle.tooltip("开启后独立采集麦克风，仅从连接的音响播放；再次点击关闭");
        event.addListener(settings);
        event.addListener(toggle);
        updateHeader();
        KaraokeNetwork.sendAction(screen.getMenu().containerId, KaraokeNetwork.Action.REFRESH, "");
    }

    public static KaraokeState current(int containerId) {
        return state != null && state.containerId() == containerId ? state : null;
    }

    public static void accept(KaraokeState incoming) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId != incoming.containerId()) return;
        serverState = incoming;
        state = localAvailability(incoming);
        String notice = incoming.message() == null ? "" : incoming.message();
        if (!notice.isBlank() && !notice.equals(lastNotice) && !(mc.screen instanceof KaraokeSettingsScreen)) {
            visibleNotice = notice;
            noticeTicks = 120;
        }
        lastNotice = notice;
        updateHeader();
        if (mc.screen instanceof KaraokeSettingsScreen screen) screen.accept(state);
    }

    private static KaraokeState localAvailability(KaraokeState value) {
        return new KaraokeState(value.containerId(), value.kind(), value.microphoneId(), value.enabled(), value.mine(),
                value.voiceAvailable() && KaraokeVoiceClient.available(), value.volume(), value.connections(), value.message());
    }

     
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        
        if (noticeTicks > 0) noticeTicks--;
        if (serverState == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId != serverState.containerId()) return;
        boolean available = serverState.voiceAvailable() && KaraokeVoiceClient.available();
        if (state == null || state.voiceAvailable() == available) return;
        state = localAvailability(serverState);
        updateHeader();
        if (mc.screen instanceof KaraokeSettingsScreen screen) screen.accept(state);
    }

     
    @SubscribeEvent public static void renderNotice(ScreenEvent.Render.Post event) {
        if (noticeTicks <= 0 || (!(event.getScreen() instanceof MusicPlayerScreen)
                && !(event.getScreen() instanceof MusicPlayerPlaylistScreen))) return;
        var mc = Minecraft.getInstance();
        var screen = event.getScreen();
        int w = Math.max(1, Math.min(screen.width - 20, mc.font.width(visibleNotice) + 16));
        int x = (screen.width - w) / 2, y = screen.height - 20;
        var g = event.getGuiGraphics();
        g.fill(x, y, x + w, y + 15, 0xF04F3535);
        g.renderOutline(x, y, w, 15, 0xFFD7A19D);
        String shown = mc.font.plainSubstrByWidth(visibleNotice, Math.max(1, w - 12));
        g.drawString(mc.font, shown, x + 6, y + 3, 0xFFFFE2CF, false);
        if (event.getMouseX() >= x && event.getMouseX() < x + w && event.getMouseY() >= y && event.getMouseY() < y + 15)
            g.renderTooltip(mc.font, mc.font.split(Component.literal(visibleNotice), Math.max(80, Math.min(290, screen.width - 24))), event.getMouseX(), event.getMouseY());
    }

    private static void updateHeader() {
        if (settings == null || toggle == null || header == null || boundScreen == null) return;
        KaraokeState value = current(boundScreen.getMenu().containerId);
        boolean microphone = isMicrophone(value);
        boolean speaker = value != null && value.kind() == KaraokeState.Kind.SPEAKER;
        settings.visible = settings.active = hasSettings(value);
        int half = (header.width() - 2) / 2;
        settings.bounds(microphone ? new Rect(header.x(), header.y(), half, header.height()) : header);
        settings.setMessage(Component.literal(microphone ? "K歌设置" : "音响设置"));
        settings.lit(value != null && value.enabled() && value.mine());
        toggle.visible = microphone;
        toggle.bounds(new Rect(header.x() + half + 2, header.y(), Math.max(1, header.width() - half - 2), header.height()));
        toggle.active = canToggle(value);
        toggle.setMessage(Component.literal(value != null && value.enabled() ? value.mine() ? "关麦" : "占用" : "开麦"));
        toggle.lit(value != null && value.enabled());
    }

    static boolean isMicrophone(KaraokeState value) {
        return value != null && (value.kind() == KaraokeState.Kind.HANDHELD || value.kind() == KaraokeState.Kind.STANDING);
    }

    static boolean hasSettings(KaraokeState value) {
        return isMicrophone(value) || value != null && value.kind() == KaraokeState.Kind.SPEAKER;
    }

    static boolean canToggle(KaraokeState value) {
        return isMicrophone(value) && (value.enabled() ? value.mine() : value.voiceAvailable());
    }

    static void toggle(int containerId) {
        KaraokeState value = current(containerId);
        if (canToggle(value)) KaraokeNetwork.sendAction(containerId, KaraokeNetwork.Action.TOGGLE, Boolean.toString(!value.enabled()));
    }

    private static void open(AbstractContainerScreen<?> parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != parent.getMenu()) return;
        KaraokeState value = current(parent.getMenu().containerId);
        if (!hasSettings(value)) return;
        if (parent instanceof MusicPlayerScreen player) player.prepareForChildScreen();
        if (parent instanceof MusicPlayerPlaylistScreen player) player.prepareForChildScreen();
        mc.setScreen(new KaraokeSettingsScreen(parent));
    }

     
    public static void setCapturing(boolean enabled) { capturing = enabled; }

    @SubscribeEvent public static void renderCaptureStatus(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!capturing || mc.player == null || mc.level == null || mc.options.hideGui) return;
        String label = "K歌麦克风已开启";
        int w = mc.font.width(label) + 24;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        var g = event.getGuiGraphics();
        g.fill(x, 1, x + w, 14, 0xE64F3535);
        g.renderOutline(x, 1, w, 13, 0xFFD7A19D);
        g.fill(x + 5, 5, x + 9, 9, 0xFFFF93A5);
        g.drawString(mc.font, label, x + 14, 3, 0xFFFFE2CF, false);
    }

    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() { capturing = false; lastNotice = visibleNotice = ""; noticeTicks = 0; state = serverState = null; boundMenu = null; boundScreen = null; settings = toggle = null; header = null; }
}
