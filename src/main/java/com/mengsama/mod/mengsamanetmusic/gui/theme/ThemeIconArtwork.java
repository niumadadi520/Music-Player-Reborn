package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;

 
final class ThemeIconArtwork {
    private ThemeIconArtwork() {}
    static void draw(ThemeArtwork.Canvas g,MenuTheme theme,Rect r,String[] pixels){
        int scale=Math.max(1,Math.min(r.width(),r.height())/7);
        int startX=r.x()+(r.width()-7*scale)/2,startY=r.y()+(r.height()-7*scale)/2;
        for(int row=0;row<7;row++)for(int col=0;col<7;col++)if(pixels[row].charAt(col)=='1'){
            int x=Math.max(r.x(),startX+col*scale),y=Math.max(r.y(),startY+row*scale);
            int right=Math.min(r.right(),startX+(col+1)*scale),bottom=Math.min(r.bottom(),startY+(row+1)*scale);
            if(right>x&&bottom>y)g.fill(x,y,right,bottom,theme.text);
        }
    }
}
