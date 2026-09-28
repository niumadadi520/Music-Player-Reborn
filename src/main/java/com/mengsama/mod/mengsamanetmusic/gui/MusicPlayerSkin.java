package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.gui.theme.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;

import static com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.*;

 
public final class MusicPlayerSkin {
    public static final int PRIMARY = 0xFF45302F;
    public static final int TITLE = 0xFFFFE2CF;
    public static final int SECONDARY = 0xFF795D54;
    public static final int ACCENT = 0xFF965361;
    public static final int LIST_BG = 0x22B88979;
    public static final int LIST_HOVER = 0x66CB9B91;
    public static final ResourceLocation TEXTURE = new ResourceLocation("mengsamanetmusic", "textures/gui/rosewood_player.png");

    private MusicPlayerSkin() {}
    public static int primary() { return ThemeSkin.current().text; }
    public static int secondary() { return ThemeSkin.current().secondary; }
    public static int title() { return ThemeSkin.current().title; }
    public static int listBackground() { return ThemeSkin.custom() ? ThemeSkin.current().raised : LIST_BG; }
    public static int listHover() { return ThemeSkin.custom() ? (0x26000000 | (ThemeSkin.current().accent & 0xFFFFFF)) : LIST_HOVER; }
    public static int inputBackground() { return ThemeSkin.custom() ? ThemeSkin.current().raised : 0xFFECDDC8; }
    public static int edge() { return ThemeSkin.current().edge; }


    public static int accent() {
        int configured = MusicPlayerUiConfig.get().accent() & 0xFFFFFF;
        return configured == MusicPlayerUiConfig.Values.DEFAULT_THEME_RGB ? ThemeSkin.current().accent : 0xFF000000 | configured;
    }

    public static int selection() {
        return (accent() & 0xFFFFFF) | (ThemeSkin.custom() ? 0x14000000 : 0x70000000);
    }

    public static void renderInactiveSearchTab(GuiGraphics g, Rect bounds) {
        if (ThemeSkin.custom()) { renderControl(g,Control.NAV_SEARCH,bounds,Component.literal("歌曲搜索")); return; }
        Rect neutral = new Rect(273, 198, 223, 76);
        blit(g, bounds, neutral);
        patch(g, bounds, neutral, new Rect(341, 207, 92, 62), new Rect(284, 207, 49, 62));
         
        Rect original = new Rect(35, 198, 225, 76);
        for (Rect strip : RECORD_STRIPS) patch(g, bounds, original, strip, strip);
    }

    private static final Rect[] RECORD_STRIPS = {
            new Rect(135, 207, 26, 5), new Rect(125, 212, 43, 6),
            new Rect(120, 218, 54, 7), new Rect(115, 225, 64, 25),
            new Rect(120, 250, 54, 9), new Rect(126, 259, 43, 6),
            new Rect(135, 265, 28, 5)
    };

    public static void renderPanel(GuiGraphics g, int left, int top, RosewoodPlayerLayout layout) {
        if (ThemeSkin.custom()) { ThemeSkin.player(g,left,top,layout); return; }
        for (Part part : Part.values()) {
            Rect source = RosewoodPlayerLayout.source(part);
            Rect target = layout.band(part).at(left, top);
            blit(g, target, source);
             
            switch (part) {
                case HEADER -> patch(g, target, source, new Rect(1114, 8, 94, 78), new Rect(700, 8, 250, 78));
                case SEARCH -> patch(g, target, source, new Rect(48, 116, 1140, 60), new Rect(180, 116, 400, 60));
                case NAVIGATION -> patch(g, target, source, new Rect(25, 193, 1185, 92), new Rect(45, 892, 120, 107));
                case CONTROLS -> patch(g, target, source, new Rect(42, 896, 1148, 102), new Rect(60, 896, 105, 102));
                case PROGRESS -> patch(g, target, source, new Rect(48, 1021, 1139, 47), new Rect(150, 1020, 150, 9));
                case NOW_PLAYING -> patch(g, target, source, new Rect(44, 1101, 124, 118), new Rect(230, 1101, 400, 118));
                case CONTENT -> { }
            }
        }
    }

     
    public static void renderProgress(GuiGraphics g, Rect track, float fraction) {
        if (ThemeSkin.custom()) { ThemeSkin.progress(g,track,fraction,accent()); return; }
        float progress = Float.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : 0;
        blit(g, track, new Rect(510, 1035, 665, 23));
        int filled = Math.round(track.width() * progress);
        if (filled > 0) blit(g, new Rect(track.x(), track.y(), filled, track.height()), new Rect(64, 1035, 391, 23));
        int thumbHeight = Math.max(7, track.height() + 6);
        int thumbWidth = Math.max(6, Math.round(thumbHeight * 41F / 44F));
        int center = track.x() + Math.round(Math.max(0, track.width() - 1) * progress);
        blit(g, new Rect(center - thumbWidth / 2, track.y() - (thumbHeight - track.height()) / 2,
                thumbWidth, thumbHeight), new Rect(464, 1023, 41, 44));
    }

    public static void renderControl(GuiGraphics g, Control control, Rect bounds, Component message) {
        renderControl(g,control,bounds,message,false,false,true);
    }
    public static void renderControl(GuiGraphics g, Control control, Rect bounds, Component message,boolean hovered,boolean selected,boolean enabled) {
        if (ThemeSkin.custom()) {
            ThemeSkin.button(g,bounds,hovered,selected,enabled);
            if (control == Control.SOURCE || control == Control.AUTH) renderLabel(g,ThemeSkin.content(bounds),message,primary());
            else {
                Rect icon = ThemeSkin.content(bounds);
                if(control==Control.MODE)icon=new Rect(icon.x(),icon.y(),Math.max(1,icon.width()-11),icon.height());
                Rect iconArea=icon;
                ThemeSkin.paint(g,c -> ThemeArtwork.icon(c,ThemeSkin.current(),iconArea,control,isPause(message)));
                if(control==Control.MODE) {
                    String value=message.getString();
                    String label=value.contains("随机")?"随":value.contains("单曲")?"单":"列";
                    g.drawString(Minecraft.getInstance().font,label,bounds.right()-12,bounds.y()+(bounds.height()-8)/2,primary(),false);
                }
            }
            return;
        }
        Rect artwork = switch (control) {
            case CLOSE -> new Rect(1135, 18, 64, 65);
            case NAV_SEARCH -> new Rect(35, 198, 225, 76);
            case NAV_PLAYLIST -> new Rect(273, 198, 223, 76);
            case NAV_FAVORITES -> new Rect(509, 198, 223, 76);
            case NAV_LYRICS -> new Rect(743, 198, 222, 76);
            case NAV_SETTINGS -> new Rect(979, 198, 221, 76);
            case SEARCH -> new Rect(62, 122, 50, 48);
            case MODE -> new Rect(198, 917, 67, 62);
            case PREVIOUS -> new Rect(462, 916, 64, 65);
            case PLAY -> new Rect(582, 900, 79, 95);
            case NEXT -> new Rect(710, 918, 65, 63);
            case VOLUME -> new Rect(967, 914, 84, 66);
            case COVER -> new Rect(50, 1106, 109, 108);
            default -> null;
        };
        if (control == Control.STOP) {
            int size = Math.min(12, Math.max(3, Math.min(bounds.width(), bounds.height()) - 6));
            int x = bounds.x() + (bounds.width() - size) / 2, y = bounds.y() + (bounds.height() - size) / 2;
            g.fill(x, y, x + size, y + size, PRIMARY);
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFFC38891);
        } else if (control == Control.PLAY && isPause(message)) {
            renderPause(g, bounds);
        } else if (control == Control.MODE) {
            Rect icon = new Rect(bounds.x() + 2, bounds.y() + 2, Math.max(1, bounds.width() - 17), Math.max(1, bounds.height() - 4));
            centered(g, icon, artwork, 0);
            String value = message.getString();
            String mode = value.contains("随机") ? "随" : value.contains("单曲") ? "单" : "列";
            g.drawString(Minecraft.getInstance().font, mode, bounds.right() - 12,
                    bounds.y() + (bounds.height() - 8) / 2, PRIMARY, false);
        } else if (artwork != null) {
            boolean panel = control.name().startsWith("NAV_") || control == Control.CLOSE || control == Control.COVER;
            if (panel) blit(g, bounds, artwork);
            else centered(g, bounds, artwork, 3);
        } else {
            renderLabel(g, bounds, message, PRIMARY);
        }
    }

    static boolean isPause(Component message) {
        String value = message.getString();
        return value.contains("暂停") || value.contains("Ⅱ") || value.contains("⏸");
    }

    public static void renderVolume(GuiGraphics g, Rect bounds, int percent) {
        renderVolume(g,bounds,percent,false,true);
    }
    public static void renderVolume(GuiGraphics g, Rect bounds, int percent,boolean hovered,boolean enabled) {
        if (ThemeSkin.custom()) {
            ThemeSkin.button(g,bounds,hovered,false,enabled);
            ThemeSkin.paint(g,c -> ThemeArtwork.icon(c,ThemeSkin.current(),new Rect(bounds.x()+5,bounds.y()+3,Math.max(1,bounds.width()-10),Math.max(1,bounds.height()-15)),Control.VOLUME,false));
            renderLabel(g,new Rect(bounds.x()+4,bounds.bottom()-13,Math.max(1,bounds.width()-8),10),Component.literal(percent==0?"静音":percent+"%"),secondary());
            return;
        }
        int availableHeight = Math.max(1, bounds.height() - 10);
        Rect iconBounds = new Rect(bounds.x(), bounds.y(), bounds.width(), availableHeight);
        if (percent > 0) centered(g, iconBounds, new Rect(967, 914, 84, 66), 1);
        else {
            centered(g, iconBounds, new Rect(967, 914, 41, 66), 1);
            int size = Math.min(13, availableHeight - 2);
            for (int i = 0; i < size; i++) {
                int x = bounds.x() + bounds.width() / 2 + i - size / 2;
                int y = bounds.y() + 1 + i;
                g.fill(x, y, x + 2, y + 2, accent());
            }
        }
        renderLabel(g, new Rect(bounds.x(), bounds.bottom() - 10, bounds.width(), 10),
                Component.literal(percent == 0 ? "静音" : percent + "%"), SECONDARY);
    }

    static void renderLabel(GuiGraphics g, Rect bounds, Component message, int color) {
        var font = Minecraft.getInstance().font;
        String label = message.getString();
        int available = Math.max(0, bounds.width() - 4);
        if (font.width(label) > available) label = font.plainSubstrByWidth(label, Math.max(0, available - font.width("…"))) + "…";
        g.drawString(font, label, bounds.x() + (bounds.width() - font.width(label)) / 2,
                bounds.y() + (bounds.height() - 8) / 2, color, false);
    }

    private static void renderPause(GuiGraphics g, Rect bounds) {
        int height = Math.min(24, Math.max(5, bounds.height() - 6));
        int bar = Math.max(3, height / 4), gap = Math.max(3, height / 5);
        int x = bounds.x() + (bounds.width() - bar * 2 - gap) / 2;
        int y = bounds.y() + (bounds.height() - height) / 2;
        for (int offset : new int[] {0, bar + gap}) {
            g.fill(x + offset, y, x + offset + bar, y + height, PRIMARY);
            g.fill(x + offset + 1, y + 2, x + offset + bar - 1, y + height - 2, 0xFFC88C96);
        }
    }

    private static void centered(GuiGraphics g, Rect bounds, Rect source, int padding) {
        float scale = Math.min(Math.max(1, bounds.width() - padding * 2) / (float) source.width(),
                Math.max(1, bounds.height() - padding * 2) / (float) source.height());
        int width = Math.max(1, Math.round(source.width() * scale));
        int height = Math.max(1, Math.round(source.height() * scale));
        blit(g, new Rect(bounds.x() + (bounds.width() - width) / 2,
                bounds.y() + (bounds.height() - height) / 2, width, height), source);
    }

    private static void patch(GuiGraphics g, Rect target, Rect band, Rect removed, Rect sample) {
        int x = target.x() + Math.round((removed.x() - band.x()) * target.width() / (float) band.width());
        int y = target.y() + Math.round((removed.y() - band.y()) * target.height() / (float) band.height());
        int right = target.x() + Math.round((removed.right() - band.x()) * target.width() / (float) band.width());
        int bottom = target.y() + Math.round((removed.bottom() - band.y()) * target.height() / (float) band.height());
        blit(g, new Rect(x, y, Math.max(1, right - x), Math.max(1, bottom - y)), sample);
    }

    private static void blit(GuiGraphics g, Rect target, Rect source) {
        g.blit(TEXTURE, target.x(), target.y(), target.width(), target.height(),
                source.x(), source.y(), source.width(), source.height(), ATLAS_WIDTH, ATLAS_HEIGHT);
    }
}
