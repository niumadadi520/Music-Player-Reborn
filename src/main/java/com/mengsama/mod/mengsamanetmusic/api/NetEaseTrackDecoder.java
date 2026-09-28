package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;

 
final class NetEaseTrackDecoder {
    static SongInfo program(String response) {
        JsonElement cursor;
        try { cursor = JsonParser.parseString(response); }
        catch (JsonParseException | IllegalStateException invalid) { return new SongInfo(); }
        for (String key : new String[]{"program", "mainSong"}) {
            if (cursor == null || !cursor.isJsonObject()) return new SongInfo();
            cursor = cursor.getAsJsonObject().get(key);
        }
        if (cursor == null || !cursor.isJsonObject()) return new SongInfo();
        SongInfo song = decode(cursor.getAsJsonObject());
        return song != null ? song : new SongInfo();
    }

    static SongInfo decode(JsonObject track) {
        long id;
        try { id = Long.parseLong(text(track, "id")); }
        catch (NumberFormatException ignored) { return null; }
        if (id <= 0) return null;
        SongInfo song = new SongInfo();
        song.songId = id;
        song.providerId = Long.toString(id);
        song.source = "netease";
        song.songUrl = "https://music.163.com/song/media/outer/url?id=" + id + ".mp3";
        song.songName = text(track, "name");
        try { song.songTime = (int) Math.min(Integer.MAX_VALUE, Math.max(0, Long.parseLong(text(track, "dt", "duration"))) / 1000); }
        catch (NumberFormatException ignored) { }
        song.vip = "1".equals(text(track, "fee"));
        JsonElement artists = first(track, "ar", "artists");
        if (artists != null && artists.isJsonArray()) for (JsonElement entry : artists.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            String name = text(entry.getAsJsonObject(), "name");
            if (!name.isBlank()) song.artists.add(name);
        }
        JsonElement album = first(track, "al", "album");
        if (album != null && album.isJsonObject()) {
            song.albumName = text(album.getAsJsonObject(), "name");
            song.picUrl = text(album.getAsJsonObject(), "picUrl");
            song.coverUrl = song.picUrl;
        }
        JsonElement translated = first(track, "tns", "transNames");
        if (translated != null && translated.isJsonArray() && !translated.getAsJsonArray().isEmpty()) {
            JsonElement value = translated.getAsJsonArray().get(0);
            if (value.isJsonPrimitive()) song.transName = value.getAsString();
        }
        song.normalizeIdentity();
        return song;
    }

    private static JsonElement first(JsonObject object, String... keys) {
        for (String key : keys) if (object.has(key) && !object.get(key).isJsonNull()) return object.get(key);
        return null;
    }
    private static String text(JsonObject object, String... keys) {
        JsonElement value = first(object, keys);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
