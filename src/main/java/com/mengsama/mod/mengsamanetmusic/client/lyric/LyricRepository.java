package com.mengsama.mod.mengsamanetmusic.client.lyric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.LrcParser;
import com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import com.mengsama.mod.mengsamanetmusic.api.LyricPayload;

 


public final class LyricRepository {

    public enum ProviderRoute {

        NETEASE, QQ, APPLE, UNSUPPORTED
    }

    @FunctionalInterface
    public interface Loader {

        LyricData load(SongInfo song) throws Exception;
    }

    public record LyricData(NavigableMap<Long, String> lines, boolean explicitlyUnavailable) {

        public LyricData {
            lines = Collections.unmodifiableNavigableMap(new TreeMap<>(lines == null ? new TreeMap<>() : lines));
        }

        public static LyricData empty(boolean explicit) {
            return new LyricData(new TreeMap<>(), explicit);
        }

        public String lineAt(long playbackMillis) {
            if (lines.isEmpty())
                return "";
            String value = lines.floorEntry(Math.max(0L, playbackMillis)) == null ? "" : lines.floorEntry(Math.max(0L, playbackMillis)).getValue();
            return value == null ? "" : value;
        }

        public int lineIndexAt(long playbackMillis) {
            if (lines.isEmpty())
                return -1;
            Long key = lines.floorKey(Math.max(0L, playbackMillis));
            if (key == null)
                return -1;
            int index = 0;
            for (Long candidate : lines.keySet()) {
                if (candidate.equals(key))
                    return index;
                index++;
            }
            return -1;
        }
    }

    private final ConcurrentHashMap<String, CompletableFuture<LyricData>> cache = new ConcurrentHashMap<>();

    private final Loader loader;

    private final Executor executor;

    public LyricRepository(Loader loader, Executor executor) {
        this.loader = loader;
        this.executor = executor;
    }

    public CompletableFuture<LyricData> get(SongInfo input) {
        SongInfo song = input == null ? null : input.clone();
        String identity = song == null ? "" : song.identityKey();
        if (identity.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Missing song identity"));
        CompletableFuture<LyricData> future = cache.computeIfAbsent(identity, ignored -> com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor.supplyAsync(() -> {
            try {
                return loader.load(song);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }, executor));
         
        future.whenComplete((value, error) -> {
            if (error != null)
                cache.remove(identity, future);
        });
        return future;
    }

    public void invalidate(String identity) {
        if (identity != null)
            cache.remove(identity);
    }

    public static ProviderRoute route(SongInfo song) {
        if (song == null)
            return ProviderRoute.UNSUPPORTED;
        song.normalizeIdentity();
        return switch(song.source) {
            case "netease" ->
                song.songId > 0 ? ProviderRoute.NETEASE : ProviderRoute.UNSUPPORTED;
            case "qq" ->
                song.providerId != null && !song.providerId.isBlank() ? ProviderRoute.QQ : ProviderRoute.UNSUPPORTED;
            case "apple" ->
                ProviderRoute.APPLE;
            default ->
                ProviderRoute.UNSUPPORTED;
        };
    }

    public static LyricData loadFromProviders(SongInfo song) throws Exception {
        return switch(route(song)) {
            case NETEASE ->
                parseNetease(MengSamaNetMusic.NET_EASE_API.lyric(song.songId));
            case QQ ->
                new LyricData(LrcParser.parseMillis(QqMusicUtils.getLyric(song.providerId)), false);
            case APPLE ->
                LyricData.empty(true);
            case UNSUPPORTED ->
                LyricData.empty(true);
        };
    }

    static LyricData parseNetease(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        NavigableMap<Long, String> original = LrcParser.parseMillis(LyricPayload.text(root, "lrc", "lyric", "lrcText", "originalLyric"));
        NavigableMap<Long, String> translated = LrcParser.parseMillis(LyricPayload.text(root, "tlyric", "lyric", "translatedLyric", "translation", "transLyric"));
        return new LyricData(translated.isEmpty() ? original : LrcParser.mergeTranslation(original, translated), false);
    }
}
