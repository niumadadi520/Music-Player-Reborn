package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;

 


public final class QqCatalog {

    private final QqHttp.Transport transport;

    private final Supplier<String> cookie;

    public QqCatalog(QqHttp.Transport transport, Supplier<String> cookie) {
        this.transport = transport;
        this.cookie = cookie;
    }

    public JsonObject rpc(URI endpoint, String version, String key, String module, String method, JsonObject parameters, String credential) throws IOException {
        JsonObject payload = QqProtocol.envelope(version, key, QqProtocol.call(module, method, parameters));
        if (module.equals("vkey.GetVkeyServer"))
            payload.addProperty("loginUin", QqProtocol.account(credential));
        QqHttp.Response response = transport.exchange(QqHttp.Request.post(endpoint, payload.toString(), QqProtocol.headers(credential, true), true));
        return data(parse(response.successful().text()), key);
    }

    public List<QqSearchResult> search(String query) throws IOException {
        if (query == null || query.isBlank())
            return List.of();
        JsonObject data = rpc(QqProtocol.CATALOG, "1859", "req", "music.search.SearchCgiService", "DoSearchForQQMusicDesktop", properties("grp", 1, "num_per_page", 50, "page_num", 1, "query", query.strip(), "search_type", 0), cookie.get());
        return searchRows(data);
    }

    public static List<QqSearchResult> searchResponse(String response, String key) {
        try {
            return searchRows(data(parse(response), key));
        } catch (IOException invalid) {
            return List.of();
        }
    }

    private static List<QqSearchResult> searchRows(JsonObject data) {
        List<QqSearchResult> result = new ArrayList<>();
        for (JsonElement entry : rows(object(data, "body", "song"), "list")) {
            QqTrack song = entry.isJsonObject() ? QqTrack.read(entry.getAsJsonObject()) : null;
            if (song != null && !song.title().isBlank())
                result.add(song.searchRow());
        }
        return List.copyOf(result);
    }

    public QqTrack detail(String mid, String credential) throws IOException {
        if (mid == null || !mid.matches("[A-Za-z0-9]{1,32}"))
            throw new IOException("QQ song MID is invalid");
        JsonObject data = rpc(QqProtocol.DETAIL, "0", "req_1", "music.pf_song_detail_svr", "get_song_detail", properties("song_mid", mid, "song_id", 0), credential);
        QqTrack song = QqTrack.read(object(data, "track_info"));
        if (song == null || !mid.equals(song.mid()))
            throw new IOException("QQ returned mismatching track metadata");
        return song;
    }

    public SongInfo detail(String mid) throws IOException {
        return detail(mid, cookie.get()).song();
    }

    public JsonObject collection(String module, String method, JsonObject parameters) throws IOException {
        return rpc(QqProtocol.CATALOG, "2121", "music", module, method, parameters, cookie.get());
    }

    public List<SongInfo> album(String mid) throws IOException {
        List<SongInfo> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int begin = 0; begin < 10000; begin += 500) {
            JsonObject page = collection("music.musichallAlbum.AlbumSongList", "GetAlbumSongList", properties("begin", begin, "num", 500, "order", 1, "albumMid", mid));
            JsonArray entries = rows(page, "songList");
            List<SongInfo> decoded = decodeCollection(entries, true);
            int added = 0;
            for (SongInfo song : decoded) if (identities.add(song.providerId)) {
                result.add(song);
                added++;
            }
            long total = number(page, "totalNum", number(page, "total", -1));
            if (total >= 0 && result.size() >= total)
                return List.copyOf(result);
            if (entries.size() < 500) {
                if (total >= 0 && result.size() < total)
                    throw new IOException("QQ album response is incomplete");
                return List.copyOf(result);
            }
            if (added == 0)
                throw new IOException("QQ album pagination made no progress");
        }
        throw new IOException("QQ album exceeds supported import limit");
    }

    public List<SongInfo> playlist(String id) throws IOException {
        JsonObject parameters = properties("disstid", id, "userinfo", 1, "tag", 1, "is_pc", 1);
        try {
            parameters.addProperty("disstid", Long.parseLong(id));
        } catch (NumberFormatException ignored) {
        }
        JsonObject data = collection("music.srfDissInfo.aiDissInfo", "uniform_get_Dissinfo", parameters);
        return decodeCollection(rows(data, "songlist"), false);
    }

    private static List<SongInfo> decodeCollection(JsonArray rows, boolean wrapped) throws IOException {
        List<SongInfo> songs = new ArrayList<>();
        for (JsonElement value : rows) {
            if (!value.isJsonObject())
                throw new IOException("QQ import contains an invalid track");
            JsonObject data = value.getAsJsonObject();
            if (wrapped && data.has("songInfo"))
                data = object(data, "songInfo");
            QqTrack song = QqTrack.read(data);
            if (song == null)
                throw new IOException("QQ import is missing a track identity");
            songs.add(song.song());
        }
        return List.copyOf(songs);
    }
}
