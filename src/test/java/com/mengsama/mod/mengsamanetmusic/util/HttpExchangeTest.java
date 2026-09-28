package com.mengsama.mod.mengsamanetmusic.util;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpExchangeTest {
    @Test void redirectsDropCredentialsAcrossOriginsAnd303ChangesPostToGet() throws Exception {
        var origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> cookie = new AtomicReference<>(), auth = new AtomicReference<>(), method = new AtomicReference<>();
        other.createContext("/result", exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            byte[] reply = "成功".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        origin.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/result");
            exchange.sendResponseHeaders(303, -1); exchange.close();
        });
        try {
            origin.start(); other.start();
            assertEquals("成功", NetWorker.post("http://127.0.0.1:" + origin.getAddress().getPort() + "/start", "payload",
                    Map.of("Cookie", "test-only", "Authorization", "test-only")));
            assertNull(cookie.get()); assertNull(auth.get()); assertEquals("GET", method.get());
        } finally { origin.stop(0); other.stop(0); }
    }
    @Test void defaultPortsAreSameOriginButDifferentPortsAreNot() {
        assertTrue(HttpExchange.sameOrigin(URI.create("https://example.invalid/a"), URI.create("https://example.invalid:443/b")));
        assertFalse(HttpExchange.sameOrigin(URI.create("http://localhost:80"), URI.create("http://localhost:81")));
    }
}
