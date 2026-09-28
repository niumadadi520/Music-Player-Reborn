package com.mengsama.mod.mengsamanetmusic.karaoke;

 
public final class KaraokeFrameBudget {
    private double tokens = 10;
    private long last;
    public synchronized boolean take(long nanos) {
        if (last != 0) tokens = Math.min(10, tokens + Math.max(0, nanos-last) * 60D / 1_000_000_000D);
        last = nanos;
        if (tokens < 1) return false;
        tokens--; return true;
    }
}
