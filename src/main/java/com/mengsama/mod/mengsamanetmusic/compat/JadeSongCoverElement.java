package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.client.SongCoverCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

 
final class JadeSongCoverElement extends Element {
    private static final int SIZE = 32;
    private final String coverUrl;

    JadeSongCoverElement(String coverUrl) {
        this.coverUrl = coverUrl;
        this.size = new Vec2(SIZE, SIZE);
    }

    @Override
    public Vec2 getSize() {
        return size;
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        ResourceLocation texture = SongCoverCache.getOrRequest(coverUrl);
        if (texture == null) return;
        RenderSystem.enableBlend();
        graphics.blit(texture, (int) x, (int) y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
    }
}
