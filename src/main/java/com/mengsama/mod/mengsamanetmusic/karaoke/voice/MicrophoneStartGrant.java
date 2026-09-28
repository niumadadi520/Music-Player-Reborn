package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

 
final class MicrophoneStartGrant {
    static final long WINDOW_NANOS = 5_000_000_000L;
    private final LongSupplier clock;
    private final AtomicLong deadline = new AtomicLong();
    MicrophoneStartGrant(LongSupplier clock) { this.clock = clock; }
    void authorize() { deadline.set(clock.getAsLong() + WINDOW_NANOS); }
    void cancel() { deadline.set(0); }
    boolean consume() {
        long expires = deadline.getAndSet(0);
        return expires != 0 && expires - clock.getAsLong() > 0;
    }
}
