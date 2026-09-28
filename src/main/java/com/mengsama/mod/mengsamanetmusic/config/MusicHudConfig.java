package com.mengsama.mod.mengsamanetmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

 
public final class MusicHudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("mengsamanetmusic-hud.json");
    private static Data data = new Data();

    private MusicHudConfig() {}

    public static Data get() { return data; }

    public static void load() {
        try {
            if (Files.isRegularFile(FILE)) {
                JsonObject json = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
                Data loaded = GSON.fromJson(json, Data.class);
                if (loaded != null) {
                    loaded.migrateLegacyPalette(json == null || !json.has("skinVersion"));
                    data = loaded;
                }
            }
            data.sanitize();
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.warn("Failed to load client HUD config", e);
            data = new Data();
        }
    }

    public static void save() {
        data.sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MengSamaNetMusic.LOGGER.warn("Failed to save client HUD config", e);
        }
    }

    public static void reset() { data = new Data(); }

    public static Data copy() { return new Data(data); }

    public static void restore(Data snapshot) { data = new Data(snapshot); }

    public static final class Data {
        public Data() {}

        public Data(Data other) {
            x = other.x; y = other.y; scale = other.scale; opacity = other.opacity;
            backgroundColor = other.backgroundColor; textColor = other.textColor;
            secondaryTextColor = other.secondaryTextColor;
            showCover = other.showCover; showTitle = other.showTitle; showArtist = other.showArtist;
            showProgress = other.showProgress; showLyrics = other.showLyrics;
            skinVersion = other.skinVersion;
        }
        public int x = 10;
        public int y = 10;
        public float scale = 1.0F;
        public float opacity = 0.85F;
        public int skinVersion = 1;
        public int backgroundColor = 0xFFFFFFFF;
        public int textColor = 0xFF45302F;
        public int secondaryTextColor = 0xFF795D54;
        public boolean showCover = true;
        public boolean showTitle = true;
        public boolean showArtist = true;
        public boolean showProgress = true;
        public boolean showLyrics = true;

         
        void migrateLegacyPalette(boolean legacy) {
            if (legacy) {
                if (backgroundColor == 0xCC101018) backgroundColor = 0xFFFFFFFF;
                if (textColor == 0xFFFFFFFF) textColor = 0xFF45302F;
                if (secondaryTextColor == 0xFFB8B8C8) secondaryTextColor = 0xFF795D54;
            }
            skinVersion = 1;
        }

        public void sanitize() {
            scale = Float.isFinite(scale) ? Math.max(0.5F, Math.min(2.0F, scale)) : 1F;
            opacity = Float.isFinite(opacity) ? Math.max(0.15F, Math.min(1.0F, opacity)) : 0.85F;
            x = Math.max(0, x);
            y = Math.max(0, y);
        }
    }
}
