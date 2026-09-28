package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class QqLyricResponseTest {
    private HttpServer server;
    private static final String LRC = "[00:01.25]测试第一行\n[00:02.50]测试第二行";
    @AfterEach void close() { if (server != null) server.stop(0); }

    private String serve(String encoding, int status, byte[] bytes) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lyric", exchange -> {
            if (!encoding.isEmpty()) exchange.getResponseHeaders().set("Content-Encoding", encoding);
            exchange.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return new com.mengsama.mod.mengsamanetmusic.api.qq.QqLyrics(com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp.LIVE).request(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/lyric"),
                Map.of("Accept-Encoding", "gzip, deflate", "Referer", "https://y.qq.com/"));
    }
    private byte[] body() {
        var json = new JsonObject(); json.addProperty("code", 0); json.addProperty("retcode", 0);
        json.addProperty("lyric", LRC); return json.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Test void gzipHttpResponseReachesMillisecondTimeline() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(body()); }
        String lyric = serve("gzip", 200, bytes.toByteArray());
        assertEquals(LRC, lyric);
        assertEquals("测试第一行", LrcParser.parseMillis(lyric).get(1250L));
        assertEquals("测试第二行", LrcParser.parseMillis(lyric).get(2500L));
    }
    @Test void deflateHttpResponseIsDecoded() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new DeflaterOutputStream(bytes)) { zip.write(body()); }
        assertEquals(LRC, serve("deflate", 200, bytes.toByteArray()));
    }
    @Test void uncompressedResponseRemainsSupported() throws Exception { assertEquals(LRC, serve("", 200, body())); }
    @Test void httpErrorIsNotCachedAsNoLyrics() { assertThrows(IOException.class, () -> serve("", 503, body())); }
    @Test void truncatedGzipFailsInsteadOfReturningGarbage() { assertThrows(IOException.class, () -> serve("gzip", 200, new byte[]{31,-117,8})); }
    @Test void providerErrorsAndMalformedPayloadsStayFailures() {
        for (String payload : new String[]{"{\"code\":-1}", "{\"code\":0,\"retcode\":-1}", "<html>error</html>", "{}", "[]", "{\"code\":0,\"lyric\":{}}"})
            assertThrows(IOException.class, () -> com.mengsama.mod.mengsamanetmusic.api.qq.QqLyrics.decode(payload));
    }
    @Test void successfulEmptyLyricIsStillEmpty() throws Exception {
        assertEquals("", com.mengsama.mod.mengsamanetmusic.api.qq.QqLyrics.decode("{\"code\":0,\"lyric\":\"\"}"));
        assertEquals("", com.mengsama.mod.mengsamanetmusic.api.qq.QqLyrics.decode("{\"retcode\":0,\"lyric\":null}"));
    }
    @Test void compressedResponseHasDecompressedSizeLimit() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(new byte[2 * 1024 * 1024 + 1]); }
        assertThrows(IOException.class, () -> serve("gzip", 200, bytes.toByteArray()));
    }
}
