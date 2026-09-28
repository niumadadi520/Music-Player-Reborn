package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

 


public final class QqUrlParser {

    private static final Map<String, ParsedUrl.ResourceType> ROUTES = Map.of("songdetail", ParsedUrl.ResourceType.SONG, "song", ParsedUrl.ResourceType.SONG, "albumdetail", ParsedUrl.ResourceType.ALBUM, "playlist", ParsedUrl.ResourceType.PLAYLIST);

    private static final Set<String> SHORT_HOSTS = Set.of("c.y.qq.com", "c6.y.qq.com");

    private QqUrlParser() {
    }

    public static ParsedUrl parse(String input) {
        return parse(input, QqUrlParser::expand);
    }

    static ParsedUrl parse(String input, Function<URI, URI> redirects) {
        if (input == null || input.isBlank())
            return null;
        String text = input.strip();
        if (text.matches("[A-Za-z0-9]{10,16}"))
            return new ParsedUrl(ParsedUrl.ResourceType.SONG, text);
        try {
            URI address = URI.create(text.contains(":") ? text : "https://" + text);
            if (!allowed(address))
                return null;
            if (SHORT_HOSTS.contains(address.getHost().toLowerCase(Locale.ROOT)) && redirects != null)
                address = redirects.apply(address);
            if (!allowed(address))
                return null;
            String route = address.getPath();
            String query = address.getRawQuery();
            if (address.getRawFragment() != null && address.getRawFragment().startsWith("/")) {
                URI fragment = URI.create(address.getRawFragment());
                route = fragment.getPath();
                query = fragment.getRawQuery();
            }
            String[] segments = Objects.requireNonNullElse(route, "").split("/");
            for (int i = 0; i + 1 < segments.length; i++) {
                var type = ROUTES.get(segments[i].toLowerCase(Locale.ROOT));
                if (type != null && valid(segments[i + 1]))
                    return new ParsedUrl(type, segments[i + 1]);
            }
            Map<String, String> fields = new LinkedHashMap<>();
            if (query != null)
                for (String field : query.split("&")) {
                    int equal = field.indexOf('=');
                    if (equal < 0)
                        continue;
                    fields.put(URLDecoder.decode(field.substring(0, equal), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT), URLDecoder.decode(field.substring(equal + 1), StandardCharsets.UTF_8));
                }
            String path = Objects.requireNonNullElse(route, "").toLowerCase(Locale.ROOT);
            if (path.endsWith("taoge.html") && valid(fields.get("id")))
                return new ParsedUrl(ParsedUrl.ResourceType.PLAYLIST, fields.get("id"));
            if (path.endsWith("playsong.html")) {
                String id = fields.getOrDefault("songmid", fields.get("id"));
                if (valid(id))
                    return new ParsedUrl(ParsedUrl.ResourceType.SONG, id);
            }
        } catch (RuntimeException malformed) {
            return null;
        }
        return null;
    }

    private static boolean valid(String id) {
        return id != null && id.matches("[A-Za-z0-9]{1,32}");
    }

    private static boolean allowed(URI uri) {
        if (!QqHttp.http(uri))
            return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("y.qq.com") || host.endsWith(".y.qq.com");
    }

    private static URI expand(URI uri) {
        try {
            return QqHttp.LIVE.exchange(new QqHttp.Request(uri, "HEAD", null, Map.of(), 65536, true)).uri();
        } catch (Exception unavailable) {
            return null;
        }
    }
}
