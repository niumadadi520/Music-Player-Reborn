package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeekPlaybackPacketTest {
    @Test
    void acceptsLegacyEmptyIdentityButRejectsStaleSongIdentity() {
        SongInfo song = new SongInfo("https://example.invalid/song.mp3", "Current", 180);
        song.source = "netease";
        song.songId = 42;
        song.normalizeIdentity();

        assertTrue(SeekPlaybackPacket.matchesIdentity("", song));
        assertTrue(SeekPlaybackPacket.matchesIdentity(song.identityKey(), song));
        assertFalse(SeekPlaybackPacket.matchesIdentity("netease:43", song));
    }
}
