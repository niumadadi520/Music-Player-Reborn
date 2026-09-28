package com.mengsama.mod.mengsamanetmusic.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayModeTest {
    private static final int[] TRACKS = {1, 3, 0, 2};

    @Test
    void sequentialTraversesNestedPlaylistThenWrapsToFirstDeviceSlot() {
        assertEquals(new PlayMode.TrackPosition(1, 1),
                PlayMode.nextTrack(PlayMode.SEQUENTIAL, 1, 0, TRACKS, ignored -> 0));
        assertEquals(new PlayMode.TrackPosition(3, 0),
                PlayMode.nextTrack(PlayMode.SEQUENTIAL, 1, 2, TRACKS, ignored -> 0));
        assertEquals(new PlayMode.TrackPosition(0, 0),
                PlayMode.nextTrack(PlayMode.SEQUENTIAL, 3, 1, TRACKS, ignored -> 0));
    }

    @Test
    void singleLoopKeepsExactSlotAndNestedSong() {
        assertEquals(new PlayMode.TrackPosition(1, 2),
                PlayMode.nextTrack(PlayMode.LOOP, 1, 2, TRACKS, ignored -> 0));
    }

    @Test
    void randomNeverReturnsCurrentTrackWhenAlternativesExist() {
        PlayMode.TrackPosition current = new PlayMode.TrackPosition(1, 1);
        for (int candidate = 0; candidate < 5; candidate++) {
            int chosen = candidate;
            assertNotEquals(current,
                    PlayMode.nextTrack(PlayMode.RANDOM, current.slotIndex(), current.songIndex(),
                            TRACKS, ignored -> chosen));
        }
    }

    @Test
    void oneTrackRandomAndInvalidCurrentAreStableAndSafe() {
        assertEquals(new PlayMode.TrackPosition(2, 0),
                PlayMode.nextTrack(PlayMode.RANDOM, 2, 0, new int[]{0, 0, 1}, ignored -> 0));
        assertEquals(new PlayMode.TrackPosition(1, 0),
                PlayMode.nextTrack(PlayMode.SEQUENTIAL, 99, 99, new int[]{0, 1}, ignored -> 0));
    }
}
