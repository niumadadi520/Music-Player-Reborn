package com.mengsama.mod.mengsamanetmusic.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QqLoginLayoutTest {
    @Test void smallAndLargeWindowsKeepQrStatusAndAllActionsSeparate() {
        for (int[] size : new int[][]{{320,240},{426,240},{640,360},{854,480},{240,180},{1920,1080}}) {
            var layout = QqLoginLayout.fit(size[0], size[1]);
            var screen = new QqLoginLayout.Rect(0, 0, size[0], size[1]);
            inside(screen, layout.panel());
            inside(layout.panel(), layout.title());
            inside(layout.panel(), layout.subtitle());
            inside(layout.panel(), layout.qrFrame());
            inside(layout.qrFrame(), layout.qrWhite());
            inside(layout.qrWhite(), layout.qrImageArea());
            inside(layout.panel(), layout.status());
            assertEquals(layout.qrImageArea().width(), layout.qrImageArea().height());
            assertFalse(layout.subtitle().overlaps(layout.qrFrame()));
            assertFalse(layout.status().overlaps(layout.qrFrame()));
            for (int button = 0; button < 3; button++) {
                var target = layout.button(button);
                inside(layout.panel(), target);
                assertFalse(target.overlaps(layout.qrFrame()));
                assertFalse(target.overlaps(layout.status()));
                for (int next = button + 1; next < 3; next++) assertFalse(target.overlaps(layout.button(next)));
            }
        }
    }

    @Test void qrScalingPreservesSquareAndQuietZoneAcrossNativeSizes() {
        for (int[] viewport : new int[][]{{320,240},{854,480}}) {
            var layout = QqLoginLayout.fit(viewport[0], viewport[1]);
            for (int source : new int[]{72,99,144,177,256,512}) {
                var image = QqLoginLayout.fitImage(layout.qrImageArea(), source, source);
                inside(layout.qrImageArea(), image);
                assertEquals(image.width(), image.height());
                assertTrue(image.x() - layout.qrWhite().x() >= 8);
                assertTrue(image.y() - layout.qrWhite().y() >= 8);
                assertTrue(layout.qrWhite().right() - image.right() >= 8);
                assertTrue(layout.qrWhite().bottom() - image.bottom() >= 8);
            }
        }
    }

    @Test void rectangularImagesAreLetterboxedInsteadOfStretchedAndSmallImagesUseIntegerScale() {
        var area = new QqLoginLayout.Rect(12,24,158,158);
        var rectangle = QqLoginLayout.fitImage(area, 120, 60);
        assertEquals(120, rectangle.width());
        assertEquals(60, rectangle.height());
        var small = QqLoginLayout.fitImage(area, 50, 50);
        assertEquals(150, small.width());
        inside(area, small);
        assertThrows(IllegalArgumentException.class, () -> QqLoginLayout.fitImage(area, 0, 50));
    }

    private static void inside(QqLoginLayout.Rect outer, QqLoginLayout.Rect inner) {
        assertTrue(inner.width() > 0 && inner.height() > 0);
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right() && inner.bottom() <= outer.bottom());
    }
}
