package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork;
import com.mengsama.mod.mengsamanetmusic.karaoke.client.KaraokeUi;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.UUID;

 
@EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID, value = Dist.CLIENT)
public final class KaraokeVoiceClient {
    interface CaptureBackend {
        void start(UUID nonce);
        void stop();
        void tick();
        String status();
    }

    private static volatile CaptureBackend backend;
    private static final MicrophoneStartGrant START_GRANT = new MicrophoneStartGrant(System::nanoTime);
    private static volatile UUID session;
    private static volatile String message = "需要 Simple Voice Chat 语音联动";
    private KaraokeVoiceClient() {}

    public static boolean available() { return backend != null; }
    public static void authorizeStart() { START_GRANT.authorize(); }
    public static void cancelStart() { START_GRANT.cancel(); }
    static boolean consumeStartGrant() { return START_GRANT.consume(); }
    public static UUID session() { return session; }
    public static String status() {
        CaptureBackend current = backend;
        return session != null && current != null ? current.status() : message;
    }

     
    public static void setSession(UUID nonce) {
        if (java.util.Objects.equals(session, nonce)) { cancelStart(); return; }
        boolean locallyAuthorized = nonce == null || consumeStartGrant();
        cancelStart();
        CaptureBackend current = backend;
        session = null;
        KaraokeUi.setCapturing(false);
        if (current != null) current.stop();
        if (nonce == null) {
            message = current == null ? "需要 Simple Voice Chat 语音联动" : "K歌麦克风已关闭";
            return;
        }
        if (!locallyAuthorized) {
            message = "未收到本地开麦操作，已拒绝启动麦克风";
            KaraokeNetwork.captureFailed(nonce, message);
            return;
        }
        if (current == null) {
            message = "Simple Voice Chat 尚未准备好";
            KaraokeNetwork.captureFailed(nonce, message);
            return;
        }
        session = nonce;
        KaraokeUi.setCapturing(true);
        message = "正在打开K歌麦克风";
        current.start(nonce);
    }

    public static void reset() { setSession(null); }

     
    public static void stopFromServer() {
        session = null;
        KaraokeUi.setCapturing(false);
        CaptureBackend current = backend;
        if (current != null) current.stop();
        message = current == null ? "需要 Simple Voice Chat 语音联动" : "K歌麦克风已关闭";
    }

    static void install(CaptureBackend replacement) {
        reset();
        backend = replacement;
        message = replacement == null ? "需要 Simple Voice Chat 语音联动" : "K歌麦克风已关闭";
    }

    static boolean isCurrent(UUID nonce) { return nonce.equals(session); }

    static void failed(UUID nonce, String reason) {
        Minecraft.getInstance().execute(() -> {
            if (!isCurrent(nonce)) return;
            reset();
            message = reason;
            if (Minecraft.getInstance().getConnection() != null) KaraokeNetwork.captureFailed(nonce, reason);
        });
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (session == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mc.player.isAlive()) reset();
        else if (backend != null) backend.tick();
    }
}
