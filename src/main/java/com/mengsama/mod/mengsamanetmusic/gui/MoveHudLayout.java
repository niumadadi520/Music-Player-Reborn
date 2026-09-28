package com.mengsama.mod.mengsamanetmusic.gui;

 
public final class MoveHudLayout {
    public static final int MARGIN = 4;
    public static final int GAP = 4;
    public static final int BUTTON_H = 18;
    public static final int MAX_STATUS_LINES = 3;

    private MoveHudLayout() {}

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Layout(Rect panel, Rect options, Rect colors, java.util.List<Rect> colorTargets,
                         Rect rgbControls, Rect backgroundImport, Rect status,
                         Rect cancel, Rect save, boolean stackedActions, int statusLines) {}

    public static Layout calculate(int screenWidth, int screenHeight, int wrappedStatusHeight, int lineHeight) {
        boolean compactHeight = screenHeight < 300;
        int panelWidth = compactHeight ? Math.max(1, screenWidth - MARGIN * 2)
                : Math.min(260, Math.max(1, screenWidth - MARGIN * 2));
        int panelX = Math.max(MARGIN, screenWidth - panelWidth - MARGIN);
        Rect panel = new Rect(panelX, 0, panelWidth, Math.max(1, screenHeight));
        int x = panelX + 7;
        int contentWidth = Math.max(1, panelWidth - 14);
        int y = 22;

        int optionsHeight = 4 * 17;
        Rect options;
        Rect colors;
        Rect background;
        java.util.List<Rect> colorTargets;
        Rect rgbControls;
        if (compactHeight) {
            int columnWidth = (contentWidth - GAP) / 2;
            options = new Rect(x, y, columnWidth, optionsHeight);
            int colorX = x + columnWidth + GAP;
            int colorWidth = contentWidth - columnWidth - GAP;
            colorTargets = colorTargetGrid(colorX, y, colorWidth);
            int gridBottom = colorTargets.stream().mapToInt(Rect::bottom).max().orElse(y);
            rgbControls = new Rect(colorX, gridBottom + GAP, colorWidth, 58);
            colors = new Rect(colorX, y, colorWidth, rgbControls.bottom() - y);
            int backgroundY = options.bottom() + GAP;
            background = new Rect(x, backgroundY, columnWidth, BUTTON_H);
            y = Math.max(background.bottom(), colors.bottom()) + GAP;
        } else {
            options = new Rect(x, y, contentWidth, optionsHeight);
            y = options.bottom() + GAP;
            colorTargets = colorTargetGrid(x, y, contentWidth);
            int gridBottom = colorTargets.stream().mapToInt(Rect::bottom).max().orElse(y);
            rgbControls = new Rect(x, gridBottom + GAP, contentWidth, 58);
            colors = new Rect(x, y, contentWidth, rgbControls.bottom() - y);
            y = colors.bottom() + GAP;
            background = new Rect(x, y, contentWidth, 31);
            y = background.bottom() + GAP;
        }

        boolean stacked = contentWidth < 150;
        int actionHeight = stacked ? BUTTON_H * 2 + GAP : BUTTON_H;
        int actionTop = Math.max(y + lineHeight, screenHeight - MARGIN - actionHeight);
        int availableStatus = Math.max(lineHeight, actionTop - GAP - y);
        int requestedLines = Math.max(1, (wrappedStatusHeight + Math.max(1, lineHeight) - 1) / Math.max(1, lineHeight));
        int statusLines = Math.min(MAX_STATUS_LINES, Math.min(requestedLines, Math.max(1, availableStatus / Math.max(1, lineHeight))));
        Rect status = new Rect(x, y, contentWidth, statusLines * Math.max(1, lineHeight));

        int buttonWidth = stacked ? contentWidth : Math.min(88, (contentWidth - GAP) / 2);
        Rect cancel;
        Rect save;
        if (stacked) {
            cancel = new Rect(x, screenHeight - MARGIN - actionHeight, buttonWidth, BUTTON_H);
            save = new Rect(x, cancel.bottom() + GAP, buttonWidth, BUTTON_H);
        } else {
            int buttonsX = x + (contentWidth - buttonWidth * 2 - GAP) / 2;
            cancel = new Rect(buttonsX, screenHeight - MARGIN - BUTTON_H, buttonWidth, BUTTON_H);
            save = new Rect(buttonsX + buttonWidth + GAP, cancel.y, buttonWidth, BUTTON_H);
        }
        return new Layout(panel, options, colors, java.util.List.copyOf(colorTargets), rgbControls,
                background, status, cancel, save, stacked, statusLines);
    }

     
    static java.util.List<Rect> colorTargetGrid(int x, int y, int width) {
        int columns = width >= 360 ? 4 : width >= 128 ? 2 : 1;
        int cellWidth = Math.max(1, (width - GAP * (columns - 1)) / columns);
        java.util.List<Rect> result = new java.util.ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int row = i / columns;
            int column = i % columns;
            int cellX = x + column * (cellWidth + GAP);
            int actualWidth = column == columns - 1 ? Math.max(1, x + width - cellX) : cellWidth;
            result.add(new Rect(cellX, y + row * (BUTTON_H + GAP), actualWidth, BUTTON_H));
        }
        return result;
    }
}
