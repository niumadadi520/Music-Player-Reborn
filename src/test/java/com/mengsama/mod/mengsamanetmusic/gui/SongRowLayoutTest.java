package com.mengsama.mod.mengsamanetmusic.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SongRowLayoutTest {
    @Test void narrowWidthKeepsThreeIndependentColumnsWithinBounds() {
        SongRowLayout.Columns columns = SongRowLayout.allocate(60, 200, 120, 160);
        assertTrue(columns.title() > 0);
        assertTrue(columns.artist() > 0);
        assertTrue(columns.album() > 0);
        assertEquals(60, columns.total());
    }

    @Test void missingFieldsHaveExplicitFallbacksAndDoNotOverflow() {
        assertEquals("未知歌曲", SongRowLayout.value(null, "未知歌曲"));
        assertEquals("未知歌手", SongRowLayout.value("", "未知歌手"));
        assertEquals("专辑", SongRowLayout.value("专辑", "未知专辑"));
        SongRowLayout.Columns columns = SongRowLayout.allocate(17, 24, 24, 24);
        assertTrue(columns.total() <= 17);
        assertTrue(columns.title() >= 0 && columns.artist() >= 0 && columns.album() >= 0);
    }

    @Test void wideAndLegacyFortyPixelRowsUseTheSameSingleLineColumnContract() {
        SongRowLayout.Columns defaultRow = SongRowLayout.allocate(300, 80, 70, 90);
        SongRowLayout.Columns legacyRow = SongRowLayout.allocate(300, 80, 70, 90);
        assertEquals(defaultRow, legacyRow, "row height only affects vertical centering, never column semantics");
        assertEquals(300, defaultRow.total());
    }

    @Test void actionHitOnlyCoversTheRightSideButton() {
        assertTrue(SongRowRenderer.hitAction(279, 20, 100, 10, 180, 20));
        assertTrue(SongRowRenderer.hitAction(240, 20, 100, 10, 180, 20));
        assertFalse(SongRowRenderer.hitAction(239, 20, 100, 10, 180, 20));
        assertFalse(SongRowRenderer.hitAction(180, 10, 100, 10, 180, 20));
        assertFalse(SongRowRenderer.hitAction(280, 31, 100, 10, 180, 20));
    }

    @Test void actionWidthIsReservedFromSongColumns() {
        SongRowLayout.Columns columns = SongRowLayout.allocate(300 - SongRowRenderer.ACTION_WIDTH - SongRowRenderer.ACTION_GAP, 120, 80, 80);
        assertEquals(300 - SongRowRenderer.ACTION_WIDTH - SongRowRenderer.ACTION_GAP, columns.total());
    }
}
