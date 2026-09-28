package com.mengsama.mod.mengsamanetmusic.client.artwork;

import com.mengsama.mod.mengsamanetmusic.api.ArtworkMetadata;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkLoadingTest {
    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
    @Test void largeCoverIsSubsampledToBoundGpuMemory() throws Exception {
        BufferedImage image = new BufferedImage(2048, 1024, BufferedImage.TYPE_INT_RGB);
        var decoded = ArtworkPixels.decode(png(image));
        assertEquals(512, decoded.getWidth()); assertEquals(256, decoded.getHeight());
    }
    @Test void smallPngKeepsColorsAndAlpha() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0x804422EE);
        assertEquals(0x804422EE, ArtworkPixels.decode(png(image)).getRGB(1, 1));
    }
    @Test void oversizedHeaderIsRejectedWithoutAllocatingItsClaimedPixels() throws Exception {
        byte[] bytes = png(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        ByteBuffer.wrap(bytes).putInt(16, 100000).putInt(20, 100000);
        CRC32 crc = new CRC32(); crc.update(bytes, 12, 17); ByteBuffer.wrap(bytes).putInt(29, (int)crc.getValue());
        assertThrows(IOException.class, () -> ArtworkPixels.decode(bytes));
        assertThrows(IOException.class, () -> ArtworkPixels.decode("<html>error".getBytes()));
    }
    @Test void metadataUnderstandsBothAlbumFieldsAndRejectsFileAddresses() throws Exception {
        assertEquals("https://cdn.example/cover.png", ArtworkMetadata.firstAlbum("{\"songs\":[{\"al\":{\"picUrl\":\"https://cdn.example/cover.png\"}}]}").toString());
        assertEquals("https://cdn.example/old.png", ArtworkMetadata.firstAlbum("{\"songs\":[{\"album\":{\"picUrl\":\"https://cdn.example/old.png\"}}]}").toString());
        assertThrows(IOException.class, () -> ArtworkMetadata.firstAlbum("{\"songs\":[{\"al\":{\"picUrl\":\"file:///secret\"}}]}"));
    }
    @Test void coverRedirectUsesDecodableFormatsAndRejectsLoops() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = png(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
        List<String> received = new ArrayList<>();
        server.createContext("/start", exchange -> { exchange.getResponseHeaders().add("Location", "/image"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/image", exchange -> {
            received.add(exchange.getRequestHeaders().getFirst("Accept")); received.add(exchange.getRequestHeaders().getFirst("Referer"));
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/loop", exchange -> { exchange.getResponseHeaders().add("Location", "/loop"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        try {
            server.start(); String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertArrayEquals(body, ArtworkImages.fetch(new URL(base + "/start"), true));
            assertTrue(received.get(0).contains("image/png")); assertFalse(received.get(0).contains("webp"));
            assertEquals("https://y.qq.com/", received.get(1));
            assertThrows(IOException.class, () -> ArtworkImages.fetch(new URL(base + "/loop"), false));
            assertThrows(IOException.class, () -> ArtworkImages.fetch(new URL("file:///local-image"), false));
        } finally { server.stop(0); }
    }
}
