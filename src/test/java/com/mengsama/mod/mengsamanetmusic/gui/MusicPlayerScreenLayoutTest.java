package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerScreenLayoutTest {
    @Test void inventorySpaceBecomesUsableMusicContentInEveryPlayerContext() {
        var ui = MusicPlayerUiConfig.Values.defaults();
        for (String context : new String[] {"portable_item", "block", "portable_block"}) {
            var layout = MusicPlayerScreenLayout.forPanel(ui, context);
            assertEquals(232, layout.contentHeight());
            assertTrue(layout.contentHeight() > 156);
            assertEquals(320, layout.controlsY());
            assertTrue(MusicPlayerScreenLayout.CONTENT_Y + layout.contentHeight() < layout.controlsY());
            assertEquals(352, layout.progressY());
            assertEquals(376, layout.nowPlayingY());
            assertTrue(layout.nowPlayingY() + 24 < ui.screenHeight);
        }
    }

    @Test void customPanelHeightExpandsTheListWhileTheBottomControlsStayInsideThePanel() {
        var ui = MusicPlayerUiConfig.Values.defaults();
        for (int height : new int[] {400, 408, 480, 560}) {
            ui.screenHeight = height;
            var layout = MusicPlayerScreenLayout.forPanel(ui, "block");
            assertEquals(height - 176, layout.contentHeight());
            assertEquals(8, height - (layout.nowPlayingY() + 24));
            assertTrue(layout.controlsY() + 28 <= layout.progressY());
            assertTrue(layout.progressY() + 20 <= layout.nowPlayingY());
        }
    }

    @Test void raisedCustomControlRemainsClickableAboveTheListEnd() {
        var ui = MusicPlayerUiConfig.Values.defaults();
        ui.buttonPositions.put("block.play_pause", new MusicPlayerUiConfig.Position(100, 280));
        var layout = MusicPlayerScreenLayout.forPanel(ui, "block");
        assertEquals(276, MusicPlayerScreenLayout.CONTENT_Y + layout.contentHeight());
        assertEquals(280, ui.buttonPositions.get("block.play_pause").y);
        assertEquals(232, MusicPlayerScreenLayout.forPanel(ui, "portable_item").contentHeight());
    }
}
