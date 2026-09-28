package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class QqHttpTransportTest {
    private HttpServer first, second;
    private URI start, destination;
    private final List<String> received = new ArrayList<>();
    @BeforeEach void servers() throws Exception {
        first = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        start = URI.create("http://127.0.0.1:" + first.getAddress().getPort() + "/start");
        destination = URI.create("http://127.0.0.1:" + second.getAddress().getPort() + "/final");
        second.createContext("/final", exchange -> {
            received.add(exchange.getRequestMethod());
            received.add(exchange.getRequestHeaders().getFirst("Cookie"));
            received.add(exchange.getRequestHeaders().getFirst("Authorization"));
            received.add(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        first.start(); second.start();
    }
    @AfterEach void close() { if (first != null) first.stop(0); if (second != null) second.stop(0); }
    private void redirect(int status) {
        first.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", destination.toString());
            exchange.getResponseHeaders().add("Set-Cookie", "session=private; Path=/");
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
    }
    @Test void crossOriginSeeOtherStripsCredentialsAndPostBody() throws Exception {
        redirect(303);
        AtomicInteger proxyLookups = new AtomicInteger();
        var http = new QqHttp(() -> { proxyLookups.incrementAndGet(); return Proxy.NO_PROXY; });
        var response = http.exchange(QqHttp.Request.post(start, "code=private", Map.of("Cookie", "key=private", "Authorization", "Bearer private"), true));
        assertEquals(200, response.status());
        assertEquals(Arrays.asList("GET", null, null, ""), received);
        assertEquals(2, proxyLookups.get());
        assertTrue(response.values("Set-Cookie").isEmpty());
    }
    @Test void crossOriginPreservedPostIsRejectedBeforeSendingSecrets() {
        redirect(307);
        assertThrows(IOException.class, () -> new QqHttp().exchange(QqHttp.Request.post(start, "code=private", Map.of(), true)));
        assertTrue(received.isEmpty());
    }
    @Test void cookiesOutsideRedirectPathAreNotSent() throws Exception {
        first.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/landing");
            exchange.getResponseHeaders().add("Set-Cookie", "only=private; Path=/different/");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        AtomicReference<String> cookie = new AtomicReference<>();
        first.createContext("/landing", exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        new QqHttp().exchange(QqHttp.Request.get(start, Map.of(), 100, true));
        assertNull(cookie.get());
    }
}
