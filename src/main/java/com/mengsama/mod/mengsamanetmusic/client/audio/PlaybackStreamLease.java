package com.mengsama.mod.mengsamanetmusic.client.audio;

import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

 
final class PlaybackStreamLease {
    private final BooleanSupplier current;
    private final Executor closer;
    private volatile boolean closed;
    private GuardedStream owned;

    PlaybackStreamLease(BooleanSupplier current, Executor closer) {
        this.current = current;
        this.closer = closer;
    }

    AudioStream attach(AudioStream decoder) {
        GuardedStream stream = new GuardedStream(decoder);
        synchronized (this) {
            if (!closed && current.getAsBoolean() && owned == null) {
                owned = stream;
                return stream;
            }
        }
        stream.close();
        return stream;
    }

    void close() {
        GuardedStream stream;
        synchronized (this) {
            closed = true;
            stream = owned;
            owned = null;
        }
        if (stream != null) stream.close();
    }

    private final class GuardedStream implements AudioStream {
        private final AudioStream decoder;
        private final AtomicBoolean stopped = new AtomicBoolean();
        GuardedStream(AudioStream decoder) { this.decoder = decoder; }
        public AudioFormat getFormat() { return decoder.getFormat(); }
        private boolean valid() { return !stopped.get() && !closed && current.getAsBoolean(); }
        public ByteBuffer read(int bytes) throws IOException {
            if (!valid()) { close(); return null; }
            ByteBuffer data = decoder.read(bytes);
             
            if (!valid()) { close(); return null; }
            return data;
        }
        public void close() {
            if (stopped.compareAndSet(false, true)) {
                 
                closer.execute(() -> {
                    try { decoder.close(); } catch (IOException | RuntimeException ignored) {}
                });
            }
        }
    }
}
