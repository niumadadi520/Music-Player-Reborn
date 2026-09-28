package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeColorEditorTest {
    @Test void switchingTargetsLoadsAndEditsIndependentValues() {
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.defaults();
        values.applyTheme(0x123456);
        values.applyBackground(0x78112233);
        ThemeColorEditor editor = new ThemeColorEditor(values);

        assertEquals(ThemeColorEditor.Target.ACCENT, editor.target());
        assertEquals(0x123456, editor.rgb());
        assertEquals("#123456", editor.hex());

        editor.switchTo(ThemeColorEditor.Target.BACKGROUND);
        assertEquals(0x78112233, editor.argb());
        assertEquals(0x78, editor.alpha());
        assertEquals("#78112233", editor.hex());
        editor.setRgb(0xAABBCC);
        assertEquals(0x78AABBCC, values.background());
        assertEquals(0x123456, values.themeRgb());
    }

    @Test void parsesBackgroundArgbAndRgbWhilePreservingCurrentAlpha() {
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.defaults();
        ThemeColorEditor editor = new ThemeColorEditor(values);
        editor.switchTo(ThemeColorEditor.Target.BACKGROUND);

        assertTrue(editor.applyHex("#40112233"));
        assertEquals(0x40112233, values.background());
        assertTrue(editor.applyHex("AABBCC"));
        assertEquals(0x40AABBCC, values.background());
        assertFalse(editor.applyHex("#12345"));
        assertEquals(0x40AABBCC, values.background());
    }

    @Test void alphaIsClampedAndDoesNotAffectAccent() {
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.defaults();
        ThemeColorEditor editor = new ThemeColorEditor(values);
        editor.setAlpha(0);
        assertEquals(0xFF, editor.alpha());

        editor.switchTo(ThemeColorEditor.Target.BACKGROUND);
        editor.setAlpha(-5);
        assertEquals(0, editor.alpha());
        editor.setAlpha(999);
        assertEquals(255, editor.alpha());
    }

    @Test void resetAffectsOnlyCurrentTargetAndCancelRollsBackBoth() {
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.defaults();
        values.applyTheme(0x102030);
        values.applyBackground(0x44556677);
        ThemeColorEditor editor = new ThemeColorEditor(values);

        editor.setRgb(0xABCDEF);
        editor.switchTo(ThemeColorEditor.Target.BACKGROUND);
        editor.resetCurrent();
        assertEquals(0xABCDEF, values.themeRgb());
        assertEquals(MusicPlayerUiConfig.Values.DEFAULT_BACKGROUND_ARGB, values.background());

        editor.cancel();
        assertEquals(0x102030, values.themeRgb());
        assertEquals(0x44556677, values.background());
    }
}
