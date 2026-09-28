package com.mengsama.mod.mengsamanetmusic.util;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

 
public final class AudioRequestHeaders {
    private AudioRequestHeaders() {}
    public static boolean isNetEaseApi(URL url) {
        return "https".equalsIgnoreCase(url.getProtocol()) && "music.163.com".equalsIgnoreCase(url.getHost());
    }
    public static boolean sameOrigin(URL first, URL second) {
        return first.getProtocol().equalsIgnoreCase(second.getProtocol())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }
    private static int effectivePort(URL url) { return url.getPort() >= 0 ? url.getPort() : url.getDefaultPort(); }
    public static Map<String, String> sanitize(URL original, URL destination, Map<String, String> headers) {
        Map<String, String> safe = new HashMap<>();
        String host = original.getHost().toLowerCase(Locale.ROOT);
        boolean trusted = isNetEaseApi(original) || "https".equalsIgnoreCase(original.getProtocol())
                && (host.equals("qq.com") || host.endsWith(".qq.com"));
        boolean credentials = trusted && sameOrigin(original, destination);
        headers.forEach((key, value) -> {
            if (key == null || value == null || key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return;
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.equals("host") || lower.equals("proxy-authorization")) return;
            if (!credentials && (lower.equals("cookie") || lower.equals("cookie2") || lower.equals("authorization"))) return;
            safe.put(key, value);
        });
        return safe;
    }
}
