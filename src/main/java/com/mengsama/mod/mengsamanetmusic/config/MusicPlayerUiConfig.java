package com.mengsama.mod.mengsamanetmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

 
@OnlyIn(Dist.CLIENT)
public final class MusicPlayerUiConfig {
    public static final String SUBDIRECTORY = "mengsamanetmusic/ui";
    public static final String FILE_NAME = "music_player_ui.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Values current = Values.defaults();

    private MusicPlayerUiConfig() {}

    public static Values get() {
        return current;
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(SUBDIRECTORY).resolve(FILE_NAME);
    }

    public static synchronized void load() {
        MenuThemeConfig.load();
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                writeDefaults(path);
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                current = Values.from(json);
            }
            MengSamaNetMusic.LOGGER.info("Loaded client music player UI config: {}", path);
        } catch (Exception ex) {
            current = Values.defaults();
            MengSamaNetMusic.LOGGER.error("Invalid client music player UI config {}; using safe defaults", path, ex);
        }
    }

    private static void writeDefaults(Path path) throws IOException {
        write(path, Values.defaults());
    }

    public static synchronized void save() throws IOException {
        Path path = path();
        Files.createDirectories(path.getParent());
        write(path, current);
    }

    public static synchronized void reset() throws IOException {
        current = Values.defaults();
        save();
    }

    private static void write(Path path, Values values) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(values, writer);
        }
    }

    public static final class Values {
        private transient boolean colorCacheReady;
        private transient String cachedBackgroundText;
        private transient String cachedThemeText;
        private transient int cachedBackground;
        private transient int cachedPanel;
        private transient int cachedList;
        private transient int cachedListHover;
        private transient int cachedPopup;
        private transient int cachedAccent;
        private transient int cachedSecondaryAccent;
        private transient int cachedPrimaryText;
        private transient int cachedSecondaryText;
        private transient int cachedBorder;

        public int screenWidth = 396;
        public int screenHeight = 408;
         
        public int layoutVersion = 2;
        public int horizontalMargin = 14;
        public int controlGap = 6;
        public int sourceButtonWidth = 64;
        public int searchBoxWidth = 154;
        public int searchButtonWidth = 52;
        public int qqLoginButtonWidth = 64;
        public int searchResultRowHeight = 24;
        public int playlistRowHeight = 24;
        public int lyricRowHeight = 18;
        public static final int DEFAULT_THEME_RGB = 0x7C6FFF;
        public static final int DEFAULT_BACKGROUND_ARGB = 0x66FFC5FF;
        public String backgroundColor = "#66FFC5FF";
         
        public String backgroundFile = "";
        public String panelColor = "#99181830";
         
        public String themeColor = "#7C6FFF";
        public String accentColor = "#FF7C6FFF";
        public String secondaryAccentColor = "#FF4ECDC4";
        public String primaryTextColor = "#FFFFFFFF";
        public String secondaryTextColor = "#FFB8B8CC";
        public String borderColor = "#FF2A2A45";
         
        public Map<String, Position> buttonPositions = new LinkedHashMap<>();

        public static Values defaults() {
            return new Values();
        }

        static Values from(JsonObject json) {
            Values d = defaults();
            Values v = new Values();
            v.screenWidth = integer(json, "screenWidth", d.screenWidth, 300, 640);
            v.screenHeight = integer(json, "screenHeight", d.screenHeight, 400, 560);
            v.horizontalMargin = integer(json, "horizontalMargin", d.horizontalMargin, 8, 32);
            v.controlGap = integer(json, "controlGap", d.controlGap, 2, 16);
            v.sourceButtonWidth = integer(json, "sourceButtonWidth", d.sourceButtonWidth, 48, 120);
            v.searchBoxWidth = integer(json, "searchBoxWidth", d.searchBoxWidth, 80, 360);
            v.searchButtonWidth = integer(json, "searchButtonWidth", d.searchButtonWidth, 42, 100);
            v.qqLoginButtonWidth = integer(json, "qqLoginButtonWidth", d.qqLoginButtonWidth, 52, 120);
            v.searchResultRowHeight = integer(json, "searchResultRowHeight", d.searchResultRowHeight, 20, 40);
            v.playlistRowHeight = integer(json, "playlistRowHeight", d.playlistRowHeight, 20, 40);
            v.lyricRowHeight = integer(json, "lyricRowHeight", d.lyricRowHeight, 14, 32);
            v.backgroundColor = colorText(json, "backgroundColor", d.backgroundColor);
            v.backgroundFile = safeFileName(json, "backgroundFile");
             
            if ("#F60A0A12".equalsIgnoreCase(v.backgroundColor)) v.backgroundColor = d.backgroundColor;
            v.panelColor = colorText(json, "panelColor", d.panelColor);
            v.accentColor = colorText(json, "accentColor", d.accentColor);
            v.themeColor = rgbText(json, "themeColor", "#" + v.accentColor.substring(3));
            v.applyTheme(v.themeRgb());
            v.secondaryAccentColor = colorText(json, "secondaryAccentColor", d.secondaryAccentColor);
            v.primaryTextColor = colorText(json, "primaryTextColor", d.primaryTextColor);
            v.secondaryTextColor = colorText(json, "secondaryTextColor", d.secondaryTextColor);
            v.borderColor = colorText(json, "borderColor", d.borderColor);
            v.buttonPositions = positions(json, v.screenWidth, v.screenHeight);
            if (integer(json, "layoutVersion", 1, 1, 2) < 2) {
                v.migrateLegacyControlRow();
            }
            return v;
        }

        private void migrateLegacyControlRow() {
             
             
             
            for (String screen : new String[] {"portable_item", "block", "portable_block"}) {
                for (String id : new String[] {"previous", "play_pause", "stop", "next", "mode", "broadcast"}) {
                    Position p = buttonPositions.get(screen + "." + id);
                    if (p != null && p.y == 244) p.y = screenHeight - 88;
                }
            }
        }

        public Position position(String screenType, String buttonId, int defaultX, int defaultY, int width, int height) {
            String key = screenType + "." + buttonId;
            Position p = buttonPositions.get(key);
            int x = p == null ? defaultX : p.x;
            int y = p == null ? defaultY : p.y;
            Position clamped = new Position(clamp(x, 0, Math.max(0, screenWidth - width)),
                    clamp(y, 0, Math.max(0, screenHeight - height)));
            buttonPositions.put(key, clamped);
            return clamped;
        }

        public void setPosition(String screenType, String buttonId, int x, int y, int width, int height) {
            buttonPositions.put(screenType + "." + buttonId,
                    new Position(clamp(x, 0, Math.max(0, screenWidth - width)),
                            clamp(y, 0, Math.max(0, screenHeight - height))));
        }

        private void ensureColorCache() {
            if (colorCacheReady && java.util.Objects.equals(cachedBackgroundText, backgroundColor)
                    && java.util.Objects.equals(cachedThemeText, themeColor)) return;
            int bg = parseColor(backgroundColor, DEFAULT_BACKGROUND_ARGB);
            int theme = parseRgb(themeColor, 0x7C6FFF);
            cachedBackground = bg;
            cachedPanel = scaleAlpha(bg, 0.82F);
            cachedList = scaleAlpha(bg, 0.62F);
            cachedListHover = scaleAlpha(bg, 0.82F);
            cachedPopup = scaleAlpha(bg, 0.92F);
            cachedAccent = 0xFF000000 | theme;
            cachedSecondaryAccent = 0xFF000000 | blend(theme, 0xFFFFFF, 0.28F);
            cachedPrimaryText = contrastText(theme);
            cachedSecondaryText = (cachedPrimaryText & 0x00FFFFFF) | 0xCC000000;
            cachedBorder = 0xFF000000 | blend(theme, 0x000000, 0.45F);
            cachedBackgroundText = backgroundColor;
            cachedThemeText = themeColor;
            colorCacheReady = true;
        }
         
        public int background() { ensureColorCache(); return cachedBackground; }
        public int panelSurface() { ensureColorCache(); return cachedPanel; }
        public int listSurface() { ensureColorCache(); return cachedList; }
        public int listHoverSurface() { ensureColorCache(); return cachedListHover; }
        public int popupSurface() { ensureColorCache(); return cachedPopup; }
        public int panel() { return panelSurface(); }
        public int accent() { ensureColorCache(); return cachedAccent; }
        public int secondaryAccent() { ensureColorCache(); return cachedSecondaryAccent; }
        public int primaryText() { ensureColorCache(); return cachedPrimaryText; }
        public int secondaryText() { ensureColorCache(); return cachedSecondaryText; }
        public int border() { ensureColorCache(); return cachedBorder; }
        public int themeRgb() { return parseRgb(themeColor, 0x7C6FFF); }

        public void applyTheme(int rgb) {
            rgb &= 0xFFFFFF;
            colorCacheReady = false;
            themeColor = String.format("#%06X", rgb);
            accentColor = String.format("#FF%06X", rgb);
            secondaryAccentColor = String.format("#FF%06X", blend(rgb, 0xFFFFFF, 0.28F));
            primaryTextColor = String.format("#%08X", contrastText(rgb));
            secondaryTextColor = String.format("#%08X", (contrastText(rgb) & 0xFFFFFF) | 0xCC000000);
            borderColor = String.format("#FF%06X", blend(rgb, 0, 0.45F));
        }

        public void applyBackground(int argb) {
            colorCacheReady = false;
            backgroundColor = String.format("#%08X", argb);
        }

        public Values copy() {
            Values copy = new Values();
            copy.screenWidth = screenWidth; copy.screenHeight = screenHeight;
            copy.layoutVersion = layoutVersion;
            copy.horizontalMargin = horizontalMargin; copy.controlGap = controlGap;
            copy.sourceButtonWidth = sourceButtonWidth; copy.searchBoxWidth = searchBoxWidth;
            copy.searchButtonWidth = searchButtonWidth; copy.qqLoginButtonWidth = qqLoginButtonWidth;
            copy.searchResultRowHeight = searchResultRowHeight; copy.playlistRowHeight = playlistRowHeight;
            copy.lyricRowHeight = lyricRowHeight; copy.backgroundColor = backgroundColor;
            copy.backgroundFile = backgroundFile;
            copy.panelColor = panelColor; copy.themeColor = themeColor; copy.accentColor = accentColor;
            copy.secondaryAccentColor = secondaryAccentColor; copy.primaryTextColor = primaryTextColor;
            copy.secondaryTextColor = secondaryTextColor; copy.borderColor = borderColor;
            for (Map.Entry<String, Position> entry : buttonPositions.entrySet())
                copy.buttonPositions.put(entry.getKey(), new Position(entry.getValue().x, entry.getValue().y));
            return copy;
        }

        public void restoreColors(Values snapshot) {
            applyTheme(snapshot.themeRgb());
            applyBackground(snapshot.background());
        }

        public static int contrastText(int rgb) {
            double luminance = (0.2126 * ((rgb >>> 16) & 255) + 0.7152 * ((rgb >>> 8) & 255) + 0.0722 * (rgb & 255)) / 255D;
            return luminance > 0.55D ? 0xFF101018 : 0xFFFFFFFF;
        }

        private static int scaleAlpha(int argb, float factor) {
            int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 255) * factor)));
            return (alpha << 24) | (argb & 0xFFFFFF);
        }

        private static int blend(int a, int b, float ratio) {
            int r = Math.round(((a >>> 16) & 255) * (1 - ratio) + ((b >>> 16) & 255) * ratio);
            int g = Math.round(((a >>> 8) & 255) * (1 - ratio) + ((b >>> 8) & 255) * ratio);
            int bl = Math.round((a & 255) * (1 - ratio) + (b & 255) * ratio);
            return (r << 16) | (g << 8) | bl;
        }

        private static int integer(JsonObject json, String key, int fallback, int min, int max) {
            try {
                int value = json.has(key) ? json.get(key).getAsInt() : fallback;
                return Math.max(min, Math.min(max, value));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        private static Map<String, Position> positions(JsonObject json, int screenWidth, int screenHeight) {
            Map<String, Position> result = new LinkedHashMap<>();
            try {
                if (!json.has("buttonPositions") || !json.get("buttonPositions").isJsonObject()) return result;
                for (Map.Entry<String, com.google.gson.JsonElement> entry : json.getAsJsonObject("buttonPositions").entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject p = entry.getValue().getAsJsonObject();
                    int x = integer(p, "x", 0, 0, screenWidth);
                    int y = integer(p, "y", 0, 0, screenHeight);
                    result.put(entry.getKey(), new Position(x, y));
                }
            } catch (RuntimeException ignored) {}
            return result;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static String safeFileName(JsonObject json, String key) {
            try {
                String value = json.has(key) ? json.get(key).getAsString().trim() : "";
                if (!value.isEmpty() && Path.of(value).getNameCount() == 1 && !value.contains("..")) return value;
            } catch (RuntimeException ignored) {}
            return "";
        }

        private static String colorText(JsonObject json, String key, String fallback) {
            try {
                String value = json.has(key) ? json.get(key).getAsString() : fallback;
                return value.matches("#[0-9a-fA-F]{8}") ? value.toUpperCase() : fallback;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        private static String rgbText(JsonObject json, String key, String fallback) {
            try {
                String value = json.has(key) ? json.get(key).getAsString().trim() : fallback;
                if (value.matches("#[0-9a-fA-F]{6}")) return value.toUpperCase();
                if (value.matches("[0-9a-fA-F]{6}")) return ("#" + value).toUpperCase();
            } catch (RuntimeException ignored) {}
            return fallback.toUpperCase();
        }

        private static int parseRgb(String value, int fallback) {
            try { return Integer.parseInt(value.replace("#", ""), 16) & 0xFFFFFF; }
            catch (RuntimeException ignored) { return fallback; }
        }

        private static int parseColor(String value, int fallback) {
            try {
                return (int) Long.parseLong(value.substring(1), 16);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    public static final class Position {
        public int x;
        public int y;
        public Position(int x, int y) { this.x = x; this.y = y; }
    }
}
