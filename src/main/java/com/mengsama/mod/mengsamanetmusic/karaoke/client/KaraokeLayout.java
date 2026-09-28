package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;

 
public final class KaraokeLayout {
    private final Rect panel;
    public KaraokeLayout(int screenWidth, int screenHeight) {
        int w = Math.min(396, Math.max(1, screenWidth - 12));
        int h = Math.min(300, Math.max(1, screenHeight - 12));
        panel = new Rect((screenWidth - w) / 2, (screenHeight - h) / 2, w, h);
    }
    public Rect panel() { return panel; }
    public boolean compact() { return panel.height() < 218; }
    public int rows() { return Math.max(1, Math.min(6, (paginationY() - 4 - rowsY()) / 18)); }
    public int rowsY() { return compact() ? 61 : 86; }
    public int paginationY() { return panel.height() - (compact() ? 83 : 99); }
    public Rect at(int x, int y, int w, int h) { return new Rect(panel.x() + x, panel.y() + y, w, h); }
    public Rect input() { return at(17, compact() ? 32 : 48, panel.width() - 90, 20); }
    public Rect add() { return at(panel.width() - 67, compact() ? 32 : 48, 50, 20); }
    public Rect row(int index) { return at(17, rowsY() + index * 18, panel.width() - 34, 16); }
    public Rect remove(int index) { var r = row(index); return new Rect(r.right() - 40, r.y(), 40, r.height()); }
    public Rect previous() { return at(17, paginationY(), 34, 17); }
    public Rect next() { return at(panel.width() - 51, paginationY(), 34, 17); }
    public Rect volume() { return at(20, panel.height() - (compact() ? 62 : 73), panel.width() - 40, 22); }
    public Rect back() { return at(17, panel.height() - 28, panel.width() - 34, 20); }
    public Rect microphoneCode() { return at(17, compact() ? 48 : 73, panel.width() - 90, 20); }
    public Rect copy() { return at(panel.width() - 67, compact() ? 48 : 73, 50, 20); }
    public Rect toggle() { return at(17, compact() ? 79 : 110, panel.width() - 34, 24); }
    public Rect message() { return at(17, panel.height() - (compact() ? 38 : 46), panel.width() - 34, 10); }

     
    public static Rect header(RosewoodPlayerLayout player, int left, int top) {
        Rect title = player.title(), close = player.control(RosewoodPlayerLayout.Control.CLOSE);
        int start = title.right() + Math.max(2, Math.round(player.width() * 8F / 396));
        int end = close.x() - Math.max(2, Math.round(player.width() * 4F / 396));
        return new Rect(left + start, top + close.y(), Math.max(1, end - start), close.height());
    }
}
