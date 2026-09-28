package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseApi;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAudioHeadersTest {
    @Test
    void qqExplicitHeadersAreNotOverwrittenByNetEaseDefaults() throws Exception {
        MengSamaNetMusic.NET_EASE_API = new NetEaseApi();
        MengSamaNetMusic.NET_EASE_API.setCookie("netease-cookie");
        Map<String, String> headers = NetMusicAudioStream.requestHeaders(
                new URL("https://dl.stream.qqmusic.qq.com/test.mp3"),
                Map.of("Referer", "https://y.qq.com/", "Cookie", "qq-cookie"));

        assertEquals("https://y.qq.com/", headers.get("Referer"));
        assertEquals("qq-cookie", headers.get("Cookie"));
        assertFalse(headers.containsValue("netease-cookie"));
    }

    @Test
    void netEaseCdnDoesNotReceiveAccountCookie() throws Exception {
        MengSamaNetMusic.NET_EASE_API = new NetEaseApi();
        MengSamaNetMusic.NET_EASE_API.setCookie("MUSIC_U=test");
        Map<String, String> headers = NetMusicAudioStream.requestHeaders(
                new URL("https://m801.music.126.net/test.mp3"), Map.of());

        assertTrue(headers.getOrDefault("Referer", "").contains("music.163.com"));
        assertFalse(headers.containsKey("Cookie"));
    }

    @Test
    void disguisedHostsAndCrossOriginRedirectsDoNotReceiveCredentials() throws Exception {
        MengSamaNetMusic.NET_EASE_API = new NetEaseApi();
        MengSamaNetMusic.NET_EASE_API.setCookie("MUSIC_U=test");
        for (String host : new String[]{"music.163.com.attacker.invalid", "netease.attacker.invalid", "evilqq.com"}) {
            var headers = NetMusicAudioStream.requestHeaders(new URL("https://" + host + "/x"),
                    Map.of("cOoKiE", "secret", "Authorization", "secret", "Referer", "https://y.qq.com/"));
            assertTrue(headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Cookie") || k.equalsIgnoreCase("Authorization")));
            assertEquals("https://y.qq.com/", headers.get("Referer"));
        }
        URL origin = new URL("https://music.163.com/audio");
        var initial = NetMusicAudioStream.requestHeaders(origin, Map.of());
        assertEquals("MUSIC_U=test", initial.get("Cookie"));
        var redirected = com.mengsama.mod.mengsamanetmusic.util.AudioRequestHeaders.sanitize(origin,
                new URL("https://example.invalid/audio"), initial);
        assertFalse(redirected.containsKey("Cookie"));
        assertFalse(com.mengsama.mod.mengsamanetmusic.util.AudioRequestHeaders.sanitize(origin,
                new URL("http://music.163.com/audio"), initial).containsKey("Cookie"));
    }
}
