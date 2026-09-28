package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT)
public final class ListeningClient {
    private static int ticks;
    public static boolean available(){
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && ListeningNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null){ticks=0;return;}
        if(mc.isPaused() || ++ticks<20)return;
        ticks=0;
        if(available())ListeningNetwork.CHANNEL.sendToServer(new ListeningNetwork.Beat(ClientMusicPlayback.listeningSources()));
    }
}
