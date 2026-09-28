package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.config.MenuThemeConfig;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;

public final class ThemeSkin {
    private ThemeSkin() {}
    public static MenuTheme current() { return MenuThemeConfig.get(); }
    public static boolean custom() { return current()!=MenuTheme.ROSEWOOD; }
    @SuppressWarnings("deprecation")
    public static void paint(GuiGraphics g, java.util.function.Consumer<ThemeArtwork.Canvas> drawing) {
         
        g.drawManaged(() -> drawing.accept(g::fill));
    }
    public static void panel(GuiGraphics g,int x,int y,int width,int height,int header) {
        if(!custom()){paint(g,c -> ThemeArtwork.panel(c,current(),new Rect(x,y,width,height),header));return;}
        PaintedThemeTextures.draw(g,current(),new Rect(x,y,width,height),0,true);
        if(header>0&&width>16&&height>16)g.fill(x+8,y+6,x+width-8,y+Math.min(height-8,header+4),current().frame);
    }
    public static void button(GuiGraphics g,Rect bounds) {button(g,bounds,false,false,true);}
    public static void button(GuiGraphics g,Rect bounds,boolean hovered,boolean selected,boolean enabled) {
        PaintedThemeTextures.draw(g,current(),bounds,ThemeAtlasLayout.state(hovered,selected,enabled),false);
    }
    public static Rect content(Rect r) {return ThemeAtlasLayout.content(r);}
    public static void well(GuiGraphics g,int x,int y,int w,int h) {paint(g,c -> ThemeArtwork.well(c,current(),new Rect(x,y,w,h)));}
    public static void player(GuiGraphics g,int x,int y,com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout layout){
        panel(g,x,y,layout.width(),layout.height(),Math.max(1,layout.band(com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Part.HEADER).height()-6));
        var input=layout.searchField().at(x,y);well(g,input.x(),input.y(),input.width(),input.height());
    }
    public static void hud(GuiGraphics g,com.mengsama.mod.mengsamanetmusic.hud.RosewoodHudLayout.Layout layout){
        if(layout.empty())return;
        PaintedThemeTextures.draw(g,current(),new Rect(0,0,layout.width(),layout.height()),0,true);
         
        for(var section:new com.mengsama.mod.mengsamanetmusic.hud.RosewoodHudLayout.Rect[]{layout.coverFrame(),layout.information(),layout.lyrics()})
            if(!section.empty())well(g,section.x(),section.y(),section.width(),section.height());
    }
    public static void progress(GuiGraphics g,Rect r,float value,int accent){
        paint(g,c -> ThemeArtwork.progress(c,current(),r,value,accent));
    }
}
