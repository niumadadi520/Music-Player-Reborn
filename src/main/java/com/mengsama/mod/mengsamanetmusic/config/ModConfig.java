package com.mengsama.mod.mengsamanetmusic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.net.Proxy;

public class ModConfig {

    public static ModConfigSpec.BooleanValue ENABLE_STEREO;
    public static ModConfigSpec.EnumValue<Proxy.Type> PROXY_TYPE;
    public static ModConfigSpec.ConfigValue<String> PROXY_ADDRESS;

    public static ModConfigSpec.BooleanValue ENABLE_PLAYER_LYRICS;
    public static ModConfigSpec.BooleanValue ENABLE_MAID_LYRICS;
    public static ModConfigSpec.ConfigValue<String> ORIGINAL_PLAYER_LYRICS_COLOR;
    public static ModConfigSpec.ConfigValue<String> TRANSLATED_PLAYER_LYRICS_COLOR;
    public static ModConfigSpec.ConfigValue<String> ORIGINAL_MAID_LYRICS_COLOR;
    public static ModConfigSpec.ConfigValue<String> TRANSLATED_MAID_LYRICS_COLOR;

    public static ModConfigSpec.ConfigValue<String> NET_EASE_COOKIE;
    public static ModConfigSpec.ConfigValue<String> NET_EASE_MUSIC_LEVEL;

    public static ModConfigSpec.ConfigValue<String> MUSIC_PROVIDER;
    public static ModConfigSpec.ConfigValue<String> QQ_VIP_COOKIE;
     
    public static ModConfigSpec.ConfigValue<String> APPLE_MUSICKIT_TOKEN;
    public static ModConfigSpec.ConfigValue<String> MUSIC_QUALITY;

    public static ModConfigSpec.BooleanValue DEBUG_MODE;
    public static ModConfigSpec.BooleanValue ENABLE_MUSIC_HUD;
    public static ModConfigSpec.IntValue MUSIC_HUD_X;
    public static ModConfigSpec.IntValue MUSIC_HUD_Y;

    private static ModConfigSpec spec;
    public static synchronized ModConfigSpec init() {
        if (spec != null) return spec;
        Schema schema = new Schema();
        ENABLE_STEREO = schema.flag("general", "EnableStereo", true);
        PROXY_TYPE = schema.builder.defineEnum(java.util.List.of("general", "ProxyType"), Proxy.Type.DIRECT);
        PROXY_ADDRESS = schema.text("general", "ProxyAddress", "");
        DEBUG_MODE = schema.flag("general", "DebugMode", false);
        ENABLE_PLAYER_LYRICS = schema.flag("lyrics", "EnablePlayerLyrics", true);
        ENABLE_MAID_LYRICS = schema.flag("lyrics", "EnableMaidLyrics", true);
        ORIGINAL_PLAYER_LYRICS_COLOR = schema.text("lyrics", "OriginalPlayerLyricsColor", "#FFAAAAAA");
        TRANSLATED_PLAYER_LYRICS_COLOR = schema.text("lyrics", "TranslatedPlayerLyricsColor", "#FFFFFFFF");
        ORIGINAL_MAID_LYRICS_COLOR = schema.text("lyrics", "OriginalMaidLyricsColor", "#FFAAAAAA");
        TRANSLATED_MAID_LYRICS_COLOR = schema.text("lyrics", "TranslatedMaidLyricsColor", "#FF000000");
        NET_EASE_COOKIE = schema.text("netease", "NetEaseCookie", "");
        NET_EASE_MUSIC_LEVEL = schema.text("netease", "NetEaseMusicLevel", "standard");
        MUSIC_PROVIDER = schema.text("provider", "MusicProvider", "netease");
        QQ_VIP_COOKIE = schema.text("provider", "QQVipCookie", "");
        APPLE_MUSICKIT_TOKEN = schema.text("provider", "AppleMusicKitToken", "");
        MUSIC_QUALITY = schema.text("provider", "MusicQuality", "standard");
        ENABLE_MUSIC_HUD = schema.flag("music_hud", "EnableMusicHUD", true);
        MUSIC_HUD_X = schema.coordinate("MusicHUDX", 1920);
        MUSIC_HUD_Y = schema.coordinate("MusicHUDY", 1080);
        return spec = schema.builder.build();
    }
    public static void save() { if (spec != null && spec.isLoaded()) spec.save(); }
    private static final class Schema {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ModConfigSpec.BooleanValue flag(String section, String key, boolean initial) {
            return builder.define(java.util.List.of(section, key), initial);
        }
        ModConfigSpec.ConfigValue<String> text(String section, String key, String initial) {
            return builder.define(java.util.List.of(section, key), initial);
        }
        ModConfigSpec.IntValue coordinate(String key, int bound) {
            return builder.defineInRange(java.util.List.of("music_hud", key), 5, 0, bound);
        }
    }
}
