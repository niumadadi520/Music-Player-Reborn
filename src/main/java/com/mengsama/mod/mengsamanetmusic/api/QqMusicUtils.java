package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import java.io.IOException;
import java.util.*;

 


public final class QqMusicUtils {

    static final QqCatalog CATALOG = new QqCatalog(QqHttp.LIVE, VipCookieState::getEffectiveVipCookie);

    private static final QqPlayback PLAYBACK = new QqPlayback(CATALOG);

    private static final QqLyrics LYRICS = new QqLyrics(QqHttp.LIVE);

    private QqMusicUtils() {
    }

    public static List<QqSearchResult> search(String query) throws IOException {
        return CATALOG.search(query);
    }



    public static SongInfo resolveSong(String mid, String cookie, int quality) throws IOException {
        return PLAYBACK.resolve(mid, cookie, quality);
    }

    public static String getLyric(String mid) throws IOException {
        return LYRICS.fetch(mid, VipCookieState.getEffectiveVipCookie());
    }









    public static List<String> buildAlbumCoverUrls(String mid) {
        return QqProtocol.artwork(mid);
    }

    public static String buildAlbumCoverUrl(String mid, String legacyPmId) {
        return buildAlbumCoverUrls(mid).stream().findFirst().orElse("");
    }

    public static Map<String, String> playbackHeaders(String cookie) {
        return QqProtocol.headers(cookie, false);
    }
}
