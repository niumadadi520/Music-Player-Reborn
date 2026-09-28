package com.mengsama.mod.mengsamanetmusic.client.lyric;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

 
public final class ClientLyricStore {
    public enum State { IDLE, LOADING, READY, EMPTY, FAILED }

    public record Snapshot(String targetId, String identity, long generation, State state,
                           LyricRepository.LyricData data) {
        public static Snapshot idle(String targetId, long generation) {
            return new Snapshot(targetId, "", generation, State.IDLE, LyricRepository.LyricData.empty(false));
        }
    }

    private static final LyricRepository REPOSITORY = new LyricRepository(
            LyricRepository::loadFromProviders, AsyncIoExecutor.executor());
    private static final Map<String, Slot> SLOTS = new ConcurrentHashMap<>();

    private ClientLyricStore() {}

    public static Snapshot bind(String targetId, SongInfo song) {
        return bind(targetId, song, REPOSITORY);
    }

    static Snapshot bind(String targetId, SongInfo song, LyricRepository repository) {
        String target = targetId == null ? "" : targetId;
        Slot slot = SLOTS.computeIfAbsent(target, ignored -> new Slot(target));
        return slot.bind(song, repository);
    }

    public static Snapshot snapshot(String targetId) {
        Slot slot = SLOTS.get(targetId == null ? "" : targetId);
        return slot == null ? Snapshot.idle(targetId, 0L) : slot.snapshot;
    }

     
    public static SongInfo selectGuiSong(SongInfo activeSoundSong, boolean menuPlaying, SongInfo menuSong) {
        if (activeSoundSong != null) return activeSoundSong;
        return menuPlaying ? menuSong : null;
    }

     
    public static long playbackMillis(int soundTick) {
        return soundTick < 0 ? -1L : soundTick * 50L;
    }

    public static int lineIndexAtTick(LyricRepository.LyricData data, int soundTick) {
        long millis = playbackMillis(soundTick);
        return data == null || millis < 0 ? -1 : data.lineIndexAt(millis);
    }

    public static void clear(String targetId) {
        String target = targetId == null ? "" : targetId;
        SLOTS.computeIfAbsent(target, ignored -> new Slot(target)).clear();
    }

    static void resetForTest() { SLOTS.clear(); }

    private static final class Slot {
        private final String targetId;
        private final AtomicLong generations = new AtomicLong();
        private volatile Snapshot snapshot;

        private Slot(String targetId) {
            this.targetId = targetId;
            this.snapshot = Snapshot.idle(targetId, 0L);
        }

        private synchronized Snapshot bind(SongInfo input, LyricRepository repository) {
            SongInfo song = input == null ? null : input.clone();
            String identity = song == null ? "" : song.identityKey();
            if (identity.isBlank()) {
                clear();
                return snapshot;
            }
             
             
            if (identity.equals(snapshot.identity())) return snapshot;

            long generation = generations.incrementAndGet();
             
            snapshot = new Snapshot(targetId, identity, generation, State.LOADING, LyricRepository.LyricData.empty(false));
            repository.get(song).whenComplete((data, error) -> complete(generation, identity, data, error));
            return snapshot;
        }

        private synchronized void complete(long generation, String identity, LyricRepository.LyricData data, Throwable error) {
            if (snapshot.generation() != generation || !Objects.equals(snapshot.identity(), identity)) return;
            if (error != null) {
                snapshot = new Snapshot(targetId, identity, generation, State.FAILED, LyricRepository.LyricData.empty(false));
                return;
            }
            LyricRepository.LyricData safe = data == null ? LyricRepository.LyricData.empty(false) : data;
            snapshot = new Snapshot(targetId, identity, generation,
                    safe.lines().isEmpty() ? State.EMPTY : State.READY, safe);
        }

        private synchronized void clear() {
            long generation = generations.incrementAndGet();
            snapshot = Snapshot.idle(targetId, generation);
        }
    }
}
