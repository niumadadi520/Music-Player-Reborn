package com.mengsama.mod.mengsamanetmusic.network;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackCredentialTransportTest {
    @Test void listenerPacketsContainOnlyPlaybackHeadersAndNeverServerCredentials() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Map<String,String> source = Map.of("cOoKiE", "SERVER_SECRET", "Authorization", "Bearer SERVER_SECRET",
                    "Referer", "https://y.qq.com/", "User-Agent", "MusicClient");
            PlayerPlayMusicPacket.writeHeaders(buf, source);
            assertEquals(Map.of("Referer", "https://y.qq.com/", "User-Agent", "MusicClient"), PlayerPlayMusicPacket.readHeaders(buf));
            assertEquals(0, buf.readableBytes());
            assertEquals("SERVER_SECRET", source.get("cOoKiE"), "the server's original credential state must remain intact");
        } finally { buf.release(); }
    }
}
