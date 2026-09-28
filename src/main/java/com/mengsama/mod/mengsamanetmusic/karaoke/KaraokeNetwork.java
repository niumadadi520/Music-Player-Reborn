package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import com.mengsama.mod.mengsamanetmusic.platform.ClientDispatch;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;
import com.mengsama.mod.mengsamanetmusic.platform.PayloadChannel.Direction;
import com.mengsama.mod.mengsamanetmusic.platform.PayloadChannel;
import java.util.*;
import java.util.function.Supplier;

public final class KaraokeNetwork {
    private static final PayloadChannel CHANNEL = new PayloadChannel("karaoke", "1", false);
    public enum Action { REFRESH, TOGGLE, ADD_CONNECTION, REMOVE_CONNECTION, SET_VOLUME, STOP }
    public static void init() {
        CHANNEL.registerMessage(0, Control.class, Control::encode, Control::decode, Control::handle, Optional.of(Direction.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(Direction.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Capture.class, Capture::encode, Capture::decode, Capture::handle, Optional.of(Direction.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, Voice.class, Voice::encode, Voice::decode, Voice::handle, Optional.of(Direction.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, Failure.class, Failure::encode, Failure::decode, Failure::handle, Optional.of(Direction.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, Pulse.class, Pulse::encode, Pulse::decode, Pulse::handle, Optional.of(Direction.PLAY_TO_CLIENT));
    }
    public static void sendAction(int containerId, Action action, String value) {
        if(action==Action.TOGGLE && "true".equals(value))
            com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceClient.authorizeStart();
        else if(action==Action.STOP || action==Action.TOGGLE && "false".equals(value))
            com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceClient.reset();
        CHANNEL.sendToServer(new Control(containerId, action, value));
    }
    public static void sendVoice(UUID nonce, byte[] opus) {
        if (opus != null && opus.length > 0 && opus.length <= 2048) CHANNEL.sendToServer(new Voice(nonce, opus));
    }
    public static void captureFailed(UUID nonce, String reason) { CHANNEL.sendToServer(new Failure(nonce, reason == null ? "无法启动麦克风" : reason.substring(0, Math.min(160, reason.length())))); }
    public static void sendState(ServerPlayer player, KaraokeState state) { CHANNEL.sendToPlayer(player, new Snapshot(state)); }
    public static void capture(ServerPlayer player, UUID nonce) { CHANNEL.sendToPlayer(player, new Capture(nonce)); }
    public static void pulse(KaraokeBlockEntity device, long until) {
        if (!(device.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var packet = new Pulse(device.getBlockPos(), device.deviceId(), until);
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(device.getBlockPos())) < 48*48)
            CHANNEL.sendToPlayer(player, packet);
    }
    private record Control(int containerId, Action action, String value) {
        static void encode(Control p, FriendlyByteBuf b) { b.writeVarInt(p.containerId); b.writeEnum(p.action); b.writeUtf(p.value, 160); }
        static Control decode(FriendlyByteBuf b) { return new Control(b.readVarInt(), b.readEnum(Action.class), b.readUtf(160)); }
        static void handle(Control p, Supplier<PacketContext> supplier) {
            var c=supplier.get(); var sender=c.getSender();
            if(sender!=null && KaraokeServer.allowControl(sender.getUUID())) c.enqueueWork(() -> KaraokeServer.action(sender, p.containerId, p.action, p.value));
            
        }
    }
    private record Snapshot(KaraokeState state) {
        static void encode(Snapshot p, FriendlyByteBuf b) {
            var s=p.state; b.writeVarInt(s.containerId()); b.writeEnum(s.kind()); b.writeBoolean(s.microphoneId()!=null);
            if(s.microphoneId()!=null)b.writeUUID(s.microphoneId());
            b.writeBoolean(s.enabled());b.writeBoolean(s.mine());b.writeBoolean(s.voiceAvailable());b.writeVarInt(s.volume());
            b.writeVarInt(s.connections().size());for(String code:s.connections())b.writeUtf(code,36);b.writeUtf(s.message(),200);
        }
        static Snapshot decode(FriendlyByteBuf b) {
            int menu=b.readVarInt();var kind=b.readEnum(KaraokeState.Kind.class);UUID mic=b.readBoolean()?b.readUUID():null;
            boolean enabled=b.readBoolean(),mine=b.readBoolean(),voice=b.readBoolean();int volume=KaraokeCode.volume(b.readVarInt());
            int size=b.readVarInt();if(size<0||size>KaraokeCode.MAX_CONNECTIONS)throw new IllegalArgumentException("Too many microphone links");
            List<String> ids=new ArrayList<>();for(int i=0;i<size;i++)ids.add(b.readUtf(36));
            return new Snapshot(new KaraokeState(menu,kind,mic,enabled,mine,voice,volume,ids,b.readUtf(200)));
        }
        static void handle(Snapshot p, Supplier<PacketContext> supplier) {
            var c=supplier.get();c.enqueueWork(() -> ClientDispatch.runWhenOn(Dist.CLIENT, () -> () ->
                    com.mengsama.mod.mengsamanetmusic.karaoke.client.KaraokeUi.accept(p.state)));
        }
    }
    private record Capture(UUID nonce) {
        static void encode(Capture p,FriendlyByteBuf b){b.writeBoolean(p.nonce!=null);if(p.nonce!=null)b.writeUUID(p.nonce);}
        static Capture decode(FriendlyByteBuf b){return new Capture(b.readBoolean()?b.readUUID():null);}
        static void handle(Capture p,Supplier<PacketContext> supplier){var c=supplier.get();c.enqueueWork(()->
                ClientDispatch.runWhenOn(Dist.CLIENT,()->()->{
                    if(p.nonce==null)com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceClient.stopFromServer();
                    else com.mengsama.mod.mengsamanetmusic.karaoke.voice.KaraokeVoiceClient.setSession(p.nonce);
                }));}
    }
    private record Voice(UUID nonce,byte[] opus) {
        static void encode(Voice p,FriendlyByteBuf b){b.writeUUID(p.nonce);b.writeByteArray(p.opus);}
        static Voice decode(FriendlyByteBuf b){return new Voice(b.readUUID(),b.readByteArray(2048));}
        static void handle(Voice p,Supplier<PacketContext> supplier){var c=supplier.get();var sender=c.getSender();
            if(sender!=null&&p.opus.length>0&&KaraokeServer.allowVoice(sender.getUUID(),p.nonce))
                c.enqueueWork(()->KaraokeServer.receiveVoice(sender,p.nonce,p.opus));}
    }
    private record Failure(UUID nonce,String reason) {
        static void encode(Failure p,FriendlyByteBuf b){b.writeUUID(p.nonce);b.writeUtf(p.reason,160);}
        static Failure decode(FriendlyByteBuf b){return new Failure(b.readUUID(),b.readUtf(160));}
        static void handle(Failure p,Supplier<PacketContext> supplier){var c=supplier.get();var sender=c.getSender();
            if(sender!=null&&KaraokeServer.allowControl(sender.getUUID()))c.enqueueWork(()->KaraokeServer.captureFailed(sender,p.nonce,p.reason));}
    }
    private record Pulse(net.minecraft.core.BlockPos pos, UUID id, long until) {
        static void encode(Pulse p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeUUID(p.id);b.writeLong(p.until);}
        static Pulse decode(FriendlyByteBuf b){return new Pulse(b.readBlockPos(),b.readUUID(),b.readLong());}
        static void handle(Pulse p,Supplier<PacketContext> supplier){var c=supplier.get();c.enqueueWork(()->ClientDispatch.runWhenOn(Dist.CLIENT,()->()->{
            var level=net.minecraft.client.Minecraft.getInstance().level;
            if(level!=null && level.getBlockEntity(p.pos) instanceof KaraokeBlockEntity device && p.id.equals(device.deviceId()))device.acceptVoicePulse(p.until);
        }));}
    }
}
