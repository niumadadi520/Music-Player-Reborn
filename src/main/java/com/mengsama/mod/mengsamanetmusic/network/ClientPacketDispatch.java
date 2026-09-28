package com.mengsama.mod.mengsamanetmusic.network;
import java.util.function.Supplier;
import com.mengsama.mod.mengsamanetmusic.platform.PayloadChannel.Direction;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

 
public final class ClientPacketDispatch {
    public static void accept(Supplier<PacketContext> supplier, Runnable action) {
        var delivery = supplier.get();
        
        if (delivery.getDirection() == Direction.PLAY_TO_CLIENT) delivery.enqueueWork(action);
    }
}
