package com.mengsama.mod.mengsamanetmusic.api;

 
public final class NetEaseAccountSession {
    private static volatile String cookie;
    private static long revision;
    private NetEaseAccountSession() {}
    public static String cookie() {
        String local = cookie;
        if (local != null) return local;
        try { return com.mengsama.mod.mengsamanetmusic.config.ConfigManager.getNetEaseCookie(); }
        catch (RuntimeException ignored) { return ""; }
    }
    public static synchronized long revision() { return revision; }
    public static synchronized void set(String value) {
        if (!safe(value)) throw new IllegalArgumentException("Invalid local login credential");
        cookie = value; revision++;
    }
    public static synchronized boolean commit(long expectedRevision, String value) {
        if (revision != expectedRevision || value == null || value.isBlank() || !safe(value)) return false;
        set(value); return true;
    }
    public static synchronized void clear() { cookie = ""; revision++; }
    private static boolean safe(String value) {
        return value != null && value.length() <= 16384 && value.chars().noneMatch(c -> c < 32 || c == 127);
    }
}
