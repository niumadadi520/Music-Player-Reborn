package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;

 
public final class ThemeArtwork {
    @FunctionalInterface public interface Canvas { void fill(int x, int y, int right, int bottom, int color); }
    private ThemeArtwork() {}
    private static void fill(Canvas g, Rect r, int color) {
        if (r.width()>0 && r.height()>0) g.fill(r.x(),r.y(),r.right(),r.bottom(),color);
    }
    private static Rect inset(Rect r, int n) {
        n=Math.min(n,Math.max(0,(Math.min(r.width(),r.height())-1)/2));
        return new Rect(r.x()+n,r.y()+n,Math.max(1,r.width()-2*n),Math.max(1,r.height()-2*n));
    }
    public static void well(Canvas g, MenuTheme t, Rect r) {
        fill(g,r,t.edge); fill(g,inset(r,1),t.frame); fill(g,inset(r,2),t.raised);
    }
    public static void button(Canvas g, MenuTheme t, Rect r) {
        ThemeButtonArtwork.button(g,t,r);
    }
    public static void panel(Canvas g, MenuTheme t, Rect r, int headerHeight) {
        fill(g,r,t.frame); fill(g,inset(r,2),t.edge); fill(g,inset(r,4),t.surface);
        if(r.width()<24 || r.height()<24)return;
        if(headerHeight>0) {
            int h=Math.min(headerHeight,r.height()-8);
            fill(g,new Rect(r.x()+4,r.y()+4,r.width()-8,h),t.frame);
            g.fill(r.x()+4,r.y()+h+3,r.right()-4,r.y()+h+4,t.edge);
        }
         
        for(int k=12;k<r.width()-12;k+=24) {
            int x=r.x()+k, y=r.y();
            switch(t) {
                case BAMBOO -> {g.fill(x,y+1,x+2,y+4,t.title);g.fill(x,r.bottom()-4,x+2,r.bottom()-1,t.accent);}
                case OCEAN -> {g.fill(x,y+1,x+6,y+2,t.accent);g.fill(x+3,y+2,x+9,y+3,t.edge);}
                case REDSTONE -> {g.fill(x,y+1,x+2,y+3,t.title);g.fill(x,r.bottom()-3,x+2,r.bottom()-1,t.edge);}
                case ENDER -> {g.fill(x,y+1,x+1,y+4,t.accent);g.fill(x-1,y+2,x+2,y+3,t.accent);}
                case BRASS -> {g.fill(x,y+1,x+7,y+2,t.title);g.fill(x+6,y+2,x+7,y+4,t.title);}
                case FROST -> {g.fill(x,y+1,x+5,y+2,t.title);g.fill(x+2,y+2,x+3,y+4,t.accent);}
                case NETHER -> {g.fill(x,y+1,x+3,y+3,t.accent);g.fill(x+3,y+2,x+6,y+3,t.edge);}
                case ARCADE -> {g.fill(x,y+1,x+3,y+3,t.accent);g.fill(x+5,y+1,x+8,y+3,t.title);}
                default -> {}
            }
        }
        for(int k=12;k<r.height()-12;k+=24) {
            int y=r.y()+k;
            g.fill(r.x()+1,y,r.x()+3,y+2,t.edge);
            g.fill(r.right()-3,y,r.right()-1,y+2,t.edge);
            if(t==MenuTheme.BAMBOO) {
                g.fill(r.x()+1,y+3,r.x()+3,y+9,t.accent);
                g.fill(r.right()-3,y+3,r.right()-1,y+9,t.accent);
            }
        }
    }
    public static void player(Canvas g, MenuTheme t, int x, int y, RosewoodPlayerLayout layout) {
        panel(g,t,new Rect(x,y,layout.width(),layout.height()),24);
        for(var part:new RosewoodPlayerLayout.Part[]{RosewoodPlayerLayout.Part.SEARCH,RosewoodPlayerLayout.Part.NAVIGATION,
                RosewoodPlayerLayout.Part.CONTROLS,RosewoodPlayerLayout.Part.NOW_PLAYING}) {
            Rect b=layout.band(part).at(x,y);
            fill(g,new Rect(b.x()+5,b.y(),Math.max(1,b.width()-10),b.height()),t.raised);
            g.fill(b.x()+5,b.bottom()-1,b.right()-5,b.bottom(),t.edge);
        }
        well(g,t,layout.searchField().at(x,y));
        Rect c=layout.content().at(x,y);
         
        emblem(g,t,new Rect(x+13,y+8,16,14));
        if(c.width()>12) {
            g.fill(c.x(),c.y()-3,c.right(),c.y()-2,t.edge);
            if(t==MenuTheme.REDSTONE) for(int i=0;i<5;i++)g.fill(c.x()+i*5,c.y()-3,c.x()+i*5+3,c.y()-2,t.accent);
        }
    }
    public static void hud(Canvas g,MenuTheme theme,com.mengsama.mod.mengsamanetmusic.hud.RosewoodHudLayout.Layout layout) {
        if(layout.empty())return;
        panel(g,theme,new Rect(0,0,layout.width(),layout.height()),0);
        for(var section:new com.mengsama.mod.mengsamanetmusic.hud.RosewoodHudLayout.Rect[]{layout.coverFrame(),layout.information(),layout.lyrics()})
            if(!section.empty())well(g,theme,new Rect(section.x(),section.y(),section.width(),section.height()));
    }
    public static float fraction(float value) {return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    public static void progress(Canvas g, MenuTheme t, Rect r, float value) {
        progress(g,t,r,value,t.accent);
    }
    public static void progress(Canvas g,MenuTheme t,Rect r,float value,int accent) {
        fill(g,r,t.frame);
        int end=Math.round(Math.max(0,r.width())*fraction(value));
        if(end>0)fill(g,new Rect(r.x(),r.y(),end,r.height()),accent);
        if(t==MenuTheme.REDSTONE || t==MenuTheme.BRASS)
            for(int i=7;i<r.width();i+=8)g.fill(r.x()+i,r.y(),r.x()+i+1,r.bottom(),t.surface);
        int cx=r.x()+Math.round(Math.max(0,r.width()-1)*fraction(value));
        g.fill(cx-2,r.y()-2,cx+3,r.bottom()+2,t.edge);
        g.fill(cx-1,r.y()-1,cx+2,r.bottom()+1,t.title);
    }
    private static final String[] NOTE={"0011000","0010110","0010010","0010010","1110111","1110111","0100010"};
    private static final String[] LEAF={"0000111","0011110","0111100","1111000","1110000","0100000","1000000"};
    private static final String[] WAVE={"0010001","0101010","1000100","0000000","0010001","0101010","1000100"};
    private static final String[] CHIP={"0101010","1111111","0100010","1101011","0100010","1111111","0101010"};
    private static final String[] STAR={"0001000","0001000","0011100","1111111","0011100","0001000","0001000"};
    private static final String[] GEAR={"0011100","0111110","1100011","1101011","1100011","0111110","0011100"};
    private static final String[] SNOW={"1001001","0101010","0011100","1111111","0011100","0101010","1001001"};
    private static final String[] FLAME={"0001000","0011000","0011100","0111010","1111110","1111111","0111110"};
    private static final String[] PAD={"0000000","0111110","1101011","1000111","1111101","1100011","1000001"};
    public static void emblem(Canvas g,MenuTheme t,Rect r){mask(g,r,switch(t){case BAMBOO->LEAF;case OCEAN->WAVE;case REDSTONE->CHIP;case ENDER->STAR;case BRASS->GEAR;case FROST->SNOW;case NETHER->FLAME;case ARCADE->PAD;default->NOTE;},t.accent);}
    public static void icon(Canvas g,MenuTheme t,Rect r,RosewoodPlayerLayout.Control control,boolean pause){
        String[] pixels=switch(control){
            case CLOSE -> new String[]{"1100011","0110110","0011100","0001000","0011100","0110110","1100011"};
            case SEARCH,NAV_SEARCH -> new String[]{"0111000","1101100","1000100","1101100","0111100","0000110","0000011"};
            case NAV_PLAYLIST -> new String[]{"1101111","1101111","0000000","1101111","1101111","0000000","1101111"};
            case NAV_FAVORITES -> new String[]{"0110110","1111111","1111111","1111111","0111110","0011100","0001000"};
            case NAV_LYRICS -> NOTE;
            case NAV_SETTINGS -> GEAR;
            case PREVIOUS -> new String[]{"1000010","1000110","1001110","1011110","1001110","1000110","1000010"};
            case NEXT -> new String[]{"0100001","0110001","0111001","0111101","0111001","0110001","0100001"};
            case PLAY -> pause ? new String[]{"0110110","0110110","0110110","0110110","0110110","0110110","0110110"} : new String[]{"0100000","0110000","0111000","0111100","0111000","0110000","0100000"};
            case STOP -> new String[]{"0000000","0111110","0111110","0111110","0111110","0111110","0000000"};
            case VOLUME -> new String[]{"0001000","0011001","1111010","1111010","1111010","0011001","0001000"};
            case MODE -> new String[]{"0000010","0111111","0100010","0100010","0100010","1111110","0100000"};
            default -> NOTE;
        };
        ThemeIconArtwork.draw(g,t,r,pixels);
    }
    private static void mask(Canvas g,Rect r,String[] pixels,int color){
        int scale=Math.max(1,Math.min(r.width(),r.height())/7),x=r.x()+(r.width()-7*scale)/2,y=r.y()+(r.height()-7*scale)/2;
        for(int row=0;row<7;row++)for(int col=0;col<7;col++)if(pixels[row].charAt(col)=='1'){
            int a=Math.max(r.x(),x+col*scale),b=Math.max(r.y(),y+row*scale);
            int right=Math.min(r.right(),x+(col+1)*scale),bottom=Math.min(r.bottom(),y+(row+1)*scale);
            if(right>a&&bottom>b)g.fill(a,b,right,bottom,color);
        }
    }
}
