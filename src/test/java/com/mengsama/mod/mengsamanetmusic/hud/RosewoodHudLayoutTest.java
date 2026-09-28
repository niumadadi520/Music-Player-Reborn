package com.mengsama.mod.mengsamanetmusic.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RosewoodHudLayoutTest {
    @Test void everyToggleCombinationHasOnlyItsRequestedSectionsWithoutOverlap() {
        for (int mask = 0; mask < 32; mask++) {
            var layout = layout(mask, 360);
            assertEquals(mask == 0, layout.empty(), "mask=" + mask);
            assertEquals((mask & 1) != 0, !layout.coverFrame().empty());
            assertEquals((mask & 2) != 0, !layout.title().empty());
            assertEquals((mask & 4) != 0, !layout.artist().empty());
            assertEquals((mask & 8) != 0, !layout.track().empty());
            assertEquals((mask & 16) != 0, !layout.lyricClip().empty());
            assertFalse(layout.coverFrame().overlaps(layout.information()));
            assertFalse(layout.coverFrame().overlaps(layout.lyrics()));
            assertFalse(layout.information().overlaps(layout.lyrics()));
            assertFalse(layout.title().overlaps(layout.artist()));
            assertFalse(layout.title().overlaps(layout.track()));
            assertFalse(layout.artist().overlaps(layout.track()));
            assertFalse(layout.track().overlaps(layout.time()));
            assertSectionsInside(layout);
        }
    }

    @Test void removedCoverReclaimsTheLeftColumnAndHiddenLyricsReclaimHeight() {
        var all = layout(31, 360);
        var noCover = layout(30, 360);
        var noLyrics = layout(15, 360);
        assertTrue(noCover.title().x() < all.title().x());
        assertTrue(noCover.width() < all.width());
        assertTrue(noLyrics.height() < all.height());
        assertEquals(all.coverFrame(), noLyrics.coverFrame());
        var lyricsOnly = layout(16, 360);
        assertTrue(lyricsOnly.coverFrame().empty());
        assertTrue(lyricsOnly.information().empty());
        assertEquals(lyricsOnly.lyrics().x() * 2 + lyricsOnly.lyrics().width(), lyricsOnly.width());
        assertEquals(lyricsOnly.lyrics().y() * 2 + lyricsOnly.lyrics().height(), lyricsOnly.height());
        assertEquals(2 * lyricsOnly.lyricRowHeight(), lyricsOnly.lyricClip().height());
        var coverOnly = layout(1, 360);
        assertEquals(coverOnly.width(), coverOnly.height());
    }

    @Test void narrowWindowsReflowInsteadOfOverlappingOrPlacingContentOffCanvas() {
        for (int width : new int[]{1, 8, 24, 64, 96, 160, 320}) {
            for (int mask = 0; mask < 32; mask++) {
                var layout = layout(mask, width);
                assertTrue(layout.width() <= width);
                assertSectionsInside(layout);
                assertFalse(layout.coverFrame().overlaps(layout.information()));
                assertFalse(layout.information().overlaps(layout.lyrics()));
            }
        }
        var stacked = layout(31, 64);
        assertTrue(stacked.information().y() > stacked.coverFrame().bottom());
    }

    @Test void scaledBoundsAndHitTestingAlwaysUseTheVisibleViewport() {
        for (int[] screen : new int[][]{{320, 180}, {854, 480}, {100, 70}, {1, 1}}) {
            for (float scale : new float[]{0.5F, 1F, 1.75F, 2F, Float.NaN, Float.POSITIVE_INFINITY}) {
                for (int mask = 0; mask < 32; mask++) {
                    var layout = layout(mask, screen[0]);
                    var placed = RosewoodHudLayout.place(layout, Integer.MAX_VALUE, Integer.MAX_VALUE, scale, screen[0], screen[1]);
                    assertTrue(placed.x() >= 0 && placed.y() >= 0);
                    assertTrue(placed.x() + placed.width() <= screen[0]);
                    assertTrue(placed.y() + placed.height() <= screen[1]);
                    assertTrue(Float.isFinite(placed.scale()) && placed.scale() > 0);
                    if (mask == 0) {
                        assertEquals(0, placed.width());
                        assertEquals(0, placed.height());
                        assertFalse(placed.contains(placed.x(), placed.y()));
                    } else {
                        assertTrue(placed.contains(placed.x() + 0.5, placed.y() + 0.5));
                        assertFalse(placed.contains(placed.x() + placed.width(), placed.y()));
                        assertFalse(placed.contains(placed.x(), placed.y() + placed.height()));
                    }
                }
            }
        }
    }

    @Test void togglingOffAndBackDoesNotRetainStaleGeometryAndZeroWindowsAreEmpty() {
        var initial = layout(31, 320);
        assertTrue(layout(0, 320).empty());
        assertEquals(initial, layout(31, 320));
        assertEquals(0, RosewoodHudLayout.place(initial, 10, 10, 1, 0, 180).width());
        assertEquals(0, RosewoodHudLayout.place(initial, 10, 10, 1, 320, 0).height());
        assertTrue(layout(31, 0).empty());
    }

    private static RosewoodHudLayout.Layout layout(int mask, int maxWidth) {
        return RosewoodHudLayout.calculate(new RosewoodHudLayout.Elements((mask & 1) != 0, (mask & 2) != 0,
                (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0), 9, 172, 88, 55, 160, maxWidth);
    }

    private static void assertSectionsInside(RosewoodHudLayout.Layout layout) {
        for (var area : List.of(layout.coverFrame(), layout.cover(), layout.information(), layout.title(), layout.artist(),
                layout.track(), layout.time(), layout.lyrics(), layout.lyricClip())) {
            if (area.empty()) continue;
            assertTrue(area.x() >= 0 && area.y() >= 0, area.toString());
            assertTrue(area.right() <= layout.width() && area.bottom() <= layout.height(), area.toString());
        }
    }
}
