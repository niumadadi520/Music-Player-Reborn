package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import java.util.ArrayList;
import java.util.List;

 
public final class ThemeAtlasLayout {
    public static final int TEXTURE_SIZE=128;
    public record Tile(Rect destination, Rect source) {}
    private ThemeAtlasLayout() {}
    public static int state(boolean hovered,boolean selected,boolean enabled){return !enabled?3:selected?2:hovered?1:0;}
    private static int buttonBorder(Rect r){return Math.min(r.width()/3,r.width()>=64?Math.min(32,r.height()*72/100):Math.min(10,r.height()/3));}
    public static Rect content(Rect r){
        int x=Math.min(buttonBorder(r),Math.max(0,(r.width()-8)/2)),y=Math.min(4,Math.max(0,(r.height()-8)/2));
        return new Rect(r.x()+x,r.y()+y,Math.max(1,r.width()-2*x),Math.max(1,r.height()-2*y));
    }
    public static List<Tile> slices(Rect r,Rect source,boolean panel){
        List<Tile> result=new ArrayList<>(9);
        if(r.width()<=0||r.height()<=0)return result;
        int bx=panel?Math.min(12,r.width()/3):buttonBorder(r),by=Math.min(panel?10:4,r.height()/3);
         
        int sx=Math.max(1,Math.round(source.width()/10F)*2),sy=Math.max(1,Math.round(source.height()*(panel?.40F:.18F)/2)*2);
        int[] dx={r.x(),r.x()+bx,r.right()-bx,r.right()},dy={r.y(),r.y()+by,r.bottom()-by,r.bottom()};
        int[] ux={source.x(),source.x()+sx,source.right()-sx,source.right()},uy={source.y(),source.y()+sy,source.bottom()-sy,source.bottom()};
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)if(dx[x+1]>dx[x]&&dy[y+1]>dy[y])
            result.add(new Tile(new Rect(dx[x],dy[y],dx[x+1]-dx[x],dy[y+1]-dy[y]),new Rect(ux[x],uy[y],ux[x+1]-ux[x],uy[y+1]-uy[y])));
        return result;
    }
}
