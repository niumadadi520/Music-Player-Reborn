package com.mengsama.mod.mengsamanetmusic.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import com.mengsama.mod.mengsamanetmusic.compat.LegacyLyricArchive;
import static org.junit.jupiter.api.Assertions.*;

class LegacyLyricCompatibilityTest {
    @Test void roundTripPreservesUnknownArchiveMetadata() {
        String source = "{\"lyric\":{\"1.0\":\"line\"},\"future\":{\"notes\":[1,2,3]}}";
        var archive = LegacyLyricArchive.fromJson(source);
        assertEquals("line", archive.at(1).primary());
        assertEquals("{\"notes\":[1,2,3]}", com.google.gson.JsonParser.parseString(archive.toJson())
                .getAsJsonObject().get("future").toString());
    }
    @Test void doesNotDisplayAFutureOrLastLineBeforeMusicStarts() {
        var lyrics = new LegacyLyricArchive(Map.of(3F,"first",8F,"second"),Map.of(3F,"translation"));
        assertEquals("",lyrics.at(0).primary());
        assertEquals("first",lyrics.at(3).primary());
        assertEquals("translation",lyrics.at(7.9F).translated());
        assertEquals("second",lyrics.at(8).primary());
        assertNull(lyrics.at(8).translated());
        assertEquals("",lyrics.at(Float.NaN).primary());
    }
    @Test void existingCacheJsonRemainsReadableAndKeepsTranslatedTiming() {
        var loaded = LegacyLyricArchive.fromJson("{\"lyric\":{\"8.0\":\"second\",\"3.0\":\"first\"},\"transform_lyric\":{\"3.0\":\"译文\"}}");
        var restored = LegacyLyricArchive.fromJson(loaded.toJson());
        assertEquals("first",restored.at(5).primary());
        assertEquals("译文",restored.at(5).translated());
    }
    @Test void songIdParserHandlesFragmentLinksAndRejectsUnrelatedParameters() throws Exception {
        assertEquals(42,SongAddress.requireId("https://music.163.com/#/song?id=42"));
        assertEquals(42,SongAddress.requireId("https://music.163.com/song/media/outer/url?id=42.mp3"));
        assertThrows(IllegalArgumentException.class,()->SongAddress.requireId("https://music.163.com/?userid=42"));
        assertThrows(IllegalArgumentException.class,()->SongAddress.requireId("https://music.163.com/?id=-1"));
    }
}
