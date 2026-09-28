package com.mengsama.mod.mengsamanetmusic.config;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class ConfigManager {
    private ConfigManager() {
    }

    public static void reload() {
        try {

            String netEaseCookie = ModConfig.NET_EASE_COOKIE.get();
            if (netEaseCookie != null && !netEaseCookie.isBlank()) {
                MengSamaNetMusic.NET_EASE_API.setCookie(netEaseCookie);
                MengSamaNetMusic.LOGGER.info("NetEase Cookie reloaded (length={})", netEaseCookie.length());
            } else {
                MengSamaNetMusic.LOGGER.info("NetEase Cookie is empty, VIP songs may not play");
            }

            String qqCookie = ModConfig.QQ_VIP_COOKIE.get();
            if (qqCookie != null && !qqCookie.isBlank()) {
                MengSamaNetMusic.LOGGER.info("QQ Music Cookie configured (length={})", qqCookie.length());
            }
            String musicKitToken = ModConfig.APPLE_MUSICKIT_TOKEN.get();
            if (musicKitToken != null && !musicKitToken.isBlank()) {
                MengSamaNetMusic.LOGGER.info("Apple MusicKit token configured for compliant future catalog use (length={}); preview playback remains iTunes previewUrl only", musicKitToken.length());
            }

            MengSamaNetMusic.LOGGER.info("MengSamaNetMusic config reloaded successfully.");
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.error("Failed to reload config", e);
        }
    }

    public static void initCookies() {
        reload();
    }

    public static String getNetEaseCookie() {
        String cookie = ModConfig.NET_EASE_COOKIE.get();
        return (cookie != null) ? cookie : "";
    }

    public static String getQqCookie() {
        String cookie = ModConfig.QQ_VIP_COOKIE.get();
        return (cookie != null) ? cookie : "";
    }

    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER;
    }
}
