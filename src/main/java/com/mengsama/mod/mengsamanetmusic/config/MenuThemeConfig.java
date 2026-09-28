package com.mengsama.mod.mengsamanetmusic.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.gui.theme.MenuTheme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

 
public final class MenuThemeConfig {
    private static volatile MenuTheme current = MenuTheme.ROSEWOOD;
    private MenuThemeConfig() {}
    public static MenuTheme get() { return current; }
    public static Path path() { return MusicPlayerUiConfig.path().resolveSibling("menu_theme.json"); }
    public static void load() { current = read(path()); }
    public static MenuTheme read(Path file) {
        try {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return MenuTheme.fromId(json.get("theme").getAsString());
        } catch (IOException | RuntimeException invalid) { return MenuTheme.ROSEWOOD; }
    }
    public static synchronized void select(MenuTheme theme) throws IOException {
        save(path(), theme);
        current = theme;
    }
    public static void save(Path file, MenuTheme theme) throws IOException {
        java.util.Objects.requireNonNull(theme);
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path pending = Files.createTempFile(parent, "menu-theme-", ".tmp");
        try {
            JsonObject json = new JsonObject();
            json.addProperty("version", 1); json.addProperty("theme", theme.id);
            Files.writeString(pending, json.toString(), StandardCharsets.UTF_8);
            try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(pending); }
    }
}
