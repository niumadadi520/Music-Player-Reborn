package com.mengsama.mod.mengsamanetmusic.util;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaylistFilterTest {
    @Test
    void filtersTitleArtistAndAlbumWithoutChangingRealSlotsOrOrder() {
        SongInfo first = song("First Song", "Alice", "Blue Album");
        SongInfo second = song("Second Song", "Bob", "Red Album");
        SongInfo third = song("Third Song", "Alice", "Green Album");
        List<SongInfo> songs = List.of(first, second, third);
        List<Integer> slots = List.of(2, 7, 11);

        assertEquals(List.of(2, 11), PlaylistFilter.filter(songs, slots, "ALICE")
                .stream().map(PlaylistFilter.Match::slotIndex).toList());
        assertEquals(List.of(7), PlaylistFilter.filter(songs, slots, "red album")
                .stream().map(PlaylistFilter.Match::slotIndex).toList());
        assertEquals(List.of(11), PlaylistFilter.filter(songs, slots, "third")
                .stream().map(PlaylistFilter.Match::slotIndex).toList());
        assertEquals(List.of(2, 7, 11), PlaylistFilter.filter(songs, slots, "")
                .stream().map(PlaylistFilter.Match::slotIndex).toList());
    }

    private static SongInfo song(String title, String artist, String album) {
        SongInfo song = new SongInfo("https://example.invalid/audio", title, 180);
        song.artists.add(artist);
        song.albumName = album;
        return song;
    }
}
