package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

 
public final class PlaybackRefreshSessions {
     
    private static final int MAX_SESSIONS = 4096;

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private record Session(long generation, String identityKey, String ownerKey, long consumedNonce) { }

    private PlaybackRefreshSessions() { }

    public static void publish(String targetId, long generation, SongInfo song, String ownerKey) {
        if (targetId == null || targetId.isBlank() || song == null) return;
        String identity = song.identityKey();
        if (identity.isBlank()) return;
        SESSIONS.compute(targetId, (ignored, previous) -> {
            if (previous != null && generation < previous.generation) return previous;
            long consumed = previous != null && generation == previous.generation ? previous.consumedNonce : 0L;
            return new Session(generation, identity, ownerKey == null ? "" : ownerKey, consumed);
        });
        trimIfNeeded();
    }

     
    public static void release(String targetId) {
        if (targetId != null) SESSIONS.remove(targetId);
    }

    public static void releaseDevice(java.util.UUID instance) {
        if (instance != null) SESSIONS.keySet().removeIf(target -> instance.equals(
                com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(target)));
    }

    private static void trimIfNeeded() {
        if (SESSIONS.size() <= MAX_SESSIONS) return;
         
        var it = SESSIONS.keySet().iterator();
        while (SESSIONS.size() > MAX_SESSIONS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

     
    public static boolean consume(String targetId, long generation, long nonce, SongInfo song, String ownerKey) {
        if (targetId == null || targetId.isBlank() || nonce <= 0L || song == null) return false;
        String identity = song.identityKey();
        if (identity.isBlank()) return false;
        final boolean[] accepted = {false};
        SESSIONS.computeIfPresent(targetId, (ignored, session) -> {
            if (session.generation != generation || session.consumedNonce == nonce
                    || !session.identityKey.equals(identity)
                    || !session.ownerKey.equals(ownerKey == null ? "" : ownerKey)) return session;
            accepted[0] = true;
            return new Session(session.generation, session.identityKey, session.ownerKey, nonce);
        });
        return accepted[0];
    }

    public static boolean isPublished(String target) { return SESSIONS.containsKey(target); }

    static void resetForTest() {
        SESSIONS.clear();
    }
}
