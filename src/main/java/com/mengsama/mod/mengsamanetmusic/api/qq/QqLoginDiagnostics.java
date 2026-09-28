package com.mengsama.mod.mengsamanetmusic.api.qq;

import java.net.HttpCookie;
import java.net.URI;
import java.util.*;

 
public final class QqLoginDiagnostics {
    private QqLoginDiagnostics() { }

    public static String response(QqHttp.Response response) {
        int seen = 0, expired = 0, empty = 0, malformed = 0;
        Set<String> domains = new TreeSet<>();
        for (String header : response.values("Set-Cookie")) try {
            for (HttpCookie cookie : HttpCookie.parse(header)) if (cookie.getName().equals("p_skey")) {
                seen++;
                if (cookie.hasExpired()) expired++;
                if (cookie.getValue().isBlank()) empty++;
                domains.add(domain(cookie.getDomain()));
            }
        } catch (IllegalArgumentException ignored) { malformed++; }
        Set<String> redirects = new TreeSet<>();
        for (String location : response.values("Location")) {
            try { redirects.add(endpoint(response.uri().resolve(location))); }
            catch (IllegalArgumentException ignored) { redirects.add("INVALID"); }
        }
        return "response[target=" + endpoint(response.uri()) + ", cookies=" + response.values("Set-Cookie").size()
                + ", key_headers=" + seen + ", expired=" + expired + ", empty=" + empty
                + ", malformed=" + malformed + ", key_domains=" + domains + ", redirects=" + redirects + "]";
    }

    private static String domain(String value) {
        if (value == null) return "HOST_ONLY";
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "graph.qq.com", ".graph.qq.com" -> "GRAPH";
            case "qq.com", ".qq.com" -> "QQ_PARENT";
            case "ptlogin2.qq.com", ".ptlogin2.qq.com" -> "PTLOGIN";
            default -> "OTHER";
        };
    }

    private static String endpoint(URI uri) {
        String host = Objects.requireNonNullElse(uri.getHost(), "").toLowerCase(Locale.ROOT);
        String path = Objects.requireNonNullElse(uri.getPath(), "");
        if (host.equals("y.qq.com")) return path.equals("/wk_v17/common_login.html") ? "MUSIC_CALLBACK" : "MUSIC_OTHER";
        if (host.equals("graph.qq.com")) return switch (path) {
            case "/oauth2.0/authorize" -> "GRAPH_AUTHORIZE";
            case "/oauth2.0/login_jump" -> "GRAPH_LOGIN_JUMP";
            case "/oauth2.0/show" -> "GRAPH_LOGIN_PAGE";
            default -> "GRAPH_OTHER";
        };
        if (host.equals("ssl.ptlogin2.graph.qq.com")) return "GRAPH_CHECK";
        if (host.equals("ssl.ptlogin2.qq.com")) return "PTLOGIN_CHECK";
        if (host.equals("xui.ptlogin2.qq.com")) return switch (path) {
            case "/ssl/ptqrshow" -> "QR_IMAGE";
            case "/ssl/ptqrlogin" -> "QR_POLL";
            case "/cgi-bin/xlogin" -> "LOGIN_BOOTSTRAP";
            default -> "PTLOGIN_OTHER";
        };
        return "OTHER";
    }
}
