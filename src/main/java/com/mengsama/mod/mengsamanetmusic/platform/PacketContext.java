package com.mengsama.mod.mengsamanetmusic.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.handling.IPayloadContext;

 
public record PacketContext(IPayloadContext context) {
    public ServerPlayer getSender() { return context.flow() == PacketFlow.SERVERBOUND && context.player() instanceof ServerPlayer player ? player : null; }
    public PayloadChannel.Direction getDirection() { return context.flow() == PacketFlow.SERVERBOUND ? PayloadChannel.Direction.PLAY_TO_SERVER : PayloadChannel.Direction.PLAY_TO_CLIENT; }
    public void enqueueWork(Runnable work) { context.enqueueWork(work); }
}
