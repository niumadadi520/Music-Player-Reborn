package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class QqProviderCompatibilityTest {
    private static QqCredential decodeCredential(String json) {
        return com.mengsama.mod.mengsamanetmusic.api.qq.QqAuthorizationFlow.credential(json, java.time.Instant.now().getEpochSecond());
    }
    @Test
    void parsesSupportedPublicLinksWithoutFollowingNormalHosts() {
        ParsedUrl song = QqUrlParser.parse("https://y.qq.com/n/ryqq/songDetail/001AbCdEfG12", uri -> {
            fail("normal y.qq.com links must not invoke the redirect resolver");
            return uri;
        });
        ParsedUrl album = QqUrlParser.parse("https://y.qq.com/n/ryqq/albumDetail/002AlbumMid");
        ParsedUrl playlist = QqUrlParser.parse("https://i.y.qq.com/n2/m/share/details/taoge.html?id=123456");

        assertNotNull(song);
        assertEquals(ParsedUrl.ResourceType.SONG, song.getType());
        assertEquals("001AbCdEfG12", song.getId());
        assertEquals("song:001AbCdEfG12", song.toString());
        assertEquals(ParsedUrl.ResourceType.ALBUM, album.getType());
        assertEquals("002AlbumMid", album.getId());
        assertEquals(ParsedUrl.ResourceType.PLAYLIST, playlist.getType());
        assertEquals("123456", playlist.getId());
    }

    @Test
    void resolvesOnlyKnownShortHostsAndRejectsMalformedInput() {
        ParsedUrl redirected = QqUrlParser.parse("https://c.y.qq.com/example", ignored ->
                URI.create("https://y.qq.com/n/ryqq/songDetail/003Redirected"));
        assertNotNull(redirected);
        assertEquals("003Redirected", redirected.getId());

        assertNull(QqUrlParser.parse("javascript:alert(1)"));
        assertNull(QqUrlParser.parse("https://example.com/playlist/not-qq"));
        assertNull(QqUrlParser.parse(""));
    }

    @Test
    void credentialValidityIncludesExpiryAndProducesProviderCookie() {
        long issuedAt = Instant.now().getEpochSecond();
        QqCredential active = new QqCredential(" 12345 ", " key ", 60, issuedAt, "", "");
        assertFalse(active.isExpiredAt(Instant.ofEpochSecond(issuedAt + 59)));
        assertTrue(active.isExpiredAt(Instant.ofEpochSecond(issuedAt + 60)));
        assertEquals("12345", active.getMusicId());
        assertEquals("uin=12345; qm_keyst=key", active.toCookieString());

        QqCredential incomplete = new QqCredential("12345", " ", 0, 0, "", "");
        assertFalse(incomplete.isValid());
        assertEquals("", incomplete.toCookieString());
    }

    @Test
    void searchRowsNormalizeNullsAndDoNotRenderDanglingSeparator() {
        QqSearchResult titleOnly = new QqSearchResult(" id ", " Song ", null, false);
        assertEquals("id", titleOnly.getId());
        assertEquals("Song", titleOnly.getTitle());
        assertEquals("Song", titleOnly.getDisplayText());

        QqSearchResult empty = new QqSearchResult(null, null, null, true);
        assertEquals("空", empty.getDisplayText());
        assertTrue(empty.isVip());
    }

    @Test
    void loginProtocolHelpersRemainDeterministic() {
        assertEquals(0L, com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.token("", 0));
        assertEquals(5381L, com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.token("", 5381));
        assertEquals(com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.token("signature", 0),
                com.mengsama.mod.mengsamanetmusic.api.qq.QqLoginReply.token("signature", 0));

        QqCredential decoded = decodeCredential("""
                {"music.login.LoginServer.Login":{"code":0,"data":{
                  "musicid":"10001","musickey":"token","keyExpiresIn":3600,
                  "musickeyCreateTime":2000,"refresh_key":"rk","refresh_token":"rt"
                }}}
                """);
        assertNotNull(decoded);
        assertEquals("10001", decoded.getMusicId());
        assertNull(decodeCredential("{bad json"));
    }
}
