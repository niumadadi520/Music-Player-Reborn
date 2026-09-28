package com.mengsama.mod.mengsamanetmusic.client.audio;
import javax.sound.sampled.AudioFormat;

record PcmFormatPlan(AudioFormat decoded, AudioFormat playback) {
    static PcmFormatPlan forSource(AudioFormat source) {
        float rate = source.getSampleRate();
        int channels = source.getChannels();
        if (!(rate > 0) || !Float.isFinite(rate) || channels < 1) throw new IllegalArgumentException("Invalid audio format");
        int bits = source.getSampleSizeInBits();
        if (bits < 1) bits = 16;
        AudioFormat decoded = new AudioFormat(rate, bits, channels, true, false);
        AudioFormat playback = new AudioFormat(rate, 16, Math.min(channels, 2), true, false);
        return new PcmFormatPlan(decoded, playback);
    }
}
