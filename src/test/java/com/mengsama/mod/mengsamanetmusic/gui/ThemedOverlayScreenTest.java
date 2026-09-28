package com.mengsama.mod.mengsamanetmusic.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemedOverlayScreenTest {
    @Test void renderingWidgetsAfterThePanelNeverRunsTheVanillaBlurPass() {
        List<String> rendered=new ArrayList<>();
        var screen=new ThemedOverlayScreen(Component.literal("test")) {
            { addRenderableOnly((graphics,x,y,partial)->rendered.add("button")); }
            @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
                rendered.add("panel");rendered.add("title");super.render(graphics,x,y,partial);
            }
        };
         
        assertDoesNotThrow(()->screen.render(null,0,0,0));
        assertEquals(List.of("panel","title","button"),rendered);
    }
    @Test void allHandDrawnChildPanelsUseTheProtectedRenderingPath() throws Exception {
        String root="com.mengsama.mod.mengsamanetmusic.";
        for(String name:List.of("gui.AccountPlaylistScreen","gui.ListeningRankingScreen",
                "gui.AccountPlaylistScreen$NetEaseAccountScreen","gui.QqLoginScreen","gui.NetEaseQrLoginScreen",
                "gui.AppleMusicAuthScreen","gui.ThemeSelectionScreen","gui.MoveHudScreen",
                "karaoke.client.KaraokeSettingsScreen","earbuds.client.EarbudClient$PanelScreen")) {
            assertTrue(ThemedOverlayScreen.class.isAssignableFrom(Class.forName(root+name,false,getClass().getClassLoader())),name);
        }
    }
}
