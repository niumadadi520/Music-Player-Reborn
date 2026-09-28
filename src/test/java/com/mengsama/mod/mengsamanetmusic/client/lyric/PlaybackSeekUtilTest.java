package com.mengsama.mod.mengsamanetmusic.client.lyric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackSeekUtilTest {
    @Test
    void mapsMiddleOfProgressBarToMiddleSecond() {
        assertEquals(50, PlaybackSeekUtil.secondAtFraction(0.5D, 100));
        assertEquals(50, PlaybackSeekUtil.secondAtFraction(0.501D, 100));
    }

    @Test
    void clampsMouseCoordinatesAndHandlesInvalidRatios() {
        assertEquals(0, PlaybackSeekUtil.secondAtFraction(-0.5D, 100));
        assertEquals(100, PlaybackSeekUtil.secondAtFraction(1.5D, 100));
        assertEquals(0, PlaybackSeekUtil.secondAtFraction(Double.NaN, 100));
        assertEquals(100, PlaybackSeekUtil.secondAtFraction(Double.POSITIVE_INFINITY, 100));
    }

    @Test
    void convertsSoundTicksToWholeSeconds() {
        assertEquals(0, PlaybackSeekUtil.secondAtTick(-1));
        assertEquals(49, PlaybackSeekUtil.secondAtTick(999));
        assertEquals(50, PlaybackSeekUtil.secondAtTick(1000));
    }
}
