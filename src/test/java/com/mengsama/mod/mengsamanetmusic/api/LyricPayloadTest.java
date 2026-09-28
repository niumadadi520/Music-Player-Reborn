package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricPayloadTest {
    @Test void supportsNestedLyricsAndFlatLegacyLyrics() {
        assertEquals("[00:01]原文", LyricPayload.text(JsonParser.parseString("{\"lrc\":{\"lyric\":\"[00:01]原文\"}}").getAsJsonObject(), "lrc", "lyric"));
        assertEquals("[00:01]翻译", LyricPayload.text(JsonParser.parseString("{\"translation\":\"[00:01]翻译\"}").getAsJsonObject(), "tlyric", "lyric", "translation"));
        assertEquals("direct", LyricPayload.text(JsonParser.parseString("{\"lrc\":\"direct\",\"lyric\":\"fallback\"}").getAsJsonObject(), "lrc", "lyric"));
    }
    @Test void absentOrNullLyricIsEmptyAndDoesNotChangePayload() {
        var payload = JsonParser.parseString("{\"lrc\":null,\"extra\":{\"unknown\":[1,2]}}").getAsJsonObject();
        var original = payload.deepCopy();
        assertEquals("", LyricPayload.text(payload, "lrc", "lyric"));
        assertEquals("", LyricPayload.text(null, "lrc", "lyric"));
        assertEquals(original, payload);
    }
}
