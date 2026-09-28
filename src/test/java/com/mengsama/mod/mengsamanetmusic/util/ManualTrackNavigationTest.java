package com.mengsama.mod.mengsamanetmusic.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ManualTrackNavigationTest {
    private final int[] counts = {0, 3, 0, 2};
    @Test void nextMovesWithinPlaylist() { assertEquals(new PlayMode.TrackPosition(1, 2), ManualTrackNavigation.move(1, 1, counts, 1)); }
    @Test void previousMovesWithinPlaylist() { assertEquals(new PlayMode.TrackPosition(1, 0), ManualTrackNavigation.move(1, 1, counts, -1)); }
    @Test void traversesEmptySlotsInBothDirections() {
        assertEquals(new PlayMode.TrackPosition(3, 0), ManualTrackNavigation.move(1, 2, counts, 1));
        assertEquals(new PlayMode.TrackPosition(1, 2), ManualTrackNavigation.move(3, 0, counts, -1));
    }
    @Test void wrapsAtBothEnds() {
        assertEquals(new PlayMode.TrackPosition(1, 0), ManualTrackNavigation.move(3, 1, counts, 1));
        assertEquals(new PlayMode.TrackPosition(3, 1), ManualTrackNavigation.move(1, 0, counts, -1));
    }
    @Test void emptyOrStaleIndicesDoNotLoseData() {
        assertEquals(new PlayMode.TrackPosition(1, 0), ManualTrackNavigation.move(0, 0, counts, 1));
        assertEquals(new PlayMode.TrackPosition(3, 1), ManualTrackNavigation.move(0, 0, counts, -1));
        assertEquals(new PlayMode.TrackPosition(0, 0), ManualTrackNavigation.move(0, 0, new int[54], -1));
        assertArrayEquals(new int[]{0, 3, 0, 2}, counts);
    }
}
