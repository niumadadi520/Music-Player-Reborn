package com.mengsama.mod.mengsamanetmusic.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNetMusicSoundTest {
    @Test
    void linearAttenuationReachesZeroAtMaximumDistance() {
        assertEquals(1f, AudioDistanceUtil.linearVolume(0, 96), 0.0001f);
        assertEquals(0.5f, AudioDistanceUtil.linearVolume(48, 96), 0.0001f);
        assertEquals(0f, AudioDistanceUtil.linearVolume(96, 96), 0.0001f);
        assertEquals(0f, AudioDistanceUtil.linearVolume(120, 96), 0.0001f);
    }

    @Test
    void volumeChangesSmoothlyButMutesImmediatelyOutsideRange() {
        assertEquals(0.2f, AudioDistanceUtil.smoothVolume(0f, 1f, 0.2f), 0.0001f);
        assertEquals(0.84f, AudioDistanceUtil.smoothVolume(0.8f, 1f, 0.2f), 0.0001f);
        assertEquals(0f, AudioDistanceUtil.smoothVolume(0.8f, 0f, 0.2f), 0.0001f);
    }

    @Test
    void realSoundClassUsesAudibleInitialVolumeBeforeFirstTick() {
        assertEquals(1f, PlayerNetMusicSound.initialVolume(0), 0.0001f);
        assertEquals(0.75f, PlayerNetMusicSound.initialVolume(24), 0.0001f);
        assertEquals(0f, PlayerNetMusicSound.initialVolume(96), 0.0001f);
    }
}
