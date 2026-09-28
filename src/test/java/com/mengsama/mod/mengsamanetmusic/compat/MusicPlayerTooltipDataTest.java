package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerTooltipDataTest {
    @Test
    void exposesOnlyFriendlyDisplayMetadata() {
        SongInfo song = new SongInfo("https://signed.example/audio?token=secret", "夜曲", 240);
        song.artists = List.of("周杰伦", "周杰伦", " Guest ");
        song.albumName = "十一月的萧邦";
        song.coverUrl = "https://img.example/cover.jpg";

        MusicPlayerTooltipData data = MusicPlayerTooltipData.of(true, true, song);

        assertTrue(data.playing());
        assertTrue(data.paused());
        assertEquals("夜曲", data.title());
        assertEquals("周杰伦 / Guest", data.artists());
        assertEquals("十一月的萧邦", data.album());
        assertEquals("https://img.example/cover.jpg", data.coverUrl());
        assertEquals("", data.targetId());
        assertNotNull(data.song());
        assertFalse(data.toString().contains("token=secret"), "playback URL must not enter Jade data");
    }

    @Test
    void notPlayingHasNoStaleMetadata() {
        SongInfo song = new SongInfo("https://example/audio", "Old Song", 120);
        MusicPlayerTooltipData data = MusicPlayerTooltipData.of(false, false, song);
        assertEquals(MusicPlayerTooltipData.empty(), data);
    }

    @Test
    void stripsControlCharactersWithoutCorruptingUnicode() {
        SongInfo song = new SongInfo("", "§a歌\u0000名\uFFFD", 1);
        song.artists = List.of("歌\u0007手");
        MusicPlayerTooltipData data = MusicPlayerTooltipData.of(true, false, song);
        assertEquals("歌名", data.title());
        assertEquals("歌手", data.artists());
    }

    @Test
    void rejectsNonWebArtworkUrlsAndBoundsLegacyText() {
        SongInfo song = new SongInfo("", "x".repeat(200), 1);
        song.coverUrl = "file:///tmp/private.png";
        MusicPlayerTooltipData data = MusicPlayerTooltipData.of(true, false, song);
        assertEquals(160, data.title().length());
        assertEquals("", data.coverUrl());
    }
}
