package com.mengsama.mod.mengsamanetmusic.cache;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CacheTransferTest {
    @TempDir Path directory;
    @Test void existingCacheIsNeverReplacedEvenByAFailedSource() throws Exception {
        Path cached = directory.resolve("keep.png"); byte[] original = {1,2,3,4}; Files.write(cached, original);
        CacheTransfer.fetch("invalid-url", cached);
        assertArrayEquals(original, Files.readAllBytes(cached));
    }
    @Test void htmlResponseLeavesNoPublishedOrTemporaryFile() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover", exchange -> {
            byte[] html = "<HTML><BODY>error</BODY></HTML>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        try {
            server.start();
            assertThrows(IOException.class, () -> CacheTransfer.fetch("http://127.0.0.1:" + server.getAddress().getPort() + "/cover", directory.resolve("bad.png")));
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        } finally { server.stop(0); }
    }
    @Test void transferLimitIsEnforcedWithoutPublishingOverflowBytes() {
        var sink = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> CacheTransfer.copy(new ByteArrayInputStream(new byte[32]), sink, 16));
        assertEquals(0, sink.size());
    }
    @Test void pngIsAcceptedWithoutChangingItsBytes() throws Exception {
        Path image = directory.resolve("cover.png");
        byte[] original = CacheDataProtectionTest.png(); Files.write(image, original);
        CacheTransfer.validateImage(image);
        assertArrayEquals(original, Files.readAllBytes(image));
    }
}
