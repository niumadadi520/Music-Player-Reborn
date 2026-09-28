package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

 
abstract class ThemedSelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    protected ThemedSelectionList(Minecraft client,int width,int screenHeight,int top,int bottom,int rowHeight) {
        super(client,width,Math.max(1,bottom-top),top,rowHeight);
    }
    @Override protected void renderListBackground(GuiGraphics graphics) {}
    @Override protected void renderListSeparators(GuiGraphics graphics) {}
}
