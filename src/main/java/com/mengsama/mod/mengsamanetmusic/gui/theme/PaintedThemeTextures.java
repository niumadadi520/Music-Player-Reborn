package com.mengsama.mod.mengsamanetmusic.gui.theme;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

 
@Mod.EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PaintedThemeTextures {
    public static final int CACHE_LIMIT=8;
    private static final int SIZE=ThemeAtlasLayout.TEXTURE_SIZE;
    private record Atlas(ResourceLocation texture,Rect[] rows) {}
    private static final Map<MenuTheme,Atlas> CACHE=new LinkedHashMap<>(8,.75f,true);
    private static final Set<MenuTheme> FAILED=EnumSet.noneOf(MenuTheme.class);
    private PaintedThemeTextures() {}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->{
            var minecraft=Minecraft.getInstance();
            minecraft.execute(()->{CACHE.values().forEach(a->minecraft.getTextureManager().release(a.texture));CACHE.clear();FAILED.clear();});
        });
    }
    private static Atlas get(MenuTheme theme){
        if(theme==MenuTheme.ROSEWOOD||FAILED.contains(theme))return null;
        Atlas cached=CACHE.get(theme);if(cached!=null)return cached;
        var mc=Minecraft.getInstance();var manager=mc.getResourceManager();
        NativeImage reduced=null;DynamicTexture dynamic=null;boolean registered=false;
        try(var input=manager.getResourceOrThrow(new ResourceLocation("mengsamanetmusic","textures/gui/themes/"+theme.id+".png")).open();
            var original=NativeImage.read(input);
            var metadata=new InputStreamReader(manager.getResourceOrThrow(new ResourceLocation("mengsamanetmusic","textures/gui/themes/regions.json")).open(),StandardCharsets.UTF_8)){
            var rows=JsonParser.parseReader(metadata).getAsJsonObject().getAsJsonArray(theme.id);
            if(rows==null||rows.size()!=4)throw new java.io.IOException("Missing four button states");
            Rect[] regions=new Rect[4];
            for(int i=0;i<4;i++){
                var pair=rows.get(i).getAsJsonArray();int y=pair.get(0).getAsInt(),end=pair.get(1).getAsInt();
                if(y<0||end>SIZE||end<=y)throw new java.io.IOException("Invalid atlas region");
                regions[i]=new Rect(0,y,SIZE,end-y);
            }
            reduced=new NativeImage(SIZE,SIZE,false);
            original.resizeSubRectTo(0,0,original.getWidth(),original.getHeight(),reduced);
            dynamic=new DynamicTexture(reduced);reduced=null;dynamic.setFilter(false,false);
            var id=new ResourceLocation("mengsamanetmusic","painted_theme/"+theme.id);
            mc.getTextureManager().register(id,dynamic);registered=true;
            Atlas atlas=new Atlas(id,regions);CACHE.put(theme,atlas);
            while(CACHE.size()>CACHE_LIMIT){var iterator=CACHE.entrySet().iterator();var oldest=iterator.next();mc.getTextureManager().release(oldest.getValue().texture);iterator.remove();}
            return atlas;
        }catch(Exception failure){
            FAILED.add(theme);com.mojang.logging.LogUtils.getLogger().warn("Unable to load painted menu theme {}; using built-in fallback",theme.id,failure);return null;
        }finally{if(reduced!=null)reduced.close();if(dynamic!=null&&!registered)dynamic.close();}
    }
    public static void draw(GuiGraphics g,MenuTheme theme,Rect r,int state,boolean panel){
        Atlas atlas=get(theme);
        if(atlas==null){ThemeSkin.paint(g,c->{ThemeArtwork.button(c,theme,r);ThemeButtonArtwork.feedback(c,theme,r,state==1,state==2,state!=3);});return;}
        for(var tile:ThemeAtlasLayout.slices(r,atlas.rows[Math.max(0,Math.min(3,state))],panel)){
            Rect d=tile.destination(),s=tile.source();
            g.blit(atlas.texture,d.x(),d.y(),d.width(),d.height(),s.x(),s.y(),s.width(),s.height(),SIZE,SIZE);
        }
    }
}
