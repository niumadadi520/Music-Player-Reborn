package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;

public final class QqPlayback {

    private record Format(String prefix, String extension) {

        String file(String mid) {
            return prefix + mid + "." + extension;
        }
    }

    private static final List<Format> FORMATS = List.of(new Format("F000", "flac"), new Format("M800", "mp3"), new Format("M500", "mp3"), new Format("RS02", "mp3"), new Format("C600", "m4a"), new Format("C400", "m4a"), new Format("C200", "m4a"), new Format("C100", "m4a"));

    private final QqCatalog catalog;

    public QqPlayback(QqCatalog catalog) {
        this.catalog = catalog;
    }

    public SongInfo resolve(String mid, String cookie, int quality) throws IOException {
        QqTrack track = catalog.detail(mid, cookie);
        String account = QqProtocol.account(cookie);
        for (int index = Math.max(0, Math.min(FORMATS.size() - 1, quality)); index < FORMATS.size(); index++) {
            JsonObject parameters = properties("filename", array(FORMATS.get(index).file(track.mediaMid())), "guid", "10000", "songmid", array(mid), "songtype", array(0), "uin", account, "loginflag", 1, "platform", "20");
            JsonObject data = catalog.rpc(QqProtocol.DETAIL, "0", "req_1", "vkey.GetVkeyServer", "CgiGetVkey", parameters, cookie);
            String url = mediaUrl(data, mid);
            if (url.isEmpty())
                continue;
            SongInfo song = track.song();
            song.songUrl = url;
            song.resolvedMediaUrl = url;
            song.playbackHeaders.putAll(QqProtocol.headers(cookie, false));
            return song;
        }
        if (track.vip() && (cookie == null || cookie.isBlank()))
            throw new IOException("QQ VIP song requires a login cookie");
        throw new IOException("QQ Music returned no playable URL");
    }

    public static String mediaUrl(JsonObject data, String expectedMid) throws IOException {
        String path = "";
        for (JsonElement candidate : rows(data, "midurlinfo")) if (candidate.isJsonObject()) {
            JsonObject item = candidate.getAsJsonObject();
            String mid = text(item, "songmid");
            if (!mid.isBlank() && !mid.equals(expectedMid))
                continue;
            path = text(item, "purl");
            if (!path.isBlank())
                break;
        }
        if (path.isBlank())
            return "";
        String origin = "http://ws.stream.qqmusic.qq.com/";
        for (JsonElement server : rows(data, "sip")) if (server.isJsonPrimitive() && !server.getAsString().isBlank()) {
            origin = server.getAsString();
            break;
        }
        try {
            URI base = URI.create(origin.endsWith("/") ? origin : origin + "/");
            URI resolved = base.resolve(path);
            if (!QqHttp.http(resolved))
                throw new IllegalArgumentException();
            return resolved.toString();
        } catch (IllegalArgumentException invalid) {
            throw new IOException("QQ returned an invalid media location");
        }
    }
}
