package com.mengsama.mod.mengsamanetmusic.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveHudLayoutTest {
    @Test void commonGuiSizesKeepSectionsAndActionsDisjoint() {
         
        int[][] sizes = {{320, 180}, {320, 240}, {426, 240}, {480, 270}, {640, 360}, {854, 480},
                {960, 540}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160}};
        for (int[] size : sizes) {
            MoveHudLayout.Layout layout = MoveHudLayout.calculate(size[0], size[1], 90, 9);
            assertInside(layout.panel(), layout.options());
            assertInside(layout.panel(), layout.colors());
            assertInside(layout.panel(), layout.rgbControls());
            for (MoveHudLayout.Rect target : layout.colorTargets()) assertInside(layout.panel(), target);
            assertInside(layout.panel(), layout.backgroundImport());
            assertInside(layout.panel(), layout.status());
            assertInside(layout.panel(), layout.cancel());
            assertInside(layout.panel(), layout.save());
            assertFalse(layout.options().overlaps(layout.colors()), label(size));
            assertNoIntersections(layout.colorTargets(), label(size));
            for (MoveHudLayout.Rect target : layout.colorTargets())
                assertTrue(target.bottom() + MoveHudLayout.GAP <= layout.rgbControls().y(), label(size));
            assertFalse(layout.backgroundImport().overlaps(layout.status()), label(size));
            assertFalse(layout.status().overlaps(layout.cancel()), label(size));
            assertFalse(layout.status().overlaps(layout.save()), label(size));
            assertFalse(layout.cancel().overlaps(layout.save()), label(size));
            assertTrue(layout.statusLines() >= 1 && layout.statusLines() <= MoveHudLayout.MAX_STATUS_LINES);
        }
    }

    @Test void statusHeightTracksWrappedTextButCapsAtThreeLines() {
        assertEquals(1, MoveHudLayout.calculate(854, 480, 9, 9).statusLines());
        assertEquals(2, MoveHudLayout.calculate(854, 480, 18, 9).statusLines());
        assertEquals(3, MoveHudLayout.calculate(854, 480, 900, 9).statusLines());
    }

    @Test void narrowPanelsStackActionButtons() {
        MoveHudLayout.Layout narrow = MoveHudLayout.calculate(150, 480, 18, 9);
        assertTrue(narrow.stackedActions());
        assertEquals(narrow.cancel().x(), narrow.save().x());
        assertTrue(narrow.cancel().bottom() < narrow.save().y());
    }

    @Test void colorGridSwitchesBetweenFourTwoAndOneColumns() {
        assertEquals(1, MoveHudLayout.colorTargetGrid(0, 0, 500).stream().map(MoveHudLayout.Rect::y).distinct().count());
        assertEquals(2, MoveHudLayout.colorTargetGrid(0, 0, 200).stream().map(MoveHudLayout.Rect::y).distinct().count());
        assertEquals(4, MoveHudLayout.colorTargetGrid(0, 0, 120).stream().map(MoveHudLayout.Rect::y).distinct().count());
    }

    private static void assertNoIntersections(java.util.List<MoveHudLayout.Rect> rects, String label) {
        for (int i = 0; i < rects.size(); i++) for (int j = i + 1; j < rects.size(); j++)
            assertFalse(rects.get(i).overlaps(rects.get(j)), label + " indexes " + i + "," + j);
    }

    private static void assertInside(MoveHudLayout.Rect outer, MoveHudLayout.Rect inner) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right() && inner.bottom() <= outer.bottom(), inner.toString());
    }

    private static String label(int[] size) { return size[0] + "x" + size[1]; }
}
