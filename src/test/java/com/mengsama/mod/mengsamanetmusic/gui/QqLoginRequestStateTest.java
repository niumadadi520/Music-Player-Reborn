package com.mengsama.mod.mengsamanetmusic.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QqLoginRequestStateTest {
    @Test void closingAndReopeningRejectsAnOldQrCompletion() {
        var state = new QqLoginRequestState();
        state.open();
        long oldFetch = state.beginFetch();
        state.close();
        assertFalse(state.finishFetch(oldFetch, true, 0));
        assertEquals(-1, state.beginPoll(100000));
        state.open();
        long current = state.beginFetch();
        assertFalse(state.finishFetch(oldFetch, true, 0));
        assertTrue(state.finishFetch(current, true, 0));
        assertEquals(current, state.beginPoll(1000));
    }

    @Test void pendingFetchCannotBeQueuedRepeatedlyAndFailuresCanRetry() {
        var state = new QqLoginRequestState();
        state.open();
        long request = state.beginFetch();
        for (int click = 0; click < 20; click++) assertEquals(-1, state.beginFetch());
        assertTrue(state.finishFetch(request, false, 0));
        assertEquals(-1, state.beginPoll(5000));
        assertTrue(state.beginFetch() > request);
    }

    @Test void pollingHasOnlyOneInFlightRequestAndUsesBackoff() {
        var state = new QqLoginRequestState();
        state.open();
        long request = state.beginFetch();
        state.finishFetch(request, true, 200);
        assertEquals(-1, state.beginPoll(1199));
        assertEquals(request, state.beginPoll(1200));
        assertEquals(-1, state.beginPoll(9999));
        assertTrue(state.finishPoll(request, true, 1400));
        assertEquals(-1, state.beginPoll(3199));
        assertEquals(request, state.beginPoll(3200));
        state.finishPoll(request, false, 3500);
        assertEquals(-1, state.beginPoll(9999));
    }

    @Test void refreshInvalidatesPendingPollAndLogoutCannotRestartIt() {
        var state = new QqLoginRequestState();
        state.open();
        long first = state.beginFetch();
        state.finishFetch(first, true, 0);
        state.beginPoll(1000);
        long refresh = state.beginFetch();
        assertFalse(state.finishPoll(first, true, 1100));
        assertTrue(state.fetching());
        state.finishFetch(refresh, true, 1200);
        state.invalidate();
        assertFalse(state.finishFetch(refresh, true, 1500));
        assertFalse(state.finishPoll(refresh, true, 1500));
        assertEquals(-1, state.beginPoll(9999));
        assertTrue(state.active());
    }
}
