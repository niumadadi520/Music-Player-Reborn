package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;

record SongPageLayout(Rect previous, Rect summary, Rect next, Rect input, Rect jump) {
    static SongPageLayout within(Rect content) {
        int y = content.bottom() - Math.min(20, content.height());
        int h = Math.min(18, content.height());
        return new SongPageLayout(part(content, y, h, 0, 19), part(content, y, h, 20, 42),
                part(content, y, h, 43, 62), part(content, y, h, 65, 79), part(content, y, h, 81, 100));
    }
    private static Rect part(Rect content, int y, int h, int from, int to) {
        int x = content.x() + content.width() * from / 100;
        int right = content.x() + content.width() * to / 100;
        return new Rect(x, y, Math.max(1, right - x), h);
    }
}
