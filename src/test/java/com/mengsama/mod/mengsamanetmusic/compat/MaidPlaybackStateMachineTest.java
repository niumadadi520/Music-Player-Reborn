package com.mengsama.mod.mengsamanetmusic.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mengsama.mod.mengsamanetmusic.compat.MaidPlaybackStateMachine.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class MaidPlaybackStateMachineTest {
    @Test
    void addAndPlayNowSurvivesInitialTaskAndNbtSyncDelay() {
        assertEquals(WAIT_FOR_SYNC, MaidPlaybackStateMachine.evaluate(
                true, true, false, true, false, false, 0));
        assertEquals(WAIT_FOR_SYNC, MaidPlaybackStateMachine.evaluate(
                true, true, true, true, false, false, 1));
        assertEquals(VALID, MaidPlaybackStateMachine.evaluate(
                true, true, true, true, true, false, 2));
    }

    @Test
    void unresolvedBindingStopsAfterBoundedGracePeriod() {
        assertEquals(WAIT_FOR_SYNC, MaidPlaybackStateMachine.evaluate(
                true, true, false, false, false, false,
                MaidPlaybackStateMachine.SYNC_GRACE_TICKS - 1));
        assertEquals(STOP, MaidPlaybackStateMachine.evaluate(
                true, true, false, false, false, false,
                MaidPlaybackStateMachine.SYNC_GRACE_TICKS));
    }

    @Test
    void removingDeviceOrChangingTaskStopsImmediatelyAfterBindingWasConfirmed() {
        assertEquals(STOP, MaidPlaybackStateMachine.evaluate(
                true, true, true, false, false, true, 3));
        assertEquals(STOP, MaidPlaybackStateMachine.evaluate(
                true, true, false, true, true, true, 3));
    }

    @Test
    void deadOrWrongEntityNeverReceivesSyncGrace() {
        assertEquals(STOP, MaidPlaybackStateMachine.evaluate(
                false, true, false, false, false, false, 0));
        assertEquals(STOP, MaidPlaybackStateMachine.evaluate(
                true, false, true, true, true, false, 0));
    }

    @Test
    void targetBindingIncludesMaidAndPhysicalDeviceIdentity() {
        UUID maid = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        String target = "maid:minecraft:overworld:" + maid + ":" + device;

        assertTrue(MaidPlaybackStateMachine.targetMatches(target, maid, device));
        assertEquals(device, PlaybackTargetId.instanceId(target));
        assertFalse(MaidPlaybackStateMachine.targetMatches(target, maid, UUID.randomUUID()));
        assertNull(PlaybackTargetId.instanceId("legacy-player:12"));
    }
}
