package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackRefreshSessionsTest {
    @BeforeEach
    void reset() {
        PlaybackRefreshSessions.resetForTest();
    }

    @Test
    void acceptsNonceOnceForExactTargetGenerationIdentityAndOwner() {
        SongInfo song = qqSong("mid-a");
        PlaybackRefreshSessions.publish("target", 7L, song, "owner");

        assertTrue(PlaybackRefreshSessions.consume("target", 7L, 11L, song, "owner"));
        assertFalse(PlaybackRefreshSessions.consume("target", 7L, 11L, song, "owner"));
        assertFalse(PlaybackRefreshSessions.consume("target", 6L, 12L, song, "owner"));
        assertFalse(PlaybackRefreshSessions.consume("target", 7L, 12L, song, "other"));
        assertFalse(PlaybackRefreshSessions.consume("target", 7L, 12L, qqSong("mid-b"), "owner"));
    }

    @Test
    void newerGenerationReplacesOldBinding() {
        SongInfo song = qqSong("mid-a");
        PlaybackRefreshSessions.publish("target", 7L, song, "owner");
        PlaybackRefreshSessions.publish("target", 8L, song, "owner");

        assertFalse(PlaybackRefreshSessions.consume("target", 7L, 21L, song, "owner"));
        assertTrue(PlaybackRefreshSessions.consume("target", 8L, 22L, song, "owner"));
    }

    private static SongInfo qqSong(String mid) {
        SongInfo song = new SongInfo("https://y.qq.com/n/ryqq/songDetail/" + mid, "test", 1);
        song.source = "qq";
        song.providerId = mid;
        song.rawUrl = song.songUrl;
        song.normalizeIdentity();
        return song;
    }

    @Test void droppingDeviceInvalidatesEveryOldSlotButPreservesOtherPlayersDevices() {
        var device=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();
        SongInfo song=qqSong("mid-a");
        for(int slot:new int[]{0,12,40})PlaybackRefreshSessions.publish("item:owner:"+slot+":"+device,7,song,"owner");
        PlaybackRefreshSessions.publish("item:owner:1:"+other,7,song,"owner");
        PlaybackRefreshSessions.releaseDevice(device);
        for(int slot:new int[]{0,12,40})assertFalse(PlaybackRefreshSessions.consume("item:owner:"+slot+":"+device,7,33,song,"owner"));
        assertTrue(PlaybackRefreshSessions.consume("item:owner:1:"+other,7,33,song,"owner"));
    }
}
