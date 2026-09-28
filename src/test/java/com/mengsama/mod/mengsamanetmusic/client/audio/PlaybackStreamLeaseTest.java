package com.mengsama.mod.mengsamanetmusic.client.audio;

import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackStreamLeaseTest {
    static class Decoder implements AudioStream {
        int closes;
        Runnable duringRead = () -> {};
        public AudioFormat getFormat() { return new AudioFormat(44100, 16, 2, true, false); }
        public ByteBuffer read(int size) { duringRead.run(); return ByteBuffer.allocate(4); }
        public void close() { closes++; }
    }

    @Test void stoppedBeforeDownloadCompletesCannotPublishAudio() throws Exception {
        var lease = new PlaybackStreamLease(() -> true, Runnable::run);
        lease.close();
        var decoder = new Decoder();
        var late = lease.attach(decoder);
        assertNull(late.read(4));
        assertEquals(1, decoder.closes);
    }

    @Test void stopClosesOwnedDecoderOnceEvenWhenEngineAlsoCloses() throws Exception {
        var lease = new PlaybackStreamLease(() -> true, Runnable::run);
        var decoder = new Decoder();
        var stream = lease.attach(decoder);
        assertNotNull(stream.read(4));
        lease.close(); lease.close(); stream.close();
        assertNull(stream.read(4));
        assertEquals(1, decoder.closes);
    }

    @Test void stopDoesNotWaitForPotentiallyBlockingNetworkClose() throws Exception {
        var queued = new ArrayList<Runnable>();
        var lease = new PlaybackStreamLease(() -> true, queued::add);
        var decoder = new Decoder();
        var stream = lease.attach(decoder);
        lease.close();
        assertEquals(0, decoder.closes);
        assertNull(stream.read(4));
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, decoder.closes);
    }

    @Test void generationChangedDuringReadDiscardsReturnedPcm() throws Exception {
        var current = new AtomicBoolean(true);
        var lease = new PlaybackStreamLease(current::get, Runnable::run);
        var decoder = new Decoder();
        decoder.duringRead = () -> current.set(false);
        assertNull(lease.attach(decoder).read(4));
        assertEquals(1, decoder.closes);
    }

    @Test void oldSongCompletingAfterNewSongCannotInterruptReplacement() throws Exception {
        var old = new PlaybackStreamLease(() -> true, Runnable::run);
        old.close();
        var replacement = new PlaybackStreamLease(() -> true, Runnable::run);
        var nextDecoder = new Decoder();
        var next = replacement.attach(nextDecoder);
        assertNull(old.attach(new Decoder()).read(4));
        assertNotNull(next.read(4));
        assertEquals(0, nextDecoder.closes);
        replacement.close();
    }

    @Test void concurrentStopAndAttachAlwaysCloseDecoderExactlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                var lease = new PlaybackStreamLease(() -> true, Runnable::run);
                var decoder = new Decoder();
                var gate = new CountDownLatch(1);
                var attached = executor.submit(() -> { gate.await(); return lease.attach(decoder); });
                var stopped = executor.submit(() -> { gate.await(); lease.close(); return null; });
                gate.countDown();
                var stream = attached.get(5, TimeUnit.SECONDS);
                stopped.get(5, TimeUnit.SECONDS);
                assertNull(stream.read(4));
                assertEquals(1, decoder.closes);
            }
        } finally { executor.shutdownNow(); }
    }

    @Test void duplicateStreamRequestCannotCreateSecondLiveDecoder() throws Exception {
        var lease = new PlaybackStreamLease(() -> true, Runnable::run);
        var first = lease.attach(new Decoder());
        var duplicate = new Decoder();
        assertNull(lease.attach(duplicate).read(4));
        assertEquals(1, duplicate.closes);
        assertNotNull(first.read(4));
        lease.close();
    }
}
