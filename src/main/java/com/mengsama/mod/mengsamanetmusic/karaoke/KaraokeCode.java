package com.mengsama.mod.mengsamanetmusic.karaoke;

import java.util.Locale;
import java.util.UUID;

 
public final class KaraokeCode {
    private KaraokeCode() {}
    public static String format(UUID id) { return id.toString().toUpperCase(Locale.ROOT); }
    public static UUID parse(String raw) {
        if (raw == null || raw.length() > 64) return null;
        String value = raw.strip().replace("-", "");
        if (!value.matches("[0-9a-fA-F]{32}")) return null;
        try { return UUID.fromString(value.substring(0,8)+"-"+value.substring(8,12)+"-"+
                value.substring(12,16)+"-"+value.substring(16,20)+"-"+value.substring(20)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static int volume(int value) { return Math.max(0, Math.min(100, value)); }
    public static final int MAX_CONNECTIONS = 32;
    public static final float SPEAKER_RANGE = 32F;
}
