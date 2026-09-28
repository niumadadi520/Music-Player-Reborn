package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.NetEaseSearchResult;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.SongCoverCache;
import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class SongRowRenderer {
    static final int ACTION_WIDTH = 40;
    static final int ACTION_GAP = 4;
    static final int FAVORITE_WIDTH = 20;

    enum Action { NONE, ADD, DELETE }

    private SongRowRenderer() {}

    static void renderSearch(GuiGraphics g, Font font, NetEaseSearchResult song,
                             int x, int y, int w, int h, boolean hovered, boolean favorite) {
        renderCard(g, x, y, w, h, hovered, MusicPlayerSkin.listBackground(), MusicPlayerSkin.listHover(), song.getCoverUrl());
        renderColumns(g, font, song.getSongName(), song.getArtistName(), song.getAlbumName(),
                x, y, contentWidth(w), h, false, MusicPlayerSkin.primary());
        renderAction(g, font, x, y, w, h, Action.ADD, hovered && hitAction(x + w - ACTION_WIDTH + 1, y + h / 2, x, y, w, h));
        renderFavoriteToggle(g, font, x, y, w, h, favorite);
    }

    static void renderPlaylist(GuiGraphics g, Font font, SongInfo song, int realSlot,
                               int x, int y, int w, int h, boolean hovered, boolean current, boolean favorite) {
        int themedSelection = MusicPlayerSkin.selection();
        renderCard(g, x, y, w, h, hovered, current ? themedSelection : MusicPlayerSkin.listBackground(), MusicPlayerSkin.listHover(), song.preferredCoverUrl());
        String prefix = (current ? "▶ " : "") + (realSlot + 1) + ". ";
        String artist = song.artists == null || song.artists.isEmpty() ? "未知歌手" : String.join(", ", song.artists);
        renderColumns(g, font, prefix + SongRowLayout.value(song.songName, "未知歌曲"), artist,
                SongRowLayout.value(song.albumName, "未知专辑"), x, y, contentWidth(w), h, current,
                current ? MusicPlayerSkin.accent() : MusicPlayerSkin.primary());
        renderAction(g, font, x, y, w, h, Action.DELETE, hovered && hitAction(x + w - ACTION_WIDTH + 1, y + h / 2, x, y, w, h));
        renderFavoriteToggle(g, font, x, y, w, h, favorite);
    }

    static void renderFavorite(GuiGraphics g, Font font, SongInfo song, int x, int y, int w, int h,
                               boolean hovered) {
        renderAccount(g, font, song, x, y, w, h, hovered, true);
    }

    static void renderAccount(GuiGraphics g, Font font, SongInfo song, int x, int y, int w, int h,
                              boolean hovered, boolean favorite) {
        renderCard(g, x, y, w, h, hovered, MusicPlayerSkin.listBackground(), MusicPlayerSkin.listHover(), song.preferredCoverUrl());
        String artist = song.artists == null || song.artists.isEmpty() ? "未知歌手" : String.join(", ", song.artists);
        renderColumns(g, font, song.songName, artist, song.albumName, x, y, contentWidth(w), h, false, MusicPlayerSkin.primary());
        renderFavoriteToggle(g, font, x, y, w, h, favorite);
        renderAction(g, font, x, y, w, h, Action.ADD, hovered);
    }

    static boolean hitFavorite(double mouseX, double mouseY, int x, int y, int w, int h) {
        int left = x + w - ACTION_WIDTH - ACTION_GAP - FAVORITE_WIDTH;
        return mouseX >= left && mouseX < left + FAVORITE_WIDTH && mouseY >= y && mouseY < y + h;
    }

    private static void renderFavoriteToggle(GuiGraphics g, Font font, int x, int y, int w, int h, boolean favorite) {
        int bx = x + w - ACTION_WIDTH - ACTION_GAP - FAVORITE_WIDTH;
        int by = y + Math.max(1, (h - 16) / 2);
        var bounds=new RosewoodPlayerLayout.Rect(bx,by,FAVORITE_WIDTH,16);
        if(ThemeSkin.custom()){ThemeSkin.button(g,bounds,false,favorite,true);}
        else {g.fill(bx, by, bx + FAVORITE_WIDTH, by + 16, MusicPlayerSkin.inputBackground());
            g.renderOutline(bx, by, FAVORITE_WIDTH, 16, favorite ? MusicPlayerSkin.accent() : MusicPlayerSkin.edge());}
        String glyph = favorite ? "★" : "☆";
        g.drawString(font, glyph, bx + (FAVORITE_WIDTH - font.width(glyph)) / 2, by + 4,
                favorite ? MusicPlayerSkin.accent() : MusicPlayerSkin.secondary(), false);
    }

    static boolean hitAction(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x + w - ACTION_WIDTH && mouseX < x + w
                && mouseY >= y && mouseY < y + h;
    }

    private static int contentWidth(int rowWidth) {
        return Math.max(0, rowWidth - ACTION_WIDTH - FAVORITE_WIDTH - ACTION_GAP * 2);
    }

    private static void renderAction(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     Action action, boolean hovered) {
        int bx = x + w - ACTION_WIDTH;
        int by = y + Math.max(1, (h - 16) / 2);
        int color = hovered ? MusicPlayerSkin.listHover() : MusicPlayerSkin.inputBackground();
        var bounds=new RosewoodPlayerLayout.Rect(bx,by,ACTION_WIDTH,16);
        if(ThemeSkin.custom()){ThemeSkin.button(g,bounds,hovered,false,true);}
        else {g.fill(bx, by, bx + ACTION_WIDTH, by + 16, color);
            g.renderOutline(bx, by, ACTION_WIDTH, 16, hovered ? MusicPlayerSkin.accent() : MusicPlayerSkin.edge());}
        String key = action == Action.ADD ? "gui.mengsamanetmusic.music_player.add" : "gui.mengsamanetmusic.music_player.delete";
        String text = net.minecraft.network.chat.Component.translatable(key).getString();
        int tw = font.width(text);
        g.drawString(font, text, bx + Math.max(2, (ACTION_WIDTH - tw) / 2), by + 4, MusicPlayerSkin.primary(), false);
    }



    private static void renderColumns(GuiGraphics g, Font font, String title, String artist, String album,
                                      int x, int y, int w, int h, boolean current, int titleColor) {
        int cover = Math.max(0, Math.min(20, h - 4));
        int tx = x + cover + 6;
        renderTextColumns(g, font, title, artist, album, tx, y, Math.max(0, w - (tx - x) - 6), h, titleColor);
    }

    private static void renderTextColumns(GuiGraphics g, Font font, String title, String artist, String album,
                                          int x, int y, int w, int h, int titleColor) {
        title = SongRowLayout.value(title, "未知歌曲");
        artist = SongRowLayout.value(artist, "未知歌手");
        album = SongRowLayout.value(album, "未知专辑");
        SongRowLayout.Columns columns = SongRowLayout.allocate(w, font.width(title), font.width(artist), font.width(album));
        int ty = y + Math.max(0, (h - font.lineHeight - 2) / 2);
        drawClipped(g, font, title, x, ty, columns.title(), titleColor);
        int artistX = x + columns.title() + columns.gap();
        drawClipped(g, font, artist, artistX, ty, columns.artist(), MusicPlayerSkin.secondary());
        int albumX = artistX + columns.artist() + columns.gap();
        drawClipped(g, font, album, albumX, ty, columns.album(), MusicPlayerSkin.secondary());
    }

    private static void renderCard(GuiGraphics g, int x, int y, int w, int h, boolean hovered,
                                   int bg, int hoverBg, String coverUrl) {
        g.fill(x, y, x + w, y + h - 2, hovered ? hoverBg : bg);
        int size = Math.max(0, Math.min(20, h - 4));
        int cx = x + 2, cy = y + Math.max(1, (h - size - 2) / 2);
        if (size <= 0) return;
        ResourceLocation texture = SongCoverCache.getOrRequest(coverUrl);
        if (texture != null) g.blit(texture, cx, cy, 0, 0, size, size, size, size);
        else MusicPlayerSkin.renderControl(g, RosewoodPlayerLayout.Control.COVER,
                new RosewoodPlayerLayout.Rect(cx, cy, size, size), net.minecraft.network.chat.Component.empty());
        g.renderOutline(cx, cy, size, size, MusicPlayerSkin.edge());
    }



    private static void drawClipped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        String value = text == null ? "" : text;
        if (font.width(value) > maxWidth) {
            int ellipsis = font.width("…");
            value = ellipsis > maxWidth ? "" : font.plainSubstrByWidth(value, Math.max(0, maxWidth - ellipsis)) + "…";
        }
        if (!value.isEmpty()) g.drawString(font, value, x, y, color, false);
    }
}
