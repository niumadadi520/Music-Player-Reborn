package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.config.MenuThemeConfig;
import com.mengsama.mod.mengsamanetmusic.gui.theme.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

 
public final class ThemeSelectionScreen extends com.mengsama.mod.mengsamanetmusic.gui.ThemedOverlayScreen {
    private final Screen parent;
    private int page;
    private String status="点击主题即可应用并保存";
    private int left,top,panelWidth,panelHeight;
    public ThemeSelectionScreen(Screen parent){super(Component.literal("菜单主题"));this.parent=parent;page=ThemePages.containing(MenuThemeConfig.get());}
    public static void open(Screen parent){PlayerScreenNavigation.openChild(parent,new ThemeSelectionScreen(parent));}
    @Override protected void init(){
        panelWidth=Math.min(480,Math.max(1,width-16));panelHeight=Math.min(300,Math.max(1,height-16));
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;page=ThemePages.clamp(page);
        int gap=6,cardWidth=Math.max(1,(panelWidth-28)/3),cardHeight=Math.max(12,(panelHeight-106)/2);
        var themes=MenuTheme.values();
        for(int i=ThemePages.first(page);i<ThemePages.end(page);i++){
            int slot=i-ThemePages.first(page);
            addRenderableWidget(new ThemeCard(left+8+(slot%3)*(cardWidth+gap),top+31+(slot/3)*(cardHeight+gap),cardWidth,cardHeight,themes[i]));
        }
        int navY=top+panelHeight-58,navWidth=Math.min(90,Math.max(1,(panelWidth-40)/3)),center=left+panelWidth/2;
        var previous=addRenderableWidget(TransparentButton.builder(Component.literal("上一页"),b->changePage(page-1)).pos(center-navWidth*3/2-6,navY).size(navWidth,18).build());previous.active=page>0;
        var number=addRenderableWidget(TransparentButton.builder(Component.literal((page+1)+" / "+ThemePages.count()),b->changePage(ThemePages.containing(MenuThemeConfig.get()))).pos(center-navWidth/2,navY).size(navWidth,18).build());
        number.setTooltip(Tooltip.create(Component.literal("点击回到当前主题所在页；PageUp / PageDown 翻页")));
        var next=addRenderableWidget(TransparentButton.builder(Component.literal("下一页"),b->changePage(page+1)).pos(center+navWidth/2+6,navY).size(navWidth,18).build());next.active=page<ThemePages.count()-1;
        int half=Math.max(1,(panelWidth-22)/2),y=top+panelHeight-24;
        addRenderableWidget(TransparentButton.builder(Component.literal("背景与 HUD 设置"),b->{if(parent instanceof MoveHudScreen)onClose();else MoveHudScreen.open(this);}).pos(left+8,y).size(half,18).build());
        addRenderableWidget(TransparentButton.builder(Component.literal("返回"),b->onClose()).pos(left+14+half,y).size(half,18).build());
    }
    private void changePage(int value){int next=ThemePages.clamp(value);if(next!=page){page=next;rebuildWidgets();}}
    private void choose(MenuTheme theme){
        try{MenuThemeConfig.select(theme);status="已保存："+theme.label;}
        catch(java.io.IOException failure){status="保存失败，保留原主题；请检查配置目录权限";}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_PAGE_UP){changePage(page-1);return true;}
        if(key==GLFW.GLFW_KEY_PAGE_DOWN){changePage(page+1);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderTransparentBackground(g);ThemeSkin.panel(g,left,top,panelWidth,panelHeight,22);
        g.drawCenteredString(font,title,left+panelWidth/2,top+11,MenuThemeConfig.get().title);
        g.drawString(font,font.plainSubstrByWidth(status,Math.max(1,panelWidth-18)),left+9,top+panelHeight-37,MusicPlayerSkin.primary(),false);
        super.render(g,mx,my,partial);
    }
    @Override public void onClose(){PlayerScreenNavigation.returnTo(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private final class ThemeCard extends AbstractButton {
        private final MenuTheme theme;
        ThemeCard(int x,int y,int w,int h,MenuTheme theme){super(x,y,w,h,Component.literal(theme.label));this.theme=theme;setTooltip(Tooltip.create(Component.literal(theme.description)));}
        @Override public void onPress(){choose(theme);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            var r=new RosewoodPlayerLayout.Rect(getX(),getY(),width,height);
            boolean selected=MenuThemeConfig.get()==theme;
            PaintedThemeTextures.draw(g,theme,r,ThemeAtlasLayout.state(isHoveredOrFocused(),selected,true),false);
            String label=(selected?"✓ ":"")+theme.label;
            label=font.plainSubstrByWidth(label,Math.max(1,width-24));
            g.drawString(font,label,getX()+(width-font.width(label))/2,getY()+(height-8)/2,theme.text,false);
            if(selected)g.fill(getX()+width/2-9,getY()+height-5,getX()+width/2+9,getY()+height-3,theme.accent);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
