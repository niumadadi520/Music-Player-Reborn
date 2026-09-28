package com.mengsama.mod.mengsamanetmusic.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LyricSessionGateIntegrationTest {
    @Test void threeSongsAndDelayedOldRequestCannotReplaceCurrentSong() {
        Harness h = new Harness();
        long first = h.start("song-1");
        long second = h.start("song-2");
        long third = h.start("song-3");
        assertFalse(h.lyricsArrive(first, "旧歌延迟歌词"));
        assertFalse(h.lyricsArrive(second, "第二首延迟歌词"));
        assertTrue(h.lyricsArrive(third, "第三首歌词"));
        assertEquals("第三首歌词", h.bubble);
    }

    @Test void lateOldEndCannotClearNewSong() {
        Harness h = new Harness();
        long old = h.start("old");
        long current = h.start("new");
        assertTrue(h.lyricsArrive(current, "新歌"));
        assertFalse(h.end(old));
        assertEquals("新歌", h.bubble);
    }

    @Test void replayingSameSongGetsFreshGeneration() {
        Harness h = new Harness();
        long firstPlay = h.start("same-song");
        long replay = h.start("same-song");
        assertNotEquals(firstPlay, replay);
        assertFalse(h.lyricsArrive(firstPlay, "第一次播放"));
        assertTrue(h.lyricsArrive(replay, "重播"));
    }

    @Test void failedSecondSongDoesNotBlockThirdSong() {
        Harness h = new Harness();
        long first = h.start("first");
        assertTrue(h.lyricsArrive(first, "一"));
        long failed = h.start("failed");
        assertTrue(h.fail(failed));
        long third = h.start("third");
        assertTrue(h.lyricsArrive(third, "三"));
        assertEquals("三", h.bubble);
    }

    @Test void loopManualSwitchAndStopAllInvalidatePriorCallbacks() {
        Harness h = new Harness();
        long loop1 = h.start("loop-song");
        long loop2 = h.start("loop-song");
        assertFalse(h.end(loop1));
        long manual = h.start("manual-song");
        assertFalse(h.lyricsArrive(loop2, "循环旧结果"));
        assertTrue(h.lyricsArrive(manual, "手动新歌"));
        h.stop();
        assertFalse(h.end(manual));
        assertNull(h.bubble);
    }

    private static final class Harness {
        final UUID maid = UUID.randomUUID();
        final LyricSessionGate gate = new LyricSessionGate();
        long current;
        String song;
        String bubble;

        long start(String song) {
            current = gate.next(maid);
            this.song = song;
            bubble = null;
            return current;
        }
        boolean lyricsArrive(long generation, String line) {
            if (!gate.isCurrent(maid, generation) || generation != current) return false;
            bubble = line;  
            return true;
        }
        boolean fail(long generation) {
            return gate.isCurrent(maid, generation) && generation == current;
        }
        boolean end(long generation) {
            if (!gate.isCurrent(maid, generation) || generation != current) return false;
            stop();
            return true;
        }
        void stop() {
            gate.invalidate(maid);
            bubble = null;
        }
    }
}
