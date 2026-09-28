package com.mengsama.mod.mengsamanetmusic.api;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SongStorageIsolationTest {
    @Test void copyingPreservesEveryPublicValueAndSeparatesMutablePlaybackState() throws Exception {
        SongInfo original = new SongInfo("stable", "title", 219, true);
        for (var field : SongInfo.class.getFields()) {
            if (field.getType() == String.class) field.set(original, "test:" + field.getName());
            if (field.getType() == long.class) field.setLong(original, 9876543210L);
            if (field.getType() == boolean.class) field.setBoolean(original, true);
        }
        original.artists.add("singer"); original.playbackHeaders.put("Referer", "origin");
        SongInfo copy = original.clone();
        for (var field : SongInfo.class.getFields()) assertEquals(field.get(original), field.get(copy), field.getName());
        copy.artists.add("second"); copy.playbackHeaders.put("Cookie", "runtime");
        assertEquals(List.of("singer"), original.artists);
        assertEquals(Map.of("Referer", "origin"), original.playbackHeaders);
        original.artists = null; original.playbackHeaders = null;
        assertTrue(original.clone().artists.isEmpty()); assertTrue(original.clone().playbackHeaders.isEmpty());
    }
    @Test void storageRetainsExtensionTagsAndExcludesTemporaryAuthorization() {
        SongInfo song = new SongInfo("https://y.qq.com/n/ryqq/songDetail/StableMid", "saved", 120);
        song.source = "qq"; song.providerId = "StableMid";
        song.resolvedMediaUrl = "https://audio.invalid/play?vkey=private";
        song.playbackHeaders.put("Cookie", "private-session");
        song.artists.add("singer"); song.readOnly = true;
        CompoundTag target = new CompoundTag(); target.putString("other_mod_extension", "keep");
        SongInfo.serializeNBT(song, target);
        assertEquals("keep", target.getString("other_mod_extension"));
        assertFalse(target.toString().contains("private"));
        assertEquals("saved", target.getString("name")); assertEquals(120,target.getInt("time"));
        assertTrue(target.getBoolean("read_only"));
        SongInfo loaded = SongInfo.deserializeNBT(target);
        assertEquals("qq:StableMid", loaded.identityKey());
        assertEquals(song.artists, loaded.artists);
        assertTrue(loaded.playbackHeaders.isEmpty()); assertEquals("",loaded.resolvedMediaUrl);
    }
    @Test void oldKeyPriorityAndMixedNumberTypesRemainCompatibleWithoutRewritingInput() {
        CompoundTag tag = new CompoundTag(); tag.putString("url","  "); tag.putString("SongURL","https://example.test/song");
        tag.putString("Name","old"); tag.putString("songTime","205000"); tag.putBoolean("readOnly",true);
        tag.putInt("artistCount",Integer.MAX_VALUE); tag.putString("artist_8","B"); tag.putString("artist_2","A");
        tag.putString("artist_02","ignore"); tag.putString("artist_-1","ignore");
        CompoundTag saved = tag.copy(); SongInfo loaded = SongInfo.deserializeNBT(tag);
        assertEquals(205,loaded.songTime); assertEquals("old",loaded.songName); assertTrue(loaded.readOnly);
        assertEquals(List.of("A","B"),loaded.artists); assertEquals(saved,tag);
    }
    @Test void programDecoderUsesTheSharedTrackRulesAndToleratesMissingOrWrongShapes() {
        for (String response : List.of("null","[]","{bad","{}","{\"program\":null}","{\"program\":false}","{\"program\":{\"mainSong\":[]}}"))
            assertFalse(NetEaseTrackDecoder.program(response).isValid());
        var song = NetEaseTrackDecoder.program("{\"program\":{\"mainSong\":{\"id\":42,\"name\":\"radio\",\"duration\":93000,\"artists\":[{\"name\":\"host\"}]}}}");
        assertEquals("netease:42",song.identityKey()); assertEquals(93,song.songTime); assertEquals(List.of("host"),song.artists);
    }
}
