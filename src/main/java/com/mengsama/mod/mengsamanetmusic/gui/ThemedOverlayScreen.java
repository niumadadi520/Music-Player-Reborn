package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

 
public abstract class ThemedOverlayScreen extends Screen {
    protected ThemedOverlayScreen(Component title) { super(title); }

     
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
         
    }
}
