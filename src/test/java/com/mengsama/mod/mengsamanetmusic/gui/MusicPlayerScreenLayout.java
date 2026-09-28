package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;

 
public record MusicPlayerScreenLayout(int contentHeight, int controlsY, int progressY, int nowPlayingY) {
    public static final int CONTENT_Y = 84;

    public static MusicPlayerScreenLayout forPanel(MusicPlayerUiConfig.Values ui, String screenType) {
        int controlsY = ui.screenHeight - 88;
        int contentBottom = controlsY - 4;
         
        for (String id : new String[] {"previous", "play_pause", "stop", "next", "mode", "broadcast"}) {
            MusicPlayerUiConfig.Position p = ui.buttonPositions.get(screenType + "." + id);
            if (p != null && p.y >= CONTENT_Y + 28 && p.y < controlsY) {
                contentBottom = Math.min(contentBottom, p.y - 4);
            }
        }
        return new MusicPlayerScreenLayout(contentBottom - CONTENT_Y, controlsY,
                ui.screenHeight - 56, ui.screenHeight - 32);
    }
}
