package com.mengsama.mod.mengsamanetmusic.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerUiConfigTest {
    @Test void migratesOnlyTheFormerDefaultControlRowWithoutRewritingTheInputOrCustomStyle() {
        var json = JsonParser.parseString("""
                {"screenWidth":420,"screenHeight":440,"backgroundColor":"#80445566","backgroundFile":"my_skin.png",
                 "buttonPositions":{"block.play_pause":{"x":123,"y":244},
                                    "portable_item.stop":{"x":88,"y":280},
                                    "block.search":{"x":90,"y":244}}}
                """).getAsJsonObject();
        String originalJson = json.toString();
        var values = MusicPlayerUiConfig.Values.from(json);
        assertEquals(352, values.buttonPositions.get("block.play_pause").y);
        assertEquals(123, values.buttonPositions.get("block.play_pause").x);
        assertEquals(280, values.buttonPositions.get("portable_item.stop").y);
        assertEquals(244, values.buttonPositions.get("block.search").y);
        assertEquals(420, values.screenWidth);
        assertEquals(440, values.screenHeight);
        assertEquals("#80445566", values.backgroundColor);
        assertEquals("my_skin.png", values.backgroundFile);
        assertEquals(2, values.layoutVersion);
        assertEquals(originalJson, json.toString());
    }

    @Test void versionTwoDoesNotRemigrateAnIntentionallySavedControlAtTheOldAnchor() {
        var json = JsonParser.parseString("""
                {"layoutVersion":2,"buttonPositions":{"block.play_pause":{"x":100,"y":244}}}
                """).getAsJsonObject();
        var values = MusicPlayerUiConfig.Values.from(json);
        assertEquals(244, values.buttonPositions.get("block.play_pause").y);
        assertEquals(2, values.copy().layoutVersion);
    }

    @Test void migratesLegacyAccentAndSerializesBoundedRgbTheme() {
        var json = JsonParser.parseString("{\"accentColor\":\"#FF123456\",\"screenWidth\":9999}").getAsJsonObject();
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.from(json);
        assertEquals(0x123456, values.themeRgb());
        assertEquals(640, values.screenWidth);
        values.applyTheme(0x1ABCDEF);
        assertEquals(0xABCDEF, values.themeRgb());
        assertEquals("#ABCDEF", values.themeColor);
        assertEquals(0xFFABCDEF, values.accent());
    }

    @Test void invalidThemeFallsBackAndContrastStaysReadable() {
        var values = MusicPlayerUiConfig.Values.from(JsonParser.parseString("{\"themeColor\":\"#GGGGGG\"}").getAsJsonObject());
        assertEquals(0x7C6FFF, values.themeRgb());
        values.applyTheme(0xFFFFFF);
        assertEquals(0xFF101018, values.primaryText());
        values.applyTheme(0x000000);
        assertEquals(0xFFFFFFFF, values.primaryText());
    }

    @Test void malformedBackgroundSafelyFallsBackAndSurfaceAlphaTracksIt() {
        var values = MusicPlayerUiConfig.Values.from(JsonParser.parseString(
                "{\"backgroundColor\":\"broken\",\"panelColor\":null}").getAsJsonObject());
        assertEquals(MusicPlayerUiConfig.Values.DEFAULT_BACKGROUND_ARGB, values.background());

        values.applyBackground(0x80112233);
        assertEquals(0x69112233, values.panelSurface());
        assertEquals(0x4F112233, values.listSurface());
        assertEquals(0x69112233, values.listHoverSurface());
        assertEquals(0x76112233, values.popupSurface());
    }

    @Test void copiesAndRestoresBothIndependentColors() {
        MusicPlayerUiConfig.Values values = MusicPlayerUiConfig.Values.defaults();
        values.applyTheme(0x123456);
        values.applyBackground(0x78112233);
        MusicPlayerUiConfig.Values snapshot = values.copy();
        values.applyTheme(0xABCDEF);
        values.applyBackground(0xFFFFFFFF);
        values.restoreColors(snapshot);
        assertEquals(0x123456, values.themeRgb());
        assertEquals(0x78112233, values.background());
    }
}
