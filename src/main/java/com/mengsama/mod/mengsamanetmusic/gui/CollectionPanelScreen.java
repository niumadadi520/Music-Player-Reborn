package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

 
abstract class CollectionPanelScreen extends Screen {
    protected final Screen parent;
    protected int left,top,panelWidth,panelHeight;
    protected EditBox pageInput;
    protected AbstractButton previous,next,pageLabel;
    protected String status="";
    protected CollectionPanelScreen(Screen parent,String title){super(Component.literal(title));this.parent=parent;}
    @Override protected void init(){
        panelWidth=Math.min(460,Math.max(180,width-12));panelHeight=Math.min(410,Math.max(150,height-12));
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        button("返回",left+panelWidth-54,top+6,44,this::onClose);
    }
    protected AbstractButton button(String name,int x,int y,int w,Runnable action){
        return addRenderableWidget(QqLoginSkin.button(new QqLoginLayout.Rect(x,y,w,20),Component.literal(name),action));
    }
    protected void paging(){
        int y=top+panelHeight-52, unit=(panelWidth-32)/6;
        previous=button("上一页",left+12,y,unit,()->go(page()-1));
        pageLabel=button("1 / 1",left+16+unit,y,unit,()->{});pageLabel.active=false;
        next=button("下一页",left+20+unit*2,y,unit,()->go(page()+1));
        pageInput=new EditBox(font,left+24+unit*3,y+2,unit,16,Component.literal("指定页码"));
        pageInput.setMaxLength(6);pageInput.setFilter(s->s.matches("[0-9]*"));pageInput.setHint(Component.literal("页码"));
        addRenderableWidget(pageInput);
        button("跳转",left+28+unit*4,y,Math.max(unit,panelWidth-40-unit*4),this::jump);
    }
    protected int page(){return 1;}
    protected int pages(){return 1;}
    protected void go(int page){}
    private void jump(){try{go(Integer.parseInt(pageInput.getValue()));}catch(NumberFormatException ignored){}}
    protected void frame(GuiGraphics g){
        renderBackground(g);QqLoginSkin.renderCollectionPanel(g,left,top,panelWidth,panelHeight);
        g.drawString(font,title,left+14,top+12,QqLoginSkin.title(),false);
        g.drawString(font,font.plainSubstrByWidth(status,panelWidth-28),left+14,top+panelHeight-22,QqLoginSkin.secondary(),false);
        if(pageLabel!=null){pageLabel.setMessage(Component.literal(page()+" / "+pages()));previous.active=page()>1;next.active=page()<pages();}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(pageInput!=null && pageInput.isFocused() && (key==257 || key==335)){jump();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void tick(){
        if(pageInput!=null)pageInput.tick();
        if(parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                && (minecraft.player==null || minecraft.player.containerMenu!=screen.getMenu()))minecraft.setScreen(null);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){PlayerScreenNavigation.returnTo(parent);}
}
