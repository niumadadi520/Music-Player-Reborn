package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAudioQueueTest {
    @Test void dropsOldestAudioAfterOneHundredMilliseconds() {
        BoundedAudioQueue<Integer> queue = new BoundedAudioQueue<>(2, 5);
        UUID singer = UUID.randomUUID();
        for (int i = 0; i < 1_000; i++) assertTrue(queue.offer(singer, i));
        assertEquals(5, queue.size());
        for (int i = 995; i < 1_000; i++) assertEquals(i, queue.poll());
        assertNull(queue.poll());
    }

    @Test void roundRobinPreventsAHotSingerFromStarvingOthers() {
        BoundedAudioQueue<String> queue = new BoundedAudioQueue<>(2, 5);
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        queue.offer(one, "one-a"); queue.offer(one, "one-b"); queue.offer(two, "two");
        assertEquals("one-a", queue.poll());
        assertEquals("two", queue.poll());
        assertEquals("one-b", queue.poll());
    }

    @Test void performerCountIsBoundedIndependentlyOfFrames() {
        BoundedAudioQueue<Integer> queue = new BoundedAudioQueue<>(2, 5);
        UUID one = UUID.randomUUID(), two = UUID.randomUUID(), three = UUID.randomUUID();
        assertTrue(queue.offer(one, 1)); assertTrue(queue.offer(two, 2));
        assertFalse(queue.offer(three, 3));
        queue.remove(one);
        assertTrue(queue.offer(three, 3));
        assertEquals(2, queue.size());
    }

    @Test void revocationDropsAllPendingFramesForOnlyThatPerformer() {
        BoundedAudioQueue<String> queue = new BoundedAudioQueue<>(2, 5);
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        queue.offer(one, "revoked1"); queue.offer(two, "live"); queue.offer(one, "revoked2");
        queue.remove(one);
        assertEquals("live", queue.poll());
        assertNull(queue.poll());
        queue.offer(two, "next"); queue.clear();
        assertEquals(0, queue.size());
    }

    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedAudioQueue<>(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new BoundedAudioQueue<>(2, 0));
    }
}
