package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.*;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import static com.mengsama.mod.mengsamanetmusic.api.QqLoginService.*;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;

 


public final class QqAuthorizationFlow {

    public static final String APP = "716027609", OAUTH_APP = "100497308";

    public static final String JUMP = "https://graph.qq.com/oauth2.0/login_jump";

    public static final String CALLBACK = "https://y.qq.com/wk_v17/common_login.html?type=QQ&&redirect=";

    private final QqHttp.Transport http;

    private final QqLoginState session;

    private final Clock clock;

    private final Consumer<QqCredential> save;

    public QqAuthorizationFlow(QqHttp.Transport http, QqLoginState session, Clock clock, Consumer<QqCredential> save) {
        this.http = http;
        this.session = session;
        this.clock = clock;
        this.save = save;
    }

    public static final class Failure extends IOException {

        public final LoginError type;

        Failure(LoginError type) {
            super(type.name());
            this.type = type;
        }
    }

    public byte[] qr(long token) throws IOException {
        session.phase(token, LoginState.FETCHING_QR);
        Map<String, String> query = new LinkedHashMap<>(Map.of("appid", APP, "e", "2", "l", "M", "s", "3", "d", "72", "v", "4", "daid", "383", "pt_3rd_aid", OAUTH_APP, "u1", JUMP));
        query.put("t", Double.toString(java.util.concurrent.ThreadLocalRandom.current().nextDouble()));
        URI endpoint = QqProtocol.query("https://xui.ptlogin2.qq.com/ssl/ptqrshow", query);
        QqHttp.Response response = send(token, "QR", QqHttp.Request.get(endpoint, Map.of(), 1 << 20, true)).successful();
        String signature = QqLoginReply.cookies(response.values("Set-Cookie")).getOrDefault("qrsig", "");
        if (signature.isBlank())
            throw new Failure(LoginError.QR_COOKIE_MISSING);
        if (response.body().length == 0)
            throw new Failure(LoginError.QR_REQUEST_FAILED);
        session.signature(token, signature);
        session.phase(token, LoginState.WAITING_SCAN);
        return response.body();
    }

    public LoginState poll(long token) throws IOException {
        String signature = session.signature(token);
        if (signature.isBlank())
            throw new Failure(LoginError.QR_COOKIE_MISSING);
        session.phase(token, LoginState.WAITING_SCAN);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("u1", JUMP);
        query.put("ptqrtoken", Long.toString(QqLoginReply.token(signature, 0)));
        query.putAll(Map.of("ptredirect", "0", "h", "1", "t", "1", "g", "1", "from_ui", "1", "ptlang", "2052", "js_ver", "25072815", "js_type", "1", "login_sig", "", "pt_uistyle", "40"));
        query.put("aid", APP);
        query.put("daid", "383");
        query.put("pt_3rd_aid", OAUTH_APP);
        query.put("action", "0-0-" + clock.millis());
        var reply = send(token, "POLL", QqHttp.Request.get(QqProtocol.query("https://xui.ptlogin2.qq.com/ssl/ptqrlogin", query), Map.of("Cookie", "qrsig=" + signature), 65536, true)).successful();
        var status = QqLoginReply.read(reply.text());
        if (status.code().isBlank() || status.state() == LoginState.FAILED)
            throw new Failure(LoginError.INVALID_CALLBACK);
        if (status.state() != LoginState.VERIFYING) {
            session.phase(token, status.state());
            return status.state();
        }
        session.phase(token, LoginState.VERIFYING);
        URI verification;
        try {
            verification = URI.create(status.verificationUrl());
        } catch (IllegalArgumentException bad) {
            throw new Failure(LoginError.INVALID_CALLBACK);
        }
        if (!safeVerification(verification))
            throw new Failure(LoginError.INVALID_CALLBACK);
         
        var verified = send(token, "VERIFY", QqHttp.Request.get(verification, Map.of(), 1 << 20, false));
        if (verified.status() / 100 != 2 && verified.status() / 100 != 3)
            throw new Failure(LoginError.VERIFICATION_REQUEST_FAILED);
        Map<String, String> cookies = verificationCookies(token, reply, verified);
        if (!hasAuthorizationSession(cookies))
            throw new Failure(LoginError.SESSION_COOKIE_MISSING);
        String account = cookies.get("pt2gguin");
        session.phase(token, LoginState.AUTHORIZING);
        String code = authorize(token, account, cookies.getOrDefault("pt_oauth_token", ""), cookies.getOrDefault("p_skey", ""));
        if (code.isBlank())
            throw new Failure(LoginError.OAUTH_CODE_MISSING);
        session.phase(token, LoginState.LOGGING_IN);
        QqCredential credential = musicLogin(token, account, code);
        if (credential == null)
            throw new Failure(LoginError.MUSIC_LOGIN_FAILED);
        if (!credential.isValid())
            throw new Failure(LoginError.INVALID_CREDENTIAL);
        session.commit(token, () -> save.accept(credential));
        session.phase(token, LoginState.SUCCESS);
        return LoginState.SUCCESS;
    }

    private Map<String,String> verificationCookies(long token, QqHttp.Response poll, QqHttp.Response first) throws IOException {
        List<String> headers = new ArrayList<>(poll.values("Set-Cookie"));
        java.net.CookieManager jar = new java.net.CookieManager(null, QqAuthorizationFlow::acceptSessionCookie);
        jar.put(poll.uri(), poll.headers());
        QqHttp.Response response = first;
        Set<URI> visited = new HashSet<>();
        for (int hop=0; hop<4; hop++) {
            session.require(token);
            headers.addAll(response.values("Set-Cookie"));
            jar.put(response.uri(), response.headers());
             
             
            Map<String,String> values = authorizationCookies(jar);
            if (values.getOrDefault("pt2gguin", "").isBlank()) {
                for (String alias : List.of("p_uin", "uin")) {
                    String account = values.getOrDefault(alias, "");
                    if (account.matches("o?[0-9]{1,24}")) { values.put("pt2gguin", account); break; }
                }
            }
            session.cookiePresence(token, values);
            session.cookieInspection(token, headers);
            if (List.of("pt2gguin","p_skey").stream().allMatch(k -> !values.getOrDefault(k,"").isBlank())) return values;
            if (response.status()/100!=3 || response.values("Location").isEmpty()) {
                 
                 
                if (hasAuthorizationSession(values)) return values;
                break;
            }
            URI next;
            try { next=response.uri().resolve(response.values("Location").get(0)); }
            catch (IllegalArgumentException invalid) { throw new Failure(LoginError.INVALID_CALLBACK); }
            if (!safeVerification(next) || !"https".equals(next.getScheme()) || !visited.add(next)) throw new Failure(LoginError.INVALID_CALLBACK);
            Map<String,String> requestHeaders = new HashMap<>();
            jar.get(next,Map.of()).forEach((name,rows)->requestHeaders.put(name,String.join("; ",rows)));
            response=send(token,"VERIFY_REDIRECT",QqHttp.Request.get(next,requestHeaders,1<<20,false));
            if(response.status()/100!=2 && response.status()/100!=3)throw new Failure(LoginError.VERIFICATION_REQUEST_FAILED);
        }
        throw new Failure(LoginError.SESSION_COOKIE_MISSING);
    }

    private static Map<String,String> authorizationCookies(java.net.CookieManager jar) throws IOException {
        URI target = URI.create("https://graph.qq.com/oauth2.0/authorize");
        Map<String,String> values = new LinkedHashMap<>();
         
         
        for (String header : jar.get(target, Map.of()).getOrDefault("Cookie", List.of())) {
            for (String pair : header.split(";")) {
                if (pair.strip().startsWith("$")) continue;
                try {
                    for (java.net.HttpCookie cookie : java.net.HttpCookie.parse(pair))
                        values.putIfAbsent(cookie.getName(), cookie.getValue());
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return values;
    }

    private static boolean acceptSessionCookie(URI origin, java.net.HttpCookie cookie) {
        String host = Objects.requireNonNullElse(origin.getHost(), "").toLowerCase(Locale.ROOT);
        String domain = Objects.requireNonNullElse(cookie.getDomain(), host).toLowerCase(Locale.ROOT);
        if (domain.startsWith(".")) domain = domain.substring(1);
         
         
        boolean accepted = (domain.equals("qq.com") || domain.endsWith(".qq.com"))
                && (host.equals(domain) || host.endsWith("." + domain));
        if (accepted) cookie.setVersion(0);  
        return accepted;
    }

    private String authorize(long token, String account, String oauth, String key) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.putAll(Map.of("response_type", "code", "client_id", OAUTH_APP, "redirect_uri", CALLBACK, "scope", "get_user_info", "state", "y_new.top.pop.logout", "switch", "", "from_ptlogin", "1", "src", "1", "update_auth", "1", "openapi", "1010"));
        fields.put("g_tk", Long.toString(QqLoginReply.token(key, 5381)));
        fields.put("auth_time", Long.toString(clock.millis()));
        Map<String, String> headers = Map.of("Content-Type", "application/x-www-form-urlencoded", "Cookie", authorizationCookie(account, oauth, key),
                "User-Agent", QqProtocol.USER_AGENT, "Origin", "https://graph.qq.com", "Referer", JUMP);
        var response = send(token, "OAUTH", QqHttp.Request.post(URI.create("https://graph.qq.com/oauth2.0/authorize"), QqProtocol.form(fields), headers, false));
        if (response.status() / 100 != 2 && response.status() / 100 != 3)
            throw new Failure(LoginError.OAUTH_REQUEST_FAILED);
        return QqLoginReply.authorizationCode(response.headers(), response.text());
    }

    private QqCredential musicLogin(long token, String account, String code) throws IOException {
        JsonObject common = properties("_channelid", "208", "_os_version", "6.2.9200-2", "authst", "", "ct", "19", "cv", "2121", "guid", "", "patch", "118", "tmeAppID", "qqmusic", "tmeLoginType", 2, "uin", account.replaceFirst("^o0*", ""));
        JsonObject parameters = properties("appid", Integer.parseInt(OAUTH_APP), "code", code, "deviceName", "minecraft", "forceRefreshToken", 0, "onlyNeedAccessToken", 0);
        JsonObject payload = properties("comm", common, "music.login.LoginServer.Login", QqProtocol.call("music.login.LoginServer", "Login", parameters));
        URI endpoint = QqProtocol.query(QqProtocol.CATALOG.toString(), Map.of("pcachetime", Long.toString(clock.instant().getEpochSecond())));
        var response = send(token, "MUSIC", QqHttp.Request.post(endpoint, payload.toString(), Map.of("Content-Type", "application/json; charset=utf-8", "User-Agent", QqProtocol.USER_AGENT), true)).successful();
        JsonObject result = parse(response.text());
        session.serviceCodes(token,number(result,"code",0),number(object(result,"music.login.LoginServer.Login"),"code",-1));
        return credential(response.text(), clock.instant().getEpochSecond());
    }

    private QqHttp.Response send(long token, String step, QqHttp.Request request) throws IOException {
        session.require(token);
        session.note(token, step, 0, 0);
        Map<String,String> headers = new HashMap<>();
        headers.put("User-Agent", QqProtocol.USER_AGENT);
        headers.put("Referer", JUMP);
        headers.putAll(request.headers());
        QqHttp.Response response = http.exchange(new QqHttp.Request(request.uri(), request.method(),
                request.body(), headers, request.limit(), request.redirects()));
        session.require(token);
        session.note(token, step, response.status(), response.body().length);
        session.diagnostic(token, QqLoginDiagnostics.response(response));
        return response;
    }

    public static String authorizationCookie(String account, String token, String key) {
        return "p_uin=" + account + (token == null || token.isBlank() ? "" : "; pt_oauth_token=" + token)
                + (key == null || key.isBlank() ? "" : "; p_skey=" + key);
    }

    private static boolean hasAuthorizationSession(Map<String,String> cookies) {
        return !cookies.getOrDefault("pt2gguin", "").isBlank()
                && (!cookies.getOrDefault("p_skey", "").isBlank()
                || !cookies.getOrDefault("pt_oauth_token", "").isBlank());
    }

    public static boolean safeVerification(URI target) {
        if (!QqHttp.http(target))
            return false;
        String host = target.getHost().toLowerCase(Locale.ROOT);
        return host.equals("qq.com") || host.endsWith(".qq.com");
    }

    public static QqCredential credential(String response, long issuedFallback) {
        try {
            JsonObject root = parse(response);
            if (number(object(root, "music.login.LoginServer.Login"), "code", -1) != 0)
                return null;
            JsonObject payload = data(root, "music.login.LoginServer.Login");
            for (JsonObject candidate : List.of(payload, object(payload,"userInfo"), object(payload,"user_info"))) {
                QqCredential decoded = QqCredentialFile.decode(candidate, issuedFallback);
                 
                if (!decoded.account().isBlank() && !decoded.accessKey().isBlank()) return decoded;
            }
            return null;
        } catch (IOException invalid) {
            return null;
        }
    }
}
