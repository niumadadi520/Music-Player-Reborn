package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;

 
public final class ArtworkMetadata {
    private ArtworkMetadata() {}
    public static URI firstAlbum(String payload) throws IOException {
        try {
            JsonArray songs = JsonParser.parseString(payload).getAsJsonObject().getAsJsonArray("songs");
            for (JsonElement row : songs) {
                if (!row.isJsonObject()) continue;
                JsonObject song = row.getAsJsonObject();
                for (String key : new String[]{"al", "album"}) {
                    JsonElement value = song.get(key);
                    if (value == null || !value.isJsonObject()) continue;
                    JsonElement location = value.getAsJsonObject().get("picUrl");
                    if (location == null || !location.isJsonPrimitive()) continue;
                    URI uri = URI.create(location.getAsString());
                    if (uri.getHost() != null && uri.getUserInfo() == null &&
                            ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return uri;
                }
            }
        } catch (RuntimeException malformed) { throw new IOException("Invalid album artwork metadata", malformed); }
        throw new IOException("Album artwork location is unavailable");
    }
}
