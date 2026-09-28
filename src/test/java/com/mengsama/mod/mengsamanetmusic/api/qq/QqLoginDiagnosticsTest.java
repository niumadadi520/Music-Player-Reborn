package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.mengsama.mod.mengsamanetmusic.api.QqLoginState;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QqLoginDiagnosticsTest {
    @Test void classifiesMissingKeyAndLoginRedirectWithoutExposingSecrets() {
        var response = new QqHttp.Response(URI.create("https://graph.qq.com/oauth2.0/authorize?code=private-code"),
                302, Map.of("Location", List.of("https://graph.qq.com/oauth2.0/show?uin=123456&token=private-token"),
                "Set-Cookie", List.of("p_skey=private-key; Domain=.graph.qq.com; Path=/", "p_skey=; Max-Age=0; Domain=.qq.com")), new byte[0]);
        String trace = QqLoginDiagnostics.response(response);
        assertTrue(trace.contains("GRAPH_LOGIN_PAGE"));
        assertTrue(trace.contains("key_headers=2"));
        assertTrue(trace.contains("expired=1"));
        assertTrue(trace.contains("empty=1"));
        assertFalse(trace.contains("private")); assertFalse(trace.contains("123456"));
        assertFalse(trace.contains("https://"));
    }

    @Test void verificationEvidenceSurvivesLaterAuthorizationRequestAndResetClearsIt() {
        var state = new QqLoginState(); long token = state.reset();
        state.note(token, "VERIFY", 302, 0);
        state.cookiePresence(token, Map.of("pt2gguin", "private-account", "pt_oauth_token", "private-token"));
        state.note(token, "OAUTH", 0, 0); state.note(token, "OAUTH", 302, 0);
        assertTrue(state.diagnostics().contains("p_skey=false"));
        assertTrue(state.diagnostics().contains("VERIFY"));
        assertTrue(state.diagnostics().contains("OAUTH"));
        assertFalse(state.diagnostics().contains("private"));
        state.reset(); assertFalse(state.diagnostics().contains("VERIFY"));
    }

    @Test void repeatedPollingCannotGrowTraceWithoutBound() {
        var state = new QqLoginState(); long token = state.reset();
        for (int i = 0; i < 2000; i++) state.note(token, "POLL", 200, 90);
        assertTrue(state.diagnostics().length() <= 4096);
    }
}
