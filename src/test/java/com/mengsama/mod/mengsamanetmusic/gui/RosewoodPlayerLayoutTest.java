package com.mengsama.mod.mengsamanetmusic.gui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RosewoodPlayerLayoutTest {
    @Test void scaledWindowsKeepSkinControlsInsideThePanelAndDisjoint() {
        for (int[] screen : new int[][]{{320,240},{426,240},{640,360},{854,480},{1920,1080},{240,180}}) {
            for (int[] request : new int[][]{{396,408},{300,400},{640,560}}) {
                var layout = RosewoodPlayerLayout.fit(request[0], request[1], screen[0], screen[1]);
                assertTrue(layout.width() <= screen[0] - 12);
                assertTrue(layout.height() <= screen[1] - 12);
                var panel = new RosewoodPlayerLayout.Rect(0,0,layout.width(),layout.height());
                List<RosewoodPlayerLayout.Rect> targets = new ArrayList<>();
                for (var control : RosewoodPlayerLayout.Control.values()) {
                    var rect = layout.control(control);
                    assertInside(panel, rect);
                    targets.add(rect);
                    assertFalse(rect.overlaps(layout.track()), control + " must not steal progress clicks");
                    assertFalse(rect.overlaps(layout.content()), control + " must not steal song clicks");
                }
                for (int i=0;i<targets.size();i++) for (int j=i+1;j<targets.size();j++)
                    assertFalse(targets.get(i).overlaps(targets.get(j)), i+" overlaps "+j);
                assertInside(layout.content(), layout.settingsTheme());
                assertInside(layout.content(), layout.settingsBroadcast());
                assertInside(layout.content(), layout.settingsEarbuds());
                assertFalse(layout.settingsTheme().overlaps(layout.settingsEarbuds()));
                assertFalse(layout.settingsBroadcast().overlaps(layout.settingsEarbuds()));
                assertInside(panel, layout.earbudConnection());
                assertFalse(layout.earbudConnection().overlaps(layout.title()));
                assertFalse(layout.earbudConnection().overlaps(layout.control(RosewoodPlayerLayout.Control.CLOSE)));
                assertFalse(layout.settingsTheme().overlaps(layout.settingsBroadcast()));
                assertFalse(layout.searchField().overlaps(layout.control(RosewoodPlayerLayout.Control.SEARCH)));
                assertFalse(layout.searchField().overlaps(layout.control(RosewoodPlayerLayout.Control.SOURCE)));
                assertInside(panel, layout.content());
            }
        }
    }

    @Test void defaultLayoutKeepsTheExpandedMusicContent() {
        var layout = RosewoodPlayerLayout.fit(396,408,854,480);
        assertEquals(396,layout.width());
        assertEquals(408,layout.height());
        assertEquals(220,layout.content().height());
        assertEquals(88,layout.content().y());
    }

    @Test void imageSlicesCoverTheApprovedAtlasExactlyAndPanelBandsMeet() {
        for (int height : new int[]{168,228,348,408,560}) {
            var layout = new RosewoodPlayerLayout(396,height);
            int sourceY=0, destY=0;
            for (var part : RosewoodPlayerLayout.Part.values()) {
                var source = RosewoodPlayerLayout.source(part);
                var dest = layout.band(part);
                assertEquals(sourceY,source.y());
                assertEquals(destY,dest.y());
                assertEquals(RosewoodPlayerLayout.ATLAS_WIDTH,source.width());
                assertEquals(layout.width(),dest.width());
                sourceY=source.bottom();destY=dest.bottom();
            }
            assertEquals(RosewoodPlayerLayout.ATLAS_HEIGHT,sourceY);
            assertEquals(height,destY);
        }
    }

    @Test void translationUsesExactlyTheSameBoundsForDrawingAndHitTesting() {
        var relative = new RosewoodPlayerLayout(396,408).control(RosewoodPlayerLayout.Control.PLAY);
        var absolute = relative.at(123,27);
        assertTrue(absolute.contains(123+relative.x(),27+relative.y()));
        assertFalse(absolute.contains(absolute.right(),absolute.y()));
        assertFalse(absolute.contains(absolute.x(),absolute.bottom()));
    }

    private static void assertInside(RosewoodPlayerLayout.Rect outer,RosewoodPlayerLayout.Rect inner) {
        assertTrue(inner.width()>0 && inner.height()>0);
        assertTrue(inner.x()>=outer.x() && inner.y()>=outer.y(),inner.toString());
        assertTrue(inner.right()<=outer.right() && inner.bottom()<=outer.bottom(),inner.toString());
    }
}
