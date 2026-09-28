package com.mengsama.mod.mengsamanetmusic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record StopMusicPacketServer(String targetId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetId);
    }

    public static StopMusicPacketServer decode(FriendlyByteBuf buf) {
        return new StopMusicPacketServer(buf.readUtf());
    }

    public static void handle(StopMusicPacketServer packet, Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) c.enqueueWork(() -> {
            var sender = c.getSender();
            if (sender != null && sender.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu menu
                    && !menu.resolveValidatedDevice(sender).isEmpty() && menu.getTargetId().equals(packet.targetId()))
                menu.stopPlayback(sender);
        });
        c.setPacketHandled(true);
    }
}
