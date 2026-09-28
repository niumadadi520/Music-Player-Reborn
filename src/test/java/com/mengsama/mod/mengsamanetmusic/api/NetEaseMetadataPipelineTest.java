package com.mengsama.mod.mengsamanetmusic.api;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetEaseMetadataPipelineTest {
    @Test
    void parsesCurrentSearchShapeWithAllMetadataAndPlaybackId() {
        String json = """
                {"result":{"songs":[{"id":19723756,"name":"Current Song","fee":1,"dt":245678,
                "ar":[{"name":"Artist A"},{"name":"Artist B"}],
                "al":{"name":"Current Album","picUrl":"https://p1.music.126.net/current.jpg"}}]}}
                """;
        NetEaseSearchResult result = only(NetEaseApi.parseSearchResults(json));
        assertEquals("19723756", result.getSongId());
        assertEquals("Current Song", result.getSongName());
        assertEquals("Artist A / Artist B", result.getArtistName());
        assertEquals("Current Album", result.getAlbumName());
        assertEquals("https://p1.music.126.net/current.jpg", result.getCoverUrl());
        assertEquals(245, result.getDuration());
        assertTrue(result.isVip());
    }

    @Test
    void normalizesProtocolEntitiesWhitespaceAndPreservesSignedParameters() {
        String json = """
                {"result":{"songs":[
                  {"id":1,"name":"Relative","al":{"picUrl":"  //p3.music.126.net/a.jpg?token=x&amp;y=2  "}},
                  {"id":2,"name":"Legacy","album":{"picUrl":"http://p4.music.126.net/b.jpg"}},
                  {"id":3,"name":"Fallback","picUrl":"https://p5.music.126.net/c.jpg"}
                ]}}
                """;
        List<NetEaseSearchResult> results = NetEaseApi.parseSearchResults(json);
        assertEquals("https://p3.music.126.net/a.jpg?token=x&y=2", results.get(0).getCoverUrl());
        assertEquals("https://p4.music.126.net/b.jpg", results.get(1).getCoverUrl());
        assertEquals("https://p5.music.126.net/c.jpg", results.get(2).getCoverUrl());
        assertEquals("https://p3.music.126.net/a.jpg?token=x&y=2", CoverUrlUtil.forDisplay(results.get(0).getCoverUrl()));
        assertEquals("https://p4.music.126.net/b.jpg?param=128y128", CoverUrlUtil.forDisplay(results.get(1).getCoverUrl()));
    }

    @Test
    void oldEmptyNbtCanReceiveDetailCoverAndPersistIt() {
        CompoundTag old = legacyTag(334455L, 205000);
        old.remove("PicUrl");
        SongInfo info = SongInfo.deserializeNBT(old);
        assertEquals("", info.preferredCoverUrl());
        info.picUrl = "http://p1.music.126.net/refilled.jpg";
        info.normalizeIdentity();
        CompoundTag updated = new CompoundTag();
        SongInfo.serializeNBT(info, updated);
        assertEquals("https://p1.music.126.net/refilled.jpg?param=128y128", SongInfo.deserializeNBT(updated).preferredCoverUrl());
    }

    @Test
    void parsesLegacySearchShapeAndMilliseconds() {
        String json = """
                {"result":{"songs":[{"id":424242,"name":"Legacy Song","duration":183000,
                "artists":[{"name":"Old A"},{"name":"Old B"}],
                "album":{"name":"Legacy Album","picUrl":"https://p2.music.126.net/legacy.jpg"}}]}}
                """;
        NetEaseSearchResult result = only(NetEaseApi.parseSearchResults(json));
        assertEquals("424242", result.getSongId());
        assertEquals("Legacy Song", result.getSongName());
        assertEquals("Old A / Old B", result.getArtistName());
        assertEquals("Legacy Album", result.getAlbumName());
        assertEquals("https://p2.music.126.net/legacy.jpg", result.getCoverUrl());
        assertEquals(183, result.getDuration());
    }

    @Test
    void currentNbtRoundTripKeepsRendererAndPlaybackFields() {
        SongInfo input = new SongInfo("https://music.163.com/song/media/outer/url?id=9988.mp3", "NBT Song", 211);
        input.source = "netease";
        input.songId = 9988;
        input.providerId = "9988";
        input.artists = List.of("Singer One", "Singer Two");
        input.albumName = "NBT Album";
        input.picUrl = "https://p1.music.126.net/nbt.jpg";
        input.coverUrl = input.picUrl;
        CompoundTag tag = new CompoundTag();
        SongInfo.serializeNBT(input, tag);
        SongInfo output = SongInfo.deserializeNBT(tag);
        assertEquals("NBT Song", output.songName);
        assertEquals(List.of("Singer One", "Singer Two"), output.artists);
        assertEquals("NBT Album", output.albumName);
        assertEquals(input.picUrl + "?param=128y128", output.preferredCoverUrl());
        assertEquals(211, output.songTime);
        assertEquals(9988, output.songId);
        assertEquals("netease:9988", output.identityKey());
    }

    @Test
    void legacyNbtSupportsCapitalizedKeysAndSecondOrMillisecondSongTime() {
        CompoundTag millis = legacyTag(334455L, 205000);
        SongInfo fromMillis = SongInfo.deserializeNBT(millis);
        assertEquals("Legacy NBT Song", fromMillis.songName);
        assertEquals(List.of("Old Singer", "Guest"), fromMillis.artists);
        assertEquals("Legacy NBT Album", fromMillis.albumName);
        assertEquals("https://p1.music.126.net/old-nbt.jpg?param=128y128", fromMillis.preferredCoverUrl());
        assertEquals(205, fromMillis.songTime);
        assertEquals(334455L, fromMillis.songId);
        assertEquals("netease:334455", fromMillis.identityKey());

        millis.putInt("SongTime", 205);
        assertEquals(205, SongInfo.deserializeNBT(millis).songTime);
    }

    private static CompoundTag legacyTag(long id, int songTime) {
        CompoundTag tag = new CompoundTag();
        tag.putString("SongUrl", "https://music.163.com/song/media/outer/url?id=" + id + ".mp3");
        tag.putString("Name", "Legacy NBT Song");
        tag.putString("Artists", "Old Singer / Guest");
        tag.putString("PicUrl", "https://p1.music.126.net/old-nbt.jpg");
        tag.putString("AlbumName", "Legacy NBT Album");
        tag.putInt("SongTime", songTime);
        tag.putLong("SongId", id);
        return tag;
    }

    private static NetEaseSearchResult only(List<NetEaseSearchResult> results) {
        assertEquals(1, results.size());
        return results.get(0);
    }
}
