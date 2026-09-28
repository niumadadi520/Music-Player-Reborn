package com.mengsama.mod.mengsamanetmusic.gui;

 
final class QqLoginLayout {
    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        boolean overlaps(Rect other) { return x < other.right() && right() > other.x() && y < other.bottom() && bottom() > other.y(); }
        Rect inset(int padding) { return new Rect(x + padding, y + padding, width - padding * 2, height - padding * 2); }
    }
    private final Rect panel;
    private QqLoginLayout(Rect panel) { this.panel = panel; }

    static QqLoginLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(260, Math.max(120, screenWidth - 12));
        int height = Math.min(300, Math.max(164, screenHeight - 12));
        return new QqLoginLayout(new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height));
    }
    Rect panel() { return panel; }
    Rect title() { return new Rect(panel.x() + 40, panel.y() + 12, panel.width() - 54, 12); }
    Rect subtitle() { return new Rect(panel.x() + 10, panel.y() + 39, panel.width() - 20, 12); }
    Rect qrFrame() {
        int size = Math.min(184, Math.min(panel.width() - 40, panel.height() - 104));
        return new Rect(panel.x() + (panel.width() - size) / 2, panel.y() + 56, size, size);
    }
    Rect qrWhite() { return qrFrame().inset(5); }
    Rect qrImageArea() { return qrWhite().inset(8); }
    Rect status() {
        int top = qrFrame().bottom() + 5;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(10, button(0).y() - top - 4));
    }
    Rect button(int index) {
        if (index < 0 || index > 2) throw new IllegalArgumentException("Login button index");
        int width = (panel.width() - 36) / 3;
        return new Rect(panel.x() + 12 + index * (width + 6), panel.bottom() - 28, width, 20);
    }
     
    static Rect fitImage(Rect area, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) throw new IllegalArgumentException("Empty QR image");
        double scale = Math.min(area.width() / (double) imageWidth, area.height() / (double) imageHeight);
        if (scale >= 1) scale = Math.floor(scale);
        int width = Math.max(1, Math.min(area.width(), (int) Math.round(imageWidth * scale)));
        int height = Math.max(1, Math.min(area.height(), (int) Math.round(imageHeight * scale)));
        return new Rect(area.x() + (area.width() - width) / 2, area.y() + (area.height() - height) / 2, width, height);
    }
}
