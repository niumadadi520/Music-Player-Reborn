package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.mengsama.mod.mengsamanetmusic.config.MenuThemeConfig;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout;
import com.mengsama.mod.mengsamanetmusic.hud.RosewoodHudLayout;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MenuThemeTest {
    @TempDir Path temp;
    @Test void twentyPaintedChoicesPlusOriginalHaveStableDistinctIds() {
        assertEquals(21,MenuTheme.values().length);
        assertEquals(21,Arrays.stream(MenuTheme.values()).map(t->t.id).distinct().count());
        for(var t:MenuTheme.values())assertSame(t,MenuTheme.fromId(t.id));
        assertSame(MenuTheme.ROSEWOOD,MenuTheme.fromId(null));
        assertSame(MenuTheme.ROSEWOOD,MenuTheme.fromId("../bad-theme"));
    }
    @Test void eachSelectionSurvivesFileReloadWithoutTouchingOtherPreferences() throws Exception {
        Path file=temp.resolve("ui/menu_theme.json"),other=temp.resolve("ui/music_player_ui.json");
        Files.createDirectories(file.getParent());Files.writeString(other,"custom dimensions and background");
        for(var t:MenuTheme.values()) {
            MenuThemeConfig.save(file,t);assertSame(t,MenuThemeConfig.read(file));
            assertEquals("custom dimensions and background",Files.readString(other));
        }
        try(var files=Files.list(file.getParent())){assertEquals(2,files.count());}
    }
    @Test void missingCorruptOrUnknownConfigurationFallsBackWithoutOverwritingFile() throws Exception {
        Path file=temp.resolve("theme.json");assertSame(MenuTheme.ROSEWOOD,MenuThemeConfig.read(file));
        for(String text:List.of("broken", "null", "{}", "{\"theme\":\"future_theme\"}")) {
            Files.writeString(file,text);assertSame(MenuTheme.ROSEWOOD,MenuThemeConfig.read(file));
            assertEquals(text,Files.readString(file));
        }
    }
    @Test void failedSaveCleansTemporaryFileAndDoesNotReplaceDestination() throws Exception {
        Path directory=temp.resolve("occupied");Files.createDirectories(directory);Files.writeString(directory.resolve("keep"),"safe");
        assertThrows(java.io.IOException.class,()->MenuThemeConfig.save(directory,MenuTheme.ENDER));
        assertEquals("safe",Files.readString(directory.resolve("keep")));
        try(var paths=Files.list(temp)){assertEquals(1,paths.count());}
    }
    @Test void textContrastsWithBothMenuAndButtonSurfaces() {
        for(var t:MenuTheme.values()) {
            assertTrue(contrast(t.text,t.surface)>=4.5,t.id+" primary");
            assertTrue(contrast(t.text,t.raised)>=4.5,t.id+" button");
            assertTrue(contrast(t.secondary,t.surface)>=4.5,t.id+" secondary");
            assertTrue(contrast(t.title,t.frame)>=4.5,t.id+" title");
            if(t!=MenuTheme.ROSEWOOD) {
                int selected=0;
                for(int shift:new int[]{16,8,0})selected|=Math.round(((t.accent>>>shift)&255)*(20/255F)+((t.surface>>>shift)&255)*(235/255F))<<shift;
                assertTrue(contrast(t.accent,selected)>=4.5,t.id+" selected song");
            }
        }
    }
    private static double luminance(int color){double sum=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double c=((color>>(16-8*i))&255)/255.;sum+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    @Test void fallbackForOriginalEightThemesKeepsDistinctGeometry() {
        Set<String> geometries=new HashSet<>();
        for(var t:MenuTheme.values())if(t.ordinal()>0&&t.ordinal()<=8){
            List<String> geometry=new ArrayList<>();ThemeArtwork.player((x,y,r,b,c)->geometry.add(x+","+y+","+r+","+b),t,0,0,new RosewoodPlayerLayout(396,408));
            assertTrue(geometries.add(geometry.toString()),t.id);
        }
    }
    @Test void everyHudVisibilityCombinationStaysInsideItsReflowedBounds() {
        for(var theme:MenuTheme.values())for(int mask=0;mask<32;mask++)for(int width:new int[]{8,64,160,360}){
            var l=RosewoodHudLayout.calculate(new RosewoodHudLayout.Elements((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0),9,172,88,55,160,width);
            List<int[]> draws=new ArrayList<>();ThemeArtwork.hud((x,y,r,b,c)->draws.add(new int[]{x,y,r,b}),theme,l);
            assertEquals(l.empty(),draws.isEmpty());
            for(int[] r:draws){assertTrue(r[0]>=0&&r[1]>=0&&r[2]<=l.width()&&r[3]<=l.height(),theme.id+" mask="+mask+" width="+width);}
        }
    }
    @Test void alreadyLoadedSkinResolvesNewTextColorsImmediately() throws Exception {
        var field=MenuThemeConfig.class.getDeclaredField("current");field.setAccessible(true);
        var original=field.get(null);
        try {
            for(var theme:MenuTheme.values()) {
                field.set(null,theme);
                assertEquals(theme.text,com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin.primary());
                assertEquals(theme.secondary,com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin.secondary());
                assertEquals(theme.title,com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin.title());
            }
        } finally {field.set(null,original);}
    }
    @Test void progressHandlesInvalidAndOutOfRangeFractions() {
        assertEquals(0,ThemeArtwork.fraction(Float.NaN));assertEquals(0,ThemeArtwork.fraction(Float.POSITIVE_INFINITY));
        assertEquals(0,ThemeArtwork.fraction(-1));assertEquals(1,ThemeArtwork.fraction(4));assertEquals(.5f,ThemeArtwork.fraction(.5f));
    }
    @Test void exportsPreviewFromProductionGeometry() throws Exception {
        List<Object> themes=new ArrayList<>();
        for(var t:MenuTheme.values())if(t!=MenuTheme.ROSEWOOD){
            List<int[]> draw=new ArrayList<>();ThemeArtwork.Canvas canvas=(x,y,r,b,c)->draw.add(new int[]{x,y,r,b,c});
            var layout=new RosewoodPlayerLayout(396,408);ThemeArtwork.player(canvas,t,0,0,layout);
            for(var control:RosewoodPlayerLayout.Control.values()) {
                var r=layout.control(control);ThemeArtwork.button(canvas,t,r);
                if(control!=RosewoodPlayerLayout.Control.AUTH&&control!=RosewoodPlayerLayout.Control.SOURCE)
                    ThemeArtwork.icon(canvas,t,ThemeButtonArtwork.content(t,r),control,false);
            }
            ThemeArtwork.progress(canvas,t,layout.track(),.37f);
            List<int[]> hud=new ArrayList<>();var h=RosewoodHudLayout.calculate(new RosewoodHudLayout.Elements(true,true,true,true,true),9,120,90,55,200,320);
            ThemeArtwork.hud((x,y,r,b,c)->hud.add(new int[]{x,y,r,b,c}),t,h);
            var track=h.track();ThemeArtwork.progress((x,y,r,b,c)->hud.add(new int[]{x,y,r,b,c}),t,new RosewoodPlayerLayout.Rect(track.x(),track.y(),track.width(),track.height()),.37f);
            List<int[]> login=new ArrayList<>();ThemeArtwork.Canvas lc=(x,y,r,b,c)->login.add(new int[]{x,y,r,b,c});
            ThemeArtwork.panel(lc,t,new RosewoodPlayerLayout.Rect(0,0,260,300),28);
            ThemeArtwork.well(lc,t,new RosewoodPlayerLayout.Rect(54,66,152,152));lc.fill(58,70,202,214,0xFFFFFFFF);
            ThemeArtwork.button(lc,t,new RosewoodPlayerLayout.Rect(28,258,96,24));ThemeArtwork.button(lc,t,new RosewoodPlayerLayout.Rect(136,258,96,24));
            var data=new LinkedHashMap<String,Object>();data.put("id",t.id);data.put("label",t.label);data.put("description",t.description);
            data.put("controls",Arrays.stream(RosewoodPlayerLayout.Control.values()).collect(java.util.stream.Collectors.toMap(Enum::name,layout::control)));data.put("content",layout.content());data.put("searchField",layout.searchField());data.put("track",layout.track());data.put("palette",new int[]{t.surface,t.raised,t.frame,t.edge,t.text,t.secondary,t.accent,t.title});data.put("draw",draw);data.put("hud",hud);data.put("hudLayout",h);data.put("login",login);
            List<Object> variants=new ArrayList<>();
            for(int mask=0;mask<32;mask++) {
                var layoutHud=RosewoodHudLayout.calculate(new RosewoodHudLayout.Elements((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0),9,120,90,55,200,320);
                List<int[]> shapes=new ArrayList<>();ThemeArtwork.Canvas hc=(x,y,r,b,c)->shapes.add(new int[]{x,y,r,b,c});
                ThemeArtwork.hud(hc,t,layoutHud);
                var ht=layoutHud.track();if(!ht.empty())ThemeArtwork.progress(hc,t,new RosewoodPlayerLayout.Rect(ht.x(),ht.y(),ht.width(),ht.height()),.37f);
                variants.add(Map.of("draw",shapes,"layout",layoutHud));
            }
            data.put("hudVariants",variants);
            List<Object> samples=new ArrayList<>();
            for(int state=0;state<4;state++) {
                List<int[]> pixels=new ArrayList<>();ThemeArtwork.Canvas bc=(x,y,right,bottom,color)->pixels.add(new int[]{x,y,right,bottom,color});
                var nav=new RosewoodPlayerLayout.Rect(8,8,75,23);
                var play=new RosewoodPlayerLayout.Rect(97,5,34,30);
                var label=new RosewoodPlayerLayout.Rect(146,8,96,23);
                for(var area:List.of(nav,play,label))ThemeArtwork.button(bc,t,area);
                ThemeArtwork.icon(bc,t,ThemeButtonArtwork.content(t,nav),RosewoodPlayerLayout.Control.NAV_PLAYLIST,false);
                ThemeArtwork.icon(bc,t,ThemeButtonArtwork.content(t,play),RosewoodPlayerLayout.Control.PLAY,state==2);
                for(var area:List.of(nav,play,label))ThemeButtonArtwork.feedback(bc,t,area,state==1,state==2,state!=3);
                samples.add(pixels);
            }
            data.put("buttonStates",samples);
            Map<String,Object> icons=new LinkedHashMap<>();
            for(var control:RosewoodPlayerLayout.Control.values()){
                var bounds=layout.control(control);var area=ThemeAtlasLayout.content(bounds);
                if(control==RosewoodPlayerLayout.Control.MODE)area=new RosewoodPlayerLayout.Rect(area.x(),area.y(),Math.max(1,area.width()-11),area.height());
                if(control==RosewoodPlayerLayout.Control.VOLUME)area=new RosewoodPlayerLayout.Rect(bounds.x()+5,bounds.y()+3,Math.max(1,bounds.width()-10),Math.max(1,bounds.height()-15));
                var icon=area;
                List<int[]> pixels=new ArrayList<>();ThemeArtwork.icon((x,y,right,bottom,color)->pixels.add(new int[]{x,y,right,bottom,color}),t,icon,control,false);icons.put(control.name(),pixels);
                if(control==RosewoodPlayerLayout.Control.PLAY){List<int[]> paused=new ArrayList<>();ThemeArtwork.icon((x,y,right,bottom,color)->paused.add(new int[]{x,y,right,bottom,color}),t,icon,control,true);icons.put("PAUSE",paused);}
            }
            data.put("icons",icons);

            Map<String,Object> smallButtons=new LinkedHashMap<>();
            for(int[] size:new int[][]{{40,16},{20,16},{66,18},{59,18},{96,24}}){
                List<int[]> shapes=new ArrayList<>();ThemeArtwork.button((x,y,right,bottom,color)->shapes.add(new int[]{x,y,right,bottom,color}),t,new RosewoodPlayerLayout.Rect(0,0,size[0],size[1]));
                smallButtons.put(size[0]+"x"+size[1],shapes);
            }
            data.put("smallButtons",smallButtons);themes.add(data);
            assertTrue(draw.size()<3600,"bounded native drawing cost");
        }
        Path output=Path.of("build/theme-preview.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new com.google.gson.Gson().toJson(themes));assertEquals(20,themes.size());
    }
}
