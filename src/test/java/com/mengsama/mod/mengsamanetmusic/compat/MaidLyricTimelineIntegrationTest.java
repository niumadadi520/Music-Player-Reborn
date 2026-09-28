package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.api.LrcParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaidLyricTimelineIntegrationTest {
    private static String fakeProviderResponse() {
        return "[00:00.00]第一行\n[00:01.00]第二行\n[00:02.00]第三行\n[00:03.00]第四行";
    }

    @Test void bubbleLifetimeCoversNextLineAndLastLineUsesTlmDefault() {
        var lines = LrcParser.parseMillis(fakeProviderResponse());
        assertEquals(24, MaidLyricSynchronizer.bubbleDurationTicks(lines, 0), "1s + four tick scheduler slack");
        assertEquals(14, MaidLyricSynchronizer.bubbleDurationTicks(lines, 500));
        assertEquals(300, MaidLyricSynchronizer.bubbleDurationTicks(lines, 3_000));
    }

    @Test void fakeProviderAdvancesThreeLinesAndSupportsPauseSeekStop() {
        var lines = LrcParser.parseMillis(fakeProviderResponse());
        MaidLyricTimeline timeline = new MaidLyricTimeline();
        assertEquals("第一行", timeline.update(lines, 0, true));
        assertEquals("第二行", timeline.update(lines, 1_100, true));
        assertNull(timeline.update(lines, 1_900, false), "paused clock must not replace bubble");
        assertEquals("第二行", timeline.update(lines, 1_900, true),
                "resume must restore the current line because TLM bubble expiry uses wall clock");
        assertEquals("第三行", timeline.update(lines, 2_100, true));
        assertEquals("第四行", timeline.update(lines, 3_200, true));
        assertEquals("第二行", timeline.update(lines, 1_050, true), "seek must replace with target line");
        timeline.stop();
        assertNull(timeline.update(lines, 2_500, true), "stop removes cursor activity");
    }
}
