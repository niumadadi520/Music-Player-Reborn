package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

 
public final class ThemeButton extends Button {
    private ThemeButton(Button.Builder builder){super(builder);}
    public static Button.Builder builder(Component text, Button.OnPress action) {
        return new Button.Builder(text,action){@Override public Button build(){return new ThemeButton(this);}};
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
        if(!ThemeSkin.custom()){super.renderWidget(g,mx,my,partial);return;}
        var r=new RosewoodPlayerLayout.Rect(getX(),getY(),width,height);
        ThemeSkin.button(g,r,isHoveredOrFocused(),false,active);
        MusicPlayerSkin.renderLabel(g,ThemeSkin.content(r),getMessage(),active?MusicPlayerSkin.primary():MusicPlayerSkin.secondary());
    }
}
