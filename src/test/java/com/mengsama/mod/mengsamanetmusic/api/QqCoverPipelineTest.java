package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QqCoverPipelineTest {
    @Test
    void officialAlbumCoverUsesHttpsAndAtLeast300Pixels() {
        List<String> urls = QqMusicUtils.buildAlbumCoverUrls("004TestAlbumMid");
        assertFalse(urls.isEmpty());
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000004TestAlbumMid.jpg", urls.get(0));
        assertTrue(urls.stream().allMatch(url -> url.startsWith("https://")));
        assertTrue(urls.get(0).contains("R300x300"));
    }

    @Test
    void missingAlbumMidNeverBuildsSongMidOrPmIdCover() {
        assertTrue(QqMusicUtils.buildAlbumCoverUrls("").isEmpty());
        assertEquals("", QqMusicUtils.buildAlbumCoverUrl("", "123456"));
    }

    @Test
    void searchResultCarriesAlbumMidAndOfficialCover() {
        String response = """
                {"code":0,"req":{"code":0,"data":{"body":{"song":{"list":[{
                  "mid":"001SongMid","name":"Song","interval":180,
                  "album":{"mid":"002AlbumMid","pmId":"999"},
                  "singer":[{"name":"Singer"}],"pay":{"pay_play":0}
                }]}}}}}
                """;
        List<QqSearchResult> results = com.mengsama.mod.mengsamanetmusic.api.qq.QqCatalog.searchResponse(response, "req");
        assertEquals(1, results.size());
        assertEquals("002AlbumMid", results.get(0).getAlbumMid());
        assertEquals(QqMusicUtils.buildAlbumCoverUrl("002AlbumMid", "999"), results.get(0).getCoverUrl());
        assertFalse(results.get(0).getCoverUrl().contains("001SongMid"));
    }

    @Test
    void songInfoNormalizesQqAlbumCoverWithoutChangingNeteaseArtwork() {
        SongInfo qq = new SongInfo("001SongMid", "QQ Song", 180);
        qq.source = "qq";
        qq.providerId = "001SongMid";
        qq.albumMid = "002AlbumMid";
        qq.normalizeIdentity();
        assertEquals(QqMusicUtils.buildAlbumCoverUrl("002AlbumMid", ""), qq.coverUrl);
        assertEquals(qq.coverUrl, qq.picUrl);

        SongInfo netease = new SongInfo("https://music.163.com/song/media/outer/url?id=123.mp3", "NE Song", 180);
        netease.source = "netease";
        netease.picUrl = "https://p1.music.126.net/cover.jpg";
        netease.normalizeIdentity();
        assertEquals("https://p1.music.126.net/cover.jpg", netease.picUrl);
        assertEquals("https://p1.music.126.net/cover.jpg", netease.coverUrl);
        assertEquals("https://p1.music.126.net/cover.jpg?param=128y128", netease.preferredCoverUrl());
        assertEquals(123L, netease.songId);
    }
}
