package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

 
public final class KaraokeVoiceBridge {
    public record Target(UUID channelId, ServerLevel level, BlockPos pos, int volume) {
        public Target {
            if (channelId == null || level == null || pos == null) throw new IllegalArgumentException("Missing speaker target");
            pos = pos.immutable();
            volume = Math.max(0, Math.min(100, volume));
        }
    }

    interface Backend {
        boolean playerConnected(UUID player);
        void route(UUID performer, UUID nonce, byte[] opus, List<Target> targets);
        void endSession(UUID performer, UUID nonce);
        void tick();
        void close();
    }

    private static volatile Backend backend;
    private KaraokeVoiceBridge() {}

    public static boolean available() { return backend != null; }
    public static boolean playerConnected(UUID player) {
        Backend current = backend;
        return current != null && current.playerConnected(player);
    }

     
    public static void route(UUID performer, UUID sessionNonce, byte[] opus, List<Target> targets) {
        Backend current = backend;
        if (current != null && performer != null && sessionNonce != null && VoiceAudioMath.validPacket(opus)) {
            current.route(performer, sessionNonce, opus, targets);
        }
    }

     
    public static void endSession(UUID performer, UUID sessionNonce) {
        Backend current = backend;
        if (current != null) current.endSession(performer, sessionNonce);
    }

     
    public static void tick() {
        Backend current = backend;
        if (current != null) current.tick();
    }

    static synchronized void install(Backend replacement) {
        Backend previous = backend;
        backend = replacement;
        if (previous != null) previous.close();
    }
}
