package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin;
import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

 
final class KaraokeSkin {
    private KaraokeSkin() {}

    static void blit(GuiGraphics g, Rect target, int u, int v, int w, int h) {
        g.blit(MusicPlayerSkin.TEXTURE, target.x(), target.y(), target.width(), target.height(),
                u, v, w, h, RosewoodPlayerLayout.ATLAS_WIDTH, RosewoodPlayerLayout.ATLAS_HEIGHT);
    }

    static void panel(GuiGraphics g, KaraokeLayout layout) {
        Rect p = layout.panel();
        if(ThemeSkin.custom()){ThemeSkin.panel(g,p.x(),p.y(),p.width(),p.height(),24);return;}
        blit(g, p, 0, 285, 1236, 604);
         
         
        contentPatch(g, p, 50, 300, 97, 76, 225);
         
        contentPatch(g, p, 1100, 785, 86, 73, 700);
        blit(g, layout.at(0, 0, p.width(), 28), 0, 0, 1236, 94);
         
        blit(g, layout.at(Math.round(p.width() * 1114F / 1236), 2,
                Math.max(1, Math.round(p.width() * 94F / 1236)), 24), 700, 8, 250, 78);
    }

    private static void contentPatch(GuiGraphics g, Rect panel, int u, int v, int width, int height, int sampleU) {
        int left = Math.round(panel.width() * u / 1236F), right = Math.round(panel.width() * (u + width) / 1236F);
        int top = Math.round(panel.height() * (v - 285) / 604F), bottom = Math.round(panel.height() * (v + height - 285) / 604F);
        blit(g, new Rect(panel.x() + left, panel.y() + top, Math.max(1, right - left), Math.max(1, bottom - top)),
                sampleU, v, width, height);
    }

    static void buttonBackground(GuiGraphics g, Rect r) {
        if(ThemeSkin.custom()){ThemeSkin.button(g,r);return;}
        blit(g, r, 273, 198, 223, 76);
         
        int x = r.x() + Math.round(r.width() * 68F / 223);
        int right = r.x() + Math.round(r.width() * 160F / 223);
        int y = r.y() + Math.round(r.height() * 9F / 76);
        int bottom = r.y() + Math.round(r.height() * 71F / 76);
        blit(g, new Rect(x, y, Math.max(1, right - x), Math.max(1, bottom - y)), 284, 207, 49, 62);
    }

    static Button button(Rect bounds, String label, Runnable action) {
        return new Button(bounds, Component.literal(label), action);
    }

    static final class Button extends AbstractButton {
        private final Runnable action;
        private boolean lit;
        Button(Rect r, Component message, Runnable action) {
            super(r.x(), r.y(), r.width(), r.height(), message);
            this.action = action;
            setTooltip(Tooltip.create(message));
        }
        void bounds(Rect r) { setX(r.x()); setY(r.y()); setWidth(r.width()); height = r.height(); }
        void lit(boolean enabled) { lit = enabled; }
        void tooltip(String value) { setTooltip(Tooltip.create(Component.literal(value))); }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Rect r = new Rect(getX(), getY(), width, height);
            if(ThemeSkin.custom()) ThemeSkin.button(g,r,isHoveredOrFocused(),lit,active);
            else buttonBackground(g,r);
            if (!ThemeSkin.custom() && lit) g.fill(r.x() + 2, r.bottom() - 3, r.right() - 2, r.bottom() - 1, MusicPlayerSkin.accent());
            if (!ThemeSkin.custom() && !active) g.fill(r.x(), r.y(), r.right(), r.bottom(), (ThemeSkin.custom() ? (0x66000000 | (ThemeSkin.current().surface & 0xFFFFFF)) : 0x66E8DBC9));
            if (!ThemeSkin.custom() && isHoveredOrFocused()) g.renderOutline(r.x(), r.y(), r.width(), r.height(), MusicPlayerSkin.accent());
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 6));
            g.drawString(font, label, r.x() + (r.width() - font.width(label)) / 2,
                    r.y() + (r.height() - 8) / 2, active ? MusicPlayerSkin.primary() : MusicPlayerSkin.secondary(), false);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
