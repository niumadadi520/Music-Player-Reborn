package com.mengsama.mod.mengsamanetmusic.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LrcParserTest {
    @Test void parsesFractionsMultipleTagsAndSkipsMetadata() {
        var lines = LrcParser.parse("[ar:artist]\n[00:01.50]first\n[00:02.005][00:03.10]second\n[00:04.00]");
        assertEquals("first", lines.get(30));
        assertEquals("second", lines.get(40));
        assertEquals("second", lines.get(62));
        assertEquals(3, lines.size());
    }

    @Test void parsesRealNetEaseCreditsOffsetDuplicatesAndTranslationShape() {
        String sample = "[ti:晴天]\n[ar:周杰伦]\n[offset:+120]\n"
                + "[00:00.000] 作词：周杰伦\n[00:00.000]编曲：周杰伦\n"
                + "[00:12.340]故事的小黄花\n[00:12.340]从出生那年就飘着\n"
                + "[00:17.01]童年的荡秋千";
        var lines = LrcParser.parseMillis(sample);
        assertEquals(2, lines.size());
        assertEquals("故事的小黄花 / 从出生那年就飘着", lines.get(12460L));
        assertEquals("童年的荡秋千", LrcParser.lineAt(lines, 17130));
        assertNull(LrcParser.lineAt(lines, 12000));
    }

    @Test void parsesQqWordTimingAndDoesNotDropRealBody() {
        String qq = "[00:01.00]词：方文山\n[00:02.00]编曲我们的夏天<00:02.20>还在继续\n"
                + "[00:03.500](0,300)风(300,300)吹过";
        var lines = LrcParser.parseMillis(qq);
        assertEquals(2, lines.size());
        assertEquals("编曲我们的夏天还在继续", lines.get(2000L));
        assertEquals("风吹过", lines.get(3500L));
    }

    @Test void untimedLyricsAreFallbackOnlyAndProgressUsesFloorBinaryLookup() {
        var fallback = LrcParser.parseMillis("纯音乐，请欣赏\n暂无歌词");
        assertEquals("纯音乐，请欣赏", LrcParser.lineAt(fallback, 2999));
        assertEquals("暂无歌词", LrcParser.lineAt(fallback, 3000));
        var timed = LrcParser.parseMillis("供应商提示\n[00:05.00]第一句\n无时间尾注");
        assertEquals(1, timed.size());
        assertNull(LrcParser.lineAt(timed, 4999));
        assertEquals("第一句", LrcParser.lineAt(timed, 5000));
    }

    @Test void creditsAreOnlyRemovedBeforeRealTimedLyricsAndTranslationAligns() {
        String lrc = "[00:00.00]作词：甲\n[00:00.00]作曲：乙\n[00:01.00]编曲：丙\n"
                + "[00:05.00][00:06.00]第一句\n[00:09.00]制作人：这是正文\n[00:12.00]第二句";
        var original = LrcParser.parseMillis(lrc);
        assertEquals(4, original.size());
        assertEquals("第一句", original.get(5000L));
        assertEquals("第一句", original.get(6000L));
        assertEquals("制作人：这是正文", original.get(9000L));
        var translated = LrcParser.parseMillis("[00:05.40]First line\n[00:12.00]Second line");
        var merged = LrcParser.mergeTranslation(original, translated);
        assertEquals("第一句\nFirst line", merged.get(5000L));
        assertEquals("第二句\nSecond line", merged.get(12000L));

        var reorderedTranslation = LrcParser.parseMillis("[00:12.00]第二句译文\n[00:05.00]第一句译文");
        var timestampMerged = LrcParser.mergeTranslation(original, reorderedTranslation);
        assertEquals("第一句\n第一句译文", timestampMerged.get(5000L));
        assertEquals("第二句\n第二句译文", timestampMerged.get(12000L));
    }

    @Test void creditLookingOnlyLineWithoutFollowingLyricsIsPreserved() {
        assertEquals("编曲：生活", LrcParser.parseMillis("[00:01.00]编曲：生活").get(1000L));
    }

    @Test void emptyLyricsAreSilent() {
        assertTrue(LrcParser.parse(null).isEmpty());
        assertTrue(LrcParser.parse("[ar:nobody]").isEmpty());
    }
}
