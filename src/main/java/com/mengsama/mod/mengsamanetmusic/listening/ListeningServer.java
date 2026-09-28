package com.mengsama.mod.mengsamanetmusic.listening;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.network.*;
import com.mengsama.mod.mengsamanetmusic.earbuds.EarbudAudioPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import com.mengsama.mod.mengsamanetmusic.listening.ListeningPlaybackBook.Play;

 
@Mod.EventBusSubscriber(modid="mengsamanetmusic")
public final class ListeningServer {
    private static final Map<MinecraftServer,State> STATES = new WeakHashMap<>();
    private static final class State {
        final Map<UUID,Map<String,Delivery>> listeners=new HashMap<>();
        final ListeningPlaybackBook plays=new ListeningPlaybackBook();
        final Map<String,Long> seeking=new HashMap<>();
        final Map<UUID,Long> beats=new HashMap<>(), queries=new HashMap<>();
    }
    private static final class Delivery {
        final Play play; final long generation; final ResourceKey<Level> dimension; final Vec3 position;
        final int entity; final boolean privateAudio; final long expires;
        boolean paused;
        Delivery(Play play,long generation,ServerPlayer listener,Vec3 position,int entity,boolean privateAudio,int duration) {
            this.play=play;this.generation=generation;this.dimension=listener.level().dimension();this.position=position;
            this.entity=entity;this.privateAudio=privateAudio;
            expires=listener.server.overworld().getGameTime()+Math.max(0L,duration)*20+200;
        }
        boolean valid(ServerPlayer player) {
            if (paused || player.server.overworld().getGameTime()>expires || !dimension.equals(player.level().dimension())) return false;
            if (privateAudio) return true;  
            var source=entity >= 0 ? player.level().getEntity(entity) : null;
            Vec3 location=source == null ? position : source.position();
            return location != null && player.position().distanceToSqr(location)<48*48;
        }
    }
    private static State state(MinecraftServer server) { return STATES.computeIfAbsent(server,ignored->new State()); }
    public static void preserveNext(ServerPlayer player,String target) {
        State state=state(player.server);
        long now=player.server.overworld().getGameTime();
        state.seeking.entrySet().removeIf(e->e.getValue()<now);
        state.seeking.put(target,now+200);
    }
    public static void delivered(Object message,ServerPlayer listener) {
        State state=state(listener.server);
        Map<String,Delivery> map=state.listeners.computeIfAbsent(listener.getUUID(),ignored->new LinkedHashMap<>());
        if (message instanceof StopMusicPacketClient stop) {
            if (stop.targetId().startsWith("device:")) {
                UUID id=com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(stop.targetId());
                if (id != null) map.keySet().removeIf(t->id.equals(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(t)));
            } else map.remove(stop.targetId());
            return;
        }
        if (message instanceof PauseMusicPacketClient pause) {
            Delivery d=map.get(pause.targetId());
            if(d != null && (pause.requestGeneration()==Long.MIN_VALUE || pause.requestGeneration()>=d.generation)) d.paused=pause.paused();
            return;
        }
        boolean privateAudio=message instanceof EarbudAudioPacket;
        if (message instanceof EarbudAudioPacket earbud) message=earbud.play();
        String target; SongInfo song; long generation,refresh; int start,duration,entity=-1; Vec3 position=null;
        if (message instanceof PlayerPlayMusicPacket p) {
            target=p.targetId();song=p.info();generation=p.requestGeneration();refresh=p.refreshNonce();start=p.startSecond();duration=p.timeSecond();entity=p.playerID();
        } else if(message instanceof PlayMusicPacket p) {
            target=p.statisticsTarget();song=p.statisticsSong();generation=p.statisticsGeneration();refresh=p.statisticsRefresh();start=p.statisticsStart();duration=p.getTimeSecond();position=Vec3.atCenterOf(p.getPos());
        } else return;
        if (song==null || target==null || target.isBlank()) return;
        long now=listener.server.overworld().getGameTime();
        Play previous=state.plays.get(target);
        boolean continuation=refresh!=0 || start>0 || state.seeking.getOrDefault(target,Long.MIN_VALUE)>=now;
        boolean newGeneration=previous==null || generation!=previous.generation;
        Play play=state.plays.accept(target,song,generation,continuation);
        if(play==null)return;
        if(newGeneration)state.seeking.remove(target);
        map.entrySet().removeIf(e->e.getValue().expires<now);
        map.put(target,new Delivery(play,generation,listener,position,entity,privateAudio,duration-start));
        if(map.size()>64) map.remove(map.keySet().iterator().next());
    }
    public static void heartbeat(ServerPlayer player,List<ListeningNetwork.Sample> samples) {
        State state=state(player.server);long now=player.server.overworld().getGameTime();
        Long last=state.beats.get(player.getUUID());
        if(last!=null && now-last<15) return;
        state.beats.put(player.getUUID(),now);
        Map<String,Delivery> delivered=state.listeners.getOrDefault(player.getUUID(),Map.of());
        ListeningData data=ListeningData.get(player.server);
        if(!data.writable()) return;
        long revision=data.ledger.revision();boolean heard=false;
        for(var sample:samples) {
            Delivery d=delivered.get(sample.target());
            if(d==null || d.generation!=sample.generation() || !d.valid(player)) continue;
             
            if (!PlaybackRefreshSessions.isPublished(sample.target()) || state.plays.get(sample.target()) != d.play) continue;
            heard=true;
            d.play.heard(data.ledger);
        }
        data.ledger.sample(player.getUUID(),player.getGameProfile().getName(),now,heard);
        if(revision!=data.ledger.revision()) data.setDirty();
    }
    public static void query(ServerPlayer player,ListeningNetwork.Query query) {
        State state=state(player.server);long now=player.server.overworld().getGameTime();
        Long last=state.queries.get(player.getUUID());
        if(last!=null && now-last<5) return;
        state.queries.put(player.getUUID(),now);
        ListeningData data=ListeningData.get(player.server);
        List<ListeningLedger.Row> rows=data.ranking(query.players(),now);
        int pages=Math.max(1,(rows.size()+19)/20), page=Math.max(1,Math.min(pages,query.page()));
        int first=(page-1)*20;
        ListeningNetwork.reply(player,new ListeningNetwork.Board(query.request(),query.players(),page,pages,
                rows.subList(first,Math.min(first+20,rows.size()))));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            State state=state(player.server);UUID id=player.getUUID();
            state.listeners.remove(id);state.beats.remove(id);state.queries.remove(id);ListeningData.get(player.server).ledger.forget(id);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {STATES.remove(event.getServer());}
}
