package com.mengsama.mod.mengsamanetmusic.client.audio;

 
public final class ClientMusicVolume {
    private static volatile int percent = 100;
    private static int lastAudiblePercent = 100;

    private ClientMusicVolume() {}

    public static int percent() { return percent; }
    public static float gain() { return percent / 100F; }

    public static synchronized void setPercent(int value) {
        percent = Math.max(0, Math.min(100, value));
        if (percent > 0) lastAudiblePercent = percent;
    }

    public static synchronized void toggleMute() {
        setPercent(percent == 0 ? lastAudiblePercent : 0);
    }

    public static synchronized void increaseStep() {
        setPercent(percent >= 100 ? 25 : Math.min(100, percent + 25));
    }
}
