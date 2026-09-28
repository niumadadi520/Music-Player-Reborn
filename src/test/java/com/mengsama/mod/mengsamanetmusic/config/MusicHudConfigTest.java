package com.mengsama.mod.mengsamanetmusic.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicHudConfigTest {
    @Test void legacyDefaultsMigrateToReadableColorsWithoutChangingUserPreferences() {
        var old = new MusicHudConfig.Data();
        old.textColor = 0xFFFFFFFF;
        old.secondaryTextColor = 0xFFB8B8C8;
        old.backgroundColor = 0xCC101018;
        old.x = 75; old.scale = 1.5F; old.opacity = 0.6F; old.showCover = false;
        old.migrateLegacyPalette(true);
        assertEquals(0xFF45302F, old.textColor);
        assertEquals(0xFF795D54, old.secondaryTextColor);
        assertEquals(0xFFFFFFFF, old.backgroundColor);
        assertEquals(75, old.x);
        assertEquals(1.5F, old.scale);
        assertEquals(0.6F, old.opacity);
        assertFalse(old.showCover);
    }

    @Test void existingCustomColorsAndExplicitWhiteInTheNewFormatRemainExact() {
        var custom = new MusicHudConfig.Data();
        custom.textColor = 0x7F254D33;
        custom.secondaryTextColor = 0xCC7B4265;
        custom.backgroundColor = 0xAA353540;
        custom.migrateLegacyPalette(true);
        assertEquals(0x7F254D33, custom.textColor);
        assertEquals(0xCC7B4265, custom.secondaryTextColor);
        assertEquals(0xAA353540, custom.backgroundColor);
        custom.textColor = 0xFFFFFFFF;
        custom.secondaryTextColor = 0xFFB8B8C8;
        custom.migrateLegacyPalette(false);
        assertEquals(0xFFFFFFFF, custom.textColor);
        assertEquals(0xFFB8B8C8, custom.secondaryTextColor);
    }

    @Test void cancelSnapshotsPreserveAllDisplaySwitchesColorsAndVersion() {
        var edited = new MusicHudConfig.Data();
        edited.showCover = false; edited.showTitle = false; edited.showArtist = false;
        edited.showProgress = false; edited.showLyrics = false;
        edited.textColor = 0x00234567; edited.secondaryTextColor = 0x88432187;
        edited.backgroundColor = 0x00000000; edited.scale = 2; edited.opacity = 0.15F;
        var snapshot = new MusicHudConfig.Data(edited);
        edited.showLyrics = true; edited.textColor = 0xFFFFFFFF;
        assertFalse(snapshot.showCover || snapshot.showTitle || snapshot.showArtist || snapshot.showProgress || snapshot.showLyrics);
        assertEquals(0x00234567, snapshot.textColor);
        assertEquals(0x88432187, snapshot.secondaryTextColor);
        assertEquals(0x00000000, snapshot.backgroundColor);
        assertEquals(1, snapshot.skinVersion);
        assertEquals(2F, snapshot.scale);
        assertEquals(0.15F, snapshot.opacity);
    }

    @Test void malformedScaleAndOpacityDoNotProduceNaNRenderingOrBounds() {
        var config = new MusicHudConfig.Data();
        config.scale = Float.NaN; config.opacity = Float.POSITIVE_INFINITY;
        config.x = -100; config.y = -5;
        config.sanitize();
        assertEquals(1F, config.scale);
        assertEquals(0.85F, config.opacity);
        assertEquals(0, config.x); assertEquals(0, config.y);
        config.scale = 900; config.opacity = -1;
        config.sanitize();
        assertEquals(2F, config.scale);
        assertEquals(0.15F, config.opacity);
    }
}
