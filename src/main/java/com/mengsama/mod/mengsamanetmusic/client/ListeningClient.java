package com.mengsama.mod.mengsamanetmusic.client;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT)
public final class ListeningClient {
    private static int ticks;
    public static boolean available(){
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && ListeningNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null){ticks=0;return;}
        if(mc.isPaused() || ++ticks<20)return;
        ticks=0;
        if(available())ListeningNetwork.CHANNEL.sendToServer(new ListeningNetwork.Beat(ClientMusicPlayback.listeningSources()));
    }
}
