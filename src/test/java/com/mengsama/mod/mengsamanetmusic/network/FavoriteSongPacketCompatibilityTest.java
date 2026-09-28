package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

 
class FavoriteSongPacketCompatibilityTest {
    private static final BlockPos POSITION = new BlockPos(17, 64, -31);

    @Test
    void neteaseFavoriteWithoutCachedUrlStillMatchesTheStoredSong() {
        SongInfo stored = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "Stored", 180);
        SongInfo favorite = new SongInfo("", "Favorite", 180);
        favorite.source = "netease";
        favorite.songId = 123;

        for (boolean block : new boolean[]{false, true}) {
            SongInfo received = roundTrip(block, favorite, true);
            assertEquals("netease:123", received.identityKey());
            assertTrue(received.sameIdentity(stored));
            assertTrue(received.canRefreshProvider());
            assertEquals("https://music.163.com/song/media/outer/url?id=123.mp3", received.rawUrl);
        }
    }

    @Test
    void oldQqSignedUrlsDoNotCreateDistinctSongIdentities() {
        SongInfo stored = new SongInfo("https://dl.stream.qqmusic.qq.com/old.m4a?vkey=expired", "Stored", 180);
        stored.source = "qq";
        stored.providerId = "001SongMid";
        SongInfo favorite = new SongInfo("https://dl.stream.qqmusic.qq.com/new.m4a?vkey=current", "Favorite", 180);
        favorite.source = "qq";
        favorite.providerId = "001SongMid";

        for (boolean block : new boolean[]{false, true}) {
            SongInfo received = roundTrip(block, favorite, true);
            assertTrue(received.sameIdentity(stored));
            assertEquals("qq:001SongMid", received.identityKey());
            assertEquals("https://y.qq.com/n/ryqq/songDetail/001SongMid", received.rawUrl);
            assertTrue(received.canRefreshProvider());
        }
    }

    @Test
    void bothPacketTypesPreserveAddOnlyAndImmediatePlaybackRequests() {
        SongInfo song = new SongInfo("https://music.163.com/song/media/outer/url?id=456.mp3", "Song", 60);
        for (boolean block : new boolean[]{false, true}) {
            for (boolean playNow : new boolean[]{false, true}) {
                assertTrue(roundTrip(block, song, playNow).sameIdentity(song));
            }
        }
    }

    @Test
    void missingOrUnknownProviderIdentityDoesNotMatchAnUnrelatedStoredSong() {
        SongInfo stored = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "Stored", 180);
        SongInfo missing = new SongInfo("", "Missing identity", 180);
        SongInfo unknownSource = new SongInfo("", "Unknown source", 180);
        unknownSource.songId = 123;

        for (boolean block : new boolean[]{false, true}) {
            SongInfo received = roundTrip(block, missing, true);
            assertFalse(received.sameIdentity(stored));
            assertFalse(received.sameIdentity(new SongInfo()));
            assertFalse(roundTrip(block, unknownSource, true).sameIdentity(stored));
        }
    }

    private static SongInfo roundTrip(boolean block, SongInfo song, boolean playNow) {
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf restored = new FriendlyByteBuf(Unpooled.buffer());
        try {
            if (block) {
                BlockAddSongPacket.encode(new BlockAddSongPacket(POSITION, song, playNow), wire);
                BlockAddSongPacket.encode(BlockAddSongPacket.decode(wire), restored);
                assertEquals(POSITION, restored.readBlockPos());
            } else {
                PlayerAddSongPacket.encode(new PlayerAddSongPacket(song, playNow), wire);
                PlayerAddSongPacket.encode(PlayerAddSongPacket.decode(wire), restored);
            }
            SongInfo received = SongInfo.deserializeNBT(restored.readNbt());
            assertEquals(playNow, restored.readBoolean(), "the server must receive the requested action");
            assertEquals(0, wire.readableBytes());
            assertEquals(0, restored.readableBytes());
            return received;
        } finally {
            wire.release();
            restored.release();
        }
    }
}
