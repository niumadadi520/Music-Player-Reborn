package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintedThemeTest {
    @Test void paginationReachesEveryThemeExactlyOnceAndClampsAtBothEnds(){
        Set<MenuTheme> found=EnumSet.noneOf(MenuTheme.class);
        assertEquals(4,ThemePages.count());assertEquals(0,ThemePages.clamp(-20));assertEquals(3,ThemePages.clamp(99));
        for(int page=0;page<ThemePages.count();page++){
            assertTrue(ThemePages.end(page)-ThemePages.first(page)<=6);
            for(int i=ThemePages.first(page);i<ThemePages.end(page);i++){
                assertTrue(found.add(MenuTheme.values()[i]));assertEquals(page,ThemePages.containing(MenuTheme.values()[i]));
            }
        }
        assertEquals(EnumSet.allOf(MenuTheme.class),found);
    }
    @Test void allTwentyPixelAtlasesHaveFourDistinctValidStates() throws Exception {
        Path base=Path.of("src/main/resources/assets/mengsamanetmusic/textures/gui/themes");
        var regions=JsonParser.parseString(Files.readString(base.resolve("regions.json"))).getAsJsonObject();
        Set<String> hashes=new HashSet<>();
        for(var theme:MenuTheme.values())if(theme!=MenuTheme.ROSEWOOD){
            Path path=base.resolve(theme.id+".png");byte[] png=Files.readAllBytes(path);
            assertTrue(hashes.add(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png))),theme.id);
            var image=ImageIO.read(path.toFile());assertNotNull(image);assertTrue(image.getColorModel().hasAlpha());
            assertEquals(128,image.getWidth());assertEquals(128,image.getHeight());assertTrue(png.length<8192,theme.id+" asset budget");
            Set<Integer> colors=new HashSet<>();
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){int pixel=image.getRGB(x,y);colors.add(pixel);int alpha=pixel>>>24;assertTrue(alpha==0||alpha==255,theme.id+" crisp alpha");}
            assertTrue(colors.size()<=24,theme.id+" limited palette");
            for(int y=0;y<128;y+=2)for(int x=0;x<128;x+=2){int pixel=image.getRGB(x,y);assertEquals(pixel,image.getRGB(x+1,y),theme.id+" pixel grid");assertEquals(pixel,image.getRGB(x,y+1),theme.id+" pixel grid");assertEquals(pixel,image.getRGB(x+1,y+1),theme.id+" pixel grid");}
            var states=regions.getAsJsonArray(theme.id);assertEquals(4,states.size());
            int last=0;Set<List<Integer>> samples=new HashSet<>();
            for(var state:states){
                int start=state.getAsJsonArray().get(0).getAsInt(),end=state.getAsJsonArray().get(1).getAsInt();
                assertTrue(start>=last&&end>start&&end<=128,theme.id);last=end;
                List<Integer> pixels=new ArrayList<>();
                for(int y=1;y<10;y++)for(int x=1;x<20;x++)pixels.add(image.getRGB(image.getWidth()*x/20,Math.min(image.getHeight()-1,(start+(end-start)*y/10)*image.getHeight()/128)));
                assertTrue(samples.add(pixels),theme.id+" state must have its own painted pixels");
            }
        }
        assertEquals(20,hashes.size());assertEquals(20,regions.size());
    }
    @Test void nineSliceCoversBoundsWithoutOverlapAndNeverSamplesAnotherState(){
        for(boolean panel:new boolean[]{false,true})for(int w:new int[]{1,8,20,40,75,260,480})for(int h:new int[]{1,5,16,24,80,408}){
            Rect target=new Rect(13,17,w,h),source=new Rect(0,32,128,32);
            int area=0;List<Rect> covered=new ArrayList<>();
            var slices=ThemeAtlasLayout.slices(target,source,panel);assertTrue(slices.size()<=9);
            for(var tile:slices){var d=tile.destination();var s=tile.source();
                assertTrue(d.x()>=target.x()&&d.y()>=target.y()&&d.right()<=target.right()&&d.bottom()<=target.bottom());
                assertTrue(s.x()>=source.x()&&s.y()>=source.y()&&s.right()<=source.right()&&s.bottom()<=source.bottom());
                for(var previous:covered)assertFalse(previous.overlaps(d));covered.add(d);area+=d.width()*d.height();
                assertEquals(0,s.x()%2);assertEquals(0,s.y()%2);assertEquals(0,s.width()%2);assertEquals(0,s.height()%2);
            }
            assertEquals(w*h,area);
        }
    }
    @Test void disabledStateWinsOverHoverAndSelection(){
        for(int flags=0;flags<4;flags++)assertEquals(3,ThemeAtlasLayout.state((flags&1)!=0,(flags&2)!=0,false));
        assertEquals(2,ThemeAtlasLayout.state(true,true,true));assertEquals(1,ThemeAtlasLayout.state(true,false,true));assertEquals(0,ThemeAtlasLayout.state(false,false,true));
    }
    @Test void textContentStaysInsideEvenNarrowControlBounds(){
        for(int w:new int[]{1,8,20,40,48,75,96,260})for(int h:new int[]{1,5,16,18,24,30}){
            Rect r=new Rect(4,6,w,h),c=ThemeAtlasLayout.content(r);
            assertTrue(c.x()>=r.x()&&c.y()>=r.y()&&c.right()<=r.right()&&c.bottom()<=r.bottom());
        }
    }
}
