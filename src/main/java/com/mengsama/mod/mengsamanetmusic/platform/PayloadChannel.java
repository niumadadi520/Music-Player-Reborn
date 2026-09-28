package com.mengsama.mod.mengsamanetmusic.platform;

import java.util.*;
import java.util.function.*;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

 
public final class PayloadChannel {
    public enum Direction { PLAY_TO_SERVER, PLAY_TO_CLIENT }
    private static final List<PayloadChannel> CHANNELS = new ArrayList<>();
    private final String name, version;
    private final boolean optional;
    private final Map<Class<?>, Binding<?>> bindings = new LinkedHashMap<>();
    private final Set<Integer> ids = new HashSet<>();
    public PayloadChannel(String name, String version, boolean optional) {
        this.name=name;this.version=version;this.optional=optional;CHANNELS.add(this);
    }
    public <T> void registerMessage(int id, Class<T> clazz, BiConsumer<T,FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf,T> decoder, BiConsumer<T,Supplier<PacketContext>> handler, Optional<Direction> direction) {
        if (!ids.add(id) || bindings.containsKey(clazz)) throw new IllegalStateException("Duplicate payload: "+name+"/"+id);
        var type=new CustomPacketPayload.Type<Envelope<T>>(ResourceLocation.fromNamespaceAndPath("mengsamanetmusic",name+"/"+id));
        bindings.put(clazz,new Binding<>(type,encoder,decoder,handler,direction.orElseThrow()));
    }
    public static void registerAll(RegisterPayloadHandlersEvent event) {
        for (var channel:CHANNELS) {
            var registrar=event.registrar(channel.version);
            if(channel.optional)registrar=registrar.optional();
            for(var binding:channel.bindings.values())binding.register(registrar);
        }
    }
    private record Envelope<T>(CustomPacketPayload.Type<Envelope<T>> type,T message) implements CustomPacketPayload {}
    private record Binding<T>(CustomPacketPayload.Type<Envelope<T>> type,BiConsumer<T,FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf,T> decoder,BiConsumer<T,Supplier<PacketContext>> handler,Direction direction) {
        void register(PayloadRegistrar registrar) {
            StreamCodec<RegistryFriendlyByteBuf,Envelope<T>> codec=StreamCodec.of((buf,value)->encoder.accept(value.message,buf),buf->new Envelope<>(type,decoder.apply(buf)));
            net.neoforged.neoforge.network.handling.IPayloadHandler<Envelope<T>> receiver=(packet,context)->handler.accept(packet.message,()->new PacketContext(context));
            if(direction==Direction.PLAY_TO_SERVER)registrar.playToServer(type,codec,receiver);
            else registrar.playToClient(type,codec,receiver);
        }
        Envelope<T> envelope(Object message) { return new Envelope<>(type,(T)message); }
    }
    private CustomPacketPayload envelope(Object message,Direction direction) {
        var binding=bindings.get(message.getClass());
        if(binding==null || binding.direction!=direction)throw new IllegalArgumentException("Unregistered payload or incorrect direction: "+message.getClass());
        return binding.envelope(message);
    }
    public void sendToServer(Object message) { PacketDistributor.sendToServer(envelope(message,Direction.PLAY_TO_SERVER)); }
    public void sendToPlayer(ServerPlayer player,Object message) { PacketDistributor.sendToPlayer(player,envelope(message,Direction.PLAY_TO_CLIENT)); }
    public void sendToAll(Object message) { PacketDistributor.sendToAllPlayers(envelope(message,Direction.PLAY_TO_CLIENT)); }
    public boolean isRemotePresent(Connection connection) {
        if (connection == null || !(connection.getPacketListener() instanceof net.neoforged.neoforge.common.extensions.ICommonPacketListener)) return false;
        return bindings.values().stream().allMatch(binding -> net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)connection.getPacketListener(),binding.type.id()));
    }
}
