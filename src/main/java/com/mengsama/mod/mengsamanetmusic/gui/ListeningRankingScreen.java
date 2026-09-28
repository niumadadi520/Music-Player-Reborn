package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.client.ListeningClient;
import com.mengsama.mod.mengsamanetmusic.listening.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.atomic.AtomicLong;

public final class ListeningRankingScreen extends CollectionPanelScreen {
    private static final AtomicLong REQUESTS=new AtomicLong();
    private long request,started,lastSent;
    private boolean players,busy,pending;
    private int page=1,pages=1;
    private RankingList list;
    ListeningRankingScreen(Screen parent){super(parent,"听歌排行榜");}
    @Override protected void init(){
        super.init();int unit=(panelWidth-32)/3;
        button("歌曲播放榜",left+12,top+38,unit,()->{players=false;go(1);});
        button("玩家时长榜",left+16+unit,top+38,unit,()->{players=true;go(1);});
        button("刷新",left+20+unit*2,top+38,unit,()->go(page));
        list=new RankingList();addRenderableWidget(list);paging();go(page);
    }
    @Override protected int page(){return page;}
    @Override protected int pages(){return pages;}
    @Override protected void go(int requested){
        if(!ListeningClient.available()){status="当前服务器尚未安装排行榜模块";return;}
        page=Math.max(1,Math.min(5000,requested));request=REQUESTS.incrementAndGet();busy=true;started=System.nanoTime();
        list.clear();status="正在读取当前存档 / 服务器的排行榜…";
        pending=true;sendPending();
    }
    private void sendPending(){
        if(!pending || System.nanoTime()-lastSent<350_000_000L || !ListeningClient.available())return;
        lastSent=System.nanoTime();pending=false;
        ListeningNetwork.CHANNEL.sendToServer(new ListeningNetwork.Query(request,players,page));
    }
    public static void receive(ListeningNetwork.Board board){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof ListeningRankingScreen screen && board.request()==screen.request && board.players()==screen.players){
            screen.busy=false;screen.page=board.page();screen.pages=board.pages();screen.list.clear();
            for(int i=0;i<board.rows().size();i++)screen.list.append(board.rows().get(i),(board.page()-1)*20+i+1);
            screen.status=board.rows().isEmpty()?"暂无记录；开始播放音乐后逐步累积":"当前存档 / 服务器 · 从安装本版起统计";
        }
    }
    @Override public void tick(){super.tick();sendPending();if(busy && System.nanoTime()-started>8_000_000_000L){busy=false;status="排行榜读取超时，请点击刷新";}}
    @Override public void removed(){request=REQUESTS.incrementAndGet();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        frame(g);super.render(g,mx,my,partial);
        String rule=players?"累计听歌时长 · 暂停不计 · 多个设备不重复计时":"累计播放次数 · 同一设备的一次播放只计一次";
        g.drawString(font,font.plainSubstrByWidth(rule,panelWidth-28),left+14,top+66,QqLoginSkin.secondary(),false);
    }
    static String duration(long ticks){long seconds=ticks/20;return seconds/3600+" 小时 "+seconds/60%60+" 分 "+seconds%60+" 秒";}
    private final class RankingList extends ThemedSelectionList<Row>{
        RankingList(){super(ListeningRankingScreen.this.minecraft,panelWidth-24,panelHeight,top+82,top+panelHeight-58,32);setX(left+12);}
        void clear(){clearEntries();setScrollAmount(0);}
        void append(ListeningLedger.Row row,int rank){addEntry(new Row(row,rank));}
        @Override public int getRowWidth(){return getWidth()-12;}
        @Override protected int getScrollbarPosition(){return getX()+getWidth()-6;}
    }
    private final class Row extends ObjectSelectionList.Entry<Row>{
        private final ListeningLedger.Row data;private final int rank;
        Row(ListeningLedger.Row data,int rank){this.data=data;this.rank=rank;}
        @Override public void render(GuiGraphics g,int index,int y,int x,int w,int h,int mx,int my,boolean hovered,float partial){
            g.fill(x,y,x+w,y+h-1,hovered?MusicPlayerSkin.listHover():MusicPlayerSkin.listBackground());
            String value=players?duration(data.value()):data.value()+" 次";
            int valueWidth=font.width(value);
            g.drawString(font,font.plainSubstrByWidth(rank+". "+data.title(),Math.max(20,w-valueWidth-16)),x+5,y+4,QqLoginSkin.text(),false);
            g.drawString(font,value,x+w-valueWidth-4,y+4,QqLoginSkin.accent(),false);
            if(!players)g.drawString(font,font.plainSubstrByWidth(data.detail(),w-12),x+5,y+17,QqLoginSkin.secondary(),false);
        }
        @Override public Component getNarration(){return Component.literal(rank+". "+data.title()+" "+(players?duration(data.value()):data.value()+" 次"));}
    }
}
