package com.mengsama.mod.mengsamanetmusic.compat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

 
public final class LyricSessionGate {
    private final ConcurrentHashMap<UUID, AtomicLong> generations = new ConcurrentHashMap<>();

    public long next(UUID maidId) {
        return generations.computeIfAbsent(maidId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public boolean isCurrent(UUID maidId, long generation) {
        AtomicLong current = generations.get(maidId);
        return current != null && current.get() == generation;
    }

    public long invalidate(UUID maidId) { return next(maidId); }
}
