package com.mengsama.mod.mengsamanetmusic.gui;

 
final class QqLoginRequestState {
    private long generation;
    private boolean active;
    private boolean fetching;
    private boolean polling;
    private boolean pollInFlight;
    private long nextPollAt;
    void open() { active = true; invalidate(); }
    void close() { invalidate(); active = false; }
    void invalidate() { generation++; fetching = false; polling = false; pollInFlight = false; }
    boolean active() { return active; }
    boolean fetching() { return fetching; }
    boolean accepts(long token) { return active && token == generation; }
    long beginFetch() {
        if (!active || fetching) return -1;
        invalidate();
        fetching = true;
        return generation;
    }
    boolean finishFetch(long token, boolean success, long now) {
        if (!accepts(token)) return false;
        fetching = false;
        polling = success;
        nextPollAt = now + 1000;
        return true;
    }
    long beginPoll(long now) {
        if (!active || !polling || pollInFlight || now < nextPollAt) return -1;
        pollInFlight = true;
        return generation;
    }
    boolean finishPoll(long token, boolean retry, long now) {
        if (!accepts(token)) return false;
        pollInFlight = false;
        polling = retry;
        nextPollAt = now + 1800;
        return true;
    }
}
