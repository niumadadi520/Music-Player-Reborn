package com.mengsama.mod.mengsamanetmusic.api;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

 
final class QqLoginSessionGuard {
    private long generation;

    synchronized long renew(Runnable clearState) {
        generation++;
        clearState.run();
        return generation;
    }

    synchronized long generation() { return generation; }
    synchronized boolean isCurrent(long token) { return generation == token; }

    synchronized void requireCurrent(long token) {
        if (generation != token) throw new CancellationException("QQ login session was replaced");
    }

    synchronized void commit(long token, Runnable action) {
        requireCurrent(token);
        action.run();
    }

    synchronized <T> T read(long token, Supplier<T> value) {
        requireCurrent(token);
        return value.get();
    }
}
