package com.mengsama.mod.mengsamanetmusic.hud;

 
public final class RosewoodHudLayout {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean empty() { return width <= 0 || height <= 0; }
        public boolean contains(double px, double py) { return !empty() && px >= x && px < right() && py >= y && py < bottom(); }
        public boolean overlaps(Rect other) { return !empty() && !other.empty() && x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
    }
    public record Elements(boolean cover, boolean title, boolean artist, boolean progress, boolean lyrics) {
        boolean information() { return title || artist || progress; }
        boolean any() { return cover || information() || lyrics; }
    }
    public record Layout(int width, int height, Rect coverFrame, Rect cover, Rect information,
                         Rect title, Rect artist, Rect track, Rect time, Rect lyrics, Rect lyricClip, int lyricRowHeight) {
        public boolean empty() { return width == 0 || height == 0; }
    }
    public record Viewport(int x, int y, int width, int height, float scale) {
        public boolean contains(double px, double py) { return new Rect(x, y, width, height).contains(px, py); }
    }
    private RosewoodHudLayout() {}

    public static Layout calculate(Elements e, int lineHeight, int titleWidth, int artistWidth,
                                   int timeWidth, int lyricWidth, int maxWidth) {
        int row = Math.max(1, lineHeight) + 1;
        int lyricRow = Math.max(1, lineHeight) + 3;
        if (!e.any() || maxWidth <= 0) return new Layout(0, 0, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, lyricRow);
        int informationWidth = Math.max(e.title ? titleWidth : 0, e.artist ? artistWidth : 0);
        if (e.progress) informationWidth = Math.max(informationWidth, 64 + 4 + Math.max(0, timeWidth));
        int informationHeight = (e.title ? row : 0) + (e.artist ? row : 0) + (e.progress ? Math.max(12, row) : 0);
        int topWanted = (e.cover ? 46 : 0) + (e.cover && e.information() ? 2 : 0)
                + (e.information() ? Math.max(1, informationWidth) + 8 : 0);
        int wanted = Math.max(topWanted, e.lyrics ? Math.max(1, lyricWidth) + 8 : 0) + 6;
        int width = Math.min(maxWidth, wanted);
        int border = Math.min(3, Math.max(0, (width - 1) / 2));
        int inner = Math.max(1, width - border * 2);
        int cursorY = border;
        Rect coverFrame = EMPTY, cover = EMPTY, information = EMPTY, title = EMPTY, artist = EMPTY, track = EMPTY, time = EMPTY;
        int topHeight = 0;
        boolean stacked = e.cover && e.information() && inner < 46 + 2 + 32;
        if (e.cover) {
            int size = Math.min(46, inner);
            coverFrame = new Rect(border, cursorY, size, size);
            cover = inset(coverFrame, 3);
            topHeight = size;
        }
        if (e.information()) {
            int infoX = e.cover && !stacked ? coverFrame.right() + 2 : border;
            int infoY = stacked ? coverFrame.bottom() + 2 : cursorY;
            int infoW = width - border - infoX;
            int infoH = informationHeight + 8;
            if (e.cover && !stacked) infoH = Math.max(infoH, topHeight);
            information = new Rect(infoX, infoY, infoW, infoH);
            Rect text = inset(information, 4);
            int y = text.y;
            if (e.title) { title = new Rect(text.x, y, text.width, Math.max(1, lineHeight)); y += row; }
            if (e.artist) { artist = new Rect(text.x, y, text.width, Math.max(1, lineHeight)); y += row; }
            if (e.progress) {
                int trackWidth = Math.max(1, text.width - Math.min(Math.max(0, timeWidth), Math.max(0, text.width - 12)) - 4);
                track = new Rect(text.x, y + 3, trackWidth, 4);
                int timeX = Math.min(text.right(), track.right() + 4);
                time = new Rect(timeX, y + 1, Math.max(0, text.right() - timeX), Math.max(1, lineHeight));
            }
            topHeight = Math.max(topHeight, information.bottom() - cursorY);
        }
        cursorY += topHeight;
        Rect lyrics = EMPTY, lyricClip = EMPTY;
        if (e.lyrics) {
            if (topHeight > 0) cursorY += 2;
            lyrics = new Rect(border, cursorY, inner, lyricRow * 2 + 8);
            Rect text = inset(lyrics, 4);
            lyricClip = new Rect(text.x, text.y, text.width, lyricRow * 2);
            cursorY = lyrics.bottom();
        }
        return new Layout(width, cursorY + border, coverFrame, cover, information, title, artist, track, time, lyrics, lyricClip, lyricRow);
    }

    private static Rect inset(Rect rect, int wanted) {
        int x = Math.min(wanted, Math.max(0, (rect.width - 1) / 2));
        int y = Math.min(wanted, Math.max(0, (rect.height - 1) / 2));
        return new Rect(rect.x + x, rect.y + y, Math.max(1, rect.width - x * 2), Math.max(1, rect.height - y * 2));
    }

    public static Viewport place(Layout layout, int configuredX, int configuredY, float requestedScale, int screenWidth, int screenHeight) {
        if (layout.empty() || screenWidth <= 0 || screenHeight <= 0) return new Viewport(0, 0, 0, 0, 1);
        float scale = Float.isFinite(requestedScale) ? Math.max(0.5F, Math.min(2F, requestedScale)) : 1F;
        scale = Math.min(scale, Math.min(screenWidth / (float)layout.width, screenHeight / (float)layout.height));
        int width = Math.min(screenWidth, (int)Math.ceil(layout.width * scale));
        int height = Math.min(screenHeight, (int)Math.ceil(layout.height * scale));
        return new Viewport(Math.max(0, Math.min(screenWidth - width, configuredX)),
                Math.max(0, Math.min(screenHeight - height, configuredY)), width, height, scale);
    }
}
