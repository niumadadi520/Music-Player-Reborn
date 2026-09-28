package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

final class QqLoginSkin {
    static final int TEXT = 0xFF543A37, SECONDARY = 0xFF866258, TITLE = 0xFFFFEFE0, ACCENT = 0xFFAA6374;
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MengSamaNetMusic.MOD_ID, "textures/gui/rosewood_qq_login.png");
    private static final int ATLAS = 1254;
    private QqLoginSkin() {}
    static int qrText() {return TEXT;}
    static int text() {return ThemeSkin.custom()?MusicPlayerSkin.primary():TEXT;}
    static int secondary() {return ThemeSkin.custom()?MusicPlayerSkin.secondary():SECONDARY;}
    static int title() {return ThemeSkin.custom()?MusicPlayerSkin.title():TITLE;}
    static int accent() {return ThemeSkin.custom()?MusicPlayerSkin.accent():ACCENT;}

    static void renderCollectionPanel(GuiGraphics g, int x, int y, int width, int height) {
        if(ThemeSkin.custom()){ThemeSkin.panel(g,x,y,width,height,27);return;}
        blit(g, new QqLoginLayout.Rect(x,y,width,height),60,72,1136,1110);
        blit(g, new QqLoginLayout.Rect(x+7,y+31,width-14,height-38),111,264,205,610);
        blit(g, new QqLoginLayout.Rect(x,y,width,32),60,72,1136,151);
    }
    static void renderPanel(GuiGraphics g, QqLoginLayout layout) {
        var p = layout.panel();
        if(ThemeSkin.custom()) {
            ThemeSkin.panel(g,p.x(),p.y(),p.width(),p.height(),27);
            var frame=layout.qrFrame();ThemeSkin.well(g,frame.x(),frame.y(),frame.width(),frame.height());
            var white=layout.qrWhite();g.fill(white.x(),white.y(),white.right(),white.bottom(),0xFFFFFFFF);
            return;
        }
        blit(g, p, 60, 72, 1136, 1110);
         
        blit(g, new QqLoginLayout.Rect(p.x() + 7, p.y() + 31, p.width() - 14, p.height() - 38), 111, 264, 205, 610);
        blit(g, new QqLoginLayout.Rect(p.x(), p.y(), p.width(), 32), 60, 72, 1136, 151);
        blit(g, layout.qrFrame(), 344, 306, 566, 559);
        var white = layout.qrWhite();
        g.fill(white.x(), white.y(), white.right(), white.bottom(), 0xFFFFFFFF);
    }
    static AbstractButton button(QqLoginLayout.Rect bounds, Component message, Runnable action) { return new SkinButton(bounds, message, action); }
    private static void blit(GuiGraphics g, QqLoginLayout.Rect target, int u, int v, int width, int height) {
        g.blit(TEXTURE, target.x(), target.y(), target.width(), target.height(), u, v, width, height, ATLAS, ATLAS);
    }
    private static final class SkinButton extends AbstractButton {
        private final Runnable action;
        SkinButton(QqLoginLayout.Rect bounds, Component message, Runnable action) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message);
            this.action = action;
            setTooltip(Tooltip.create(message));
        }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if(ThemeSkin.custom())ThemeSkin.button(g,new RosewoodPlayerLayout.Rect(getX(),getY(),width,height),isHoveredOrFocused(),false,active);
            else blit(g, new QqLoginLayout.Rect(getX(), getY(), width, height), 132, 946, 317, 137);
            if (!ThemeSkin.custom() && !active) g.fill(getX(), getY(), getX() + width, getY() + height, (ThemeSkin.custom() ? (0x77000000 | (ThemeSkin.current().surface & 0xFFFFFF)) : 0x77F0E5D3));
            if (!ThemeSkin.custom() && isHoveredOrFocused()) g.renderOutline(getX(), getY(), width, height, accent());
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 8));
            g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2, active ? text() : secondary(), false);
        }
        @Override protected void updateWidgetNarration(@NotNull NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
