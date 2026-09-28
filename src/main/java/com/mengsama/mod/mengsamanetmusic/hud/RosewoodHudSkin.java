package com.mengsama.mod.mengsamanetmusic.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

 
public final class RosewoodHudSkin {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mengsamanetmusic", "textures/gui/rosewood_hud.png");
    public static final int TEXTURE_WIDTH = 2103, TEXTURE_HEIGHT = 748;
    private record Source(int x, int y, int width, int height) {}
    private static final Source OUTER = new Source(53, 76, 1996, 631);
    private static final Source COVER = new Source(92, 121, 280, 302);
    private static final Source INFORMATION = new Source(383, 121, 1615, 302);
    private static final Source LYRICS = new Source(92, 436, 1907, 225);

    private RosewoodHudSkin() {}

    public static void render(GuiGraphics g, RosewoodHudLayout.Layout layout) {
        if (layout.empty()) return;
        if(ThemeSkin.custom()) {
            ThemeSkin.hud(g,layout);
            return;
        }
        var whole = new RosewoodHudLayout.Rect(0, 0, layout.width(), layout.height());
         
        blit(g, whole, new Source(67, 161, 23, 340));
        nineSlice(g, whole, OUTER, 44, 3, false);
        if (!layout.coverFrame().empty()) blit(g, layout.coverFrame(), COVER);
        if (!layout.information().empty()) nineSlice(g, layout.information(), INFORMATION, 22, 3, true);
        if (!layout.lyrics().empty()) nineSlice(g, layout.lyrics(), LYRICS, 22, 3, true);
    }

    private static void nineSlice(GuiGraphics g, RosewoodHudLayout.Rect target, Source source, int sourceBorder, int targetBorder, boolean paper) {
        if (target.empty()) return;
        int bx = Math.min(targetBorder, target.width() / 2), by = Math.min(targetBorder, target.height() / 2);
        int[] dx = {target.x(), target.x() + bx, target.right() - bx, target.right()};
        int[] dy = {target.y(), target.y() + by, target.bottom() - by, target.bottom()};
        int[] sx = {source.x, source.x + sourceBorder, source.x + source.width - sourceBorder, source.x + source.width};
        int[] sy = {source.y, source.y + sourceBorder, source.y + source.height - sourceBorder, source.y + source.height};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            var tile = new RosewoodHudLayout.Rect(dx[col], dy[row], dx[col + 1] - dx[col], dy[row + 1] - dy[row]);
            if (row == 1 && col == 1) {
                 
                if (paper) blit(g, tile, new Source(620, 210, 650, 150));
            } else blit(g, tile, new Source(sx[col], sy[row], sx[col + 1] - sx[col], sy[row + 1] - sy[row]));
        }
    }

    private static void blit(GuiGraphics g, RosewoodHudLayout.Rect dst, Source src) {
        if (dst.empty()) return;
        g.blit(TEXTURE, dst.x(), dst.y(), dst.width(), dst.height(), src.x, src.y, src.width, src.height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

     
    static final class RenderState implements AutoCloseable {
        private final GuiGraphics graphics;
        private final float[] color;
        private final boolean blend;
        private final int srcRgb, dstRgb, srcAlpha, dstAlpha;
        RenderState(GuiGraphics graphics) {
            this.graphics = graphics;
            graphics.flush();
            color = RenderSystem.getShaderColor().clone();
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB); dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA); dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        void opacity(float opacity) { graphics.setColor(1, 1, 1, Math.max(0, Math.min(1, opacity))); }
        @Override public void close() {
            graphics.flush();
            graphics.setColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
