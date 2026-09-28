package com.mengsama.mod.mengsamanetmusic.listening;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

 
public final class ListeningNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("mengsamanetmusic","listening"),
            ()->"1",v->v.equals("1") || v.equals(NetworkRegistry.ABSENT),v->v.equals("1") || v.equals(NetworkRegistry.ABSENT));
    public record Sample(String target,long generation) {}
    public record Beat(List<Sample> samples) {
        public Beat {samples=List.copyOf(samples);if(samples.size()>32) throw new IllegalArgumentException("Too many listening sources");}
        public void encode(FriendlyByteBuf b) {b.writeVarInt(samples.size());for(var s:samples){b.writeUtf(s.target,512);b.writeLong(s.generation);}}
        public static Beat decode(FriendlyByteBuf b) {
            int n=b.readVarInt();if(n<0 || n>32) throw new IllegalArgumentException("Invalid listening source count");
            List<Sample> rows=new ArrayList<>();for(int i=0;i<n;i++)rows.add(new Sample(b.readUtf(512),b.readLong()));return new Beat(rows);
        }
    }
    public record Query(long request,boolean players,int page) {
        public void encode(FriendlyByteBuf b){b.writeLong(request);b.writeBoolean(players);b.writeVarInt(page);}
        public static Query decode(FriendlyByteBuf b){return new Query(b.readLong(),b.readBoolean(),b.readVarInt());}
    }
    public record Board(long request,boolean players,int page,int pages,List<ListeningLedger.Row> rows) {
        public Board {rows=List.copyOf(rows);if(rows.size()>20)throw new IllegalArgumentException("Ranking page too large");}
        public void encode(FriendlyByteBuf b){b.writeLong(request);b.writeBoolean(players);b.writeVarInt(page);b.writeVarInt(pages);b.writeVarInt(rows.size());
            for(var r:rows){b.writeUtf(r.key(),512);b.writeUtf(r.title(),160);b.writeUtf(r.detail(),160);b.writeLong(r.value());}}
        public static Board decode(FriendlyByteBuf b){
            long request=b.readLong();boolean players=b.readBoolean();int page=b.readVarInt(),pages=b.readVarInt(),n=b.readVarInt();
            if(page<1 || pages<page || pages>5000 || n<0 || n>20)throw new IllegalArgumentException("Invalid ranking page");
            List<ListeningLedger.Row> rows=new ArrayList<>();for(int i=0;i<n;i++)rows.add(new ListeningLedger.Row(b.readUtf(512),b.readUtf(160),b.readUtf(160),Math.max(0,b.readLong())));
            return new Board(request,players,page,pages,rows);
        }
    }
    public static void init(){
        CHANNEL.registerMessage(0,Beat.class,Beat::encode,Beat::decode,(p,s)->{var c=s.get();var sender=c.getSender();if(sender!=null)c.enqueueWork(()->ListeningServer.heartbeat(sender,p.samples));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Query.class,Query::encode,Query::decode,(p,s)->{var c=s.get();var sender=c.getSender();if(sender!=null)c.enqueueWork(()->ListeningServer.query(sender,p));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Board.class,Board::encode,Board::decode,(p,s)->com.mengsama.mod.mengsamanetmusic.network.ClientPacketDispatch.accept(s,()->com.mengsama.mod.mengsamanetmusic.gui.ListeningRankingScreen.receive(p)),Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    static void reply(ServerPlayer player,Board board){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),board);}
}
