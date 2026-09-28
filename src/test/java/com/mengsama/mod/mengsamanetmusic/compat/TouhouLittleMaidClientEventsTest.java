package com.mengsama.mod.mengsamanetmusic.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TouhouLittleMaidClientEventsTest {
    @Test
    void transferButtonUsesActualContainerRightEdgeAndGap() {
        int leftPos = 137;
        int imageWidth = 256;

        int x = MaidGuiLayout.buttonX(leftPos, imageWidth, 6);

        assertEquals(399, x);
        assertTrue(x > leftPos + imageWidth);
    }

    @Test
    void transferButtonPositionTracksDynamicLeftPosAndImageWidth() {
        assertEquals(286, MaidGuiLayout.buttonX(80, 200, 6));
        assertEquals(442, MaidGuiLayout.buttonX(120, 316, 6));
    }

    @Test
    void transferButtonAlignsWithNearestNativeRightColumn() {
        assertEquals(388, MaidGuiLayout.alignedButtonX(137, 246, 6, java.util.List.of(410, 388, 90)));
    }

    @Test
    void transferButtonFallsBackWhenNativeRightColumnIsUnavailable() {
        assertEquals(389, MaidGuiLayout.alignedButtonX(137, 246, 6, java.util.List.of(90, 120)));
    }

    @Test
    void tlm153EmptyBackpackInitializationUsesSafeFallbackWithoutNativeWidgets() {
        assertEquals(389, MaidGuiLayout.alignedButtonX(137, 246, 6, java.util.List.of()));
        assertEquals(389, MaidGuiLayout.alignedButtonX(137, 246, 6, null));
    }

    @Test
    void clientMixinConfigurationDoesNotRequireScreenAccessor() throws IOException {
        String mixins = Files.readString(Path.of("src/main/resources/mengsamanetmusic.mixins.json"));
        assertFalse(mixins.contains("ScreenAccessor"));
    }
}
