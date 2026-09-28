package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;

 
public final class ThemeColorEditor {
    public enum Target { ACCENT, BACKGROUND }

    private final MusicPlayerUiConfig.Values values;
    private final int originalAccent;
    private final int originalBackground;
    private Target target = Target.ACCENT;

    public ThemeColorEditor(MusicPlayerUiConfig.Values values) {
        this.values = values;
        this.originalAccent = values.themeRgb();
        this.originalBackground = values.background();
    }

    public Target target() { return target; }
    public void switchTo(Target target) { this.target = target == null ? Target.ACCENT : target; }
    public int argb() { return target == Target.ACCENT ? 0xFF000000 | values.themeRgb() : values.background(); }
    public int rgb() { return argb() & 0xFFFFFF; }
    public int alpha() { return (argb() >>> 24) & 255; }

    public void setRgb(int rgb) {
        if (target == Target.ACCENT) values.applyTheme(rgb);
        else values.applyBackground((values.background() & 0xFF000000) | (rgb & 0xFFFFFF));
    }

    public void setAlpha(int alpha) {
        if (target != Target.BACKGROUND) return;
        int bounded = Math.max(0, Math.min(255, alpha));
        values.applyBackground((bounded << 24) | (values.background() & 0xFFFFFF));
    }

     
    public boolean applyHex(String text) {
        if (text == null) return false;
        String normalized = text.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        try {
            if (target == Target.ACCENT && normalized.matches("[0-9a-fA-F]{6}")) {
                values.applyTheme(Integer.parseInt(normalized, 16));
                return true;
            }
            if (target == Target.BACKGROUND) {
                if (normalized.matches("[0-9a-fA-F]{8}")) {
                    values.applyBackground((int) Long.parseLong(normalized, 16));
                    return true;
                }
                if (normalized.matches("[0-9a-fA-F]{6}")) {
                    setRgb(Integer.parseInt(normalized, 16));
                    return true;
                }
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    public String hex() {
        return target == Target.ACCENT ? String.format("#%06X", values.themeRgb())
                : String.format("#%08X", values.background());
    }

    public void resetCurrent() {
        if (target == Target.ACCENT) values.applyTheme(MusicPlayerUiConfig.Values.DEFAULT_THEME_RGB);
        else values.applyBackground(MusicPlayerUiConfig.Values.DEFAULT_BACKGROUND_ARGB);
    }

    public void cancel() {
        values.applyTheme(originalAccent);
        values.applyBackground(originalBackground);
    }
}
