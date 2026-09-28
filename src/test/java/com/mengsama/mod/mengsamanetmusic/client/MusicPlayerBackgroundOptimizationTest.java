package com.mengsama.mod.mengsamanetmusic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerBackgroundOptimizationTest {
    @Test void failedBackgroundHasNoMoreThanOneRetryWithinOneMinute() {
        long failure = 10_000L;
        long retryAfter = MusicPlayerBackground.retryDeadline(failure);
        assertFalse(MusicPlayerBackground.retryAllowed(failure + 59_999_999_999L, retryAfter));
        assertTrue(MusicPlayerBackground.retryAllowed(retryAfter, retryAfter));
    }

    @Test void cropCacheKeyChangesForSourceOrTargetSize() {
        var first = MusicPlayerBackground.calculateCrop(1600, 900, 400, 300);
        var same = MusicPlayerBackground.calculateCrop(1600, 900, 400, 300);
        var changedTarget = MusicPlayerBackground.calculateCrop(1600, 900, 300, 300);
        var changedSource = MusicPlayerBackground.calculateCrop(1200, 900, 400, 300);
        assertTrue(first.matches(1600, 900, 400, 300));
        assertEquals(first, same);
        assertFalse(first.matches(changedTarget.sourceWidth(), changedTarget.sourceHeight(),
                changedTarget.targetWidth(), changedTarget.targetHeight()));
        assertFalse(first.matches(changedSource.sourceWidth(), changedSource.sourceHeight(),
                changedSource.targetWidth(), changedSource.targetHeight()));
    }
}
