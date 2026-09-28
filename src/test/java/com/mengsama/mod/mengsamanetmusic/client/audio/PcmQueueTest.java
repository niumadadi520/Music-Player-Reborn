package com.mengsama.mod.mengsamanetmusic.client.audio;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcmQueueTest {
    @Test void partialReadsAcrossChunksPreserveBytesAndProducerCursors() {
        PcmQueue queue = new PcmQueue();
        ByteBuffer first = ByteBuffer.wrap(new byte[]{0,1,2,3,4}); first.position(1);
        queue.offer(first); queue.offer(ByteBuffer.wrap(new byte[]{5,6,7}));
        assertArrayEquals(new byte[]{1,2,3}, bytes(queue.drain(3)));
        assertArrayEquals(new byte[]{4,5,6}, bytes(queue.drain(3)));
        assertArrayEquals(new byte[]{7}, bytes(queue.drain(99)));
        assertNull(queue.drain(1)); assertEquals(1, first.position()); assertEquals(5, first.limit());
    }
    @Test void emptyAndCancelledQueueDoNotPublishData() {
        PcmQueue queue = new PcmQueue(); queue.offer(ByteBuffer.allocate(0)); assertNull(queue.drain(1));
        queue.offer(ByteBuffer.wrap(new byte[]{1,2})); assertNull(queue.drain(0));
        queue.clear(); assertNull(queue.drain(4));
    }
    @Test void playbackPlanIs16BitMonoOrStereoAndRejectsInvalidFormat() {
        var mono = PcmFormatPlan.forSource(new AudioFormat(22050, 8, 1, false, false));
        assertEquals(2, mono.playback().getFrameSize());
        var stereo = PcmFormatPlan.forSource(new AudioFormat(48000, 24, 2, true, true));
        assertEquals(4, stereo.playback().getFrameSize()); assertFalse(stereo.playback().isBigEndian());
        assertThrows(IllegalArgumentException.class, () -> PcmFormatPlan.forSource(new AudioFormat(-1,16,2,true,false)));
    }
    private byte[] bytes(ByteBuffer input) { byte[] bytes = new byte[input.remaining()]; input.get(bytes); return bytes; }
}
