package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.mengsama.mod.mengsamanetmusic.api.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;
import static com.mengsama.mod.mengsamanetmusic.api.QqLoginService.*;

class QqAuthorizationFlowTest {
    private final QqLoginState state = new QqLoginState();
    private final List<QqCredential> saved = new ArrayList<>();
    private final List<QqHttp.Request> requests = new ArrayList<>();

    private QqHttp.Response response(QqHttp.Request request, int status, Map<String,List<String>> headers, String body) {
        return new QqHttp.Response(request.uri(), status, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    private QqHttp.Response happy(QqHttp.Request request) {
        requests.add(request);
        return switch (requests.size()) {
            case 1 -> response(request, 200, Map.of("set-cookie", List.of("qrsig=qr-secret; Path=/")), "png-image");
            case 2 -> response(request, 200, Map.of(), "ptuiCB('0','0','https://ssl.ptlogin2.qq.com/check_sig','0','ok','nick')");
            case 3 -> response(request, 302, Map.of("Set-Cookie", List.of("p_skey=session-secret; Path=/", "pt_oauth_token=oauth-secret", "pt2gguin=o12345")), "");
            case 4 -> response(request, 302, Map.of("location", List.of("https://y.qq.com/no-code", "https://y.qq.com/callback?code=auth-code&state=ok")), "");
            case 5 -> response(request, 200, Map.of(), "{\"code\":0,\"music.login.LoginServer.Login\":{\"code\":0,\"data\":{\"musicid\":\"12345\",\"musickey\":\"music-secret\",\"keyExpiresIn\":86400}}}");
            default -> throw new AssertionError("Unexpected request");
        };
    }

    @Test void completeLoginUsesIsolatedHeadersAndCommitsOnce() throws Exception {
        List<LoginState> phases = new ArrayList<>();
        state.listener(phases::add);
        var flow = new QqAuthorizationFlow(this::happy, state, Clock.systemUTC(), saved::add);
        long generation = state.reset();
        assertEquals("png-image", new String(flow.qr(generation), StandardCharsets.UTF_8));
        assertEquals(LoginState.SUCCESS, flow.poll(generation));
        assertEquals(5, requests.size());
        assertEquals("qrsig=qr-secret", requests.get(1).headers().get("Cookie"));
        assertTrue(requests.get(2).headers().isEmpty());
        assertFalse(requests.get(2).redirects());
        assertEquals("p_uin=o12345; pt_oauth_token=oauth-secret; p_skey=session-secret", requests.get(3).headers().get("Cookie"));
        assertFalse(requests.get(3).redirects());
        assertFalse(requests.get(4).headers().containsKey("Cookie"));
        var music = QqFields.parse(new String(requests.get(4).body(), StandardCharsets.UTF_8));
        assertEquals("auth-code", music.getAsJsonObject("music.login.LoginServer.Login").getAsJsonObject("param").get("code").getAsString());
        assertEquals("12345", music.getAsJsonObject("comm").get("uin").getAsString());
        assertEquals(1, saved.size());
        assertEquals("12345", saved.get(0).getMusicId());
        assertTrue(saved.get(0).isValid());
        assertTrue(phases.containsAll(List.of(LoginState.FETCHING_QR, LoginState.WAITING_SCAN, LoginState.VERIFYING, LoginState.AUTHORIZING, LoginState.LOGGING_IN, LoginState.SUCCESS)));
        assertFalse(state.diagnostics().contains("secret"));
        assertFalse(requests.toString().contains("secret"));
    }

    @Test void resettingWhileRequestCompletesPreventsSaveAndFurtherRequests() throws Exception {
        var flow = new QqAuthorizationFlow(request -> {
            var result = happy(request);
            if (requests.size() == 5) state.reset();
            return result;
        }, state, Clock.systemUTC(), saved::add);
        long token = state.reset();
        flow.qr(token);
        assertThrows(CancellationException.class, () -> flow.poll(token));
        assertTrue(saved.isEmpty());
        assertEquals(LoginState.IDLE, state.phase());
    }

    @Test void missingQrSignatureDoesNotStartPollingOrSave() {
        var flow = new QqAuthorizationFlow(request -> response(request, 200, Map.of(), "image"), state, Clock.systemUTC(), saved::add);
        var error = assertThrows(QqAuthorizationFlow.Failure.class, () -> flow.qr(state.reset()));
        assertEquals(LoginError.QR_COOKIE_MISSING, error.type);
        assertTrue(saved.isEmpty());
    }

    @Test void unsuccessfulOAuthCannotUseCodeFromErrorBody() throws Exception {
        var flow = new QqAuthorizationFlow(request -> {
            var result = happy(request);
            return requests.size() == 4 ? response(request, 503, Map.of(), "code=must-not-use") : result;
        }, state, Clock.systemUTC(), saved::add);
        long token = state.reset();
        flow.qr(token);
        assertEquals(LoginError.OAUTH_REQUEST_FAILED, assertThrows(QqAuthorizationFlow.Failure.class, () -> flow.poll(token)).type);
        assertEquals(4, requests.size());
        assertTrue(saved.isEmpty());
    }

    @Test void callbackSupportsEscapedFieldsAndRejectsUntrustedTargets() {
        var reply = QqLoginReply.read("ptuiCB('0','0','https:\\x2f\\x2fssl.ptlogin2.qq.com/check_sig','0','It\\'s OK','')");
        assertEquals("https://ssl.ptlogin2.qq.com/check_sig", reply.verificationUrl());
        assertTrue(QqAuthorizationFlow.safeVerification(URI.create(reply.verificationUrl())));
        for (String target : List.of("//ssl.ptlogin2.qq.com/check_sig", "https://qq.com.evil.invalid/", "https://user@qq.com/", "file:///tmp/qq")) {
            assertFalse(QqAuthorizationFlow.safeVerification(URI.create(target)), target);
        }
    }

    @Test void expiredQrRemainsUncommittedAndStopsBeforeAuthorization() throws Exception {
        var flow = new QqAuthorizationFlow(request -> response(request, 200, Map.of(), "ptuiCB('65','0','','0','expired','')"), state, Clock.systemUTC(), saved::add);
        long token = state.reset();
        state.signature(token, "qr");
        assertEquals(LoginState.QR_EXPIRED, flow.poll(token));
        assertTrue(saved.isEmpty());
    }

    @Test void loginCredentialRequiresExplicitSuccessfulOperation() {
        assertNull(QqAuthorizationFlow.credential("{\"music.login.LoginServer.Login\":{\"data\":{\"musicid\":\"123\",\"musickey\":\"secret\"}}}", 1));
        assertNull(QqAuthorizationFlow.credential("{\"code\":1,\"music.login.LoginServer.Login\":{\"code\":0,\"data\":{\"musicid\":\"123\"}}}", 1));
    }

    @Test void loginSupportsStringMusicIdAndNestedUserPayloads() {
        for (String user : List.of("{\"str_musicid\":\"900012345\",\"music_key\":\"key-test\"}",
                "{\"userInfo\":{\"musicid\":\"900012345\",\"musickey\":\"key-test\"}}")) {
            QqCredential result=QqAuthorizationFlow.credential("{\"code\":0,\"music.login.LoginServer.Login\":{\"code\":0,\"data\":"+user+"}}",1);
            assertNotNull(result);assertEquals("900012345",result.account());assertTrue(result.isValid());
        }
    }

    @Test void verificationCombinesPollingCookiesWithConfirmationCookies() throws Exception {
        var flow=new QqAuthorizationFlow(request->{
            var result=happy(request);
            if(requests.size()==2)return response(request,200,Map.of("Set-Cookie",List.of("pt_oauth_token=poll-secret; Path=/")),result.text());
            if(requests.size()==3)return response(request,302,Map.of("Set-Cookie",List.of("p_skey=session-secret; Path=/","p_uin=o12345; Path=/")),"");
            return result;
        },state,Clock.systemUTC(),saved::add);
        long token=state.reset();flow.qr(token);assertEquals(LoginState.SUCCESS,flow.poll(token));
        assertEquals(1,saved.size());assertTrue(requests.get(3).headers().get("Cookie").contains("pt_oauth_token=poll-secret"));
    }

    @Test void verificationFollowsBoundedTrustedRedirectWhenCookiesArriveLater() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        var flow=new QqAuthorizationFlow(request->{
            return switch(count.incrementAndGet()){
                case 1->response(request,200,Map.of("Set-Cookie",List.of("qrsig=test; Path=/")),"image");
                case 2->response(request,200,Map.of(),"ptuiCB('0','0','https://ssl.ptlogin2.qq.com/check_sig','0','','')");
                case 3->response(request,302,Map.of("Location",List.of("https://graph.qq.com/login_jump"),"Set-Cookie",List.of("pt2gguin=o12345; Domain=.qq.com; Path=/","pt_oauth_token=oauth; Domain=.qq.com; Path=/")),"");
                case 4->{assertEquals("graph.qq.com",request.uri().getHost());assertFalse(request.redirects());yield response(request,200,Map.of("Set-Cookie",List.of("p_skey=session; Path=/")),"");}
                case 5->response(request,302,Map.of("Location",List.of("https://y.qq.com/callback?code=test")),"");
                case 6->response(request,200,Map.of(),"{\"code\":0,\"music.login.LoginServer.Login\":{\"code\":0,\"data\":{\"str_musicid\":\"12345\",\"musickey\":\"secret\"}}}");
                default->throw new AssertionError("Extra network request");
            };
        },state,Clock.systemUTC(),saved::add);
        long token=state.reset();flow.qr(token);assertEquals(LoginState.SUCCESS,flow.poll(token));assertEquals(6,count.get());assertEquals(1,saved.size());
    }

    @Test void verificationNeverFollowsForeignRedirect() throws Exception {
        var flow=new QqAuthorizationFlow(request->{var result=happy(request);return requests.size()==3?
                response(request,302,Map.of("Location",List.of("https://evil.example/callback")),""):result;},state,Clock.systemUTC(),saved::add);
        long token=state.reset();flow.qr(token);
        assertEquals(LoginError.INVALID_CALLBACK,assertThrows(QqAuthorizationFlow.Failure.class,()->flow.poll(token)).type);
        assertEquals(3,requests.size());assertTrue(saved.isEmpty());
    }

    @Test void failureRetainsSafeStageAndResponseCodes() {
        long token=state.reset();state.phase(token,LoginState.LOGGING_IN);state.note(token,"MUSIC",200,120);state.serviceCodes(token,0,4001);
        state.fail(token,LoginError.MUSIC_LOGIN_FAILED);
        assertTrue(state.diagnostics().contains("LOGGING_IN"));assertTrue(state.diagnostics().contains("HTTP 200"));assertTrue(state.diagnostics().contains("operation=4001"));
    }
}
