package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.util.NetWorker;

import java.util.HashMap;
import java.util.Map;

public final class MetingApi {
    private static final String API_BASE = "https://api.qijieya.cn/meting/";

    private MetingApi() {
    }

    public static Map<String, String> playbackHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0");
        headers.put("Referer", "https://music.163.com/");
        return headers;
    }

    public static String getSongUrl(long songId) {
        try {
            String url = API_BASE + "?server=netease&type=url&id=" + songId;
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");

            String resolvedUrl = NetWorker.resolveRedirect(url, 5, headers);
            if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
                MengSamaNetMusic.LOGGER.debug("MetingApi resolved URL for id {}: {}", songId, resolvedUrl);
                return resolvedUrl;
            }

            return url;
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("MetingApi.getSongUrl failed for id {}: {}", songId, e.getMessage());
            return null;
        }
    }

    public static SongInfo getSongInfo(long songId) {
        try {
            String url = API_BASE + "?server=netease&type=song&id=" + songId;
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            String json = NetWorker.get(url, headers);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            if (arr.isEmpty()) return null;
            JsonObject song = arr.get(0).getAsJsonObject();

            SongInfo info = new SongInfo();
            if (song.has("name")) {
                info.songName = song.get("name").getAsString();
            }
            if (song.has("url") && !song.get("url").isJsonNull()) {
                info.songUrl = song.get("url").getAsString();
            }
            if (song.has("artist")) {
                String artist = song.get("artist").getAsString();
                for (String a : artist.split("/")) {
                    a = a.trim();
                    if (!a.isEmpty()) {
                        info.artists.add(a);
                    }
                }
            }

            info.songTime = 0;
            return info;
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("MetingApi.getSongInfo failed for id {}: {}", songId, e.getMessage());
            return null;
        }
    }

    public static boolean isMetingUrl(String url) {
        return url != null && url.contains("api.qijieya.cn/meting/");
    }
}
