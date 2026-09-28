package com.mengsama.mod.mengsamanetmusic.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MaidLyricSynchronizerOptimizationTest {
    @Test void playbackClockUsesStableTotalAndCurrentTime() {
        assertEquals(0, MaidLyricSynchronizer.playbackElapsedTicks(2_064, 2_064, 0));
        assertEquals(500, MaidLyricSynchronizer.playbackElapsedTicks(2_064, 1_500, 64));
        assertEquals(0, MaidLyricSynchronizer.playbackElapsedTicks(2_064, 3_000, 0));
    }

    @Test void normalTickSourceDoesNotScanLevelsOrReloadPlaylist() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/mengsama/mod/mengsamanetmusic/compat/MaidLyricSynchronizer.java"));
        int tickStart = source.indexOf("public static void onServerTick");
        int tickEnd = source.indexOf("private static boolean isPlaybackCurrent", tickStart);
        assertTrue(tickStart >= 0 && tickEnd > tickStart);
        String tickBody = source.substring(tickStart, tickEnd);
        assertFalse(tickBody.contains("getAllLevels"));
        assertFalse(tickBody.contains("loadAllCds"));
        assertTrue(source.contains("RECOVERY_INTERVAL_TICKS = 100"));
        assertTrue(source.contains("volatile ServerLevel maidLevel"));
        assertTrue(source.contains("findMaid(session.maidLevel"));
        assertTrue(source.contains("if (cachedLevel != null && cachedLevel.getEntity(maidId)"));
    }
}
