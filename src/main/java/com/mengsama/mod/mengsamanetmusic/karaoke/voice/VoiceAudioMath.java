package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

 
final class VoiceAudioMath {
    static final int SAMPLE_RATE = 48_000;
    static final int FRAME_SAMPLES = 960;
    static final int FRAME_BYTES = FRAME_SAMPLES * 2;
    static final int MAX_OPUS_BYTES = 2048;
    static final long MAX_AGE_NANOS = 100_000_000L;

    private VoiceAudioMath() {}

    static short[] fromLittleEndian(byte[] bytes) {
        if (bytes.length != FRAME_BYTES) throw new IllegalArgumentException("Expected one PCM frame");
        short[] samples = new short[FRAME_SAMPLES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((bytes[i * 2] & 255) | (bytes[i * 2 + 1] << 8));
        }
        return samples;
    }

    static short[] withVolume(short[] samples, int volume) {
        if (samples.length != FRAME_SAMPLES) throw new IllegalArgumentException("Expected one PCM frame");
        int clamped = Math.max(0, Math.min(100, volume));
        short[] output = new short[FRAME_SAMPLES];
        for (int i = 0; i < samples.length; i++) output[i] = (short) ((samples[i] * clamped) / 100);
        return output;
    }

    static boolean validPacket(byte[] opus) {
        return opus != null && opus.length > 0 && opus.length <= MAX_OPUS_BYTES;
    }
}
