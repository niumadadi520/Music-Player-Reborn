package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.SequenceInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetMusicAudioStreamTest {
    @Test void cancellationDiscardsAlreadyBufferedPcmAtZeroSeekOffset() throws Exception {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        var format = new AudioFormat(44100, 16, 2, true, false);
        var input = new javax.sound.sampled.AudioInputStream(new ByteArrayInputStream(new byte[44100 * 4]), format, 44100);
        try (var stream = new NetMusicAudioStream(input, 0, cancelled::get)) {
            cancelled.set(true);
            org.junit.jupiter.api.Assertions.assertNull(stream.read(4096));
        }
    }

    @Test
    void detectsMpegAacMp4AndRejectsApiBodies() {
        assertEquals(NetMusicAudioStream.AudioContainer.MPEG,
                NetMusicAudioStream.AudioContainer.detect("application/octet-stream", new byte[]{'I','D','3',4,0,0}));
        assertEquals(NetMusicAudioStream.AudioContainer.AAC_ADTS,
                NetMusicAudioStream.AudioContainer.detect("audio/aac", new byte[]{(byte) 0xff, (byte) 0xf1, 0x50, (byte) 0x80}));
        assertEquals(NetMusicAudioStream.AudioContainer.MP4_AAC,
                NetMusicAudioStream.AudioContainer.detect("audio/mp4", new byte[]{0,0,0,24,'f','t','y','p','M','4','A',' '}));
        assertEquals(NetMusicAudioStream.AudioContainer.JSON,
                NetMusicAudioStream.AudioContainer.detect("application/octet-stream", " {\"code\":403}".getBytes()));
        assertEquals(NetMusicAudioStream.AudioContainer.HTML,
                NetMusicAudioStream.AudioContainer.detect("text/plain", "<!doctype html>".getBytes()));
        assertEquals(NetMusicAudioStream.AudioContainer.JSON,
                NetMusicAudioStream.AudioContainer.detect("audio/mpeg",
                        new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '\r', '\n', ' ', '{', '"', 'c', 'o', 'd', 'e', '"', ':', '2', '0', '0', '}'}));
        assertEquals(NetMusicAudioStream.AudioContainer.HTML,
                NetMusicAudioStream.AudioContainer.detect("audio/aac",
                        new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '\t', '<', '?', 'x', 'm', 'l'}));
    }

    @Test
    void magicProbeReplayPreservesEveryByte() throws Exception {
        byte[] payload = new byte[4096];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);
        ByteArrayInputStream body = new ByteArrayInputStream(payload);
        byte[] prefix = body.readNBytes(64);
        try (SequenceInputStream replay = new SequenceInputStream(new ByteArrayInputStream(prefix), body)) {
            assertArrayEquals(payload, replay.readAllBytes());
        }
    }

    @Test
    void fakeExpiredUrlRefreshesToWorkingAudioWithProviderHeaders() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> headers = new AtomicReference<>();
        byte[] wav = tinyWav();
        server.createContext("/expired", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.createContext("/fresh", exchange -> {
            headers.set(exchange.getRequestHeaders().getFirst("Referer") + "|"
                    + exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Exception expired = assertThrows(Exception.class, () -> new NetMusicAudioStream(
                    new URL("http://127.0.0.1:" + port + "/expired"), Map.of()));
            assertEquals(NetMusicAudioStream.FailureCategory.EXPIRED, NetMusicAudioStream.classifyFailure(expired));
            URL fresh = new URL("http://127.0.0.1:" + port + "/fresh");
            var connection = (java.net.HttpURLConnection) fresh.openConnection();
            NetMusicAudioStream.requestHeaders(fresh,
                    Map.of("Referer", "https://y.qq.com/", "Cookie", "qq-effective"))
                    .forEach(connection::setRequestProperty);
            assertEquals(200, connection.getResponseCode());
            assertEquals(wav.length, connection.getInputStream().readAllBytes().length);
            connection.disconnect();
            assertEquals("https://y.qq.com/|null", headers.get(), "arbitrary local audio hosts must not receive provider credentials");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void http200HtmlAndJsonAreExpiredOnlyForRefreshableProviderSongs() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/html", exchange -> send(exchange, "text/html", "<!doctype html><title>expired</title>".getBytes()));
        server.createContext("/json", exchange -> send(exchange, "application/json", "{\"code\":403}".getBytes()));
        server.createContext("/disguised", exchange -> send(exchange, "audio/mpeg",
                new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '\r', '\n', '{', '"', 'c', 'o', 'd', 'e', '"', ':', '2', '0', '0', '}'}));
        server.start();
        try {
            SongInfo refreshable = refreshableSong();
            SongInfo direct = new SongInfo("https://example.invalid/file.mp3", "direct", 1);
            int port = server.getAddress().getPort();
            for (String path : new String[]{"html", "json", "disguised"}) {
                Exception failure = assertThrows(Exception.class, () -> new NetMusicAudioStream(
                        new URL("http://127.0.0.1:" + port + "/" + path), Map.of()));
                assertEquals(NetMusicAudioStream.FailureCategory.EXPIRED,
                        NetMusicAudioStream.classifyFailure(failure));
                assertTrue(NetMusicAudioStream.shouldRefreshProvider(refreshable, failure));
                assertFalse(NetMusicAudioStream.shouldRefreshProvider(direct, failure));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void corruptAudioRemainsDecodeFailureAndNeverTriggersRefresh() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] corruptWav = new byte[]{'R','I','F','F', 24,0,0,0, 'W','A','V','E', 'b','r','o','k','e','n'};
        server.createContext("/corrupt", exchange -> send(exchange, "audio/wav", corruptWav));
        server.start();
        try {
            Exception failure = assertThrows(Exception.class, () -> new NetMusicAudioStream(
                    new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/corrupt"), Map.of()));
            assertEquals(NetMusicAudioStream.FailureCategory.DECODE_FAILED,
                    NetMusicAudioStream.classifyFailure(failure));
            assertFalse(NetMusicAudioStream.shouldRefreshProvider(refreshableSong(), failure));
        } finally {
            server.stop(0);
        }
    }

    private static SongInfo refreshableSong() {
        SongInfo song = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "song", 1);
        song.source = "netease";
        song.providerId = "123";
        song.songId = 123L;
        song.rawUrl = song.songUrl;
        return song;
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, String contentType, byte[] payload)
            throws java.io.IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static byte[] tinyWav() throws Exception {
        var format = new javax.sound.sampled.AudioFormat(44100, 16, 2, true, false);
        byte[] pcm = new byte[44100 * 4];
        try (var input = new javax.sound.sampled.AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 4);
             var output = new ByteArrayOutputStream()) {
            javax.sound.sampled.AudioSystem.write(input, javax.sound.sampled.AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        }
    }

    @Test
    void pcmSkipUsesDecodedFrameRateAndDoesNotDependOnSkipImplementation() throws Exception {
        AudioFormat format = new AudioFormat(44_100, 16, 2, true, false);
        byte[] pcm = new byte[44_100 * 4 * 2];
        InputStream misleadingSkip = new FilterInputStream(new ByteArrayInputStream(pcm)) {
            @Override public long skip(long amount) throws IOException {
                return Math.min(amount * 100, super.skip(amount * 100));
            }
        };
        try (var audio = new javax.sound.sampled.AudioInputStream(misleadingSkip, format, pcm.length / 4)) {
            assertEquals(44_100L * 4, NetMusicAudioStream.skipPcmSeconds(audio, 1, new byte[4]));
            assertEquals(44_100 * 4, audio.readAllBytes().length);
        }
    }

    @Test
    void largePcmSkipMakesProgressWhenDecoderSkipReturnsZero() throws Exception {
        AudioFormat format = new AudioFormat(1000, 16, 2, true, false);
        byte[] pcm = new byte[12_000];
        InputStream noSkip = new FilterInputStream(new ByteArrayInputStream(pcm)) {
            @Override public long skip(long amount) { return 0L; }
        };
        try (var audio = new javax.sound.sampled.AudioInputStream(noSkip, format, pcm.length / 4)) {
            assertEquals(8_000L, NetMusicAudioStream.skipPcmSeconds(audio, 2, new byte[4]));
            assertEquals(4_000, audio.readAllBytes().length);
        }
    }

    @Test
    void supersededPcmSkipAbortsBeforeReadingTheWholeDistance() throws Exception {
        AudioFormat format = new AudioFormat(1000, 16, 2, true, false);
        byte[] pcm = new byte[40_000];
        var audio = new javax.sound.sampled.AudioInputStream(
                new ByteArrayInputStream(pcm), format, pcm.length / 4) {
            @Override public long skip(long amount) { return 0L; }
        };
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        IOException failure = assertThrows(IOException.class, () -> NetMusicAudioStream.skipPcmSeconds(
                audio, 8, new byte[4], () -> checks.incrementAndGet() > 1));
        assertTrue(failure.getMessage().contains("superseded"));
    }

    @Test
    void gzipResponseCanBeDecodedBeforeProbeAndReplay() throws Exception {
        byte[] payload = new byte[]{'I', 'D', '3', 4, 0, 0, 1, 2, 3, 4};
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(payload);
        }
        try (GZIPInputStream decoded = new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] prefix = decoded.readNBytes(6);
            assertEquals(NetMusicAudioStream.AudioContainer.MPEG,
                    NetMusicAudioStream.AudioContainer.detect("application/octet-stream", prefix));
            try (SequenceInputStream replay = new SequenceInputStream(new ByteArrayInputStream(prefix), decoded)) {
                assertArrayEquals(payload, replay.readAllBytes());
            }
        }
    }
}
