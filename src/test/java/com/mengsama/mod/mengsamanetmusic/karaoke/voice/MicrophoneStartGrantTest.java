package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MicrophoneStartGrantTest {
    @Test void unsolicitedAcknowledgementDoesNotAuthorizeCapture() {
        MicrophoneStartGrant grant = new MicrophoneStartGrant(() -> 1_000L);
        assertFalse(grant.consume());
        assertFalse(grant.consume());
    }

    @Test void oneLocalClickAuthorizesOnlyOneAcknowledgement() {
        AtomicLong clock = new AtomicLong(1_000);
        MicrophoneStartGrant grant = new MicrophoneStartGrant(clock::get);
        grant.authorize();
        clock.addAndGet(MicrophoneStartGrant.WINDOW_NANOS - 1);
        assertTrue(grant.consume());
        assertFalse(grant.consume());
    }

    @Test void lateServerAcknowledgementCannotOpenMicrophone() {
        AtomicLong clock = new AtomicLong(1_000);
        MicrophoneStartGrant grant = new MicrophoneStartGrant(clock::get);
        grant.authorize();
        clock.addAndGet(MicrophoneStartGrant.WINDOW_NANOS);
        assertFalse(grant.consume());
        assertFalse(grant.consume());
    }

    @Test void closingOrDisconnectingRevokesPendingRequest() {
        MicrophoneStartGrant grant = new MicrophoneStartGrant(() -> 1_000L);
        grant.authorize(); grant.cancel();
        assertFalse(grant.consume());
        grant.authorize();
        assertTrue(grant.consume());
    }
}
