package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshPlaybackPacketTest {
    @Test
    void roundTripPreservesNonceAndStableIdentity() {
        SongInfo song = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "song", 42);
        song.source = "netease";
        song.providerId = "123";
        song.songId = 123L;
        song.rawUrl = song.songUrl;
        RefreshPlaybackPacket original = new RefreshPlaybackPacket(
                new BlockPos(1, 2, 3), "block:test:1", 9L, 987654321L, song);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());

        RefreshPlaybackPacket.encode(original, encoded);
        RefreshPlaybackPacket decoded = RefreshPlaybackPacket.decode(encoded);

        assertEquals(original.blockPos(), decoded.blockPos());
        assertEquals(9L, decoded.requestGeneration());
        assertEquals(987654321L, decoded.requestNonce());
        assertTrue(song.sameIdentity(decoded.song()));
    }
}
