package com.mengsama.mod.mengsamanetmusic.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class QqLoginSessionTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesPollingStatesAndVerificationUrl() {
        com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply waiting = com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.read("ptuiCB('66','0','','0','二维码未失效','')");
        com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply expired = com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.read("ptuiCB('65','0','','0','二维码已失效','')");
        com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply verified = com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.read(
                "ptuiCB('0','0','https://ssl.ptlogin2.qq.com/check_sig','0','登录成功','Nick')");

        assertEquals(QqLoginService.LoginState.WAITING_SCAN, waiting.state());
        assertEquals(QqLoginService.LoginState.QR_EXPIRED, expired.state());
        assertEquals(QqLoginService.LoginState.VERIFYING, verified.state());
        assertEquals("https://ssl.ptlogin2.qq.com/check_sig", verified.verificationUrl());
        assertEquals(QqLoginService.LoginState.WAITING_SCAN, com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.read("bad body").state());
    }

    @Test
    void mergesEverySetCookieAndPreservesQrsig() {
        var cookies = QqLoginReply.cookies(List.of("qrsig=signature; Path=/; HttpOnly", "p_skey=old; Path=/",
                "pt_oauth_token=oauth; Secure", "p_skey=new; Path=/", "pt2gguin=o12345; Path=/"));
        assertEquals("signature", cookies.get("qrsig"));
        assertEquals("new", cookies.get("p_skey"));
        assertEquals("oauth", cookies.get("pt_oauth_token"));
        assertEquals("o12345", cookies.get("pt2gguin"));
    }

    @Test
    void buildsExactProvenOAuthCookieContract() {
        assertEquals("p_uin=o12345; pt_oauth_token=oauth; p_skey=session",
                com.mengsama.mod.mengsamanetmusic.api.qq.QqAuthorizationFlow.authorizationCookie("o12345", "oauth", "session"));
    }

    @Test
    void verificationStartsWithoutQrCookieButRedirectsUseNewSessionCookies() throws Exception {
        List<String> receivedCookies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        
        server.createContext("/check_sig", exchange -> {
            receivedCookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            redirect(exchange, "/landing", "p_skey=session; Path=/");
        });
        server.createContext("/landing", exchange -> {
            receivedCookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/check_sig");
        var response = QqHttp.LIVE.exchange(QqHttp.Request.get(start, Map.of(), 4096, true));
        assertEquals(200, response.status());
        assertNull(receivedCookies.get(0));
        assertTrue(receivedCookies.get(1).contains("p_skey=session"));
    }

    @Test
    void oauthCodeFallbacksHandleHeaderCaseMultipleValuesAndResponseBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth", exchange -> {
            exchange.getResponseHeaders().add("location", "https://example.invalid/no-code");
            exchange.getResponseHeaders().add("location", "https://example.invalid/callback?state=ok&code=header-code");
            byte[] body = "fallback callback: code=body-code&state=ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/oauth");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals("header-code", com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.authorizationCode(response.headers().map(),
                "https://example.invalid/no-code"));
        assertEquals("body-code", com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.code(response.body()));
        assertEquals("primary-code", com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.code(
                "https://example.invalid/callback?code=primary-code&state=ok"));
    }

    @Test
    void followsRelativeRedirectChainWhileMergingAndSendingCookies() throws Exception {
        List<String> receivedCookies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        
        server.createContext("/start", exchange -> redirect(exchange, "/middle", "qrsig=keep; Path=/"));
        server.createContext("/middle", exchange -> {
            receivedCookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            redirect(exchange, "final", "p_skey=session; Path=/");
        });
        server.createContext("/final", exchange -> {
            receivedCookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getResponseHeaders().add("Set-Cookie", "pt_oauth_token=oauth; Path=/");
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
        var response = QqHttp.LIVE.exchange(QqHttp.Request.get(start, Map.of(), 4096, true));
        var cookies = QqLoginReply.cookies(response.values("Set-Cookie"));
        assertEquals(200, response.status());
        assertEquals("ok", response.text());
        assertEquals("/final", response.uri().getPath());
        assertEquals("keep", cookies.get("qrsig"));
        assertEquals("session", cookies.get("p_skey"));
        assertEquals("oauth", cookies.get("pt_oauth_token"));
        assertTrue(receivedCookies.get(0).contains("qrsig=keep"));
        assertTrue(receivedCookies.get(1).contains("qrsig=keep"));
        assertTrue(receivedCookies.get(1).contains("p_skey=session"));
    }

    private static void redirect(HttpExchange exchange, String location, String cookie) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
