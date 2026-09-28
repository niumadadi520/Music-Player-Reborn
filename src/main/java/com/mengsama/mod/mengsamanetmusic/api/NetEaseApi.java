package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.util.NetWorker;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NetEaseApi {

    private volatile String cookie = "";
    private static final Map<String,String> BASE_HEADERS = Map.of(
            "Content-Type", "application/x-www-form-urlencoded", "User-Agent", "Mozilla/5.0",
            "Origin", "https://music.163.com", "Referer", "https://music.163.com/");
    public Map<String,String> getRequestPropertyData() {
        String credential = cookie;
        if (credential.isBlank()) return BASE_HEADERS;
        var headers = new HashMap<>(BASE_HEADERS);
        headers.put("Cookie", credential);
        return Map.copyOf(headers);
    }
    public void setCookie(String value) { cookie = value == null ? "" : value.strip(); }
    private String query(String endpoint, Map<String,String> fields) throws IOException {
        String encoded = fields.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        return NetWorker.get("https://music.163.com/api/" + endpoint + "?" + encoded, getRequestPropertyData());
    }
    public String getPlayInfo(long musicId, NetEaseMusicLevel level) throws IOException {
        return query("song/enhance/player/url/v1", Map.of("encodeType", "flac", "ids", "[" + musicId + "]",
                "level", level.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public String search(String key, int type, int limit) throws IOException {
        String url = String.format("https://music.163.com/api/search/get/web?s=%s&type=%d&limit=%d",
                com.google.common.net.UrlEscapers.urlPathSegmentEscaper().escape(key), type, limit);
        return NetWorker.get(url, getRequestPropertyData());
    }

    public String list(long id) throws IOException {
        String url = String.format("http://music.163.com/api/playlist/detail?id=%d", id);
        return NetWorker.get(url, getRequestPropertyData());
    }

     
    public static java.util.List<NetEaseSearchResult> parseSearchResults(String json) {
        java.util.List<NetEaseSearchResult> results = new java.util.ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject result = object(root, "result");
            JsonArray songs = array(result, "songs");
            if (songs == null) return results;
            for (JsonElement element : songs) {
                if (!element.isJsonObject()) continue;
                JsonObject song = element.getAsJsonObject();
                String id = string(song, "id");
                String name = string(song, "name");
                if (id.isBlank() || name.isBlank()) continue;

                JsonArray artists = firstArray(song, "ar", "artists");
                java.util.List<String> artistNames = new java.util.ArrayList<>();
                if (artists != null) {
                    for (JsonElement artist : artists) {
                        if (!artist.isJsonObject()) continue;
                        String artistName = string(artist.getAsJsonObject(), "name");
                        if (!artistName.isBlank()) artistNames.add(artistName);
                    }
                }

                JsonObject album = firstObject(song, "al", "album");
                String albumName = string(album, "name");
                String cover = CoverUrlUtil.normalize(string(album, "picUrl"));
                if (cover.isBlank()) cover = CoverUrlUtil.normalize(string(song, "picUrl"));
                int duration = durationSeconds(song);
                int fee = integer(song, "fee", 0);
                results.add(new NetEaseSearchResult(id, name, String.join(" / ", artistNames), fee > 0,
                        "netease", "", cover, albumName, duration));
            }
        } catch (RuntimeException error) {
            MengSamaNetMusic.LOGGER.error("Failed to parse NetEase search results", error);
        }
        return results;
    }

    private static int durationSeconds(JsonObject song) {
        long value = longValue(song, song != null && song.has("dt") ? "dt" : "duration", 0L);
        if (value <= 0) return 0;
         
        long seconds = value >= 10_000L ? value / 1000L : value;
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    private static JsonArray firstArray(JsonObject object, String... keys) {
        for (String key : keys) { JsonArray value = array(object, key); if (value != null) return value; }
        return null;
    }

    private static JsonObject firstObject(JsonObject object, String... keys) {
        for (String key : keys) { JsonObject value = object(object, key); if (value != null) return value; }
        return null;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        try { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public String songs(long[] ids) throws IOException {
        String idsStr = StringUtils.deleteWhitespace(Arrays.toString(ids));
        String url = "http://music.163.com/api/song/detail/?ids=" + URLEncoder.encode(idsStr, "UTF-8");
        return NetWorker.get(url, getRequestPropertyData());
    }

     
    public Map<Long, SongInfo> searchDetails(long[] ids) throws IOException {
        return parseSongDetails(songs(ids));
    }

    static Map<Long, SongInfo> parseSongDetails(String json) {
        Map<Long, SongInfo> details = new HashMap<>();
        try {
            JsonArray rows = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("songs");
            if (rows != null) for (JsonElement row : rows) {
                if (!row.isJsonObject()) continue;
                SongInfo song = NetEaseTrackDecoder.decode(row.getAsJsonObject());
                if (song != null) details.put(song.songId, song);
            }
        } catch (RuntimeException error) {
            MengSamaNetMusic.LOGGER.warn("Failed to parse NetEase song details", error);
        }
        return details;
    }

    public String song(long songId) throws IOException { return query("song/detail/", Map.of("id", Long.toString(songId), "ids", "[" + songId + "]")); }

    public String lyric(long songId) throws IOException { return query("song/lyric/", Map.of("id", Long.toString(songId), "lv", "-1", "kv", "-1", "tv", "-1")); }

    public String dj(long djId) throws IOException { return query("dj/program/detail", Map.of("id", Long.toString(djId))); }

    public SongInfo get163Song(long id) throws IOException {
        SongInfo info = null;
        try {
            info = parseSongDetails(song(id)).get(id);
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("NetEase song detail API failed for id {}: {}", id, e.getMessage());
        }

        String metingUrl = MetingApi.getSongUrl(id);
        if (metingUrl != null && !metingUrl.isEmpty()) {
            if (info != null) {
                info.songUrl = metingUrl;
            }
            MengSamaNetMusic.LOGGER.info("Using Meting API URL for song id {}: {}", id, metingUrl);
        }

        if (info != null && info.songUrl != null && !info.songUrl.isEmpty()) {
            info.source = SongInfo.detectSource(info.songUrl);
            if ("unknown".equals(info.source)) info.source = "netease";

            if (info.songId == 0) info.songId = id;
            info.providerId = Long.toString(info.songId);
            return info;
        }

        SongInfo metingInfo = MetingApi.getSongInfo(id);
        if (metingInfo != null && metingInfo.songUrl != null && !metingInfo.songUrl.isEmpty()) {
            metingInfo.source = "netease";
            metingInfo.songId = id;
            metingInfo.providerId = Long.toString(metingInfo.songId);
            return metingInfo;
        }

        if (info == null) {
            info = new SongInfo();
            info.songName = "NetEase #" + id;
            info.songUrl = MetingApi.getSongUrl(id);
        }
        info.songId = id;
        info.providerId = Long.toString(info.songId);
        return info;
    }

     
    public SongInfo get163Song(NetEaseSearchResult searchResult) throws IOException {
        long id = Long.parseLong(searchResult.getSongId());
        SongInfo info = get163Song(id);
        if (info == null) info = new SongInfo();
        info.source = "netease";
        info.songId = id;
        info.providerId = Long.toString(id);
        if (info.songUrl == null || info.songUrl.isBlank()) {
            info.songUrl = String.format("https://music.163.com/song/media/outer/url?id=%d.mp3", id);
        }
        if (info.songName == null || info.songName.isBlank() || info.songName.startsWith("NetEase #")) {
            info.songName = searchResult.getSongName();
        }
        if (info.artists == null) info.artists = new java.util.ArrayList<>();
        if (info.artists.isEmpty() && searchResult.getArtistName() != null) {
            for (String artist : searchResult.getArtistName().split("\\s*/\\s*")) {
                if (!artist.isBlank()) info.artists.add(artist);
            }
        }
        if (info.albumName == null || info.albumName.isBlank()) info.albumName = searchResult.getAlbumName();
        if (info.songTime <= 0) info.songTime = searchResult.getDuration();
        if (info.picUrl == null || info.picUrl.isBlank()) info.picUrl = CoverUrlUtil.normalize(searchResult.getCoverUrl());
        if (info.coverUrl == null || info.coverUrl.isBlank()) info.coverUrl = info.picUrl;
        info.vip = info.vip || searchResult.isVip();
        info.normalizeIdentity();
        return info;
    }

    public SongInfo getDjSong(long djId) throws IOException {
        return NetEaseTrackDecoder.program(dj(djId));
    }
}
