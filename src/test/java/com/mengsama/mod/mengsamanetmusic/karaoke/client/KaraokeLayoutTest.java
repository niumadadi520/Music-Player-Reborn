package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KaraokeLayoutTest {
    @Test void musicHeaderLeavesTheFiveTabsTitleAndCloseButtonUntouched() {
        for (int[] screen : new int[][]{{240,180},{320,240},{426,240},{640,360},{854,480},{1920,1080}}) {
            for (int[] request : new int[][]{{396,408},{300,400},{640,560}}) {
                var player = RosewoodPlayerLayout.fit(request[0], request[1], screen[0], screen[1]);
                Rect header = KaraokeLayout.header(player, 0, 0);
                inside(new Rect(0,0,player.width(),player.height()), header);
                assertFalse(header.overlaps(player.title()), "title and maid return must remain clickable");
                for (var control : RosewoodPlayerLayout.Control.values())
                    assertFalse(header.overlaps(player.control(control)), control.toString());
                assertFalse(header.overlaps(player.searchField()));
                assertFalse(header.overlaps(player.content()));
                assertFalse(header.overlaps(player.track()));
            }
        }
    }

    @Test void speakerControlsAndPagesStaySeparateAtSmallAndLargeGuiScales() {
        for (int[] screen : new int[][]{{240,180},{320,240},{426,240},{640,360},{854,480},{1920,1080}}) {
            var layout = new KaraokeLayout(screen[0],screen[1]);
            var controls = new ArrayList<>(List.of(layout.input(),layout.add(),layout.previous(),layout.next(),layout.volume(),layout.back(),layout.message()));
            for (int i = 0; i < layout.rows(); i++) controls.add(layout.row(i));
            assertDisjoint(layout.panel(),controls);
            assertTrue(layout.rows() >= 1 && layout.rows() <= 6);
            for (int i = 0; i < layout.rows(); i++) inside(layout.row(i),layout.remove(i));
            assertTrue(layout.rowsY() + layout.rows()*18 <= layout.paginationY());
        }
    }

    @Test void microphoneSwitchNeverCoversConnectionCodeOrReturnControl() {
        for (int[] screen : new int[][]{{240,180},{320,240},{426,240},{640,360},{854,480}}) {
            var layout = new KaraokeLayout(screen[0],screen[1]);
            assertDisjoint(layout.panel(),List.of(layout.microphoneCode(),layout.copy(),layout.toggle(),layout.message(),layout.back()));
        }
    }

    private static void assertDisjoint(Rect panel, List<Rect> controls) {
        for (int i=0;i<controls.size();i++) {
            inside(panel,controls.get(i));
            for (int j=i+1;j<controls.size();j++) assertFalse(controls.get(i).overlaps(controls.get(j)),controls.get(i)+" overlaps "+controls.get(j));
        }
    }
    private static void inside(Rect panel,Rect child) {
        assertTrue(child.width()>0 && child.height()>0);
        assertTrue(child.x()>=panel.x() && child.y()>=panel.y(),child.toString());
        assertTrue(child.right()<=panel.right() && child.bottom()<=panel.bottom(),child.toString());
    }
}
