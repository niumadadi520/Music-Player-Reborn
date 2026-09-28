package com.mengsama.mod.mengsamanetmusic.earbuds.client;
import com.mengsama.mod.mengsamanetmusic.earbuds.*;
import com.mengsama.mod.mengsamanetmusic.gui.*;
import com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class EarbudScreen extends AbstractContainerScreen<EarbudMenu> {
    public EarbudScreen(EarbudMenu menu,Inventory inv,Component title) {super(menu,inv,title);imageWidth=232;imageHeight=212;}
    @Override protected void init() {
        super.init();
        addRenderableWidget(TransparentButton.builder(Component.literal(menu.caseMenu?"合上盒子":"返回随身听"),b->{
            if(menu.caseMenu) onClose();else ModNetwork.CHANNEL.sendToServer(new EarbudActionPacket(4,EarbudActionPacket.NONE,menu.containerId));
        }).pos(leftPos+130,topPos+190).size(86,18).build());
        if(!menu.caseMenu) addRenderableWidget(TransparentButton.builder(Component.literal("结束一起听"),b->ModNetwork.CHANNEL.sendToServer(new EarbudActionPacket(3,EarbudActionPacket.NONE,0)))
                .pos(leftPos+16,topPos+190).size(86,18).build());
    }
    public static void panel(GuiGraphics g,int x,int y,int width,int height) {
        if(ThemeSkin.custom()){ThemeSkin.panel(g,x,y,width,height,24);return;}
        g.fill(x,y,x+width,y+height,0xff704653);g.fill(x+2,y+2,x+width-2,y+height-2,0xffc69696);
        g.blit(MusicPlayerSkin.TEXTURE,x+4,y+4,width-8,24,20,15,1180,65,1236,1272);
        g.fill(x+4,y+30,x+width-4,y+height-4,0xffffeddb);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        panel(g,leftPos,topPos,imageWidth,imageHeight);
        for(var slot:menu.slots) if(slot.isActive()) {
            int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,MusicPlayerSkin.edge());g.fill(x,y,x+16,y+16,MusicPlayerSkin.inputBackground());
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,12,12,MusicPlayerSkin.title(),false);
        String[] labels={"有线耳机","蓝牙左耳","蓝牙右耳"};
        for(int i=0;i<3;i++) if(!menu.caseMenu||i!=0) g.drawCenteredString(font,labels[i],53+i*64,38,MusicPlayerSkin.primary());
        g.drawString(font,menu.caseMenu?"取出左右耳，再装入随身听":"耳机接入后强制私密播放；有线优先",12,79,MusicPlayerSkin.primary(),false);
        g.drawString(font,menu.caseMenu?"盒内耳机取走后不会重复生成":"修改耳机配置后，请重新点击播放",12,91,MusicPlayerSkin.secondary(),false);
        g.drawString(font,"物品栏",35,101,MusicPlayerSkin.secondary(),false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) { renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my); }
}
