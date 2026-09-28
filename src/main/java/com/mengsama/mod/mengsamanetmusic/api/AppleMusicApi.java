package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.util.NetWorker;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

 



public final class AppleMusicApi {
    private static final String ENDPOINT = "https://itunes.apple.com/search";
    private static final int PREVIEW_SECONDS = 30;

    private AppleMusicApi() {}

    public static List<NetEaseSearchResult> search(String term) throws Exception {
        String query = URLEncoder.encode(term == null ? "" : term.trim(), StandardCharsets.UTF_8);
        String url = ENDPOINT + "?term=" + query + "&country=CN&media=music&entity=song&limit=30&explicit=No";
        String json = NetWorker.get(url, Map.of("Accept", "application/json", "User-Agent", "MengSamaNetMusic/1.0"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray rows = root.has("results") && root.get("results").isJsonArray()
                ? root.getAsJsonArray("results") : new JsonArray();
        List<NetEaseSearchResult> result = new ArrayList<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String id = text(row, "trackId");
            String title = text(row, "trackName");
            String artist = text(row, "artistName");
            String preview = text(row, "previewUrl");
            if (id.isBlank() || title.isBlank() || !isSafePreviewUrl(preview)) continue;
            int duration = row.has("trackTimeMillis") ? Math.max(0, row.get("trackTimeMillis").getAsInt() / 1000) : 0;
            result.add(new NetEaseSearchResult(id, title, artist, false, "apple", preview,
                    highResolutionArtwork(text(row, "artworkUrl100")), text(row, "collectionName"), duration));
        }
        return result;
    }

    public static SongInfo toSong(NetEaseSearchResult row) {
        if (row == null || !row.isApple() || !isSafePreviewUrl(row.getAlbumMid())) return null;
        SongInfo song = new SongInfo(row.getAlbumMid(), row.getSongName(),
                row.getDuration() > 0 ? Math.min(PREVIEW_SECONDS, row.getDuration()) : PREVIEW_SECONDS);
        song.source = "apple";
        song.providerId = row.getSongId();
        song.albumName = row.getAlbumName();
        song.coverUrl = highResolutionArtwork(row.getCoverUrl());
        song.picUrl = song.coverUrl;
        if (row.getArtistName() != null && !row.getArtistName().isBlank()) song.artists.add(row.getArtistName());
        song.normalizeIdentity();
        return song;
    }

     
    public static String highResolutionArtwork(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("mzstatic.com") || host.endsWith(".mzstatic.com"))) return raw;
            String path = uri.getRawPath();
            if (path == null || !path.matches(".*[/]100x100[bB][bB](?:[-][0-9]+)?[.][a-zA-Z0-9]+$")) return raw;
            String upgraded = path.replaceFirst("/100x100([bB][bB])", "/600x600$1");
            return new URI(uri.getScheme(), uri.getRawAuthority(), upgraded, uri.getRawQuery(), uri.getRawFragment()).toASCIIString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    public static boolean isSafePreviewUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equals("mzstatic.com") || host.endsWith(".mzstatic.com")
                    || host.equals("itunes.apple.com") || host.endsWith(".itunes.apple.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String text(JsonObject row, String key) {
        if (!row.has(key) || row.get(key).isJsonNull()) return "";
        try { return row.get(key).getAsString(); } catch (Exception ignored) { return ""; }
    }
}
