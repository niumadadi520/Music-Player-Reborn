package com.mengsama.mod.mengsamanetmusic.gui;

 
public final class RosewoodPlayerLayout {
    public static final int ATLAS_WIDTH = 1236, ATLAS_HEIGHT = 1272;
    public enum Part { HEADER, SEARCH, NAVIGATION, CONTENT, CONTROLS, PROGRESS, NOW_PLAYING }
    public enum Control { CLOSE, SEARCH, SOURCE, AUTH, NAV_SEARCH, NAV_PLAYLIST, NAV_FAVORITES, NAV_LYRICS,
        NAV_SETTINGS, MODE, PREVIOUS, PLAY, NEXT, VOLUME, STOP, COVER }
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public Rect at(int left, int top) { return new Rect(left + x, top + y, width, height); }
        public boolean overlaps(Rect other) { return x < other.right() && right() > other.x() && y < other.bottom() && bottom() > other.y(); }
    }

    private final int width, height, logicalHeight;

    public RosewoodPlayerLayout(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.logicalHeight = Math.max(228, this.height);
    }

    public static RosewoodPlayerLayout fit(int requestedWidth, int requestedHeight, int screenWidth, int screenHeight) {
        return new RosewoodPlayerLayout(Math.min(requestedWidth, Math.max(1, screenWidth - 12)),
                Math.min(requestedHeight, Math.max(1, screenHeight - 12)));
    }

    public int width() { return width; }
    public int height() { return height; }
    private int sx(int x) { return Math.round(x * width / 396F); }
    private int sy(int y) { return Math.round(y * height / (float)logicalHeight); }
    private Rect rect(int x, int y, int w, int h) {
        int left = sx(x), top = sy(y);
        return new Rect(left, top, Math.max(1, sx(x + w) - left), Math.max(1, sy(y + h) - top));
    }

    public Rect band(Part part) {
        return switch (part) {
            case HEADER -> rect(0, 0, 396, 28);
            case SEARCH -> rect(0, 28, 396, 28);
            case NAVIGATION -> rect(0, 56, 396, 26);
            case CONTENT -> rect(0, 82, 396, logicalHeight - 174);
            case CONTROLS -> rect(0, logicalHeight - 92, 396, 36);
            case PROGRESS -> rect(0, logicalHeight - 56, 396, 24);
            case NOW_PLAYING -> rect(0, logicalHeight - 32, 396, 32);
        };
    }

    public static Rect source(Part part) {
        return switch (part) {
            case HEADER -> new Rect(0, 0, ATLAS_WIDTH, 94);
            case SEARCH -> new Rect(0, 94, ATLAS_WIDTH, 99);
            case NAVIGATION -> new Rect(0, 193, ATLAS_WIDTH, 92);
            case CONTENT -> new Rect(0, 285, ATLAS_WIDTH, 604);
            case CONTROLS -> new Rect(0, 889, ATLAS_WIDTH, 118);
            case PROGRESS -> new Rect(0, 1007, ATLAS_WIDTH, 83);
            case NOW_PLAYING -> new Rect(0, 1090, ATLAS_WIDTH, 182);
        };
    }

    public Rect content() { return rect(14, 88, 368, logicalHeight - 188); }
    public Rect songContent() {
        Rect full = content();
        return new Rect(full.x(), full.y(), full.width(), Math.max(1, full.height() - 38));
    }
    public Rect searchField() { return rect(43, 34, 219, 18); }
    public Rect track() { return rect(18, logicalHeight - 50, 360, 5); }
    public Rect title() { return rect(38, 9, 232, 12); }
    public Rect earbudConnection() { return rect(274, 5, 82, 18); }
    public Rect settingsEarbuds() { return compactSettings() ? rect(272, 105, 100, 20) : rect(68, 199, 260, 22); }
    public boolean compactSettings() { return logicalHeight < 330; }
    public Rect settingsTheme() { return compactSettings() ? rect(28, 105, 122, 20) : rect(68, 135, 260, 22); }
    public Rect settingsBroadcast() { return compactSettings() ? rect(158, 105, 107, 20) : rect(68, 167, 260, 22); }

    public Rect control(Control control) {
        int y = logicalHeight - 88;
        return switch (control) {
            case CLOSE -> rect(362, 4, 22, 22);
            case SEARCH -> rect(17, 34, 22, 18);
            case SOURCE -> rect(267, 34, 48, 18);
            case AUTH -> rect(320, 34, 60, 18);
            case NAV_SEARCH -> rect(10, 57, 75, 23);
            case NAV_PLAYLIST -> rect(87, 57, 72, 23);
            case NAV_FAVORITES -> rect(163, 57, 72, 23);
            case NAV_LYRICS -> rect(239, 57, 72, 23);
            case NAV_SETTINGS -> rect(315, 57, 71, 23);
            case MODE -> rect(53, y, 44, 28);
            case PREVIOUS -> rect(142, y, 35, 28);
            case PLAY -> rect(184, y - 1, 34, 30);
            case NEXT -> rect(225, y, 34, 28);
            case STOP -> rect(264, y + 3, 20, 22);
            case VOLUME -> rect(306, y, 42, 28);
            case COVER -> rect(14, logicalHeight - 30, 24, 24);
        };
    }
}
