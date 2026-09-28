package com.mengsama.mod.mengsamanetmusic.network;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import com.mengsama.mod.mengsamanetmusic.platform.PayloadChannel.Direction;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

 
final class ServerPacketDispatch {
    static void withPlayer(Supplier<PacketContext> supplier, Consumer<ServerPlayer> operation) {
        var context = supplier.get();
        
        if (context.getDirection() != Direction.PLAY_TO_SERVER) return;
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && !player.isRemoved()) operation.accept(player);
        });
    }
}
