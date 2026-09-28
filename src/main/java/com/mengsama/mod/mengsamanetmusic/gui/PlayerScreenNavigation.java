package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

 
final class PlayerScreenNavigation {
    private PlayerScreenNavigation() {}

    static void openChild(Screen parent, Screen child) {
        if (parent instanceof MusicPlayerScreen player) player.prepareForChildScreen();
        if (parent instanceof MusicPlayerPlaylistScreen player) player.prepareForChildScreen();
        Minecraft.getInstance().setScreen(child);
    }

    static void returnTo(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (parent instanceof AbstractContainerScreen<?> container
                && (mc.player == null || mc.player.containerMenu != container.getMenu())) {
            mc.setScreen(null);
        } else {
            mc.setScreen(parent);
        }
    }
}
