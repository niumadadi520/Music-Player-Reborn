package com.mengsama.mod.mengsamanetmusic.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

 
public final class SearchGeneration {
    private final AtomicLong sequence = new AtomicLong();
    private volatile String currentQuery = "";
    private volatile boolean closed;

    public long begin(String query) {
        closed = false;
        currentQuery = query == null ? "" : query;
        return sequence.incrementAndGet();
    }

    public boolean isCurrent(long generation, String query) {
        return !closed && generation == sequence.get() && Objects.equals(currentQuery, query == null ? "" : query);
    }

    public void invalidate() {
        closed = true;
        sequence.incrementAndGet();
    }
}
