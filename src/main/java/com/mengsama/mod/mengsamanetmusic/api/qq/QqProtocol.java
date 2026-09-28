package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.JsonObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class QqProtocol {

    public static final URI CATALOG = URI.create("https://u6.y.qq.com/cgi-bin/musicu.fcg");

    public static final URI DETAIL = URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg");

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    public static JsonObject call(String module, String method, JsonObject parameters) {
        return QqFields.properties("module", module, "method", method, "param", parameters);
    }

    public static JsonObject envelope(String version, String key, JsonObject call) {
        return QqFields.properties("comm", QqFields.properties("uin", "0", "format", "json", "ct", 19, "cv", version), key, call);
    }

    public static Map<String, String> headers(String cookie, boolean json) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("User-Agent", USER_AGENT);
        fields.put("Referer", "https://y.qq.com/");
        if (json) {
            fields.put("Origin", "https://y.qq.com");
            fields.put("Accept", "application/json, text/plain, */*");
            fields.put("Accept-Encoding", "gzip, deflate");
            fields.put("Content-Type", "application/json;charset=utf-8");
        }
        if (cookie != null && !cookie.isBlank())
            fields.put("Cookie", cookie);
        return Map.copyOf(fields);
    }

    public static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
    }

    public static URI query(String base, Map<String, String> values) {
        return URI.create(base + "?" + form(values));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static Map<String, String> cookies(String header) {
        Map<String, String> values = new LinkedHashMap<>();
        if (header != null)
            for (String pair : header.split(";")) {
                int equal = pair.indexOf('=');
                if (equal < 1)
                    continue;
                values.put(pair.substring(0, equal).strip().toLowerCase(Locale.ROOT), pair.substring(equal + 1).strip());
            }
        return values;
    }

    public static String account(String cookie) {
        var fields = cookies(cookie);
        for (String name : List.of("uin", "wxuin", "o2_uin")) {
            String value = fields.getOrDefault(name, "");
            if (!value.isBlank())
                return value.replaceFirst("^o0*", "");
        }
        return "0";
    }

    public static List<String> artwork(String mid) {
        if (mid == null || !mid.strip().matches("[A-Za-z0-9]+"))
            return List.of();
        String suffix = "M000" + mid.strip() + ".jpg";
        return List.of("https://y.gtimg.cn/music/photo_new/T002R300x300" + suffix, "https://y.gtimg.cn/music/photo_new/T002R500x500" + suffix, "https://y.gtimg.cn/music/photo_new/T002R150x150" + suffix, "https://y.qq.com/music/photo_new/T002R300x300" + suffix);
    }
}
