package com.mengsama.mod.mengsamanetmusic.cache;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CacheDataProtectionTest {
    @TempDir Path directory;
    static byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, 2), "png", output);
        return output.toByteArray();
    }
    private static void drain(MusicAssetCache cache) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (cache.activeDownloads() > 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(0, cache.activeDownloads());
    }
    @Test void downloadsAreBoundedAndDeduplicatedAndNeverRewriteLegacyAudioOrIndex() throws Exception {
        var cache = new MusicAssetCache(directory);
        byte[] image = png(), audio = {73, 68, 51, 1, 2, 3};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/cover", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, image.length); exchange.getResponseBody().write(image); exchange.close();
        });
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(4);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), resolutions = new AtomicInteger();
        try {
            String original = "{\"1\":\"original\",\"future\":{\"unknown\":[1,2,3]}}";
            Files.writeString(directory.resolve("index.json"), original);
            Files.writeString(directory.resolve("covers.json"), "{\"future\":{\"unknown\":[1,2,3]}}");
            Files.write(directory.resolve("original.mp3"), audio);
            cache.loadAsync().get(5, TimeUnit.SECONDS);
            server.start();
            Supplier<String> resolver = () -> {
                resolutions.incrementAndGet(); int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max); entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException error) { throw new RuntimeException(error); }
                finally { active.decrementAndGet(); }
                return "http://127.0.0.1:" + server.getAddress().getPort() + "/cover";
            };
            var futures = new ArrayList<CompletableFuture<Path>>();
            for (int i = 0; i < 6; i++) futures.add(cache.requestCover(100L+i, resolver));
            for (int i = 0; i < 20; i++) assertSame(futures.get(0), cache.requestCover(100, resolver));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(4, resolutions.get()); assertEquals(6, cache.activeDownloads());
            release.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            drain(cache);
            assertEquals(6, resolutions.get()); assertEquals(6, requests.get()); assertTrue(peak.get() <= 4);
            var index = new CacheIndexStore(directory, "covers.json").read();
            for (int i = 0; i < 6; i++) {
                assertEquals("cover-"+(100+i), index.get(""+(100+i)));
                assertArrayEquals(image, Files.readAllBytes(futures.get(i).join()));
            }
            assertArrayEquals(audio, Files.readAllBytes(directory.resolve("original.mp3")));
            assertEquals(original, Files.readString(directory.resolve("index.json")));
            assertFalse(Files.exists(directory.resolve("index.json.bak")));
            assertTrue(Files.readString(directory.resolve("covers.json")).contains("\"unknown\":[1,2,3]"));
            try (var files = Files.list(directory)) { assertEquals(1, files.filter(p -> p.toString().endsWith(".mp3")).count()); }
        } finally { release.countDown(); server.stop(0); cache.shutdown(); }
    }

    @Test void existingLegacyCoverIsReusedBeforeResolvingAndSurvivesRestart() throws Exception {
        String original = "{\"42\":\"old-id\",\"unknown\":[9]}";
        Files.writeString(directory.resolve("index.json"), original);
        Files.write(directory.resolve("old-id.png"), png());
        for (int restart = 0; restart < 2; restart++) {
            var cache = new MusicAssetCache(directory);
            try {
                Path image = cache.requestCover(42, () -> { throw new AssertionError("cached image must not request metadata"); }).get(5, TimeUnit.SECONDS);
                assertEquals(directory.resolve("old-id.png"), image);
                drain(cache);
                assertEquals(original, Files.readString(directory.resolve("index.json")));
            } finally { cache.shutdown(); }
        }
    }

    @Test void missingLegacyCoverAndMalformedIndexDoNotPreventNewCoverDownloads() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] image = png();
        server.createContext("/cover", exchange -> {
            exchange.sendResponseHeaders(200, image.length); exchange.getResponseBody().write(image); exchange.close();
        });
        server.start();
        try {
            for (String old : List.of("{\"7\":\"audio-only\"}", "{truncated old index")) {
                Path folder = Files.createTempDirectory(directory, "legacy-");
                Files.writeString(folder.resolve("index.json"), old);
                Files.write(folder.resolve("audio-only.mp3"), new byte[]{1,2,3});
                var cache = new MusicAssetCache(folder);
                try {
                    Path downloaded = cache.requestCover(7, () -> "http://127.0.0.1:"+server.getAddress().getPort()+"/cover").get(5, TimeUnit.SECONDS);
                    assertArrayEquals(image, Files.readAllBytes(downloaded));
                    assertEquals(old, Files.readString(folder.resolve("index.json")));
                    assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(folder.resolve("audio-only.mp3")));
                    drain(cache);
                } finally { cache.shutdown(); }
            }
        } finally { server.stop(0); }
    }

    @Test void corruptLegacyCoverAndOccupiedDestinationArePreservedWhileNewCoverIsPublished() throws Exception {
        String old = "{\"9\":\"broken\"}";
        Files.writeString(directory.resolve("index.json"), old);
        Files.writeString(directory.resolve("broken.png"), "old broken cover");
        Files.writeString(directory.resolve("cover-9.png"), "occupied destination");
        byte[] image = png();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover", exchange -> {
            exchange.sendResponseHeaders(200, image.length); exchange.getResponseBody().write(image); exchange.close();
        });
        var cache = new MusicAssetCache(directory);
        try {
            server.start();
            Path cover = cache.requestCover(9, () -> "http://127.0.0.1:"+server.getAddress().getPort()+"/cover").get(5, TimeUnit.SECONDS);
            assertEquals("cover-9-1.png", cover.getFileName().toString());
            assertArrayEquals(image, Files.readAllBytes(cover));
            assertEquals(old, Files.readString(directory.resolve("index.json")));
            assertEquals("old broken cover", Files.readString(directory.resolve("broken.png")));
            assertEquals("occupied destination", Files.readString(directory.resolve("cover-9.png")));
            drain(cache);
        } finally { server.stop(0); cache.shutdown(); }
    }

    @Test void malformedIndexIsPreservedBeforeAReplacementCanBeWritten() throws Exception {
        String broken = "{truncated index";
        Files.writeString(directory.resolve("index.json"), broken);
        var index = new CacheIndexStore(directory);
        assertTrue(index.read().isEmpty());
        assertEquals(broken, Files.readString(directory.resolve("index.json")));
        index.write(Map.of("7", "track-seven"));
        try (var files = Files.list(directory)) {
            var backups = files.filter(p -> p.getFileName().toString().startsWith("index-unreadable-")).toList();
            assertEquals(1, backups.size()); assertEquals(broken, Files.readString(backups.get(0)));
        }
        assertEquals("track-seven", new CacheIndexStore(directory).read().get("7"));
    }

    @Test void unsafeIndexEntriesAreRetainedAsDataButNeverReturnedAsFiles() throws Exception {
        Files.writeString(directory.resolve("index.json"), "{\"1\":\"../outside\",\"2\":\"valid\",\"version\":3}");
        var index = new CacheIndexStore(directory);
        assertEquals(Map.of("2", "valid"), index.read());
        index.write(Map.of("9", "another"));
        String data = Files.readString(directory.resolve("index.json"));
        assertTrue(data.contains("../outside")); assertTrue(data.contains("\"version\":3"));
        assertThrows(java.io.IOException.class, () -> index.write(Map.of("3", "../escape")));
    }


    @Test void failedTransferDoesNotPublishAnIndexEntryAndCanRetry() throws Exception {
        var cache = new MusicAssetCache(directory);
        AtomicInteger attempts = new AtomicInteger();
        try {
            for (int i = 0; i < 2; i++) {
                assertThrows(ExecutionException.class, () -> cache.requestCover(10, () -> {
                    attempts.incrementAndGet(); return "file:///not-allowed";
                }).get(5, TimeUnit.SECONDS));
                drain(cache);
            }
            assertEquals(2, attempts.get());
            assertFalse(Files.exists(directory.resolve("cover-10.png")));
            assertFalse(Files.exists(directory.resolve("covers.json")));
        } finally { cache.shutdown(); }
    }
}
