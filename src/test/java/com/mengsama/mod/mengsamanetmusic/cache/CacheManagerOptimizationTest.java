package com.mengsama.mod.mengsamanetmusic.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CacheManagerOptimizationTest {
    @TempDir Path directory;
    @Test void metadataResolutionRunsOffTheCallingThread() throws Exception {
        var cache = new MusicAssetCache(directory);
        Thread caller = Thread.currentThread();
        var resolved = new CompletableFuture<Thread>();
        try {
            var result = cache.requestCover(1, () -> { resolved.complete(Thread.currentThread()); return "invalid-url"; });
            assertNotSame(caller, resolved.get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        } finally { cache.shutdown(); }
    }
    @Test void imageDimensionLimitRejectsOversizedCoverBeforeDecode() throws Exception {
        byte[] image = CacheDataProtectionTest.png();
        java.nio.ByteBuffer.wrap(image).putInt(16, 20_000_000);
        Path file = directory.resolve("large.png"); Files.write(file, image);
        assertThrows(java.io.IOException.class, () -> CacheTransfer.validateImage(file));
    }
    @Test void invalidIdentitiesNeverReachAResolverOrCreateFiles() throws Exception {
        var cache = new MusicAssetCache(directory);
        try {
            assertThrows(ExecutionException.class, () -> cache.requestCover(0, () -> { throw new AssertionError(); }).get());
            assertThrows(ExecutionException.class, () -> cache.requestCover(1, null).get());
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        } finally { cache.shutdown(); }
    }
}
