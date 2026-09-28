package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.client.audio.NetMusicAudioStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class ProviderLiveChainTest {
    @Test
    void netEaseAnonymousSearchAndMetadataWorkWithoutCookie() throws Exception {
        requireLive();
        NetEaseApi api = new NetEaseApi();
        api.setCookie("");
        List<NetEaseSearchResult> results = NetEaseApi.parseSearchResults(api.search("晴天", 1, 3));
        assertFalse(results.isEmpty());
        NetEaseSearchResult row = results.get(0);
        SongInfo detail = api.searchDetails(new long[]{Long.parseLong(row.getSongId())})
                .get(Long.parseLong(row.getSongId()));
        assertNotNull(detail);
        assertFalse(detail.songName.isBlank());
    }

    @Test
    void qqAnonymousSearchMetadataAndLyricChainWorks() throws Exception {
        requireLive();
        List<QqSearchResult> results = QqMusicUtils.search("晴天");
        assertFalse(results.isEmpty());
        QqSearchResult row = results.get(0);
        assertFalse(row.getTitle().isBlank());
        assertTrue(row.getCoverUrl().isBlank() || row.getCoverUrl().startsWith("https://"));
        assertFalse(QqMusicUtils.getLyric(row.getId()).isBlank());
    }

    @Test
    void applePreviewRedirectReturnsAudioPayload() throws Exception {
        requireLive();
        List<NetEaseSearchResult> results = AppleMusicApi.search("Taylor Swift");
        assertFalse(results.isEmpty());
        SongInfo song = AppleMusicApi.toSong(results.get(0));
        assertNotNull(song);
        assertTrue(AppleMusicApi.isSafePreviewUrl(song.songUrl));
        HttpURLConnection connection = (HttpURLConnection) URI.create(song.songUrl).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MengSamaNetMusic-live-test");
        connection.setRequestProperty("Accept", "audio/mp4,audio/aac,audio/*");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        assertTrue(connection.getResponseCode() >= 200 && connection.getResponseCode() < 300);
        String type = connection.getContentType();
        assertTrue(type == null || type.startsWith("audio/") || type.contains("octet-stream"), String.valueOf(type));
        try (var body = connection.getInputStream()) { assertTrue(body.readNBytes(16).length > 8); }
        connection.disconnect();
    }

    @Test
    void netEaseQqAndAppleResponsesDecodeToPcm() throws Exception {
        requireLive();

        NetEaseApi netEase = new NetEaseApi();
        netEase.setCookie("");
        com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.NET_EASE_API = netEase;
        NetEaseSearchResult netEaseRow = NetEaseApi.parseSearchResults(netEase.search("晴天", 1, 10)).stream()
                .filter(row -> !row.isVip()).findFirst().orElseThrow();
        assertDecodesToPcm("NetEase", netEase.get163Song(netEaseRow).songUrl);

        SongInfo qqSong = resolveFirstAnonymousQqSong(QqMusicUtils.search("晴天"));
        assertDecodesToPcm("QQ", qqSong.songUrl);

        NetEaseSearchResult appleRow = AppleMusicApi.search("Taylor Swift").stream().findFirst().orElseThrow();
        assertDecodesToPcm("Apple", AppleMusicApi.toSong(appleRow).songUrl);
    }

    private static SongInfo resolveFirstAnonymousQqSong(List<QqSearchResult> rows) throws Exception {
        Exception last = null;
        for (QqSearchResult row : rows) {
            if (row.isVip()) continue;
            try {
                return QqMusicUtils.resolveSong(row.getId(), "", 0);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("QQ search returned no anonymously playable row", last);
    }

    private static void assertDecodesToPcm(String provider, String url) throws Exception {
        assertNotNull(url, provider + " returned no URL");
        try (NetMusicAudioStream stream = new NetMusicAudioStream(URI.create(url).toURL())) {
            AudioFormat format = stream.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), provider + " did not decode to PCM");
            assertTrue(format.getChannels() == 1 || format.getChannels() == 2,
                    provider + " returned unsupported channel count " + format.getChannels());
            var pcm = stream.read(Math.max(4096, format.getFrameSize() * 1024));
            assertNotNull(pcm, provider + " produced no PCM buffer");
            assertTrue(pcm.remaining() >= format.getFrameSize(), provider + " PCM buffer is empty");
        }
    }

    private static void requireLive() {
        Assumptions.assumeTrue(Boolean.getBoolean("mengsama.liveTests"),
                "Set -Dmengsama.liveTests=true to execute external provider chains");
    }
}
