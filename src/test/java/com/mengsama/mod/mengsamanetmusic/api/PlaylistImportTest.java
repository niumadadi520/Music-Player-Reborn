package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistImportTest {
    private static SongInfo song(long id) {
        var result = new SongInfo("https://music.163.com/song/media/outer/url?id=" + id + ".mp3", "Song " + id, 123);
        result.songId = id; result.providerId = Long.toString(id); result.source = "netease";
        return result;
    }
    @Test void fillsHolesByIdentityAndPreservesPlaylistOrderRegardlessOfResponseOrder() throws Exception {
        String input = """
                {"playlist":{"trackIds":[{"id":3},{"id":1},{"id":2},{"id":3}],
                "tracks":[{"id":2,"name":"Existing","dt":100000}]}}
                """;
        var result = PlaylistImport.load(input, ids -> {
            assertArrayEquals(new long[]{3,1}, ids);
            return Map.of(1L, song(1), 3L, song(3), 999L, song(999));
        });
        assertEquals(List.of(3L,1L,2L), result.stream().map(s -> s.songId).toList());
        assertEquals("Existing", result.get(2).songName);
    }
    @Test void fetchesOnlyBoundedBatchesAndLimitsTheImport() throws Exception {
        JsonArray ids = new JsonArray();
        for (int i = 1; i <= 1100; i++) ids.add(i);
        List<Integer> sizes = new ArrayList<>();
        var result = PlaylistImport.load("{\"playlist\":{\"trackIds\":" + ids + "}}", batch -> {
            sizes.add(batch.length);
            Map<Long, SongInfo> data = new HashMap<>();
            for (long id : batch) data.put(id, song(id));
            return data;
        });
        assertEquals(1000, result.size()); assertEquals(Collections.nCopies(10,100), sizes);
        assertEquals(1000, result.get(999).songId);
    }
    @Test void missingOrFailedDetailsNeverReturnASilentlyTruncatedPlaylist() {
        String input = "{\"playlist\":{\"trackIds\":[1,2]}}";
        assertThrows(IOException.class, () -> PlaylistImport.load(input, batch -> Map.of(1L,song(1))));
        assertThrows(IOException.class, () -> PlaylistImport.load(input, batch -> { throw new IOException("unavailable"); }));
        assertThrows(IOException.class, () -> PlaylistImport.load("{\"code\":403}", batch -> Map.of()));
    }
    @Test void supportsLegacyFieldsWithoutDetailRequestsAndKeepsUnicodeMetadata() throws Exception {
        var result = PlaylistImport.load("""
                {"result":{"tracks":[{"id":7,"name":"认真的雪","duration":123456,"fee":1,
                "artists":[{"name":"薛之谦"}],"album":{"name":"专辑","picUrl":"https://example.org/cover.png"},
                "transNames":["Snow"]}]}}
                """, batch -> { fail("Already complete"); return Map.of(); });
        SongInfo song = result.get(0);
        assertEquals("认真的雪", song.songName); assertEquals(123, song.songTime);
        assertEquals(List.of("薛之谦"), song.artists); assertEquals("专辑", song.albumName);
        assertEquals("Snow", song.transName); assertTrue(song.vip);
    }
    @Test void returnedSongsAreIsolatedFromTheResolverCache() throws Exception {
        SongInfo original = song(1);
        var imported = PlaylistImport.load("{\"playlist\":{\"trackIds\":[1]}}", ids -> Map.of(1L,original));
        imported.get(0).songName = "changed";
        assertEquals("Song 1", original.songName);
    }
}
