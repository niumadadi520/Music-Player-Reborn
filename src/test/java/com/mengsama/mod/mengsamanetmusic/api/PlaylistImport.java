package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

 
public final class PlaylistImport {
    private static final int LIMIT = 1000;
    private static final int BATCH = 100;
    @FunctionalInterface public interface Details { Map<Long, SongInfo> fetch(long[] ids) throws IOException; }

    public static List<SongInfo> load(String response, Details details) throws IOException {
        JsonObject root;
        try { root = JsonParser.parseString(response).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Invalid playlist response", ex); }
        JsonElement payload = root.has("playlist") ? root.get("playlist") : root.get("result");
        if (payload == null || !payload.isJsonObject()) throw new IOException("Playlist is unavailable");
        JsonObject list = payload.getAsJsonObject();
        LinkedHashSet<Long> order = new LinkedHashSet<>();
        JsonArray ids = array(list, "trackIds");
        for (JsonElement id : ids) {
            long value = id(id);
            if (value > 0 && order.size() < LIMIT) order.add(value);
        }
        Map<Long, SongInfo> songs = new HashMap<>();
        JsonArray tracks = array(list, "tracks");
        JsonObject songPayload = new JsonObject();
        songPayload.add("songs", tracks);
        songs.putAll(NetEaseApi.parseSongDetails(songPayload.toString()));
         
        if (ids.isEmpty()) for (JsonElement track : tracks) {
            long value = id(track);
            if (value > 0 && order.size() < LIMIT) order.add(value);
        }
        long[] missing = order.stream().filter(key -> !complete(songs.get(key))).mapToLong(Long::longValue).toArray();
        for (int start = 0; start < missing.length; start += BATCH) {
            Map<Long, SongInfo> fetched = details.fetch(Arrays.copyOfRange(missing, start, Math.min(missing.length, start + BATCH)));
            if (fetched != null) fetched.forEach((key, song) -> { if (complete(song) && key == song.songId) songs.put(key, song); });
        }
        List<SongInfo> result = new ArrayList<>(order.size());
        for (long key : order) {
            SongInfo song = songs.get(key);
            if (!complete(song)) throw new IOException("Playlist details incomplete for song " + key);
            result.add(song.clone());
        }
        return result;
    }

    private static boolean complete(SongInfo song) { return song != null && song.songId > 0 && song.isValid(); }
    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
    private static long id(JsonElement element) {
        try { return element.isJsonObject() ? element.getAsJsonObject().get("id").getAsLong() : element.getAsLong(); }
        catch (RuntimeException ignored) { return 0; }
    }
}
