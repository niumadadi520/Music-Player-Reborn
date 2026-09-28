package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NetEaseSearchMetadataLoaderTest {
    private static final Executor DIRECT = Runnable::run;

    @BeforeEach
    void clearCache() {
        NetEaseSearchMetadataLoader.clearForTests();
    }

    @Test
    void parsesBatchDetailsAndMergesCoverIntoCorrespondingSearchRow() {
        String json = """
                {"songs":[
                  {"id":101,"name":"One","dt":181000,"ar":[{"name":"A"}],"al":{"name":"Album A","picUrl":"http://p1.music.126.net/a.jpg"}},
                  {"id":202,"name":"Two","dt":202000,"ar":[{"name":"B"}],"al":{"name":"Album B","picUrl":"https://p2.music.126.net/b.jpg"}}
                ]}
                """;
        Map<Long, SongInfo> details = NetEaseApi.parseSongDetails(json);
        NetEaseSearchResult row = row(202, "Two");
        NetEaseSearchResult merged = row.mergeDetail(details.get(202L));

        assertEquals("https://p2.music.126.net/b.jpg?param=128y128", merged.getCoverUrl());
        assertEquals("Album B", merged.getAlbumName());
        assertEquals(202, merged.getDuration());
        assertEquals("B", merged.getArtistName());
        assertEquals("", row.getCoverUrl(), "merge must replace the SearchList value rather than mutate unrelated storage");
    }

    @Test
    void hydratesAsynchronouslyPreservesOrderAndCachesBySongId() {
        AtomicInteger calls = new AtomicInteger();
        NetEaseSearchMetadataLoader.DetailFetcher fetcher = id -> {
            calls.incrementAndGet();
            return detail(id, "https://p1.music.126.net/" + id + ".jpg");
        };
        List<NetEaseSearchResult> rows = List.of(row(11, "First"), row(22, "Second"), row(11, "Duplicate"));

        List<NetEaseSearchResult> hydrated = NetEaseSearchMetadataLoader
                .hydrateMissing(rows, fetcher, DIRECT, 1_000L).join();
        assertEquals(List.of("11", "22", "11"), hydrated.stream().map(NetEaseSearchResult::getSongId).toList());
        assertTrue(hydrated.stream().allMatch(result -> !result.getCoverUrl().isBlank()));
        assertEquals(2, calls.get(), "duplicate songId must share one detail request");

        NetEaseSearchMetadataLoader.hydrateMissing(List.of(row(11, "Again")), fetcher, DIRECT, 1_001L).join();
        assertEquals(2, calls.get(), "successful details must be cached by songId");
    }

    @Test
    void failedDetailKeepsPlaceholderAndSuppressesImmediateRetry() {
        AtomicInteger calls = new AtomicInteger();
        NetEaseSearchMetadataLoader.DetailFetcher failing = id -> { calls.incrementAndGet(); return null; };

        NetEaseSearchResult first = NetEaseSearchMetadataLoader
                .hydrateMissing(List.of(row(33, "Missing")), failing, DIRECT, 5_000L).join().get(0);
        NetEaseSearchResult second = NetEaseSearchMetadataLoader
                .hydrateMissing(List.of(row(33, "Missing")), failing, DIRECT, 5_001L).join().get(0);

        assertEquals("", first.getCoverUrl());
        assertEquals("", second.getCoverUrl());
        assertEquals(1, calls.get());
    }

    @Test
    void generationRejectsOldQueryAndClosedScreenCallbacks() {
        SearchGeneration token = new SearchGeneration();
        long old = token.begin("netease:old");
        long current = token.begin("netease:new");
        assertFalse(token.isCurrent(old, "netease:old"));
        assertFalse(token.isCurrent(current, "netease:old"));
        assertTrue(token.isCurrent(current, "netease:new"));
        token.invalidate();
        assertFalse(token.isCurrent(current, "netease:new"));
    }

    @Test
    void qqAndExistingCoverRowsNeverRequestNetEaseDetail() {
        AtomicInteger calls = new AtomicInteger();
        NetEaseSearchMetadataLoader.DetailFetcher fetcher = id -> { calls.incrementAndGet(); return detail(id, "ignored"); };
        NetEaseSearchResult qq = new NetEaseSearchResult("qq-id", "QQ", "Singer", false,
                "qq", "album-mid", "https://y.qq.com/cover.jpg");
        NetEaseSearchResult complete = new NetEaseSearchResult("44", "Complete", "Singer", false,
                "netease", "", "https://p1.music.126.net/already.jpg");

        List<NetEaseSearchResult> output = NetEaseSearchMetadataLoader
                .hydrateMissing(List.of(qq, complete), fetcher, DIRECT, 10_000L).join();
        assertSame(qq, output.get(0));
        assertSame(complete, output.get(1));
        assertEquals(0, calls.get());
    }

    private static NetEaseSearchResult row(long id, String name) {
        return new NetEaseSearchResult(Long.toString(id), name, "", false,
                "netease", "", "", "", 0);
    }

    private static SongInfo detail(long id, String cover) {
        SongInfo info = new SongInfo("https://music.163.com/song/media/outer/url?id=" + id + ".mp3", "Detail", 180);
        info.source = "netease";
        info.songId = id;
        info.providerId = Long.toString(id);
        info.picUrl = cover;
        info.coverUrl = cover;
        info.albumName = "Detail Album";
        info.artists = List.of("Detail Artist");
        return info;
    }
}
