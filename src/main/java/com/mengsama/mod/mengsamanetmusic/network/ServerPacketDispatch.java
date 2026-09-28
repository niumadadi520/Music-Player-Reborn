package com.mengsama.mod.mengsamanetmusic.network;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

 
final class ServerPacketDispatch {
    static void withPlayer(Supplier<NetworkEvent.Context> supplier, Consumer<ServerPlayer> operation) {
        var context = supplier.get();
        context.setPacketHandled(true);
        if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) return;
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && !player.isRemoved()) operation.accept(player);
        });
    }
}
