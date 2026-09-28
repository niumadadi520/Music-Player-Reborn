package com.mengsama.mod.mengsamanetmusic.network;

import java.util.concurrent.atomic.AtomicLong;

 
public final class PlaybackGenerations {
    private static final AtomicLong NEXT = new AtomicLong(System.currentTimeMillis() << 20);
    private PlaybackGenerations() {}
    public static long next() { return NEXT.incrementAndGet(); }
}
