package com.mengsama.mod.mengsamanetmusic.api.qq;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.util.function.Supplier;
import com.mengsama.mod.mengsamanetmusic.util.NetWorker;

 


public final class QqHttp {

    @FunctionalInterface
    public interface Transport {

        Response exchange(Request request) throws IOException;
    }

    public static final Transport LIVE = new QqHttp(NetWorker::getProxyFromConfig)::exchange;

    private final Supplier<Proxy> proxy;

    public QqHttp() {
        this(() -> Proxy.NO_PROXY);
    }

    public QqHttp(Supplier<Proxy> proxy) {
        this.proxy = Objects.requireNonNull(proxy);
    }

    public record Request(URI uri, String method, byte[] body, Map<String, String> headers, int limit, boolean redirects) {

        public Request {
            body = body == null ? new byte[0] : body.clone();
            headers = Map.copyOf(headers);
        }

        public static Request get(URI uri, Map<String, String> headers, int limit, boolean redirects) {
            return new Request(uri, "GET", null, headers, limit, redirects);
        }

        public static Request post(URI uri, String body, Map<String, String> headers, boolean redirects) {
            return new Request(uri, "POST", body.getBytes(StandardCharsets.UTF_8), headers, 4 << 20, redirects);
        }

        @Override
        public String toString() {
            return "QQ request " + method + " (sensitive fields omitted)";
        }
    }

    public record Response(URI uri, int status, Map<String, List<String>> headers, byte[] body) {

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public List<String> values(String key) {
            return headers.entrySet().stream().filter(e -> key.equalsIgnoreCase(e.getKey())).flatMap(e -> e.getValue().stream()).toList();
        }

        public Response successful() throws IOException {
            if (status / 100 != 2)
                throw new IOException("QQ HTTP status " + status);
            return this;
        }

        @Override
        public String toString() {
            return "QQ response HTTP " + status + " (body omitted)";
        }
    }

    public Response exchange(Request request) throws IOException {
        URI target = request.uri();
        String method = request.method();
        byte[] payload = request.body();
        boolean credentialsAllowed = true;
        Set<URI> visited = new HashSet<>();
        List<String> cookies = new ArrayList<>();
        CookieManager redirects = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        for (int hop = 0; hop <= 5; hop++) {
            if (!http(target) || !visited.add(target))
                throw new IOException("Invalid or cyclic QQ HTTP target");
            Map<String, String> outgoing = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (var header : request.headers().entrySet()) {
                if (!credentialsAllowed && Set.of("cookie", "authorization", "proxy-authorization").contains(header.getKey().toLowerCase(Locale.ROOT)))
                    continue;
                outgoing.put(header.getKey(), header.getValue());
            }
            List<String> carried = redirects.get(target, Map.of()).getOrDefault("Cookie", List.of());
            if (!carried.isEmpty()) {
                String initial = outgoing.get("Cookie");
                outgoing.put("Cookie", (initial == null ? "" : initial + "; ") + String.join("; ", carried));
            }
            HttpResponse<byte[]> response = exchangeIsolated(target, method, payload, outgoing, request.limit());
            int status = response.statusCode();
            Map<String, List<String>> headers = new LinkedHashMap<>(response.headers().map());
            redirects.put(target, headers);
            for (var entry : headers.entrySet()) if (entry.getKey().equalsIgnoreCase("set-cookie"))
                cookies.addAll(entry.getValue());
            String next = response.headers().firstValue("Location").orElse(null);
            if (request.redirects() && Set.of(301, 302, 303, 307, 308).contains(status) && next != null) {
                URI resolved;
                try {
                    resolved = target.resolve(next);
                } catch (IllegalArgumentException bad) {
                    throw new IOException("Invalid QQ redirect");
                }
                 
                if ("https".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(resolved.getScheme()))
                    throw new IOException("QQ redirect attempted TLS downgrade");
                credentialsAllowed &= sameOrigin(request.uri(), resolved);
                if (!sameOrigin(target, resolved)) {
                    cookies.clear();
                    redirects.getCookieStore().removeAll();
                }
                if (!sameOrigin(target, resolved) && payload.length > 0 && status != 303)
                    throw new IOException("QQ redirect would forward a request body across origins");
                if (status == 303) {
                    method = "GET";
                    payload = new byte[0];
                }
                target = resolved;
                continue;
            }
            headers.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase("set-cookie"));
            if (!cookies.isEmpty())
                headers.put("Set-Cookie", List.copyOf(cookies));
            byte[] data = read(new ByteArrayInputStream(response.body()), response.headers().firstValue("Content-Encoding").orElse(""), request.limit());
            return new Response(target, status, Map.copyOf(headers), data);
        }
        throw new IOException("QQ HTTP redirect limit exceeded");
    }

    private HttpResponse<byte[]> exchangeIsolated(URI target, String method, byte[] payload,
                                                  Map<String, String> headers, int limit) throws IOException {
         
         
        Proxy selected = Objects.requireNonNull(proxy.get());
        if (selected.type() == Proxy.Type.SOCKS)
            throw new IOException("SOCKS is not supported by the QQ HTTP client");
        ProxySelector selector = selected.type() == Proxy.Type.DIRECT ? HttpClient.Builder.NO_PROXY
                : ProxySelector.of((InetSocketAddress) selected.address());
        HttpClient client = HttpClient.newBuilder().proxy(selector).connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(15));
            headers.forEach(builder::header);
            builder.method(method, payload.length == 0 ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(payload));
             
            pending = client.sendAsync(builder.build(), info -> new BoundedBody((long) limit + 65536));
            return pending.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("QQ request interrupted", interrupted);
        } catch (TimeoutException timedOut) {
            throw new HttpTimeoutException("QQ response timeout");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof IOException io) throw io;
            throw new IOException("QQ transport failed", failed.getCause());
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            client.shutdownNow();
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private Flow.Subscription subscription;
        private long received;
        private boolean done;
        BoundedBody(long maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (done) return;
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > maximum) {
                subscription.cancel();
                onError(new IOException("QQ response exceeds encoded size limit"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) {
            if (!done) { done = true; delegate.onError(error); }
        }
        @Override public void onComplete() {
            if (!done) { done = true; delegate.onComplete(); }
        }
    }

    private static byte[] read(InputStream raw, String encoding, int maximum) throws IOException {
        try (raw) {
            InputStream data = raw;
            List<String> layers = new ArrayList<>(Arrays.asList(Objects.requireNonNullElse(encoding, "").toLowerCase(Locale.ROOT).split(",")));
            Collections.reverse(layers);
            for (String layer : layers) data = switch(layer.strip()) {
                case "", "identity" ->
                    data;
                case "gzip", "x-gzip" ->
                    new GZIPInputStream(data);
                case "deflate" ->
                    new InflaterInputStream(data);
                default ->
                    throw new IOException("Unsupported QQ content encoding");
            };
            try (InputStream decoded = data) {
                byte[] bytes = decoded.readNBytes(maximum + 1);
                if (bytes.length > maximum)
                    throw new IOException("QQ response exceeds decompressed size limit");
                return bytes;
            }
        }
    }

    public static boolean http(URI uri) {
        return uri != null && uri.getScheme() != null && uri.getHost() != null && uri.getUserInfo() == null && Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT));
    }

    private static boolean sameOrigin(URI first, URI next) {
        return Objects.equals(first.getScheme(), next.getScheme()) && Objects.equals(first.getHost(), next.getHost()) && port(first) == port(next);
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
    }
}
