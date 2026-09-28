package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

public final class QqLyrics {

    private final QqHttp.Transport transport;

    public QqLyrics(QqHttp.Transport transport) {
        this.transport = transport;
    }

    public String fetch(String mid, String cookie) throws IOException {
        if (mid == null || mid.isBlank())
            return "";
        URI url = QqProtocol.query("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg", Map.of("songmid", mid, "format", "json", "nobase64", "1", "g_tk", "5381"));
        return request(url, QqProtocol.headers(cookie, true));
    }

    public String request(URI url, Map<String, String> headers) throws IOException {
        return decode(transport.exchange(QqHttp.Request.get(url, headers, 2 << 20, true)).successful().text());
    }

    public static String decode(String payload) throws IOException {
        JsonObject root = QqFields.parse(payload);
        long code = QqFields.number(root, "code", QqFields.number(root, "retcode", -1));
        if (code != 0 || QqFields.number(root, "retcode", code) != 0)
            throw new IOException("QQ lyric provider rejected request");
        JsonElement lyric = root.get("lyric");
        if (lyric == null || lyric.isJsonNull())
            return "";
        if (!lyric.isJsonPrimitive() || !lyric.getAsJsonPrimitive().isString())
            throw new IOException("Invalid QQ lyric field");
        return lyric.getAsString();
    }
}
