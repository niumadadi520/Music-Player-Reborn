package com.mengsama.mod.mengsamanetmusic.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

 
public final class AppleMusicKitAuthorization {
    public enum State { IDLE, STARTING, WAITING, SUCCESS, FAILED, CANCELLED, TIMED_OUT }

    private static final Object LOCK = new Object();
    private static final AtomicLong REVISION = new AtomicLong();
    private static volatile String musicUserToken = "";
    private static volatile State state = State.IDLE;
    private static volatile HttpServer activeServer;
    private static volatile CompletableFuture<Boolean> activeFuture;

    private AppleMusicKitAuthorization() {}

    public static java.util.Map<String,String> libraryHeaders(String developerToken) {
        synchronized (LOCK) {
            if (!isAuthorized() || !isDeveloperTokenUsable(developerToken)) return java.util.Map.of();
            return java.util.Map.of("Authorization", "Bearer " + developerToken, "Music-User-Token", musicUserToken);
        }
    }

    public static State state() { return state; }
    public static long revision() { return REVISION.get(); }
    public static boolean isAuthorized() { return state == State.SUCCESS && !musicUserToken.isBlank(); }
    public static boolean isBusy() { return state == State.STARTING || state == State.WAITING; }

    public static boolean isDeveloperTokenUsable(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            return json.has("exp") && json.get("exp").getAsLong() > Instant.now().getEpochSecond() + 30;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static CompletableFuture<Boolean> authorize(String developerToken) {
        synchronized (LOCK) {
            if (!isDeveloperTokenUsable(developerToken)) return CompletableFuture.completedFuture(false);
            if (isBusy() && activeFuture != null) return activeFuture;
            setState(State.STARTING);
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            activeFuture = result;
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                activeServer = server;
                server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "mengsamanetmusic-musickit-callback");
                    thread.setDaemon(true);
                    return thread;
                }));
                server.createContext("/", exchange -> html(exchange, developerToken));
                server.createContext("/complete", exchange -> complete(exchange, result));
                server.createContext("/cancel", exchange -> cancelFromBrowser(exchange));
                server.start();
                setState(State.WAITING);
                int port = server.getAddress().getPort();
                result.orTimeout(3, TimeUnit.MINUTES).whenComplete((ok, error) -> {
                    synchronized (LOCK) {
                        stopServer();
                        activeFuture = null;
                        if (error != null && state != State.CANCELLED) setState(State.TIMED_OUT);
                        else if (Boolean.TRUE.equals(ok)) setState(State.SUCCESS);
                        else if (state != State.CANCELLED) setState(State.FAILED);
                    }
                });
                net.minecraft.Util.getPlatform().openUri(new URI("http://localhost:" + port + "/"));
            } catch (Exception e) {
                stopServer();
                activeFuture = null;
                setState(State.FAILED);
                result.completeExceptionally(e);
            }
            return result;
        }
    }

    public static void cancel() {
        synchronized (LOCK) {
            if (!isBusy()) return;
            setState(State.CANCELLED);
            CompletableFuture<Boolean> future = activeFuture;
            if (future != null) future.complete(false);
            stopServer();
        }
    }

     
    public static void revoke(String developerToken) {
        synchronized (LOCK) {
            cancel();
            musicUserToken = "";
            setState(State.IDLE);
        }
        if (isDeveloperTokenUsable(developerToken)) openRevokePage(developerToken);
    }

    private static void openRevokePage(String developerToken) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                String page = basePage(developerToken, """
                        <p>正在撤销 Apple Music 浏览器授权…</p><script>
                        (async()=>{try{await MusicKit.configure(CFG);await MusicKit.getInstance().unauthorize();}catch(e){}
                        document.body.innerHTML='<h2>Apple Music 授权已撤销</h2><p>可以关闭此页面。</p>';setTimeout(()=>window.close(),800);})();
                        </script>""");
                send(exchange, 200, "text/html; charset=utf-8", page);
            });
            server.start();
            int port = server.getAddress().getPort();
            CompletableFuture.delayedExecutor(20, TimeUnit.SECONDS).execute(() -> server.stop(0));
            net.minecraft.Util.getPlatform().openUri(new URI("http://localhost:" + port + "/"));
        } catch (Exception ignored) {
             
        }
    }

    private static void html(HttpExchange exchange, String developerToken) throws IOException {
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) { send(exchange, 403, "text/plain", "Forbidden"); return; }
        String body = """
                <h2>Apple Music 官方授权</h2>
                <p>Apple ID 与密码只会输入在 Apple 官方 MusicKit 授权窗口。本地模组只在内存中保留返回的用户令牌，不写日志或普通配置。</p>
                <button id='auth'>继续官方授权</button> <button id='cancel'>取消</button><p id='status'>等待操作</p>
                <script>
                const status=document.getElementById('status');
                document.getElementById('cancel').onclick=()=>fetch('/cancel',{method:'POST'}).then(()=>{status.textContent='已取消，可以关闭页面。'});
                document.getElementById('auth').onclick=async()=>{try{status.textContent='等待 Apple 授权…';await MusicKit.configure(CFG);
                const token=await MusicKit.getInstance().authorize();const r=await fetch('/complete',{method:'POST',headers:{'Content-Type':'text/plain'},body:token});
                status.textContent=r.ok?'授权成功，可以关闭页面。':'授权回传失败。';}catch(e){status.textContent='授权失败或已取消。';}};
                </script>
                """;
        send(exchange, 200, "text/html; charset=utf-8", basePage(developerToken, body));
    }

    private static String basePage(String developerToken, String body) {
        String token = developerToken.replace("\\", "\\\\").replace("'", "\\'").replace("</", "<\\/");
        return """
                <!doctype html><html><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'><title>MengSamaNetMusic MusicKit</title>
                <style>body{font:16px sans-serif;max-width:720px;margin:60px auto;padding:20px}button{padding:12px 20px}</style>
                <script src='https://js-cdn.music.apple.com/musickit/v3/musickit.js'></script><script>
                const CFG={developerToken:'%s',app:{name:'MengSamaNetMusic',build:'1.0.0'}};</script></head><body>%s</body></html>
                """.formatted(token, body);
    }

    private static void complete(HttpExchange exchange, CompletableFuture<Boolean> result) throws IOException {
        if (!localPost(exchange)) return;
        byte[] bytes = exchange.getRequestBody().readNBytes(8193);
        String token = new String(bytes, StandardCharsets.UTF_8).trim();
        if (token.isBlank() || bytes.length > 8192) { send(exchange, 400, "text/plain", "Invalid token"); return; }
        musicUserToken = token;
        send(exchange, 200, "text/plain; charset=utf-8", "OK");
        result.complete(true);
    }

    private static void cancelFromBrowser(HttpExchange exchange) throws IOException {
        if (!localPost(exchange)) return;
        send(exchange, 200, "text/plain", "OK");
        cancel();
    }

    private static boolean localPost(HttpExchange exchange) throws IOException {
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) { send(exchange, 403, "text/plain", "Forbidden"); return false; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, "text/plain", "Method Not Allowed"); return false; }
        return true;
    }

    private static void stopServer() {
        HttpServer server = activeServer;
        activeServer = null;
        if (server != null) server.stop(0);
    }

    private static void setState(State next) {
        state = next;
        REVISION.incrementAndGet();
    }

    private static void send(HttpExchange exchange, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self' https://js-cdn.music.apple.com; script-src 'self' 'unsafe-inline' https://js-cdn.music.apple.com; connect-src 'self' https://*.apple.com");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
