package com.mengsama.mod.mengsamanetmusic.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.nio.charset.StandardCharsets;

 
final class HttpExchange {
    static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final HttpClient TRANSPORT = client(HttpClient.Redirect.NEVER);
    static HttpClient client(HttpClient.Redirect redirects) {
        return HttpClient.newBuilder().followRedirects(redirects)
                .connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
    }
    static boolean sameOrigin(URI a, URI b) {
        return Objects.equals(a.getScheme(), b.getScheme()) && Objects.equals(a.getHost(), b.getHost())
                && effectivePort(a) == effectivePort(b);
    }
    private static int effectivePort(URI u) { return u.getPort() >= 0 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80; }
    static String text(String address, String body, Map<String,String> headers) throws IOException {
        URI original;
        try { original = URI.create(address); } catch (RuntimeException invalid) { throw new IOException("Invalid request address", invalid); }
        URI destination = original;
        Set<URI> visited = new HashSet<>();
        boolean credentialsAllowed = true;
        for (int hops = 0; hops <= 5; hops++) {
            if (!Set.of("http", "https").contains(destination.getScheme()) || !visited.add(destination))
                throw new IOException("Unsupported or cyclic HTTP redirect");
            var request = HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(30));
            if (headers != null) for (var field : headers.entrySet()) {
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (!credentialsAllowed && Set.of("authorization", "cookie", "proxy-authorization").contains(key)) continue;
                request.header(field.getKey(), field.getValue());
            }
            request.method(body == null ? "GET" : "POST", body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            HttpResponse<String> response;
            try { response = TRANSPORT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("HTTP request interrupted", interrupted); }
            if (!REDIRECTS.contains(response.statusCode())) return response.body();
            String next = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing redirect destination"));
            URI resolved;
            try { resolved = destination.resolve(next); } catch (IllegalArgumentException invalid) { throw new IOException("Invalid redirect destination", invalid); }
            credentialsAllowed &= sameOrigin(original, resolved);
            if (response.statusCode() == 303) body = null;
            destination = resolved;
        }
        throw new IOException("HTTP redirect limit exceeded");
    }
}
