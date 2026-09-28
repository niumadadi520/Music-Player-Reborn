package com.mengsama.mod.mengsamanetmusic.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CoverImageDownloaderTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void followsRedirectSendsImageHeadersAndDecodesActualBytes() throws Exception {
        byte[] png = png();
        AtomicReference<String> headers = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/cover.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/cover.png", exchange -> {
            headers.set(exchange.getRequestHeaders().getFirst("User-Agent") + "|"
                    + exchange.getRequestHeaders().getFirst("Referer") + "|"
                    + exchange.getRequestHeaders().getFirst("Accept"));
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();

        byte[] downloaded = CoverImageDownloader.download("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(downloaded)));
        assertTrue(headers.get().contains("MengSamaNetMusic"));
        assertTrue(headers.get().contains("https://music.163.com/"));
        assertTrue(headers.get().contains("image/png"));
    }

    @Test
    void rejectsHtmlAndFakeImageBytesAndModernFormats() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/html", exchange -> respond(exchange, "text/html", "<html>blocked</html>".getBytes()));
        server.createContext("/fake.jpg", exchange -> respond(exchange, "image/jpeg", "not jpeg".getBytes()));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        assertThrows(java.io.IOException.class, () -> CoverImageDownloader.download(base + "/html"));
        assertThrows(java.io.IOException.class, () -> CoverImageDownloader.download(base + "/fake.jpg"));
        assertThrows(java.io.IOException.class, () -> CoverImageDownloader.download(base + "/cover.webp"));
        assertThrows(java.io.IOException.class, () -> CoverImageDownloader.download(base + "/cover.avif"));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String type, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
