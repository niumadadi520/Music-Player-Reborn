package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThemeButtonArtworkTest {
    @Test void allEightThemesHaveDifferentOpaqueSilhouettesAtRealButtonSizes(){
        for(var r:List.of(new Rect(0,0,75,23),new Rect(0,0,34,30),new Rect(0,0,96,24))){
            Set<BitSet> silhouettes=new HashSet<>();
            for(var theme:MenuTheme.values())if(theme.ordinal()>0&&theme.ordinal()<=8){
                BitSet mask=new BitSet(r.width()*r.height());
                ThemeArtwork.button((x,y,right,bottom,color)->{for(int row=y;row<bottom;row++)for(int col=x;col<right;col++)mask.set(row*r.width()+col);},theme,r);
                assertTrue(silhouettes.add(mask),theme.id+" has the same silhouette at "+r);
                assertTrue(mask.cardinality()<r.width()*r.height(),theme.id+" must have shaped edges");
            }
        }
    }
    @Test void everyControlAndFeedbackStateStaysInsideExistingClickBounds(){
        for(var t:MenuTheme.values())for(var r:List.of(new Rect(13,21,1,1),new Rect(13,21,8,7),new Rect(13,21,20,16),new Rect(13,21,40,16),new Rect(13,21,75,23),new Rect(13,21,260,22))){
            ThemeArtwork.Canvas checked=(x,y,right,bottom,color)->assertTrue(x>=r.x()&&y>=r.y()&&right<=r.right()&&bottom<=r.bottom()&&right>x&&bottom>y,t.id+" "+r);
            ThemeArtwork.button(checked,t,r);
            for(var c:RosewoodPlayerLayout.Control.values()){
                ThemeArtwork.icon(checked,t,ThemeButtonArtwork.content(t,r),c,false);
                ThemeArtwork.icon(checked,t,ThemeButtonArtwork.content(t,r),c,true);
            }
            for(int flags=0;flags<8;flags++)ThemeButtonArtwork.feedback(checked,t,r,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0);
        }
    }
    @Test void playAndPauseRemainVisuallyDifferentInEveryTheme(){
        for(var t:MenuTheme.values()){
            List<String> play=new ArrayList<>(),pause=new ArrayList<>();var r=new Rect(0,0,18,18);
            ThemeArtwork.icon((x,y,right,bottom,color)->play.add(x+","+y+","+right+","+bottom+","+color),t,r,RosewoodPlayerLayout.Control.PLAY,false);
            ThemeArtwork.icon((x,y,right,bottom,color)->pause.add(x+","+y+","+right+","+bottom+","+color),t,r,RosewoodPlayerLayout.Control.PLAY,true);
            assertNotEquals(play,pause,t.id);
        }
    }
    @Test void hoveredSelectedAndDisabledMaterialFeedbackAreDistinct(){
        for(var t:MenuTheme.values())if(t!=MenuTheme.ROSEWOOD){
            Set<String> states=new HashSet<>();
            for(int state=0;state<4;state++){
                List<String> pixels=new ArrayList<>();
                ThemeButtonArtwork.feedback((x,y,right,bottom,color)->pixels.add(x+","+y+","+right+","+bottom+","+color),t,new Rect(0,0,75,23),state==1,state==2,state!=3);
                assertTrue(states.add(pixels.toString()),t.id+" state="+state);
            }
        }
    }
    @Test void labelsRetainRoomForAtLeastOneGlyphWithoutLeavingTheirButtons(){
        for(var t:MenuTheme.values())for(var r:List.of(new Rect(4,6,20,16),new Rect(4,6,40,16),new Rect(4,6,48,18),new Rect(4,6,96,24))){
            var content=ThemeButtonArtwork.content(t,r);
            assertTrue(content.x()>=r.x()&&content.y()>=r.y()&&content.right()<=r.right()&&content.bottom()<=r.bottom());
            assertTrue(content.width()>=7&&content.height()>=7);
        }
    }
}
