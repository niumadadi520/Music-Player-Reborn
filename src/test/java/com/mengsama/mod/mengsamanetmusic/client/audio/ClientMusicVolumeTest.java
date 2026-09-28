package com.mengsama.mod.mengsamanetmusic.client.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientMusicVolumeTest {
    @AfterEach void restoreVolume() { ClientMusicVolume.setPercent(100); }

    @Test void muteRestoresTheLastNonzeroLevel() {
        ClientMusicVolume.setPercent(35);
        ClientMusicVolume.toggleMute();
        assertEquals(0, ClientMusicVolume.percent());
        assertEquals(0F, ClientMusicVolume.gain());
        ClientMusicVolume.toggleMute();
        assertEquals(35, ClientMusicVolume.percent());
        assertEquals(.35F, ClientMusicVolume.gain());
    }

    @Test void wheelValuesCannotAmplifyOrBecomeNegative() {
        ClientMusicVolume.setPercent(Integer.MAX_VALUE);
        assertEquals(1F, ClientMusicVolume.gain());
        ClientMusicVolume.setPercent(Integer.MIN_VALUE);
        assertEquals(0F, ClientMusicVolume.gain());
    }

    @Test void rightClickCanRaiseVolumeAndCycleAtTheMaximum() {
        ClientMusicVolume.setPercent(0);
        ClientMusicVolume.increaseStep();
        assertEquals(25, ClientMusicVolume.percent());
        ClientMusicVolume.setPercent(90);
        ClientMusicVolume.increaseStep();
        assertEquals(100, ClientMusicVolume.percent());
        ClientMusicVolume.increaseStep();
        assertEquals(25, ClientMusicVolume.percent());
    }
}
