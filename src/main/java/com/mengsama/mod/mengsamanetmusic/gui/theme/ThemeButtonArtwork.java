package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;

 
public final class ThemeButtonArtwork {
    private ThemeButtonArtwork() {}
    private static final class Ink {
        final ThemeArtwork.Canvas canvas; final Rect bounds; final int w,h;
        Ink(ThemeArtwork.Canvas canvas,Rect bounds){this.canvas=canvas;this.bounds=bounds;w=bounds.width();h=bounds.height();}
        void box(int x,int y,int right,int bottom,int color){
            x=Math.max(0,x);y=Math.max(0,y);right=Math.min(w,right);bottom=Math.min(h,bottom);
            if(right>x&&bottom>y)canvas.fill(bounds.x()+x,bounds.y()+y,bounds.x()+right,bounds.y()+bottom,color);
        }
        void cut(int inset,int bevel,int color){
            for(int y=inset;y<h-inset;y++){
                int cut=Math.max(0,bevel-Math.min(y-inset,h-inset-1-y));
                box(inset+cut,y,w-inset-cut,y+1,color);
            }
        }
        void dot(int x,int y,int color){box(x,y,x+1,y+1,color);}
    }
    public static Rect content(MenuTheme t,Rect r){
        int side=switch(t){case ENDER->Math.min(10,r.height()/3+2);case BRASS,BAMBOO,OCEAN->6;default->4;};
        side=Math.min(side,Math.max(0,(r.width()-7)/2));
        int vertical=Math.min(4,Math.max(0,(r.height()-7)/2));
        return new Rect(r.x()+side,r.y()+vertical,Math.max(1,r.width()-side*2),Math.max(1,r.height()-vertical*2));
    }
    public static void button(ThemeArtwork.Canvas canvas,MenuTheme t,Rect r){
        Ink g=new Ink(canvas,r);int w=g.w,h=g.h;
        if(w<12||h<10){g.box(0,0,w,h,t.frame);g.box(1,1,w-1,h-1,t.raised);return;}
        switch(t){
            case BAMBOO -> {
                 
                g.box(3,2,w-3,h-1,t.frame);g.box(4,4,w-4,h-4,t.raised);
                g.box(1,1,w-2,4,t.accent);g.box(3,h-4,w-1,h-1,t.accent);
                g.box(3,1,w-3,2,t.title);g.box(4,h-4,w-2,h-3,t.edge);
                for(int x=8;x<w-4;x+=17){g.box(x,0,x+2,5,t.edge);g.dot(x+1,1,t.title);g.box(x+3,h-5,x+5,h,t.frame);g.box(x+4,h-4,x+5,h-1,t.edge);}
                g.box(2,4,5,h-4,t.edge);g.box(w-5,4,w-2,h-4,t.edge);
                g.box(0,3,3,5,t.accent);g.box(1,5,3,7,t.accent);
            }
            case OCEAN -> {
                 
                g.cut(0,Math.min(6,h/3),t.frame);g.cut(1,Math.min(5,h/3),t.edge);g.cut(3,3,t.raised);
                for(int x=7;x<w-5;x+=9){g.box(x,1,x+3,2,t.title);g.box(x+2,2,x+5,3,t.accent);g.box(x,h-3,x+3,h-1,t.frame);}
                g.box(2,h/2-2,5,h/2+1,t.accent);g.dot(3,h/2-2,t.title);
                g.box(w-5,h/2,w-2,h/2+3,t.accent);g.dot(w-4,h/2,t.title);
            }
            case REDSTONE -> {
                 
                g.box(0,3,w-3,h,t.frame);g.box(3,0,w,h-3,t.frame);
                g.box(2,4,w-4,h-2,t.edge);g.box(4,2,w-2,h-4,t.edge);g.box(4,4,w-4,h-4,t.raised);
                for(int x:new int[]{3,w-6})for(int y:new int[]{3,h-6}){g.box(x,y,x+3,y+3,t.frame);g.box(x+1,y,x+2,y+2,t.title);}
                g.box(8,h-4,w/2,h-3,t.accent);g.box(w/2,h-5,w/2+2,h-3,t.accent);g.box(w/2,h-5,w-8,h-4,t.edge);
                g.box(1,h/2-2,3,h/2+2,t.accent);
            }
            case ENDER -> {
                 
                int tip=Math.min(9,h/2);
                g.cut(0,tip,t.frame);g.cut(1,Math.max(1,tip-1),t.edge);g.cut(3,Math.max(1,tip-3),t.raised);
                for(int y=2;y<h-2;y++){int x=Math.max(1,tip-Math.min(y,h-1-y));g.dot(x,y,t.accent);g.dot(w-1-x,y,t.edge);}
                for(int x=tip+4;x<w-tip-2;x+=11){g.dot(x,1,t.title);g.dot(x+2,h-2,t.accent);}
                g.box(3,h/2-1,6,h/2+1,t.accent);g.dot(w-4,h/2,t.title);
            }
            case BRASS -> {
                 
                g.box(4,0,w-4,h,t.frame);g.box(2,2,w-2,h-2,t.edge);g.box(5,3,w-5,h-3,t.frame);g.box(6,4,w-6,h-4,t.raised);
                for(int y=3;y<h-2;y+=5){g.box(0,y,4,y+3,t.frame);g.box(w-4,y,w,y+3,t.frame);g.box(1,y+1,4,y+2,t.accent);g.box(w-4,y+1,w-1,y+2,t.accent);}
                for(int x:new int[]{3,w-6}){g.box(x,h/2-2,x+3,h/2+2,t.accent);g.box(x+1,h/2-1,x+2,h/2+1,t.title);}
                g.box(8,1,w-8,2,t.title);g.box(w/2-2,h-3,w/2+2,h-1,t.accent);
            }
            case FROST -> {
                 
                g.cut(0,4,t.frame);g.cut(1,3,t.edge);g.cut(2,3,t.raised);
                g.box(7,1,w-6,3,t.title);g.box(4,3,8,4,t.title);g.box(w-5,5,w-3,h-6,t.title);
                for(int x=5;x<w-4;x+=11){g.box(x,h-4,x+4,h-2,t.edge);g.box(x+1,h-2,x+3,h-1,t.edge);g.dot(x+2,h-1,t.title);}
                for(int i=0;i<4;i++)g.dot(2+i,h-6+i,t.title);
                g.box(w-9,3,w-6,4,t.accent);g.dot(w-7,2,t.accent);
            }
            case NETHER -> {
                 
                g.box(2,0,w-6,h-1,t.frame);g.box(0,4,w,h-4,t.frame);g.box(5,2,w-2,h,t.frame);
                g.box(3,3,w-4,h-3,t.edge);g.box(5,4,w-5,h-4,t.raised);
                g.box(2,h/2,4,h-4,t.accent);g.box(4,h-6,8,h-4,t.accent);g.box(7,h-5,9,h-2,t.accent);g.dot(8,h-3,t.title);
                g.box(w-10,1,w-7,3,t.accent);g.box(w-8,3,w-6,6,t.accent);g.dot(w-7,3,t.title);
                g.box(2,0,6,2,t.surface);g.box(w/2,h-2,w/2+5,h,t.surface);
            }
            case ARCADE -> {
                 
                g.box(3,1,w-3,h,t.frame);g.box(1,3,w-1,h-2,t.frame);
                int depth=h>=22?5:3;
                g.box(3,2,w-3,h-depth,t.edge);g.box(5,4,w-5,h-depth-1,t.raised);
                g.box(5,2,w-5,3,t.title);g.box(3,h-depth,w-3,h-depth+2,t.accent);
                for(int i=0;i<3;i++)g.box(w/2-5+i*4,h-2,w/2-3+i*4,h-1,i==1?t.accent:t.edge);
            }
            default -> {g.box(0,0,w,h,t.frame);g.box(1,1,w-1,h-1,t.edge);g.box(2,2,w-2,h-2,t.raised);}
        }
    }
     
    public static void feedback(ThemeArtwork.Canvas canvas,MenuTheme t,Rect r,boolean hovered,boolean selected,boolean enabled){
        if(!hovered&&!selected&&enabled)return;
        Ink g=new Ink(canvas,r);int w=g.w,h=g.h,color=enabled?t.accent:t.secondary,light=selected?t.title:color;
        switch(t){
            case BAMBOO -> {g.box(5,0,7,5,light);g.box(w-8,h-5,w-6,h,color);}
            case OCEAN -> {g.box(2,h/2-2,5,h/2+1,light);g.box(w-5,h/2,w-2,h/2+3,color);}
            case REDSTONE -> {g.box(1,h/2-2,3,h/2+2,light);g.box(8,h-4,Math.max(9,w-8),h-3,color);}
            case ENDER -> {g.box(1,h/2-1,4,h/2+1,light);g.box(w-4,h/2-1,w-1,h/2+1,color);}
            case BRASS -> {g.box(3,h/2-1,6,h/2+1,light);g.box(w-6,h/2-1,w-3,h/2+1,color);}
            case FROST -> {g.box(5,1,Math.max(6,w-6),2,light);g.box(w-4,5,w-3,Math.max(6,h-6),color);}
            case NETHER -> {g.box(3,h-6,8,h-4,light);g.box(w-10,1,w-7,3,color);}
            case ARCADE -> {int depth=h>=22?5:3;g.box(3,h-depth,w-3,h-depth+2,light);g.box(1,4,3,h-depth,color);}
            default -> {g.box(1,h-3,w-1,h-1,color);}
        }
        if(selected&&enabled){
             
            g.box(w/2-1,h-3,w/2+1,h-1,t.title);
        }
    }
}
