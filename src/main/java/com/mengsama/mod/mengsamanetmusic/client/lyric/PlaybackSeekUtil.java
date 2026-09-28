package com.mengsama.mod.mengsamanetmusic.client.lyric;

 
public final class PlaybackSeekUtil {
    private PlaybackSeekUtil() {}

    public static int clampSecond(double fraction, int durationSeconds) {
        if (durationSeconds <= 0) return 0;
        return Math.max(0, Math.min(durationSeconds, (int) Math.round(clampFraction(fraction) * durationSeconds)));
    }

     
    public static int secondAtFraction(double fraction, int durationSeconds) {
        return clampSecond(fraction, durationSeconds);
    }

     
    public static int secondAtTick(int tick) {
        return Math.max(0, tick) / 20;
    }

    static double clampFraction(double fraction) {
        if (!Double.isFinite(fraction)) return fraction > 0D ? 1D : 0D;
        return Math.max(0D, Math.min(1D, fraction));
    }

}
