package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;
import static org.junit.jupiter.api.Assertions.*;

class QqCatalogPlaybackTest {
    private static final String MID = "003ExampleAbcd";
    private final List<JsonObject> bodies = new ArrayList<>();
    private JsonObject track(String mid) {
        return properties("mid", mid, "name", "认真的雪", "id", 123, "interval", 260,
                "file", properties("media_mid", "mediaABC"), "album", properties("mid", "albumABC", "name", "专辑"));
    }
    private QqHttp.Response response(QqHttp.Request request, String key, JsonObject data) {
        return new QqHttp.Response(request.uri(), 200, Map.of(), properties("code", 0, key,
                properties("code", 0, "data", data)).toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test void qualityFallbackFetchesMetadataOnlyOnceAndUsesMediaMidAndAccount() throws Exception {
        var catalog = new QqCatalog(request -> {
            JsonObject body = parse(new String(request.body(), StandardCharsets.UTF_8));
            bodies.add(body);
            if (bodies.size() == 1) return response(request, "req_1", properties("track_info", track(MID)));
            JsonArray candidates = new JsonArray();
            candidates.add(properties("songmid", MID, "purl", bodies.size() == 2 ? "" : "https://cdn.qqmusic.qq.com/play.mp3?vkey=test"));
            return response(request, "req_1", properties("midurlinfo", candidates, "sip", array("https://cdn.qqmusic.qq.com/")));
        }, () -> "uin=o001234; qm_keyst=test");
        var song = new QqPlayback(catalog).resolve(MID, "uin=o001234; qm_keyst=test", 0);
        assertEquals(3, bodies.size());
        assertEquals("1234", bodies.get(1).get("loginUin").getAsString());
        assertEquals("F000mediaABC.flac", object(bodies.get(1), "req_1", "param").getAsJsonArray("filename").get(0).getAsString());
        assertEquals("M800mediaABC.mp3", object(bodies.get(2), "req_1", "param").getAsJsonArray("filename").get(0).getAsString());
        assertEquals("https://cdn.qqmusic.qq.com/play.mp3?vkey=test", song.resolvedMediaUrl);
        assertEquals("认真的雪", song.songName);
        assertEquals(MID, song.providerId);
        assertEquals("qq", song.source);
        assertEquals(260, song.songTime);
    }

    @Test void mediaResolutionSkipsOtherSongsAndRejectsNonHttpUrls() throws Exception {
        JsonArray candidates = new JsonArray();
        candidates.add(properties("songmid", "other", "purl", "wrong.mp3"));
        candidates.add(properties("songmid", MID, "purl", "right.mp3"));
        JsonObject data = properties("midurlinfo", candidates, "sip", array("https://cdn.qqmusic.qq.com/folder/"));
        assertEquals("https://cdn.qqmusic.qq.com/folder/right.mp3", QqPlayback.mediaUrl(data, MID));
        candidates.get(1).getAsJsonObject().addProperty("purl", "file:///tmp/track");
        assertThrows(IOException.class, () -> QqPlayback.mediaUrl(data, MID));
    }

    @Test void albumPaginationCollectsAllTracksInOrder() throws Exception {
        var catalog = new QqCatalog(request -> {
            var body = parse(new String(request.body(), StandardCharsets.UTF_8));
            bodies.add(body);
            int begin = object(body, "music", "param").get("begin").getAsInt();
            JsonArray rows = new JsonArray();
            for (int id = begin; id < Math.min(begin + 500, 502); id++) rows.add(properties("songInfo", track("track" + id)));
            return response(request, "music", properties("songList", rows, "totalNum", 502));
        }, () -> "");
        var songs = catalog.album("albumABC");
        assertEquals(502, songs.size());
        assertEquals("track0", songs.get(0).providerId);
        assertEquals("track501", songs.get(501).providerId);
        assertEquals(2, bodies.size());
    }

    @Test void incompleteAlbumFailsInsteadOfReturningPartialImport() {
        var catalog = new QqCatalog(request -> {
            JsonArray rows = new JsonArray(); rows.add(properties("songInfo", track(MID)));
            return response(request, "music", properties("songList", rows, "totalNum", 2));
        }, () -> "");
        assertThrows(IOException.class, () -> catalog.album("albumABC"));
    }

    @Test void paginationWithNoNewTracksStopsWithError() {
        var catalog = new QqCatalog(request -> {
            bodies.add(parse(new String(request.body(), StandardCharsets.UTF_8)));
            JsonArray rows = new JsonArray();
            for (int i = 0; i < 500; i++) rows.add(properties("songInfo", track("track" + i)));
            return response(request, "music", properties("songList", rows, "totalNum", 1000));
        }, () -> "");
        assertThrows(IOException.class, () -> catalog.album("albumABC"));
        assertEquals(2, bodies.size());
    }

    @Test void malformedPlaylistAndServiceErrorsAreNotEmptySuccesses() {
        var catalog = new QqCatalog(request -> response(request, "music", properties("songlist", array("invalid row"))), () -> "");
        assertThrows(IOException.class, () -> catalog.playlist("123"));
        assertThrows(IOException.class, () -> data(properties("code", 403), "music"));
        assertThrows(IOException.class, () -> data(properties("code", 0, "music", properties("code", 1, "data", properties("x", 1))), "music"));
    }

    @Test void searchDecodesMetadataThroughProductionRequestPath() throws Exception {
        var catalog = new QqCatalog(request -> {
            bodies.add(parse(new String(request.body(), StandardCharsets.UTF_8)));
            JsonArray rows = new JsonArray(); rows.add(track(MID));
            return response(request, "req", properties("body", properties("song", properties("list", rows))));
        }, () -> "");
        assertEquals("认真的雪", catalog.search("  认真的雪  ").get(0).getTitle());
        assertEquals("认真的雪", object(bodies.get(0), "req", "param").get("query").getAsString());
        assertTrue(catalog.search(" ").isEmpty());
        assertEquals(1, bodies.size());
    }
}
