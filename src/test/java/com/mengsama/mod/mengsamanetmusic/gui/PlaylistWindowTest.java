package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistWindowTest {
    private final List<SongInfo> songs = List.of(
            new SongInfo("https://example.invalid/1", "Alpha", 180),
            new SongInfo("https://example.invalid/2", "Beta", 120),
            new SongInfo("https://example.invalid/3", "Alpha reprise", 240));
    private final List<Integer> slots = List.of(2, 17, 53);

    @Test void filteredRowsKeepDeviceSlotsAndScrollAnchor() {
        var page = PlaylistWindow.filter(songs, slots, "alpha", 800, 53, 7, 30);
        assertEquals(List.of(2, 53), page.rows().stream().map(row -> row.slotIndex()).toList());
        assertSame(songs.get(2), page.rows().get(1).song());
        assertEquals(37, page.scroll());
        assertEquals(List.of(2, 17, 53), slots);
        assertEquals(3, songs.size());
    }
    @Test void removedAnchorRetainsFallbackInsteadOfUsingAnotherDeviceSlot() {
        var page = PlaylistWindow.filter(songs, slots, "alpha", 25, 17, 7, 30);
        assertEquals(25, page.scroll());
    }
    @Test void emptyResultsAndNegativeScrollAreSafe() {
        assertTrue(PlaylistWindow.filter(songs, slots, "missing", -4, 53, 0, 30).rows().isEmpty());
        assertEquals(0, PlaylistWindow.filter(songs, slots, "missing", -4, 53, 0, 30).scroll());
        assertEquals(0, PlaylistWindow.filter(songs, slots, "alpha", 0, 2, -3, 30).scroll());
    }
}
