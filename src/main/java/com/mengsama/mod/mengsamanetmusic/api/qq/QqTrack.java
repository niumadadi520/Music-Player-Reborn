package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.*;
import java.util.*;

 


public record QqTrack(String mid, String mediaMid, long numericId, String title, int seconds, List<String> singers, String albumMid, String albumPmId, String albumTitle, boolean vip) {

    public static QqTrack read(JsonObject input) {
        if (input == null)
            return null;
        String mid = QqFields.text(input, "mid", "songmid");
        if (mid.isBlank())
            return null;
        JsonObject album = QqFields.object(input, "album");
        List<String> singers = new ArrayList<>();
        for (JsonElement entry : QqFields.rows(input, "singer")) if (entry.isJsonObject()) {
            String singer = QqFields.text(entry.getAsJsonObject(), "name");
            if (!singer.isBlank())
                singers.add(singer);
        }
        String media = QqFields.text(QqFields.object(input, "file"), "media_mid");
        String albumMid = QqFields.text(album, "mid");
        if (albumMid.isBlank())
            albumMid = QqFields.text(input, "albummid");
        String albumName = QqFields.text(album, "name");
        if (albumName.isBlank())
            albumName = QqFields.text(input, "albumname");
        return new QqTrack(mid, media.isBlank() ? mid : media, QqFields.number(input, "id", QqFields.number(input, "songid", 0)), QqFields.text(input, "name", "title", "songname"), (int) Math.max(0, Math.min(Integer.MAX_VALUE, QqFields.number(input, "interval", 0))), List.copyOf(singers), albumMid, QqFields.text(album, "pmId"), albumName, QqFields.number(QqFields.object(input, "pay"), "pay_play", 0) == 1);
    }

    public SongInfo song() {
        SongInfo value = new SongInfo(mid, title, seconds);
        value.providerId = mid;
        value.source = "qq";
        value.songId = numericId;
        value.artists = new ArrayList<>(singers);
        value.albumMid = albumMid;
        value.albumName = albumTitle;
        value.vip = vip;
        value.coverUrl = QqProtocol.artwork(albumMid).stream().findFirst().orElse("");
        value.picUrl = value.coverUrl;
        return value;
    }

    public QqSearchResult searchRow() {
        return new QqSearchResult(mid, title, String.join("/", singers), vip, albumMid, albumPmId, song().coverUrl, albumTitle, seconds);
    }
}
