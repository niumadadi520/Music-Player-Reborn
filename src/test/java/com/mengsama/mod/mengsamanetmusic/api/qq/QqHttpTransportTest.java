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

    @Test void globalCookieHandlerCannotHideHttpOnlySessionOrInjectAnotherAccount() throws Exception {
        CookieHandler previous = CookieHandler.getDefault();
        CookieManager global = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        global.put(start, Map.of("Set-Cookie", List.of("other_account=unrelated; Path=/")));
        AtomicReference<String> sent = new AtomicReference<>();
        first.createContext("/start", exchange -> {
            sent.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getResponseHeaders().add("Set-Cookie", "p_skey=session-key; Path=/; HttpOnly");
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        try {
            CookieHandler.setDefault(global);
            var response = new QqHttp().exchange(QqHttp.Request.get(start, Map.of("Cookie", "qrsig=own-attempt"), 100, false));
            assertEquals("session-key", QqLoginReply.cookies(response.values("Set-Cookie")).get("p_skey"));
            assertEquals("qrsig=own-attempt", sent.get());
            assertSame(global, CookieHandler.getDefault());
            assertTrue(global.getCookieStore().getCookies().stream().noneMatch(c -> c.getName().equals("p_skey")));
        } finally {
            CookieHandler.setDefault(previous);
        }
    }

    @Test void oversizedEncodedResponseIsCancelled() throws Exception {
        first.createContext("/start", exchange -> {
            byte[] payload = new byte[100000];
            exchange.sendResponseHeaders(200, payload.length);
            try { exchange.getResponseBody().write(payload); }
            finally { exchange.close(); }
        });
        assertThrows(IOException.class, () -> new QqHttp().exchange(QqHttp.Request.get(start, Map.of(), 100, false)));
    }

    @Test void httpOnlyCookiesSurviveRedirectUnderGlobalCookieManager() throws Exception {
        CookieHandler previous = CookieHandler.getDefault();
        AtomicReference<String> sent = new AtomicReference<>();
        first.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/landing");
            exchange.getResponseHeaders().add("Set-Cookie", "p_skey=own-key; Path=/; HttpOnly");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        first.createContext("/landing", exchange -> {
            sent.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        try {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            var response = new QqHttp().exchange(QqHttp.Request.get(start, Map.of(), 100, true));
            assertEquals("own-key", QqLoginReply.cookies(response.values("Set-Cookie")).get("p_skey"));
            assertTrue(sent.get().contains("p_skey=own-key"));
        } finally { CookieHandler.setDefault(previous); }
    }
}
