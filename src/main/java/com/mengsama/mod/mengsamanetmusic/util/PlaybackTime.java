package com.mengsama.mod.mengsamanetmusic.util;

public final class PlaybackTime {
    private PlaybackTime() {}
    public static String clock(int seconds) {
        int value = Math.max(seconds, 0), minutes = value / 60, remainder = value % 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }
}
