package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class VoiceAudioMathTest {
    @Test void convertsSignedLittleEndianMicrophoneSamples() {
        byte[] pcm = new byte[1920];
        pcm[0] = -1; pcm[1] = 127; pcm[2] = 0; pcm[3] = -128;
        pcm[4] = -1; pcm[5] = -1; pcm[6] = 0x34; pcm[7] = 0x12;
        short[] samples = VoiceAudioMath.fromLittleEndian(pcm);
        assertEquals(960, samples.length);
        assertArrayEquals(new short[]{32767, -32768, -1, 0x1234}, Arrays.copyOf(samples, 4));
    }

    @Test void speakerVolumePreservesPolarityAndNeverOverflows() {
        short[] input = new short[960];
        input[0] = 32767; input[1] = -32768; input[2] = 1000; input[3] = -1000;
        assertArrayEquals(new short[]{16383, -16384, 500, -500}, Arrays.copyOf(VoiceAudioMath.withVolume(input, 50), 4));
        assertArrayEquals(input, VoiceAudioMath.withVolume(input, 100));
        assertArrayEquals(input, VoiceAudioMath.withVolume(input, Integer.MAX_VALUE));
        assertArrayEquals(new short[960], VoiceAudioMath.withVolume(input, 0));
        assertArrayEquals(new short[960], VoiceAudioMath.withVolume(input, Integer.MIN_VALUE));
        assertEquals(32767, input[0], "Applying speaker volume must not mutate the shared input frame");
    }

    @Test void malformedPcmFramesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VoiceAudioMath.fromLittleEndian(new byte[1919]));
        assertThrows(IllegalArgumentException.class, () -> VoiceAudioMath.fromLittleEndian(new byte[1921]));
        assertThrows(IllegalArgumentException.class, () -> VoiceAudioMath.withVolume(new short[959], 50));
    }

    @Test void networkFrameBudgetRejectsEmptyAndOversizedPackets() {
        assertFalse(VoiceAudioMath.validPacket(null));
        assertFalse(VoiceAudioMath.validPacket(new byte[0]));
        assertTrue(VoiceAudioMath.validPacket(new byte[1]));
        assertTrue(VoiceAudioMath.validPacket(new byte[2048]));
        assertFalse(VoiceAudioMath.validPacket(new byte[2049]));
    }
}
